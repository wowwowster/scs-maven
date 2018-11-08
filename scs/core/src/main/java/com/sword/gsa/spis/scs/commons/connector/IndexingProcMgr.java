package com.sword.gsa.spis.scs.commons.connector;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.indexing.IndexerConf;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;

public class IndexingProcMgr implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IndexingProcMgr.class);

	private final ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
	private final Map<String, Thread> runningIndexers = new HashMap<>();
	private final Map<String, IndexingProcWatcher> indexerWatchers = new HashMap<>();
	private final SCSConfiguration scsConf;
	private final String envPath;

	public IndexingProcMgr(final SCSConfiguration scsConf, final String envPath) {
		this.scsConf = scsConf;
		this.envPath = envPath;

		// Check if some indexers are running and supposed to be stopped (happens in case of schedule reconfiguration)
		final long nowMillis = System.currentTimeMillis();
		for (final IndexerConf ic : scsConf.indexers)
			if (ic.schedule != null) {
				final long howLongWillRemainEffective = ic.schedule.howLongWillRemainEffective(nowMillis);
				if (howLongWillRemainEffective != Schedule.NOT_EFFECTIVE_NOW) {
					LOG.warn("Indexer #" + ic.connectorId + " is running out of schedule - stopping");
					try {
						final IndexingProcWatcher ipw = getWatcher(ic);
						if (ipw.answersPing()) ipw.killIndexer();
					} catch (IllegalArgumentException e) {
						LOG.warn("Indexer #" + ic.connectorId + " is not a valid indexer");
					}
				}
			}
	}
	
	private IndexingProcWatcher getWatcher(final IndexerConf ic) {
		final AConnector c = scsConf.configuredConnectors.get(ic.connectorId);
		if ((c != null) && (c instanceof Indexer)) {
			final IConnectorContext cc = scsConf.scsCtx.getConnectorCtx(c);
			return new IndexingProcWatcher(scsConf, ic, c, cc, this.envPath);
		} else {
			throw new IllegalArgumentException("Invalid connector #" + ic.connectorId);
		}
	}

	public void startAllIndexers() {
		rwLock.writeLock().lock();
		try {
			for (final IndexerConf ic : scsConf.indexers) {
				LOG.info("Starting indexer #" + ic.connectorId);
				try {
					final IndexingProcWatcher ipw = getWatcher(ic);
					final Thread ipwThread = new Thread(ipw);
					ipwThread.setName("IndexerWatcher #" + ic.connectorId);
					ipwThread.start();
					indexerWatchers.put(ic.connectorId, ipw);
					runningIndexers.put(ic.connectorId, ipwThread);
				} catch (IllegalArgumentException e) {
					LOG.warn("Indexer #" + ic.connectorId + " is not a valid indexer");
				}
			}
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	public IndexingProcWatcher start(final IndexerConf ic, boolean dbBrowsingMode) {
		if (dbBrowsingMode) LOG.info("Starting db browsing mode for Indexer #" + ic.connectorId);
		else LOG.info("Starting indexer (forced) #" + ic.connectorId);

		final boolean exists;
		rwLock.readLock().lock();
		try {
			exists = runningIndexers.containsKey(ic.connectorId);
		} finally {
			rwLock.readLock().unlock();
		}

		if (exists) {
			final IndexingProcWatcher ipw;
			final Thread ipwThread;
			rwLock.readLock().lock();
			try {
				ipw = indexerWatchers.get(ic.connectorId);
				ipw.setForcedStart();
				if (dbBrowsingMode) ipw.enableDBBrowsingMode();
				ipwThread = runningIndexers.get(ic.connectorId);
			} finally {
				rwLock.readLock().unlock();
			}
			if (ipwThread.isAlive()) {
				LOG.info("IndexerWatcherThread is already running for #" + ic.connectorId);
				ipw.wake();// If the thread is waiting for next start time, wake it
				return ipw;
			} else {
				final Thread newIpwThread = new Thread(ipw);
				newIpwThread.setName("IndexerWatcher #" + ic.connectorId);
				newIpwThread.start();
				rwLock.writeLock().lock();
				try {
					runningIndexers.put(ic.connectorId, newIpwThread);
				} finally {
					rwLock.writeLock().unlock();
				}
				final Object o = new Object();
				synchronized (o) {
					try {
						o.wait(250);
					} catch (final InterruptedException e) {}
				}// Wait a few milliseconds to make sure the thread has verified its next start time
				ipw.wake();// If the thread is waiting for next start time, wake it
				return ipw;
			}
		} else {
			final IndexingProcWatcher ipw = getWatcher(ic);
			ipw.setForcedStart();
			if (dbBrowsingMode) ipw.enableDBBrowsingMode();
			final Thread ipwThread = new Thread(ipw);
			ipwThread.setName("IndexerWatcher #" + ic.connectorId);
			ipwThread.start();
			rwLock.writeLock().lock();
			try {
				indexerWatchers.put(ic.connectorId, ipw);
				runningIndexers.put(ic.connectorId, ipwThread);
			} finally {
				rwLock.writeLock().unlock();
			}
			if (!dbBrowsingMode) {
				final Object o = new Object();
				synchronized (o) {
					try {
						o.wait(250);
					} catch (final InterruptedException e) {}
				}// Wait a few milliseconds to make sure the thread has verified its next start time
				ipw.wake();// If the thread is waiting for next start time, wake it
			}
			return ipw;
		}
	}
	
	public static boolean waitStart(final IndexingProcWatcher ipw, final IndexerConf ic, long lastStartRequestTime, long maxWait) throws InterruptedException {
		final long start = System.currentTimeMillis();
		final Object lock = new Object();
		synchronized (lock) { lock.wait(800); }//Starting monitor thread is not the 1st thing the indexer does - leave it some time to do so.
		final long waitTime = 300L;

		boolean isRunning = false;
		while (!isRunning) {
			if (ipw.lastRunAttemptEndTime > lastStartRequestTime) return false;//Stopped after last time start was requested
			else if ((System.currentTimeMillis() - start) > maxWait) return false;//Waited for too long for the connector to start
			else isRunning = ipw.answersPing();//Check whether connector answers ping
			
			if (!isRunning) synchronized (lock) { lock.wait(waitTime); }
			
		}
		return isRunning;
	}

	public void stop(final IndexerConf ic) throws IOException {
		LOG.info("Stopping indexer #" + ic.connectorId);
		final AConnector c = scsConf.configuredConnectors.get(ic.connectorId);
		if (c != null && c instanceof Indexer) getWatcher(ic).requestIndexerStop();
	}
	
	public boolean waitStop(final IndexerConf ic, long maxWait) throws InterruptedException {
		return waitStop(getWatcher(ic), ic, maxWait);
	}
	public static boolean waitStop(final IndexingProcWatcher ipw, final IndexerConf ic, long maxWait) throws InterruptedException {
		final long start = System.currentTimeMillis();
		final Object lock = new Object();
		final long waitTime = 300L;
		boolean isRunning;
		while ((isRunning = ipw.answersPing()) && ((System.currentTimeMillis() - start) < maxWait)) {
			synchronized (lock) { lock.wait(waitTime); }
		}
		if (!isRunning) {
			synchronized (lock) { lock.wait(waitTime); }//Wait some more time to ensure process actually exited (monitor stops answering before process actually ends)
		}
		return !isRunning;
	}

	public void reset(final IndexerConf ic) throws IOException, InterruptedException {
		LOG.info("Resetting indexer #" + ic.connectorId);
		rwLock.writeLock().lock();
		IndexingProcWatcher ipw = null;
		try {
			if (runningIndexers.containsKey(ic.connectorId)) {
				ipw = indexerWatchers.get(ic.connectorId);
				final Thread ipwThread = runningIndexers.get(ic.connectorId);
				ipw.stopWatcher(ipwThread);
				if (ipw.answersPing()) {
					LOG.info("Killing running indexer #" + ic.connectorId);
					ipw.killIndexer();
					if (!waitStop(ipw, ic, TimeUnit.MINUTES.toMillis(2))) throw new IllegalStateException("Failed to kill indexing process");
				}
				runningIndexers.remove(ic.connectorId);
				indexerWatchers.remove(ic.connectorId);
			} else {
				final AConnector c = scsConf.configuredConnectors.get(ic.connectorId);
				if (c != null && c instanceof Indexer) {
					ipw = getWatcher(ic);
					if (ipw.answersPing()) {
						LOG.info("Killing running indexer #" + ic.connectorId);
						ipw.killIndexer();
					}
				}
			}
			if (ipw != null) ipw.clearState();
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	public void kill(final IndexerConf ic) throws InterruptedException {
		rwLock.writeLock().lock();
		try {
			LOG.info("Killing indexer #" + ic.connectorId);
			if (runningIndexers.containsKey(ic.connectorId)) {
				final IndexingProcWatcher ipw = indexerWatchers.get(ic.connectorId);
				if (ipw.answersPing()) {
					ipw.killIndexer();
					if (!waitStop(ipw, ic, TimeUnit.MINUTES.toMillis(2))) throw new IllegalStateException("Failed to kill indexing process");
				}
				runningIndexers.remove(ic.connectorId);
				indexerWatchers.remove(ic.connectorId);
			} else {
				final AConnector c = scsConf.configuredConnectors.get(ic.connectorId);
				if (c != null && c instanceof Indexer) {
					final IndexingProcWatcher ipw = getWatcher(ic);
					if (ipw.answersPing()) {
						ipw.killIndexer();
						if (!waitStop(ipw, ic, TimeUnit.MINUTES.toMillis(2))) throw new IllegalStateException("Failed to kill indexing process");
					}
				}
			}
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	@Override
	public void close() {
		rwLock.writeLock().lock();
		try {
			LOG.info("Stopping all indexer watchers");
			final Set<String> cids = runningIndexers.keySet();
			for (final String cid : cids) {
				final IndexingProcWatcher ipw = indexerWatchers.get(cid);
				final Thread ipwThread = runningIndexers.get(cid);
				LOG.info("Stopping Watcher #" + cid);
				ipw.stopWatcher(ipwThread);
			}
			runningIndexers.clear();
			indexerWatchers.clear();
		} finally {
			rwLock.writeLock().unlock();
		}
	}

}
