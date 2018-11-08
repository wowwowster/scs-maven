package com.sword.gsa.spis.scs.push.connector.threading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.enterprise.springboard.common.Application;
import com.sword.springboard.ItemPush;
import com.sword.springboard.JsonFeedOutputStream;
import org.apache.log4j.Logger;

import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.throwables.ExceptionDigester;
import sword.gsa.xmlfeeds.builder.FeedType;
import sword.gsa.xmlfeeds.builder.acl.ACL;
import sword.gsa.xmlfeeds.builder.acl.Access;
import sword.gsa.xmlfeeds.builder.acl.AndACL;
import sword.gsa.xmlfeeds.builder.acl.CaseSensitivityType;
import sword.gsa.xmlfeeds.builder.acl.Group;
import sword.gsa.xmlfeeds.builder.acl.Permission;
import sword.gsa.xmlfeeds.builder.acl.Principal;
import sword.gsa.xmlfeeds.builder.acl.PublicDoc;
import sword.gsa.xmlfeeds.builder.streamed.SendFeed;
import sword.gsa.xmlfeeds.builder.streamed.XMLFeedOutputStream;

import com.sword.gsa.spis.scs.commons.connector.models.AEventExplorer;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushManager;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.connector.URLManager;
import com.sword.gsa.spis.scs.push.databases.AclHashStore;
import com.sword.gsa.spis.scs.push.databases.PushTableManager;
import com.sword.gsa.spis.scs.push.databases.TreeTableManager;
import com.sword.gsa.spis.scs.push.monitoring.Statistics;
import com.sword.gsa.spis.scs.push.throwables.ExceptionWrapper;
import com.sword.gsa.spis.scs.push.throwables.ReachedIndexingEndTime;
import com.sword.gsa.spis.scs.push.throwables.StopRequestedExternally;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;
import com.sword.gsa.spis.scs.push.tree.ErrorState;
import com.sword.gsa.spis.scs.push.tree.Node;
import com.sword.gsa.spis.scs.push.tree.NodeRef;
import com.sword.gsa.spis.scs.push.tree.State;

public final class ExploringTask {

	private static final Logger LOG = Logger.getLogger(ExploringTask.class);

	private final PushProcessSharedObjectsStore sharedObjects;
	private final ThreadPoolManager indexingThreadMgr;
	private final Digester md5HashCalculator;
	private final List<String> publicGroups;
	private final ItemPush push;

	public ExploringTask(final PushProcessSharedObjectsStore sharedObjects) throws GeneralSecurityException, IOException {
		this.sharedObjects = sharedObjects;
		publicGroups=sharedObjects.pushConf.publicGroups;
		indexingThreadMgr = new ThreadPoolManager(this.sharedObjects.indexingThreadQueue);
		md5HashCalculator = new Digester(HashAlgorithm.MD5);
		Properties config = new Properties();
		LOG.info("cloudsource: "+sharedObjects.pushConf.cloudsource);
		config.setProperty("api.customerId",sharedObjects.scsConf.configuredCloudSources.get(sharedObjects.pushConf.cloudsource).custId);
		config.setProperty("api.sourceId",sharedObjects.scsConf.configuredCloudSources.get(sharedObjects.pushConf.cloudsource).sourceId);
		config.setProperty("api.serviceAccountPrivateKeyFile","../../../../"+sharedObjects.scsConf.configuredCloudSources.get(sharedObjects.pushConf.cloudsource).filename);
		Application.Options options = new Application.Options().setConfig(config)
				.setHttpRequestInitializer(new Application.RetryRequestInitializer());
		push = new ItemPush(options);
	}

