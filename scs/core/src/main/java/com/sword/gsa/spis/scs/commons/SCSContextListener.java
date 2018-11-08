package com.sword.gsa.spis.scs.commons;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;

import javax.naming.ConfigurationException;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import sword.common.security.PwdManager;
import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.HttpParams;
import com.sword.gsa.spis.scs.commons.connector.ConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.GroupCacheManager;
import com.sword.gsa.spis.scs.commons.connector.IndexingProcMgr;
import com.sword.gsa.spis.scs.commons.connector.TransformedNamesCacheMgr;
import com.sword.gsa.spis.scs.gsaadmin.HostnameVerifier;
import com.sword.scs.Constants;
import com.sword.scs.utils.LicMgr;
import com.sword.scs.utils.LicMgr.LicDate;

/**
 * See servlet specification for more information about servlet contexts.
 */
public class SCSContextListener implements ServletContextListener {

	private static final String LIC_PARAM = "License";

	public static final String ADMIN_USER_PWD_PARAM = "AdministrationPassword";
	public static final String HTTP_HOST_PARAM_NAME = "SCS_HTTP_DEFAULT_HOST";
	public static final String LIC_PARAM_NAME = "SCS_CONNECTOR_LIC";
	public static final String CONF_PARAM_NAME = "SCS_CONNECTOR_CONFIG";
	public static final String NAME_TRANSFORMER_CACHE_PARAM_NAME = "SCS_MAPPER_CACHE";
	public static final String GROUP_CACHE_LOADER_PARAM_NAME = "SCS_GCL";
	public static final String INDEXER_MGR_PARAM_NAME = "SCS_INDEXER_MGR";
	public static final String PASSWORD_MANAGER_PARAM_NAME = "SCS_PASSWORD_MANAGER";

	public static final Logger LOG = Logger.getLogger(SCSContextListener.class);

	private static final Collection<AutoCloseable> SD_HOOKS = new HashSet<>();

	public static void addShutdownHook(final AutoCloseable c) {
		synchronized (SD_HOOKS) {
			SD_HOOKS.add(c);
		}
	}

