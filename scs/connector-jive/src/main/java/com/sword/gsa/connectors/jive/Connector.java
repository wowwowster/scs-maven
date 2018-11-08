package com.sword.gsa.connectors.jive;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

import sword.common.http.client.HttpClientHelper;
import sword.common.utils.StringUtils;
import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;
import sword.connectors.commons.config.CPUtils;
import sword.connectors.commons.config.ConnectorSpec;

import com.sword.gsa.connectors.jive.apiwrap.ApiHelper;
import com.sword.gsa.connectors.jive.apiwrap.RunAsStrategy;
import com.sword.gsa.connectors.jive.apiwrap.security.Group;
import com.sword.gsa.connectors.jive.apiwrap.security.User;
import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;

@ConnectorSpec(name = "Jive", version = "2.2.2")
public class Connector extends AConnector implements ICachableGroupRetriever, Indexer {

	static final String ID_PREFIX_ATTACHMENT = "A";
	static final String ID_PREFIX_POST = "B";
	static final String ID_PREFIX_DOCUMENT = "C";
	static final String ID_PREFIX_DISCUSSION = "D";
	static final String ID_PREFIX_EVENT = "E";
	static final String ID_PREFIX_FILE = "F";
	static final String ID_PREFIX_IDEA = "I";
	static final String ID_PREFIX_MESSAGE = "M";
	static final String ID_PREFIX_PLACE = "P";
	static final String ID_PREFIX_POLL = "S";
	static final String ID_PREFIX_TASK = "T";
	static final String ID_PREFIX_UPDATE = "U";
	static final String ID_PREFIX_VIDEO = "V";
	static final String ID_PREFIX_BOOKMARK = "Y";

	public static final String JIVE_COMMUNITY_NAME = "Jive Community";

	static final String PARAM_JIVE_HOST = "Host";
	static final String PARAM_JIVE_PORT = "Port";
	static final String PARAM_SVC_ACCOUNT = "SvcAccount";
	static final String PARAM_SVC_ACCOUNT_PWD = "SvcAccountPwd";
	static final String PARAM_IS_GUEST_ALLOWED = "IsGuestAllowed";
	static final String PARAM_ACL_FORMAT = "AclFormat";
	static final String PARAM_DISCUSS_THREAD_AS_ONE_ITEM = "DiscussThreadAsOneItem";
	static final String PARAM_SPECIAL_PERMISSIONS_FILE = "SpecialPermissionsFile";
	static final String PARAM_JIVE_VERSION = "JiveVersion";
	static final String PARAM_RUN_AS = "RunAsStrategy";
	static final String PARAM_PLACE_FILTER = "PlaceFilter";

	public static final CP[] CONFIGURATION_PARAMETERS = new CP[] {
		new CP(CPType.STRING, PARAM_JIVE_HOST, "Jive Host", "Fully-qualified hostname of the Jive server (for example, <i>company.jiveon.com</i> for a cloud instance)", CP.MANDATORY),
		new CP(CPType.DECIMAL, PARAM_JIVE_PORT, "API Port", "Default to 443"),
		new CP(CPType.STRING, PARAM_SVC_ACCOUNT, "Service Account", "Username of a \"Full Access\" service user", CP.MANDATORY),
		new CP(CPType.STRING, PARAM_SVC_ACCOUNT_PWD, "Service Account Password", "Password for above user", CP.MANDATORY_ENCRYPTED),
		new CP(CPType.BOOLEAN, PARAM_IS_GUEST_ALLOWED, "Is guest allowed", "Defines whether or not guest access has been enabled in the Jive Guest Settings"),
		new CP(CPType.ENUM, PARAM_ACL_FORMAT, "ACL format", "Defines whether the username sent to the group retriever is the Jive username or Jive user's email address", new String[] {"username", "email"}),
		new CP(CPType.BOOLEAN, PARAM_DISCUSS_THREAD_AS_ONE_ITEM, "Discussion thread as 1 item", "Whether to index discussion threads as one item or index each dicussion message separately"),
		new CP(CPType.FILE, PARAM_SPECIAL_PERMISSIONS_FILE, "Special permissions file", "File that contains the permissions for spaces and/or system blogs that do not inherit permissions from the root space"),
		new CP(CPType.ENUM, PARAM_JIVE_VERSION, "Jive Version", "", new String[] {"6.x", "7 or higher"}),
		new CP(CPType.ENUM, PARAM_RUN_AS, "\"run-as\" strategy",
			"Run-As feature is a Jive API feature allowing \"Full Access\" admins to impersonate any Jive user. If this feature is disabled, only the documents that the service account as permissions to see will be indexed", new String[] {"none", "uri",
			"email", "userid", "username"}), new CP(CPType.STRING, PARAM_PLACE_FILTER, "Place filter", "List of place URIs that the connector will index", CP.MULTIVALUE)};
	

