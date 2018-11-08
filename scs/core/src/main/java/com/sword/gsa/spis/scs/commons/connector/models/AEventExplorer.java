package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.List;

import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.databases.TreeTableManager;
import com.sword.gsa.spis.scs.push.tree.NodeRef;

public abstract class AEventExplorer extends AExplorer {

	public AEventExplorer(final PushProcessSharedObjectsStore sos) {
		super(sos);
	}

	public abstract boolean isEventModeEnabled();

	public abstract void storeInitialState() throws Exception;

	public abstract void processEvents(final TreeTableManager ttm, final List<NodeRef> deletedItems) throws Exception;

	public abstract void commitDeletionChanges(final List<NodeRef> deletedItems) throws Exception;

}
