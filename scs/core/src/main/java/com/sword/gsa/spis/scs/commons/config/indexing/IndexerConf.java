package com.sword.gsa.spis.scs.commons.config.indexing;

public class IndexerConf {

	public final String connectorId;
	public final long interval;
	public final long throughput;
	public final Schedule schedule;

	public IndexerConf(final String connectorId, final long interval, final long throughput, final Schedule schedule) {
		super();
		this.connectorId = connectorId;
		this.interval = interval;
		this.throughput = throughput;
		this.schedule = schedule;
	}

}
