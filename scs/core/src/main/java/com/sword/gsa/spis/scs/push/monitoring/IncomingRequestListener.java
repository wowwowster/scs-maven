package com.sword.gsa.spis.scs.push.monitoring;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class IncomingRequestListener implements Runnable {

	private final ServerSocket listener;
	private final AtomicBoolean closed;
	private final BlockingQueue<Socket> socketQueue;

	public IncomingRequestListener(final ServerSocket listener, final AtomicBoolean closed, final BlockingQueue<Socket> socketQueue) {
		super();
		this.listener = listener;
		this.closed = closed;
		this.socketQueue = socketQueue;
	}

	@Override
	public void run() {
		if (listener != null) while (!closed.get())
			try {
				socketQueue.put(listener.accept());
			} catch (final Exception e) {
				if (!closed.get()) Monitor.LOG.error("Error occurred waiting for a connection", e);
			}
	}
}