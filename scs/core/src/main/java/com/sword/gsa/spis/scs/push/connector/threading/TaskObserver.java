package com.sword.gsa.spis.scs.push.connector.threading;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This class awaits for task completion and notifies the {@link ThreadPoolManager}.<br/>
 * Completion notification and error handling cannot be made by the task itself since it could throw an uncatchable {@link Throwable} such as an {@link OutOfMemoryError} or any {@link Error}.
 */
public final class TaskObserver implements Runnable {

	private final BlockingQueue<String> queue;
	private final List<Throwable> errors;
	private final Future<Object> task;
	private final String taskDesc;

	TaskObserver(final BlockingQueue<String> queue, final List<Throwable> errors, final Future<Object> task, final String taskDesc) {
		super();
		this.queue = queue;
		this.errors = errors;
		this.task = task;
		this.taskDesc = taskDesc;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("TaskObserver[" + taskDesc + "]");
		try {
			task.get();
		} catch (final ExecutionException t) {
			synchronized (errors) { errors.add(t.getCause()); }
		} catch (final Throwable t) {
			synchronized (errors) { errors.add(t); }
		}
		try {
			queue.take();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
	}

}
