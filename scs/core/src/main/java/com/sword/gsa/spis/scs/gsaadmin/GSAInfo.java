package com.sword.gsa.spis.scs.gsaadmin;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sword.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonGenerator;

public class GSAInfo {

	private static final Pattern VERSION_CHECK = Pattern.compile("^([0-9]+)\\.([0-9]+).+");

	public final boolean ssl;
	public final String defaultHost;
	public final String adminHost;
	public final String software;
	public final String system;
	public final String id;
	public final boolean noConnection;
	public final boolean is72orHigher;

	public GSAInfo(final boolean ssl, final String defaultHost, final String adminHost, final String software, final String system, final String id, final boolean noConnection) {
		super();
		this.ssl = ssl;
		this.defaultHost = defaultHost;
		this.adminHost = adminHost;
		this.software = software;
		this.system = system;
		this.id = id;
		this.noConnection = noConnection;
		this.is72orHigher = parseVersion(system) && parseVersion(software);
	}

	public static void toJson(final GSAInfo gi, final JsonGenerator jg) throws IOException {
		jg.writeObjectFieldStart("gsa_info");
		jg.writeBooleanField("ssl", gi.ssl);
		jg.writeStringField("DefaultHost", gi.defaultHost);
		jg.writeStringField("AdminHost", gi.adminHost);
		jg.writeStringField("Software", gi.software);
		jg.writeStringField("System", gi.system);
		jg.writeStringField("ID", gi.id);
		jg.writeBooleanField("SupportMembershipFeeds", gi.is72orHigher);
		jg.writeEndObject();
	}

	private static boolean parseVersion(final String v) {
		final Matcher m = VERSION_CHECK.matcher(v);
		if (m.find()) {
			final String major = m.group(1);
			final String minor = m.group(2);
			if (StringUtils.isInteger(major) && StringUtils.isInteger(minor)) {
				final int majorInt = Integer.parseInt(major);
				final int minorInt = Integer.parseInt(minor);
				return majorInt > 7 || majorInt == 7 && minorInt >= 2;
			} else return false;
		} else return false;
	}

}
