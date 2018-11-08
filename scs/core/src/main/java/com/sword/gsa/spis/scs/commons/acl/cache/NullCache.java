package com.sword.gsa.spis.scs.commons.acl.cache;

import java.io.Serializable;

public final class NullCache extends PseudoCache implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public NullCache() {
		super("Cache loader just started - cache loading is about to begin.");
	}
}