	public void run() throws Throwable {

		boolean hasReceivedStopRequest = false;
		boolean hasReachedEndOfSchedule = false;
		final boolean deletionMode;

		final boolean ite = LOG.isTraceEnabled();

		Throwable thrownInFinallyBlock = null;//When a finally block throws an Exception, the Exception that was thrown before entering the finally block (if any) is lost => prevent that
		try (
			final TreeTableManager treeMgr = new TreeTableManager(sharedObjects.pushConf, true); 
			final AclHashStore aclHashMgr = new AclHashStore(sharedObjects.pushConf); 
			) {

			Statistics.INSTANCE.isInitialIndexing.set(sharedObjects.isInitialPush);

			boolean isRecovering = treeMgr.isRecovering();
			Statistics.INSTANCE.isRecovering.set(isRecovering);
			if (!isRecovering) sharedObjects.monitor.clearHistory();

			//If no ACL or ACL change detected directly by explorer - a deletion push is necessary to cleanup non-existent documents
			// In that case check how long it's been since last deletion push
			if (!sharedObjects.connectorIndexesACLs || sharedObjects.connectorCanDetectAclChanges) {
				if (sharedObjects.hasUnfinishedDeletionPush) {
					LOG.info("Indexer is tracking deleted documents for cleanup");
					deletionMode = true;
				} else {
					final String deletionFrequencyStr = System.getProperty("scs.CleanupFrequency", "30");
					final long deletionFrequency = TimeUnit.DAYS.toMillis(Long.parseLong(deletionFrequencyStr));
					deletionMode = (System.currentTimeMillis() - sharedObjects.lastDeletionPushDate.getTime()) > deletionFrequency;
					if (deletionMode) {
						LOG.info("Indexer will also track deleted documents for cleanup");
						try (PushTableManager ptm = new PushTableManager(sharedObjects.pushConf)) {
							ptm.registerDeletionPushStart();
						}
					}
				}
			} else {
				deletionMode = false;
			}

			final boolean emptyTree = treeMgr.isEmpty();// emptyTree case can happen when a Doctype is added - deprecated

			// Tells the Explorer whether it should return all children or only modified children
			final boolean isUpdateMode = !(sharedObjects.isInitialPush || emptyTree || deletionMode);

			//Defines whether each time Explorer#loadChildren is called, the list of children that is returned is a complete list, or is a list of modified entities.
			final boolean loadChildrenMethodReturnsExhaustiveList = (!isUpdateMode) || (isUpdateMode && sharedObjects.connectorIndexesACLs && !sharedObjects.connectorCanDetectAclChanges);

			try (AExplorer explorer = sharedObjects.getExplorer()) {

				try {

					//if Explorer support Events and if events processing is enabled in config
					final boolean isEventMode = (explorer instanceof AEventExplorer) && ((AEventExplorer) explorer).isEventModeEnabled();
					Statistics.INSTANCE.isEventMode.set(isEventMode);

					// Update-delete mode happens for connectors that index ACLs and are not able to detect ACL modifications
					final boolean isUpdateDeleteMode = (isUpdateMode && !isEventMode && loadChildrenMethodReturnsExhaustiveList) || deletionMode;

					if (ite) LOG.trace(String.format("isRecovering: %b ; emptyTree: %b ; isUpdateMode: %b ; isPublicMode: %b ; isEventMode: %b ; isUpdateDeleteMode: %b", isRecovering, emptyTree, isUpdateMode, !sharedObjects.connectorIndexesACLs, isEventMode, isUpdateDeleteMode));

					final StringBuilder sb = new StringBuilder("Exploration task is starting:");

					if (sharedObjects.isInitialPush || emptyTree) sb.append(EnvUtils.CR).append("\t- initial indexing");
					else sb.append(EnvUtils.CR).append("\t- incremental indexing");

					if (isRecovering) sb.append(EnvUtils.CR).append("\t- task is recovering from a previous unfinished process");

					if (isUpdateMode) {
						if (isUpdateDeleteMode) sb.append(EnvUtils.CR).append("\t- all items will be retrieved - modified items will be identified by using their last modification date");
						else sb.append(EnvUtils.CR).append("\t- only modified items will be retrieved");
					} else sb.append(EnvUtils.CR).append("\t- all items will be retrieved");

					LOG.info(sb);

					final List<ContainerNode> containers = new ArrayList<>();
					LOG.info("Loading root nodes");

					List<ContainerNode> rootNodes = explorer.getRootNodes();
					{//Delete children of deleted root nodes
						List<ContainerNode> knownRootNodes = treeMgr.getRootNodes();

						try (XMLFeedOutputStream deletedNodesXmlOS = getXmlOutputStream(sharedObjects)) {

							boolean hasDeletedNodes = false;
							for (ContainerNode krn: knownRootNodes) {
								if (!rootNodes.contains(krn)) {

									hasDeletedNodes = true;

									List<NodeRef> children = treeMgr.removeNodeAndChildren(new NodeRef(true, krn.id));
									Statistics.INSTANCE.documentsAddedToFeedForDeletion.addAndGet(children.size());
									for (NodeRef ntr : children) {
										try {
											deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, ntr.id));
										} catch (final SendFeed e) {
											handleSendFeed(e);
										}
									}

								}
							}
							if (deletedNodesXmlOS.containsAnyRecord()) {
								final SendFeed sf = deletedNodesXmlOS.closeAndPrepareShipment();
								handleSendFeed(sf);
							}
							if (hasDeletedNodes) treeMgr.commit();

						}

					}

					containers.addAll(rootNodes);
					ensureNoDuplicates(containers);

					LOG.info(String.format("Found %d root nodes", containers.size()));
					Statistics.INSTANCE.nodesExplored.incrementAndGet();

					try (XMLFeedOutputStream nodesAclsXOS = getXmlOutputStream(sharedObjects, 12_000_000)) {

						if (isEventMode) {
							if ((sharedObjects.isInitialPush && !isRecovering) || emptyTree) {
								LOG.info("Storing audit initial state");
								((AEventExplorer) explorer).storeInitialState();
								LOG.info("Audit initial state stored successfully");
							} else if (!isRecovering) {

								LOG.info("Parsing audit events");
								final List<NodeRef> deletedItems = new ArrayList<>();
								((AEventExplorer) explorer).processEvents(treeMgr, deletedItems);// Will update node state only for modified nodes
								LOG.info("Audit events parsed successfully");

								try (XMLFeedOutputStream deletedNodesXmlOS = getXmlOutputStream(sharedObjects)) {

									for (final NodeRef di : deletedItems) {
										try {
											Statistics.INSTANCE.documentsAddedToFeedForDeletion.incrementAndGet();
											deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, di.id));
										} catch (final SendFeed e) {
											handleSendFeed(e);
										}
										if (di.isContainer) {
											final List<NodeRef> cdis = treeMgr.removeNodeAndChildren(di);
											Statistics.INSTANCE.documentsAddedToFeedForDeletion.addAndGet(cdis.size());
											for (final NodeRef cdi : cdis)
												try {
													deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, cdi.id));
												} catch (final SendFeed e) {
													handleSendFeed(e);
												}
										} else {
											treeMgr.removeNode(di, false);
										}
									}
									if (deletedNodesXmlOS.containsAnyRecord()) {
										final SendFeed sf = deletedNodesXmlOS.closeAndPrepareShipment();
										handleSendFeed(sf);
									}
									((AEventExplorer) explorer).commitDeletionChanges(deletedItems);
									treeMgr.commit();
									isRecovering = true;

								}
							}
						}

						ContainerNode curNode = null;
						while (!(
							containers.isEmpty() || 
							(hasReceivedStopRequest = PushManager.hasReceivedStopRequest(sharedObjects.pushConf.connectorCtx, sharedObjects.pushConf.connectorId)) || 
							(hasReachedEndOfSchedule = PushManager.hasReachedEndOfSchedule(sharedObjects.pushConf.endTime, System.currentTimeMillis()))
							)) {

							curNode = containers.remove(0);

							if (treeMgr.contains(curNode)) {

								if (isRecovering || isEventMode) treeMgr.reloadState(curNode);// If is recovering or event mode, state from DB determines whether children need reloading or not
								else treeMgr.checkExclusionState(curNode);// Otherwise we just need to know if node is excluded
								if (ite) LOG.trace(String.format("Reloaded state for root node %s -> %d", curNode, curNode.state));
							} else {
								treeMgr.addNode(curNode, true);
							}

							if (curNode.state == State.EXCLUDED) {

								LOG.debug("Skipping node because of its exclusion state: " + curNode);

								try (XMLFeedOutputStream deletedNodesXmlOS = getXmlOutputStream(sharedObjects)) {
									List<NodeRef> children = treeMgr.removeNodeChildren(new NodeRef(true, curNode.id));
									Statistics.INSTANCE.documentsAddedToFeedForDeletion.addAndGet(children.size());
									for (NodeRef ntr : children) {
										try {
											deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, ntr.id));
										} catch (final SendFeed e) {
											handleSendFeed(e);
										}
									}
									if (deletedNodesXmlOS.containsAnyRecord()) {
										final SendFeed sf = deletedNodesXmlOS.closeAndPrepareShipment();
										handleSendFeed(sf);
									}
									treeMgr.commit();
								}
								continue;
							}

							if (sharedObjects.connectorIndexesACLs) processNodePermissions(nodesAclsXOS, curNode, aclHashMgr);

							if ((!isRecovering) || (isRecovering && (curNode.state != State.COMPLETE))) {
								try {
									explorer.loadChildren(curNode, isUpdateMode, !sharedObjects.connectorIndexesACLs);
									ensureNoDuplicates(curNode.children);
									LOG.info(String.format("Loaded %d children for node %s", curNode.children.size(), curNode));
									for (final Node child : curNode.children) {
										if (child instanceof DocumentNode) {
											treeMgr.checkExclusionState(child);
											Statistics.INSTANCE.documentsFound.incrementAndGet();
										}
									}
									Statistics.INSTANCE.nodesExplored.incrementAndGet();
								} catch (final Exception e) {
									LOG.error("Could not load children of Node [" + curNode + "] - setting error state", e);
									final ErrorState es = State.setErrorState(treeMgr, curNode, true);
									if (es.state == State.EXCLUDED) {
										LOG.error("Reached repeating error max count - excluding node");
										Statistics.INSTANCE.nodesExcluded.incrementAndGet();
									} else {
										LOG.error("Error count: " + es.errorCount);
										Statistics.INSTANCE.explorationErrors.incrementAndGet();
									}
									continue;
								}

								if (loadChildrenMethodReturnsExhaustiveList) {// If list of nodes is exhaustive => remove old children

									if (ite) LOG.trace("Children list is exhaustive - saving list");
									final List<NodeRef> deletedChildren = treeMgr.saveAllChildren(curNode);

									try (XMLFeedOutputStream deletedNodesXmlOS = getXmlOutputStream(sharedObjects)) {

										for (final NodeRef nr : deletedChildren) {
											final List<NodeRef> oc = treeMgr.removeNodeAndChildren(nr);
											if (ite) LOG.trace(String.format("Found %d old children that no longer exist - deleting from index", oc.size()));
											Statistics.INSTANCE.documentsAddedToFeedForDeletion.addAndGet(oc.size());
											for (final NodeRef onr : oc)push.addToDelete(onr.id);
											/*	try {
													deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, onr.id));

												} catch (final SendFeed e) {
													handleSendFeed(e);
												}*/
										}

									/*	if (deletedNodesXmlOS.containsAnyRecord()) {
											final SendFeed sf = deletedNodesXmlOS.closeAndPrepareShipment();
											handleSendFeed(sf);
										}*/

										if(!push.deleteListIsEmpty()){
											push.deleteAll();
										}

										treeMgr.commit();
									}

								} else {
									treeMgr.saveNewChildren(curNode);
								}

							} else if (isRecovering) {
								treeMgr.reloadChildren(curNode);
								if (ite) LOG.trace(String.format("Node exploration was complete for node %s - reloaded %d children", curNode.toString(), curNode.children.size()));
							}

							for (final Node n : curNode.children) if (n instanceof ContainerNode) containers.add((ContainerNode) n);

							final List<DocumentNode> childDocs = new ArrayList<>();

							try (XMLFeedOutputStream deletedNodesXmlOS = getXmlOutputStream(sharedObjects)) {

								for (final Node child : curNode.children) {
									if (child instanceof DocumentNode) {

										//if (sharedObjects.connectorIndexesACLs) processNodePermissions(nodesAclsXOS, child, aclHashMgr);//TODO ACLs list

										if (isRecovering) {
											if (!(child.state == State.COMPLETE || child.state == State.EXCLUDED)) {
												/*if (isPublicMode) childDocs.add(new DocumentNode(child.id, child.parent));
												else childDocs.add((DocumentNode) child);*/
												//Don't know why we used to create a copy of the docnode in publicmode
												childDocs.add((DocumentNode) child);
											} else if (child.state == State.EXCLUDED) {
												try {
													Statistics.INSTANCE.documentsAddedToFeedForDeletion.incrementAndGet();
													deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, child.id));
												} catch (final SendFeed e) {
													handleSendFeed(e);
												}
											}
										} else if (child.state == State.EXCLUDED) {
											push.addToDelete(child.id);
											/*try {
												Statistics.INSTANCE.documentsAddedToFeedForDeletion.incrementAndGet();
												deletedNodesXmlOS.addDeleteRecord(URLManager.getSystemURL(sharedObjects.pushConf.datasource, child.id));
											} catch (final SendFeed e) {
												handleSendFeed(e);
											}*/
										} else {
											/*if (isPublicMode) childDocs.add(new DocumentNode(child.id, child.parent));
											else childDocs.add((DocumentNode) child);*/
											//Don't know why we used to create a copy of the docnode in publicmode
											childDocs.add((DocumentNode) child);
										}
									}
								}

								//if (deletedNodesXmlOS.containsAnyRecord()) handleSendFeed(deletedNodesXmlOS.closeAndPrepareShipment());
								if(!push.deleteListIsEmpty()){
									push.deleteAll();
								}

							}

							if (ite) LOG.trace(String.format("%d/%d children of %s needed indexing", childDocs.size(), curNode.children.size(), curNode.toString()));
							if (!childDocs.isEmpty()) {
								/*if (isPublicMode) indexingThreadMgr.startThread(new IndexingTask(sharedObjects, explorer, new ContainerNode(curNode.id, curNode.parent), childDocs, isUpdateDeleteMode));
								else indexingThreadMgr.startThread(new IndexingTask(sharedObjects, explorer, curNode, childDocs, isUpdateDeleteMode));*/
								//Don't know why we used to create a copy of the docnode in publicmode
								indexingThreadMgr.startThread(new IndexingTask(sharedObjects, push, explorer, curNode, childDocs, isUpdateDeleteMode));
							}
						}

						//if (nodesAclsXOS.containsAnyRecord()) handleSendFeed(nodesAclsXOS.closeAndPrepareShipment(), aclHashMgr);//TODO call ACLs update

					}

				} finally {
					try {
						waitForIndexingThreads();
					} catch (Throwable t) {
						thrownInFinallyBlock = t;
					}
				}

			}

		} catch (Throwable t) {
			if (thrownInFinallyBlock != null) LOG.error("Finally block also threw an exception: ", thrownInFinallyBlock);
			throw t;
		}

		if (thrownInFinallyBlock != null) throw thrownInFinallyBlock;

		if (hasReceivedStopRequest) throw new StopRequestedExternally();
		else if (hasReachedEndOfSchedule) throw new ReachedIndexingEndTime();

		if (deletionMode) {
			try (PushTableManager ptm = new PushTableManager(sharedObjects.pushConf)) {
				ptm.setLastDeletionPushDate(new Date(System.currentTimeMillis()));
			}
		}

	}

	public static void ensureNoDuplicates(List<? extends Node> nodeList) {
		List<Integer> duplicates = new ArrayList<>();
		List<NodeRef> allNodes = new ArrayList<>();
		int c = nodeList.size();
		Node n;
		NodeRef nr;
		for (int i=0; i<c; i++) {
			n = nodeList.get(i);
			nr = new NodeRef(n instanceof ContainerNode, n.id);
			if (allNodes.contains(nr)) duplicates.add(i);
			else allNodes.add(nr);
		}
		Collections.reverse(duplicates);
		for (Integer i: duplicates) nodeList.remove(i.intValue());
	}

	private void processNodePermissions(final XMLFeedOutputStream nodesAclsXOS, final Node node, final AclHashStore aclHashMgr) throws Throwable {
		if (node.serPerm == null) {
			aclHashMgr.setNodeLastIndexedAclHash(node, null);
		} else {

			byte[] aclHash = md5HashCalculator.digest(node.serPerm);
			byte[] lastIndexedAclHash = aclHashMgr.getNodeLastIndexedAclHash(node);
			if (Arrays.equals(aclHash, lastIndexedAclHash)) {
				//skip sending feed
				Statistics.INSTANCE.aclSkippedAsUnmodified.incrementAndGet();
			} else {

				final String sysUrl = URLManager.getSystemURL(sharedObjects.pushConf.datasource, node.id);
				final String ns = sharedObjects.pushConf.aclNamespace;
				final String sug = sharedObjects.pushConf.aclSuperUserGroup;
				final boolean uag = sharedObjects.pushConf.aclUsersAsGroups;

				Permission p = Permission.deserialize(node.serPerm);
				Boolean skipACL = false;
				if (p instanceof AndACL) {
					AndACL a= (AndACL) p;
					List<List<Principal>> principals = a.principalsList;
					if(principals!=null){
						for(List<Principal> princList:principals)
						{
							for(Principal princ:princList)
							{
								for (String pg:publicGroups){
									if(princ.principal!=null && princ.principal.equals(pg)){
										LOG.debug("The following node [" + node + "] belongs to a public group ["+pg+"] - ACLs will be skipped");
										skipACL=true;
										aclHashMgr.setNodeLastIndexedAclHash(node, null);
										break;
									}
								}
							}
						}
					}
					if(!skipACL){
						final ACL[] aa = AndACL.toAclArray((AndACL) p, sysUrl, ns, sug, uag, CaseSensitivityType.EVERYTHING_CASE_SENSITIVE);
						int l = aa.length;
						for (int i=0; i<l; i++) {
							Statistics.INSTANCE.aclAddedToFeedForIndexing.incrementAndGet();
							try { nodesAclsXOS.addGroupACL(aa[i]); } catch (final SendFeed e) { handleSendFeed(e, aclHashMgr); }
						}
						//1st ACL in the array is a document ACL - need to transform it to group ACL first
						try { nodesAclsXOS.addGroupACL(ACL.toGroupACL(aa[0], sysUrl)); } catch (final SendFeed e) { handleSendFeed(e, aclHashMgr); }
						aclHashMgr.setNodeLastIndexedAclHash(node, aclHash);//Have to add ACL hash afterwards to avoid committing when only part of the ACL has actually been sent
					}
				} else if (p instanceof ACL) {
					try {
						final ACL groupACL = ACL.toGroupACL((ACL) p, sysUrl);
						List<Principal> principals = groupACL.principals;
						if(principals!=null){
							for(Principal princ:principals)//check whether node should be public according to ACl content
							{
								for (String pg:publicGroups){
									if(princ.principal!=null && princ.principal.equals(pg)){
										LOG.debug("The following node [" + node + "] belongs to a public group ["+pg+"] - ACLs will be skipped");
										skipACL=true;
										aclHashMgr.setNodeLastIndexedAclHash(node, null);
										break;
									}
								}
							}
						}
						if(!skipACL){
							if (uag) ACL.usersAsGroups(groupACL);
							if (!StringUtils.isNullOrEmpty(sug)) groupACL.principals.add(new Group(sug, Access.PERMIT, ns, CaseSensitivityType.EVERYTHING_CASE_SENSITIVE));
							aclHashMgr.setNodeLastIndexedAclHash(node, aclHash);
							Statistics.INSTANCE.aclAddedToFeedForIndexing.incrementAndGet();
							nodesAclsXOS.addGroupACL(groupACL);
						}
					} catch (final SendFeed sf) {
						handleSendFeed(sf, aclHashMgr);
					}
				}
			}
		}
	}

	private void waitForIndexingThreads() throws InterruptedException, StopRequestedExternally, ReachedIndexingEndTime {

		StopRequestedExternally sre = null;
		ReachedIndexingEndTime riet = null;
		final List<Throwable> actualErrors = new ArrayList<>();
		{
			final List<Throwable> errors = indexingThreadMgr.awaitTermination();
			for (Throwable t : errors) {

				if (t != null) {

					if (t instanceof ExceptionWrapper) {
						do {
							t = ((ExceptionWrapper) t).getCause();
						} while ((t!=null) && (t instanceof ExceptionWrapper));
					}

					if (!((t instanceof StopRequestedExternally) || (t instanceof ReachedIndexingEndTime))) actualErrors.add(t);
					else if (t instanceof StopRequestedExternally) sre = (StopRequestedExternally) t;
					else if (t instanceof ReachedIndexingEndTime) riet = (ReachedIndexingEndTime) t;

				}
			}
		}

		if (!actualErrors.isEmpty()) {
			Statistics.INSTANCE.indexingProcessAborted.addAndGet(actualErrors.size());
			final StringBuilder sb = new StringBuilder("Exploration aborted because of the following error(s):");
			for (final Throwable t : actualErrors)
				sb.append(EnvUtils.CR).append("\t- ").append(ExceptionDigester.toString(t));
			LOG.error(sb);
			throw new InterruptedException("At least one indexing task failed.");
		} else if (sre != null) throw sre;
		else if (riet != null) throw riet;
	}

	public void handleSendFeed(final SendFeed sf) throws Throwable {
		handleSendFeed(sf, null);
	}

	public void handleSendFeed(final SendFeed sf, final AclHashStore aclHashMgr) throws Throwable {//TODO use ItemPush
		if(sharedObjects.pushConf.ftpMode)sharedObjects.feedPoster.upload(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, sf.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, sharedObjects.pushConf.ftpUsername, sharedObjects.pushConf.ftpPassword);
		sharedObjects.feedPoster.post(sharedObjects.pushConf.gsaHostName, sharedObjects.pushConf.datasource, FeedType.INCREMENTAL, sf.feedFile, sharedObjects.pushConf.feedsConservationPeriod < 1, true);
		Statistics.INSTANCE.updateBytesActuallySentToGsa(sf.feedFile.length());
		if (aclHashMgr != null) aclHashMgr.commit();
	}


	public static XMLFeedOutputStream getXmlOutputStream(final PushProcessSharedObjectsStore sharedObjects) throws IOException {//TODO remove & replace with list
		return getXmlOutputStream(sharedObjects, sharedObjects.pushConf.maxFeedSize);
	}

	public static XMLFeedOutputStream getXmlOutputStream(final PushProcessSharedObjectsStore sharedObjects, long maxFeedSize) throws IOException {
		return new XMLFeedOutputStream(
			sharedObjects.pushConf.gsaHostName, 
			sharedObjects.pushConf.datasource, 
			FeedType.INCREMENTAL, 
			sharedObjects.pushConf.feedsFolder,
			StandardCharsets.UTF_8, 
			maxFeedSize
			);
	}

	public static JsonFeedOutputStream getJsonOutputStream(final PushProcessSharedObjectsStore sharedObjects) throws IOException {
		return getJsonOutputStream(sharedObjects, sharedObjects.pushConf.maxFeedSize);
	}

	public static JsonFeedOutputStream getJsonOutputStream(final PushProcessSharedObjectsStore sharedObjects, long maxFeedSize) throws IOException {
		return new JsonFeedOutputStream(
				sharedObjects.pushConf.gsaHostName,
				sharedObjects.pushConf.datasource,
				FeedType.INCREMENTAL,
				sharedObjects.pushConf.feedsFolder,
				StandardCharsets.UTF_8,
				maxFeedSize
		);
	}
}
