package com.sword.gsa.spis.scs.push.connector.threading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ThreadPoolManager {

	private final List<Throwable> errors = new ArrayList<>();
	private final BlockingQueue<String> queue;
	private final ExecutorService pool;

	public ThreadPoolManager(final BlockingQueue<String> queue) {
		this.queue = queue;
		pool = Executors.newCachedThreadPool();
	}

	public void startThread(final AConnectorTask ct) throws InterruptedException {
		queue.put("");// Thread will not be started until some room becomes available in the queue
		final Future<Object> task = pool.submit(ct);
		pool.submit(new TaskObserver(queue, errors, task, ct.description));
	}

	public List<Throwable> awaitTermination() throws InterruptedException {
		pool.shutdown();
		boolean allTasksCompleted = false;
		while (!allTasksCompleted)
			allTasksCompleted = pool.awaitTermination(1, TimeUnit.HOURS);// Wait for tasks to complete for as long as necessary
		return errors;
	}
}