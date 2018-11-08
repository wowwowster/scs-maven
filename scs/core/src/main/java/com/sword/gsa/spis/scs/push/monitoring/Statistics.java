package com.sword.gsa.spis.scs.push.monitoring;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import sword.common.utils.numbers.DecimalUnitsConverter;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.sword.gsa.spis.scs.push.config.PushType;

public class Statistics {
	
	private static final String STATS_MESSAGE_FORMAT = new StringBuilder("\n\t- Start time: %d")
														.append("\n\t- Mode: %s")
														.append("\n\t- Audit events enabled: %b")
														.append("\n\t- Audit events processed: %d")
														.append("\n\t- Nodes explored: %d")
														.append("\n\t- Documents found: %d")
														.append("\n\t- Nodes excluded: %d")
														.append("\n\t- ACL added to feed for indexing: %d")
														.append("\n\t- ACL skipped as unmodified: %d")
														.append("\n\t- Documents added to feed for indexing: %d")
														.append("\n\t- Documents excluded: %d")
														.append("\n\t- Documents added to feed for deletion: %d")
														.append("\n\t- Documents skipped as unmodified: %d")
														.append("\n\t- Exploration errors: %d")
														.append("\n\t- Indexing errors: %d")
														.append("\n\t- Indexing process aborted: %d")
														.append("\n\t- Content bytes added to feed: %f")
														.append("\n\t- Bytes actually sent to GSA: %f")
														.append("\n\t- End time: %d")
														.toString();

	public static final Statistics INSTANCE = new Statistics();

	public final AtomicLong startTime = new AtomicLong(0L);
	public final AtomicLong endTime = new AtomicLong(-1L);

	public final AtomicBoolean isInitialIndexing = new AtomicBoolean(true);
	public final AtomicBoolean isRecovering = new AtomicBoolean(false);

	public AtomicBoolean isEventMode = new AtomicBoolean(false);

	public final AtomicInteger auditEventsProcessed = new AtomicInteger(0);
	public final AtomicInteger nodesExplored = new AtomicInteger(0);
	public final AtomicInteger documentsFound = new AtomicInteger(0);
	public final AtomicInteger nodesExcluded = new AtomicInteger(0);
	public final AtomicInteger aclAddedToFeedForIndexing = new AtomicInteger(0);
	public final AtomicInteger aclSkippedAsUnmodified = new AtomicInteger(0);
	public final AtomicInteger documentsAddedToFeedForIndexing = new AtomicInteger(0);
	public final AtomicInteger documentsExcluded = new AtomicInteger(0);
	public final AtomicInteger documentsAddedToFeedForDeletion = new AtomicInteger(0);
	public final AtomicInteger documentsSkippedAsUnmodified = new AtomicInteger(0);
	public final AtomicInteger explorationErrors = new AtomicInteger(0);
	public final AtomicInteger indexingErrors = new AtomicInteger(0);
	public final AtomicInteger indexingProcessAborted = new AtomicInteger(0);

	private double contentBytesAddedToFeed = 0D;
	private double bytesActuallySentToGsa = 0D;

	public Statistics() {
		super();
		startTime.set(System.currentTimeMillis());
	}

	public Statistics(final SerStats serStats) {
		super();
		startTime.set(serStats.startTime);
		endTime.set(serStats.endTime);
		isInitialIndexing.set(serStats.isInitialIndexing);
		isRecovering.set(serStats.isRecovering);
		isEventMode.set(serStats.isEventMode);
		auditEventsProcessed.set(serStats.auditEventsProcessed);
		auditEventsProcessed.set(serStats.auditEventsProcessed);
		nodesExplored.set(serStats.nodesExplored);
		documentsFound.set(serStats.documentsFound);
		nodesExcluded.set(serStats.nodesExcluded);
		aclAddedToFeedForIndexing.set(serStats.aclAddedToFeedForIndexing);
		aclSkippedAsUnmodified.set(serStats.aclSkippedAsUnmodified);
		documentsAddedToFeedForIndexing.set(serStats.documentsAddedToFeedForIndexing);
		documentsExcluded.set(serStats.documentsExcluded);
		documentsAddedToFeedForDeletion.set(serStats.documentsAddedToFeedForDeletion);
		documentsSkippedAsUnmodified.set(serStats.documentsSkippedAsUnmodified);
		explorationErrors.set(serStats.explorationErrors);

		synchronized (this) {
			contentBytesAddedToFeed = serStats.contentBytesAddedToFeed;
			bytesActuallySentToGsa = serStats.bytesActuallySentToGsa;
		}
	}

	public synchronized double getContentBytesAddedToFeed() {
		return contentBytesAddedToFeed;
	}

	public synchronized void updateContentBytesAddedToFeed(final long db) {
		contentBytesAddedToFeed += db;
	}

	public synchronized double getBytesActuallySentToGsa() {
		return bytesActuallySentToGsa;
	}

	public synchronized void updateBytesActuallySentToGsa(final long db) {
		bytesActuallySentToGsa += db;
	}

