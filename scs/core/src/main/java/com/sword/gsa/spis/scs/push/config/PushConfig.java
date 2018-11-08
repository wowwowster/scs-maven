package com.sword.gsa.spis.scs.push.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;

import sword.common.utils.StringUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;
import sword.connectors.commons.config.CPUtils;
import sword.gsa.xmlfeeds.builder.Authmethod;
import sword.gsa.xmlfeeds.builder.Metadata;

public final class PushConfig {

	public static final Authmethod DEFAULT_AUTH_MTHD = Authmethod.httpsso;
	public static final int DEFAULT_MAX_THREADS = 2;
	public static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
	public static final String DEFAULT_FEED_DATES_FORMAT = "yyyyMMdd";
	public static final long DEFAULT_MAX_CONTENT_SIZE = 100_000_000L;
	public static final long DEFAULT_MAX_FEED_SIZE = 100_000_000L;
	public static final int DEFAULT_CONS_PERIOD = 0;

	private static final String DOCTYPE = "conf";

	// configuration.xml parameters names

	// Feed files configuration
	public static final String FEEDS_CONSERVATION_PERIOD_PARAM_NAME = "FeedsFilesConservationPeriod";
	public static final String MAX_FEED_SIZE_PARAM_NAME = "MaxFeedSize";
	public static final String MAX_CONTENT_SIZE_PARAM_NAME = "MaxContentSize";
	public static final String FEED_DATES_FORMAT_PARAM_NAME = "FeedDatesFormat";

	// Misc
	public static final String UNSUPPORTED_MIMES = "UnsupportedMimeTypes";
	public static final String CONSTANT_META_TAGS = "ContantMetaTags";
	public static final String META_TAGS_RENAMING = "MetaTagsRenaming";
	public static final String SO_TIMEOUT_PARAM_NAME = "SocketTimeout";
	public static final String SB_SOURCE = "CloudSource";

	// Threading
	public static final String MAX_INDEXING_THREADS = "MaxIndexingThreads";

	// ACL configuration
	public static final String AUTH_METHOD_PARAM_NAME = "AuthMethod";
	public static final String ACL_SUPER_USER_PARAM_NAME = "SuperUserGroup";
	public static final String ACL_USERS_AS_GROUPS_PARAM_NAME = "ACLUsersAsGroups";
	public static final String ACL_PUBLIC_GROUPS = "PublicGroups";

	//Non-config - passed at Java runtime
	public static final String JAVA_OPTIONS = "JavaOptions";

