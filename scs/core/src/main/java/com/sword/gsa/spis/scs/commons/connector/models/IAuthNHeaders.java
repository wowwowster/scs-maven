package com.sword.gsa.spis.scs.commons.connector.models;

import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Authentication process interface.
 */
public interface IAuthNHeaders extends IAuthenticator {

	/**
	 * Authenticates a user, extracting authentication information from the headers and cookies
	 *
	 * @param http
	 *            request
	 * @param http
	 *            response
	 * @return The SSO username associated with this user ; null if authentication fails.
	 */
	public String authenticate(HttpServletRequest req, HttpServletResponse resp) throws AuthenticationException;

}
