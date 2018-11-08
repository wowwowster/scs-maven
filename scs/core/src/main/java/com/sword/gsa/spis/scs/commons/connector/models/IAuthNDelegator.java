package com.sword.gsa.spis.scs.commons.connector.models;

import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface IAuthNDelegator extends IAuthenticator {

	/**
	 * First, the connector will be asked to redirect the user to the page we're delegating authentication to
	 */
	public void redirectToAuthNProvider(HttpServletRequest req, HttpServletResponse resp) throws Exception;

	/**
	 * Secondly, the connector will be asked if the request contains enough information to perform the final authentication task
	 */
	public boolean isFinalStep(HttpServletRequest req, HttpServletResponse resp);

	/**
	 * Finally, the connector will be asked to extract a user name form the request
	 */
	public String authenticate(HttpServletRequest req, HttpServletResponse resp) throws AuthenticationException;

}
