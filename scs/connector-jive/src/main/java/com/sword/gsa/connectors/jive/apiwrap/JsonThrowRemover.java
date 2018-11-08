package com.sword.gsa.connectors.jive.apiwrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import sword.common.utils.streams.StreamUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

public final class JsonThrowRemover implements Runnable {

	private final InputStream src;
	private final PipedInputStream is;
	private final PipedOutputStream os;

	public JsonThrowRemover(final InputStream src, final Charset cs) throws IOException {
		this.src = src;
		is = new PipedInputStream();
		os = new PipedOutputStream(is);

		final byte[] buf = new byte[64];
		int c = this.src.read(buf);

		String streamStart = new String(buf, 0, c);
		if (streamStart.startsWith("throw ")) {// JSON response starts with a throw clause - remove it

			int sc = streamStart.indexOf(';');
			while (sc < 0) {
				c = this.src.read(buf);
				if (c < 0) throw new IllegalArgumentException("Stream does not contain a JSON object");
				else {
					streamStart = new String(buf, 0, c);
					sc = streamStart.indexOf(';');
				}
			}

			os.write(streamStart.substring(sc + 1).getBytes(cs));

		} else os.write(buf, 0, c);

		new Thread(this).start();

	}

	public JsonParser getParser() throws JsonParseException, IOException {
		return new JsonFactory().createParser(is);
	}

	@Override
	public void run() {

		try {
			StreamUtils.transferBytes(src, os);
		} catch (final IOException e1) {
			try {
				os.write(("Read error: " + e1.toString()).getBytes(StandardCharsets.UTF_8));
			} catch (final IOException e2) {
				e2.printStackTrace();
			}
		}

	}

}
