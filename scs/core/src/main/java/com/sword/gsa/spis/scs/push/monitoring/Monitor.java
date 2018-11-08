package com.sword.gsa.spis.scs.push.monitoring;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import sword.common.utils.StringUtils;
import sword.common.utils.streams.StreamUtils;

import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.databases.DBBrowser;

public final class Monitor implements AutoCloseable {

	static final Logger LOG = Logger.getLogger(Monitor.class);

//	static {
//		LOG.setLevel(Level.ALL);
//		LOG.addAppender(new ConsoleAppender(new PatternLayout("%d{dd MMM yyyy HH:mm:ss,SSS} %p %t %c - %m%n")));
//	}

	// private final Date creationDate;
	private final PushConfig conf;
	private final ServerSocket listener;
	private final File linkFile;
	public final File historyFile;
	private final File lastStatBackupFile;
	private final BlockingQueue<Socket> socketQueue = new ArrayBlockingQueue<>(12);
	private final ExecutorService execSvc = Executors.newCachedThreadPool();
	private final ScheduledExecutorService schedSvc = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean closed = new AtomicBoolean(false);
	public final DBBrowser dbBrowser;

	public Monitor(final PushConfig c) throws IOException, ClassNotFoundException, SQLException {

		// creationDate = new Date();
		conf = c;
		listener = new ServerSocket(0);
		final int lp = listener.getLocalPort();
		LOG.info("Starting Monitor thread on port " + lp);
		linkFile = getMonPortFile(conf.connectorCtx, conf.connectorId);
		try {
			Files.copy(new ByteArrayInputStream(Integer.toString(lp).getBytes(StandardCharsets.UTF_8)), linkFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception e) {
			LOG.error("Could create monitor port file", e);
		}
		historyFile = getHistoryFile(conf.connectorCtx, conf.connectorId);
		lastStatBackupFile = getStatsBackupFile(conf.connectorCtx, conf.connectorId);

		if (c.pushType == PushType.BROWSE_DB) {
			dbBrowser = new DBBrowser(conf);
		} else {
			dbBrowser = null;
			schedSvc.scheduleAtFixedRate(new SaveHistory(historyFile), 1, 1, TimeUnit.MINUTES);
			schedSvc.scheduleAtFixedRate(new SaveLastStats(lastStatBackupFile), 5, 180, TimeUnit.SECONDS);
		}

		execSvc.execute(new IncomingRequestListener(listener, closed, socketQueue));
		execSvc.execute(new IncomingRequestConsumer(execSvc, closed, socketQueue, historyFile, conf, dbBrowser));

	}

	@Override
	public void close() throws Exception {

		closed.set(true);

		execSvc.shutdown();
		schedSvc.shutdown();
		if (listener != null) try {
			listener.close();
		} // Closes the IncomingRequestListener
		catch (final Throwable ignored) {}// Should throw a SocketException since listener in blocking on Socket#accept

		try (FakeSocket fs = new FakeSocket()) {
			socketQueue.put(fs);// Closes the IncomingRequestConsumer
		}
		execSvc.awaitTermination(2L, TimeUnit.MINUTES);
		schedSvc.awaitTermination(1L, TimeUnit.MINUTES);

		if (linkFile != null && linkFile.exists() && !linkFile.delete()) linkFile.deleteOnExit();

		if (conf.pushType == PushType.BROWSE_DB) {
			LOG.info("Closing monitor.");
			if (dbBrowser != null) dbBrowser.close();
		} else {
			LOG.info("Closing monitor and saving last known statistics: " + Statistics.INSTANCE.toString());
			new SaveHistory(historyFile).run();
			new SaveLastStats(lastStatBackupFile).run();
		}
	}

	public void clearHistory() {
		synchronized (historyFile) {
			if (historyFile.exists()) {
				if (!historyFile.delete()) {
					LOG.info("Failed to delete history");
					historyFile.deleteOnExit();
				}
			}
		}
	}

	public static File getMonPortFile(final IConnectorContext cc, final String instanceId) {
		return getDashboardFile(cc, instanceId, "port");
	}

	public static File getHistoryFile(final IConnectorContext cc, final String instanceId) {
		return getDashboardFile(cc, instanceId, "history");
	}

	public static File getStatsBackupFile(final IConnectorContext cc, final String instanceId) {
		return getDashboardFile(cc, instanceId, "last-stats");
	}

	public static File getDashboardFile(final IConnectorContext cc, final String instanceId, String filename) {
		return cc.getIndexerDashboardDir(instanceId).resolve(filename).toFile();
	}

	public static SerStats reloadStats(final IConnectorContext cc, final String instanceId) {
		final File lsb = getStatsBackupFile(cc, instanceId);
		if (lsb.exists()) {
			try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(lsb))) {
				return (SerStats) ois.readObject();
			} catch (Exception e) {
				return null;
			}
		} else return null;
	}

	public static List<String> reloadHistory(final IConnectorContext cc, final String instanceId) {
		final File lkh = getHistoryFile(cc, instanceId);
		return lkh.exists() ? History.reloadHistory(lkh) : null;
	}

	public static boolean answersPing(final IConnectorContext cc, final String instanceId, final Logger logger) {
		logger.debug("Checking if connector answers ping requests");
		System.out.println("[SCS Indexer #" + instanceId + "] Checking if connector answers ping requests");
		final int monPort;
		try {
			monPort = getMonitorPort(cc, instanceId);
		} catch (IOException e) {
			logger.debug("Monitor port not found - Unable to ping connector: ", e);
			System.out.println("[SCS Indexer #" + instanceId + "] Monitor port not found - Unable to ping connector: ");
			e.printStackTrace(System.out);
			return false;
		}
		if (monPort > 0) {
			logger.debug("Last known monitor port: " + monPort + " - sending ping request");
			System.out.println("[SCS Indexer #" + instanceId + "] Last known monitor port: " + monPort + " - sending ping request");
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /ping.action HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				final byte[] buf = new byte[4096];
				final int r = socketIs.read(buf);
				final String resp = new String(buf, 0, r, StandardCharsets.UTF_8);
				logger.debug("Connector answered ping request with " + resp);
				System.out.println("[SCS Indexer #" + instanceId + "] Connector answered ping request with " + resp);
				if (StringUtils.npeProofEquals(resp, instanceId)) {
					logger.debug("Connector is running");
					return true;
				} else {
					logger.debug("Connector is not running");
					return false;
				}
			} catch (Exception e) {
				logger.debug("Ping request failed: ", e);
				System.out.println("[SCS Indexer #" + instanceId + "] Ping request failed: ");
				e.printStackTrace(System.out);
				return false;
			}
		} else {
			logger.debug("Monitor port not found - Unable to ping connector.");
			System.out.println("[SCS Indexer #" + instanceId + "] Monitor port not found - Unable to ping connector.");
			return false;
		}
	}

	public static void killIndexer(final IConnectorContext cc, final String instanceId, final Logger logger) {
		logger.debug("Killing indexer");
		System.out.println("[SCS Indexer #" + instanceId + "] Killing indexer");
		final int monPort;
		try {
			monPort = getMonitorPort(cc, instanceId);
			if (monPort > 0) {
				logger.debug("Last known monitor port: " + monPort + " - sending termination request");
				System.out.println("[SCS Indexer #" + instanceId + "] Last known monitor port: " + monPort + " - sending termination request");
				try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
					socketOs.write("GET /kill.action HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
					socketOs.flush();
				} catch (Exception e) {
					logger.debug("Termination request failed: ", e);
					System.out.println("[SCS Indexer #" + instanceId + "] Termination request failed: ");
					e.printStackTrace(System.out);
					return;
				}
			} else {
				logger.debug("Monitor port not found - Unable to kill indexer.");
				System.out.println("[SCS Indexer #" + instanceId + "] Monitor port not found - Unable to kill indexer.");
			}
		} catch (IOException e) {
			logger.debug("Monitor port not found - Unable to kill indexer: ", e);
			System.out.println("[SCS Indexer #" + instanceId + "] Monitor port not found - Unable to kill indexer: ");
			e.printStackTrace(System.out);
		}
	}

	public static boolean isDBBrowsingMode(final IConnectorContext cc, final String instanceId) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /dbbrowsingmode HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				StreamUtils.transferBytes(socketIs, baos, false);
				return Boolean.parseBoolean(new String(baos.toByteArray(), StandardCharsets.UTF_8));
			}
		} else {
			return false;
		}
	}

	public static void stopDBBrowsing(final IConnectorContext cc, final String instanceId) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /internaldb?action=stop HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
			}
		}
	}

	public static void commitDBBrowsingChanges(final IConnectorContext cc, final String instanceId) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /internaldb?action=commit HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
			}
		}
	}

	public static void getIndexerStats(final IConnectorContext cc, final String instanceId, final OutputStream os) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /Stats.json HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				StreamUtils.transferBytes(socketIs, os, false);
			}
		}
	}

	public static void getIndexerHistory(final IConnectorContext cc, final String instanceId, final OutputStream os) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write("GET /History.json HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				StreamUtils.transferBytes(socketIs, os, false);
			}
		}
	}

	public static void getDBChildNodes(final IConnectorContext cc, final String instanceId, final OutputStream os, final String parentNodeId, final int pageSize, final int page, final String states) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write(String.format(
					"GET /internaldb?action=listchildren%s&num=%d&page=%d%s HTTP/1.0 \r\n\r\n", 
					StringUtils.isNullOrEmpty(parentNodeId) ? ("") : ("&parent=" + URLEncoder.encode(parentNodeId, "UTF-8")), 
					pageSize, 
					page, 
					StringUtils.isNullOrEmpty(states) ? ("") : ("&states=" + URLEncoder.encode(states, "UTF-8"))
				).getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				StreamUtils.transferBytes(socketIs, os, false);
			}
		}
	}

	public static void reloadDBNodeState(final IConnectorContext cc, final String instanceId, final OutputStream os, final String nodeId, final boolean isDir, final boolean exclude) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write(String.format(
					"GET /internaldb?action=reloadstate&nid=%s&isdir=%d&exclude=%d HTTP/1.0 \r\n\r\n", 
					nodeId, 
					isDir ? 1 : 0, 
					exclude ? 1 : 0
				).getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				StreamUtils.transferBytes(socketIs, os, false);
			}
		}
	}

	public static void findDBNode(final IConnectorContext cc, final String instanceId, final OutputStream os, final String parentNodeId, final String nodeId) throws IOException {
		final int monPort = getMonitorPort(cc, instanceId);
		if (monPort > 0) {
			try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
				socketOs.write(String.format(
					"GET /internaldb?action=find%s&nid=%s HTTP/1.0 \r\n\r\n", 
					StringUtils.isNullOrEmpty(parentNodeId) ? ("") : ("&parent=" + URLEncoder.encode(parentNodeId, "UTF-8")), 
					nodeId
				).getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				StreamUtils.transferBytes(socketIs, os, false);
			}
		}
	}

	private static int getMonitorPort(final IConnectorContext cc, final String instanceId) throws IOException {
		final File monPortFile = getMonPortFile(cc, instanceId);
		int monitorPort = -1;
		if (monPortFile.exists()) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			Files.copy(monPortFile.toPath(), os);
			final String monitorFileContents = new String(os.toByteArray(), StandardCharsets.UTF_8);
			if (StringUtils.isInteger(monitorFileContents)) monitorPort = Integer.parseInt(monitorFileContents);
			else monitorPort = -1;
		} else monitorPort = -1;
		return monitorPort;
	}

}
