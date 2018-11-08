package com.sword.gsa.spis.scs.saml.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * Starts Authorization Threads and waits until they complete or timeout.
 */
public final class AuthzProgressObserver {

	private static final Logger LOG = Logger.getLogger(GAuthZService.class);

	private final ExecutorService threadPool = Executors.newCachedThreadPool();
	private final Collection<Future<?>> startedAuthZProcesses = new ArrayList<>();
	private final long timeout;

	public AuthzProgressObserver(final long timeout) {
		this.timeout = timeout;
	}

	public void authorize(final Collection<RepositoryDecisions> repositories) throws InterruptedException {
		startedAuthZProcesses.clear();
		startThreads(repositories);
		final boolean finishedInTime = waitCompletion();
		if (!finishedInTime) killRunningThreads();
	}

	private void startThreads(final Collection<RepositoryDecisions> repositories) {
		LOG.info("Starting auhtorization threads (timeout: " + timeout + " mS).");
		for (final RepositoryDecisions rd : repositories) {
			final RepositoryProcessor authzProcess = new RepositoryProcessor(rd);
			if (authzProcess.hasWorkToDo()) {
				LOG.info("Starting auhtorization thread for Repository: " + rd.authorizerConf.authorizer.uniqueId);
				startedAuthZProcesses.add(threadPool.submit(authzProcess));
			}
		}
		threadPool.shutdown();
		LOG.debug("Auhtorization threads started");
	}

	private boolean waitCompletion() throws InterruptedException {
		return threadPool.awaitTermination(timeout, TimeUnit.MILLISECONDS);
	}

	private void killRunningThreads() {
		LOG.debug("Killing unfinished Threads");
		for (final Future<?> task : startedAuthZProcesses)
			if (!task.isDone()) {
				LOG.debug("Unfinished thread ; killing");
				task.cancel(true);
			}
	}
}
