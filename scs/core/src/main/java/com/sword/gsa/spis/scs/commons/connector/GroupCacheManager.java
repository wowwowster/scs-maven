package com.sword.gsa.spis.scs.commons.connector;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.springboard.CloudSource;
import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.authn.GroupRetrieverConf;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;

public final class GroupCacheManager implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(GroupCacheManager.class);

	private final Map<String, GroupCacheLoader> cacheLoaders = new HashMap<>();
	private final Map<String, Thread> cacheLoaderThreads = new HashMap<>();

	public GroupCacheManager(final SCSConfiguration scsConf) {
		for (final GroupRetrieverConf grc : scsConf.groupRetrievers)
			try {
				if (grc.connector instanceof ICachableGroupRetriever) {

					final IConnectorContext cc = scsConf.scsCtx.getConnectorCtx(grc.connector);
					final Path groupRetrieverCachePath = cc.getGroupRetrieverCacheDir(grc.connector.uniqueId);
					final PushConfig pc = PushConfig.readConfiguration(cc, grc.connector.uniqueId, false, "", false, "", "", PushType.UPDATE, grc.connector.namespace, grc.connector.uniqueId, -1, false);
					final CloudSource cloudsource = scsConf.configuredCloudSources.get(pc.cloudsource);
					final ICachableGroupRetriever cr = (ICachableGroupRetriever) grc.connector;
					LOG.info("Found cache loader: " + grc.connector.uniqueId + " ; cache refresh interval (minutes): " + TimeUnit.MILLISECONDS.toMinutes(grc.cacheRefreshInterval));
					final GroupCacheLoader gcmt = new GroupCacheLoader(cloudsource, cr, grc.connector.uniqueId, groupRetrieverCachePath, grc.cacheRefreshInterval);
					final Thread t = new Thread(gcmt);
					t.start();
					cacheLoaders.put(grc.connector.uniqueId, gcmt);
					cacheLoaderThreads.put(grc.connector.uniqueId, t);
				}
			} catch (final Throwable th) {
				LOG.warn("Could not create repository instance: " + grc.connector.uniqueId, th);
			}
	}

	public GroupCache getGroupCache(final String connectorId) {
		return cacheLoaders.get(connectorId).getCache();
	}

	public boolean getIsReloading(String connectorId) {
		return cacheLoaders.get(connectorId).isReloading();
	}

	public void reloadCache(String connectorId) {
		cacheLoaders.get(connectorId).reloadCache();
	}

	@Override
	public void close() {
		LOG.info("Stopping group cache loaders");
		for (final GroupCacheLoader gcl : cacheLoaders.values()) gcl.stop();
		for (final String colId : cacheLoaderThreads.keySet()) {
			final Thread colThread = cacheLoaderThreads.get(colId);
			try {
				colThread.join(TimeUnit.SECONDS.toMillis(15));
			} catch (final InterruptedException e) {}
			if (colThread.isAlive()) {
				LOG.info("Group cache loader #" + colId + " has not stopped - interrupting thread");
				colThread.interrupt();
			}
		}
	}

}