	public static final CP[] CONFIGURATION_PARAMETERS = new CP[] {
		new CP(CPType.ENUM, SB_SOURCE, "Cloud Source", "Select the cloud source to push the contents of this connector to", new String[] {}),
		new CP(CPType.DECIMAL, FEEDS_CONSERVATION_PERIOD_PARAM_NAME, "Feed files conservation period <i>(days)</i>", "Defines how long feed files will be stored on disk. 0 &lt;=&gt; immediate delete. Defaults to 0."),
		new CP(CPType.DECIMAL, MAX_FEED_SIZE_PARAM_NAME, "Max feed size <i>(MB)</i>", "The maximum size for a feed file. GSA does not support files larger than 1GB. Defaults to 100MB."),
		new CP(CPType.DECIMAL, MAX_CONTENT_SIZE_PARAM_NAME, "Max document size <i>(MB)</i>", "Maximum size for an individual document content. Defaults to 100MB."),
		new CP(CPType.STRING, FEED_DATES_FORMAT_PARAM_NAME, "Feed dates format", "Format for date metadata tags. Defaults to yyyyMMdd."),
		new CP(CPType.STRING, UNSUPPORTED_MIMES, "Excluded MIME types", "List of MIME types that should not be indexed.", CP.MULTIVALUE),
		new CP(CPType.STRING, CONSTANT_META_TAGS, "Constant meta tags", "List of metadata tags that will be added to all documents (observing format: name=value ; name cannot contain an equal sign).", CP.MULTIVALUE),
		new CP(CPType.STRING, META_TAGS_RENAMING, "Meta tags renaming", "List of meta tags which name should be changed. For instance if you want the meta tag named Creator to be change to Author, enter Creator=Author", CP.MULTIVALUE),
		new CP(CPType.DECIMAL, SO_TIMEOUT_PARAM_NAME, "Socket timeout <i>(seconds)</i>", "Socket timeout configured for the HTTP client. Defaults to 1 minute."),
		new CP(CPType.DECIMAL, MAX_INDEXING_THREADS, "Max indexing threads", "Size of the thread pool dedicated to indexing. Defaults to 2."),
		new CP(CPType.ENUM, AUTH_METHOD_PARAM_NAME, "Auth method", "Authentication method. Defaults to httpsso.", new String[] {DEFAULT_AUTH_MTHD.name(), Authmethod.none.name()}), 
		new CP(CPType.STRING, ACL_SUPER_USER_PARAM_NAME, "Super-user group", "If a group has access to all documents, specify this parameter and the group will be added to all documents ACLs."),
		new CP(CPType.BOOLEAN, ACL_USERS_AS_GROUPS_PARAM_NAME, "User ACEs as Group ACEs",
			"When indexing multiple repositories, if a user has a different names accross repositories, indexing user ACEs as group ACEs is necessary because the GSA only allows one verified identity per credential group."),
			new CP(CPType.STRING, ACL_PUBLIC_GROUPS, "Public groups", "List of groups connector will consider as public. If a document belongs to one of these groups, it will be sent as public document to the GSA (authmethod=none).", CP.MULTIVALUE),
		new CP(CPType.STRING, JAVA_OPTIONS, "JAVA Options", "Misc options that are passed JAVA at runtime (-D options, -XX options, ...)", CP.MULTIVALUE)
	};

	public final IConnectorContext connectorCtx;
	public final String connectorId;

	public final int feedsConservationPeriod;
	public final long maxFeedSize;
	public final long maxContentSize;
	public final ThreadSafeDateFormat feedDatesFormat;

	public final Collection<String> unsupportedMimeTypes = new HashSet<>();
	public final Collection<Metadata> constants = new ArrayList<>();
	public final Collection<Metadata> metaRenaming = new ArrayList<>();
	public final long httpClientTimeout;

	public final int maxIndexingThreads;

	public final String aclNamespace;
	public final String aclSuperUserGroup;
	public final boolean aclUsersAsGroups;
	public final List<String> publicGroups = new ArrayList<>();

	public final boolean sslGsa;
	public final String gsaHostName;
	public final String datasource;
	public final Authmethod authMethod;

	public final PushType pushType;

	public final long endTime;

	public final File feedsFolder;

	public final List<String> javaOptions = new ArrayList<>();
	public final boolean ftpMode;
	public final String ftpUsername;
	public final String ftpPassword;

	public final String cloudsource;


