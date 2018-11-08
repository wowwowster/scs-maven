package com.sword.gsa.spis.scs.push.connector.threading;

import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.push.throwables.ExceptionWrapper;

public abstract class AConnectorTask implements Callable<Object> {

	private static final Logger LOG = Logger.getLogger(AConnectorTask.class);

	final String description;

	public AConnectorTask(final String description) {
		this.description = description;
	}

	@Override
	public final Object call() throws Exception {
		Thread.currentThread().setName("[" + description + "]");
		LOG.info("Starting ConnectorTask: " + description);
		try {
			doTask();
		} catch (final Throwable t) {
			System.gc();
			if (t instanceof Exception) throw (Exception) t;
			else throw new ExceptionWrapper(t);
		}
		LOG.info("ConnectorTask completed: " + description);
		return null;
	}

	abstract void doTask() throws Throwable;

}