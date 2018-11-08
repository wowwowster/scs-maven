package com.sword.gsa.spis.scs.push;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import sword.gsa.xmlfeeds.FeedPoster;
import sword.gsa.xmlfeeds.builder.Authmethod;
import sword.gsa.xmlfeeds.builder.streamed.RateWatcher;

import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.databases.PushTableManager;
import com.sword.gsa.spis.scs.push.monitoring.Monitor;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;

public final class PushProcessSharedObjectsStore implements AutoCloseable {

	public final Indexer connector;
	public final PushConfig pushConf;
	public final SCSConfiguration scsConf;
	public final Date pushStartTime;
	public final boolean isInitialPush;
	public final Date lastPushDate;
	public final Date lastDeletionPushDate;
	public final boolean hasUnfinishedDeletionPush;
	public final boolean connectorIndexesACLs;
	public final boolean connectorCanDetectAclChanges;
	public final BlockingQueue<String> indexingThreadQueue;
	public final FeedPoster feedPoster;
	public final RateWatcher rateWatcher;
	public final Monitor monitor;

	public PushProcessSharedObjectsStore(final Indexer connector, final PushConfig confStore, SCSConfiguration scsConf, final Date pushStartTime, final long maxBytesPerSecond) throws ReflectiveOperationException, SQLException, SecurityException, IOException {
		this.connector = connector;
		pushConf = confStore;
		this.scsConf = scsConf;
		this.pushStartTime = pushStartTime;
		rateWatcher = maxBytesPerSecond > 0L ? new RateWatcher(maxBytesPerSecond, 1L, TimeUnit.SECONDS) : null;
		this.monitor = new Monitor(pushConf);

		boolean iip = true;
		Date lpd = null;
		Date ldpd = null;
		boolean hudp = false;
		try (PushTableManager ptm = new PushTableManager(pushConf)) {
			iip = ptm.isInitialPush();
			lpd = ptm.getLastPushDate();
			ldpd = ptm.getLastDeletionPushDate();
			hudp = ptm.hasUnfinishedDeletionPush();
		}
		isInitialPush = iip;
		lastPushDate = lpd;
		lastDeletionPushDate = ldpd;
		hasUnfinishedDeletionPush = hudp;

		connectorIndexesACLs = pushConf.authMethod != Authmethod.none && connector.supportsEarlyBinding();
		connectorCanDetectAclChanges = connector.canDetectAclModification();

		indexingThreadQueue = new ArrayBlockingQueue<>(pushConf.maxIndexingThreads);

		feedPoster = new FeedPoster(confStore.sslGsa);

	}

	public AExplorer getExplorer() throws ReflectiveOperationException {
		return connector.getExplorerClass().getConstructor(PushProcessSharedObjectsStore.class).newInstance(this);
	}

	public ADocLoader getDocLoader(final AExplorer explorer, final ContainerNode parentNode) throws ReflectiveOperationException {
		return connector.getDocLoaderClass().getConstructor(AExplorer.class, PushProcessSharedObjectsStore.class, ContainerNode.class).newInstance(explorer, this, parentNode);
	}

	@Override
	public void close() throws Exception {
		feedPoster.close();
		monitor.close();
	}

}
