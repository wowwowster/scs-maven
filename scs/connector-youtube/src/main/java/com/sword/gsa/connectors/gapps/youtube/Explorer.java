package com.sword.gsa.connectors.gapps.youtube;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import sword.common.utils.StringUtils;
import sword.connectors.commons.config.CPUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.sword.gsa.connectors.gapps.apiwrap.ApiHelper;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class Explorer extends AExplorer {

	public final YouTube ys;
	private String channel;
	public ApiHelper sp;
	public String channeltitle;
	private String clientId;
	private String clientSecret;
	private String accessToken;
	private String refreshToken;

	public Explorer(final PushProcessSharedObjectsStore sharedObjects) throws Exception {
		super(sharedObjects);
		channel = configurationParams.get(Connector.PARAM_CHANNEL);
		clientId = configurationParams.get(Connector.PARAM_CLIENT_ID);
		clientSecret = configurationParams.get(Connector.PARAM_CLIENT_SECRET);
		accessToken = configurationParams.get(Connector.PARAM_ACCESS_TOKEN);
		refreshToken = configurationParams.get(Connector.PARAM_REFRESH_TOKEN);
		sp = new ApiHelper(null, null, this.sharedObjects.pushConf.httpClientTimeout);
		if (StringUtils.isNullOrEmpty(clientId) || StringUtils.isNullOrEmpty(clientSecret) || StringUtils.isNullOrEmpty(refreshToken)) {
			ys = sp.getYouTubeService(configurationParams.get(Connector.PARAM_APPNAME),configurationParams.get(Connector.PARAM_APIKEY)); //new YouTube.Builder(httpTransport, jsonFactory, null).setYouTubeRequestInitializer(new YouTubeRequestInitializer()).setApplicationName(configurationParams.get(Connector.PARAM_APPNAME)).build();
		}else {
			final GoogleCredential gc = ApiHelper.getCredential(clientId, clientSecret, accessToken, refreshToken, Arrays.asList(new String[] {"https://www.googleapis.com/auth/youtube"}), sharedObjects.pushConf.httpClientTimeout);
			ApacheHttpTransport httpTransport = new ApacheHttpTransport.Builder().build();
			JsonFactory jsonFactory = new JacksonFactory();
			ys = new YouTube.Builder(httpTransport, jsonFactory, gc).setApplicationName(configurationParams.get(Connector.PARAM_APPNAME)).build();			
			RetryableAction.refreshToken(5, gc);
		}
	}
	@Override
	public List<ContainerNode> getRootNodes() throws Exception {
		List<ContainerNode> playLists=new ArrayList<ContainerNode>();
		if(!StringUtils.isNullOrEmpty(channel))playLists=getChannelPlayLists(channel);
		playLists.addAll(getAdditionalPlaylists(CPUtils.stringToList(configurationParams.get(Connector.PARAM_PLAYLISTS))));
		return playLists;
	}
	
	private List<ContainerNode> getAdditionalPlaylists(List<String> playListsIDs) {
		List<ContainerNode> playLists=new ArrayList<ContainerNode>();
		for (int k = 0; k < playListsIDs.size(); k++) {//size supposed to be 1
			playLists.add(new ContainerNode(playListsIDs.get(k), null));
		}
		return playLists;
	}
	//"channel" is either an username (for old youtube account) or an id (for new google+ accounts)
	private List<ContainerNode> getChannelPlayLists(String channel) throws Exception {
		List<ContainerNode> playLists=new ArrayList<ContainerNode>();
		final YouTube.Channels.List get = ys.channels().list("contentDetails,snippet").setForUsername(channel);
		com.google.api.services.youtube.model.ChannelListResponse list = ApiHelper.executeWithRetry(get, 3);
		final YouTube.Channels.List get2 = ys.channels().list("contentDetails,snippet").setId(channel);
		com.google.api.services.youtube.model.ChannelListResponse list2 = ApiHelper.executeWithRetry(get2, 3);
		String json;
		if(list.getItems().size()!=0)
			json=list.toString();
		else json=list2.toString();
		final ObjectMapper m = new ObjectMapper();
		TreeNode t=Explorer.parseJson(m, json).path("items");
		for (int k = 0; k < t.size(); k++) {//size supposed to be 1
			final TreeNode root = t.path(k);
			final String id = Explorer.getTextValue(root.path("contentDetails").path("relatedPlaylists").path("uploads"));
			channeltitle = Explorer.getTextValue(root.path("snippet").path("title"));
			playLists.add(new ContainerNode(id, null));
		}
		return playLists;
	}

	@Override
	public void loadChildren(final ContainerNode node, final boolean isUpdateMode, final boolean isPublicMode) throws Exception {

		List<String>playListItems=getPlayListItems(node.id, null);
		LOG.info("items: "+playListItems.size());
		for (Iterator<String> videoIdIt = playListItems.iterator(); videoIdIt.hasNext();) {
			String videoId = videoIdIt.next();
			node.children.add(new DocumentNode(videoId, node));
		}
	}

	private List<String> getPlayListItems(String playListId, String pageToken) throws Exception {
		List<String> videos=new ArrayList<String>();
		Long maxresults=(long) 50;
		final YouTube.PlaylistItems.List get = ys.playlistItems().list("contentDetails").setPlaylistId(playListId).setPageToken(pageToken).setMaxResults(maxresults);
		PlaylistItemListResponse list = ApiHelper.executeWithRetry(get, 3);
		String json=list.toString();
		final ObjectMapper m = new ObjectMapper();
		TreeNode t=Explorer.parseJson(m, json);
		String token=Explorer.getTextValue(t.path("nextPageToken"));
		TreeNode items=t.path("items");
		for (int k = 0; k < items.size(); k++) {
			final TreeNode item = items.path(k);
			final String id = Explorer.getTextValue(item.path("contentDetails").path("videoId"));
			videos.add(id);
		}
		if(token!=null)videos.addAll(getPlayListItems(playListId,token));
		return videos;
	}

	public static TreeNode parseJson(final ObjectMapper m, final String string) throws JsonProcessingException, IOException {

		if (StringUtils.isNullOrEmpty(string)) return  MissingNode.getInstance();

		TreeNode node = new ObjectNode(m.getNodeFactory());

		try {
			node = m.readTree(string);
		} catch (final JsonParseException e) {
			// if first chars are the UTF-8 BOM. It does not always happen here...
			if (!StringUtils.isNullOrEmpty(string)) node = parseJson(m, string.substring(1));
		} catch (final JsonMappingException e1) {
			return new ObjectNode(m.getNodeFactory());
		}
		return node;
	}

	public static String getTextValue(final TreeNode tn) {
		if (tn.isMissingNode()) return null;
		else if (tn instanceof JsonNode) return ((JsonNode) tn).asText();
		else return null;
	}

	@Override
	public void close() throws Exception {
	}


}