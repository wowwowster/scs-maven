package com.sword.gsa.connectors.jive;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import sword.common.http.client.HttpClientHelper;
import sword.common.utils.StringUtils;
import sword.connectors.commons.config.CPUtils;
import sword.gsa.xmlfeeds.builder.acl.ACL;
import sword.gsa.xmlfeeds.builder.acl.AndACL;
import sword.gsa.xmlfeeds.builder.acl.InheritanceType;
import sword.gsa.xmlfeeds.builder.acl.Permission;
import sword.gsa.xmlfeeds.builder.acl.Principal;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sword.gsa.connectors.jive.apiwrap.ApiHelper;
import com.sword.gsa.connectors.jive.apiwrap.JsonThrowRemover;
import com.sword.gsa.connectors.jive.apiwrap.RunAsStrategy;
import com.sword.gsa.connectors.jive.apiwrap.places.Blog;
import com.sword.gsa.connectors.jive.apiwrap.places.Group;
import com.sword.gsa.connectors.jive.apiwrap.places.Place;
import com.sword.gsa.connectors.jive.apiwrap.places.Space;
import com.sword.gsa.connectors.jive.apiwrap.security.User;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.connector.URLManager;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class Explorer extends AExplorer {

	static final List<User> USERS = new ArrayList<User>();

	final HttpHost host;
	final Header authzHeader;
	final CloseableHttpClient httpClient;

	final boolean discussThreadsAsOneItem;
	final boolean isJive7orHigher;
	final RunAsStrategy ras;

	private final List<String> places;

	private final XPath xpath;
	private final Element specialPermissions;

	private final List<Principal> jiveCommunity;
	private final List<Principal> defaultSocialGroupPermissions;
	private final List<Principal> defaultBlogPermissions;
	private final List<Principal> defaultSpacePermissions;

	private final Space rootSpace;

	public Explorer(final PushProcessSharedObjectsStore sharedObjects) throws Exception {
		super(sharedObjects);
		int port = 443;
		if(configurationParams.get(Connector.PARAM_JIVE_PORT) != null)
		{
			port = Integer.parseInt(configurationParams.get(Connector.PARAM_JIVE_PORT));
			}
		host = new HttpHost(configurationParams.get(Connector.PARAM_JIVE_HOST), port, "https");

		final String user = configurationParams.get(Connector.PARAM_SVC_ACCOUNT);
		if ("anonymous".equals(user)) authzHeader = null;
		else {
			final String encAuth = Base64.encodeBase64String(new StringBuilder(user).append(":").append(CPUtils.decrypt(configurationParams.get(Connector.PARAM_SVC_ACCOUNT_PWD))).toString().getBytes(StandardCharsets.UTF_8));
			authzHeader = new BasicHeader("Authorization", "Basic " + encAuth);
		}

		httpClient = HttpClientHelper.getHttpClient(HttpClientHelper.getMultithreadedConnMgr(80, 100), HttpClientHelper.createRequestConfig(true, false, 3, 500_000, 500_000, (int) this.sharedObjects.pushConf.httpClientTimeout),
			HttpClientHelper.createHttpRoutePlannerFromSysProps(), null, "sword-jive-connector");

		final String discussThreadsAsOneItemStr = configurationParams.get(Connector.PARAM_DISCUSS_THREAD_AS_ONE_ITEM);
		discussThreadsAsOneItem = StringUtils.isNullOrEmpty(discussThreadsAsOneItemStr) ? false : Boolean.parseBoolean(discussThreadsAsOneItemStr);

		final String jvStr = configurationParams.get(Connector.PARAM_JIVE_VERSION);
		isJive7orHigher = StringUtils.isNullOrEmpty(jvStr) ? false : "7 or higher".equalsIgnoreCase(jvStr);

		ras = isJive7orHigher ? RunAsStrategy.parse(configurationParams.get(Connector.PARAM_RUN_AS)) : RunAsStrategy.NONE;

		final String pfStr = configurationParams.get(Connector.PARAM_PLACE_FILTER);
		if (StringUtils.isNullOrEmpty(pfStr)) places = null;
		else places = CPUtils.stringToList(pfStr);

		rootSpace = ApiHelper.getRootSpace(httpClient, host, authzHeader);
		ApiHelper.recursivePlaceExplore(httpClient, host, authzHeader, rootSpace);
		ApiHelper.getGroups(httpClient, host, authzHeader, rootSpace);
		for (final Place g : rootSpace.children)
				if (g instanceof Group) {
					try{
						ApiHelper.recursivePlaceExplore(httpClient, host, authzHeader, g);
					}catch(HttpException e){
						LOG.warn(e);
					}
				}

		jiveCommunity = new ArrayList<Principal>();
		jiveCommunity.add(new sword.gsa.xmlfeeds.builder.acl.Group(Connector.JIVE_COMMUNITY_NAME, this.sharedObjects.pushConf.aclNamespace));

		final String specialPermissionsFileStr = configurationParams.get(Connector.PARAM_SPECIAL_PERMISSIONS_FILE);
		File specialPermissionsFile = null;
		if (!StringUtils.isNullOrEmpty(specialPermissionsFileStr)) {
			specialPermissionsFile = new File(specialPermissionsFileStr);
			if (!specialPermissionsFile.exists()) specialPermissionsFile = null;
		}

		xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		if (specialPermissionsFile == null) {
			specialPermissions = null;
			defaultSocialGroupPermissions = jiveCommunity;
			defaultBlogPermissions = jiveCommunity;
			defaultSpacePermissions = jiveCommunity;
		} else {
			specialPermissions = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(specialPermissionsFile).getDocumentElement();
			final double defaultSpacePermsCount = (double) xpath.evaluate("count(default)", specialPermissions, XPathConstants.NUMBER);
			if (defaultSpacePermsCount > 0) {
				defaultSpacePermissions = new ArrayList<Principal>();
				// Add groups
				NodeList nl = (NodeList) xpath.evaluate("default/group", specialPermissions, XPathConstants.NODESET);
				int l = nl.getLength();
				Node n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultSpacePermissions.add(new sword.gsa.xmlfeeds.builder.acl.Group(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

				// Add users
				nl = (NodeList) xpath.evaluate("default/user", specialPermissions, XPathConstants.NODESET);
				l = nl.getLength();
				n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultSpacePermissions.add(new sword.gsa.xmlfeeds.builder.acl.User(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

			} else defaultSpacePermissions = jiveCommunity;

			final double socialGroupPermsCount = (double) xpath.evaluate("count(socialgroups)", specialPermissions, XPathConstants.NUMBER);
			if (socialGroupPermsCount > 0) {
				defaultSocialGroupPermissions = new ArrayList<Principal>();
				// Add groups
				NodeList nl = (NodeList) xpath.evaluate("socialgroups/group", specialPermissions, XPathConstants.NODESET);
				int l = nl.getLength();
				Node n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultSocialGroupPermissions.add(new sword.gsa.xmlfeeds.builder.acl.Group(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

				// Add users
				nl = (NodeList) xpath.evaluate("socialgroups/user", specialPermissions, XPathConstants.NODESET);
				l = nl.getLength();
				n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultSocialGroupPermissions.add(new sword.gsa.xmlfeeds.builder.acl.User(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

			} else defaultSocialGroupPermissions = jiveCommunity;

			final double blogPermsCount = (double) xpath.evaluate("count(blogs)", specialPermissions, XPathConstants.NUMBER);
			if (blogPermsCount > 0) {
				defaultBlogPermissions = new ArrayList<Principal>();
				// Add groups
				NodeList nl = (NodeList) xpath.evaluate("blogs/group", specialPermissions, XPathConstants.NODESET);
				int l = nl.getLength();
				Node n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultBlogPermissions.add(new sword.gsa.xmlfeeds.builder.acl.Group(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

				// Add users
				nl = (NodeList) xpath.evaluate("blogs/user", specialPermissions, XPathConstants.NODESET);
				l = nl.getLength();
				n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) defaultBlogPermissions.add(new sword.gsa.xmlfeeds.builder.acl.User(n.getTextContent(), this.sharedObjects.pushConf.aclNamespace));

			} else defaultBlogPermissions = jiveCommunity;
		}

		synchronized (USERS) {
			if (USERS.isEmpty()) USERS.addAll(ApiHelper.loadUsers(httpClient, host, authzHeader));
		}

	}

	@Override
	public List<ContainerNode> getRootNodes() throws Exception {

		final List<ContainerNode> rootNodes = new ArrayList<>();

		// User nodes will be used to retrieve docs that the user owns
		for (final User u : USERS)
			rootNodes.add(new ContainerNode(u.apiSelf, null));

		// Places nodes will only be used for ACL inheritance
		addPlacesNodes(rootNodes, rootSpace);
		// User and system blogs are not found by the recursive places retrieval method - fetch them
		addUserAndSystemBlogNodes(rootNodes);

		return rootNodes;

	}

	private void addUserAndSystemBlogNodes(final List<ContainerNode> rootNodes) throws ClientProtocolException, IOException, HttpException, InterruptedException {
		final List<Blog> blogs = ApiHelper.getUserAndSystemBlogs(httpClient, host, authzHeader, rootSpace);
		for (final Blog b : blogs)
			rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + b.apiSelf, null, new ACL(InheritanceType.AND_BOTH_PERMIT, null, defaultBlogPermissions)));
	}

	/*
	 * Add a pseudo container node for each place. These nodes will only be used for ACL inheritance
	 */
	private void addPlacesNodes(final List<ContainerNode> rootNodes, final Place place) throws ClientProtocolException, IOException, HttpException, InterruptedException, XPathExpressionException {

		final String parentAclUrl = place.parent == null ? null : URLManager.getSystemURL(sharedObjects.pushConf.datasource, Connector.ID_PREFIX_PLACE + place.parent.apiSelf);

		if (place instanceof Space) {
			final double sc = specialPermissions == null ? -1 : (double) xpath.evaluate("count(space[@id='" + place.apiSelf + "'])", specialPermissions, XPathConstants.NUMBER);
			if (sc > 0) {
				// Add groups
				NodeList nl = (NodeList) xpath.evaluate("space[@id='" + place.apiSelf + "']/group", specialPermissions, XPathConstants.NODESET);
				int l = nl.getLength();
				final ACL a = new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, new ArrayList<Principal>());
				Node n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) a.principals.add(new sword.gsa.xmlfeeds.builder.acl.Group(n.getTextContent(), sharedObjects.pushConf.aclNamespace));

				// Add users
				nl = (NodeList) xpath.evaluate("space[@id='" + place.apiSelf + "']/user", specialPermissions, XPathConstants.NODESET);
				l = nl.getLength();
				n = null;
				for (int i = 0; i < l; i++)
					if ((n = nl.item(i)).getNodeType() == Node.ELEMENT_NODE) a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(n.getTextContent(), sharedObjects.pushConf.aclNamespace));

				rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, a));
			} else if (rootSpace.equals(place)) rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, jiveCommunity)));
			else rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, defaultSpacePermissions)));
		} else if (place instanceof Group) {
			final Group g = (Group) place;
			if (g.canBeViewedByEntireCommunity()) rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, defaultSocialGroupPermissions)));
			else {

				final List<List<Principal>> allGroupPrincipals = new ArrayList<>();
				allGroupPrincipals.add(defaultSocialGroupPermissions);

				final List<Principal> explicitGroupAclPrincipals = new ArrayList<>();
				final List<User> users = ApiHelper.getGroupMembers(httpClient, host, authzHeader, g, USERS);
				for (final User user : users)
					explicitGroupAclPrincipals.add(new sword.gsa.xmlfeeds.builder.acl.User(user.email, sharedObjects.pushConf.aclNamespace));
				allGroupPrincipals.add(explicitGroupAclPrincipals);

				final AndACL aa = new AndACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, allGroupPrincipals);

				rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, aa));
			}
		} else rootNodes.add(new ContainerNode(Connector.ID_PREFIX_PLACE + place.apiSelf, null, new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, jiveCommunity)));

		for (final Place cp : place.children)
			addPlacesNodes(rootNodes, cp);

	}

	@Override
	public void loadChildren(final ContainerNode node, final boolean isUpdateMode, final boolean isPublicMode) throws Exception {
		if (node.parent == null) getUserDocs(node, isUpdateMode, isPublicMode);
	}

	private void getUserDocs(final ContainerNode userNode, final boolean isUpdateMode, final boolean isPublicMode) throws Exception {
		if (userNode.id.startsWith(Connector.ID_PREFIX_PLACE)) return;
		final String svcPath = String.format("/api/core/v3/contents?filter=%s&fields=%s&sort=dateCreatedAsc&count=%d", URLEncoder.encode(String.format("author(%s)", userNode.id), "UTF-8"),
			URLEncoder.encode("parent,visibility,author,users,type,attachments,updated", "UTF-8"), ApiHelper.BATCH_SIZE);
		User user = null;
		for (final User u : USERS)
			if (u.apiSelf.equals(userNode.id)) {
				user = u;
				break;
			}
		if (user == null) throw new IllegalStateException("No user found with URI: " + userNode.id);
		else if (places == null) getUserDocs(user, userNode, svcPath);
		else if (isJive7orHigher) getUserDocs(user, userNode, svcPath + buildPlaceFilterClause(places));
		else for (final String pu : places)
			getUserDocs(user, userNode, svcPath + buildPlaceFilterClause(pu));
	}

	private static String buildPlaceFilterClause(final List<String> places) throws UnsupportedEncodingException {
		final StringBuilder sb = new StringBuilder("&filter=place%28");// place(
		final int len = places.size();
		for (int i = 0; i < len; i++) {
			if (i > 0) sb.append("%2C");// ,
			sb.append(URLEncoder.encode(places.get(i), "UTF-8"));
		}
		sb.append("%29");// )
		return sb.toString();
	}

	private static String buildPlaceFilterClause(final String placeUri) throws UnsupportedEncodingException {
		return new StringBuilder("&filter=place%28").append(URLEncoder.encode(placeUri, "UTF-8")).append("%29").toString();
	}

	private void getUserDocs(final User user, final ContainerNode userNode, final String svcPath) throws HttpException, IOException, UnsupportedEncodingException, ClientProtocolException, JsonParseException, InterruptedException {
		HttpGet getContentsReq = new HttpGet(svcPath);
		while (getContentsReq != null) {
			if (ras != RunAsStrategy.NONE) getContentsReq.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(user, ras));
			if (authzHeader != null) getContentsReq.addHeader(authzHeader);
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, host, getContentsReq, false)) {
				final int sc = resp.getStatusLine().getStatusCode();
				if (sc != 200) {
					if (sc == 403) {
						LOG.warn("Not authorized to retrieve documents for user: " + userNode.id + " ; request obtained status code 403: " + svcPath + " - skipping.");
						return;
					} else throw HttpClientHelper.getError(sc, getContentsReq, resp);
				} else {
					final HttpEntity entity = resp.getEntity();
					final ContentType ct = ContentType.getOrDefault(entity);
					Charset cs = ct.getCharset();
					if (cs == null) cs = Charset.defaultCharset();

					final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
					try (JsonParser jp = jtr.getParser()) {
						final ObjectMapper om = new ObjectMapper();
						final TreeNode tn = om.readTree(jp);
						final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
						final TreeNode userDocNodes = tn.path("list");
						final int userDocNodesCount = userDocNodes.size();
						if (userDocNodesCount > 0) {
							for (int i = 0; i < userDocNodesCount; i++) {

								final TreeNode userDocNode = userDocNodes.get(i);

								String type = ApiHelper.getValueAsString(userDocNode.path("type"));
								if (StringUtils.isNullOrEmpty(type)) type = null;

								final String selfUrl = ApiHelper.getValueAsString(userDocNode.path("resources").path("self").path("ref"));

								String visibility = ApiHelper.getValueAsString(userDocNode.path("visibility"));
								if (StringUtils.isNullOrEmpty(visibility)) visibility = null;

								String parent = ApiHelper.getValueAsString(userDocNode.path("parent"));
								if (StringUtils.isNullOrEmpty(parent)) parent = null;

								Permission p = null;
								if ("place".equals(visibility) || visibility == null) {

									if ("favorite".equals(type)) {// Favorites are either private or public
										final boolean priv = ApiHelper.getBoolValue(userDocNode.path("private"), false);
										if (priv) {
											final ACL a = new ACL(InheritanceType.LEAF_NODE, null, new ArrayList<Principal>());
											final String userApiSelf = userNode.id;
											for (final User u : USERS)
												if (u.apiSelf.equals(userApiSelf)) {
													a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(u.email, sharedObjects.pushConf.aclNamespace));
													break;
												}
											p = a;
										} else p = new ACL(InheritanceType.AND_BOTH_PERMIT, null, jiveCommunity);
									} else if (parent != null && parent.contains("/places/")) {
										final String parentAclUrl = URLManager.getSystemURL(sharedObjects.pushConf.datasource, Connector.ID_PREFIX_PLACE + parent);
										p = new ACL(InheritanceType.AND_BOTH_PERMIT, parentAclUrl, jiveCommunity);
									} else if (parent != null && parent.contains("/people/")) p = new ACL(InheritanceType.AND_BOTH_PERMIT, null, jiveCommunity);
									else LOG.warn("Unknown parent URL: " + parent + " for doc #" + selfUrl);
								} else if ("all".equals(visibility)) p = new ACL(InheritanceType.AND_BOTH_PERMIT, null, jiveCommunity);
								else if ("hidden".equals(visibility)) {
									final ACL a = new ACL(InheritanceType.AND_BOTH_PERMIT, null, new ArrayList<Principal>());
									final String authorApiSelf = ApiHelper.getValueAsString(userDocNode.path("author").path("resources").path("self").path("ref"));
									for (final User u : USERS)
										if (u.apiSelf.equals(authorApiSelf)) {
											a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(u.email, sharedObjects.pushConf.aclNamespace));
											break;
										}
									p = a;
								} else if ("people".equals(visibility)) {
									final ACL a = new ACL(InheritanceType.AND_BOTH_PERMIT, null, new ArrayList<Principal>());
									final TreeNode users = userDocNode.path("users");
									final int nUsers = users.size();
									for (int j = 0; j < nUsers; j++) {
										final String userApiSelf = ApiHelper.getValueAsString(users.get(j).path("resources").path("self").path("ref"));
										for (final User u : USERS)
											if (u.apiSelf.equals(userApiSelf)) {
												a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(u.email, sharedObjects.pushConf.aclNamespace));
												break;
											}
									}
									final String authorApiSelf = ApiHelper.getValueAsString(userDocNode.path("author").path("resources").path("self").path("ref"));
									for (final User u : USERS)
										if (u.apiSelf.equals(authorApiSelf)) {
											a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(u.email, sharedObjects.pushConf.aclNamespace));
											break;
										}
									p = a;
								} else LOG.warn("Unknown visibility property for " + type + " #" + selfUrl + ": " + visibility);

								Date lastModif = null;
								final String lmStr = ApiHelper.getValueAsString(userDocNode.path("updated"));
								try {
									lastModif = DocLoader.JIVE_DATES.parse(lmStr);
								} catch (final ParseException e) {
									lastModif = null;
								}

								if ("discussion".equals(type)) {
									final String discussId = Connector.ID_PREFIX_DISCUSSION + selfUrl;
									userNode.children.add(new DocumentNode(discussId, userNode, p, lastModif));
									if (!discussThreadsAsOneItem) {
										final String discussMessages = ApiHelper.getValueAsString(userDocNode.path("resources").path("messages").path("ref"));
										getDiscussionMessages(user, userNode, discussMessages, URLManager.getSystemURL(sharedObjects.pushConf.datasource, discussId));
									}
									getAttachments(userNode, userDocNode, discussId);
								} else if ("document".equals(type)) {
									final String docId = Connector.ID_PREFIX_DOCUMENT + selfUrl;
									userNode.children.add(new DocumentNode(docId, userNode, p, lastModif));
									getAttachments(userNode, userDocNode, docId);
								} else if ("file".equals(type)) {
									final String fileId = Connector.ID_PREFIX_FILE + selfUrl;
									userNode.children.add(new DocumentNode(fileId, userNode, p, lastModif));
								} else if ("poll".equals(type)) {
									final String pollId = Connector.ID_PREFIX_POLL + selfUrl;
									userNode.children.add(new DocumentNode(pollId, userNode, p, lastModif));
								} else if ("post".equals(type)) {
									final String postId = Connector.ID_PREFIX_POST + selfUrl;
									userNode.children.add(new DocumentNode(postId, userNode, p, lastModif));
									getAttachments(userNode, userDocNode, postId);
								} else if ("task".equals(type)) {
									final String taskId = Connector.ID_PREFIX_TASK + selfUrl;
									userNode.children.add(new DocumentNode(taskId, userNode, p, lastModif));
								} else if ("favorite".equals(type)) {
									final boolean _private = ApiHelper.getBoolValue(userDocNode.path("private"), false);
									if (_private) {
										final ACL a = new ACL(InheritanceType.LEAF_NODE, null, new ArrayList<Principal>());
										final String authorApiSelf = ApiHelper.getValueAsString(userDocNode.path("author").path("resources").path("self").path("ref"));
										for (final User u : USERS)
											if (u.apiSelf.equals(authorApiSelf)) {
												a.principals.add(new sword.gsa.xmlfeeds.builder.acl.User(u.email, sharedObjects.pushConf.aclNamespace));
												break;
											}
										p = a;
									} else p = new ACL(InheritanceType.LEAF_NODE, null, jiveCommunity);
									final String bookmarkId = Connector.ID_PREFIX_BOOKMARK + selfUrl;
									userNode.children.add(new DocumentNode(bookmarkId, userNode, p, lastModif));
								} else if ("update".equals(type)) {
									final String updateId = Connector.ID_PREFIX_UPDATE + selfUrl;
									userNode.children.add(new DocumentNode(updateId, userNode, p, lastModif));
								} else if ("video".equals(type)) {
									final String videoId = Connector.ID_PREFIX_VIDEO + selfUrl;
									userNode.children.add(new DocumentNode(videoId, userNode, p, lastModif));
									getAttachments(userNode, userDocNode, videoId);
								} else if ("event".equals(type)) {
									final String eventId = Connector.ID_PREFIX_EVENT + selfUrl;
									userNode.children.add(new DocumentNode(eventId, userNode, p, lastModif));
									getAttachments(userNode, userDocNode, eventId);
								} else if ("idea".equals(type)) {
									final String ideaId = Connector.ID_PREFIX_IDEA + selfUrl;
									userNode.children.add(new DocumentNode(ideaId, userNode, p, lastModif));
									getAttachments(userNode, userDocNode, ideaId);
								} else LOG.error("Unknown content type: " + type + " for #" + selfUrl);

							}
							getContentsReq = new HttpGet(svcPath + "&startIndex=" + (curStartIndex + userDocNodesCount));
						} else getContentsReq = null;
					}
				}
			}
		}
	}

	private void getDiscussionMessages(final User user, final ContainerNode node, final String messageRetrAvcPath, final String discussionAclUrl) throws HttpException, ClientProtocolException, IOException, NumberFormatException, InterruptedException {

		final String svcPath = String.format("%s?fields=%s&count=%d", messageRetrAvcPath, URLEncoder.encode("id", "UTF-8"), ApiHelper.BATCH_SIZE);

		HttpGet getMessagesReq = new HttpGet(svcPath);
		while (getMessagesReq != null) {
			getMessagesReq.addHeader(authzHeader);
			if (ras != RunAsStrategy.NONE) getMessagesReq.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(user, ras));
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, host, getMessagesReq)) {
				final HttpEntity entity = resp.getEntity();
				final ContentType ct = ContentType.getOrDefault(entity);
				Charset cs = ct.getCharset();
				if (cs == null) cs = Charset.defaultCharset();

				final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
				try (JsonParser jp = jtr.getParser()) {
					final ObjectMapper om = new ObjectMapper();
					final TreeNode tn = om.readTree(jp);

					final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
					final TreeNode discussMessages = tn.path("list");
					final int discussMessagesCount = discussMessages.size();
					if (discussMessagesCount > 0) {
						for (int i = 0; i < discussMessagesCount; i++) {
							final String selfUrl = ApiHelper.getValueAsString(discussMessages.get(i).path("resources").path("self").path("ref"));
							final String messageId = Connector.ID_PREFIX_MESSAGE + selfUrl;
							node.children.add(new DocumentNode(messageId, node, new ACL(InheritanceType.LEAF_NODE, discussionAclUrl, jiveCommunity)));
						}
						getMessagesReq = new HttpGet(svcPath + "&startIndex=" + (curStartIndex + discussMessagesCount));
					} else getMessagesReq = null;
				}
			}
		}
	}

	private void getAttachments(final ContainerNode node, final TreeNode userDocNode, final String parentId) throws ClientProtocolException, IOException, NumberFormatException {
		final TreeNode attachments = userDocNode.path("attachments");
		final int attachmentsCount = attachments.size();
		if (attachmentsCount > 0) {
			final String parentAclUrl = URLManager.getSystemURL(sharedObjects.pushConf.datasource, parentId);
			for (int i = 0; i < attachmentsCount; i++) {
				final String selfUrl = ApiHelper.getValueAsString(attachments.get(i).path("resources").path("self").path("ref"));
				final String attachId = Connector.ID_PREFIX_ATTACHMENT + parentId + "~@" + selfUrl;
				node.children.add(new DocumentNode(attachId, node, new ACL(InheritanceType.LEAF_NODE, parentAclUrl, jiveCommunity)));
			}
		}
	}

	@SuppressWarnings("unused")
	private void getAttachmentsByUrl(final User user, final ContainerNode node, final Node n, final String parentId, final String url) throws HttpException, ClientProtocolException, IOException, NumberFormatException, XPathExpressionException, InterruptedException {

		final String svcPath = String.format("%s?count=%d", url, ApiHelper.BATCH_SIZE);

		final HttpGet getAttachReq = new HttpGet(svcPath);
		getAttachReq.addHeader(authzHeader);
		if (ras != RunAsStrategy.NONE) getAttachReq.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(user, ras));
		try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, host, getAttachReq, false)) {
			final HttpEntity entity = resp.getEntity();
			final ContentType ct = ContentType.getOrDefault(entity);
			Charset cs = ct.getCharset();
			if (cs == null) cs = Charset.defaultCharset();

			final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
			try (JsonParser jp = jtr.getParser()) {
				final ObjectMapper om = new ObjectMapper();
				final TreeNode tn = om.readTree(jp);
				final TreeNode attachments = tn.path("list");
				final int attachmentsCount = attachments.size();
				if (attachmentsCount > 0) {
					final Node an = null;
					final String parentAclUrl = URLManager.getSystemURL(sharedObjects.pushConf.datasource, parentId);
					for (int i = 0; i < attachmentsCount; i++) {
						final String selfUrl = ApiHelper.getValueAsString(attachments.get(i).path("resources").path("self").path("ref"));
						final String attachId = Connector.ID_PREFIX_ATTACHMENT + parentId + "~@" + selfUrl;
						node.children.add(new DocumentNode(attachId, node, new ACL(InheritanceType.LEAF_NODE, parentAclUrl, jiveCommunity)));
					}
				}
			}
		}
	}

	@Override
	public void close() throws Exception {
		httpClient.close();
	}

}
