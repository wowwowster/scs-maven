package com.sword.gsa.spis.scs.commons.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.naming.ConfigurationException;
import javax.servlet.http.Part;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sword.springboard.CloudSource;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import sword.common.security.PwdManager;
import sword.common.utils.HexUtils;
import sword.common.utils.StringUtils;
import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;
import sword.connectors.commons.config.CPUtils;

import com.sword.gsa.spis.scs.commons.SCSContext;
import com.sword.gsa.spis.scs.commons.config.authn.AuthenticatorConf;
import com.sword.gsa.spis.scs.commons.config.authn.GroupRetrieverConf;
import com.sword.gsa.spis.scs.commons.config.authn.SamlMode;
import com.sword.gsa.spis.scs.commons.config.authz.AuthorizerConf;
import com.sword.gsa.spis.scs.commons.config.indexing.IndexerConf;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule.Period;
import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.IGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.INameTransformer;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.commons.utils.HttpUtils;
import com.sword.gsa.spis.scs.gsaadmin.GSAInfo;
import com.sword.scs.Constants;

public final class SCSConfiguration {

	/** @claurier Le regex suivant implique  un nom comprenant que des minuscules */
	public static final String CONNECTOR_ID_PATTERN = "^[a-z_][a-z_\\d\\-]{0,63}$";

	public static final String TAG_OR_ATTR_CONNECTOR = "connector";

	public static final String TAGNAME_ROOT = "config";
	public static final String TAGNAME_GSA = "gsa";
	public static final String TAGNAME_SB = "sb";
	public static final String TAGNAME_FTP = "ftp";
	public static final String TAGNAME_AUTHN = "authentication";
	public static final String TAGNAME_GROUP_RETR = "group-retriever";
	public static final String TAGNAME_INDEXER = "indexer";
	public static final String TAGNAME_SCHEDULE_PERIOD = "schedule-period";
	public static final String TAGNAME_CONNECTOR_PARAM = "cp";

	public static final String ATTRNAME_SSL = "ssl";
	public static final String ATTRNAME_DEFAULT_HOST = "default-host";
	public static final String ATTRNAME_ADMIN_HOST = "admin-host";
	public static final String ATTRNAME_SYS = "system";
	public static final String ATTRNAME_SOFT = "software";
	public static final String ATTRNAME_NO_CONNECTION = "no-connection";
	public static final String ATTRNAME_CLASS = "class";
	public static final String ATTRNAME_ID = "id";
	public static final String ATTRNAME_CONNECTOR_PARAM_NAME = "n";
	public static final String ATTRNAME_CONNECTOR_PARAM_VALUE = "v";
	public static final String ATTRNAME_NAME_TRANFORMER = "name-transformer";
	public static final String ATTRNAME_NS = "namespace";
	public static final String ATTRNAME_TIMEOUT = "timeout";
	public static final String ATTRNAME_KRB = "kerberos";
	public static final String ATTRNAME_GROUP_RETR = "group-retrieval";
	public static final String ATTRNAME_SAML_TYPE = "saml-type";
	public static final String ATTRNAME_TRUST_DUR = "trust-duration";
	public static final String ATTRNAME_ENT_ID = "entity-id";
	public static final String ATTRNAME_CM_MAIN_CONN_NAME = "cm-name";
	public static final String ATTRNAME_UK_URLS_DEC = "unknown-urls";
	public static final String ATTRNAME_PATTERN = "pattern";
	public static final String ATTRNAME_CACHE_REFRESH_INT = "refresh-interval";
	public static final String ATTRNAME_INDEXER_INTERVAL = "interval";
	public static final String ATTRNAME_INDEXER_THROUGHPUT = "throughput";
	public static final String ATTRNAME_SCHED_PERIOD_DAY = "day";
	public static final String ATTRNAME_SCHED_PERIOD_START = "start";
	public static final String ATTRNAME_SCHED_PERIOD_DUR = "duration";
	public static final String ATTRNAME_FTPMODE = "ftp-mode";
	public static final String ATTRNAME_FTP_HOST = "ftp-host";
	public static final String ATTRNAME_FTP_USERNAME = "ftp-username";
	public static final String ATTRNAME_FTP_PASSWORD = "ftp-password";
	public static final String ATTRNAME_SB_SOURCE_ID = "source-id";
	public static final String ATTRNAME_SB_SOURCE_NAME = "source-name";
	public static final String ATTRNAME_SB_CUST_ID = "cust-id";
	public static final String ATTRNAME_SB_KEYPATH = "keyfile";

	public final long creationTime;
	public final SCSContext scsCtx;
	public final GSAInfo gsa;
	public final String ftpHost;
	public final String ftpUsername;
	public final String ftpPassword;
	public final Map<String, AConnector> configuredConnectors;
	public final AuthenticatorConf authnConf;
	public final Collection<GroupRetrieverConf> groupRetrievers;
	public final AuthorizerConf authzConf;
	public final Collection<IndexerConf> indexers;
	public final boolean ftpMode;
	public final Map<String, CloudSource> configuredCloudSources;

