package com.sword.gsa.spis.scs.push.monitoring;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sword.common.utils.StringUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;

import com.sword.gsa.spis.scs.push.PushInitializer;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.databases.DBBrowser;

public class IncomingRequestProcessor implements Runnable {

	// private static final MessageFormat HTML = new MessageFormat(
	// "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"></head><body onload=\"window.location=''http://localhost:{0}/Monitor.html'';\" ><noscript><a href=\"http://localhost:{0}/Monitor.html\">Javascript is not enabled; please click this link.</a></noscript></body></html>");

	private static final String NO_CONTENT_RESP = "HTTP/1.0 %d %s\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 0\r\nconnection: close\r\n\r\n";
	private static final byte[] BAD_REQUEST = String.format(NO_CONTENT_RESP, 400, "Bad Request").getBytes(StandardCharsets.UTF_8);
	private static final byte[] NOT_FOUND = String.format(NO_CONTENT_RESP, 404, "Not Found").getBytes(StandardCharsets.UTF_8);
	// private static final byte[] NOT_MODIFIED = String.format(NO_CONTENT_RESP, 304, "Not Modified").getBytes(StandardCharsets.UTF_8);
	public static final ThreadSafeDateFormat DATE_HEADERS_FORMAT = new ThreadSafeDateFormat(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"));
	// private static final long THIRTY_DAYS = TimeUnit.DAYS.toMillis(30);

	private static final Pattern NID_EXTRACT = Pattern.compile("&nid=([^&]+)");
	private static final Pattern PID_EXTRACT = Pattern.compile("&parent=([^&]+)");
	private static final Pattern PAGE_SIZE_EXTRACT = Pattern.compile("&num=([0-9]+)");
	private static final Pattern PAGE_EXTRACT = Pattern.compile("&page=([0-9]+)");
	private static final Pattern STATE_EXTRACT = Pattern.compile("&states=((?:[0-9]+(?:%2C)?)+)");
	private static final Pattern IS_DIR_EXTRACT = Pattern.compile("&isdir=([01])");
	private static final Pattern EXCL_EXTRACT = Pattern.compile("&exclude=([01])");

	private final File historyFile;
	private final Socket s;
	private final PushConfig conf;
	private final DBBrowser dbBrowser;

	public IncomingRequestProcessor(final File historyFile, final Socket s, final PushConfig conf, DBBrowser dbBrowser) {
		this.historyFile = historyFile;
		this.s = s;
		this.conf = conf;
		this.dbBrowser = dbBrowser;
	}

	@Override
	public void run() {
		try {

			final int headerMaxSize = 32_000;
			final byte[] buf = new byte[headerMaxSize];
			int r = -1;
			try (final InputStream sis = s.getInputStream(); final OutputStream sos = s.getOutputStream()) {
				r = sis.read(buf);
				if (r > 0) {

					final String utfReq = new String(buf, 0, r, StandardCharsets.UTF_8);

					final Matcher m = Pattern.compile("([^ \t]+)[ \t]+([^ \t]+)[ \t]+([^ \t]+)").matcher(utfReq);
					if (m.find()) {
						final String method = m.group(1);
						final String uri = m.group(2);
						final String protocol = m.group(3);

						if (StringUtils.isNullOrEmpty(method) || StringUtils.isNullOrEmpty(uri) || StringUtils.isNullOrEmpty(protocol) || !method.equalsIgnoreCase("get") || !protocol.toLowerCase().startsWith("http")) sos.write(BAD_REQUEST);
						else if (method.equalsIgnoreCase("get")) {

							if ("/Stats.json".equals(uri)) Statistics.outputStatsAndThreadsInfo(sos, conf.pushType);
							else if (uri.equals("/History.json")) {
								List<String> history = History.reloadHistory(historyFile);
								History.outputHistory(history, sos);
							} else if (uri.equals("/kill.action")) {
								Monitor.LOG.warn("Received termination request");
								if (s.getInetAddress().isLoopbackAddress()) PushInitializer.exitWithCode(PushInitializer.RC_RECEIVED_STOP_REQUEST);
								else Monitor.LOG.error("Kill called remotely!!!");
							} else if (uri.equals("/ping.action")) {
								Monitor.LOG.debug("Received ping request");
								sos.write(this.conf.connectorId.getBytes(StandardCharsets.UTF_8));
							} else if (uri.equals("/dbbrowsingmode")) {
								sos.write(Boolean.toString(conf.pushType == PushType.BROWSE_DB).getBytes(StandardCharsets.UTF_8));
								sos.flush();
							} else if (uri.startsWith("/internaldb")) {
								if (uri.contains("action=stop")) {
									synchronized (dbBrowser.dbBrowsingEndNotifier) { dbBrowser.dbBrowsingEndNotifier.notifyAll(); }
								} else if (uri.contains("action=commit")) {
									dbBrowser.commit();
								} else if (uri.contains("action=listchildren")) {
									String pid = null;
									int num = 25;
									int page = 0;
									String state = null;
									Matcher m2 = PID_EXTRACT.matcher(uri);
									if (m2.find()) pid = URLDecoder.decode(m2.group(1), "UTF-8");
									m2 = PAGE_SIZE_EXTRACT.matcher(uri);
									if (m2.find()) num = Integer.parseInt(m2.group(1));
									m2 = PAGE_EXTRACT.matcher(uri);
									if (m2.find()) page = Integer.parseInt(m2.group(1));
									m2 = STATE_EXTRACT.matcher(uri);
									if (m2.find()) state = URLDecoder.decode(m2.group(1), "UTF-8");
									dbBrowser.listChildren(sos, pid, num, page, state, true);
								} else if (uri.contains("action=reloadstate")) {
									String nodeId = null;
									boolean isDir = false;
									boolean exclude = false;
									Matcher m2 = NID_EXTRACT.matcher(uri);
									if (m2.find()) nodeId = URLDecoder.decode(m2.group(1), "UTF-8");
									m2 = IS_DIR_EXTRACT.matcher(uri);
									if (m2.find()) isDir = "1".equals(m2.group(1));
									m2 = EXCL_EXTRACT.matcher(uri);
									if (m2.find()) exclude = "1".equals(m2.group(1));
									dbBrowser.reloadState(sos, nodeId, isDir, exclude);
								} else if (uri.contains("action=find")) {
									String pid = null;
									String nid = null;
									Matcher m2 = PID_EXTRACT.matcher(uri);
									if (m2.find()) pid = URLDecoder.decode(m2.group(1), "UTF-8");
									m2 = NID_EXTRACT.matcher(uri);
									if (m2.find()) nid = URLDecoder.decode(m2.group(1), "UTF-8");
									dbBrowser.findChild(sos, pid, nid);
								} else {
									sos.write(BAD_REQUEST);
								}
							} else sos.write(NOT_FOUND);

						} else sos.write(BAD_REQUEST);
					} else sos.write(BAD_REQUEST);

				} else sos.write(BAD_REQUEST);
			}
		} catch (final Exception e) {
			Monitor.LOG.error("Error occurred outputting connector activity", e);
		}
	}

	// private void processImageRequest(final OutputStream sos, String utfReq, String imgName) throws IOException {
	// if (utfReq.contains("\nIf-Modified-Since: ")) sos.write(NOT_MODIFIED);
	// else try (InputStream iis = MonitorThread.class.getResourceAsStream("/images/" + imgName)) {
	// sos.write("HTTP/1.0 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
	// sos.write(("Last-Modified: " + DATE_HEADERS_FORMAT.format(creationDate) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Expires: " + DATE_HEADERS_FORMAT.format(new Date(creationDate.getTime() + THIRTY_DAYS)) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Date: " + DATE_HEADERS_FORMAT.format(new Date()) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Cache-Control: max-age=" + THIRTY_DAYS / 1000 + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write("Content-Type: image/png; charset=utf-8\r\n".getBytes(StandardCharsets.UTF_8));
	// sos.write("connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
	// StreamUtils.transferBytes(iis, sos, false);
	// }
	// }
	//
	// private void processScriptRequest(final OutputStream sos, String utfReq, String scriptName) throws IOException {
	// if (utfReq.contains("\nIf-Modified-Since: ")) sos.write(NOT_MODIFIED);
	// else try (InputStream iis = MonitorThread.class.getResourceAsStream("/scripts/" + scriptName)) {
	// sos.write("HTTP/1.0 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
	// sos.write(("Last-Modified: " + DATE_HEADERS_FORMAT.format(creationDate) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Expires: " + DATE_HEADERS_FORMAT.format(new Date(creationDate.getTime() + THIRTY_DAYS)) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Date: " + DATE_HEADERS_FORMAT.format(new Date()) + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write(("Cache-Control: max-age=" + THIRTY_DAYS / 1000 + "\r\n").getBytes(StandardCharsets.UTF_8));
	// sos.write("Content-Type: text/javascript; charset=utf-8\r\n".getBytes(StandardCharsets.UTF_8));
	// sos.write("connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
	// StreamUtils.transferBytes(iis, sos, false);
	// }
	// }

}