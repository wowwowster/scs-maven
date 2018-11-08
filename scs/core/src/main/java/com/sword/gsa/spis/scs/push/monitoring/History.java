package com.sword.gsa.spis.scs.push.monitoring;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class History {

	private static final byte[] OPENING_ARRAY = new String("{ \"history\" : [").getBytes(StandardCharsets.UTF_8);
	private static final byte[] CLOSING_ARRAY = new String("]}").getBytes(StandardCharsets.UTF_8);
	private static final byte[] COMA_SEPARATOR = new String(", ").getBytes(StandardCharsets.UTF_8);
	
	public static List<String> reloadHistory(final File historyFile) {
		List<String> history;
		synchronized (historyFile) {
			if (historyFile.exists()) {
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(historyFile))) {
					history = unsafeCast(ois.readObject());
				} catch (final Exception e) {
					history = new ArrayList<>();
				}
			} else history = new ArrayList<>();
		}
		return history;
	}
	
	public static void outputHistory(final List<String> history, final OutputStream os) throws IOException {
		os.write(OPENING_ARRAY);
		final int hs = history.size();
		final int hsM1 = hs - 1;
		for (int i = 0; i < hs; i++) {
			os.write(history.get(i).getBytes(StandardCharsets.UTF_8));
			if (i < hsM1) os.write(COMA_SEPARATOR);
		}
		os.write(CLOSING_ARRAY);
	}
	
	@SuppressWarnings("unchecked")
	private static List<String> unsafeCast(Object o) {
		return (List<String>) o;
	}

}
