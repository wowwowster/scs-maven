package com.sword.gsa.spis.scs.push.monitoring;

import java.io.File;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.databases.DBBrowser;

public class IncomingRequestConsumer implements Runnable {

	private final ExecutorService execSvc;
	private final AtomicBoolean closed;
	private final BlockingQueue<Socket> socketQueue;
	private final File historyFile;
	private final PushConfig conf;
	private final DBBrowser dbBrowser;

	public IncomingRequestConsumer(final ExecutorService execSvc, final AtomicBoolean closed, final BlockingQueue<Socket> socketQueue, final File historyFile, final PushConfig conf, DBBrowser dbBrowser) {
		super();
		this.execSvc = execSvc;
		this.closed = closed;
		this.socketQueue = socketQueue;
		this.historyFile = historyFile;
		this.conf = conf;
		this.dbBrowser = dbBrowser;
	}

	@Override
	public void run() {
		while (!closed.get())
			try {
				@SuppressWarnings("resource")
				final Socket s = socketQueue.take();
				if (!(s instanceof FakeSocket)) execSvc.execute(new IncomingRequestProcessor(historyFile, s, conf, dbBrowser));
			} catch (final Exception e) {
				if (!closed.get()) Monitor.LOG.error("Error occurred waiting for a connection", e);
			}
	}

}