package com.sword.gsa.spis.scs.commons.connector.models;

import javax.naming.AuthenticationException;

/**
 * Authentication process interface.
 */
public interface IAuthNFormData extends IAuthenticator {

	/**
	 * Authenticates a user after credential was posted in the login page
	 *
	 * @param username
	 * @param password
	 * @return The SSO username associated with this user
	 */
	public String authenticate(String username, String password) throws AuthenticationException;

}
