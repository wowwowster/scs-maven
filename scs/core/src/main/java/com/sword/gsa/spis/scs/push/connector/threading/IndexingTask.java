package com.sword.gsa.spis.scs.push.connector.threading;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.springboardindex.model.Item;
import com.sword.springboard.ItemPush;
import org.apache.log4j.Logger;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.DateUtils;
import sword.common.utils.files.MimeType;
import sword.common.utils.numbers.NumFormatUtils;
import sword.gsa.xmlfeeds.builder.Authmethod;
import sword.gsa.xmlfeeds.builder.FeedType;
import sword.gsa.xmlfeeds.builder.Metadata;
import sword.gsa.xmlfeeds.builder.acl.ACL;
import sword.gsa.xmlfeeds.builder.acl.Access;
import sword.gsa.xmlfeeds.builder.acl.AndACL;
import sword.gsa.xmlfeeds.builder.acl.CaseSensitivityType;
import sword.gsa.xmlfeeds.builder.acl.Group;
import sword.gsa.xmlfeeds.builder.acl.Permission;
import sword.gsa.xmlfeeds.builder.acl.Principal;
import sword.gsa.xmlfeeds.builder.acl.PublicDoc;
import sword.gsa.xmlfeeds.builder.streamed.Document;
import sword.gsa.xmlfeeds.builder.streamed.SendFeed;
import sword.gsa.xmlfeeds.builder.streamed.XMLFeedOutputStream;

import com.sword.springboard.JsonFeedOutputStream;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushManager;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.connector.URLManager;
import com.sword.gsa.spis.scs.push.databases.TreeTableManager;
import com.sword.gsa.spis.scs.push.monitoring.Statistics;
import com.sword.gsa.spis.scs.push.throwables.DoNotIndex;
import com.sword.gsa.spis.scs.push.throwables.ReachedIndexingEndTime;
import com.sword.gsa.spis.scs.push.throwables.StopRequestedExternally;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;
import com.sword.gsa.spis.scs.push.tree.ErrorState;
import com.sword.gsa.spis.scs.push.tree.Node;
import com.sword.gsa.spis.scs.push.tree.State;

public final class IndexingTask extends AConnectorTask {

	private static final Logger LOG = Logger.getLogger(IndexingTask.class);

	public static final Charset XML_CHARSET = StandardCharsets.UTF_8;
	public static final String SYS_PARAMETER_ID = "SwordID";
	public static final String SYS_PARAMETER_MDATE = "SwordMDate";
	public static final String SYS_PARAMETER_MIME = "SwordMIME";
	public static final String SYS_PARAMETER_MIME_CATEG = "SwordFileType";
	public static final String SYS_PARAMETER_CONTENT_SIZE = "SwordContentSize";
	public static final String SYS_PARAMETER_SOURCE = "SwordSource";
	public static final String SYS_PARAMETER_FULL_PATH = "FullPath";
	public static final String SYS_PARAMETER_PATH_ELEMENT = "PathElement_";

	private final PushProcessSharedObjectsStore sharedObjects;
	private final AExplorer explorer;
	private final ContainerNode parentNode;
	private final List<DocumentNode> childDocs;
	private final boolean isUpdateDeleteMode;
	private final String parentFullPath;
	private final List<Metadata> parentPathElements = new ArrayList<>();
	private final Map<String, String> metaRenaming = new HashMap<>();
	private final ItemPush push;
	private List<Item> itemsToPush = new ArrayList<Item>();

	public IndexingTask(final PushProcessSharedObjectsStore sos, final ItemPush push, final AExplorer explorer, final ContainerNode parentNode, final List<DocumentNode> childDocs, final boolean isUpdateDeleteMode) {
		super("Indexing " + parentNode.id);
		this.sharedObjects = sos;
		this.push = push;
		this.explorer = explorer;
		this.parentNode = parentNode;
		this.childDocs = childDocs;
		this.isUpdateDeleteMode = isUpdateDeleteMode;

		StringBuilder parentFullPath = new StringBuilder();
		final List<String> parentPathElements = new ArrayList<>();
		Node n = this.parentNode;
		do {
			parentFullPath.insert(0, n.id.replace('/', '_')).insert(0, '/');
			parentPathElements.add(0, n.id);
			n = n.parent;
		} while (n != null);
		this.parentFullPath = parentFullPath.toString();
		for (int i=0; i<parentPathElements.size(); i++) this.parentPathElements.add(new Metadata(SYS_PARAMETER_PATH_ELEMENT + i, parentPathElements.get(i)));

		for (Metadata md: sos.pushConf.metaRenaming) {
			metaRenaming.put(md.name, md.value);
		}
	}

