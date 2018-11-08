package com.sword.gsa.spis.scs.commons.config.authz;

import java.util.regex.Pattern;

import com.sword.gsa.spis.scs.commons.connector.models.AConnector;

public final class SAMLAuthzConf {

	public final AConnector authorizer;
	public final AConnector nameTransformer;
	public final Pattern urlPattern;

	public SAMLAuthzConf(final AConnector authorizer, final AConnector nameTransformer, final Pattern urlPattern) {
		super();
		this.authorizer = authorizer;
		this.nameTransformer = nameTransformer;
		this.urlPattern = urlPattern;
	}

}