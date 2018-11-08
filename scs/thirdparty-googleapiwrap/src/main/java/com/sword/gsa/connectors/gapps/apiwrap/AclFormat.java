package com.sword.gsa.connectors.gapps.apiwrap;

import sword.common.utils.StringUtils;

public enum AclFormat {
	NAME, EMAIL;

	public static AclFormat parse(final String s) {
		if (!StringUtils.isNullOrEmpty(s)) {
			for (final AclFormat af : AclFormat.values())
				if (af.name().equalsIgnoreCase(s)) return af;
		}
		return EMAIL;
	}
}
