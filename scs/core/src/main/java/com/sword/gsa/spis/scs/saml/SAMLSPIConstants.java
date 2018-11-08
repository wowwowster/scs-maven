package com.sword.gsa.spis.scs.saml;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import sword.common.utils.dates.ThreadSafeDateFormat;

public class SAMLSPIConstants {

	public static final String SAML_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
	public static final String SAMLP_NS = "urn:oasis:names:tc:SAML:2.0:protocol";

	public static final String SAML_SC_SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
	public static final String SAML_SC_REQUESTER_FAILURE = "urn:oasis:names:tc:SAML:2.0:status:Requester";
	public static final String SAML_SC_RESPONDER_FAILURE = "urn:oasis:names:tc:SAML:2.0:status:Responder";
	public static final String SAML_SC_VERSION_MISMATCH = "urn:oasis:names:tc:SAML:2.0:status:VersionMismatch";
	public static final String SAML_SC_AUTHN_FAILURE = "urn:oasis:names:tc:SAML:2.0:status:AuthnFailed";

	public static final ThreadSafeDateFormat SAML_DF = new ThreadSafeDateFormat(new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'.'SSS'Z'"));

	static {
		SAMLSPIConstants.SAML_DF.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

}