	public static final Logger LOG = Logger.getLogger(SCSConfiguration.class);


	public SCSConfiguration(final SCSContext scsCtx, final GSAInfo gsa, String ftpMode, String ftpHost, String ftpUsername, String ftpPassword, final Map<String, AConnector> configuredConnectors, final AuthenticatorConf authenticator, final Collection<GroupRetrieverConf> groupRetrievers, final AuthorizerConf authorizers, final Collection<IndexerConf> indexers, final Map<String,CloudSource> configuredCloudSources) {
		super();
		creationTime = System.currentTimeMillis();
		this.scsCtx = scsCtx;
		this.gsa = gsa;
		this.ftpHost=ftpHost;
		this.ftpMode=Boolean.parseBoolean(ftpMode);
		this.ftpUsername=ftpUsername;
		this.ftpPassword=ftpPassword;
		this.configuredConnectors = configuredConnectors;
		authnConf = authenticator;
		this.groupRetrievers = groupRetrievers;
		authzConf = authorizers;
		this.indexers = indexers;
		this.configuredCloudSources = configuredCloudSources;
	}

	public static SCSConfiguration invalidConf() {
		return new SCSConfiguration(null, null, null, null, null, null, null, null, null, null, null, null);
	}

	public static boolean isInvalidConf(SCSConfiguration c) {
		return c==null || c.scsCtx==null;
	}

	public static SCSConfiguration readConfig(final Path confFile, final SCSContext gc) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();
		
		final double gsaNum = (double) xpath.evaluate("count(" + TAGNAME_GSA + ")", rootNode, XPathConstants.NUMBER);
		final Element gsa = (Element) ((gsaNum == 1d) ? xpath.evaluate(TAGNAME_GSA, rootNode, XPathConstants.NODE) : null);
		
		final double ftpNum = (double) xpath.evaluate("count(" + TAGNAME_FTP + ")", rootNode, XPathConstants.NUMBER);
		final Element ftp = (Element) ((ftpNum == 1d) ? xpath.evaluate(TAGNAME_FTP, rootNode, XPathConstants.NODE) : null);
		String ftpMode = "", ftpHost = "", ftpUsername= "", ftpPassword= "";
		if(ftp!=null){
			ftpMode=ftp.getAttribute(ATTRNAME_FTPMODE);
			ftpHost=ftp.getAttribute(ATTRNAME_FTP_HOST);
			ftpUsername=ftp.getAttribute(ATTRNAME_FTP_USERNAME);
			ftpPassword=ftp.getAttribute(ATTRNAME_FTP_PASSWORD);
		}
		GSAInfo gi = null;
		if (gsa != null) gi = new GSAInfo("true".equals(gsa.getAttribute(ATTRNAME_SSL)), gsa.getAttribute(ATTRNAME_DEFAULT_HOST), gsa.getAttribute(ATTRNAME_ADMIN_HOST), gsa.getAttribute(ATTRNAME_SOFT), gsa.getAttribute(ATTRNAME_SYS), gsa.getAttribute(ATTRNAME_ID), "true".equals(gsa.getAttribute(ATTRNAME_NO_CONNECTION)));
		else gi = new GSAInfo(false , "", "", "", "", "", true);

		LOG.info("Retrieving configured connectors list");
		// Find configured connectors
		final Map<String, AConnector> configuredConnectors = new HashMap<>();
		final NodeList connectorNodes = (NodeList) xpath.evaluate(TAG_OR_ATTR_CONNECTOR, rootNode, XPathConstants.NODESET);
		if (connectorNodes != null && connectorNodes.getLength() > 0) {
			final int nc = connectorNodes.getLength();
			CONNECTOR_LABEL: for (int i = 0; i < nc; i++) {
				final Element e = (Element) connectorNodes.item(i);
				final String className = e.getAttribute(ATTRNAME_CLASS);
				if (StringUtils.isNullOrEmpty(className)) LOG.warn(String.format("Found %s tag with no %s attribute - discarding", TAG_OR_ATTR_CONNECTOR, ATTRNAME_CLASS));
				else {
					final String id = e.getAttribute(ATTRNAME_ID);
					if (StringUtils.isNullOrEmpty(id)) LOG.warn(String.format("Found %s tag (%s=%s) with no %s attribute - discarding", TAG_OR_ATTR_CONNECTOR, ATTRNAME_CLASS, className, ATTRNAME_ID));
					else try {
						final IConnectorContext cc = gc.getConnectorCtx(className);
						LOG.info(String.format("Found connector #%s (%s) - fetching confguration parameters", id, className));
						try {
							final AConnector c = loadConnectorInstance(LOG, xpath, e, id, className, cc);
							configuredConnectors.put(id, c);
						} catch (final ReflectiveOperationException roe) {
							LOG.warn(String.format("Failed to create connector instance (%s=%s) - discarding", ATTRNAME_CLASS, className), roe);
						} catch (final ConfigurationException ce) {
							LOG.warn(ce.getExplanation());
							continue CONNECTOR_LABEL;
						}
					} catch (final Exception ex) {
						LOG.warn(String.format("Found %s tag (%s=%s) which references a connector that is not installed - discarding", TAG_OR_ATTR_CONNECTOR, ATTRNAME_CLASS, className));
					}
				}
			}
		}
		LOG.info("Found " + configuredConnectors.size() + " configured connector(s)");

