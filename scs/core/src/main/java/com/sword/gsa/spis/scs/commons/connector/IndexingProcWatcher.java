package com.sword.gsa.spis.scs.commons.connector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sword.springboard.CloudSource;
import org.apache.log4j.Logger;

import sword.common.utils.EnvUtils;
import sword.common.utils.dates.DateUtils;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.common.utils.runtime.RuntimeUtils;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.indexing.IndexerConf;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.push.PushInitializer;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.monitoring.Monitor;
import com.sword.scs.Constants;

public class IndexingProcWatcher implements Runnable {

	private static final Logger LOG = Logger.getLogger(IndexingProcWatcher.class);

	private final AtomicBoolean stop = new AtomicBoolean(false);
	private final AtomicBoolean dbBrowsingMode = new AtomicBoolean(false);
	private final AtomicBoolean forceStart = new AtomicBoolean(false);
	private final Object waitLock = new Object();
	private final IndexerConf indexerConf;
	private final boolean sslGsa;
	private final String gsaHost;
	private final String javaExecutablePath;
	private final String mainJarFilePath;
	private final Path tomcatRootPath;
	private final IConnectorContext connectorCtx;
	private final String connectorId;
	private final String connectorNS;
	private final String envPath;
	private  final boolean ftpMode;
	private final String ftpUsername;
	private final String ftpPassword;
	private final Map<String, CloudSource> cloudSources;

	public long lastRunAttemptEndTime = -1L;


	
	IndexingProcWatcher(final SCSConfiguration scsConf, final IndexerConf indexerConf, final AConnector c, final IConnectorContext cc, final String envPath) {
		this.indexerConf = indexerConf;
		if(scsConf.gsa!=null){
		this.sslGsa = scsConf.gsa.ssl;
		this.gsaHost = scsConf.gsa.defaultHost;
		}else{
			this.sslGsa=false;
			this.gsaHost=scsConf.ftpHost;
		}
		this.ftpMode=scsConf.ftpMode;
		this.ftpUsername=scsConf.ftpUsername;
		this.ftpPassword=scsConf.ftpPassword;
		this.cloudSources=scsConf.configuredCloudSources;
		//this.javaExecutablePath = scsConf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_JAVA).toString();
		this.javaExecutablePath = "java";
	//	this.mainJarFilePath = scsConf.scsCtx.tomcatRoot.resolve("webapps_SCS_WEB-INF_lib_scs-core.jar".replace('_', File.separatorChar)).toString();
		this.tomcatRootPath = scsConf.scsCtx.tomcatRoot;
		this.connectorCtx = cc;
		this.mainJarFilePath = connectorCtx.getHomeDir().toString()+"_lib_core.jar".replace('_', File.separatorChar);
		this.connectorId = c.uniqueId;
		this.connectorNS = c.namespace;
		this.envPath = envPath;
	}

