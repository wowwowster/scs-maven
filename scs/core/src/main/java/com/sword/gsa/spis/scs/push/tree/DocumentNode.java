package com.sword.gsa.spis.scs.push.tree;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import sword.gsa.xmlfeeds.builder.acl.Permission;

public class DocumentNode extends Node {

	public final Date lastModificationDate;
	public final boolean isDateSet;

	public DocumentNode(final String id, final ContainerNode parent) {
		super(id, parent);
		lastModificationDate = null;
		isDateSet = false;
	}

	public DocumentNode(final String id, final ContainerNode parent, final InputStream aclInputStream) throws IOException {
		super(id, parent, aclInputStream);
		lastModificationDate = null;
		isDateSet = false;
	}

	public DocumentNode(final String id, final ContainerNode parent, final Permission p) throws IOException {
		super(id, parent, p);
		lastModificationDate = null;
		isDateSet = false;
	}

	public DocumentNode(final String id, final ContainerNode parent, final Date lastModificationDate) {
		super(id, parent);
		this.lastModificationDate = lastModificationDate;
		isDateSet = true;
	}

	public DocumentNode(final String id, final ContainerNode parent, final InputStream aclInputStream, final Date lastModificationDate) throws IOException {
		super(id, parent, aclInputStream);
		this.lastModificationDate = lastModificationDate;
		isDateSet = true;
	}

	public DocumentNode(final String id, final ContainerNode parent, final Permission p, final Date lastModificationDate) throws IOException {
		super(id, parent, p);
		this.lastModificationDate = lastModificationDate;
		isDateSet = true;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof DocumentNode) return id.equals(((Node) obj).id);
		return false;
	}

	@Override
	public String toString() {
		return String.format("DocumentNode #%s - child of #%s", id, parent == null ? "root" : parent.id);
	}

}
