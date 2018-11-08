package com.sword.gsa.spis.scs.commons.http;

public class AuthNStrings {

	// Cookies names
	public static final String REFERER_COOKIE_NAME = "swref";
	public static final String ISSUER_COOKIE_NAME = "swissuer";
	public static final String SAMLID_COOKIE_NAME = "swid";
	public static final String SAMLDATE_COOKIE_NAME = "swtime";

	// Form HTTP parameters names
	public static final String USERNAME_HTTP_PARAM = "swU";
	public static final String PASSWORD_HTTP_PARAM = "swP";
	public static final String MODE_HTTP_PARAM = "AuthentMode";
	public static final String TWIGKIT_MODE = "Twigkit";

	public static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";
	public static final String HEADER_AUTHORIZATION = "Authorization";
	public static final String NEG_TOKEN = "Negotiate";

	// GSA authentication HTTP parameters
	public static final String RELAY_STATE_HTTP_PARAM = "RelayState";
	public static final String SAML_REQUEST_HTTP_PARAM = "SAMLRequest";

	// KrbFailed suggestions
	public static final String KRB_FAILED_HTTP_PARAM = "KrbUnavailable";
	public static final String TO_FORM_HTTP_PARAM_VALUE = "To login form";

}
