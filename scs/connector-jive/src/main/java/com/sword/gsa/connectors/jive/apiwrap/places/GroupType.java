package com.sword.gsa.connectors.jive.apiwrap.places;

import sword.common.utils.StringUtils;

public enum GroupType {
	NON_GROUP, OPEN, MEMBER_ONLY, PRIVATE, SECRET;

	public static GroupType parse(final String groupTypeString) {
		if (StringUtils.isNullOrEmpty(groupTypeString)) return NON_GROUP;
		for (final GroupType gt : GroupType.values())
			if (gt.name().equals(groupTypeString)) return gt;
		throw new IllegalArgumentException("Unknown group type: " + groupTypeString);
	}
}