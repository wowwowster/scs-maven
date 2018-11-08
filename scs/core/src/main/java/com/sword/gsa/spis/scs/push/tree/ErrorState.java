package com.sword.gsa.spis.scs.push.tree;

public final class ErrorState {

	public final int errorCount;
	public final int state;

	public ErrorState(final int errorCount, final int state) {
		super();
		this.errorCount = errorCount;
		this.state = state;
	}

}
