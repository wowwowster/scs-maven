package com.sword.gsa.spis.scs.push.monitoring;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SaveHistory implements Runnable {

	private final File historyFile;

	public SaveHistory(final File historyFile) {
		super();
		this.historyFile = historyFile;
	}

	@Override
	public void run() {
		synchronized (historyFile) {
			try {

				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Statistics.outputStats(baos, true);
				final String latestHistoryEntry = new String(baos.toByteArray(), StandardCharsets.UTF_8);

				List<String> history;
				if (historyFile.exists()) {
					try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(historyFile))) {
						history = usafeCast(ois);
					} catch (final Exception e) {
						history = new ArrayList<>();
					}
				} else history = new ArrayList<>();
				history.add(latestHistoryEntry);
				while (history.size() > 5000) history.remove(0);
				try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(historyFile))) {
					oos.writeObject(history);
				}
			} catch (final Exception e) {
				Monitor.LOG.error("Error occurred saving History", e);
				try {
					historyFile.delete();
				} catch (final Exception ignored) {}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> usafeCast(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		List<String> history = (List<String>) ois.readObject();
		return history;
	}

}
