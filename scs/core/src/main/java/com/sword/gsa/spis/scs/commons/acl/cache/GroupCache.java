package com.sword.gsa.spis.scs.commons.acl.cache;

import java.io.Serializable;

public abstract class GroupCache implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public final long creationTime;
	
	public GroupCache() {
		this.creationTime = System.currentTimeMillis();
	}

	public abstract String getCacheInfo();
	
}