	private final boolean isGuestAllowed;
	private final boolean checkUsername;

	public Connector(final String uniqueId, final Map<String, String> configurationParameters, final String namespace, final String nameTransformer) {
		super(uniqueId, configurationParameters, namespace, nameTransformer);
		final String igaStr = cpMap.get(Connector.PARAM_IS_GUEST_ALLOWED);
		isGuestAllowed = StringUtils.isNullOrEmpty(igaStr) ? false : Boolean.parseBoolean(igaStr);
		final String afStr = cpMap.get(Connector.PARAM_ACL_FORMAT);
		checkUsername = StringUtils.isNullOrEmpty(afStr) ? false : "username".equals(afStr);
	}

	@Override
	public Collection<sword.gsa.xmlfeeds.builder.acl.Group> getGroups(final String username, final GroupCache groupCache) {

		final JiveGroupCache jgc = (JiveGroupCache) groupCache;

		User u = null;
		if (username != null) for (final User _u : jgc.jiveUsers)
			if (checkUsername) {
				if (_u.name != null && _u.name.equalsIgnoreCase(username)) {
					u = _u;
					break;
				}
			} else if (_u.email != null && _u.email.equalsIgnoreCase(username)) {
				u = _u;
				break;
			}

		final Collection<sword.gsa.xmlfeeds.builder.acl.Group> groups = new HashSet<>();
		if (u == null) {
			if (isGuestAllowed) groups.add(new sword.gsa.xmlfeeds.builder.acl.Group(Connector.JIVE_COMMUNITY_NAME, namespace));
		} else {
			groups.add(new sword.gsa.xmlfeeds.builder.acl.Group(u.email, namespace));
			groups.add(new sword.gsa.xmlfeeds.builder.acl.Group(u.name, namespace));
			groups.add(new sword.gsa.xmlfeeds.builder.acl.Group(Connector.JIVE_COMMUNITY_NAME, namespace));
			for (final Group g : jgc.jiveGroups)
				for (final User _u : g.members)
					if (_u.apiSelf.equals(u.apiSelf)) {
						groups.add(new sword.gsa.xmlfeeds.builder.acl.Group(g.name, namespace));
						break;
					}
		}

		return groups;
	}

	@Override
	public GroupCache getNewCache() throws Exception {

		final HttpHost host = new HttpHost(cpMap.get(Connector.PARAM_JIVE_HOST), 443, "https");
		final String encAuth = Base64.encodeBase64String(new StringBuilder(cpMap.get(Connector.PARAM_SVC_ACCOUNT)).append(":").append(CPUtils.decrypt(cpMap.get(Connector.PARAM_SVC_ACCOUNT_PWD))).toString().getBytes(StandardCharsets.UTF_8));
		final Header authzHeader = new BasicHeader("Authorization", "Basic " + encAuth);
		try (final CloseableHttpClient client = HttpClientHelper.getHttpClient(HttpClientHelper.getMultithreadedConnMgr(80, 100), HttpClientHelper.createRequestConfigFromSysProps(true), HttpClientHelper.createHttpRoutePlannerFromSysProps(), null,
			"sword-jive-connector")) {
			final List<User> users = ApiHelper.loadUsers(client, host, authzHeader);
			final List<Group> groups = ApiHelper.loadSecurityGroups(client, host, authzHeader, users);
			return new JiveGroupCache(users, groups);
		}
	}

	@Override
	public boolean supportsEarlyBinding() {
		return true;
	}

	@Override
	public boolean canDetectAclModification() {
		return false;
	}

	@Override
	public Class<? extends AExplorer> getExplorerClass() {
		return Explorer.class;
	}

	@Override
	public Class<? extends ADocLoader> getDocLoaderClass() {
		return DocLoader.class;
	}

	static String getImpersonationHeaderValue(final User u, final RunAsStrategy ras) {
		if (ras == RunAsStrategy.EMAIL) return String.format("email %s", u.email);
		else if (ras == RunAsStrategy.URI) return String.format("uri %s", u.apiSelf.substring(u.apiSelf.indexOf("/people/")));
		else if (ras == RunAsStrategy.USERID) return String.format("userid %s", u.apiSelf.substring("/people/".length() + u.apiSelf.indexOf("/people/")));
		else if (ras == RunAsStrategy.USERNAME) return String.format("username %s", u.name);
		else throw new IllegalStateException("Cannot construct a run-as strategy HTTP header when strategy is set to \"none\"");
	}

}
