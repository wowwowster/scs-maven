package com.sword.gsa.spis.scs.push.tree;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import sword.gsa.xmlfeeds.builder.acl.Permission;

public class ContainerNode extends Node {

	public final List<Node> children = new ArrayList<>();

	public ContainerNode(final String id, final ContainerNode parent) {
		super(id, parent);
	}

	public ContainerNode(final String id, final ContainerNode parent, final InputStream aclInputStream) throws IOException {
		super(id, parent, aclInputStream);
	}

	public ContainerNode(final String id, final ContainerNode parent, final Permission p) throws IOException {
		super(id, parent, p);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof ContainerNode) return id.equals(((ContainerNode) obj).id);
		return false;
	}

	@Override
	public String toString() {
		return String.format("ContainerNode #%s - child of #%s", id, parent == null ? "root" : parent.id);
	}

}
