package com.sword.gsa.spis.scs.push.monitoring;

import java.io.Serializable;

public class SerStats implements Serializable {

	private static final long serialVersionUID = 3L;

	public final long startTime;
	public final boolean isInitialIndexing;
	public final boolean isRecovering;
	public final boolean isEventMode;
	public final int auditEventsProcessed;
	public final int nodesExplored;
	public final int documentsFound;
	public final int nodesExcluded;
	public final int aclAddedToFeedForIndexing;
	public final int aclSkippedAsUnmodified;
	public final int documentsAddedToFeedForIndexing;
	public final int documentsExcluded;
	public final int documentsAddedToFeedForDeletion;
	public final int documentsSkippedAsUnmodified;
	public final int explorationErrors;
	public final int indexingErrors;
	public final int indexingProcessAborted;
	public final double contentBytesAddedToFeed;
	public final double bytesActuallySentToGsa;
	public final long endTime;

	public SerStats(final Statistics stats) {
		super();
		startTime = stats.startTime.get();
		isInitialIndexing = stats.isInitialIndexing.get();
		isRecovering = stats.isRecovering.get();
		isEventMode = stats.isEventMode.get();
		auditEventsProcessed = stats.auditEventsProcessed.get();
		nodesExplored = stats.nodesExplored.get();
		documentsFound = stats.documentsFound.get();
		nodesExcluded = stats.nodesExcluded.get();
		aclAddedToFeedForIndexing = stats.aclAddedToFeedForIndexing.get();
		aclSkippedAsUnmodified = stats.aclSkippedAsUnmodified.get();
		documentsAddedToFeedForIndexing = stats.documentsAddedToFeedForIndexing.get();
		documentsExcluded = stats.documentsExcluded.get();
		documentsAddedToFeedForDeletion = stats.documentsAddedToFeedForDeletion.get();
		documentsSkippedAsUnmodified = stats.documentsSkippedAsUnmodified.get();
		explorationErrors = stats.explorationErrors.get();
		indexingErrors = stats.indexingErrors.get();
		indexingProcessAborted = stats.indexingProcessAborted.get();
		contentBytesAddedToFeed = stats.getContentBytesAddedToFeed();
		bytesActuallySentToGsa = stats.getBytesActuallySentToGsa();
		endTime = stats.endTime.get();
	}

}
