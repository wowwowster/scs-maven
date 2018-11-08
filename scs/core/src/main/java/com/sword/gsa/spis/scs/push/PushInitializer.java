package com.sword.gsa.spis.scs.push;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Date;

import javax.naming.ConfigurationException;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sword.gsa.spis.scs.commons.SCSContext;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import sword.common.utils.StringUtils;
import sword.common.utils.files.FileLockingDisposableHandles;
import sword.common.utils.files.FileUtils;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.ConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.gsaadmin.HostnameVerifier;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.monitoring.Monitor;
import com.sword.scs.Constants;

public final class PushInitializer implements AutoCloseable {

	// 01x: errors
	public static final int RC_INVALID_ARGS = 13;
	public static final int RC_MISC_RUNTIME_ERROR = 15;
	public static final int RC_INVALID_CONF = 16;
	public static final int RC_INITIALIZATION_ERROR = 17;
	public static final int RC_ALREADY_RUNNING = 18;
	// 00x: non-errors
	public static final int RC_RECOVERABLE_ERRORS = 2;
	public static final int RC_RECEIVED_STOP_REQUEST = 5;
	public static final int RC_REACHED_INDEXING_PERIOD_END = 6;

	public static final String ARG_TOMCAT_ROOT = "/root";
	public static final String ARG_CONNECTOR_HOME = "/home";
	public static final String ARG_CONNECTOR_ID = "/cid";
	public static final String ARG_SSL_GSA = "/sslgsa";
	public static final String ARG_GSA = "/gsa";
	public static final String ARG_PUSH_MODE = "/mode";
	public static final String ARG_PUSH_RATE = "/rate";
	public static final String ARG_END_TIME = "/end";
	public static final String ARG_FTP_MODE = "/ftp";
	public static final String ARG_FTP_USERNAME = "/ftpuser";
	public static final String ARG_FTP_PASSWORD = "/ftppass";
	private final File rootDir;

