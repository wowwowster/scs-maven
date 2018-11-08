package com.sword.gsa.spis.scs.push.monitoring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class SaveLastStats implements Runnable {

	private final File lastStatBackupFile;

	public SaveLastStats(final File lastStatBackupFile) {
		super();
		this.lastStatBackupFile = lastStatBackupFile;
	}

	@Override
	public void run() {
		synchronized (lastStatBackupFile) {
			try {
				final SerStats ss = new SerStats(Statistics.INSTANCE);
				try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(lastStatBackupFile))) {
					oos.writeObject(ss);
				}
			} catch (final Exception e) {
				Monitor.LOG.error("Error occurred saving last stats", e);
				try {
					lastStatBackupFile.delete();
				} catch (final Exception ignored) {}
			}
		}
	}

}
