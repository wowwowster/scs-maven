package com.sword.gsa.spis.scs.push;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import org.apache.log4j.Logger;

import sword.common.utils.dates.DateUtils;

import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.connector.threading.ExploringTask;
import com.sword.gsa.spis.scs.push.databases.PushDBConnection;
import com.sword.gsa.spis.scs.push.databases.PushTableManager;
import com.sword.gsa.spis.scs.push.monitoring.Statistics;
import com.sword.gsa.spis.scs.push.throwables.ReachedIndexingEndTime;
import com.sword.gsa.spis.scs.push.throwables.StopRequestedExternally;

public final class PushManager implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(PushManager.class);

	private final PushProcessSharedObjectsStore sos;

	PushManager(final Indexer connector, final PushConfig confStore, final SCSConfiguration scsConf, final Date pushStartTime, final long rate) throws SecurityException, ReflectiveOperationException, SQLException, IOException {
		sos = new PushProcessSharedObjectsStore(connector, confStore, scsConf, pushStartTime, rate);
		Statistics.INSTANCE.isInitialIndexing.set(sos.isInitialPush);
	}

	public int run() {
		
		try {
			
			if (sos.pushConf.pushType == PushType.BROWSE_DB) {
				
				try {
					synchronized (sos.monitor.dbBrowser.dbBrowsingEndNotifier) { sos.monitor.dbBrowser.dbBrowsingEndNotifier.wait(); }
					return 0;
				} catch (InterruptedException ex) {
					return 0;
				}
				
			} else {

				final StringBuilder message = new StringBuilder("Source system data retrieval start. ");
				if (sos.isInitialPush) message.append("Retrieving all documents.");
				else message.append("Retrieving documents modified since " + DateUtils.RFC822_DATE_FORMAT.format(sos.lastPushDate));
				LOG.info(message);

				Throwable error = null;

				LOG.debug("Process will use " + sos.pushConf.maxIndexingThreads + " indexing threads.");
				final ExploringTask et;
				try {
					et = new ExploringTask(sos);
				} catch (Exception ex) {
					LOG.error("Failed to initialize explorer:", ex);
					return PushInitializer.RC_INITIALIZATION_ERROR;
				}

				try {
					et.run();
				} catch (final Throwable t) {
					error = t;
				}

				final int explorationErrors = Statistics.INSTANCE.explorationErrors.get();
				final int indexingErrors = Statistics.INSTANCE.indexingErrors.get();
				// If no fatal error thrown and some recoverable errors - return a specific status code
				if (error == null && (explorationErrors > 0 || indexingErrors > 0)) {
					LOG.error(String.format("Process raised errors and need to be re-executed to recover from these errors: \n\t- Exploration errors: %d \n\t- Indexing errors: %d", explorationErrors, indexingErrors));
					return PushInitializer.RC_RECOVERABLE_ERRORS;
				}

				if (error == null) {
					LOG.info("Process completed without error.");

					try (PushTableManager ptm = new PushTableManager(sos.pushConf)) {

						LOG.trace("Setting last push date.");
						ptm.setLastPushDate(sos.pushStartTime);
						ptm.notifyPushCompletion();
						Statistics.INSTANCE.endTime.set(System.currentTimeMillis());

					} catch (final Throwable e) {
						LOG.error("Could not update internal DB: ", e);
						return PushInitializer.RC_MISC_RUNTIME_ERROR;
					}

					return 0;
				} else if (error instanceof StopRequestedExternally) {
					LOG.error("Received stop request - aborting");
					return PushInitializer.RC_RECEIVED_STOP_REQUEST;
				} else if (error instanceof ReachedIndexingEndTime) {
					LOG.error("Reached end of indexing period - aborting");
					return PushInitializer.RC_REACHED_INDEXING_PERIOD_END;
				} else {
					LOG.error("Exploration was interrupted because of the following error:", error);
					return PushInitializer.RC_MISC_RUNTIME_ERROR;
				}
				
			}
			
		} finally {
			try {
				PushDBConnection.shutdownDerby(sos.pushConf);
			} catch (Exception e) {
				LOG.warn("Failed to shutdown Derby DB:", e);
			}
		}

	}

	@Override
	public void close() throws Exception {
		sos.close();
	}

	public static boolean hasReceivedStopRequest(final IConnectorContext cc, final String instanceId) {
		return cc.getIndexerStopFile(instanceId).toFile().exists();
	}

	public static boolean hasReachedEndOfSchedule(final long endTime, final long currentTime) {
		if (endTime > 0) return currentTime > endTime - TimeUnit.MINUTES.toMillis(2);
		return false;
	}
}
