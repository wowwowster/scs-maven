package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.Node;

/**
 * The explorer takes care of discovering the children of a container. A container may contain containers, documents or both. Containers lightweight representation is the {@link Node}. Child documents
 * IDs should be added to current container's {@link Node#childDocs}. Child containers lightweight representation should be added to current container's {@link Node#childConts}. All child containers
 * have to be added. Child documents have to be added only if requested.
 */
public abstract class AExplorer implements AutoCloseable {

	protected static final String ROOT_NODE_NAME = "root";
	protected static final ContainerNode ROOT_NODE = new ContainerNode(ROOT_NODE_NAME, null);
	protected static final Logger LOG = Logger.getLogger(AExplorer.class);

	protected final PushProcessSharedObjectsStore sharedObjects;
	protected final Map<String, String> configurationParams;

	public AExplorer(final PushProcessSharedObjectsStore sharedObjects) {
		super();
		this.sharedObjects = sharedObjects;
		configurationParams = ((AConnector) this.sharedObjects.connector).cpMap;
	}

	@SuppressWarnings({"static-method", "unused"})
	public List<ContainerNode> getRootNodes() throws Exception {
		final List<ContainerNode> rni = new ArrayList<>();
		rni.add(ROOT_NODE);
		return rni;
	}

	public abstract void loadChildren(final ContainerNode node, final boolean isUpdateMode, final boolean isPublicMode) throws Exception;

}
