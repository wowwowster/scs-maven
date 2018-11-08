package com.sword.gsa.connectors.jive.apiwrap;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;

import sword.common.http.client.HttpClientHelper;
import sword.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.sword.gsa.connectors.jive.apiwrap.places.Blog;
import com.sword.gsa.connectors.jive.apiwrap.places.Group;
import com.sword.gsa.connectors.jive.apiwrap.places.Place;
import com.sword.gsa.connectors.jive.apiwrap.places.Project;
import com.sword.gsa.connectors.jive.apiwrap.places.Space;
import com.sword.gsa.connectors.jive.apiwrap.security.User;

public class ApiHelper {

	public static final int BATCH_SIZE = 100;

	private static final Logger LOG = Logger.getLogger(ApiHelper.class);

	public static List<User> loadUsers(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader) throws ClientProtocolException, IOException, HttpException, InterruptedException {

		LOG.info("Loading Jive users");

		final List<User> users = new ArrayList<>();

		final String svcPath = String.format("/api/core/v3/people?sort=firstNameAsc&fields=%%40summary&count=%d", BATCH_SIZE);

		HttpGet getPeopleReq = new HttpGet(svcPath);
		while (getPeopleReq != null) {
			if (authzHeader != null) getPeopleReq.addHeader(authzHeader);
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getPeopleReq)) {
				final HttpEntity entity = resp.getEntity();
				final ContentType ct = ContentType.getOrDefault(entity);
				Charset cs = ct.getCharset();
				if (cs == null) cs = Charset.defaultCharset();

				final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
				try (JsonParser jp = jtr.getParser()) {
					final ObjectMapper om = new ObjectMapper();
					final TreeNode tn = om.readTree(jp);
					final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
					final TreeNode people = tn.path("list");
					final int peopleCount = people.size();
					if (peopleCount > 0) {
						for (int i = 0; i < peopleCount; i++) {
							final TreeNode person = people.get(i);
							String id = getValueAsString(person.path("id"));
							if (StringUtils.isNullOrEmpty(id)) id = null;
							String apiSelf = getValueAsString(person.path("resources").path("self").path("ref"));
							if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;

							String name = getValueAsString(person.path("jive").path("username"));
							if (StringUtils.isNullOrEmpty(name)) {
								name = getValueAsString(person.path("displayName"));
								if (StringUtils.isNullOrEmpty(name)) name = null;
							}
							final TreeNode emails = person.path("emails");
							final int emailsCount = emails.size();
							String email = null;
							for (int j = 0; j < emailsCount; j++)
								if (ApiHelper.getBoolValue(emails.get(j).path("primary"), false)) email = getValueAsString(emails.get(j).path("value"));
							if (StringUtils.isNullOrEmpty(email)) email = apiSelf;
							final User u = new User(id, name, email, apiSelf);
							if (!users.contains(u)) users.add(u);
						}
						getPeopleReq = new HttpGet(svcPath + "&startIndex=" + (curStartIndex + peopleCount));
					} else getPeopleReq = null;
				}
			}
		}

		LOG.info("Found " + users.size() + " Jive users");

		return users;
	}

	public static List<com.sword.gsa.connectors.jive.apiwrap.security.Group> loadSecurityGroups(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader, final List<User> users) throws Exception {

		LOG.info("Loading Jive security groups");

		final List<com.sword.gsa.connectors.jive.apiwrap.security.Group> groups = new ArrayList<>();

		{
			final String svcPath = String.format("/api/core/v3/securityGroups?count=%d&fields=%s", BATCH_SIZE, URLEncoder.encode("id,name", "UTF-8"));

			HttpGet getSecGrReq = new HttpGet(svcPath);
			while (getSecGrReq != null) {
				if (authzHeader != null) getSecGrReq.addHeader(authzHeader);
				try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getSecGrReq)) {
					final int sc = resp.getStatusLine().getStatusCode();
					if (sc != 200) throw new Exception("Security Groups listing request obtained status code: " + sc);
					else {
						final HttpEntity entity = resp.getEntity();
						final ContentType ct = ContentType.getOrDefault(entity);
						Charset cs = ct.getCharset();
						if (cs == null) cs = Charset.defaultCharset();

						final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
						try (JsonParser jp = jtr.getParser()) {

							final ObjectMapper om = new ObjectMapper();
							final TreeNode tn = om.readTree(jp);
							final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
							final TreeNode securityGroups = tn.path("list");
							final int securityGroupsCount = securityGroups.size();
							if (securityGroupsCount > 0) {
								for (int i = 0; i < securityGroupsCount; i++) {
									final TreeNode securityGroup = securityGroups.get(i);
									final String apiSelf = getValueAsString(securityGroup.path("resources").path("self").path("ref"));
									final String apiMembers = getValueAsString(securityGroup.path("resources").path("members").path("ref"));
									final String name = getValueAsString(securityGroup.path("name"));
									final String id = getValueAsString(securityGroup.path("id"));
									groups.add(new com.sword.gsa.connectors.jive.apiwrap.security.Group(id, name, apiSelf, apiMembers));
								}
								getSecGrReq = new HttpGet(svcPath + "&startIndex=" + (curStartIndex + securityGroupsCount));
							} else getSecGrReq = null;
						}
					}
				}
			}
		}

		LOG.info("Found " + groups.size() + " Jive security groups ; loading members");

		for (final com.sword.gsa.connectors.jive.apiwrap.security.Group gr : groups) {

			LOG.info("Loading members of security group " + gr.name);

			final int BATCH_SIZE = 100;

			final String svcPath = String.format("%s?count=%d&fields=%s", gr.apiMembers, BATCH_SIZE, URLEncoder.encode("id", "UTF-8"));

			HttpGet getGrMembersReq = new HttpGet(svcPath);
			while (getGrMembersReq != null) {
				if (authzHeader != null) getGrMembersReq.addHeader(authzHeader);
				try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getGrMembersReq)) {
					final int sc = resp.getStatusLine().getStatusCode();
					if (sc != 200) throw new Exception("Security Groups listing request obtained status code: " + sc);
					else {
						final HttpEntity entity = resp.getEntity();
						final ContentType ct = ContentType.getOrDefault(entity);
						Charset cs = ct.getCharset();
						if (cs == null) cs = Charset.defaultCharset();

						final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
						try (JsonParser jp = jtr.getParser()) {

							final ObjectMapper om = new ObjectMapper();
							final TreeNode tn = om.readTree(jp);
							final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
							final TreeNode groupMembers = tn.path("list");
							final int groupMembersCount = groupMembers.size();
							if (groupMembersCount > 0) {
								for (int i = 0; i < groupMembersCount; i++) {
									final TreeNode groupMember = groupMembers.get(i);
									String apiSelf = getValueAsString(groupMember.path("resources").path("self").path("ref"));
									if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;
									User member = null;
									for (final User u : users)
										if (u.apiSelf.equals(apiSelf)) {
											member = u;
											break;
										}
									if (member == null) System.out.println("Could not find member with id #" + apiSelf);
									else gr.members.add(member);
								}
								getGrMembersReq = new HttpGet(svcPath + "&startIndex=" + (curStartIndex + groupMembersCount));
							} else getGrMembersReq = null;
						}
					}
				}
			}

			LOG.info("Found " + gr.members.size() + " members for security group " + gr.name);

		}

		LOG.info("Security groups members loading complete");

		return groups;
	}

	public static Space getRootSpace(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader) throws ClientProtocolException, IOException, HttpException, InterruptedException {
		LOG.info("Loading root space");
		final String svcPath = "/api/core/v3/places/root";
		final HttpGet getRootSpaceReq = new HttpGet(svcPath);
		if (authzHeader != null) getRootSpaceReq.addHeader(authzHeader);
		Space rootSpace = null;
		try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getRootSpaceReq)) {
			final HttpEntity entity = resp.getEntity();
			final ContentType ct = ContentType.getOrDefault(entity);
			Charset cs = ct.getCharset();
			if (cs == null) cs = Charset.defaultCharset();

			final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
			try (JsonParser jp = jtr.getParser()) {
				final ObjectMapper om = new ObjectMapper();
				final TreeNode tn = om.readTree(jp);
				String apiSelf = getValueAsString(tn.path("resources").path("self").path("ref"));
				if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;
				String apiPlaces = getValueAsString(tn.path("resources").path("places").path("ref"));
				if (StringUtils.isNullOrEmpty(apiPlaces)) apiPlaces = apiSelf + "/places";
				String placeId = getValueAsString(tn.path("placeID"));
				if (StringUtils.isNullOrEmpty(placeId)) {// Try to extract place ID from the places URL
					final Matcher m = Pattern.compile("/v3/places/([0-9]+)/places").matcher(apiPlaces);
					if (m.find()) placeId = m.group(1);
					else placeId = null;
				}
				String name = getValueAsString(tn.path("name"));
				if (StringUtils.isNullOrEmpty(name)) {
					name = getValueAsString(tn.path("displayName"));
					if (StringUtils.isNullOrEmpty(name)) name = null;
				}
				if (StringUtils.isNullOrEmpty(name)) name = null;
				rootSpace = new Space(placeId, name, apiSelf, apiPlaces, null);
			}
		}
		LOG.info("Root space loading complete");
		return rootSpace;
	}

	public static void recursivePlaceExplore(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader, final Place rootPlace) throws ClientProtocolException, IOException, HttpException, InterruptedException {
		final List<Place> placesToExplore = new ArrayList<>();
		placesToExplore.add(rootPlace);
		Place parentPlace = null;
		while (!placesToExplore.isEmpty()) {
			parentPlace = placesToExplore.remove(0);
			LOG.info("Loading sub-places of place " + parentPlace.name);
			if (parentPlace.apiPlaces != null) {
				final String svcPath = parentPlace.apiPlaces;
				HttpGet getSubPlacesReq = new HttpGet(svcPath);
				while (getSubPlacesReq != null) {
					if (authzHeader != null) getSubPlacesReq.addHeader(authzHeader);
					try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getSubPlacesReq)) {
						final HttpEntity entity = resp.getEntity();
						final ContentType ct = ContentType.getOrDefault(entity);
						Charset cs = ct.getCharset();
						if (cs == null) cs = Charset.defaultCharset();

						final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
						try (JsonParser jp = jtr.getParser()) {
							final ObjectMapper om = new ObjectMapper();
							final TreeNode tn = om.readTree(jp);
							final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
							final TreeNode subPlaces = tn.path("list");
							final int subPlacesCount = subPlaces.size();
							if (subPlacesCount > 0) {
								for (int i = 0; i < subPlacesCount; i++) {
									final TreeNode subPlaceNode = subPlaces.get(i);
									String apiSelf = getValueAsString(subPlaceNode.path("resources").path("self").path("ref"));
									if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;
									String apiPlaces = getValueAsString(subPlaceNode.path("resources").path("places").path("ref"));
									if (StringUtils.isNullOrEmpty(apiPlaces)) apiPlaces = apiSelf + "/places";
									String placeId = getValueAsString(subPlaceNode.path("placeID"));
									if (StringUtils.isNullOrEmpty(placeId)) {// Try to extract place ID from the places URL
										final Matcher m = Pattern.compile("/v3/places/([0-9]+)/places").matcher(apiPlaces);
										if (m.find()) placeId = m.group(1);
										else placeId = null;
									}
									String name = getValueAsString(subPlaceNode.path("name"));
									if (StringUtils.isNullOrEmpty(name)) {
										name = getValueAsString(subPlaceNode.path("displayName"));
										if (StringUtils.isNullOrEmpty(name)) name = null;
									}
									String type = getValueAsString(subPlaceNode.path("type"));
									if (StringUtils.isNullOrEmpty(type)) type = null;

									Place subPlace = null;
									if ("space".equals(type)) subPlace = new Space(placeId, name, apiSelf, apiPlaces, parentPlace);
									else if ("blog".equals(type)) subPlace = new Blog(placeId, name, apiSelf, apiPlaces, parentPlace);
									else if ("group".equals(type)) {
										String grType = getValueAsString(subPlaceNode.path("groupType"));
										if (StringUtils.isNullOrEmpty(grType)) grType = null;
										subPlace = new Group(placeId, name, apiSelf, apiPlaces, parentPlace, grType);
									} else if ("project".equals(type)) subPlace = new Project(placeId, name, apiSelf, apiPlaces, parentPlace);
									else LOG.error("Unknown place type: " + type);
									if (subPlace != null) {
										parentPlace.children.add(subPlace);
										placesToExplore.add(subPlace);
									}
								}
								final char pc = svcPath.contains("?") ? '&' : '?';
								getSubPlacesReq = new HttpGet(svcPath + pc + "startIndex=" + (curStartIndex + subPlacesCount));
							} else getSubPlacesReq = null;
						}
					}
				}
				LOG.info("Finished loading sub-places of place " + parentPlace.name);
			}
		}
	}

	public static List<Group> getGroups(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader, final Space rootSpace) throws ClientProtocolException, IOException, HttpException, InterruptedException {
		LOG.info("Loading groups (places)");
		final List<Group> groups = new ArrayList<>();

		String svcPath = String.format("/api/core/v3/places?filter=%s&count=%d", URLEncoder.encode("type(group)", "UTF-8"), BATCH_SIZE);

		HttpGet getGroupsReq = new HttpGet(svcPath);
		while (getGroupsReq != null) {
			if (authzHeader != null) getGroupsReq.addHeader(authzHeader);
			int curStartIndex = 0;
			Boolean isFirstError = true;
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getGroupsReq)) {
				
				final HttpEntity entity = resp.getEntity();
				final ContentType ct = ContentType.getOrDefault(entity);
				Charset cs = ct.getCharset();
				if (cs == null) cs = Charset.defaultCharset();
				isFirstError = true;
				final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
				try (JsonParser jp = jtr.getParser()) {
					final ObjectMapper om = new ObjectMapper();
					final TreeNode tn = om.readTree(jp);
					curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
					final TreeNode groupNodes = tn.path("list");
					final int groupNodesCount = groupNodes.size();
					if (groupNodesCount > 0) {
						for (int i = 0; i < groupNodesCount; i++) {
							final TreeNode groupNode = groupNodes.get(i);
							String apiSelf = getValueAsString(groupNode.path("resources").path("self").path("ref"));
							if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;
							String apiPlaces = getValueAsString(groupNode.path("resources").path("places").path("ref"));
							if (StringUtils.isNullOrEmpty(apiPlaces)) apiPlaces = apiSelf + "/places";
							String placeId = getValueAsString(groupNode.path("placeID"));
							if (StringUtils.isNullOrEmpty(placeId)) {// Try to extract place ID from the places URL
								final Matcher m = Pattern.compile("/v3/places/([0-9]+)/places").matcher(apiPlaces);
								if (m.find()) placeId = m.group(1);
								else placeId = null;
							}
							String name = getValueAsString(groupNode.path("name"));
							if (StringUtils.isNullOrEmpty(name)) {
								name = getValueAsString(groupNode.path("displayName"));
								if (StringUtils.isNullOrEmpty(name)) name = null;
							}
							String type = getValueAsString(groupNode.path("type"));
							if (StringUtils.isNullOrEmpty(type)) type = null;

							if ("group".equals(type)) {
								String grType = getValueAsString(groupNode.path("groupType"));
								if (StringUtils.isNullOrEmpty(grType)) grType = null;
								groups.add(new Group(placeId, name, apiSelf, apiPlaces, rootSpace, grType));
							} else LOG.error("Unknown group type: " + type);
						}
						final char pc = svcPath.contains("?") ? '&' : '?';
						curStartIndex +=groupNodesCount;
						getGroupsReq = new HttpGet(svcPath + pc + "startIndex=" + curStartIndex);
					} else getGroupsReq = null;
				}
			}catch(HttpException e){
				if(isFirstError){
					isFirstError = false;
				LOG.error("Got http Exception, retrying with different parameters."+e);
				svcPath = String.format("/api/core/v3/places?filter=%s", URLEncoder.encode("type(group)", "UTF-8"));
				final char pc = svcPath.contains("?") ? '&' : '?';
				if(curStartIndex == 0) getGroupsReq = new HttpGet(svcPath);
				else getGroupsReq = new HttpGet(svcPath + pc + "startIndex=" + curStartIndex);
				}else getGroupsReq = null;
			}
		}
		rootSpace.children.addAll(groups);
		LOG.info("Found " + groups.size() + " groups (places)");
		return groups;
	}

	public static List<User> getGroupMembers(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader, final Group group, final List<User> allUsers) throws ClientProtocolException, IOException, HttpException, InterruptedException {
		final List<User> groupMembers = new ArrayList<>();

		LOG.info("Loading members of group (place) " + group.name);

		final String svcPath = String.format("/api/core/v3/members/places/%s?count=%d", group.id, BATCH_SIZE);

		HttpGet getGroupMembersReq = new HttpGet(svcPath);
		while (getGroupMembersReq != null) {
			if (authzHeader != null) getGroupMembersReq.addHeader(authzHeader);
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getGroupMembersReq)) {
				final HttpEntity entity = resp.getEntity();
				final ContentType ct = ContentType.getOrDefault(entity);
				Charset cs = ct.getCharset();
				if (cs == null) cs = Charset.defaultCharset();

				final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
				try (JsonParser jp = jtr.getParser()) {
					final ObjectMapper om = new ObjectMapper();
					final TreeNode tn = om.readTree(jp);
					final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));
					final TreeNode groupMemberNodes = tn.path("list");
					final int groupMemberNodesCount = groupMemberNodes.size();
					if (groupMemberNodesCount > 0) {
						for (int i = 0; i < groupMemberNodesCount; i++) {
							final TreeNode groupMemberNode = groupMemberNodes.get(i);
							final TreeNode groupMemberEmails = groupMemberNode.path("person").path("emails");
							final int groupMemberEmailsCount = groupMemberEmails.size();
							String userPrimaryEmail = null;
							for (int j = 0; j < groupMemberEmailsCount; j++)
								if (ApiHelper.getBoolValue(groupMemberEmails.get(j).path("primary"), false)) userPrimaryEmail = getValueAsString(groupMemberEmails.get(j).path("value"));
							if (StringUtils.isNullOrEmpty(userPrimaryEmail)) userPrimaryEmail = null;
							for (final User u : allUsers)
								if (u.email.equals(userPrimaryEmail)) {
									groupMembers.add(u);
									break;
								}
						}
						final char pc = svcPath.contains("?") ? '&' : '?';
						getGroupMembersReq = new HttpGet(svcPath + pc + "startIndex=" + (curStartIndex + groupMemberNodesCount));
					} else getGroupMembersReq = null;
				}
			}
		}

		LOG.info("Found " + groupMembers.size() + " members of group (place) " + group.name);

		return groupMembers;
	}

	public static List<Blog> getUserAndSystemBlogs(final CloseableHttpClient cl, final HttpHost host, final Header authzHeader, final Space rootSpace) throws ClientProtocolException, IOException, HttpException, InterruptedException {

		LOG.info("Loading user/system blogs");

		final List<Blog> blogs = new ArrayList<>();

		final String svcPath = String.format("/api/core/v3/places?filter=%s&count=%d", URLEncoder.encode("type(blog)", "UTF-8"), BATCH_SIZE);

		HttpGet getBlogsReq = new HttpGet(svcPath);
		while (getBlogsReq != null) {
			if (authzHeader != null) getBlogsReq.addHeader(authzHeader);
			try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(cl, host, getBlogsReq)) {
				final HttpEntity entity = resp.getEntity();
				final ContentType ct = ContentType.getOrDefault(entity);
				Charset cs = ct.getCharset();
				if (cs == null) cs = Charset.defaultCharset();

				final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
				try (JsonParser jp = jtr.getParser()) {
					final ObjectMapper om = new ObjectMapper();
					final TreeNode tn = om.readTree(jp);
					final int curStartIndex = ApiHelper.getIntValue(tn.path("startIndex"));

					final TreeNode blogNodes = tn.path("list");
					final int blogNodesCount = blogNodes.size();
					if (blogNodesCount > 0) {
						for (int i = 0; i < blogNodesCount; i++) {
							final TreeNode blogNode = blogNodes.get(i);
							String apiSelf = getValueAsString(blogNode.path("resources").path("self").path("ref"));
							if (StringUtils.isNullOrEmpty(apiSelf)) apiSelf = null;
							String apiPlaces = getValueAsString(blogNode.path("resources").path("places").path("ref"));
							if (StringUtils.isNullOrEmpty(apiPlaces)) apiPlaces = apiSelf + "/places";
							String placeId = getValueAsString(blogNode.path("placeID"));
							if (StringUtils.isNullOrEmpty(placeId)) {// Try to extract place ID from the places URL
								final Matcher m = Pattern.compile("/v3/places/([0-9]+)/places").matcher(apiPlaces);
								if (m.find()) placeId = m.group(1);
								else placeId = null;
							}
							String name = getValueAsString(blogNode.path("name"));
							if (StringUtils.isNullOrEmpty(name)) {
								name = getValueAsString(blogNode.path("displayName"));
								if (StringUtils.isNullOrEmpty(name)) name = null;
							}
							String parent = getValueAsString(blogNode.path("parent"));
							if (StringUtils.isNullOrEmpty(parent)) parent = null;
							String type = getValueAsString(blogNode.path("type"));
							if (StringUtils.isNullOrEmpty(type)) type = null;

							if ("blog".equals(type) && (parent == null || parent.contains("/api/core/v3/people/"))) blogs.add(new Blog(placeId, name, apiSelf, apiPlaces, rootSpace));
							else if ("blog".equals(type) && parent != null && parent.contains("/api/core/v3/places/")) {// Place blog
								// Place blog - ignored here - can be found through recursive places exploration
							} else LOG.error("Unknown blog type: " + type + " from " + parent);
						}
						final char pc = svcPath.contains("?") ? '&' : '?';
						getBlogsReq = new HttpGet(svcPath + pc + "startIndex=" + (curStartIndex + blogNodesCount));
					} else getBlogsReq = null;
				}
			}
		}
		rootSpace.children.addAll(blogs);

		LOG.info("Found " + blogs.size() + " user/system blogs");

		return blogs;
	}

	public static String getValueAsString(final TreeNode tn) {
		if (tn.isMissingNode()) return null;
		else if (tn instanceof JsonNode) return ((JsonNode) tn).asText();
		else return null;
	}

	public static boolean getBoolValue(final TreeNode tn, final boolean _default) {
		if (tn.isMissingNode()) return _default;
		else return ((BooleanNode) tn).booleanValue();
	}

	public static int getIntValue(final TreeNode tn) {
		if (tn.isMissingNode()) return -1;
		else return ((IntNode) tn).intValue();
	}

}
