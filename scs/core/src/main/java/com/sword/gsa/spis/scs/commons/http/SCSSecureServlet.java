package com.sword.gsa.spis.scs.commons.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;

import org.apache.commons.httpclient.HttpMethod;
import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.utils.HexUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;

import com.sword.gsa.adminapi.gdatawrap.GSAClientBuilder;
import com.sword.gsa.spis.scs.commons.SCSContextListener;
import com.sword.gsa.spis.scs.gsaadmin.GSAConnectivityStatus;

public abstract class SCSSecureServlet extends SCSServlet {

	private static final String ADMIN_USERNAME = "SCSAdmin";

	private static final String BASIC_CHALLENGE_HEADER = "WWW-Authenticate";
	private static final String SCS_REALM_NAME = "Basic realm=\"SCS Authentication\"";
	private static final String BASIC_CHALLENGE_HEADER_RESP = "Authorization";
	private static final Pattern BASIC_HEADER_RE = Pattern.compile("^([^\\:]+)\\:(.+)$");

	private static final String AUTH_STATUS = "AuthStatus";
	private static final String AUTH_OK = "-68953";
	private static final String AUTH_RENEW = "-68954";

	private static final ReadWriteLock FAILED_AUTH_SYNC = new ReentrantReadWriteLock();
	private static final Map<String, Long> FAILED_AUTH = new HashMap<>();

	protected static final String CTX_ATTR_GSA_CONN_STATUS = "SCSSecureServletGCS";

	protected static final String SESSION_ATTR_USERNAME = "SCSSecureServletUsername";
	protected static final String SESSION_ATTR_GSA_CLIENT_BUILDER = "SCSSecureServletGCB";
	
	public static final ThreadSafeDateFormat CONF_FILE_DATES = new ThreadSafeDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

	private static final Digester HASHER;