		{
			AConnector c = null;
			AConnector m = null;
			final Collection<String> invalidIds = new HashSet<>();
			for (final String id : configuredConnectors.keySet()) {
				c = configuredConnectors.get(id);
				if (!StringUtils.isNullOrEmpty(c.nameTransformer)) if (configuredConnectors.containsKey(c.nameTransformer)) {
					m = configuredConnectors.get(c.nameTransformer);
					if (!(m instanceof INameTransformer)) {
						LOG.warn(String.format("Connector %s references a connector that is not a Name Transformer implementation: %s - discarding connector", c.uniqueId, c.nameTransformer));
						invalidIds.add(c.uniqueId);
					}
				} else {
					LOG.warn(String.format("Connector %s references a non-existing Name Transformer: %s - discarding connector", c.uniqueId, c.nameTransformer));
					invalidIds.add(c.uniqueId);
				}
			}
			for (final String id : invalidIds)
				configuredConnectors.remove(id);
		}

		final AuthenticatorConf authenticator = AuthenticatorConf.readConfig(configuredConnectors, rootNode, xpath);
		final AuthorizerConf authorizer = AuthorizerConf.readConfig(configuredConnectors, rootNode, xpath);

		final Collection<GroupRetrieverConf> groupRetrievers = new ArrayList<>();
		{
			LOG.info("Reading group retrievers conf");
			final NodeList nl = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_GROUP_RETR, rootNode, XPathConstants.NODESET);
			if (nl != null) {
				final int nll = nl.getLength();
				if (nll > 0) for (int i = 0; i < nll; i++) {
					final Element grElement = (Element) nl.item(i);
					final String grConnectorId = grElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
					final String criStr = grElement.getAttribute(SCSConfiguration.ATTRNAME_CACHE_REFRESH_INT);
					final long cri = StringUtils.isNullOrEmpty(criStr) ? GroupRetrieverConf.DEFAULT_CRI : Long.parseLong(criStr);
					try {
						if (!StringUtils.isNullOrEmpty(grConnectorId) && configuredConnectors.containsKey(grConnectorId)) {
							final AConnector grConnector = configuredConnectors.get(grConnectorId);
							if (grConnector instanceof IGroupRetriever) {
								if (grConnector instanceof ICachableGroupRetriever) groupRetrievers.add(new GroupRetrieverConf(grConnector, cri));
								else groupRetrievers.add(new GroupRetrieverConf(grConnector, -1));
							} else LOG.info("Group retriever connector does not implement any group retrieval interface - discarding (#" + grConnectorId + ")");
						} else LOG.info("Group retriever connector not found: " + grConnectorId);
					} catch (final Exception e) {
						LOG.warn(String.format("Error parsing group retrievers #%s configuration - discarding", grConnectorId), e);
					}
				}
			}
			LOG.info("Found " + groupRetrievers.size() + " group retriever(s)");
		}

		final Collection<IndexerConf> indexers = new ArrayList<>();
		{
			LOG.info("Reading indexers conf");
			final NodeList indexersNodeList = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_INDEXER, rootNode, XPathConstants.NODESET);
			if (indexersNodeList != null) {
				final int indexersCount = indexersNodeList.getLength();
				if (indexersCount > 0) for (int i = 0; i < indexersCount; i++) {
					final Element iElement = (Element) indexersNodeList.item(i);
					final String indexerId = iElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
					long interval;
					try {
						if (iElement.hasAttribute(SCSConfiguration.ATTRNAME_INDEXER_INTERVAL)) {
							interval = Long.parseLong(iElement.getAttribute(SCSConfiguration.ATTRNAME_INDEXER_INTERVAL));
							if (interval < 0L) interval = 0L;
						} else interval = TimeUnit.HOURS.toMillis(3);
					} catch (final NumberFormatException nfe) {
						interval = TimeUnit.HOURS.toMillis(3);
					}
					long throughput;
					try {
						if (iElement.hasAttribute(SCSConfiguration.ATTRNAME_INDEXER_THROUGHPUT)) throughput = Long.parseLong(iElement.getAttribute(SCSConfiguration.ATTRNAME_INDEXER_THROUGHPUT));
						else throughput = -1L;
					} catch (final NumberFormatException nfe) {
						throughput = -1L;
					}
					try {
						if (!StringUtils.isNullOrEmpty(indexerId) && configuredConnectors.containsKey(indexerId)) {
							final AConnector indexer = configuredConnectors.get(indexerId);
							if (indexer instanceof Indexer) {
								final NodeList schedNodeList = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_SCHEDULE_PERIOD, iElement, XPathConstants.NODESET);
								Schedule sched = null;
								if (schedNodeList != null) {
									final int schedCount = schedNodeList.getLength();
									if (schedCount > 0) {
										sched = new Schedule();
										for (int j = 0; j < schedCount; j++) {
											final Element schedElement = (Element) schedNodeList.item(j);
											sched.periods.add(new Period(Integer.parseInt(schedElement.getAttribute(ATTRNAME_SCHED_PERIOD_DAY)), Integer.parseInt(schedElement.getAttribute(ATTRNAME_SCHED_PERIOD_START)), Integer.parseInt(schedElement
												.getAttribute(ATTRNAME_SCHED_PERIOD_DUR))));
										}
									}
								}
								indexers.add(new IndexerConf(indexerId, interval, throughput, sched));
							} else LOG.info("Indexer connector does not implement any indexing interface - discarding (#" + indexerId + ")");
						} else LOG.info("Indexer connector not found: " + indexerId);
					} catch (final Exception e) {
						LOG.warn(String.format("Error parsing indexers #%s configuration - discarding", indexerId), e);
					}
				}
			}
			LOG.info("Found " + indexers.size() + " indexer(s)");
		}


		/******************************************************
		 * Getting
		 ******************************************************/
		LOG.info("Retrieving configured cloud sources list");
		// Find configured connectors
		final Map<String, CloudSource> configuredCloudSources = new HashMap<>();
		final NodeList sourceNodes = (NodeList) xpath.evaluate(TAGNAME_SB, rootNode, XPathConstants.NODESET);
		if (sourceNodes != null && sourceNodes.getLength() > 0) {


			final int nc = sourceNodes.getLength();
			CONNECTOR_LABEL: for (int i = 0; i < nc; i++) {
				final Element e = (Element) sourceNodes.item(i);
				LOG.info("attr custid: " + e.getAttribute("cust-id"));

				String sourceId = e.getAttribute("source-id");
				CloudSource source = new CloudSource(sourceId, e.getAttribute("source-name"), e.getAttribute("cust-id"), e.getAttribute("keyfile"));
				configuredCloudSources.put(sourceId, source);
			}
		}
		LOG.info("Found " + configuredCloudSources.size() + " configured cloud source(s)");


		return new SCSConfiguration(gc, gi, ftpMode, ftpHost, ftpUsername, ftpPassword, configuredConnectors, authenticator, groupRetrievers, authorizer, indexers, configuredCloudSources);

	}

	public static AConnector loadConnectorInstance(final Logger log, final XPath xpath, final Element connectorXmlElement, final String connectorId, final String connectorClass, final IConnectorContext cc) throws XPathExpressionException, ConfigurationException, ReflectiveOperationException {
		final String paramValueXpathBase = String.format("%s[@%s='%s']/@%s", TAGNAME_CONNECTOR_PARAM, ATTRNAME_CONNECTOR_PARAM_NAME, "%s", ATTRNAME_CONNECTOR_PARAM_VALUE);
		final Map<String, String> cpMap = new HashMap<>();
		for (final CP cp : cc.getConfParams()) {
			final String cpVal = xpath.evaluate(String.format(paramValueXpathBase, cp.name), connectorXmlElement);
			log.info(String.format("\t- %s = %s", cp.name, cpVal));
			// Check if mandatory parameter
			final boolean inoe = StringUtils.isNullOrEmpty(cpVal);
			if (inoe && CP.isMandatory(cp)) throw new ConfigurationException(String.format("Mandatory configuration parameter for connector #%s is not defined: %s - discarding connector", connectorId, cp.name));
			else if (!inoe) cpMap.put(cp.name, cpVal);
		}
		final String namespace = connectorXmlElement.hasAttribute(ATTRNAME_NS) ? connectorXmlElement.getAttribute(ATTRNAME_NS) : null;
		final String nameTransformerId = connectorXmlElement.hasAttribute(ATTRNAME_NAME_TRANFORMER) ? connectorXmlElement.getAttribute(ATTRNAME_NAME_TRANFORMER) : null;
		return (AConnector) cc.getClassLoader().loadClass(cc.getClassName()).getConstructor(String.class, Map.class, String.class, String.class).newInstance(connectorId, cpMap, namespace, nameTransformerId);
	}

	/**
	 * @param confFile
	 * @param cc
	 * @param cid
	 *            : connector id
	 * @param ns
	 *            : namespace
	 * @param mid
	 *            : name transformer id
	 * @param confParams
	 */
	public static void updateConnectorConfig(final Charset cs, final PwdManager pm, final Path confFile, final IConnectorContext cc, final String cid, final String ns, final String mid, final Map<CP, Part> confParams) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, IllegalBlockSizeException, BadPaddingException, TransformerConfigurationException, TransformerException, TransformerFactoryConfigurationError {

		if (StringUtils.isNullOrEmpty(cid) || !cid.matches(CONNECTOR_ID_PATTERN)) throw new IllegalArgumentException(cid + " is not a valid connector ID");

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = (Element) xpath.evaluate("/".concat(TAGNAME_ROOT), doc, XPathConstants.NODE);

		Element connectorNode = (Element) xpath.evaluate(String.format("%s[@%s='%s']", TAG_OR_ATTR_CONNECTOR, ATTRNAME_ID, cid.replace('\'', '_')), rootNode, XPathConstants.NODE);
		if (connectorNode == null) {// New connector
			connectorNode = doc.createElement(TAG_OR_ATTR_CONNECTOR);
			rootNode.appendChild(connectorNode);
		}

		// Add new attributes
		connectorNode.setAttribute(ATTRNAME_ID, cid);
		connectorNode.setAttribute(ATTRNAME_CLASS, cc.getClassName());
		if (ns == null) connectorNode.setAttribute(ATTRNAME_NS, "");
		else connectorNode.setAttribute(ATTRNAME_NS, ns);
		if (StringUtils.isNullOrEmpty(mid) && connectorNode.hasAttribute(ATTRNAME_NAME_TRANFORMER)) connectorNode.removeAttribute(ATTRNAME_NAME_TRANFORMER);
		if (!StringUtils.isNullOrEmpty(mid)) connectorNode.setAttribute(ATTRNAME_NAME_TRANFORMER, mid);

		for (final CP cp : confParams.keySet()) {

			final Part part = confParams.get(cp);
			Element cpNode = (Element) xpath.evaluate(String.format("%s[@%s='%s']", TAGNAME_CONNECTOR_PARAM, ATTRNAME_CONNECTOR_PARAM_NAME, cp.name), connectorNode, XPathConstants.NODE);
			final boolean isNewCP = cpNode == null;
			if (isNewCP) {
				cpNode = doc.createElement(TAGNAME_CONNECTOR_PARAM);
				cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_NAME, cp.name);
				connectorNode.appendChild(cpNode);
			}

			if (CP.isMultivalue(cp) && CP.isEncrypted(cp)) {// Special processing for fields that are encrypted AND multivalue
				if (isNewCP) {
					final List<String> valsFromUI = CPUtils.stringToList(HttpUtils.partToString(cs, part));
					final int size = valsFromUI.size();
					for (int i = 0; i < size; i++)
						valsFromUI.set(i, pm.encrypt(valsFromUI.get(i)));
					cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, CPUtils.listToString(valsFromUI));
				} else {
					LOG.info("Updating Multivalue/Encrypted field");
					final List<String> valsFromUI = CPUtils.stringToList(HttpUtils.partToString(cs, part));
					LOG.info("valsFromUI: " + valsFromUI);
					final List<String> valsFromConf = CPUtils.stringToList(cpNode.getAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE));
					LOG.info("valsFromConf: " + valsFromConf);

					final int sizeFromUI = valsFromUI.size();

					// Add empty elements to the conf values if UI contains more elements
					final String novalue = pm.encrypt("");
					while (valsFromConf.size() < sizeFromUI)
						valsFromConf.add(novalue);

					// Remove elements from conf values if UI contains less elements
					while (valsFromConf.size() > sizeFromUI)
						valsFromConf.remove(valsFromConf.size() - 1);

					for (int i = 0; i < sizeFromUI; i++) {
						LOG.info(String.format("Comparing _%s_ with _%s_", valsFromUI.get(i), Constants.DO_NOT_CHANGE));
						if (!Constants.DO_NOT_CHANGE.equals(valsFromUI.get(i))) {
							LOG.info("-> diff");
							valsFromConf.set(i, pm.encrypt(valsFromUI.get(i)));
						}
					}
					cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, CPUtils.listToString(valsFromConf));
				}

			} else if (cp.type == CPType.FILE) {
				LOG.info("Updating File field: " + cp.label);
				if (part == null) {
					LOG.info("File field " + cp.label + " is empty");
				} else {
					@SuppressWarnings("resource")
					InputStream partIs = part.getInputStream();
					if (partIs == null) {
						LOG.info("File field " + cp.label + " is empty");
					} else {
						final File tmpFile = File.createTempFile("sword-scs", ".bin");
						final Path tmpFilePath = tmpFile.toPath();
						Files.copy(partIs, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
						if (tmpFile.length() > 1) {
							final Path instanceConfPath = cc.getIndexerConfDir(cid);
							final Path confFilePath = instanceConfPath.resolve(HexUtils.toHexString(cp.name.getBytes()));
							Files.move(tmpFilePath, confFilePath, StandardCopyOption.REPLACE_EXISTING);
							cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, confFilePath.toString());
							LOG.info("Copied " + tmpFile.length() + "bytes");
						} else {
							LOG.info("File field " + cp.label + " is empty");
							if (!tmpFile.delete()) tmpFile.deleteOnExit();
						}
					}
				}
			} else if (cp.type == CPType.BOOLEAN) {
				if (CP.isMultivalue(cp)) {
					final List<String> ss = CPUtils.stringToList(HttpUtils.partToString(cs, part));
					final int s = ss.size();
					for (int i = 0; i < s; i++)
						ss.set(i, Boolean.toString("on".equals(ss.get(i))));
					cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, CPUtils.listToString(ss));
				} else cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, Boolean.toString("on".equals(HttpUtils.partToString(cs, part))));
			} else {
				final String valFromUI = HttpUtils.partToString(cs, part);
				if (CP.isEncrypted(cp) && Constants.DO_NOT_CHANGE.equals(valFromUI)) {
					// Do nothing - keep current value
				} else if (CP.isEncrypted(cp) && valFromUI != null) cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, pm.encrypt(valFromUI));
				else if (valFromUI != null) cpNode.setAttribute(ATTRNAME_CONNECTOR_PARAM_VALUE, valFromUI);
			}
		}

		saveConfFile(confFile, doc);

	}
	
	public static void updateFTP(Path confFile, String ftpHost, String ftpUsername, String ftpPassword) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		final Element newFtpElement = doc.createElement(TAGNAME_FTP);
		if (ftpHost != null) {
			newFtpElement.setAttribute(ATTRNAME_FTPMODE, "true");
			newFtpElement.setAttribute(ATTRNAME_FTP_HOST, ftpHost);
			newFtpElement.setAttribute(ATTRNAME_FTP_USERNAME, ftpUsername);
			newFtpElement.setAttribute(ATTRNAME_FTP_PASSWORD, ftpPassword);

		}

		final Element oldFtpElement = (Element) xpath.evaluate(TAGNAME_FTP, rootNode, XPathConstants.NODE);
		if (oldFtpElement == null) {
			if (ftpHost != null) rootNode.appendChild(newFtpElement);
		} else {
			if (ftpHost == null) rootNode.removeChild(oldFtpElement);
			else rootNode.replaceChild(newFtpElement, oldFtpElement);
		}

		saveConfFile(confFile, doc);
	}

	public static void updateSB(final Path confFile, final String custID, final String sourceID, String sourceName, String filename) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();
		final Element rootNode = doc.getDocumentElement();
		final Element newGsaElement = doc.createElement(TAGNAME_SB);

		newGsaElement.setAttribute(ATTRNAME_SB_SOURCE_ID, sourceID);
		newGsaElement.setAttribute(ATTRNAME_SB_SOURCE_NAME, sourceName);
		newGsaElement.setAttribute(ATTRNAME_SB_CUST_ID, custID);
		newGsaElement.setAttribute(ATTRNAME_SB_KEYPATH, filename);
		rootNode.appendChild(newGsaElement);
		saveConfFile(confFile, doc);
	}

	public static void updateGsa(final Path confFile, final GSAInfo gsa) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		final Element newGsaElement = doc.createElement(TAGNAME_GSA);
		if (gsa != null) {
			newGsaElement.setAttribute(ATTRNAME_SSL, gsa.ssl ? "true" : "false");
			newGsaElement.setAttribute(ATTRNAME_DEFAULT_HOST, gsa.defaultHost);
			newGsaElement.setAttribute(ATTRNAME_ADMIN_HOST, gsa.adminHost);
			newGsaElement.setAttribute(ATTRNAME_SYS, gsa.system);
			newGsaElement.setAttribute(ATTRNAME_SOFT, gsa.software);
			newGsaElement.setAttribute(ATTRNAME_ID, gsa.id);
		}

		final Element oldGsaElement = (Element) xpath.evaluate(TAGNAME_GSA, rootNode, XPathConstants.NODE);
		if (oldGsaElement == null) {
			if (gsa != null) rootNode.appendChild(newGsaElement);
		} else {
			if (gsa == null) rootNode.removeChild(oldGsaElement);
			else rootNode.replaceChild(newGsaElement, oldGsaElement);
		}
		//also, we are not in FTP mode
		final Element oldFtpElement = (Element) xpath.evaluate(TAGNAME_FTP, rootNode, XPathConstants.NODE);
		if (oldFtpElement != null) oldFtpElement.setAttribute(ATTRNAME_FTPMODE, "false");
		
		saveConfFile(confFile, doc);

	}

	public static void updateAuthentConfig(final Path confFile, final String samlType, final String trustDurationStr, String entityId, final boolean kerberosAuthN, final boolean groupRetrieval, String cmMainConnectorName, final String authenticatorStr) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		final Element newAuthentElement = doc.createElement(TAGNAME_AUTHN);

		{// SAML mode
			SamlMode samlMode = AuthenticatorConf.DEFAULT_SAML_MODE;
			if (SamlMode.POST_BINDING.name().equals(samlType)) samlMode = SamlMode.POST_BINDING;
			else if (SamlMode.ARTIFACT_BINDING.name().equals(samlType)) samlMode = SamlMode.ARTIFACT_BINDING;
			else {
				LOG.warn(String.format("Invalid SAML mode supplied (%s) - using default", samlType));
				samlMode = AuthenticatorConf.DEFAULT_SAML_MODE;
			}
			newAuthentElement.setAttribute(ATTRNAME_SAML_TYPE, samlMode.name());
		}

		{// Trust duration
			long trustDuration = AuthenticatorConf.DEFAULT_TRUST_DURATION;
			try {
				trustDuration = Long.parseLong(trustDurationStr);
				trustDuration = TimeUnit.MINUTES.toMillis(trustDuration);
			} catch (final Exception e) {
				LOG.warn(String.format("Invalid trust duration supplied (%s) - using default", trustDurationStr));
				trustDuration = AuthenticatorConf.DEFAULT_TRUST_DURATION;
			}
			newAuthentElement.setAttribute(ATTRNAME_TRUST_DUR, Long.toString(trustDuration));
		}

		{// Entity ID
			if (StringUtils.isNullOrEmpty(entityId)) {
				LOG.warn("No SAML entity ID supplied - using default");
				entityId = AuthenticatorConf.DEFAULT_ENTITY_ID;
			}
			newAuthentElement.setAttribute(ATTRNAME_ENT_ID, entityId);
		}

		// Kerberos
		newAuthentElement.setAttribute(ATTRNAME_KRB, Boolean.toString(kerberosAuthN));

		// Group Retrieval
		newAuthentElement.setAttribute(ATTRNAME_GROUP_RETR, Boolean.toString(groupRetrieval));

		{// CM main connector name
			if (StringUtils.isNullOrEmpty(cmMainConnectorName)) {
				LOG.warn("No CM main connector name supplied - using default");
				cmMainConnectorName = AuthenticatorConf.DEFAULT_CM_MAIN_CONN_NAME;
			}
			newAuthentElement.setAttribute(ATTRNAME_CM_MAIN_CONN_NAME, cmMainConnectorName);
		}

		if (!StringUtils.isNullOrEmpty(authenticatorStr)) newAuthentElement.setAttribute(TAG_OR_ATTR_CONNECTOR, authenticatorStr);

		final Element oldAuthentElement = (Element) xpath.evaluate(TAGNAME_AUTHN, rootNode, XPathConstants.NODE);
		if (oldAuthentElement == null) rootNode.appendChild(newAuthentElement);
		else rootNode.replaceChild(newAuthentElement, oldAuthentElement);

		saveConfFile(confFile, doc);

	}

	public static void addOrUpdateGroupRetriever(final Path confFile, final String groupRetrieverID, final long cacheRefreshInterval) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {

		if (StringUtils.isNullOrEmpty(groupRetrieverID)) {
			LOG.info(String.format("Invalid group retrieval connector supplied (%s) - discarding", groupRetrieverID));
			return;
		}

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		// Remove old group retriever definition
		final NodeList groupRetrieverNodes = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_GROUP_RETR, rootNode, XPathConstants.NODESET);
		if (groupRetrieverNodes != null) {
			final int groupRetrieverNodesCount = groupRetrieverNodes.getLength();
			if (groupRetrieverNodesCount > 0) for (int i = 0; i < groupRetrieverNodesCount; i++) {
				final Element grElement = (Element) groupRetrieverNodes.item(i);
				final String grId = grElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
				if (grId.equals(groupRetrieverID)) rootNode.removeChild(grElement);
			}
		}

		// Add new group retriever definition
		final Element grElement = doc.createElement(TAGNAME_GROUP_RETR);
		grElement.setAttribute(TAG_OR_ATTR_CONNECTOR, groupRetrieverID);
		grElement.setAttribute(ATTRNAME_CACHE_REFRESH_INT, Long.toString(cacheRefreshInterval));
		rootNode.appendChild(grElement);

		saveConfFile(confFile, doc);

	}

	public static void removeGroupRetriever(final Path confFile, final String groupRetrieverID) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {

		if (StringUtils.isNullOrEmpty(groupRetrieverID)) {
			LOG.info(String.format("Invalid group retrieval connector supplied (%s) - discarding", groupRetrieverID));
			return;
		}

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		boolean existed = false;

		// Remove old group retriever definition
		final NodeList groupRetrieverNodes = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_GROUP_RETR, rootNode, XPathConstants.NODESET);
		if (groupRetrieverNodes != null) {
			final int groupRetrieverNodesCount = groupRetrieverNodes.getLength();
			if (groupRetrieverNodesCount > 0) for (int i = 0; i < groupRetrieverNodesCount; i++) {
				final Element grElement = (Element) groupRetrieverNodes.item(i);
				final String grId = grElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
				if (grId.equals(groupRetrieverID)) {
					existed = true;
					rootNode.removeChild(grElement);
				}
			}
		}

		if (existed) saveConfFile(confFile, doc);

	}

	public static void addOrUpdateIndexer(final Path confFile, final IndexerConf newIndexer) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {

		if (StringUtils.isNullOrEmpty(newIndexer.connectorId)) {
			LOG.info(String.format("Invalid indexing connector supplied (%s) - discarding", newIndexer.connectorId));
			return;
		}

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		// Remove old indexer definition
		final NodeList indexerNodes = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_INDEXER, rootNode, XPathConstants.NODESET);
		if (indexerNodes != null) {
			final int indexerNodesCount = indexerNodes.getLength();
			if (indexerNodesCount > 0) for (int i = 0; i < indexerNodesCount; i++) {
				final Element indexerElement = (Element) indexerNodes.item(i);
				final String indexerId = indexerElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
				if (indexerId.equals(newIndexer.connectorId)) rootNode.removeChild(indexerElement);
			}
		}

		// Add new indexer definition
		final Element iElement = doc.createElement(TAGNAME_INDEXER);
		iElement.setAttribute(TAG_OR_ATTR_CONNECTOR, newIndexer.connectorId);
		iElement.setAttribute(ATTRNAME_INDEXER_INTERVAL, Long.toString(newIndexer.interval));
		iElement.setAttribute(ATTRNAME_INDEXER_THROUGHPUT, Long.toString(newIndexer.throughput));
		rootNode.appendChild(iElement);
		if (!(newIndexer.schedule == null || newIndexer.schedule.periods == null)) for (final Period p : newIndexer.schedule.periods) {
			final Element spElement = doc.createElement(TAGNAME_SCHEDULE_PERIOD);
			spElement.setAttribute(ATTRNAME_SCHED_PERIOD_DAY, Integer.toString(p.day));
			spElement.setAttribute(ATTRNAME_SCHED_PERIOD_START, Integer.toString(p.startHour));
			spElement.setAttribute(ATTRNAME_SCHED_PERIOD_DUR, Integer.toString(p.duration));
			iElement.appendChild(spElement);
		}

		saveConfFile(confFile, doc);
	}

	public static void removeIndexer(final Path confFile, final String indexerID) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {

		if (StringUtils.isNullOrEmpty(indexerID)) {
			LOG.info(String.format("Invalid indexing connector supplied (%s) - discarding", indexerID));
			return;
		}

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = doc.getDocumentElement();

		boolean existed = false;

		// Remove old group retriever definition
		final NodeList indexerNodes = (NodeList) xpath.evaluate(SCSConfiguration.TAGNAME_INDEXER, rootNode, XPathConstants.NODESET);
		if (indexerNodes != null) {
			final int indexerNodesCount = indexerNodes.getLength();
			if (indexerNodesCount > 0) for (int i = 0; i < indexerNodesCount; i++) {
				final Element indexerElement = (Element) indexerNodes.item(i);
				final String indexerId = indexerElement.getAttribute(SCSConfiguration.TAG_OR_ATTR_CONNECTOR);
				if (indexerId.equals(indexerID)) {
					existed = true;
					rootNode.removeChild(indexerElement);
				}
			}
		}

		if (existed) saveConfFile(confFile, doc);

	}

	public static void deleteConnectorConfig(final Path confFile, final String cid) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(confFile.toFile());
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Element rootNode = (Element) xpath.evaluate("/".concat(TAGNAME_ROOT), doc, XPathConstants.NODE);

		final Element connectorNode = (Element) xpath.evaluate(String.format("%s[@%s='%s']", TAG_OR_ATTR_CONNECTOR, ATTRNAME_ID, cid.replace('\'', '_')), rootNode, XPathConstants.NODE);
		final Element grNode = (Element) xpath.evaluate(String.format("%s[@%s='%s']", TAGNAME_GROUP_RETR, TAG_OR_ATTR_CONNECTOR, cid.replace('\'', '_')), rootNode, XPathConstants.NODE);
		final Element iNode = (Element) xpath.evaluate(String.format("%s[@%s='%s']", TAGNAME_INDEXER, TAG_OR_ATTR_CONNECTOR, cid.replace('\'', '_')), rootNode, XPathConstants.NODE);
		boolean saveConfig = !((connectorNode == null) && (grNode == null) && (iNode == null));
		if (connectorNode != null) rootNode.removeChild(connectorNode);
		if (grNode != null) rootNode.removeChild(grNode);
		if (iNode != null) rootNode.removeChild(iNode);
		if (saveConfig) saveConfFile(confFile, doc);

	}

	private static void saveConfFile(final Path confFile, final Document doc) throws TransformerFactoryConfigurationError, TransformerException, IOException {
		final Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		t.setOutputProperty(OutputKeys.INDENT, "yes");
		t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		try (FileOutputStream fos = new FileOutputStream(confFile.toFile())) {
			t.transform(new DOMSource(doc), new StreamResult(fos));
		}
	}



}