	public static void main(final String[] args) {

		if (args == null || args.length == 0) {
			exitWithCode(RC_INVALID_ARGS);
			return;
		}

		final int al = args.length;
		String rootDir = null;
		String homeDir = null;
		String cid = null;
		boolean sslGsa = false;
		String gsa = null;
		String ftpUsername = null;
		String ftpPassword= null;
		String pushTypeStr = null;
		String rateStr = null;
		String endTimeStr = null;
		boolean ftpMode = false;
		try {
			for (int i = 0; i < al; i++) {
				final String _switch = args[i];

				if (ARG_TOMCAT_ROOT.equals(_switch)) rootDir = args[++i];
				else if (ARG_CONNECTOR_HOME.equals(_switch)) homeDir = args[++i];
				else if (ARG_CONNECTOR_ID.equals(_switch)) cid = args[++i];
				else if (ARG_SSL_GSA.equals(_switch)) sslGsa = Boolean.parseBoolean(args[++i]);
				else if (ARG_GSA.equals(_switch)) gsa = args[++i];
				else if (ARG_PUSH_MODE.equals(_switch)) pushTypeStr = args[++i];
				else if (ARG_PUSH_RATE.equals(_switch)) rateStr = args[++i];
				else if (ARG_END_TIME.equals(_switch)) endTimeStr = args[++i];
				else if (ARG_FTP_MODE.equals(_switch)) ftpMode = Boolean.parseBoolean(args[++i]);
				else if (ARG_FTP_USERNAME.equals(_switch)) ftpUsername = args[++i];
				else if (ARG_FTP_PASSWORD.equals(_switch)) ftpPassword = args[++i];
				else {
					System.err.println("Unknown switch: " + _switch);
					i++;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
			exitWithCode(RC_INVALID_ARGS);
			return;
		}

		if (StringUtils.hasOneNullOrEmpty(new String[] {rootDir, homeDir, cid, gsa})) {
			exitWithCode(RC_INVALID_ARGS);
			return;
		}

		final File rootDirFile = new File(rootDir);
		if (!(rootDirFile.exists() && rootDirFile.isDirectory())) {
			System.err.println("Invalid directory path: " + rootDir);
			exitWithCode(RC_INVALID_ARGS);
			return;
		}

		final File homeDirFile = new File(homeDir);
		if (!(homeDirFile.exists() && homeDirFile.isDirectory())) {
			System.err.println("Invalid directory path: " + homeDir);
			exitWithCode(RC_INVALID_ARGS);
			return;
		}

		System.setProperty("scs.home", rootDirFile.getAbsolutePath());
		System.setProperty("indexer.id", cid);
		System.setProperty("log4j.configuration", "file:///" + homeDirFile.getAbsolutePath().replace('\\', '/') + "/log4j.properties");

//		System.setProperty("derby.storage.pageSize", "16384");
		System.setProperty("derby.storage.pageCacheSize", "8000");

		PushType pt = PushType.UPDATE;
		if (!StringUtils.isNullOrEmpty(pushTypeStr)) pt = PushType.lookupName(pushTypeStr);

		long endTime = -1;
		try {
			if (!StringUtils.isNullOrEmpty(endTimeStr)) endTime = Long.parseLong(endTimeStr);
		} catch (final NumberFormatException nfe) {
			endTime = -1;
		}

		long rate = -1L;
		try {
			if (!StringUtils.isNullOrEmpty(rateStr)) rate = Long.parseLong(rateStr);
		} catch (final NumberFormatException nfe) {
			rate = -1;
		}

		int rc = -1;
		try {
			System.out.println("[SCS Indexer #" + cid + "] Start requested");
			try (PushInitializer pm = new PushInitializer(rootDirFile, homeDirFile, cid, sslGsa, gsa, ftpMode, ftpUsername, ftpPassword)) {
				if (pm.isAnyOtherProcessRunning()) {
					rc = RC_ALREADY_RUNNING;
				} else {
					System.out.println("[SCS Indexer #" + cid + "] Starting connector");
					rc = pm.start(pt, endTime, rate);
					System.out.println("[SCS Indexer #" + cid + "] Connector completed");
					System.out.println("[SCS Indexer #" + cid + "] Releasing lock");
				}
			}
			System.out.println("[SCS Indexer #" + cid + "] Completed with status: " + rc);
			System.out.println();
		} catch (final Throwable e) {
			System.out.println("[SCS Indexer #" + cid + "] Unhandled exception:");
			e.printStackTrace(System.out);
			rc = RC_MISC_RUNTIME_ERROR;
		}

		exitWithCode(rc);

	}
	
	public static void exitWithCode(final int code) {
		System.out.println(String.format("Exiting with code {%d} at {%s}", code, Long.toString(System.currentTimeMillis())));
		System.exit(code);
	}
	
	private final Logger log = Logger.getLogger(PushInitializer.class);
	private final Path scsConf;
	private final ConnectorContext connectorCtx;
	private final String connectorId;
	private final boolean sslGsa;
	private final String gsaHost;
	private final String ftpUsername;
	private final String ftpPassword;
	private final FileLockingDisposableHandles flHandles;
	private boolean ftpMode;

	private PushInitializer(final File rootDir, final File homeDir, final String cid, final boolean sslGsa, final String gsa, boolean ftpMode, String ftpUsername, String ftpPassword) throws InterruptedException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		this.rootDir = rootDir;
//		this.homeDir = homeDir;
		this.scsConf = rootDir.toPath().resolve(Constants.REL_PATH_SCS_CONF_FILE);
		this.connectorCtx = new ConnectorContext(homeDir.toPath());
		this.connectorId = cid;
		this.sslGsa = sslGsa;
		this.gsaHost = gsa;
		this.ftpMode=ftpMode;
		this.ftpUsername=ftpUsername;
		this.ftpPassword=ftpPassword;
		this.flHandles = FileUtils.getReadWriteLock(connectorCtx.getIndexerLockFile(connectorId).toFile(), true, -1);
		if(!ftpMode)HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(rootDir.toPath().resolve(Constants.REL_PATH_CACERTS), gsaHost, gsaHost));
	}
	