	static {
		Digester d = null;
		try {
			d = new Digester(HashAlgorithm.SHA_256);
		} catch (final NoSuchAlgorithmException e) {
			LOG.error("Failed to initialize SHA_256 hasher", e);
		}
		HASHER = d;
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

		final ServletContext sc = req.getServletContext();
		final String adminPassword = (String) sc.getAttribute(SCSContextListener.ADMIN_USER_PWD_PARAM);

		LOG.info("Request to a secure page: " + req.getRequestURI());
		if (HASHER == null) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		if (StringUtils.isNullOrEmpty(adminPassword)) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		if(req.getMethod().toLowerCase().equals("options")){
			resp.setHeader("Access-Control-Allow-Origin","*");
			String requestedHeaders=req.getHeader("Access-Control-Request-Headers");
			resp.setHeader("Access-Control-Allow-Headers",requestedHeaders);
			String requestedMethods=req.getHeader("Access-Control-Request-Method");
			resp.setHeader("Access-Control-Allow-Methods",requestedMethods);
			resp.setStatus(HttpServletResponse.SC_OK);
			return;
		}
		if(req.getRequestURI().contains("/restconf/") ){
			resp.setHeader("Access-Control-Allow-Origin","*");
			final String ah = req.getHeader(BASIC_CHALLENGE_HEADER_RESP);
			if (StringUtils.isNullOrEmpty(ah)) {
				storeFailedAttempt(req);
				resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			} else {
				final String decAh = new String(Base64.decodeBase64(ah.substring(6)), StandardCharsets.UTF_8);
				final Matcher matcher = BASIC_HEADER_RE.matcher(decAh);
				if (matcher.find()) {

					final String u = matcher.group(1);
					final String p = matcher.group(2);

					String hp = null;
					if (!StringUtils.isNullOrEmpty(p)) hp = HexUtils.toHexString(HASHER.digest(p.getBytes(StandardCharsets.UTF_8)));


					if (ADMIN_USERNAME.equals(u) && adminPassword.equals(hp)) {
						LOG.info("Successfully authenticated as SCS local admin");
						final HttpSession session = req.getSession(true);
						session.setAttribute(AUTH_STATUS, AUTH_OK);
						session.setAttribute(SESSION_ATTR_USERNAME, u);
						super.service(req, resp);
					}  else {
						storeFailedAttempt(req);
						resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					}
				} else {
					storeFailedAttempt(req);
					resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				}
			}
			return;
		}

		final HttpSession session = req.getSession(true);
		if (session.isNew()) {
			LOG.debug("New secure session");
			clearSessionInfo(session);
			LOG.trace("New session; sending HttpBasic challenge");
			sendChallenge(req, resp, session, "0");
		} else {
			final Object ia = session.getAttribute(AUTH_STATUS);
			if (ia != null && AUTH_OK.equals(ia.toString())) {
				String u = (String) session.getAttribute(SESSION_ATTR_USERNAME);
				GSAClientBuilder gcb = (GSAClientBuilder) session.getAttribute(SESSION_ATTR_GSA_CLIENT_BUILDER);
				LOG.debug("Session is already authenticated: " + u + " - " + (gcb == null));
				if (ADMIN_USERNAME.equals(u)) {
					ensureFreshGsaConnectivityStatus();
					super.service(req, resp);
				} else {
					if (gcb==null || conf==null || conf.gsa==null || conf.gsa.adminHost==null || !conf.gsa.adminHost.equals(gcb.host)) {
						LOG.info("GSA client needs to be renewed - forcing reauthentication");
						session.setAttribute(AUTH_STATUS, null);
						clearSessionInfo(session);
						sendChallenge(req, resp, session, "0");
					} else {
						ensureFreshGsaConnectivityStatus();
						super.service(req, resp);
					}
				}
			} else if (ia != null && AUTH_RENEW.equals(ia.toString())) {
				LOG.debug("Session renewal was requested");
				session.setAttribute(AUTH_STATUS, null);
				clearSessionInfo(session);
				sendChallenge(req, resp, session, "0");
			} else {
				clearSessionInfo(session);
				LOG.debug("Anonymous session: " + (ia == null ? "null" : ia.toString()));
				final String ah = req.getHeader(BASIC_CHALLENGE_HEADER_RESP);
				if (StringUtils.isNullOrEmpty(ah)) {
					storeFailedAttempt(req);
					resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				} else {
					final String decAh = new String(Base64.decodeBase64(ah.substring(6)), StandardCharsets.UTF_8);
					final Matcher matcher = BASIC_HEADER_RE.matcher(decAh);
					if (matcher.find()) {

						final String u = matcher.group(1);
						final String p = matcher.group(2);

						String hp = null;
						if (!StringUtils.isNullOrEmpty(p)) hp = HexUtils.toHexString(HASHER.digest(p.getBytes(StandardCharsets.UTF_8)));

						final GSAConnectivityStatus gcs = ensureFreshGsaConnectivityStatus();

						GSAClientBuilder gcb = null;
						if (ADMIN_USERNAME.equals(u) && adminPassword.equals(hp)) {
							LOG.info("Successfully authenticated as SCS local admin");
							session.setAttribute(AUTH_STATUS, AUTH_OK);
							createSessionInfo(session, ADMIN_USERNAME, null);
							super.service(req, resp);
						} else if ((gcb = checkGsaAuthent(gcs, u, p)) != null) {
							session.setAttribute(AUTH_STATUS, AUTH_OK);
							createSessionInfo(session, u, gcb);
							super.service(req, resp);
						} else if (ia != null) {
							int i = -1;
							try {
								i = Integer.parseInt(ia.toString());
							} catch (final NumberFormatException e) {}
							LOG.debug("Challenge response #" + i);
							if (i >= 0) {
								storeFailedAttempt(req);
								if (i > 5) {
									resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
								} else {
									sendChallenge(req, resp, session, Integer.toString(i + 1));
									return;
								}
							} else {
								storeFailedAttempt(req);
								resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
							}
						} else {
							storeFailedAttempt(req);
							resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
						}
					} else {
						storeFailedAttempt(req);
						resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					}
				}
			}
		}
	}

	private static void storeFailedAttempt(HttpServletRequest req) {
		FAILED_AUTH_SYNC.writeLock().lock();
		try {
			String ra = req.getRemoteAddr();
			if (!FAILED_AUTH.containsKey(ra)) FAILED_AUTH.put(ra, 0L);
			long fac = FAILED_AUTH.get(ra);
			FAILED_AUTH.put(ra, fac + 1L);
		} finally {
			FAILED_AUTH_SYNC.writeLock().unlock();
		}
	}