	public PushConfig(
			final IConnectorContext connectorCtx, final String connectorId,
			boolean initializeFeedDirectories,
			final int feedsConservationPeriod, final long maxFeedSize, final long maxContentSize, final SimpleDateFormat feedDatesFormat,
			final List<String> unsupportedMimeTypes, final List<String> constants, final List<String> metaRenaming, final long httpClientTimeout,
			final int maxIndexingThreads,
			final Authmethod authMethod, final String aclNamespace, final String aclSuperUserGroup, final boolean aclUsersAsGroups,
			final boolean sslGsa, final String gsaHost, boolean ftpMode, final String ftpUsername, final String ftpPassword, final String datasource,
			final PushType pushType, final long endTime, final List<String> javaOptions, final List<String> publicGroups,
			final String cloudsource) {
		super();

		this.connectorCtx = connectorCtx;
		this.connectorId = connectorId;

		this.feedsConservationPeriod = feedsConservationPeriod;
		this.maxFeedSize = maxFeedSize;
		this.maxContentSize = maxContentSize;
		this.feedDatesFormat = new ThreadSafeDateFormat(feedDatesFormat);
		this.cloudsource = cloudsource;

		this.unsupportedMimeTypes.addAll(unsupportedMimeTypes);
		String[] nv;
		for (final String c : constants) {
			nv = c.split("=", 2);
			this.constants.add(new Metadata(nv[0], nv[1]));
		}
		for (final String c : metaRenaming) {
			nv = c.split("=", 2);
			this.metaRenaming.add(new Metadata(nv[0], nv[1]));
		}
		this.httpClientTimeout = httpClientTimeout;
		
		this.maxIndexingThreads = maxIndexingThreads;

		this.aclNamespace = aclNamespace;
		this.aclSuperUserGroup = aclSuperUserGroup;
		this.aclUsersAsGroups = aclUsersAsGroups;
		this.publicGroups.addAll(publicGroups);
		this.sslGsa = sslGsa;
		final String altGsaHostName = System.getProperty("sword.indexer.AltFeederGate", "none");
		this.gsaHostName = "none".equals(altGsaHostName) ? gsaHost : altGsaHostName;
		this.ftpMode = ftpMode;
		this.ftpUsername=ftpUsername;
		this.ftpPassword=ftpPassword;
		this.datasource = datasource;
		this.authMethod = authMethod;

		this.pushType = pushType;

		this.endTime = endTime;

		if (initializeFeedDirectories) feedsFolder = prepareFolders(this.connectorCtx.getIndexerFeedDir(this.connectorId).toFile(), this.feedsConservationPeriod);
		else feedsFolder = this.connectorCtx.getIndexerFeedDir(this.connectorId).toFile();
		
		this.javaOptions.addAll(javaOptions);
	}

	private static File prepareFolders(final File feedsFolder, int feedsConservationPeriod) {

		if (!feedsFolder.exists()) feedsFolder.mkdirs();
		final Path feedsFolderPath = feedsFolder.toPath();

		final SimpleDateFormat sdf = new SimpleDateFormat(DEFAULT_FEED_DATES_FORMAT);
		final long nowTime = System.currentTimeMillis();
		final Date nowDate = new Date(nowTime);
		if (feedsConservationPeriod < 0) feedsConservationPeriod = 0;

		if (feedsConservationPeriod > 0) {// Delete only folders that are too old
			final long consPeriod = TimeUnit.DAYS.toMillis(feedsConservationPeriod);
			Date tmp = null;
			final String[] subfolders = feedsFolder.list();
			if (subfolders != null) 
				for (final String folderName : subfolders) {
					try {
						tmp = sdf.parse(folderName);
						if ((nowTime - tmp.getTime()) > consPeriod) 
							try {
								Files.walkFileTree(feedsFolderPath.resolve(folderName), new FileTreeDeleter());
							} catch (final IOException ignored) {}
					} catch (final Exception e) {
						continue;//just not a feed folder
					}
				}
		} else {
			try {
				Files.walkFileTree(feedsFolderPath, new FileTreeDeleter(feedsFolderPath, false));
			} catch (final IOException ignored) {}
		}

		final File dateFolder = new File(feedsFolder, sdf.format(nowDate));
		if (dateFolder.exists()) dateFolder.mkdirs();
		return dateFolder;

	}

