package com.sword.gsa.spis.scs.commons.acl.cache;

import java.io.Serializable;

public final class FailedCache extends PseudoCache implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public final Exception error;
	
	public FailedCache(Exception e) {
		super("Cache loader interrupted because it reached its maximum number of consecutive errors (last error was: " + e.getClass().getName() + "). Please check log for more information about this failure.");
		this.error = e;
	}
}
