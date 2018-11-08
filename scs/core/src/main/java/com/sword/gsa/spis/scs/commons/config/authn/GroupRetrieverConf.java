package com.sword.gsa.spis.scs.commons.config.authn;

import java.util.concurrent.TimeUnit;

import com.sword.gsa.spis.scs.commons.connector.models.AConnector;

public class GroupRetrieverConf {

	public static final long DEFAULT_CRI = TimeUnit.HOURS.toMillis(6);

	public final AConnector connector;
	public final long cacheRefreshInterval;

	public GroupRetrieverConf(final AConnector connector, final long cacheRefreshInterval) {
		super();
		this.connector = connector;
		this.cacheRefreshInterval = cacheRefreshInterval;
	}

}
