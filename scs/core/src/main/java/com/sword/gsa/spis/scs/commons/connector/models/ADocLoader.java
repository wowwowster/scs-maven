package com.sword.gsa.spis.scs.commons.connector.models;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import sword.common.utils.files.MimeType;
import sword.gsa.xmlfeeds.builder.Metadata;

import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.throwables.DoNotIndex;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public abstract class ADocLoader implements AutoCloseable {

	protected static final Logger LOG = Logger.getLogger(ADocLoader.class);

	protected final PushProcessSharedObjectsStore sharedObjects;
	protected final ContainerNode parentNode;
	protected final Map<String, String> configurationParams;

	public ADocLoader(final AExplorer explorer, final PushProcessSharedObjectsStore sharedObjects, final ContainerNode parentNode) {
		super();
		this.sharedObjects = sharedObjects;
		this.parentNode = parentNode;
		configurationParams = ((AConnector) this.sharedObjects.connector).cpMap;
	}

	public abstract void loadObject(DocumentNode docNode) throws DoNotIndex, Exception;

	public abstract Date getModifyDate() throws Exception;

	public String getMime(final String curID) {
		String curMime = MimeType.UNKNOWN_BINARY.mime;
		try {
			curMime = getMIME();
			if (curMime == null) curMime = MimeType.UNKNOWN_BINARY.mime;
		} catch (final Exception e) {
			LOG.info("No Mime for doc with ID: " + curID);
			curMime = MimeType.UNKNOWN_BINARY.mime;
		}
		LOG.trace("MimeType: " + curMime);
		return curMime;
	}

	public abstract void getMetadata(final List<Metadata> metadata) throws Exception;

	protected static final void addMetaTag(final String tagName, final List<String> metaValues, final List<Metadata> metadata) {
		final int metaCount = metaValues.size();
		if (metaCount > 0) {
			if (metaCount > 1) {
				for (int i = 0; i < metaCount; i++) {
					final String value = metaValues.get(i);
					metadata.add(new Metadata(tagName, value));
					metadata.add(new Metadata(tagName + "_" + i, value));
				}
			} else metadata.add(new Metadata(tagName, metaValues.get(0)));
		}
	}

	protected abstract String getMIME() throws Exception;

	public abstract boolean hasContent() throws Exception;

	public abstract InputStream getContent() throws Exception, DoNotIndex;

	public abstract String getUrl() throws Exception;

}
