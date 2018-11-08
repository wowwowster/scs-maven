package com.sword.gsa.spis.scs.commons.config.authz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import sword.common.utils.StringUtils;
import sword.common.utils.dates.DateUtils;
import sword.common.utils.enums.EnumFactory;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthorizer;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.INameTransformer;
import com.sword.gsa.spis.scs.saml.authorization.Decision;

public final class AuthorizerConf {

	public final long globalAuthZTimeout;
	public final Decision unknownURLsDefaultAccess;
	public final List<SAMLAuthzConf> authorizers;

	static final Logger LOG = Logger.getLogger(AuthorizerConf.class);

	AuthorizerConf(final Map<String, AConnector> configuredConnectors, final Element authzElement, final XPath xpath) throws XPathExpressionException {
		super();

		long globalAuthZTimeout = TimeUnit.SECONDS.toMillis(15);
		try {
			globalAuthZTimeout = Long.parseLong(authzElement.getAttribute(SCSConfiguration.ATTRNAME_TIMEOUT));
		} catch (final Exception e) {
			globalAuthZTimeout = TimeUnit.SECONDS.toMillis(15);
		}
		this.globalAuthZTimeout = globalAuthZTimeout;
		LOG.info("Authorization timeout: " + DateUtils.toReadableTimeSpan(this.globalAuthZTimeout));

		Decision unknownURLsDefaultAccess = Decision.INDETERMINATE;
		try {
			unknownURLsDefaultAccess = EnumFactory.parse(authzElement.getAttribute(SCSConfiguration.ATTRNAME_UK_URLS_DEC), Decision.class);
		} catch (final Exception e) {
			unknownURLsDefaultAccess = Decision.INDETERMINATE;
		}
		this.unknownURLsDefaultAccess = unknownURLsDefaultAccess;
		LOG.info("Default access for unknown URLs: " + this.unknownURLsDefaultAccess.name());

		LOG.info("Reading authorizers conf");
		final List<SAMLAuthzConf> authorizers = new ArrayList<>();
		final NodeList nl = (NodeList) xpath.evaluate(SCSConfiguration.TAG_OR_ATTR_CONNECTOR, authzElement, XPathConstants.NODESET);
		if (nl != null) {
			final int nll = nl.getLength();
			if (nll > 0) for (int i = 0; i < nll; i++) {
				final Element authzConnectorElement = (Element) nl.item(i);
				final String authzConnectorId = authzConnectorElement.getAttribute(SCSConfiguration.ATTRNAME_ID);
				try {
					if (!StringUtils.isNullOrEmpty(authzConnectorId) && configuredConnectors.containsKey(authzConnectorId)) {
						final AConnector authzConnector = configuredConnectors.get(authzConnectorId);
						if (authzConnector instanceof IAuthorizer) {
							AConnector nameTransformerConnector = null;
							final String nameTransformerConnectorId = authzConnectorElement.getAttribute(SCSConfiguration.ATTRNAME_NAME_TRANFORMER);
							if (!StringUtils.isNullOrEmpty(nameTransformerConnectorId) && configuredConnectors.containsKey(nameTransformerConnectorId)) nameTransformerConnector = configuredConnectors.get(nameTransformerConnectorId);
							if (!(nameTransformerConnector instanceof INameTransformer)) nameTransformerConnector = null;
							if (nameTransformerConnector != null) LOG.info(String.format("Authorizer connector #%s uses name transformer #%s", authzConnectorId, nameTransformerConnector.uniqueId));
							final String pattern = authzConnectorElement.getAttribute(SCSConfiguration.ATTRNAME_PATTERN);
							LOG.info(String.format("URL pattern for authorizer #%s: %s", authzConnectorId, pattern));
							authorizers.add(new SAMLAuthzConf(authzConnector, nameTransformerConnector, Pattern.compile(pattern)));
						} else LOG.info("Authorizer connector does not implement any authorization interface - discarding (#" + authzConnectorId + ")");
					} else LOG.info("Authorizer connector not found: " + authzConnectorId);
				} catch (final Exception e) {
					LOG.warn(String.format("Error parsing authorizers #%s configuration - discarding", authzConnectorId), e);
				}
			}
		}
		this.authorizers = authorizers;
		LOG.info("Found " + this.authorizers.size() + " authorizer(s)");

	}

	public static AuthorizerConf readConfig(final Map<String, AConnector> configuredConnectors, final Element rootNode, final XPath xpath) throws XPathExpressionException {
		LOG.info("Reading authorization configuration");
		final Element element = (Element) xpath.evaluate("authorizer", rootNode, XPathConstants.NODE);
		if (element != null) return new AuthorizerConf(configuredConnectors, element, xpath);
		else {
			LOG.info("No authorization defined");
			return null;
		}
	}

}