package com.sword.gsa.spis.scs.commons.config.authn;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import sword.common.utils.StringUtils;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthenticator;

public final class AuthenticatorConf {

	static final Logger LOG = Logger.getLogger(AuthenticatorConf.class);

	public static final long DEFAULT_TRUST_DURATION = TimeUnit.MINUTES.toMillis(60);
	public static final SamlMode DEFAULT_SAML_MODE = SamlMode.POST_BINDING;
	public static final String DEFAULT_ENTITY_ID = "SCSAuth";
	public static final String DEFAULT_CM_MAIN_CONN_NAME = "_scs_";

	public final IAuthenticator authenticator;

	public final SamlMode samlMode;
	public final String samlEntityId;
	public final long trustDuration;
	public final boolean kerberosAuthN;
	public final boolean groupRetrieval;
	public final String cmMainConnectorName;

	/**
	 * Utilized only to generate the default config
	 */
	private AuthenticatorConf(final IAuthenticator authenticator, final SamlMode samlMode, final String samlEntityId, final long trustDuration, final boolean kerberosAuthN, final boolean groupRetrieval, final String cmMainConnectorName) {
		super();
		this.authenticator = authenticator;
		this.samlMode = samlMode;
		this.samlEntityId = samlEntityId;
		this.trustDuration = trustDuration;
		this.kerberosAuthN = kerberosAuthN;
		this.groupRetrieval = groupRetrieval;
		this.cmMainConnectorName = cmMainConnectorName;
	}

	private AuthenticatorConf(final Map<String, AConnector> configuredConnectors, final Element authentElement, final XPath xpath) {
		super();

		{
			AConnector authenticator = null;
			final String authentConnectorId = authentElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
			if (!StringUtils.isNullOrEmpty(authentConnectorId) && configuredConnectors.containsKey(authentConnectorId)) {
				authenticator = configuredConnectors.get(authentConnectorId);
				if (!(authenticator instanceof IAuthenticator)) {
					LOG.info("Connector configured for authentication does not implement any authentication interface - discarding (#" + authentConnectorId + ")");
					authenticator = null;
				}
			} else LOG.info("Authentication connector not found: " + authentConnectorId);
			this.authenticator = (IAuthenticator) authenticator;
			LOG.info("Authentication connector: " + (this.authenticator == null ? "none" : authenticator.uniqueId));
		}

		{// SAML mode
			SamlMode samlMode = AuthenticatorConf.DEFAULT_SAML_MODE;
			final String samlType = authentElement.getAttribute(SCSConfiguration.ATTRNAME_SAML_TYPE);
			if (SamlMode.POST_BINDING.name().equals(samlType)) samlMode = SamlMode.POST_BINDING;
			else if (SamlMode.ARTIFACT_BINDING.name().equals(samlType)) samlMode = SamlMode.ARTIFACT_BINDING;
			else {
				LOG.warn(String.format("Invalid SAML mode configured (%s) - using default", samlType));
				samlMode = AuthenticatorConf.DEFAULT_SAML_MODE;
			}
			this.samlMode = samlMode;
		}

		{// Trust duration
			final String trustDurationStr = authentElement.getAttribute(SCSConfiguration.ATTRNAME_TRUST_DUR);
			long trustDuration = AuthenticatorConf.DEFAULT_TRUST_DURATION;
			try {
				trustDuration = Long.parseLong(trustDurationStr);
			} catch (final Exception e) {
				LOG.warn(String.format("Invalid trust duration configured (%s) - using default", trustDurationStr));
				trustDuration = AuthenticatorConf.DEFAULT_TRUST_DURATION;
			}
			this.trustDuration = trustDuration;
		}

		{// Entity ID
			String entityId = authentElement.getAttribute(SCSConfiguration.ATTRNAME_ENT_ID);
			if (StringUtils.isNullOrEmpty(entityId)) {
				LOG.warn("No SAML entity ID configured - using default");
				entityId = AuthenticatorConf.DEFAULT_ENTITY_ID;
			}
			samlEntityId = entityId;
		}

		{// Kerberos
			boolean kerberosAuthN = false;
			try {
				kerberosAuthN = Boolean.parseBoolean(authentElement.getAttribute(SCSConfiguration.ATTRNAME_KRB));
			} catch (final Exception e) {
				kerberosAuthN = false;
			}
			this.kerberosAuthN = kerberosAuthN;
			LOG.info("Kerberos authentication is " + (this.kerberosAuthN ? "enabled" : "disabled"));
		}

		{// Group Retrieval
			boolean groupRetr = false;
			try {
				groupRetr = Boolean.parseBoolean(authentElement.getAttribute(SCSConfiguration.ATTRNAME_GROUP_RETR));
			} catch (final Exception e) {
				groupRetr = false;
			}
			this.groupRetrieval = groupRetr;
			LOG.info("Group Retrieval is " + (this.groupRetrieval ? "enabled" : "disabled") + " for SAML authentication");
		}

		{// CM main connector name
			String cmMainConnectorName = authentElement.getAttribute(SCSConfiguration.ATTRNAME_CM_MAIN_CONN_NAME);
			if (StringUtils.isNullOrEmpty(cmMainConnectorName)) {
				LOG.warn("No name configured for connector manager - using default");
				cmMainConnectorName = AuthenticatorConf.DEFAULT_CM_MAIN_CONN_NAME;
			}
			this.cmMainConnectorName = cmMainConnectorName;
		}

	}

	public static AuthenticatorConf readConfig(final Map<String, AConnector> configuredConnectors, final Element rootNode, final XPath xpath) throws XPathExpressionException {
		LOG.info("Reading authentication configuration");
		final Element element = (Element) xpath.evaluate(SCSConfiguration.TAGNAME_AUTHN, rootNode, XPathConstants.NODE);
		if (element == null) return new AuthenticatorConf((IAuthenticator) null, DEFAULT_SAML_MODE, DEFAULT_ENTITY_ID, DEFAULT_TRUST_DURATION, false, false, DEFAULT_CM_MAIN_CONN_NAME);
		else return new AuthenticatorConf(configuredConnectors, element, xpath);
	}

}