	@Override
	public void contextInitialized(final ServletContextEvent ce) {
		LOG.info("Initializing SCS");
		try {

			final ServletContext ctx = ce.getServletContext();
			synchronized (ctx) {

				final String license = ctx.getInitParameter(LIC_PARAM);
				try {
					LicDate licDate = LicMgr.checkLicense(license);
					ctx.setAttribute(LIC_PARAM_NAME, licDate);
					if (!licDate.isValid) {
						LOG.error("Invalid license:" + licDate.dateString);
						ctx.setAttribute(CONF_PARAM_NAME, SCSConfiguration.invalidConf());
						return;
					}
				} catch (final Throwable t) {
					LOG.error("License check threw exception:" + license);
					ctx.setAttribute(LIC_PARAM_NAME, LicDate.UNKNOWN_ERROR);
					ctx.setAttribute(CONF_PARAM_NAME, SCSConfiguration.invalidConf());
					return;
				}

				final String root = System.getProperty("catalina.home", "nada");
				if ("nada".equals(root)) throw new ConfigurationException("CATALINA_HOME environment variable is not set");

				@SuppressWarnings("resource")
				final SCSContext gc = new SCSContext(root);
				
				final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				final XPath xpath = XPathFactory.newInstance().newXPath();
				final Document serverConf = db.parse(gc.tomcatRoot.resolve("conf_server.xml".replace('_', File.separatorChar)).toFile());
				final String hostname = xpath.evaluate("/Server/Service/Engine/@defaultHost", serverConf);
				final String httpPortStr = xpath.evaluate("/Server/Service/Connector[1]/@port", serverConf);
				final String httpsPortStr = xpath.evaluate("/Server/Service/Connector[2]/@port", serverConf);
				ctx.setAttribute(HTTP_HOST_PARAM_NAME, new HttpParams(hostname, Integer.parseInt(httpPortStr), Integer.parseInt(httpsPortStr)));

				final Path confFile = gc.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
				LOG.info("Reading configuration from: " + confFile);

				String aup = ctx.getInitParameter(ADMIN_USER_PWD_PARAM);
				if (StringUtils.isNullOrEmpty(aup)) aup = null;
				ctx.setAttribute(ADMIN_USER_PWD_PARAM, aup);

				final SCSConfiguration scsConf = SCSConfiguration.readConfig(confFile, gc);
				ctx.setAttribute(CONF_PARAM_NAME, scsConf);
				ctx.setAttribute(NAME_TRANSFORMER_CACHE_PARAM_NAME, new TransformedNamesCacheMgr());
				ctx.setAttribute(PASSWORD_MANAGER_PARAM_NAME, PWD_MGR);
				
				if (scsConf.gsa != null || scsConf.ftpMode) {
					//TODO handle cert for ftp ?
					if (scsConf.gsa != null)HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(gc.tomcatRoot.resolve(Constants.REL_PATH_CACERTS), scsConf.gsa));

					LOG.info("Starting cache loaders");
					@SuppressWarnings("resource")
					final GroupCacheManager gcm = new GroupCacheManager(scsConf);
					ctx.setAttribute(GROUP_CACHE_LOADER_PARAM_NAME, gcm);
					LOG.info("Cache loaders started");

					final ByteArrayOutputStream os = new ByteArrayOutputStream();
					//JMT Files.copy(gc.tomcatRoot.resolve(Constants.REL_PATH_SCS_BIN).resolve(Constants.ENVPATH_STORE_FILE), os);
					@SuppressWarnings("resource")
					final IndexingProcMgr ipm = new IndexingProcMgr(scsConf, new String(os.toByteArray(), StandardCharsets.UTF_8));
					LOG.info("Starting indexers");
					ipm.startAllIndexers();
					ctx.setAttribute(INDEXER_MGR_PARAM_NAME, ipm);
					
				} else {
					LOG.info("No GSA configured yet - not starting indexers or cache loaders");
				}

				if (LOG.isDebugEnabled()) {
					final StringBuilder sb = new StringBuilder("Available connectors: ");
					for (final ConnectorContext cc : scsConf.scsCtx.installedConnectors)
						sb.append(String.format("%s\t- Connector %s version %s", EnvUtils.CR, cc.getSpec().name(), cc.getSpec().version()));
					LOG.debug(sb.toString());
				}
			}
		} catch (final Throwable t) {
			LOG.error("Eror configuring WebApp - please review configuration.", t);
		}
	}

	@Override
	public void contextDestroyed(final ServletContextEvent ce) {
		LOG.info("Destroying connector context");

		final ServletContext ctx = ce.getServletContext();
		try (final GroupCacheManager gcm = (GroupCacheManager) ctx.getAttribute(GROUP_CACHE_LOADER_PARAM_NAME)) {}
		try (final IndexingProcMgr ipm = (IndexingProcMgr) ctx.getAttribute(INDEXER_MGR_PARAM_NAME)) {}

		for (final AutoCloseable c : SD_HOOKS)
			try {
				c.close();
			} catch (final Exception e) {
				LOG.info("Shutdown hook exception:", e);
			}

		try {
			final Object co = ctx.getAttribute(CONF_PARAM_NAME);
			if ((co!=null) && (co instanceof SCSConfiguration)) {
				final SCSContext scsCtx = ((SCSConfiguration) co).scsCtx;
				if (scsCtx != null) scsCtx.close();
			}
		} catch (final Exception e) {
			LOG.error(e);
		}

	}

	private static final PwdManager PWD_MGR;

	static {
		PwdManager pm = null;
		try {
			pm = new PwdManager();
		} catch (final Exception e) {
			pm = null;
		}
		PWD_MGR = pm;
	}
}
