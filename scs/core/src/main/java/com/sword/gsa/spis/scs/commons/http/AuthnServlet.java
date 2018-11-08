package com.sword.gsa.spis.scs.commons.http;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import javax.naming.AuthenticationException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import sword.common.utils.StringUtils;
import sword.common.utils.zip.DeflateUtil;
import sword.gsa.xmlfeeds.builder.acl.Group;

import com.sword.gsa.spis.scs.commons.SCSContextListener;
import com.sword.gsa.spis.scs.commons.acl.cache.FailedCache;
import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.acl.cache.NullCache;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.authn.AuthenticatorConf;
import com.sword.gsa.spis.scs.commons.config.authn.GroupRetrieverConf;
import com.sword.gsa.spis.scs.commons.config.authn.SamlMode;
import com.sword.gsa.spis.scs.commons.connector.GroupCacheManager;
import com.sword.gsa.spis.scs.commons.connector.TransformedNamesCacheMgr;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNDelegator;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNFormData;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNHeaders;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthenticator;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.IGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.INameTransformer;
import com.sword.gsa.spis.scs.commons.krb.KerberosChallenge;
import com.sword.gsa.spis.scs.commons.throwables.FailGroupRetrieval;
import com.sword.gsa.spis.scs.commons.utils.HttpUtils;
import com.sword.gsa.spis.scs.saml.SAMLRequestParser;
import com.sword.gsa.spis.scs.saml.SAMLResponseBuilder;
import com.sword.gsa.spis.scs.saml.SAMLSPIConstants;
import com.sword.gsa.spis.scs.saml.authent.PostBindingRequestSigner;
import com.sword.gsa.spis.scs.saml.authent.SAMLCache;
import com.sword.gsa.spis.scs.saml.authent.SAMLCache.CachedUser;

public class AuthnServlet extends SCSServlet {

	private static final long serialVersionUID = 1L;

	private static final String LOGIN_PAGE_NAME = "login.html";
	private static final String POSTBINDING_PAGE = "<!DOCTYPE html>\n<html>\n<head>\n\n	<meta charset=\"UTF-8\">\n	<meta name=\"robots\" content=\"NOINDEX,NOFOLLOW\" >\n\n	<title>SAML Auto-login page</title>\n\n	<link rel=\"shortcut icon\" href=\"/favicon.ico\" type=\"image/x-icon\" >\n	<link rel=\"stylesheet\" href=\"/static/css/pages.css\" type=\"text/css\" >\n	<link rel=\"stylesheet\" href=\"/static/css/jquery-ui/jquery-ui.min.css\">\n\n	<script type=\"text/javascript\" src=\"/static/script/jquery.min.js\" ></script>\n\n	<script type=\"text/javascript\" >\nfunction init() { var imgSrc = $(\"#TheImg\").find(\"img\").attr(\"src\"); $(\"#TheForm\").submit(); $(\"#TheImg\").find(\"img\").attr(\"src\", imgSrc); }\n$(document).ready(init);\n	</script>\n</head>\n<body>\n	<noscript>\n		<p><strong>Note:</strong> JavaScript is blocked on you web browser - please press <i>Continue</i> to proceed.</p>\n	</noscript>\n	<form id=\"TheForm\" action=\"%s\" method=\"post\">\n		<div>\n			<input type=\"hidden\" name=\"RelayState\" value=\"\"/>\n			<input type=\"hidden\" name=\"SAMLResponse\" value=\"%s\"/>\n		</div>\n		<div id=\"TheImg\" style=\"text-align: center; margin-top: 10%%\" >\n			<img src=\"/static/img/progress.gif\" title=\"Authentication in progress\" alt=\"Authentication in progress\" />\n		</div>\n		<noscript>\n			<div><input type=\"submit\" value=\"Continue\" /></div>\n		</noscript>\n	</form>\n</body>\n</html>";

	@Override
	protected boolean checkConfig(final ServletConfig config) {
		return true;
	}

