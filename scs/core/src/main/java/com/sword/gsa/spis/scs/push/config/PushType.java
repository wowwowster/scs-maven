package com.sword.gsa.spis.scs.push.config;

import sword.common.utils.StringUtils;

public enum PushType {

	UPDATE(), BROWSE_DB("browse_db, browsedb");

	private String[] acceptableNames;

	PushType() {
		acceptableNames = null;
	}

	PushType(final String typeName) {
		acceptableNames = typeName.split(",");
	}

	public static PushType lookupName(final String curName) {
		if (StringUtils.isNullOrEmpty(curName)) return UPDATE;
		for (final PushType pt : PushType.values())
			if (pt.isNameOk(curName)) return pt;
		return UPDATE;
	}

	private boolean isNameOk(final String curName) {
		if (acceptableNames == null) return false;
		for (final String name : acceptableNames)
			if (name.equalsIgnoreCase(curName)) return true;
		return false;
	}
}
