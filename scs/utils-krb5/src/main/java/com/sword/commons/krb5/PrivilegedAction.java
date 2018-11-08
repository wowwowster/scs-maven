package com.sword.commons.krb5;

import org.apache.commons.codec.binary.Base64;
import org.ietf.jgss.*;

public class PrivilegedAction implements java.security.PrivilegedAction<Boolean> {

	private final String authzHeader;
	
	private boolean first = true;
	private GSSManager manager = null;
	private GSSCredential serverCreds = null;
	private GSSContext context = null;
	private GSSException exception = null;

	public PrivilegedAction(String authzHeader) {
		this.authzHeader = authzHeader;
	}
	
	public boolean failed() {
		return exception != null;
	}
	
	public GSSException getException() {
		return exception;
	}
	
	public GSSContext getGSSContext() {
		return context;
	}

	@Override
	public Boolean run() {
		exception = null;
		try {
			if (first) {
				Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
				manager = GSSManager.getInstance();
				serverCreds = manager.createCredential(null, 0, spnegoOid, 2);
				first = false;
			} else {
				context = manager.createContext(serverCreds);
				byte[] token = Base64.decodeBase64(authzHeader.substring(10));
				token = context.acceptSecContext(token, 0, token.length);
			}
			return true;
		} catch (GSSException e) {
			exception = e;
			return false;
		}
	}

}