	@Override
	public void run() {

		while (!stop.get()) {

			boolean dbBrowsingMode = this.dbBrowsingMode.get();
			this.dbBrowsingMode.set(false);

			boolean forceStart = this.forceStart.get();
			this.forceStart.set(false);
			
			boolean checkSchedule = !(dbBrowsingMode || forceStart);

			final long nowMillis = System.currentTimeMillis();

			long isInEffectForHowLong = Schedule.NOT_EFFECTIVE_NOW;
			if (checkSchedule && (indexerConf.schedule != null)) isInEffectForHowLong = indexerConf.schedule.howLongWillRemainEffective(nowMillis);

			if (isInEffectForHowLong == Schedule.ALWAYS_EFFECTIVE) {
				LOG.warn("Not starting Indexer #" + indexerConf.connectorId + " due to permanent interruption schedule");
				return;
			} else if (isInEffectForHowLong == Schedule.NOT_EFFECTIVE_NOW) {
				long nextTimeWillBeInEffect = -1L;
				if (checkSchedule) {
					if (indexerConf.schedule != null) nextTimeWillBeInEffect = indexerConf.schedule.getNextTimeWillBeInEffect(nowMillis);
					if (nextTimeWillBeInEffect == Schedule.NEVER_EFFECTIVE) nextTimeWillBeInEffect = -1L;
				}

				int rc = -1;

				if (checkSchedule) {
					final Path nextStartFilePath = connectorCtx.getIndexerNextStartFile(connectorId);
					final File nextStartFile = nextStartFilePath.toFile();
					if (nextStartFile.exists()) {
						try {
							final ByteArrayOutputStream os = new ByteArrayOutputStream();
							Files.copy(nextStartFilePath, os);
							final long nextStartTime = Long.parseLong(new String(os.toByteArray(), StandardCharsets.UTF_8));
							final long now = System.currentTimeMillis();
							if (now < nextStartTime) {
								LOG.info("Indexing process is scheduled for restart in " + DateUtils.toReadableTimeSpan(nextStartTime - now));
								try {
									synchronized (waitLock) { waitLock.wait(nextStartTime - now); }
									if (stop.get()) return;
								} catch (final InterruptedException ie) {
									if (stop.get()) return;
									else throw ie;
								}
								
								forceStart = this.forceStart.get();
								this.forceStart.set(false);
								dbBrowsingMode = this.dbBrowsingMode.get();
								this.dbBrowsingMode.set(false);
								checkSchedule = !(dbBrowsingMode || forceStart);
								
							}
						} catch (final Exception e) {
							LOG.warn("Failed to check next start time: ", e);
						}
					}

					if (!dbBrowsingMode) deleteNextStartFile();
				}
				
				if (dbBrowsingMode) LOG.info("Starting DB browsing process for indexer #" + indexerConf.connectorId);
				else LOG.info("Starting indexing process for connector #" + indexerConf.connectorId);
				File endorsedDir = connectorCtx.getConnectorEndorsedDir().toFile();
				List<String> args = new ArrayList<>();
				args.add(javaExecutablePath);
				if (EnvUtils.isX64(tomcatRootPath.resolve(Constants.JDK_DIR_NAME))) {
					args.add("-Xmx3G");
				} else {
					args.add("-Xmx1400M");
				}
				if (endorsedDir.exists() && endorsedDir.isDirectory()) {
					args.add("-Djava.endorsed.dirs=\"" + endorsedDir.getAbsolutePath() + "\"");
				}
				try {
					final PushConfig pc = PushConfig.readConfiguration(connectorCtx, connectorId, sslGsa, gsaHost, ftpMode, ftpUsername, ftpPassword, PushType.UPDATE, connectorNS, connectorId, -1, false);
					for (String jo: pc.javaOptions) args.add(jo);
				} catch (Exception e) {
					lastRunAttemptEndTime = System.currentTimeMillis();
					LOG.warn("Unable to read indexer config: ", e);
					return;
				}
				args.add("-jar");
				args.add(mainJarFilePath);
				args.add(PushInitializer.ARG_TOMCAT_ROOT);
				args.add(tomcatRootPath.toString());
				args.add(PushInitializer.ARG_CONNECTOR_HOME);
				args.add(connectorCtx.getHomeDir().toString());
				args.add(PushInitializer.ARG_CONNECTOR_ID);
				args.add(connectorId);
				args.add(PushInitializer.ARG_SSL_GSA);
				args.add(Boolean.toString(sslGsa));
				args.add(PushInitializer.ARG_GSA);
				args.add(gsaHost);
				args.add(PushInitializer.ARG_FTP_MODE);
				args.add(Boolean.toString(ftpMode));
				args.add(PushInitializer.ARG_FTP_USERNAME);
				args.add(ftpUsername);
				args.add(PushInitializer.ARG_FTP_PASSWORD);
				args.add(ftpPassword);
				args.add(PushInitializer.ARG_PUSH_RATE);
				args.add(Long.toString(indexerConf.throughput));
				args.add(PushInitializer.ARG_END_TIME);
				args.add(nextTimeWillBeInEffect > 0 ? Long.toString(nextTimeWillBeInEffect) : "-1");
				if (dbBrowsingMode) {
					args.add(PushInitializer.ARG_PUSH_MODE);
					args.add(PushType.BROWSE_DB.toString());
				}

				try {
					LOG.info(Arrays.toString(args.toArray()));
					final ProcessBuilder pb = RuntimeUtils.getProcessBuilderWithPath(args.toArray(new String[args.size()]), connectorCtx.getInstanceHomeDir(connectorId).toFile(), envPath);
					final File log = getProcStdOut(tomcatRootPath, connectorId).toFile();
					pb.redirectErrorStream(true);
					pb.redirectOutput(Redirect.appendTo(log));
					final Process proc = pb.start();

					try {

						rc = proc.waitFor();
						lastRunAttemptEndTime = System.currentTimeMillis();
						LOG.info("Indexing process exited with code " + rc);

					} catch (final InterruptedException e) {// proc.waitFor interrupted before actual process end
						if (stop.get()) return;
						else {
							try {
								rc = waitIndexerEnd();
								lastRunAttemptEndTime = System.currentTimeMillis();
							} catch (InterruptedException e1) {
								lastRunAttemptEndTime = System.currentTimeMillis();
								if (stop.get()) return;
							}
						}
					}
				} catch (final IOException e) {
					lastRunAttemptEndTime = System.currentTimeMillis();
					LOG.warn("Failed to start Indexer #" + indexerConf.connectorId + ": ", e);
					return;
				}

				if (rc == PushInitializer.RC_ALREADY_RUNNING) {
					LOG.info("Indexer #" + indexerConf.connectorId + " did not start because it is already running - waiting for process to complete");
					try {
						rc = waitIndexerEnd();
					} catch (InterruptedException e1) {
						if (stop.get()) return;
					}
				}

				if ((rc == PushInitializer.RC_INITIALIZATION_ERROR) || (rc == PushInitializer.RC_INVALID_ARGS) || (rc == PushInitializer.RC_INVALID_CONF)) {
					LOG.warn("Failed to start Indexer #" + indexerConf.connectorId + " - connector could not be initialized: " + rc);
					return;
				} else if (rc == PushInitializer.RC_REACHED_INDEXING_PERIOD_END) {
					LOG.info("Indexer #" + indexerConf.connectorId + " stopped due to its indexing schedule");
				} else if (rc == PushInitializer.RC_RECEIVED_STOP_REQUEST) {
					LOG.info("Indexer #" + indexerConf.connectorId + " because it received a STOP request");
					return;
				} else if ((rc == PushInitializer.RC_RECOVERABLE_ERRORS) || (rc == PushInitializer.RC_MISC_RUNTIME_ERROR)) {
					if (rc == PushInitializer.RC_RECOVERABLE_ERRORS) LOG.info("Indexer #" + indexerConf.connectorId + " completed with some recoverable errors");
					else LOG.info("Indexer #" + indexerConf.connectorId + " failed due to an unknown error");
					if (!stop.get()) {
						if (rc == PushInitializer.RC_RECOVERABLE_ERRORS) LOG.info("Restarting in 10 minutes");
						else LOG.info("Restarting in 45 minutes");
						try {
							synchronized (waitLock) { waitLock.wait(TimeUnit.MINUTES.toMillis((rc == PushInitializer.RC_RECOVERABLE_ERRORS) ? 10L : 45L)); }
						} catch (final InterruptedException ie) {
							if (stop.get()) return;
							else LOG.error(ie);
						}
					}
				} else if (rc == 0) {
					if (dbBrowsingMode) {
						LOG.info("DB Browsing completed");
					} else {
						LOG.info("Indexer #" + indexerConf.connectorId + " completed with no error");
						storeNextStartTime();
					}
				} else if (rc == -20) {
					if (dbBrowsingMode) {
						LOG.info("DB Browsing completed with an unknown status");
					} else {
						LOG.info("Indexer #" + indexerConf.connectorId + " completed with an unknown status");
						storeNextStartTime();
					}
				} else {
					LOG.warn("Indexer #" + indexerConf.connectorId + " returned an unknown code: " + rc);
					return;
				}

			} else {
				LOG.warn("Not starting Indexer #" + indexerConf.connectorId + " due to interruption schedule");
				try {
					synchronized (waitLock) { waitLock.wait(isInEffectForHowLong); }
				} catch (final InterruptedException ie) {
					if (stop.get()) return;
					else LOG.error(ie);
				}
			}

		}
	}

