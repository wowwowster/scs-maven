package com.sword.gsa.spis.scs.commons.krb;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Iterator;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ietf.jgss.GSSException;

import com.sword.commons.krb5.KerberosAuthenticator;
import com.sword.gsa.spis.scs.commons.SCSContext;
import com.sword.gsa.spis.scs.commons.http.AuthNStrings;
import com.sword.scs.Constants;

public class KerberosChallenge {

	private static final byte[] INVISIBLE_PAGE = ("<!DOCTYPE html>\n<html>\n<head>\n\n	<meta charset=\"UTF-8\">\n	<meta name=\"robots\" content=\"NOINDEX,NOFOLLOW\" >\n\n	<title>Kerberos Authentication</title>\n\n	<link rel=\"shortcut icon\" href=\"/favicon.ico\" type=\"image/x-icon\" >\n	<link rel=\"stylesheet\" href=\"/static/css/pages.css\" type=\"text/css\" >\n\n</head>\n<body>\n	<div id=\"header\" >\n		<!-- Nothing yet -->\n	</div>\n	<div id=\"body\" >\n		<div class=\"title lonely_title\">Kerberos negotiation is not handled seamlessly by your webbrowser.</div>\n		<form method=\"get\" action=\"authenticate\" >\n			<div class=\"form_block\" >\n				<div class=\"form_row\" >Click the button to be redirected to a login form</div>\n				<div class=\"form_row\" ><input class=\"buttons\" type=\"submit\" name=\""
		+ AuthNStrings.KRB_FAILED_HTTP_PARAM + "\" value=\"{2}\" /></div>\n			</div>\n		</form>\n	</div>\n	<div id=\"footer\" >\n		<div class=\"copy\" >Copyright &copy; 2013 Sword Group. All Rights Reserved.</div>\n	</div>\n</body>\n</html>")
		.getBytes(StandardCharsets.UTF_8);

	public static void sendNegotiate(final HttpServletRequest req, final HttpServletResponse resp, final boolean canFallbackToLoginForm) throws IOException {
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		resp.addHeader(AuthNStrings.HEADER_WWW_AUTHENTICATE, AuthNStrings.NEG_TOKEN);
		resp.addHeader("Connection", "Close");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resp.setContentType("text/html");
		resp.setContentLength(INVISIBLE_PAGE.length);
		try (BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream())) {
			bos.write(INVISIBLE_PAGE);
		}
	}

	public static String challenge(final SCSContext scsCtx, final HttpServletRequest req, final HttpServletResponse resp) throws LoginException, GSSException {
		KerberosAuthenticator.init(scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_JAAS_CONF).toString(), scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_KRB_CONF).toString());
		return getPrincipalStr(KerberosAuthenticator.authenticateNegoHeader(req.getHeader(AuthNStrings.HEADER_AUTHORIZATION)));
	}

	public static String getPrincipalStr(final Subject subject) {
		String principal = null;
		final Set<Principal> principals = subject.getPrincipals();
		if (!principals.isEmpty()) {
			final Iterator<Principal> it = principals.iterator();
			if (it.hasNext()) {
				final Principal ppal = it.next();
				principal = ppal.getName().substring(0, ppal.getName().indexOf("@"));
			}
		}
		return principal;
	}

}