	private GSAConnectivityStatus ensureFreshGsaConnectivityStatus() {
		synchronized (CTX_ATTR_GSA_CONN_STATUS) {
			final ServletContext sc = getServletContext();
			GSAConnectivityStatus gcs = (GSAConnectivityStatus) sc.getAttribute(CTX_ATTR_GSA_CONN_STATUS);
			if (!GSAConnectivityStatus.isValid(gcs, conf)) {
				gcs = new GSAConnectivityStatus(conf);
				sc.setAttribute(CTX_ATTR_GSA_CONN_STATUS, gcs);
			}
			return gcs;
		}
	}

	private final static void setSessionInfo(final HttpSession session, final String user, final GSAClientBuilder gcb, final boolean syncAccess) {
		if (syncAccess) synchronized (SESSION_ATTR_USERNAME) {
			_setSessionInfo(session, user, gcb);
		}
		else _setSessionInfo(session, user, gcb);
	}

	private final static void _setSessionInfo(final HttpSession session, final String user, final GSAClientBuilder gcb) {
		session.setAttribute(SESSION_ATTR_USERNAME, user);
		session.setAttribute(SESSION_ATTR_GSA_CLIENT_BUILDER, gcb);
	}

	private static void clearSessionInfo(final HttpSession session) {
		setSessionInfo(session, null, null, true);
	}

	private static void createSessionInfo(final HttpSession session, final String username, final GSAClientBuilder gcb) {
		setSessionInfo(session, username, gcb, true);
	}

	private GSAClientBuilder checkGsaAuthent(final GSAConnectivityStatus gcs, final String u, final String p) {
		LOG.info("Testing authentication against GSA");
		if (gcs.isConfigured && gcs.canBeBound) {
			LOG.info("GSA can be bound - attempting authentication");
			try {
				final GSAClientBuilder gcb = new GSAClientBuilder(conf.gsa.ssl, conf.gsa.adminHost, conf.gsa.ssl ? 8443 : 8000, u, p, pwdMgr);
				gcb.buildGSAClient(pwdMgr, 10_000);
				LOG.info("Success");
				return gcb;
			} catch (final Exception e) {
				LOG.warn("Failed to establish a connection to GSA " + conf.gsa.adminHost, e);
				return null;
			}
		} else {
			LOG.info("Invalid GSA status: " + gcs.isConfigured + " - " + gcs.canBeBound);
			return null;
		}
	}

	private static void sendChallenge(final HttpServletRequest req, final HttpServletResponse resp, final HttpSession session, final String authStatus) throws IOException {
		String ra = req.getRemoteAddr();
		final long failedCount;
		FAILED_AUTH_SYNC.readLock().lock();
		try {
			failedCount = FAILED_AUTH.containsKey(ra) ? FAILED_AUTH.get(ra) : 0L;
		} finally {
			FAILED_AUTH_SYNC.readLock().unlock();
		}
		if (failedCount > 0L) {
			final Object o = new Object();
			synchronized (o) {
				try {
					o.wait((1 << failedCount) * 1000L);
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			}
		}
		session.setAttribute(AUTH_STATUS, authStatus);
		resp.setHeader("Access-Control-Allow-Origin","*");
		resp.addHeader(BASIC_CHALLENGE_HEADER, SCS_REALM_NAME);
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

	protected static void clearSession(final HttpServletRequest req) {
		final HttpSession session = req.getSession(true);
		session.setAttribute(AUTH_STATUS, AUTH_RENEW);
	}

	/**
	 * {@link File#setLastModified(long)} does not work on Linux for some reason - add some content to web.xml to make sure context is reloaded
	 * 
	 * @throws IOException
	 */
	protected void reloadConfig() throws IOException {
		final String webFilePath = getServletContext().getRealPath("/WEB-INF/web.xml");
		final File webFile = new File(webFilePath);

		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		Files.copy(webFile.toPath(), os);
		
		final String dateString = CONF_FILE_DATES.format(new Date());

		String contents = new String(os.toByteArray(), StandardCharsets.UTF_8);
		if (contents.contains("<!-- Updated on ")) contents = contents.replaceFirst("<!-- Updated on .+? -->", "<!-- Updated on " + dateString + " -->");
		else contents = contents.replaceFirst("<listener>", "<!-- Updated on " + dateString + " -->\n\t<listener>");

		Files.copy(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), webFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

	}

}