	public static void outputStatsAndThreadsInfo(final OutputStream os, final PushType pt) throws JsonGenerationException, IOException {

		try (JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8).configure(Feature.AUTO_CLOSE_TARGET, false)) {

			jg.setPrettyPrinter(new DefaultPrettyPrinter());

			jg.writeStartObject();

			jg.writeStringField("PushType", pt.name());

			jg.writeArrayFieldStart("Threads");
			outputThreadStackTraces(jg);
			jg.writeEndArray();

			jg.writeObjectFieldStart("Statistics");
			INSTANCE.outputStats(jg, false);
			jg.writeEndObject();

			jg.writeEndObject();

			jg.flush();

		}
	}
	
	public static void outputThreadStackTraces(final JsonGenerator jg) throws JsonGenerationException, IOException {
		final Map<Thread, StackTraceElement[]> stMap = Thread.getAllStackTraces();
		final List<Thread> threads = new ArrayList<>();
		threads.addAll(stMap.keySet());
		Collections.sort(threads, new Comparator<Thread>() {
			@Override
			public int compare(Thread o1, Thread o2) { return (int) (o1.getId() - o2.getId()); }
		});

		for (final Thread t : threads) {

			jg.writeStartObject();

			jg.writeStringField("Name", t.getName());
			jg.writeNumberField("ID", t.getId());
			jg.writeArrayFieldStart("Stack");
			final StackTraceElement[] aste = stMap.get(t);
			if (aste != null) for (final StackTraceElement ste : aste) {
				jg.writeStartObject();
				jg.writeStringField("Class", ste.getClassName());
				jg.writeStringField("File", ste.getFileName());
				jg.writeStringField("Method", ste.getMethodName());
				jg.writeNumberField("ln", ste.getLineNumber());
				jg.writeEndObject();
			}
			jg.writeEndArray();

			jg.writeEndObject();
		}

	}

	public static void outputStats(final OutputStream os, final boolean minimal) throws JsonGenerationException, IOException {
		try (JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8).configure(Feature.AUTO_CLOSE_TARGET, false)) {
			jg.writeStartObject();
			INSTANCE.outputStats(jg, minimal);
			jg.writeEndObject();
			jg.flush();
		}
	}

	public void outputStats(final JsonGenerator jg, final boolean minimal) throws JsonGenerationException, IOException {
		
		jg.writeNumberField("Now", System.currentTimeMillis());
		jg.writeNumberField("StartTime", startTime.get());
		jg.writeNumberField("DocumentsFound", documentsFound.get());
		jg.writeNumberField("DocumentsAddedToFeedForIndexing", documentsAddedToFeedForIndexing.get());
		jg.writeNumberField("ContentBytesAddedToFeed", contentBytesAddedToFeed);
		jg.writeNumberField("BytesActuallySentToGsa", bytesActuallySentToGsa);
		jg.writeBooleanField("IsRecovering", isRecovering.get());

		final boolean outputAll = !minimal;
		
		if (outputAll) {
			jg.writeStringField("StartDate", IncomingRequestProcessor.DATE_HEADERS_FORMAT.format(new Date(startTime.get())));
			jg.writeStringField("Mode", (isInitialIndexing.get() ? "Initial" : "Incremental") + (isRecovering.get() ? " (recovery)" : ""));
			jg.writeBooleanField("AuditEvents", isEventMode.get());
			final long et = endTime.get();
			jg.writeNumberField("EndTime", et);
			if (et > 0) jg.writeStringField("EndDate", IncomingRequestProcessor.DATE_HEADERS_FORMAT.format(new Date(et)));
			jg.writeNumberField("AuditEventsProcessed", auditEventsProcessed.get());
			jg.writeNumberField("NodesExplored", nodesExplored.get());
			jg.writeNumberField("NodesExcluded", nodesExcluded.get());
			jg.writeNumberField("AclAddedToFeedForIndexing", aclAddedToFeedForIndexing.get());
			jg.writeNumberField("AclSkippedAsUnmodified", aclSkippedAsUnmodified.get());
			jg.writeNumberField("DocumentsExcluded", documentsExcluded.get());
			jg.writeNumberField("DocumentsAddedToFeedForDeletion", documentsAddedToFeedForDeletion.get());
			jg.writeNumberField("DocumentsSkippedAsUnmodified", documentsSkippedAsUnmodified.get());
			jg.writeNumberField("ExplorationErrors", explorationErrors.get());
			jg.writeNumberField("IndexingErrors", indexingErrors.get());
			jg.writeNumberField("IndexingProcessAborted", indexingProcessAborted.get());
			jg.writeStringField("ContentBytesAddedToFeedString", DecimalUnitsConverter.SIZE_UNIT.toReadableString(contentBytesAddedToFeed));
			jg.writeStringField("BytesActuallySentToGsaString", DecimalUnitsConverter.SIZE_UNIT.toReadableString(bytesActuallySentToGsa));
		}
	}

	@Override
	public String toString() {
		double contentBytesAddedToFeed = 0;
		double bytesActuallySentToGsa = 0;
		synchronized (this) {
			contentBytesAddedToFeed = this.contentBytesAddedToFeed;
			bytesActuallySentToGsa = this.bytesActuallySentToGsa;
		}
		return String.format(
			STATS_MESSAGE_FORMAT, 
			startTime.longValue(), 
			(isInitialIndexing.get() ? "Initial" : "Incremental") + (isRecovering.get() ? " (recovery)" : ""), 
			isEventMode.get(), 
			auditEventsProcessed.intValue(), 
			nodesExplored.intValue(),
			documentsFound.intValue(), 
			nodesExcluded.intValue(), 
			aclAddedToFeedForIndexing.intValue(), 
			aclSkippedAsUnmodified.intValue(), 
			documentsAddedToFeedForIndexing.intValue(), 
			documentsExcluded.intValue(), 
			documentsAddedToFeedForDeletion.intValue(),
			documentsSkippedAsUnmodified.intValue(), 
			explorationErrors.intValue(), 
			indexingErrors.intValue(), 
			indexingProcessAborted.intValue(), 
			contentBytesAddedToFeed, 
			bytesActuallySentToGsa, 
			endTime.get()
		);
	}

}
