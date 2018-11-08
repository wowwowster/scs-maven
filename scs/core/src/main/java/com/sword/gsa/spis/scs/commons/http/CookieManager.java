package com.sword.gsa.spis.scs.commons.http;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public final class CookieManager {

	private static final Logger LOG = Logger.getLogger(CookieManager.class);

	public static void addLocalSessionCookie(final HttpServletRequest req, final HttpServletResponse resp, final String cookieName, String cookieValue) {
		cookieValue = new BigInteger(1, cookieValue.getBytes(StandardCharsets.UTF_8)).toString(16);
		final Cookie c = new Cookie(cookieName, cookieValue);
		c.setPath(req.getContextPath());
		c.setMaxAge(-1);
		resp.addCookie(c);
		LOG.debug("Adding cookie to response: " + c.getName() + ": " + c.getValue());
	}

	public static String restoreCookieValue(final String encoded) {
		return new String(new BigInteger(encoded, 16).toByteArray(), StandardCharsets.UTF_8);
	}

}
