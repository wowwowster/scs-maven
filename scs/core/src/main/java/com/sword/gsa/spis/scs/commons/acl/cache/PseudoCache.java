package com.sword.gsa.spis.scs.commons.acl.cache;

import java.io.Serializable;

public abstract class PseudoCache extends GroupCache implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final String cacheInfo;
	
	public PseudoCache(String cacheInfo) {
		super();
		this.cacheInfo = cacheInfo;
	}
	
	@Override
	public String getCacheInfo() {
		return cacheInfo;
	}
	
}
