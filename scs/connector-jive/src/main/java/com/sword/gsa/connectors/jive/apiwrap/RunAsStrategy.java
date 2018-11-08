package com.sword.gsa.connectors.jive.apiwrap;

import sword.common.utils.StringUtils;

public enum RunAsStrategy {

	NONE, URI, EMAIL, USERID, USERNAME;

	public static RunAsStrategy parse(final String strRunAs) {
		for (final RunAsStrategy ras : RunAsStrategy.values())
			if (StringUtils.npeProofEquals(strRunAs, ras.name(), true)) return ras;
		return NONE;
	}

}