	/**
	 * Handles all authentication HTTP requests.
	 */
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {

			final AuthenticatorConf authnConf = conf.authnConf;
			if (authnConf == null) {
				LOG.warn("Refusing authentication request - No connector have been configured for authentication");
				ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No connector have been configured for authentication");
			} else {

				final IAuthenticator authnConnector = authnConf.authenticator;
				final boolean kerberosAuthN = authnConf.kerberosAuthN;
				LOG.info(String.format("Authentication request - Kerberos is %s ; Authentication connector is %s", kerberosAuthN ? "enabled" : "disabled", authnConnector == null ? "undefined" : authnConnector.getClass().getSimpleName()));

				if (!kerberosAuthN && authnConnector == null) {
					LOG.warn("Refusing authentication request - No connector have been configured for authentication");
					ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No connector have been configured for authentication");
				} else {

					final String sr = req.getParameter(AuthNStrings.SAML_REQUEST_HTTP_PARAM);
					final String rs = req.getParameter(AuthNStrings.RELAY_STATE_HTTP_PARAM);// Redirect Binding parameter
					final String noKrb = req.getParameter(AuthNStrings.KRB_FAILED_HTTP_PARAM);// Kerberos failed parameter

					final String nego = req.getHeader(AuthNStrings.HEADER_AUTHORIZATION);// Krb challenge header

					final String credU = req.getParameter(AuthNStrings.USERNAME_HTTP_PARAM);// POST from login page parameter
					final String credP = req.getParameter(AuthNStrings.PASSWORD_HTTP_PARAM);// POST from login page parameter
					final boolean isTwigkit = AuthNStrings.TWIGKIT_MODE.equals(req.getParameter(AuthNStrings.MODE_HTTP_PARAM));

					if (!(credU == null || credP == null)) {// User Posting his credential => authenticate user

						if (authnConnector == null) {
							LOG.warn("Refusing authentication request - No connector have been configured for forms authentication");
							ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No connector have been configured for forms authentication");
						} else if (authnConnector instanceof IAuthNFormData) {
							final String authenticatedUser = doUsernamePasswordLogin((IAuthNFormData) authnConnector, credU, credP);
							if (authenticatedUser == null) ErrorManager.processError(req, resp, HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
							else successfulLoginPostWork(req, resp, authenticatedUser, isTwigkit);
						} else {
							LOG.warn("Refusing authentication request - Authentication connector does not support form data authentication");
							ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Authentication connector does not support form data authentication");
						}

					} else if (nego != null) {// User answering a Kerberos Challenge => check header value

						LOG.debug("Kerberos negotiation request: " + nego);
						if (kerberosAuthN) {
							final String authenticatedUser = doKerberos(req, resp);
							if (authenticatedUser == null) {
								if (authnConnector != null) {
									if (authnConnector instanceof IAuthNFormData) doBuildLoginPage(req, resp);
									else if (authnConnector instanceof IAuthNDelegator) ((IAuthNDelegator) authnConnector).redirectToAuthNProvider(req, resp);
									else ErrorManager.processError(req, resp, HttpServletResponse.SC_FORBIDDEN, "No fallback authentication");
								} else ErrorManager.processError(req, resp, HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
								return;
							} else successfulLoginPostWork(req, resp, authenticatedUser, false);
						} else {
							LOG.warn("Refusing authentication request - Authentication connector does not support Kerberos authentication");
							ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Authentication connector does not support Kerberos authentication");
						}

					} else if (sr != null) {// Original request from the user ; create a referrer cookie and redirect to Kerberos, delegated authN provider or login form depending on configuration.

						LOG.debug("HTTP Post/Redirect Binding request");
						final String samlArt = SAMLResponseBuilder.generateType4Artifact(authnConf.samlEntityId);
						final Charset cs = HttpUtils.getRequestCharset(req);

						LOG.debug("Encoded AuthNRequest is: " + sr);
						final String decodedRequest = new String(DeflateUtil.inflate(Base64.decodeBase64(sr), true), StandardCharsets.UTF_8);
						LOG.debug("Decoded AuthNRequest is: " + decodedRequest);

						final Element doc = new SAMLRequestParser(cs).getDocFromString(decodedRequest);// Root element is
						final String consumer = doc.getAttribute("AssertionConsumerServiceURL");
						final String id = doc.getAttribute("ID");
						final String issueInstant = doc.getAttribute("IssueInstant");

						final Element firstTagWithName = SAMLRequestParser.getFirstTagWithName("Issuer", doc);
						final String issuer = firstTagWithName.getTextContent();
						LOG.debug(String.format("Service consumer: %s ; AuthN id: %s ; IssueInstant: %s ; AuthN issuer: %s", consumer, id, issueInstant, issuer));

						String target = consumer + "?SAMLart=" + URLEncoder.encode(samlArt, "UTF-8");
						if (!StringUtils.isNullOrEmpty(rs)) target += "&RelayState=" + URLEncoder.encode(rs, "UTF-8");
						LOG.debug("Target is: " + target);

						CookieManager.addLocalSessionCookie(req, resp, AuthNStrings.REFERER_COOKIE_NAME, target);
						CookieManager.addLocalSessionCookie(req, resp, AuthNStrings.ISSUER_COOKIE_NAME, issuer);
						CookieManager.addLocalSessionCookie(req, resp, AuthNStrings.SAMLID_COOKIE_NAME, id);
						CookieManager.addLocalSessionCookie(req, resp, AuthNStrings.SAMLDATE_COOKIE_NAME, issueInstant);

						// Ask for default authentication
						if (kerberosAuthN) KerberosChallenge.sendNegotiate(req, resp, true);
						else if (authnConnector == null) {
							LOG.warn("Refusing authentication request - No connector have been configured for non-Kerberos SAML authentication");
							ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No connector have been configured for non-Kerberos SAML authentication");
							return;
						} else if (authnConnector instanceof IAuthNDelegator) ((IAuthNDelegator) authnConnector).redirectToAuthNProvider(req, resp);
						else if (authnConnector instanceof IAuthNFormData) doBuildLoginPage(req, resp);
						else ErrorManager.processError(req, resp, HttpServletResponse.SC_UNAUTHORIZED, "No valid SAML authentication connector");
					} else if (noKrb != null) {// When sending WWW-Authenticate, a page that contains several buttons is built. If Kerberos works, the user does not have the time to see this page. If
						// not, the user sees the page and can choose between searching for public docs only or authenticate with a login form. This if statement checks what
						// the user asked for and takes corresponding action.
						LOG.debug("Kerberos unavailable for user; building a login page");
						if (authnConnector == null) {
							LOG.warn("Refusing authentication request - No connector have been configured for non-Kerberos SAML authentication");
							ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No connector have been configured for non-Kerberos SAML authentication");
							return;
						} else if (authnConnector instanceof IAuthNDelegator) ((IAuthNDelegator) authnConnector).redirectToAuthNProvider(req, resp);
						else if (authnConnector instanceof IAuthNFormData) doBuildLoginPage(req, resp);
						else if (authnConnector instanceof IAuthNHeaders) {
							final String authenticatedUser = ((IAuthNHeaders) authnConnector).authenticate(req, resp);
							if (authenticatedUser == null) resp.sendRedirect("error.htm");
							else successfulLoginPostWork(req, resp, authenticatedUser, true);
						} else ErrorManager.processError(req, resp, HttpServletResponse.SC_UNAUTHORIZED, "No valid authentication connector");
					} else if (authnConnector != null && authnConnector instanceof IAuthNDelegator && ((IAuthNDelegator) authnConnector).isFinalStep(req, resp)) {// User coming back from delegated
						final String authenticatedUser = ((IAuthNDelegator) authnConnector).authenticate(req, resp);
						if (authenticatedUser == null) resp.sendRedirect("error.htm");
						else successfulLoginPostWork(req, resp, authenticatedUser, false);
					} else if (authnConnector != null && authnConnector instanceof IAuthNHeaders) {
						final String authenticatedUser = ((IAuthNHeaders) authnConnector).authenticate(req, resp);
						if (authenticatedUser == null) resp.sendRedirect("error.htm");
						else successfulLoginPostWork(req, resp, authenticatedUser, true);
					} else ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request");
				}
			}
		} catch (final Throwable t) {
			final String mess = t.getClass().getName();
			LOG.error("Fatal Exception", t);
			ErrorManager.processError(req, resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mess, t);
			return;
		}
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		doGet(req, resp);
	}

	/**
	 * Authenticates supplied credential.
	 *
	 * @return authenticated user name or null if authentication failed
	 */
	public static String doUsernamePasswordLogin(final IAuthNFormData authN, final String username, final String password) {
		try {
			return authN.authenticate(username, password);
		} catch (final AuthenticationException e) {
			LOG.error(e.getMessage());
		} catch (final Exception e) {
			LOG.error("Authentication Exception: ", e);
		}
		return null;
	}

	public static Collection<Group> getUserGroups(final TransformedNamesCacheMgr mcm, final SCSConfiguration scsConf, final Map<String, AConnector> configuredConnectors, final String username, final ServletContext servletCtx) throws FailGroupRetrieval {
		final Collection<Group> allGroups = new HashSet<>();
		for (final GroupRetrieverConf grc : scsConf.groupRetrievers)
			allGroups.addAll(getUserGroupsForConnector(mcm, username, grc.connector, configuredConnectors, servletCtx));
		if (LOG.isDebugEnabled()) LOG.debug(new StringBuilder(username).append(" belongs to the following groups: ").append(allGroups).toString());
		return allGroups;
	}

	public static Collection<Group> getUserGroupsForConnector(final TransformedNamesCacheMgr mcm, final String username, final AConnector connector, final Map<String, AConnector> configuredConnectors, final ServletContext servletCtx) throws FailGroupRetrieval {
		final Collection<Group> groupCollection = new HashSet<>();
		try {
			String alternativeUsername = null;
			final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
			if (StringUtils.isNullOrEmpty(connector.nameTransformer)) alternativeUsername = username;
			else {
				final AConnector nameTransformer = configuredConnectors.get(connector.nameTransformer);
				try {
					Thread.currentThread().setContextClassLoader(nameTransformer.getClass().getClassLoader());
					alternativeUsername = mcm.getCachedMapping(nameTransformer, username);
				} finally {
					Thread.currentThread().setContextClassLoader(originalClassLoader);
				}
				if (alternativeUsername == null) {
					alternativeUsername = ((INameTransformer) nameTransformer).transformName(username);
					mcm.addCachedMapping(nameTransformer, username, alternativeUsername);
				}
			}

			try {
				Thread.currentThread().setContextClassLoader(connector.getClass().getClassLoader());

				GroupCache groupCache = null;
				if (connector instanceof ICachableGroupRetriever) {
					@SuppressWarnings("resource")
					final GroupCacheManager gcm = (GroupCacheManager) servletCtx.getAttribute(SCSContextListener.GROUP_CACHE_LOADER_PARAM_NAME);
					if (gcm != null) groupCache = gcm.getGroupCache(connector.uniqueId);
				}
				if (groupCache == null) {
					groupCollection.addAll(((IGroupRetriever) connector).getGroups(alternativeUsername, null));
				} else if (groupCache instanceof NullCache) {
					LOG.error("Unable to retrieve groups for connector #" + connector.uniqueId + " has the group cache has not been loaded yet");
				} else if (groupCache instanceof FailedCache) {
					LOG.error("Unable to retrieve groups for connector #" + connector.uniqueId + " has the group cache loading caused a repeating error: ", ((FailedCache)groupCache).error);
				} else {
					groupCollection.addAll(((IGroupRetriever) connector).getGroups(alternativeUsername, groupCache));
				}
			} finally {
				Thread.currentThread().setContextClassLoader(originalClassLoader);
			}
			groupCollection.add(new Group(alternativeUsername, connector.namespace));
		} catch (final Exception e) {
			LOG.error("Group retrieval failed for connector #" + connector.uniqueId, e);
		}
		return groupCollection;
	}

	/**
	 * Verifies supplied authorization header and retrieves the user subject from it.
	 *
	 * @param req
	 * @param resp
	 * @return authenticated user name
	 */
	private String doKerberos(final HttpServletRequest req, final HttpServletResponse resp) {
		String princ = null;
		try {
			princ = KerberosChallenge.challenge(conf.scsCtx, req, resp);
			LOG.debug("Authenticated user is: " + princ);
			return princ;
		} catch (final Exception e) {
			LOG.error("Kerberos failed: " + e.getMessage());
			LOG.debug("Kerberos failure stack: ", e);
		}
		return null;
	}

	/**
	 * Builds a login page containing a form.
	 *
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	private static void doBuildLoginPage(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		resp.sendRedirect(LOGIN_PAGE_NAME);
	}

	private void successfulLoginPostWork(final HttpServletRequest req, final HttpServletResponse resp, final String originalUsername, final boolean returnUserAndGroupsInHeader) throws Exception {

		final Cookie[] ac = req.getCookies();
		String referrer = null, issuer = null, samlid = null, issueInstantStr = null;
		if (ac != null) for (final Cookie c : ac)
			if (AuthNStrings.REFERER_COOKIE_NAME.equals(c.getName())) {
				referrer = CookieManager.restoreCookieValue(c.getValue());
				c.setMaxAge(0);
				resp.addCookie(c);
			} else if (AuthNStrings.ISSUER_COOKIE_NAME.equals(c.getName())) {
				issuer = CookieManager.restoreCookieValue(c.getValue());
				c.setMaxAge(0);
				resp.addCookie(c);
			} else if (AuthNStrings.SAMLID_COOKIE_NAME.equals(c.getName())) {
				samlid = CookieManager.restoreCookieValue(c.getValue());
				c.setMaxAge(0);
				resp.addCookie(c);
			} else if (AuthNStrings.SAMLDATE_COOKIE_NAME.equals(c.getName())) {
				issueInstantStr = CookieManager.restoreCookieValue(c.getValue());
				c.setMaxAge(0);
				resp.addCookie(c);
			}
		
		final String username;
		if (
			conf.authnConf.groupRetrieval || 
			(conf.authnConf.authenticator==null) || 
			StringUtils.isNullOrEmpty(((AConnector)conf.authnConf.authenticator).nameTransformer)
		) {
			username = originalUsername;
		} else {
			final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
			final AConnector nameTransformer = conf.configuredConnectors.get(((AConnector)conf.authnConf.authenticator).nameTransformer);
			String alternativeUsername = null;
			try {
				Thread.currentThread().setContextClassLoader(nameTransformer.getClass().getClassLoader());
				alternativeUsername = mappings.getCachedMapping(nameTransformer, originalUsername);
			} finally {
				Thread.currentThread().setContextClassLoader(originalClassLoader);
			}
			if (alternativeUsername == null) {
				alternativeUsername = ((INameTransformer) nameTransformer).transformName(originalUsername);
				mappings.addCachedMapping(nameTransformer, originalUsername, alternativeUsername);
			}
			username = alternativeUsername;
		}

		if (referrer != null && referrer.startsWith(req.getContextPath())) resp.sendRedirect(referrer);
		else {

			Collection<Group> groups = null;
			boolean failedGroupRetrieval = false;
			try {
				groups = conf.authnConf.groupRetrieval ? getUserGroups(mappings, conf, conf.configuredConnectors, username, getServletContext()) : new ArrayList<>();
			} catch (FailGroupRetrieval e) {
				failedGroupRetrieval = true;
				LOG.warn("Group retrieval failed: " + e.getMessage());
			}

			if (returnUserAndGroupsInHeader) {
				
				if (failedGroupRetrieval) {
					
					resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					resp.setContentType("text/plain");
					resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
					resp.setContentLength(0);
					return;
					
				} else {

					final QuotedPrintableCodec qpc = new QuotedPrintableCodec();
					resp.addHeader("X-Username", qpc.encode(username));
					final Iterator<Group> it = groups.iterator();
					final StringBuilder sb = new StringBuilder();
					Group g;
					while (it.hasNext()) {
						g = it.next();
						sb.append(qpc.encode(g.namespace + g.principal));
						if (it.hasNext()) sb.append(", ");
					}
					resp.addHeader("X-Groups", sb.toString());
					resp.setStatus(HttpServletResponse.SC_OK);
					resp.setContentType("text/plain");
					resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
					resp.setContentLength(0);
					return;
					
				}

			} else {// artifact-binding or post-binding authN mode
				final String art = SAMLRequestParser.extractArtifactFromReferrer(referrer);
				final Date issueInstant = SAMLRequestParser.parseIssueInstantString(issueInstantStr);
				String consumer = "";
				CachedUser cUser = null;
				if (!failedGroupRetrieval) {
					if (art != null) {
						int i;
						if (referrer != null && (i = referrer.indexOf("?SAMLart=")) > 0) consumer = referrer.substring(0, i);
						LOG.debug("Registering artifiact: art=" + art + "; samlid=" + samlid + "; consumer=" + consumer + "; user=" + username);
						if (conf.authnConf.samlMode == SamlMode.ARTIFACT_BINDING) SAMLCache.get().registerArtifact(art, samlid, issueInstant, consumer, issuer, username, groups);
						else cUser = SAMLCache.buildUser(samlid, issueInstant, consumer, issuer, username, groups);
					} else {
						LOG.error("SamlArt is null");
						throw new Exception("Authentication request contains no SamlArt parameter");
					}
				}

				if (conf.authnConf.samlMode == SamlMode.ARTIFACT_BINDING) resp.sendRedirect(referrer);
				else doPostBindingSuccessfulLogin(req, resp, cUser);
			}

		}
	}

	/**
	 * Sends the Signed Authentication response using HTTP POST Binding
	 */
	private void doPostBindingSuccessfulLogin(final HttpServletRequest req, final HttpServletResponse resp, final CachedUser cUser) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, KeyStoreException, CertificateException, FileNotFoundException, UnrecoverableEntryException, IOException, SAXException, ParserConfigurationException, MarshalException, XMLSignatureException, Exception {
		LOG.info("Generating auto POST binding page");

		String consumer = "";
		String respXML = null;
		if (cUser == null) {
			LOG.info("No corresponding user.");
			respXML = SAMLResponseBuilder.buildPostResponse(SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE, null, "", new Date(), "", "", null, conf.authnConf.trustDuration, conf.authnConf.samlEntityId);
			consumer = "nobody";
		} else {
			consumer = cUser.getConsumer();
			LOG.debug("Found corresponding user: " + cUser.getUsername());
			respXML = SAMLResponseBuilder.buildPostResponse(SAMLSPIConstants.SAML_SC_SUCCESS, cUser.getUsername(), cUser.getSamlid(), cUser.getIssueInstant(), consumer, cUser.getSecMngrIssuer(), cUser.getGroups(), conf.authnConf.trustDuration,
				conf.authnConf.samlEntityId);
		}

		LOG.debug("Signing the response");
		final byte[] signedResponse = new PostBindingRequestSigner(conf.scsCtx).signResponse(respXML);
		if (LOG.isTraceEnabled()) LOG.trace("Signed response is: " + new String(signedResponse, StandardCharsets.UTF_8));
		final String base64EncodedSignedResponse = new String(Base64.encodeBase64Chunked(signedResponse), StandardCharsets.UTF_8);
		LOG.debug("Generating and serving the auto POST HTML page");

		if (StringUtils.isNullOrEmpty(consumer)) {
			ErrorManager.processError(req, resp, HttpServletResponse.SC_BAD_REQUEST, "No target specified for form");
			return;
		}

		final byte[] response = String.format(POSTBINDING_PAGE, consumer, base64EncodedSignedResponse).getBytes(StandardCharsets.UTF_8);

		resp.addHeader("Cache-Control", "no-cache");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resp.setContentLength(response.length);
		resp.setContentType("text/html");
		try (BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream())) {
			bos.write(response);
		}

	}
}