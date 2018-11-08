package com.sword.gsa.spis.scs.gsaadmin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import sword.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;

public class GSAConnectivityStatus {

	private static final long MAX_AGE = TimeUnit.MINUTES.toMillis(30);

	public final long creationTime;
	public final boolean isConfigured;
	public final boolean canBeBound;
	public final String adminHost;

	public GSAConnectivityStatus(final SCSConfiguration conf) {
		super();
		this.creationTime = System.currentTimeMillis();
		this.isConfigured = !((conf == null) || (conf.gsa == null));
		this.canBeBound = this.isConfigured ? GsaManager.answersPing(conf.gsa) : false;
		this.adminHost = this.isConfigured ? conf.gsa.adminHost : null;
	}

	public void toJson(final JsonGenerator jg, final boolean isAuthenticated) throws IOException {
		jg.writeObjectFieldStart("connectivity");
		jg.writeBooleanField("IsConfigured", isConfigured);
		jg.writeBooleanField("CanBeBound", canBeBound);
		jg.writeBooleanField("IsAuthenticated", isAuthenticated);
		jg.writeEndObject();
	}

	public static boolean isValid(final GSAConnectivityStatus gcs, final SCSConfiguration conf) {
		if (gcs == null) return false;
		else if (System.currentTimeMillis() - gcs.creationTime > MAX_AGE) return false;
		else if ((conf == null) || (conf.gsa==null)) return (gcs.adminHost==null);
		else return StringUtils.npeProofEquals(conf.gsa.adminHost, gcs.adminHost);
	}

}
