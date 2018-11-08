package com.sword.gsa.spis.scs.push.tree;

import java.sql.SQLException;

import com.sword.gsa.spis.scs.push.databases.TreeTableManager;

public final class State {

	public static final int EXCLUDED = 1;
	public static final int COMPLETE = 2;
	public static final int NEW = 3;
	public static final int PSEUDO_STATE_ERROR_RANGE_START = 4;

	// STATE_ERROR = any int > PSEUDO_STATE_ERROR_RANGE_START

	public static ErrorState setErrorState(final TreeTableManager ttm, final Node node, final boolean commit) throws SQLException {
		int errorCount = 0;
		if (node.state > State.PSEUDO_STATE_ERROR_RANGE_START) {
			errorCount = node.state - State.PSEUDO_STATE_ERROR_RANGE_START;
		}
		if (errorCount > 3) {
			ttm.updateState(node, State.EXCLUDED, commit);
			return new ErrorState(-1, State.EXCLUDED);
		} else {
			errorCount++;
			ttm.updateState(node, State.PSEUDO_STATE_ERROR_RANGE_START + errorCount, commit);
			return new ErrorState(errorCount, State.PSEUDO_STATE_ERROR_RANGE_START + errorCount);
		}
	}

	public static ErrorState getErrorState(final Node node) {
		int errorCount = 0;
		if (node.state > State.PSEUDO_STATE_ERROR_RANGE_START) {
			errorCount = node.state - State.PSEUDO_STATE_ERROR_RANGE_START;
		}
		if (errorCount > 3) return new ErrorState(-1, State.EXCLUDED);
		else {
			errorCount++;
			return new ErrorState(errorCount, State.PSEUDO_STATE_ERROR_RANGE_START + errorCount);
		}
	}

}
