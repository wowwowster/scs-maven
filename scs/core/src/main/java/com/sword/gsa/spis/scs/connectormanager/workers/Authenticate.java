package com.sword.gsa.spis.scs.connectormanager.workers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import sword.common.utils.StringUtils;
import sword.common.utils.streams.StreamUtils;
import sword.gsa.xmlfeeds.builder.acl.Group;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.TransformedNamesCacheMgr;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNFormData;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthenticator;
import com.sword.gsa.spis.scs.commons.connector.models.IGroupRetriever;
import com.sword.gsa.spis.scs.commons.http.AuthnServlet;
import com.sword.gsa.spis.scs.commons.throwables.FailGroupRetrieval;

public class Authenticate extends XMLWorker {

	private final TransformedNamesCacheMgr mappings;

	public Authenticate(final SCSConfiguration conf, final ServletContext ctx, final TransformedNamesCacheMgr mappings) {
		super(conf, ctx);
		this.mappings = mappings;
	}

	private static final String RESPONSE_SUCCESS = "<CmResponse><AuthnResponse><Success ConnectorName=\"%s\"><Identity>%s</Identity>%s</Success></AuthnResponse></CmResponse>";
	private static final String RESPONSE_FAILURE = "<CmResponse><AuthnResponse><Failure ConnectorName=\"%s\"/></AuthnResponse></CmResponse>";
	private static final Logger LOG = Logger.getLogger(Authenticate.class);

	private static final Pattern USERNAME_P = Pattern.compile("<Username>(.+?)</Username>");
	private static final Pattern PASSWORD_P = Pattern.compile("<Password>(.+?)</Password>");
	private static final Pattern CONNECTOR_P = Pattern.compile("<ConnectorName>(.+?)</ConnectorName>");

	/**
	 * <div>&lt;AuthnRequest&gt;</div> <div style="padding-left: 8px" >&lt;Connectors&gt;</div> <div style="padding-left: 16px" >&lt;ConnectorName&gt;connector_id&lt;/ConnectorName&gt;</div> <div
	 * style="padding-left: 8px" >&lt;/Connectors&gt;</div> <div style="padding-left: 8px" >&lt;Credentials&gt;</div> <div style="padding-left: 16px" >&lt;Username&gt;User&lt;/Username&gt;</div> <div
	 * style="padding-left: 16px" >&lt;Password&gt;**********&lt;/Password&gt;</div> <div style="padding-left: 8px" >&lt;/Credentials&gt;</div> <div>&lt;/AuthnRequest&gt;</div>
	 */
	@Override
	public String getXMLResponse(final HttpServletRequest req) throws Exception {

		LOG.info("GSA call using Connector SPI. Servlet: Authenticate");

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		StreamUtils.transferBytes(req.getInputStream(), baos, false);
		final String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
		LOG.trace("Authentication XML: " + content);

		String responseS = null;
		String username = null;
		String password = null;
		String connectorId = null;

		Matcher m = USERNAME_P.matcher(content);
		if (m.find()) username = m.group(1);
		m = PASSWORD_P.matcher(content);
		if (m.find()) password = m.group(1);
		m = CONNECTOR_P.matcher(content);
		if (m.find()) connectorId = m.group(1);

		LOG.debug("Authenticating " + username + " on rep #" + connectorId);

		if (StringUtils.isNullOrEmpty(connectorId)) return String.format(RESPONSE_FAILURE, "null");

		responseS = String.format(RESPONSE_FAILURE, connectorId);

		if (conf.authnConf.cmMainConnectorName.equals(connectorId)) {

			if (!StringUtils.isNullOrEmpty(username)) {
				final boolean skipAuthN = password == null;
				String authenticatedUser = null;
				if (skipAuthN) authenticatedUser = username;
				else {
					final IAuthenticator authenticator = conf.authnConf.authenticator;
					if (authenticator instanceof IAuthNFormData) authenticatedUser = AuthnServlet.doUsernamePasswordLogin((IAuthNFormData) authenticator, username, password);
					else {
						LOG.error("Received authention request for the CM SPI whereas the connector configured for authentication does not support form data authentication");
					}
				}
				if (authenticatedUser != null) {
					Collection<Group> groups = null;
					boolean failedGroupRetrieval = false;
					try {
						groups = AuthnServlet.getUserGroups(mappings, conf, conf.configuredConnectors, authenticatedUser, ctx);
					} catch (FailGroupRetrieval e) {
						failedGroupRetrieval = true;
						LOG.warn("Group retrieval failed: " + e.getMessage());
					}
					if (failedGroupRetrieval) {
						responseS = String.format(RESPONSE_FAILURE, connectorId);
					} else {
						final StringBuilder groupString = new StringBuilder();
						for (final Group group : groups)
							groupString.append("<Group namespace=\"").append(StringUtils.toXMLString(StringUtils.isNullOrEmpty(group.namespace) ? "Default" : group.namespace)).append("\">").append(StringUtils.toXMLString(group.principal)).append("</Group>");
						responseS = String.format(RESPONSE_SUCCESS, connectorId, username, groupString.toString());
					}
				}
			}

		} else {

			final AConnector connector = conf.configuredConnectors.get(connectorId);
			if (connector != null) if (!StringUtils.isNullOrEmpty(username)) try {

				if (password != null) {
					if (connector instanceof IAuthNFormData) {
						LOG.info("Password is not null, we will enforce authentication.");
						final String usernameAfterAuthN = ((IAuthNFormData) connector).authenticate(username, password);
						if (usernameAfterAuthN != null && !username.equals(usernameAfterAuthN)) {
							LOG.warn("Warning: username before authentication doesn't match with username after authentication. This is not supported by the connector SPI. AuthZ are likely to fail.");
							LOG.warn("username before authN: " + username);
							LOG.warn("username after authN (discarded): " + usernameAfterAuthN);
						}
					} else {
						throw new ConfigurationException("Repository #" + connectorId + " was configured for authentication and does not support form data authentication");
					}
				}

				final StringBuilder groupString = new StringBuilder();
				if (connector instanceof IGroupRetriever) if (!StringUtils.isNullOrEmpty(username)) try {// If group retrieval fails, the whole authentication will fail; return an empty group list
					// instead
					final Collection<Group> groups = AuthnServlet.getUserGroupsForConnector(mappings, username, connector, conf.configuredConnectors, ctx);
					for (final Group group : groups)
						groupString.append("<Group namespace=\"").append(StringUtils.isNullOrEmpty(group.namespace) ? "Default" : StringUtils.toXMLString(group.namespace)).append("\">").append(StringUtils.toXMLString(group.principal)).append("</Group>");
				} catch (final Exception e) {
					LOG.error("Could not retrieve groups for user: " + username, e);
				}
				responseS = String.format(RESPONSE_SUCCESS, connectorId, username, groupString.toString());
			} catch (final Throwable e) {
				LOG.error("Could not authenticate user: " + username, e);
			}

		}

		LOG.debug("Replying with: " + responseS);
		return responseS;

	}
}