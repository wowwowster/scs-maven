package com.sword.gsa.spis.scs.commons.http;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import sword.common.security.PwdManager;
import sword.common.utils.StringUtils;

import com.sword.gsa.spis.scs.commons.SCSContextListener;
import com.sword.gsa.spis.scs.commons.config.HttpParams;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.GroupCacheManager;
import com.sword.gsa.spis.scs.commons.connector.IndexingProcMgr;
import com.sword.gsa.spis.scs.commons.connector.TransformedNamesCacheMgr;
import com.sword.scs.utils.LicMgr.LicDate;

/**
 * Abstract HttpServlet that forces configuration to be verified before any work can be done.
 */
public abstract class SCSServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	protected static final Logger LOG = Logger.getLogger(SCSServlet.class);

	protected HttpParams httpDefaultHost = null;
	protected SCSConfiguration conf = null;
	protected TransformedNamesCacheMgr mappings = null;
	protected PwdManager pwdMgr = null;
	protected GroupCacheManager gcm = null;
	protected IndexingProcMgr ipm = null;
	
	protected boolean active = false;
	protected boolean licenseProblem = true;
	private Exception error = null;

	/**
	 * Retrieves a {@link ConfigHolder} instance and passes it to the abstract method {@link SCSServlet#checkConfig(ServletConfig)}
	 */
	@Override
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);
		final ServletContext sc = config.getServletContext();
		try {
			LicDate licDate = null;
			synchronized (sc) {
				licDate = (LicDate) sc.getAttribute(SCSContextListener.LIC_PARAM_NAME);
				httpDefaultHost = (HttpParams) sc.getAttribute(SCSContextListener.HTTP_HOST_PARAM_NAME);
				conf = (SCSConfiguration) sc.getAttribute(SCSContextListener.CONF_PARAM_NAME);
				mappings = (TransformedNamesCacheMgr) sc.getAttribute(SCSContextListener.NAME_TRANSFORMER_CACHE_PARAM_NAME);
				pwdMgr = (PwdManager) sc.getAttribute(SCSContextListener.PASSWORD_MANAGER_PARAM_NAME);
				gcm = (GroupCacheManager) sc.getAttribute(SCSContextListener.GROUP_CACHE_LOADER_PARAM_NAME);
				ipm = (IndexingProcMgr) sc.getAttribute(SCSContextListener.INDEXER_MGR_PARAM_NAME);
			}
			
			licenseProblem = (licDate==null) || !licDate.isValid;
			if (licenseProblem) {
				active = false;
			} else {
				if (SCSConfiguration.isInvalidConf(conf)) throw new IllegalStateException("InitializationError - configuration was not set");
				active = checkConfig(config);
				if (active) LOG.debug("Configuration is valid ; continuing");
			}
		} catch (final Exception e) {
			error = e;
			active = false;
		}
	}

	/**
	 * Checks whether all the configuration parameters the servlet needs to run properly are present.
	 */
	protected abstract boolean checkConfig(ServletConfig config) throws Exception;

	/**
	 * Checks whether configuration was properly read and returns an 'internal-error' page if configuration is invalid.
	 */
	@Override
	public void service(final ServletRequest req, final ServletResponse resp) throws ServletException, IOException {
		final HttpServletRequest hreq = (HttpServletRequest) req;
		LOG.info("Processing request: " + hreq.getMethod() + " " + hreq.getRequestURI().toString());
		LOG.debug("Range: " + hreq.getHeader("Range"));
		if (licenseProblem) {
			LOG.error("SCS not ready, invalid license; refusing request");
			ErrorManager.processError(hreq, (HttpServletResponse) resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Invalid license");
			return;
		} else if (!active) {
			if (error == null) LOG.error("SCS not ready, configuration error; refusing request");
			else LOG.error("SCS not ready, configuration error; refusing request", error);
			ErrorManager.processError(hreq, (HttpServletResponse) resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service not initialized.");
			return;
		}
		super.service(req, resp);
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		String m = req.getMethod();
		m = StringUtils.isNullOrEmpty(m) ? "" : m.toLowerCase();

		String r = req.getHeader("Range");
		r = StringUtils.isNullOrEmpty(r) ? "" : r.toLowerCase();

		if ("head".equals(m) || ("get".equals(m) && "bytes=0-0".equals(r))) {
			LOG.error("Refusing HEAD request: " + req.getRequestURI());
			ErrorManager.processError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Head requests not allowed.");
		} else super.service(req, resp);
	}

}