	@Override
	public void doTask() throws Throwable {

		int numDocProcessed = 0;
		int numDocPushed = 0;
		int numDocExcluded = 0;
		int numDocErrors = 0;
		int numDocUnchanged = 0;
		final long start = System.currentTimeMillis();
		final long time = start;
		final boolean ite = LOG.isTraceEnabled();

		final List<String> publicGroups=sharedObjects.pushConf.publicGroups;
		final String ns = sharedObjects.pushConf.aclNamespace;
		final String sug = sharedObjects.pushConf.aclSuperUserGroup;
		final boolean uag = sharedObjects.pushConf.aclUsersAsGroups;

		try (
			ADocLoader indexer = sharedObjects.getDocLoader(explorer, parentNode); 
			//XMLFeedOutputStream out = ExploringTask.getXmlOutputStream(sharedObjects);
			JsonFeedOutputStream out = ExploringTask.getJsonOutputStream(sharedObjects);//TODO remove & replace with map
			TreeTableManager treeMgr = new TreeTableManager(sharedObjects.pushConf, false)
			) {

			if (sharedObjects.rateWatcher != null) out.setRateWatcher(sharedObjects.rateWatcher);//TODO update for request/sec

			boolean hasReceivedStopRequest = false;
			boolean hasReachedEndOfSchedule = false;

			final Map<DocumentNode, Integer> docStates = new HashMap<>();
			final Map<DocumentNode, Date> docDates = new HashMap<>();

			for (final DocumentNode docNode : childDocs) {

				hasReceivedStopRequest = PushManager.hasReceivedStopRequest(sharedObjects.pushConf.connectorCtx, sharedObjects.pushConf.connectorId);
				hasReachedEndOfSchedule = PushManager.hasReachedEndOfSchedule(sharedObjects.pushConf.endTime, System.currentTimeMillis());

				if (hasReceivedStopRequest || hasReachedEndOfSchedule) break;

				numDocProcessed++;

				if (docNode.state == State.EXCLUDED) {
					numDocExcluded++;
					LOG.debug("Skipping node because of its exclusion state: " + docNode);
					continue;
				}

				if (ite) {
					LOG.trace("isUpdateDeleteMode: " + isUpdateDeleteMode + " ; isDateSet: " + docNode.isDateSet + " ; lastModificationDate: " + docNode.lastModificationDate + " ; state: " + docNode.state);
				}

				if (isUpdateDeleteMode && docNode.isDateSet && (docNode.lastModificationDate != null)) {//If doc has already been indexed since it was modified -> skip
					if (ite) LOG.trace("Checking if document has been indexed since " + docNode.lastModificationDate);
					Date lid = treeMgr.getLastIndexingDate(docNode);
					if (ite) LOG.trace("Last indexing date: " + lid);
					if ((lid != null) && (lid.after(docNode.lastModificationDate))) {
						if (ite) LOG.trace("Skipping already indexed document because it has not been modified since last indexing time");
						numDocUnchanged++;
						//						treeMgr.updateState(docNode, State.COMPLETE, false);
						docStates.put(docNode, State.COMPLETE);
						Statistics.INSTANCE.documentsSkippedAsUnmodified.incrementAndGet();
						continue;
					} else if (ite) LOG.trace("Indexing document because it has not been reindexed since the last time it was modified");
				}

				if (sharedObjects.rateWatcher != null) {
					final long tWaited = sharedObjects.rateWatcher.waitAcceptableRate();
					if (tWaited > 0L) LOG.debug("Rate was too high ; waited for " + tWaited + "mS");
				}
				try {
					if (ite) LOG.trace("Loading object: " + docNode.id);
					indexer.loadObject(docNode);
				} catch (final DoNotIndex e) {
					numDocExcluded++;
					LOG.info("Object will not be indexed: " + docNode.id + "; cause: " + e.getMessage());
					//					treeMgr.updateState(docNode, State.EXCLUDED, false);
					docStates.put(docNode, State.EXCLUDED);
					docDates.put(docNode, null);
					Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					continue;
				} catch (final Exception e) {
					LOG.error("Could not load object [" + docNode + "] - setting error state", e);
					final ErrorState es = State.getErrorState(docNode);
					docStates.put(docNode, es.state);
					if (es.state == State.EXCLUDED) {
						numDocExcluded++;
						docDates.put(docNode, null);
						LOG.error("Reached repeating error max count - excluding node");
						Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					} else {
						numDocErrors++;
						LOG.error("Error count: " + es.errorCount);
						Statistics.INSTANCE.indexingErrors.incrementAndGet();
					}
					continue;
				}

				Date curMD;
				try {
					curMD = indexer.getModifyDate();
				} catch (final Exception e) {
					LOG.error("Could not load object ModifyDate: " + docNode.id, e);
					curMD = null;
				}
				if (curMD == null) curMD = new Date();// If date is null, set the date to a recent date for the code to behave as if document had been modified recently
				else if (ite) LOG.trace("Last modif date: " + curMD.toString());

				if (ite) {
					if (isUpdateDeleteMode) LOG.trace("Checking if " + curMD + " is more recent than doc's last indexing time");
					else LOG.trace("Not checking Last Modification date");
				}
				if (isUpdateDeleteMode) {//If doc has already been indexed since it was modified -> skip
					Date lid = treeMgr.getLastIndexingDate(docNode);
					if (ite) LOG.trace("Last indexing date: " + lid);
					if ((lid != null) && (lid.after(curMD))) {
						if (ite) LOG.trace("Skipping already indexed document because it has not been modified since last indexing time");
						numDocUnchanged++;
						//						treeMgr.updateState(docNode, State.COMPLETE, false);
						docStates.put(docNode, State.COMPLETE);
						Statistics.INSTANCE.documentsSkippedAsUnmodified.incrementAndGet();
						continue;
					} else if (ite) LOG.trace("Indexing document because it has not been reindexed since the last time it was modified");
				}

				final String knownMIME;
				try {
					knownMIME = indexer.getMime(docNode.id);
				} catch (final Exception e) {
					LOG.error("Could not load MIME for object [" + docNode + "] - setting error state", e);
					final ErrorState es = State.getErrorState(docNode);
					docStates.put(docNode, es.state);
					if (es.state == State.EXCLUDED) {
						numDocExcluded++;
						docDates.put(docNode, null);
						LOG.error("Reached repeating error max count - excluding node");
						Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					} else {
						numDocErrors++;
						LOG.error("Error count: " + es.errorCount);
						Statistics.INSTANCE.indexingErrors.incrementAndGet();
					}
					continue;
				}
				if (sharedObjects.pushConf.unsupportedMimeTypes.contains(knownMIME)) {
					numDocExcluded++;
					LOG.debug("Document MIME is not supported: " + knownMIME);
					//					treeMgr.updateState(docNode, State.EXCLUDED, false);
					docStates.put(docNode, State.EXCLUDED);
					docDates.put(docNode, null);
					Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					continue;
				}

				final String sysUrl = URLManager.getSystemURL(sharedObjects.pushConf.datasource, docNode.id);

				Authmethod am = sharedObjects.pushConf.authMethod;
				final Permission p;
				if (am == Authmethod.none) {
					p = new PublicDoc();
				} else {
					Permission pTemp = Permission.deserialize(docNode.serPerm);
					if (pTemp!=null && publicGroups!=null && publicGroups.size()>0){
						if (pTemp instanceof ACL){
							ACL a= (ACL) pTemp;
							List<Principal> principals = a.principals;
							if(principals!=null){
								for(Principal princ:principals)
								{
									for (String pg:publicGroups){
										if(princ.principal!=null && princ.principal.equals(pg)){
											LOG.debug("The following node [" + docNode + "] belongs to a public group ["+pg+"] - ACLs will be skipped and document will be sent as public");
											pTemp = new PublicDoc();
											break;
										}
									}
								}
							}
						}
						else if (pTemp instanceof AndACL){
							AndACL a= (AndACL) pTemp;
							List<List<Principal>> principals = a.principalsList;
							if(principals!=null){
								for(List<Principal> princList:principals)
								{
									for(Principal princ:princList)
									{
										for (String pg:publicGroups){
											if(princ.principal!=null && princ.principal.equals(pg)){
												LOG.debug("The following node [" + docNode + "] belongs to a public group ["+pg+"] - ACLs will be skipped and document will be sent as public");
												pTemp = new PublicDoc();
												break;
											}
										}
									}
								}
							}
						}
					}
					p=pTemp;
					if (p == null || p instanceof PublicDoc) am = Authmethod.none;
				}

				final List<Metadata> metas = new ArrayList<>();
				try {
					indexer.getMetadata(metas);
				} catch (Exception e) {
					LOG.error("Could not load metadata for object [" + docNode + "] - setting error state", e);
					final ErrorState es = State.getErrorState(docNode);
					docStates.put(docNode, es.state);
					if (es.state == State.EXCLUDED) {
						numDocExcluded++;
						docDates.put(docNode, null);
						LOG.error("Reached repeating error max count - excluding node");
						Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					} else {
						numDocErrors++;
						LOG.error("Error count: " + es.errorCount);
						Statistics.INSTANCE.indexingErrors.incrementAndGet();
					}
					continue;
				}
				for (Metadata md: sharedObjects.pushConf.constants) metas.add(md);

				metas.add(new Metadata(SYS_PARAMETER_FULL_PATH, String.format("%s/%s", parentFullPath, docNode.id)));
				metas.addAll(parentPathElements);
				metas.add(new Metadata(SYS_PARAMETER_ID, docNode.id));
				metas.add(new Metadata(SYS_PARAMETER_MDATE, sharedObjects.pushConf.feedDatesFormat.format(curMD)));
				metas.add(new Metadata(SYS_PARAMETER_MIME, knownMIME));
				metas.add(new Metadata(SYS_PARAMETER_MIME_CATEG, MimeType.getMimeCategory(knownMIME)));
				metas.add(new Metadata(SYS_PARAMETER_SOURCE, sharedObjects.pushConf.datasource));

				final List<Metadata> tagsToRemove = new ArrayList<>();
				for (final Metadata tag : metas)
					if (StringUtils.hasOneNullOrEmpty(new String[] {tag.name, tag.value})) tagsToRemove.add(tag);
				if (!tagsToRemove.isEmpty()) {
					metas.removeAll(tagsToRemove);
					if (ite){
						for(Metadata tagRem:tagsToRemove){
							if(tagRem!=null && Metadata.isValid(tagRem))
								LOG.trace("Removing empty meta: " + tagRem);
						}
					}
				}

				tagsToRemove.clear();
				final List<Metadata> newTags = new ArrayList<>();
				for (final Metadata tag : metas) {
					if (metaRenaming.containsKey(tag.name)) {
						tagsToRemove.add(tag);
						newTags.add(new Metadata(metaRenaming.get(tag.name), tag.value));
					}
				}
				if (!tagsToRemove.isEmpty()) metas.removeAll(tagsToRemove);
				if (!newTags.isEmpty()) metas.addAll(newTags);

				InputStream content = null;
				try {
					if (indexer.hasContent()) {
						@SuppressWarnings("resource")
						InputStream _content = indexer.getContent();
						content = _content;
					}
				} catch (final DoNotIndex e) {
					numDocExcluded++;
					LOG.info("Object will not be indexed: " + docNode.id + "; cause: " + e.getMessage());
					//					treeMgr.updateState(docNode, State.EXCLUDED, false);
					docStates.put(docNode, State.EXCLUDED);
					docDates.put(docNode, null);
					Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					continue;
				} catch (final Throwable e) {
					LOG.error("Could not get content of object [" + docNode + "] - setting error state", e);
					final ErrorState es = State.getErrorState(docNode);
					docStates.put(docNode, es.state);
					if (es.state == State.EXCLUDED) {
						numDocExcluded++;
						docDates.put(docNode, null);
						LOG.error("Reached repeating error max count - excluding node");
						Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					} else {
						numDocErrors++;
						LOG.error("Error count: " + es.errorCount);
						Statistics.INSTANCE.indexingErrors.incrementAndGet();
					}
					continue;
				}

				String curURL;
				try {
					curURL = indexer.getUrl();
					if (ite) LOG.trace("\t\t\t- " + curURL);
				} catch (final Exception e) {
					LOG.error("Could not get URL of object [" + docNode + "] - setting error state", e);
					final ErrorState es = State.getErrorState(docNode);
					docStates.put(docNode, es.state);
					if (es.state == State.EXCLUDED) {
						numDocExcluded++;
						docDates.put(docNode, null);
						LOG.error("Reached repeating error max count - excluding node");
						Statistics.INSTANCE.documentsExcluded.incrementAndGet();
					} else {
						numDocErrors++;
						LOG.error("Error count: " + es.errorCount);
						Statistics.INSTANCE.indexingErrors.incrementAndGet();
					}
					continue;
				}

				boolean feedAlreadySent = false;
				//try {
					final Document document = new Document(docNode.id, "Document #" + docNode.id, knownMIME, curMD, metas, content);
					long contentSize = -1;

					if (p == null) {
						//contentSize = out.addContentRecord(sysUrl, curURL, true, am, -1, null, document, sharedObjects.pushConf.maxContentSize);
						push.registerSBItem(itemsToPush, document, curURL);
					} else if (p instanceof PublicDoc) {
						//contentSize = out.addContentRecord(sysUrl, curURL, true, Authmethod.none, -1, null, document, sharedObjects.pushConf.maxContentSize);
						push.registerSBItem(itemsToPush, document, curURL);
					} else if (p instanceof ACL) {
						ACL a = (ACL) p;
						if (uag) ACL.usersAsGroups(a);
						if (!StringUtils.isNullOrEmpty(sug)) a.principals.add(new Group(sug, Access.PERMIT, ns, CaseSensitivityType.EVERYTHING_CASE_SENSITIVE));
						//contentSize = out.addContentRecord(sysUrl, curURL, true, am, -1, a, document, sharedObjects.pushConf.maxContentSize);
						push.registerSBItem(itemsToPush, document, curURL, a);
					} else if (p instanceof AndACL) {
						ACL a = AndACL.toAclArray((AndACL) p, sysUrl, ns, sug, uag, CaseSensitivityType.EVERYTHING_CASE_SENSITIVE)[0];
						//contentSize = out.addContentRecord(sysUrl, curURL, true, am, -1, a, document, sharedObjects.pushConf.maxContentSize);
						push.registerSBItem(itemsToPush, document, curURL, a);
					} else {
						//contentSize = out.addContentRecord(sysUrl, curURL, true, am, -1, null, document, sharedObjects.pushConf.maxContentSize);
						push.registerSBItem(itemsToPush, document, curURL);
					}

					//Statistics.INSTANCE.updateContentBytesAddedToFeed(contentSize);//TODO update with requests
				/*} catch (final SendFeed e) {
					Statistics.INSTANCE.updateContentBytesAddedToFeed(e.lastFileAddedLength);
					try {
						if(sharedObjects.pushConf.ftpMode) sharedObjects.feedPoster.upload(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, e.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, sharedObjects.pushConf.ftpUsername, sharedObjects.pushConf.ftpPassword);
						else sharedObjects.feedPoster.post(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, e.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, true);//TODO update but might be beforehand as we send 1 by 1
						Statistics.INSTANCE.updateBytesActuallySentToGsa(e.feedFile.length());
					} catch (final Throwable t) {
						handleFeedPostError(sharedObjects.pushConf, e.feedFile, t);
					}
					feedAlreadySent = true;
				}*/

				if (ite) LOG.trace("Inserting into DB");
				docStates.put(docNode, State.COMPLETE);
				docDates.put(docNode, new Date());
				//				treeMgr.updateStateAndDate(docNode, State.COMPLETE, new Date(), false);
				Statistics.INSTANCE.documentsAddedToFeedForIndexing.incrementAndGet();
				numDocPushed++;
				if (feedAlreadySent) {
					treeMgr.updateStatesAndDates(docStates, docDates);
					docStates.clear();
					docDates.clear();
				}

				if (LOG.isInfoEnabled() && numDocProcessed % 500 == 0) {
					final double timeSpent = System.currentTimeMillis() - start;
					final double processed = numDocProcessed;
					final double actualTotal = childDocs.size();
					if (actualTotal > 0d) {// If all versions are pushed, the actual total number of docs is unknown
						final double percentDone = processed / actualTotal;
						if (percentDone > 0d) {
							final double percentRemaining = 1 - percentDone;
							final double doneRemRatio = percentRemaining / percentDone;
							LOG.info(new StringBuilder("Processed ").append(Integer.toString(numDocProcessed)).append(" items (").append(NumFormatUtils.readablePercentage(percentDone)).append("%) in ").append(DateUtils.toReadableTimeSpan((long) timeSpent))
								.append(" ; Estimated time remaining: ").append(DateUtils.toReadableTimeSpan((long) (timeSpent * doneRemRatio))).toString());
						}
					}
				}
			}

			if (hasReceivedStopRequest) LOG.info("Interrupting process [external interruption request]");
			else if (hasReachedEndOfSchedule) LOG.info("Interrupting process [schedule]");
			else LOG.info(new StringBuilder("Node: ").append(parentNode.id).append(" completed successfully:").append(EnvUtils.CR).append("\t- ").append(numDocProcessed).append(" docs processed").append(EnvUtils.CR).append("\t- ").append(numDocPushed)
				.append(" docs indexed").append(EnvUtils.CR).append("\t- ").append(numDocExcluded).append(" docs excluded").append(EnvUtils.CR).append("\t- ").append(numDocErrors).append(" docs raised an error and will be reprocessed at a later time")
				.append(EnvUtils.CR).append("\t- ").append(numDocUnchanged).append(" docs have been skipped because they had not changed").toString());

			if (!itemsToPush.isEmpty()) {
				final long timeSpent = System.currentTimeMillis() - time;
				LOG.info(new StringBuilder("Processed ").append(Integer.toString(numDocProcessed)).append(" items in ").append(DateUtils.toReadableTimeSpan(timeSpent)).toString());
				/*final SendFeed sf = out.closeAndPrepareShipment();
				try {
					if(sharedObjects.pushConf.ftpMode) sharedObjects.feedPoster.upload(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, sf.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, sharedObjects.pushConf.ftpUsername, sharedObjects.pushConf.ftpPassword);
					else sharedObjects.feedPoster.post(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, sf.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, true);
					Statistics.INSTANCE.updateBytesActuallySentToGsa(sf.feedFile.length());
				} catch (final Throwable t) {
					handleFeedPostError(sharedObjects.pushConf, sf.feedFile, t);
				}
*/
				push.pushAllItems(itemsToPush);
			}
			if (!(docStates.isEmpty() && docDates.isEmpty())) {
				treeMgr.updateStatesAndDates(docStates, docDates);
				docStates.clear();
				docDates.clear();
			}
			if (hasReceivedStopRequest) throw new StopRequestedExternally();
			else if (hasReachedEndOfSchedule) throw new ReachedIndexingEndTime();
			return;

		}

	}

	private static void handleFeedPostError(final PushConfig c, final File feedFile, final Throwable t) throws Throwable {
		if (c.feedsConservationPeriod == 143) LOG.error(
			"GSA did not accept feed continuing as feeds are stored locally for more than 143 days. If this feed is not sent manually within 3 days, it will be lost forever. Feed path: " + feedFile.getAbsolutePath(), t);
		else throw t;
	}
}