	private boolean isAnyOtherProcessRunning() {
		System.out.println("[SCS Indexer #" + connectorId + "] Checking if connector is already running");
		if (flHandles.obtainedLock()) {
			System.out.println("[SCS Indexer #" + connectorId + "] Acquired lock - checking if connector answers ping request");
			boolean ap = Monitor.answersPing(connectorCtx, connectorId, log);
			if (ap) System.out.println("[SCS Indexer #" + connectorId + "] Connector is running");
			else System.out.println("[SCS Indexer #" + connectorId + "] Connector is NOT running");
			return ap;
		} else {
			System.out.println("[SCS Indexer #" + connectorId + "] Could not acquire lock - connector is running");
			return true;
		}
	}

	public int start(final PushType pushType, final long endTime, final long rate) {

		final Date start = new Date(System.currentTimeMillis());
		log.info("Starting indexer");

		Indexer connector;
		try {
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(scsConf.toFile());
			final XPath xpath = XPathFactory.newInstance().newXPath();
			final Element rootNode = doc.getDocumentElement();
			final Node connectorNode = (Node) xpath.evaluate(String.format("%s[@%s='%s']", SCSConfiguration.TAG_OR_ATTR_CONNECTOR, SCSConfiguration.ATTRNAME_ID, connectorId), rootNode, XPathConstants.NODE);
			connector = (Indexer) SCSConfiguration.loadConnectorInstance(log, xpath, (Element) connectorNode, connectorId, connectorCtx.getClassName(), connectorCtx);
		} catch (XPathExpressionException | ConfigurationException | SAXException | IOException | ParserConfigurationException | ReflectiveOperationException e) {
			log.error("Configuration could not be read ; exiting", e);
			return RC_INITIALIZATION_ERROR;
		}

		final PushConfig conf;
		final SCSConfiguration scsConf;
		try {
			conf = PushConfig.readConfiguration(connectorCtx, connectorId, sslGsa, gsaHost, ftpMode, ftpUsername, ftpPassword, pushType, ((AConnector) connector).namespace, connectorId, endTime, true);
			log.debug("PushConfiguration successfully read.");
			final String root = System.getProperty("scs.home", "nada");
			if ("nada".equals(root)) throw new ConfigurationException("CATALINA_HOME environment variable is not set");
			File rootDirFile = new File(root);
			String scsDir = rootDirFile.toPath().resolve(Constants.SCS_WORK_DIR_NAME).toString();
			System.out.println("Reading SCSconfiguration from "+ scsDir);
			@SuppressWarnings("resource")
			final SCSContext gc = new SCSContext(root);
			final Path confFile = gc.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
			log.info("Reading SCSconfiguration from: " + confFile);
			scsConf = SCSConfiguration.readConfig(confFile, gc);
		} catch (final Exception e) {
			log.error("Configuration could not be read ; exiting", e);
			return RC_INVALID_CONF;
		}

		final File stopFile = connectorCtx.getIndexerStopFile(connectorId).toFile();
		if (stopFile.exists() && !stopFile.delete()) stopFile.deleteOnExit();

		int rc = -1;

		log.info("Intialization completed - starting...");
		try (PushManager pm = new PushManager(connector, conf, scsConf, start, rate)) {
			rc = pm.run();
			log.info("Indexer returned with status " + rc);
		} catch (final Exception e) {
			log.info("Indexer threw an exception - returning with status " + RC_MISC_RUNTIME_ERROR);
			log.error("Unknown error: ", e);
			return RC_MISC_RUNTIME_ERROR;
		}

		return rc;
	}

	@Override
	public void close() throws Exception {// Releases lock
		connectorCtx.close();
		flHandles.close();
	}

}
