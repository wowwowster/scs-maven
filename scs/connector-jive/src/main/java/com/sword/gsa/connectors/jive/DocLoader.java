package com.sword.gsa.connectors.jive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;

import sword.common.http.client.FullyClosableInputStream;
import sword.common.http.client.HttpClientHelper;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;
import sword.common.utils.files.MimeType;
import sword.gsa.xmlfeeds.builder.Metadata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sword.gsa.connectors.jive.apiwrap.ApiHelper;
import com.sword.gsa.connectors.jive.apiwrap.JsonThrowRemover;
import com.sword.gsa.connectors.jive.apiwrap.RunAsStrategy;
import com.sword.gsa.connectors.jive.apiwrap.security.User;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class DocLoader extends ADocLoader {

	static final ThreadSafeDateFormat JIVE_DATES = new ThreadSafeDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
	private static final String TYPE_META_NAME = "ObjectType";

	private static final Map<String, String> PARENT_IIDS = new HashMap<>();
	private static final Map<String, String> PARENT_URLS = new HashMap<>();

	final Header authzHeader;
	private final CloseableHttpClient httpClient;

	private final boolean discussThreadsAsOneItem;
	private final RunAsStrategy ras;

	private User owner = null;
	private String type = null;
	private String apiSelf = null;
	private TreeNode docNode = null;
	private TreeNode commentsNode = null;
	private String parentId = null;
	private String parentIid = null;
	private TreeNode discussMessagesNode = null;

	public DocLoader(final AExplorer explorer, final PushProcessSharedObjectsStore sharedObjects, final ContainerNode parentNode) {
		super(explorer, sharedObjects, parentNode);

		final Explorer jExpl = (Explorer) explorer;

		authzHeader = jExpl.authzHeader;
		httpClient = jExpl.httpClient;
		discussThreadsAsOneItem = jExpl.discussThreadsAsOneItem;
		ras = jExpl.ras;

	}

	@Override
	public void loadObject(final DocumentNode docNode) throws Exception {

		type = null;
		apiSelf = null;
		this.docNode = null;
		commentsNode = null;
		parentId = null;
		parentIid = null;
		discussMessagesNode = null;

		final String ownerUri = docNode.parent.id;

		owner = null;
		for (final User u : Explorer.USERS)
			if (u.apiSelf.equals(ownerUri)) {
				owner = u;
				break;
			}
		if (owner == null) throw new IllegalStateException("No user found with URI: " + ownerUri);

		type = docNode.id.substring(0, 1);
		apiSelf = docNode.id.substring(1);
		if (Connector.ID_PREFIX_ATTACHMENT.equals(type)) {
			final int i = apiSelf.indexOf("~@");
			parentId = apiSelf.substring(0, i);
			apiSelf = apiSelf.substring(i + 2);
		}

		final String svcPath = String.format("%s?fields=%s", apiSelf, URLEncoder.encode("@all", "UTF-8"));

		final HttpGet getJson = new HttpGet(svcPath);
		if (authzHeader != null) getJson.addHeader(authzHeader);
		if (ras != RunAsStrategy.NONE) getJson.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
		try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getJson)) {
			final HttpEntity entity = resp.getEntity();
			final ContentType ct = ContentType.getOrDefault(entity);
			Charset cs = ct.getCharset();
			if (cs == null) cs = Charset.defaultCharset();
			final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
			try (JsonParser jp = jtr.getParser()) {
				final ObjectMapper om = new ObjectMapper();
				this.docNode = om.readTree(jp);
				String apiComments = ApiHelper.getValueAsString(this.docNode.path("resources").path("comments").path("ref"));// Standard comments location
				if (StringUtils.isNullOrEmpty(apiComments)) {
					apiComments = ApiHelper.getValueAsString(this.docNode.path("favoriteObject").path("resources").path("comments").path("ref"));// Check if comments are present in the favoriteObject
					// (for Bookmarks)
					if (StringUtils.isNullOrEmpty(apiComments)) commentsNode = null;
					else commentsNode = getComments(apiComments);
				} else commentsNode = getComments(apiComments);
				if (Connector.ID_PREFIX_DISCUSSION.equals(type) && discussThreadsAsOneItem) {
					final String apiMessages = ApiHelper.getValueAsString(this.docNode.path("resources").path("messages").path("ref"));
					if (StringUtils.isNullOrEmpty(apiMessages)) discussMessagesNode = null;
					else discussMessagesNode = getMessages(apiMessages);
				}
			}
		}
	}

	private TreeNode getComments(final String apiComments) throws ClientProtocolException, IOException, InterruptedException {
		final HttpGet getCommentsJson = new HttpGet(apiComments);
		if (authzHeader != null) getCommentsJson.addHeader(authzHeader);
		if (ras != RunAsStrategy.NONE) getCommentsJson.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
		try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getCommentsJson)) {
			final HttpEntity entity = resp.getEntity();
			final ContentType ct = ContentType.getOrDefault(entity);
			Charset cs = ct.getCharset();
			if (cs == null) cs = Charset.defaultCharset();
			final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
			try (JsonParser jp = jtr.getParser()) {
				final ObjectMapper om = new ObjectMapper();
				return om.readTree(jp);
			}
		} catch (final HttpException e) {
			LOG.error(e);
			return null;
		}
	}

	private TreeNode getMessages(final String apiMessages) throws ClientProtocolException, IOException, InterruptedException {
		final HttpGet getMessagesJson = new HttpGet(apiMessages);
		if (authzHeader != null) getMessagesJson.addHeader(authzHeader);
		if (ras != RunAsStrategy.NONE) getMessagesJson.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
		try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getMessagesJson)) {
			final HttpEntity entity = resp.getEntity();
			final ContentType ct = ContentType.getOrDefault(entity);
			Charset cs = ct.getCharset();
			if (cs == null) cs = Charset.defaultCharset();
			final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);
			try (JsonParser jp = jtr.getParser()) {
				final ObjectMapper om = new ObjectMapper();
				return om.readTree(jp);
			}
		} catch (final HttpException e) {
			LOG.error(e);
			return null;
		}
	}

	@Override
	public Date getModifyDate() throws Exception {
		final String stringDate = ApiHelper.getValueAsString(docNode.path("updated"));
		if (StringUtils.isNullOrEmpty(stringDate)) return null;
		else return JIVE_DATES.parse(stringDate);
	}

	@Override
	public void getMetadata(final List<Metadata> metadata) throws Exception {

		if (Connector.ID_PREFIX_ATTACHMENT.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Attachment"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("contentType"), false, "ContentType", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("doUpload"), false, "DoUpload", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("name"), false, "Name", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("size"), false, "Size", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("url"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			metaValues.add(parentId);
			addMetaTag("ParentID", metaValues, metadata);

			final String parentSelf = parentId.substring(1);
			final String parentUrl;
			synchronized (PARENT_URLS) {
				if (PARENT_URLS.containsKey(parentSelf)) {
					parentUrl = PARENT_URLS.get(parentSelf);
					parentIid = PARENT_IIDS.get(parentSelf);
				} else {
					final HttpGet getParentJson = new HttpGet(parentSelf);
					if (authzHeader != null) getParentJson.addHeader(authzHeader);
					if (ras != RunAsStrategy.NONE) getParentJson.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
					String pu = null;
					String piid = null;
					try (CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getParentJson)) {
						final HttpEntity entity = resp.getEntity();
						final ContentType ct = ContentType.getOrDefault(entity);
						Charset cs = ct.getCharset();
						if (cs == null) cs = Charset.defaultCharset();
						final JsonThrowRemover jtr = new JsonThrowRemover(entity.getContent(), cs);

						TreeNode parentNode;
						try (JsonParser jp = jtr.getParser()) {
							final ObjectMapper om = new ObjectMapper();
							parentNode = om.readTree(jp);
						}
						pu = ApiHelper.getValueAsString(parentNode.path("resources").path("html").path("ref"));
						piid = ApiHelper.getValueAsString(parentNode.path("id"));
					} catch (final HttpException e) {
						LOG.error(e);
						pu = null;
						piid = null;
					}
					parentUrl = pu;
					PARENT_URLS.put(parentSelf, parentUrl);
					parentIid = piid;
					PARENT_IIDS.put(parentSelf, parentIid);
				}
			}
			metaValues.clear();
			metaValues.add(parentUrl);
			addMetaTag("ParentHtml", metaValues, metadata);

		} else if (Connector.ID_PREFIX_BOOKMARK.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Bookmark"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("favoriteObject").path("published"), true, "FavoriteObjectPublished", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("favoriteObject").path("updated"), true, "FavoriteObjectUpdated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("favoriteObject").path("url"), false, "FavoriteObjectURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("private"), false, "Private", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_DISCUSSION.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Discussion"));
			final List<String> metaValues = new ArrayList<>();
			addSimpleMeta(docNode.path("answer"), false, "Answer", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("helpful"), metaValues);
			addMetaTag("Helpful", metaValues, metadata);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("onBehalfOf").path("email"), false, "OnBehalfOf", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("question"), false, "Question", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resolved"), false, "Resolved", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("restrictReplies"), false, "RestrictReplies", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("via").path("displayName"), false, "Via", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			if (discussThreadsAsOneItem) {
				final int mc = discussMessagesNode.path("list").size();
				metaValues.clear();
				metaValues.add(Integer.toString(mc));
				addMetaTag("MessagesCount", metaValues, metadata);
			}
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_DOCUMENT.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Document"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getPersonArray(docNode.path("approvers"), metaValues);
			addMetaTag("Approvers", metaValues, metadata);
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("authorship"), false, "Authorship", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("editingBy").path("displayName"), false, "EditingBy", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("fromQuest"), false, "FromQuest", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("restrictComments"), false, "RestrictComments", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updater").path("displayName"), false, "Updater", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_EVENT.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Event"));
			final List<String> metaValues = new ArrayList<>();
			// Start attendance property
			addSimpleMeta(docNode.path("attendance").path("anonymous"), false, "Anonymous", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("attendance").path("response"), false, "Responses", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("attendance").path("ended"), false, "Ended", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getAttendees(docNode, "yesAttendees", metaValues);
			addMetaTag("YesAttendees", metaValues, metadata);
			metaValues.clear();
			getAttendees(docNode, "noAttendees", metaValues);
			addMetaTag("NoAttendees", metaValues, metadata);
			metaValues.clear();
			getAttendees(docNode, "maybeAttendees", metaValues);
			addMetaTag("MaybeAttendees", metaValues, metadata);
			metaValues.clear();
			getAttendees(docNode, "unansweredInvitees", metaValues);
			addMetaTag("UnansweredInvitees", metaValues, metadata);
			// End attendance property
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("authorship"), false, "Authorship", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			addSimpleMeta(docNode.path("city"), false, "City", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("country"), false, "Country", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("email"), false, "Email", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("endDate"), true, "EndDate", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("eventAccess"), false, "EventAccess", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("eventType"), false, "EventType", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("language"), false, "Language", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("location"), false, "Location", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("maxAttendees"), false, "MaxAttendees", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("phone"), false, "Phone", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("startDate"), true, "StartDate", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("street"), false, "Street", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("url"), false, "Url", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_FILE.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "File"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("authorship"), false, "Authorship", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("binaryURL"), false, "BinaryURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("contentType"), false, "ContentType", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("name"), false, "Name", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("restrictComments"), false, "RestrictComments", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("size"), false, "Size", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_IDEA.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Idea"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("authorship"), false, "Authorship", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("authorshipPolicy"), false, "AuthorshipPolicy", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			metaValues.clear();
			getComments(commentsNode, metaValues);
			addMetaTag("Comments", metaValues, metadata);
			addSimpleMeta(docNode.path("commentCount"), false, "CommentCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("score"), false, "Score", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("stage"), false, "Stage", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("voteCount"), false, "VoteCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("voted"), false, "Voted", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_MESSAGE.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Message"));
			final List<String> metaValues = new ArrayList<>();
			addSimpleMeta(docNode.path("answer"), false, "Answer", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("discussion"), false, "Discussion", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("fromQuest"), false, "FromQuest", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("helpful"), false, "Helpful", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("onBehalfOf").path("email"), false, "OnBehalfOf", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("via").path("displayName"), false, "Via", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_POLL.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Poll"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("options"), metaValues);
			addMetaTag("Options", metaValues, metadata);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("voteCount"), false, "VoteCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("votes"), metaValues);
			addMetaTag("Votes", metaValues, metadata);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_POST.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Post"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("permalink"), false, "Permalink", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("publishDate"), true, "PublishDate", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("restrictComments"), false, "RestrictComments", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_TASK.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Task"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("completed"), false, "Completed", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("dueDate"), true, "DueDate", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("owner"), false, "Owner", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentTask"), false, "ParentTask", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("subTasks"), metaValues);
			addMetaTag("SubTasks", metaValues, metadata);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_UPDATE.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Update"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("latitude"), false, "Latitude", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("longitude"), false, "Longitude", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		} else if (Connector.ID_PREFIX_VIDEO.equals(type)) {
			metadata.add(new Metadata(TYPE_META_NAME, "Video"));
			final List<String> metaValues = new ArrayList<>();
			metaValues.clear();
			getAuthors(docNode, metaValues);
			addMetaTag("Author", metaValues, metadata);
			metaValues.clear();
			getStringArrayValues(docNode.path("categories"), metaValues);
			addMetaTag("Categories", metaValues, metadata);
			addSimpleMeta(docNode.path("content").path("text"), false, "Content", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("contentID"), false, "ContentID", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "contentType", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesType", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "name", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesName", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "ref", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "size", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesSize", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentImages", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentImagesHeight", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "stillImageURL", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosStillImageURL", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "width", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosWidth", metaValues, metadata);
			metaValues.clear();
			getArrayNamedProp(docNode, "contentVideos", "height", metaValues, false, sharedObjects.pushConf.feedDatesFormat);
			addMetaTag("ContentVideosHeight", metaValues, metadata);
			metaValues.clear();
			getPersonArray(docNode.path("extendedAuthors"), metaValues);
			addMetaTag("ExtendedAuthors", metaValues, metadata);
			addSimpleMeta(docNode.path("followerCount"), false, "FollowerCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightBody"), false, "HighlightBody", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightSubject"), false, "HighlightSubject", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("highlightTags"), false, "HighlightTags", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("iconCss"), false, "IconCss", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("id"), false, "ID", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("likeCount"), false, "LikeCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("name"), false, "ParentPlace", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("parentPlace").path("html"), false, "ParentPlaceURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("published"), true, "Published", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("replyCount"), false, "ReplyCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("status"), false, "Status", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("subject"), false, "Subject", metadata, sharedObjects.pushConf.feedDatesFormat);
			metaValues.clear();
			getStringArrayValues(docNode.path("tags"), metaValues);
			addMetaTag("Tags", metaValues, metadata);
			addSimpleMeta(docNode.path("type"), false, "Type", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("updated"), true, "Updated", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("viewCount"), false, "ViewCount", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibility"), false, "Visibility", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("visibleToExternalContributors"), false, "VisibleToExternalContributors", metadata, sharedObjects.pushConf.feedDatesFormat);
			// Non-documented fields
			addSimpleMeta(docNode.path("autoplay"), false, "Autoplay", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("duration"), false, "Duration", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("embedded"), false, "Embedded", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("hours"), false, "Hours", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("minutes"), false, "Minutes", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("seconds"), false, "Seconds", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("videoThumbnail"), false, "VideoThumbnail", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("videoType"), false, "VideoType", metadata, sharedObjects.pushConf.feedDatesFormat);
			addSimpleMeta(docNode.path("watermarkURL"), false, "WatermarkURL", metadata, sharedObjects.pushConf.feedDatesFormat);
			// End non-documented fields
			addSimpleMeta(docNode.path("resources").path("html").path("ref"), false, "JiveUrl", metadata, sharedObjects.pushConf.feedDatesFormat);
		}
	}

	@Override
	public String getUrl() throws Exception {
		if (Connector.ID_PREFIX_ATTACHMENT.equals(type)) {
			final String url = getValueAsString(docNode.path("url"), false, sharedObjects.pushConf.feedDatesFormat);
			final String id = getValueAsString(docNode.path("id"), false, sharedObjects.pushConf.feedDatesFormat);
			final String name = getValueAsString(docNode.path("name"), false, sharedObjects.pushConf.feedDatesFormat);
			return url.replaceFirst("/api/core/v3/.+", "/servlet/JiveServlet/download/" + parentIid + "-" + id + "/" + URLEncoder.encode(name, "UTF-8"));
		} else return getValueAsString(docNode.path("resources").path("html").path("ref"), false, sharedObjects.pushConf.feedDatesFormat);
	}

	private static void addSimpleMeta(final TreeNode tn, final boolean isDate, final String label, final List<Metadata> metadata, final ThreadSafeDateFormat feedDatesFormat) throws ParseException {
		metadata.add(new Metadata(label, getValueAsString(tn, isDate, feedDatesFormat)));
	}

	private static String getValueAsString(final TreeNode tn, final boolean isDate, final ThreadSafeDateFormat feedDatesFormat) throws ParseException {
		final String s = ApiHelper.getValueAsString(tn);
		if (StringUtils.isNullOrEmpty(s)) return null;
		else if (isDate) return feedDatesFormat.format(JIVE_DATES.parse(s));
		else return s;
	}

	private static void getStringArrayValues(final TreeNode arrayNode, final List<String> valueContainer) {
		final int nll = arrayNode.size();
		if (nll > 0) for (int i = 0; i < nll; i++) {
			final String s = ApiHelper.getValueAsString(arrayNode.get(i));
			if (StringUtils.isNullOrEmpty(s)) valueContainer.add("nullvalue");
			else valueContainer.add(s);
		}
	}

	private static void getArrayNamedProp(final TreeNode docNode, final String arrayName, final String propName, final List<String> valueContainer, final boolean isDate, final ThreadSafeDateFormat feedDatesFormat) throws ParseException {
		final TreeNode nl = docNode.path(arrayName);
		final int nll = nl.size();
		if (nll > 0) for (int i = 0; i < nll; i++) {
			final String s = ApiHelper.getValueAsString(nl.get(i).path(propName));
			if (StringUtils.isNullOrEmpty(s)) valueContainer.add("nullvalue");
			else if (isDate) valueContainer.add(feedDatesFormat.format(JIVE_DATES.parse(s)));
			else valueContainer.add(s);
		}
	}

	private static void getPersonArray(final TreeNode personNode, final List<String> valueContainer) {
		final int nll = personNode.size();
		if (nll > 0) for (int i = 0; i < nll; i++) {
			final String s = ApiHelper.getValueAsString(personNode.get(i).path("displayName"));
			if (StringUtils.isNullOrEmpty(s)) valueContainer.add("nullvalue");
			else valueContainer.add(s);
		}
	}

	private static void getAttendees(final TreeNode docNode, final String attType, final List<String> valueContainer) {
		final TreeNode nl = docNode.path("attendance").path(attType).path("users");
		final int nll = nl.size();
		if (nll > 0) for (int i = 0; i < nll; i++) {
			final String s = ApiHelper.getValueAsString(nl.get(i).path("displayName"));
			if (StringUtils.isNullOrEmpty(s)) valueContainer.add("nullvalue");
			else valueContainer.add(s);
		}
	}

	private static void getAuthors(final TreeNode docNode, final List<String> valueContainer) {
		final TreeNode nl = docNode.path("authors");
		final int nll = nl.size();
		if (nll > 0) for (int i = 0; i < nll; i++) {
			final String s = ApiHelper.getValueAsString(nl.get(i).path("displayName"));
			if (StringUtils.isNullOrEmpty(s)) valueContainer.add("nullvalue");
			else valueContainer.add(s);
		}
		else valueContainer.add(ApiHelper.getValueAsString(docNode.path("author").path("displayName")));
	}

	private static void getComments(final TreeNode commentsNode, final List<String> valueContainer) {
		if (!(commentsNode == null || commentsNode.isMissingNode())) {
			final TreeNode nl = commentsNode.path("list");
			final int nll = nl.size();
			if (nll > 0) for (int i = 0; i < nll; i++)
				valueContainer.add(renderCommentElement(nl.get(i)));
		}
	}

	public static String renderCommentElement(final TreeNode n) {
		final String author = ApiHelper.getValueAsString(n.path("author").path("displayName"));
		final String body = ApiHelper.getValueAsString(n.path("content").path("text")).replaceAll("</?body[^>]*>", "").replaceAll("<\\!\\-\\-[^>]*\\-\\->", "");
		final int replyCount = ApiHelper.getIntValue(n.path("replyCount"));
		final int likeCount = ApiHelper.getIntValue(n.path("likeCount"));
		return String.format("<table><tr><td>Author</td><td>%s</td></tr><tr><td>Body</td><td>%s</td></tr><tr><td>Reply count</td><td>%d</td></tr><tr><td>Like count</td><td>%d</td></tr></table>", author, body, replyCount, likeCount);
	}

	@Override
	public String getMIME() {
		if (Connector.ID_PREFIX_ATTACHMENT.equals(type) || Connector.ID_PREFIX_FILE.equals(type)) return ApiHelper.getValueAsString(docNode.path("contentType"));
		else return MimeType.HTML.mime;
	}

	@Override
	public boolean hasContent() {
		return true;
	}

	@Override
	public InputStream getContent() throws Exception {
		if (Connector.ID_PREFIX_FILE.equals(type)) {
			final String url = ApiHelper.getValueAsString(docNode.path("binaryURL"));
			final HttpGet getContent = new HttpGet(url);
			if (authzHeader != null) getContent.addHeader(authzHeader);
			if (ras != RunAsStrategy.NONE) getContent.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
			final CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getContent);
			return new FullyClosableInputStream(resp);
		} else if (Connector.ID_PREFIX_ATTACHMENT.equals(type)) {
			final String url = ApiHelper.getValueAsString(docNode.path("url"));
			final HttpGet getContent = new HttpGet(url);
			if (authzHeader != null) getContent.addHeader(authzHeader);
			if (ras != RunAsStrategy.NONE) getContent.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
			final CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getContent);
			return new FullyClosableInputStream(resp);
		} else if (Connector.ID_PREFIX_DOCUMENT.equals(type) || Connector.ID_PREFIX_BOOKMARK.equals(type) || Connector.ID_PREFIX_DISCUSSION.equals(type) || Connector.ID_PREFIX_EVENT.equals(type) || Connector.ID_PREFIX_IDEA.equals(type)
			|| Connector.ID_PREFIX_MESSAGE.equals(type) || Connector.ID_PREFIX_POLL.equals(type) || Connector.ID_PREFIX_POST.equals(type) || Connector.ID_PREFIX_TASK.equals(type) || Connector.ID_PREFIX_UPDATE.equals(type)
			|| Connector.ID_PREFIX_VIDEO.equals(type)) {

			String content = getValueAsString(docNode.path("content").path("text"), false, sharedObjects.pushConf.feedDatesFormat);
			if (StringUtils.isNullOrEmpty(content)) content = "<body>no content found</body>";

			String title = getValueAsString(docNode.path("subject"), false, sharedObjects.pushConf.feedDatesFormat);
			if (StringUtils.isNullOrEmpty(title)) title = "No Title";

			return new ByteArrayInputStream(String.format("<html><head><meta charset=\"UTF-8\"><title>%s</title></head>%s</html>", title, content).getBytes(StandardCharsets.UTF_8));
		} else {
			final String url = ApiHelper.getValueAsString(docNode.path("resources").path("html").path("ref"));
			final HttpGet getContent = new HttpGet(url);
			if (authzHeader != null) getContent.addHeader(authzHeader);
			if (ras != RunAsStrategy.NONE) getContent.addHeader("X-Jive-Run-As", Connector.getImpersonationHeaderValue(owner, ras));
			final CloseableHttpResponse resp = HttpClientHelper.executeWithRetry(httpClient, getContent);
			return new FullyClosableInputStream(resp);
		}
	}

	@Override
	public void close() {}

}
