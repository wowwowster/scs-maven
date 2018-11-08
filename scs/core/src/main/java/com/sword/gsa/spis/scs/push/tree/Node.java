package com.sword.gsa.spis.scs.push.tree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import sword.common.utils.streams.StreamUtils;
import sword.gsa.xmlfeeds.builder.acl.Permission;

public abstract class Node {

	public final String id;
	public final ContainerNode parent;
	public final byte[] serPerm;// serialized Permission

	public int state = State.NEW;

	public Node(final String id, final ContainerNode parent) {
		this(id, parent, (byte[]) null);
	}

	public Node(final String id, final ContainerNode parent, final InputStream aclInputStream) throws IOException {
		this(id, parent, consumeStream(aclInputStream));
	}

	public Node(final String id, final ContainerNode parent, final Permission p) throws IOException {
		this(id, parent, Permission.serialize(p));
	}

	private Node(final String id, final ContainerNode parent, final byte[] serPerm) {
		this.id = id;
		this.parent = parent;
		this.serPerm = serPerm;
	}

	private static byte[] consumeStream(final InputStream aclInputStream) throws IOException {
		if (aclInputStream == null) return null;
		else {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			StreamUtils.transferBytes(aclInputStream, os);
			return os.toByteArray();
		}
	}

}