	public static PushConfig readConfiguration(final IConnectorContext connectorCtx, final String connectorId, final boolean sslGsa, final String gsaHost, final boolean ftpMode, final String ftpUsername, final String ftpPassword, final PushType pt, final String aclNamespace, final String datasource, final long endTime, final boolean initializeFeedDirectories) throws ConfigurationException, FileNotFoundException, IOException, SAXException, ParserConfigurationException {

		final File confFile = connectorCtx.getIndexerConfFile(connectorId).toFile();
		if (!confFile.exists()) throw new ConfigurationException("The file " + confFile.getAbsolutePath() + " could not be found");

		int feedsConservationPeriod = DEFAULT_CONS_PERIOD;
		long maxFeedSize = DEFAULT_MAX_FEED_SIZE;
		long maxContentSize = DEFAULT_MAX_CONTENT_SIZE;
		SimpleDateFormat feedDates = new SimpleDateFormat(DEFAULT_FEED_DATES_FORMAT);
		final List<String> umt = new ArrayList<>();
		final List<String> constants = new ArrayList<>();
		final List<String> metaRenaming = new ArrayList<>();
		long soTimeout = DEFAULT_TIMEOUT;
		int maxIndexingThreads = DEFAULT_MAX_THREADS;
		String aclSuperuser = "";
		boolean usersAsGroups = true;
		Authmethod authMethd = DEFAULT_AUTH_MTHD;
		final List<String> javaOpts = new ArrayList<>();
		final List<String> publicGroups = new ArrayList<>();
		String cloudSource = "";

		try (FileInputStream fileInputStream = new FileInputStream(confFile)) {
			final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fileInputStream).getDocumentElement();
			final NodeList nl = root.getChildNodes();
			final int len = nl.getLength();
			Element el = null;
			String name = null;
			for (int i = 0; i < len; i++)
				if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
					el = (Element) nl.item(i);
					name = el.getNodeName();
					if (FEEDS_CONSERVATION_PERIOD_PARAM_NAME.equals(name)) feedsConservationPeriod = Integer.parseInt(el.getTextContent());
					else if (MAX_FEED_SIZE_PARAM_NAME.equals(name)) maxFeedSize = Long.parseLong(el.getTextContent());
					else if (MAX_CONTENT_SIZE_PARAM_NAME.equals(name)) maxContentSize = Long.parseLong(el.getTextContent());
					else if (FEED_DATES_FORMAT_PARAM_NAME.equals(name)) feedDates = new SimpleDateFormat(el.getTextContent());
					else if (UNSUPPORTED_MIMES.equals(name)) {
						String umtStr = el.getTextContent();
						if (!StringUtils.isNullOrEmpty(umtStr)) umt.addAll(CPUtils.stringToList(umtStr));
					} else if (CONSTANT_META_TAGS.equals(name)) {
						String constantsStr = el.getTextContent();
						if (!StringUtils.isNullOrEmpty(constantsStr)) constants.addAll(CPUtils.stringToList(constantsStr));
					} else if (META_TAGS_RENAMING.equals(name)) {
						String renameStr = el.getTextContent();
						if (!StringUtils.isNullOrEmpty(renameStr)) metaRenaming.addAll(CPUtils.stringToList(renameStr));
					} else if (SO_TIMEOUT_PARAM_NAME.equals(name)) soTimeout = Long.parseLong(el.getTextContent());
					else if (MAX_INDEXING_THREADS.equals(name)) maxIndexingThreads = Integer.parseInt(el.getTextContent());
					else if (ACL_SUPER_USER_PARAM_NAME.equals(name)) aclSuperuser = el.getTextContent();
					else if (ACL_USERS_AS_GROUPS_PARAM_NAME.equals(name)) usersAsGroups = Boolean.parseBoolean(el.getTextContent());
					else if (AUTH_METHOD_PARAM_NAME.equals(name)) authMethd = Authmethod.resolve(el.getTextContent());
					else if (JAVA_OPTIONS.equals(name)) {
						String javaOptsStr = el.getTextContent();
						if (!StringUtils.isNullOrEmpty(javaOptsStr)) javaOpts.addAll(CPUtils.stringToList(javaOptsStr));
					}
					else if (ACL_PUBLIC_GROUPS.equals(name)) {
						String publicGroupsStr = el.getTextContent();
						if (!StringUtils.isNullOrEmpty(publicGroupsStr)) publicGroups.addAll(CPUtils.stringToList(publicGroupsStr));
					}
					else if (SB_SOURCE.equals(name)){
						cloudSource = el.getTextContent();
					}
				}
		}

		return new PushConfig(
			connectorCtx, connectorId, 
			initializeFeedDirectories, 
			feedsConservationPeriod, maxFeedSize, maxContentSize, feedDates, 
			umt, constants, metaRenaming, soTimeout, 
			maxIndexingThreads,
			authMethd, aclNamespace, aclSuperuser, usersAsGroups, 
			sslGsa, gsaHost, ftpMode, ftpUsername, ftpPassword, datasource, 
			pt, endTime, javaOpts, publicGroups,
				cloudSource);
	}

	public static void saveAllConfig(final File confFile, final PushConfig cs) throws ParserConfigurationException, TransformerException, FileNotFoundException, IOException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element rootEl = doc.createElement(DOCTYPE);
		doc.appendChild(rootEl);

		rootEl.appendChild(doc.createComment("Feed files configuration"));
		addNode(doc, rootEl, SB_SOURCE, cs.cloudsource);
		addNode(doc, rootEl, FEEDS_CONSERVATION_PERIOD_PARAM_NAME, Integer.toString(cs.feedsConservationPeriod));
		addNode(doc, rootEl, MAX_FEED_SIZE_PARAM_NAME, Long.toString(cs.maxFeedSize));
		addNode(doc, rootEl, MAX_CONTENT_SIZE_PARAM_NAME, Long.toString(cs.maxContentSize));
		addNode(doc, rootEl, FEED_DATES_FORMAT_PARAM_NAME, cs.feedDatesFormat.toPattern());

		rootEl.appendChild(doc.createComment("Misc"));
		List<String> unsupportedMimeTypes = new ArrayList<>();
		for (String mt: cs.unsupportedMimeTypes) unsupportedMimeTypes.add(mt);
		addNode(doc, rootEl, UNSUPPORTED_MIMES, CPUtils.listToString(unsupportedMimeTypes));
		List<String> constants = new ArrayList<>();
		for (Metadata md: cs.constants) constants.add(md.name+"="+md.value);
		addNode(doc, rootEl, CONSTANT_META_TAGS, CPUtils.listToString(constants));
		List<String> mdrename = new ArrayList<>();
		for (Metadata md: cs.metaRenaming) mdrename.add(md.name+"="+md.value);
		addNode(doc, rootEl, META_TAGS_RENAMING, CPUtils.listToString(mdrename));
		addNode(doc, rootEl, SO_TIMEOUT_PARAM_NAME, Long.toString(cs.httpClientTimeout));

		rootEl.appendChild(doc.createComment("Threading"));
		addNode(doc, rootEl, MAX_INDEXING_THREADS, Integer.toString(cs.maxIndexingThreads));

		rootEl.appendChild(doc.createComment("ACL configuration"));
		addNode(doc, rootEl, ACL_SUPER_USER_PARAM_NAME, cs.aclSuperUserGroup);
		addNode(doc, rootEl, ACL_USERS_AS_GROUPS_PARAM_NAME, Boolean.toString(cs.aclUsersAsGroups));
		addNode(doc, rootEl, ACL_PUBLIC_GROUPS, CPUtils.listToString(cs.publicGroups));
		rootEl.appendChild(doc.createComment("GSA configuration"));
		addNode(doc, rootEl, AUTH_METHOD_PARAM_NAME, cs.authMethod.toString());

		rootEl.appendChild(doc.createComment("Non-config - passed at Java runtime"));
		addNode(doc, rootEl, JAVA_OPTIONS, CPUtils.listToString(cs.javaOptions));

		try (FileOutputStream fos = new FileOutputStream(confFile)) { saveXML(fos, doc); }

	}

	private static void addNode(final Document doc, final Element rootEl, final String name, final String val) {
		if (val == null) return;
		final Element el = doc.createElement(name);
		el.setTextContent(val);
		rootEl.appendChild(el);
	}

	private static void saveXML(final FileOutputStream fos, final Document doc) throws TransformerException {
		final DOMSource domSource = new DOMSource(doc);
		final StreamResult result = new StreamResult(fos);
		final TransformerFactory tf = TransformerFactory.newInstance();
		final Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.transform(domSource, result);
	}
}
