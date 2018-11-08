package com.sword.gsa.spis.scs.push.tree;

public class NodeRef {

	public final boolean isContainer;
	public final String id;

	public NodeRef(final boolean isContainer, final String id) {
		super();
		this.isContainer = isContainer;
		this.id = id;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof NodeRef) {
			final NodeRef nr = (NodeRef) obj;
			return id.equals(nr.id) && isContainer == nr.isContainer;
		}
		return false;
	}

}
