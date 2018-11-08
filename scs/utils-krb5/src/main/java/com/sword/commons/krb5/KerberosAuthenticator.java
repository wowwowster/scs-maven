package com.sword.commons.krb5;

import org.ietf.jgss.GSSException;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class KerberosAuthenticator {

	public static void init(String jaasConf, String krbConf) {
		System.setProperty("java.security.auth.login.config", jaasConf);
		System.setProperty("java.security.krb5.conf", krbConf);
	}

	public static Subject authenticateNegoHeader(String headerValue) throws LoginException, GSSException {
		Subject subject = new Subject();
		LoginContext ctx = new LoginContext("Server", new CallbackHandler());
		ctx.login();
		subject = ctx.getSubject();

		PrivilegedAction pa = new PrivilegedAction(headerValue);
		
		Subject.doAs(subject, pa);
		if (pa.failed()) throw pa.getException();

		Subject.doAs(subject, pa);
		if (pa.failed()) throw pa.getException();

		Subject clientSubject = new Subject();
		clientSubject.getPrincipals().add(new KerberosPrincipal(pa.getGSSContext().getSrcName().toString()));
		return clientSubject;
	}

}
