package com.sword.gsa.spis.scs.commons.connector;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sword.gsa.spis.scs.commons.connector.models.AConnector;

public final class TransformedNamesCacheMgr {

	private static final long CACHE_MAX_AGE = TimeUnit.MINUTES.toMillis(90);

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Map<String, MappingsCache> mappingsCache = new HashMap<>();

	public String getCachedMapping(final AConnector cr, final String username) {
		lock.readLock().lock();
		try {
			if (mappingsCache.containsKey(cr.uniqueId)) {
				final MappingsCache mc = mappingsCache.get(cr.uniqueId);
				if (mc.mappings.containsKey(username)) {
					final long age = mc.mappingsCreationTime.get(username);
					if (System.currentTimeMillis() < age + CACHE_MAX_AGE) return mc.mappings.get(username);
				}
			}
		} finally {
			lock.readLock().unlock();
		}
		return null;
	}

	public void addCachedMapping(final AConnector cr, final String username, final String alternativeUsername) {
		lock.writeLock().lock();
		try {
			if (!mappingsCache.containsKey(cr.uniqueId)) mappingsCache.put(cr.uniqueId, this.new MappingsCache());
			final MappingsCache mc = mappingsCache.get(cr.uniqueId);
			mc.mappings.put(username, alternativeUsername);
			mc.mappingsCreationTime.put(username, System.currentTimeMillis());
		} finally {
			lock.writeLock().unlock();
		}
	}

	class MappingsCache {

		final Map<String, String> mappings = new HashMap<>();
		final Map<String, Long> mappingsCreationTime = new HashMap<>();
	}

}