package com.sword.gsa.connectors.gapps.youtube;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import sword.common.utils.StringUtils;
import sword.gsa.xmlfeeds.builder.Metadata;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Caption;
import com.google.api.services.youtube.model.CaptionListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.sword.gsa.connectors.gapps.apiwrap.ApiHelper;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.throwables.DoNotIndex;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class DocLoader extends ADocLoader {

	private final YouTube ys;
	private TreeNode docJson;
	final ObjectMapper m = new ObjectMapper();
	private String id;
	private String channeltitle;
	private Video video;

	public DocLoader(final AExplorer explorer, final PushProcessSharedObjectsStore sharedObjects, final ContainerNode parentNode) {
		super(explorer, sharedObjects, parentNode);
		Explorer dExpl = (Explorer) explorer;
		ys=dExpl.ys;
		channeltitle=dExpl.channeltitle;

	}

	@Override
	public void loadObject(DocumentNode docNode) throws DoNotIndex, Exception {
		docJson=getVideo(docNode.id);
		id=docNode.id;
	}

	private TreeNode getVideo(String videoId) throws Exception {
		final YouTube.Videos.List get = ys.videos().list("contentDetails,snippet,status,statistics").setId(videoId);
		VideoListResponse list = ApiHelper.executeWithRetry(get, 3);
		video=list.getItems().get(0);
		String json=list.toPrettyString();
		TreeNode t=Explorer.parseJson(m, json).path("items");
		for (int k = 0; k < t.size();) {//size supposed to be 1
			return t.path(k);
		}
		//TODO comments+test without OAuth+private are private
		return null;
	}

	private String getCaptions(String videoId) throws Exception {
		final YouTube.Captions.List get = ys.captions().list("snippet",videoId);
		CaptionListResponse list = ApiHelper.executeWithRetry(get, 3);
		List<Caption> captions=list.getItems();
		String result="";
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		for (int k = 0; k < captions.size(); k++) {
			String id =	captions.get(k).getId();
			LOG.debug("loading caption "+id);
			//ttml format captions parsing
			try(InputStream is=loadCaption(id)){
				org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(is);
				XPath xPath = XPathFactory.newInstance().newXPath();
				Node resultNode = (Node)xPath.evaluate("tt/body/div", doc, XPathConstants.NODE);
				result+=nodeToString(resultNode);
			}
		}

		return result;
	}

	private InputStream loadCaption(String captionId)throws IOException {
		YouTube.Captions.Download get= ys.captions().download(captionId).setTfmt("ttml");//sbv, scc, srt, ttml, vtt
		MediaHttpDownloader downloader = get.getMediaHttpDownloader();
		downloader.setDirectDownloadEnabled(false);
		// Set the download state for the caption track file.
		MediaHttpDownloaderProgressListener downloadProgressListener = new MediaHttpDownloaderProgressListener() {
			@Override
			public void progressChanged(MediaHttpDownloader downloader) throws IOException {
				switch (downloader.getDownloadState()) {
					case MEDIA_IN_PROGRESS:
						LOG.debug("Download in progress");
						LOG.debug("Download percentage: " + downloader.getProgress());
						break;
						// This value is set after the entire media file has
						//  been successfully downloaded.
					case MEDIA_COMPLETE:
						LOG.debug("Download Completed!");
						break;
						// This value indicates that the download process has
						//  not started yet.
					case NOT_STARTED:
						LOG.debug("Download Not Started!");
						break;
				}
			}
		};
		downloader.setProgressListener(downloadProgressListener);
		return get.executeMediaAsInputStream();
	}

	@Override
	public Date getModifyDate() {

		return new Date(video.getSnippet().getPublishedAt().getValue());
	}

	@Override
	public String getMIME() {
		return "video/html";
	}

	@Override
	public void getMetadata(List<Metadata> metadata) {
		addJsonToMeta(docJson, metadata, "");
		metadata.add(new Metadata("channel title", channeltitle));
	}

	@Override
	public String getUrl() {
		return "https://www.youtube.com/watch?v="+id;
	}

	@Override
	public boolean hasContent() {
		return true;
	}

	@Override
	public InputStream getContent() throws DoNotIndex, Exception {
		String title=video.getSnippet().getLocalized().getTitle();//Explorer.getTextValue(docJson.path("snippet").path("title"));
		String desc=video.getSnippet().getDescription();//Explorer.getTextValue(docJson.path("snippet").path("description"));
		String tags="";
		if(video.getSnippet().getTags()!=null){
			for (Iterator<String> iterator = video.getSnippet().getTags().iterator(); iterator.hasNext();) {
				tags += iterator.next()+" ";
			}
		}
		String content="<html><head>"
			+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><meta charset=\"utf-8\"><title>"+title+"</title></head>"
			+ "<body><div>"+desc+"</div>"
			+ "<div>"+tags+"</div>";
		try{
			content+=getCaptions(id);
		}catch(Exception e){
			LOG.warn(e);
		}
		finally{
			content+="</body></html>";
		}

		return IOUtils.toInputStream(content, StandardCharsets.UTF_8);

	}

	private static String nodeToString(Node node)
		throws TransformerException
	{
		StringWriter buf = new StringWriter();
		Transformer xform = TransformerFactory.newInstance().newTransformer();
		xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		xform.transform(new DOMSource(node), new StreamResult(buf));
		return(buf.toString());
	}

	private static void addJsonToMeta(final TreeNode root, final List<Metadata> metadata, final String prefix) {
		if (root.isContainerNode()) {
			if (root.isArray()) for (int i = 0; i < root.size(); i++)
				if (root.path(i).isContainerNode()) {
					if (!StringUtils.isNullOrEmpty(prefix)) addJsonToMeta(root.path(i), metadata, prefix + "_" + i);
					else addJsonToMeta(root.path(i), metadata, "" + i);
				}

			if (root.isObject()) {
				final Iterator<String> it = root.fieldNames();
				while (it.hasNext()) {
					final String name = it.next();
					if (root.path(name).isValueNode()) {
						boolean exists = false;
						if (!exists) {
							Metadata meta;
							if (!StringUtils.isNullOrEmpty(prefix)) meta = new Metadata(prefix + "_" + name, Explorer.getTextValue(root.path(name)));
							else meta = new Metadata(name, Explorer.getTextValue(root.path(name)));

							metadata.add(meta);
						}
					} else if (root.path(name).isContainerNode()) if (!StringUtils.isNullOrEmpty(prefix)) addJsonToMeta(root.path(name), metadata, prefix + "_" + name);
					else addJsonToMeta(root.path(name), metadata, name);
				}
			}
		}
	}

	@Override
	public void close() {

	}

}
