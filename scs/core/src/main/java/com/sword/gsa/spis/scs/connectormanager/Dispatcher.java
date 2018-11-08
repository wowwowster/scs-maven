package com.sword.gsa.spis.scs.connectormanager;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sword.common.utils.StringUtils;

import com.sword.gsa.spis.scs.commons.http.ErrorManager;
import com.sword.gsa.spis.scs.commons.http.SCSServlet;
import com.sword.gsa.spis.scs.connectormanager.workers.Authenticate;
import com.sword.gsa.spis.scs.connectormanager.workers.GetConnectorConfigToEdit;
import com.sword.gsa.spis.scs.connectormanager.workers.GetConnectorInstanceList;
import com.sword.gsa.spis.scs.connectormanager.workers.GetConnectorList;
import com.sword.gsa.spis.scs.connectormanager.workers.GetConnectorLogLevel;
import com.sword.gsa.spis.scs.connectormanager.workers.GetConnectorStatus;
import com.sword.gsa.spis.scs.connectormanager.workers.GetFeedLogLevel;
import com.sword.gsa.spis.scs.connectormanager.workers.LazyWorker;
import com.sword.gsa.spis.scs.connectormanager.workers.TestConnectivity;
import com.sword.gsa.spis.scs.connectormanager.workers.XMLWorker;

public class Dispatcher extends SCSServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected boolean checkConfig(final ServletConfig config) {
		return true;
	}

	/**
	 * Override SAMLSPIServlet.service(ServletRequest, ServletResponse) to let configuration management servlets reconfigure connectors.
	 */
	@Override
	public void service(final ServletRequest req, final ServletResponse resp) throws IOException {
		service((HttpServletRequest) req, (HttpServletResponse) resp);
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {

			final String ai = getServletContext().getInitParameter("AllowedIP");
			final String ra = req.getRemoteAddr();

			final ServletContext ctx = getServletContext();
			String ru = req.getRequestURI();
			if (ru == null) ru = "";
			LOG.debug("Processing Connector Manager request: " + ru + " from " + ra + " (" + ai + ")");
			if (!checkIP(ai, ra)) {
				ErrorManager.processError(req, resp, HttpServletResponse.SC_UNAUTHORIZED, "Who are you?");
				return;
			}

			XMLWorker xw = null;
			if (ru.matches(".+/(cm|connector\\-manager)/+authenticate")) xw = new Authenticate(conf, ctx, mappings);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorConfigToEdit")) xw = new GetConnectorConfigToEdit(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorInstanceList")) xw = new GetConnectorInstanceList(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorList")) xw = new GetConnectorList(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getFeedLogLevel")) xw = new GetFeedLogLevel(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorLogLevel")) xw = new GetConnectorLogLevel(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorLogs")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorStatus")) xw = new GetConnectorStatus(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+get(?:FeedLogs|TeedFeedFile)")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+setManagerConfig")) xw = new LazyWorker();
			else if (ru.matches(".+/(cm|connector\\-manager)/+testConnectivity")) xw = new TestConnectivity(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConfigForm")) xw = new GetConnectorConfigToEdit(conf, ctx);
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConfig(?:uration)?")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+getSchema")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+getConnectorServingStatus")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+restartConnectorTraversal")) xw = new LazyWorker();
			else if (ru.matches(".+/(cm|connector\\-manager)/+setSchedule")) xw = new LazyWorker();
			else if (ru.matches(".+/(cm|connector\\-manager)/+authorization")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+HelloClientFromGSA")) ;
			else if (ru.matches(".+/(cm|connector\\-manager)/+startUp")) ;
			// else if (ru.matches(".+/(cm|connector\\-manager)/+removeConnector")) xw = new RemoveConnector(this.conf, ctx);
			if (xw != null) {
				final String respStr = xw.getXMLResponse(req);
				if (LOG.isTraceEnabled()) LOG.trace("Returning XML response: " + respStr);
				final byte[] response = respStr.getBytes(StandardCharsets.UTF_8);
				try (final BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream())) {
					resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
					resp.setContentLength(response.length);
					resp.setContentType("text/xml");
					bos.write(response);
				}
			} else {
				LOG.error("Unknown request: " + ru);
				ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request: " + ru);
			}
		} catch (final Exception e) {
			LOG.error("An error occured", e);
			ErrorManager.processError(req, resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured", e);
		}
	}

	/**
	 * see <a href="http://en.wikipedia.org/wiki/CIDR_notation">CIDR notation</a>
	 */
	private static boolean checkIP(final String ai, final String ra) {
		if (StringUtils.isNullOrEmpty(ai)) return true;
		try {
			final String[] aiComp = ai.split("/");
			final InetAddress ip = InetAddress.getByName(aiComp[0]);
			int size;
			if (ip instanceof Inet4Address) size = 32;
			else size = 128;
			BigInteger ipRef = new BigInteger(ip.getAddress());
			final int shift = Math.max(size - Integer.parseInt(aiComp[1]), 0);
			ipRef = ipRef.shiftRight(shift);

			BigInteger ip2check = new BigInteger(InetAddress.getByName(ra).getAddress());
			ip2check = ip2check.shiftRight(shift);

			return ip2check.compareTo(ipRef) == 0;
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	//
	// public static void main(String[] args) {
	// System.out.println(checkIP("10.0.0.0/8", "10.50.21.1"));
	// System.out.println(checkIP("10.0.0.0/8", "10.10.2.51"));
	// System.out.println(checkIP("10.0.0.0/8", "10.10.2.50"));
	// }
}
