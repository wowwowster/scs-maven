package com.sword.gsa.spis.scs.gsaadmin;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.InvalidNameException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.log4j.Logger;

import sword.common.utils.streams.StreamUtils;

public class GsaManager {

	private static final Logger LOG = Logger.getLogger(GsaManager.class);
	private static final Pattern OLD_STYLE_GSA_INFO_EXTRACTION = Pattern.compile("Software Version:.*?([0-9A-Z]+(?:\\.[0-9A-Z]+)*).+?System Version:.*?([0-9A-Z]+(?:\\.[0-9A-Z]+)*).+?Appliance ID:.*?([0-9A-Za-z\\-]+)", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern NEW_STYLE_GSA_INFO_EXTRACTION = Pattern.compile("startGsaAdmin\\(.+?\\{['\"]sysVersion['\"]:.*?['\"]([0-9A-Z]+(?:\\.[0-9A-Z]+)*)(?:\\\\n)?['\"],.*?['\"]applianceId['\"]:.*?['\"]([0-9A-Za-z\\-]+)['\"]", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern REDIR_TARGET = Pattern.compile("Refresh: 0;URL=([^\\r\\n]+).+Set-Cookie: (.+?); ", Pattern.DOTALL | Pattern.MULTILINE);

	private final ReloadableTrustManager tm;

	public GsaManager(final Path trustStore) throws NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, CertificateException, IOException {
		tm = new ReloadableTrustManager(trustStore);
	}

	public GSAInfo getGsaInfo(final boolean ssl, final String defaultGsaHost, final String adminGsaHost) throws KeyManagementException, NoSuchAlgorithmException, UnknownHostException, IOException {
		final int port = ssl ? 8443 : 8000;
		boolean redirect = true;
		String dest = "/EnterpriseController?actionType=about";
		String cookie = "";
		while (redirect) {
			try (Socket s = getSocket(ssl, adminGsaHost, port, tm); OutputStream socketOs = s.getOutputStream(); InputStream is = s.getInputStream()) {
				String req = String.format("GET %s HTTP/1.0\r\nHost: %s:%d\r\nUser-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64; rv:32.0) Gecko/20100101 Firefox/32.0\r\n"
						+ "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\nAccept-Language: en,fr;q=0.8,fr-fr;q=0.5,en-us;q=0.3\r\n" + 
						"Pragma: no-cache\r\nCache-Control: no-cache%s\r\n\r\n", dest, adminGsaHost, port, cookie);
				socketOs.write(req.getBytes(StandardCharsets.UTF_8));
				socketOs.flush();
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				StreamUtils.transferBytes(is, baos, false);
				final String resp = new String(baos.toByteArray());
				Matcher m = REDIR_TARGET.matcher(resp);
				if (m.find()) {
					dest = m.group(1);
					cookie = "\r\nCookie: " + m.group(2);
				} else {
					m = OLD_STYLE_GSA_INFO_EXTRACTION.matcher(resp);
					if (m.find()) {
						final String soft = m.group(1);
						final String sys = m.group(2);
						final String id = m.group(3);
						return new GSAInfo(ssl, defaultGsaHost, adminGsaHost, soft, sys, id, false);
					}
					else {
						m = NEW_STYLE_GSA_INFO_EXTRACTION.matcher(resp);
						if (m.find()) {
							final String soft = m.group(1);
							final String sys = soft;
							final String id = m.group(2);
							return new GSAInfo(ssl, defaultGsaHost, adminGsaHost, soft, sys, id, false);
						} else {
							LOG.warn("Failed to parse GSA information from: " + resp);
							return null;
						}
					}
				}
			}
		}
		return null;
	}

	public void trustCertificate(final X509Certificate x509Certificate) throws KeyStoreException, FileNotFoundException, NoSuchAlgorithmException, CertificateException, InvalidNameException, IOException {
		tm.trustCertificate(x509Certificate);
	}

	public void untrustCertificate(final String alias) throws KeyStoreException, FileNotFoundException, NoSuchAlgorithmException, CertificateException, IOException {
		tm.untrustCertificate(alias);
	}

	public static List<X509Certificate> getCertificates(GSAInfo gi) throws NoSuchAlgorithmException, KeyManagementException, UnknownHostException, IOException {
		final SSLContext ctx = SSLContext.getInstance("TLS");

		final X509CertsPeeker tm = new X509CertsPeeker();
		ctx.init((KeyManager[]) null, new TrustManager[] {tm}, (SecureRandom) null);

		final SSLSocketFactory sf = ctx.getSocketFactory();
		try (Socket s = sf.createSocket(gi.adminHost, 8443); OutputStream os = s.getOutputStream(); InputStream is = s.getInputStream()) {
			os.write("GET /EnterpriseController?actionType=about HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			final byte[] buf = new byte[4096];
			is.read(buf);
		}

		return tm.certs;
	}

	public static boolean checkFeederGateAccess(final GSAInfo gi) throws UnknownHostException, IOException {
		final int port = gi.ssl ? 19902 : 19900;
		try (Socket s = getSocket(gi.ssl, gi.defaultHost, port); OutputStream os = s.getOutputStream(); InputStream is = s.getInputStream()) {
			os.write("GET /getbacklogcount HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			final byte[] buf = new byte[4096];
			final int c = is.read(buf);
			final String answer = new String(buf, 0, c);
			return !answer.contains("Error - Unauthorized Request");
		}
	}

	public static boolean answersPing(final GSAInfo gi) {
		if (gi.noConnection) return false;
		final int port = gi.ssl ? 8443 : 8000;
		try (Socket s = getSocket(gi.ssl, gi.adminHost, port); OutputStream os = s.getOutputStream(); InputStream is = s.getInputStream()) {
			os.write("GET / HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			final byte[] buf = new byte[4096];
			is.read(buf);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	private static Socket getSocket(final boolean ssl, final String host, final int port) throws UnknownHostException, IOException {
		if (ssl) return SSLSocketFactory.getDefault().createSocket(host, port);
		else return new Socket(host, port);
	}
	
	private static Socket getSocket(final boolean ssl, final String host, final int port, final ReloadableTrustManager tm) throws UnknownHostException, IOException, KeyManagementException, NoSuchAlgorithmException {
		if (ssl) return tm.createSocket(host, port);
		else return new Socket(host, port);
	}

}