	private void storeNextStartTime() {
		final long interval = indexerConf.interval > 0 ? indexerConf.interval : TimeUnit.MINUTES.toMillis(10);
		final long now = System.currentTimeMillis();

		try {
			Files.copy(new ByteArrayInputStream(Long.toString(now + interval).getBytes(StandardCharsets.UTF_8)), connectorCtx.getIndexerNextStartFile(connectorId), StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			LOG.info("Could not store next start time: ", e);
		}
	}

	private void deleteNextStartFile() {
		final File nsf = connectorCtx.getIndexerNextStartFile(connectorId).toFile();
		if (nsf.exists() && !nsf.delete()) nsf.deleteOnExit();;
	}

	public void wake() {
		synchronized (waitLock) { waitLock.notifyAll(); }
	}

	private int waitIndexerEnd() throws InterruptedException {
		final Object o = new Object();
		LOG.info("Starting to wait for indexing process to end");
		final long startWait = System.currentTimeMillis();
		while (true) {
			synchronized (o) { o.wait(TimeUnit.MINUTES.toMillis(2L)); }
			if (!Monitor.answersPing(connectorCtx, connectorId, LOG)) {
				synchronized (o) { o.wait(TimeUnit.SECONDS.toMillis(5L)); }//Make sure process is stopped (monitor is closed a few milliseconds before the process actually finishes)
				final Path stdOutPath = getProcStdOut(tomcatRootPath, connectorId);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				try {
					Files.copy(stdOutPath, os);
				} catch (Exception e) {
					LOG.info("Indexing process ended but the standard output could not be read: ", e);
					return -21;
				}
				String stdOut = new String(os.toByteArray());
				Matcher m = Pattern.compile("Exiting with code \\{([0-9]+)\\} at \\{([0-9]+)\\}").matcher(stdOut);
				if (m.find()) {
					int rc = Integer.parseInt(m.group(1));
					long at = Long.parseLong(m.group(2));
					LOG.info("Indexing process ended at " + new Date(at) + " with code " + rc);
					if (at > startWait) {
						return rc;
					} else {
						return -20;
					}
				} else {
					LOG.info("Indexing process standard output does not have the expected format");
					return -20;
				}
			}
		}
	}

	public void stopWatcher(final Thread watcherThread) {
		LOG.info("Notifying stop request");
		stop.set(true);
		wake();
		final Object o = new Object();
		synchronized (o) { try { o.wait(250); } catch (InterruptedException ignored) { } }
		LOG.info("Checking thread state: " + (watcherThread != null && watcherThread.isAlive()));
		if (watcherThread != null && watcherThread.isAlive()) {
			LOG.info("Thread is alive - interrupting");
			watcherThread.interrupt();
		}
	}

	public void setForcedStart() { forceStart.set(true); }
	public void enableDBBrowsingMode() { dbBrowsingMode.set(true); }

	public void requestIndexerStop() throws IOException {
		connectorCtx.getIndexerStopFile(connectorId).toFile().createNewFile();
	}

	public void killIndexer() {
		stop.set(true);
		Monitor.killIndexer(connectorCtx, connectorId, LOG);
	}

	public boolean answersPing() {
		return Monitor.answersPing(connectorCtx, connectorId, LOG);
	}

	public boolean isDBBrowsingMode() throws IOException {
		return Monitor.isDBBrowsingMode(connectorCtx, connectorId);
	}

	public void clearState() throws IOException {
		Files.walkFileTree(connectorCtx.getIndexerWorkDir(connectorId), new FileTreeDeleter());
		Files.walkFileTree(connectorCtx.getIndexerDashboardDir(connectorId), new FileTreeDeleter());
		Files.walkFileTree(connectorCtx.getIndexerDBDir(connectorId), new FileTreeDeleter());
		Files.walkFileTree(connectorCtx.getIndexerFeedDir(connectorId), new FileTreeDeleter());
	}

	private static Path getProcStdOut(final Path tomcatRootPath, final String connectorId) {
		return tomcatRootPath.resolve(String.format("logs/indexer-%s-stdout.log", connectorId));
	}

}