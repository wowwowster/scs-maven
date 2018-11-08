package com.sword.gsa.spis.scs.gsaadmin;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class ReloadableTrustManager implements X509TrustManager {

	private static final ReadWriteLock LOCK = new ReentrantReadWriteLock(true);

	private final Path trustStore;
	private X509TrustManager x509Tm = null;

	public ReloadableTrustManager(final Path trustStore) throws NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, IOException, CertificateException {
		this.trustStore = trustStore;
		reloadTrustManager();
	}

	public void reloadTrustManager() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, FileNotFoundException {
		LOCK.readLock().lock();
		try {
			final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream is = new FileInputStream(trustStore.toFile())) {
				ks.load(is, "changeit".toCharArray());
			}

			final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);
			X509TrustManager x509Tm = null;
			for (final TrustManager tm : tmf.getTrustManagers())
				if (tm instanceof X509TrustManager) {
					x509Tm = (X509TrustManager) tm;
					break;
				}
			this.x509Tm = x509Tm;
		} finally {
			LOCK.readLock().unlock();
		}
	}

	public Socket createSocket(final String host, final int port) throws NoSuchAlgorithmException, KeyManagementException, UnknownHostException, IOException {
		final SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init((KeyManager[]) null, new TrustManager[] {this}, (SecureRandom) null);
		final SSLSocketFactory sf = ctx.getSocketFactory();
		return sf.createSocket(host, port);
	}

	public void trustCertificate(final X509Certificate cert) throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException, InvalidNameException {
		LOCK.writeLock().lock();
		try {
			final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream is = new FileInputStream(trustStore.toFile())) {
				ks.load(is, "changeit".toCharArray());
			}
			final String cn = extractCnFromCert(cert);
			if (cn == null) throw new IllegalArgumentException("Could not find a CN in supplied certificate");
			else {
				if (ks.containsAlias(cn)) ks.deleteEntry(cn);
				ks.setCertificateEntry(cn, cert);
				try (FileOutputStream os = new FileOutputStream(trustStore.toFile())) {
					ks.store(os, "changeit".toCharArray());
				}
			}
		} finally {
			LOCK.writeLock().unlock();
		}
		reloadTrustManager();
	}

	public void untrustCertificate(final String alias) throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException {
		LOCK.writeLock().lock();
		try {
			final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream is = new FileInputStream(trustStore.toFile())) {
				ks.load(is, "changeit".toCharArray());
			}
			if (ks.containsAlias(alias)) ks.deleteEntry(alias);
			try (FileOutputStream os = new FileOutputStream(trustStore.toFile())) {
				ks.store(os, "changeit".toCharArray());
			}
		} finally {
			LOCK.writeLock().unlock();
		}
		reloadTrustManager();
	}

	@Override
	public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		x509Tm.checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		x509Tm.checkServerTrusted(chain, authType);
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return x509Tm.getAcceptedIssuers();
	}

	public static String extractCnFromCert(final X509Certificate cert) throws InvalidNameException {
		final LdapName ln = new LdapName(cert.getSubjectX500Principal().getName());
		String cn = null;
		for (final Rdn rdn : ln.getRdns())
			if (rdn.getType().equalsIgnoreCase("CN")) {
				cn = rdn.getValue().toString();
				break;
			}
		return cn;
	}

	public static String principalToHTML(final Principal p) throws InvalidNameException {
		final LdapName ln = new LdapName(p.getName());
		final StringBuilder sb = new StringBuilder();
		for (final Rdn rdn : ln.getRdns())
			if (rdn.getType().equalsIgnoreCase("CN")) sb.insert(0, "</div>").insert(0, rdn.getValue().toString()).insert(0, "<div>CN: ");
			else sb.append("<div>").append(rdn.getType()).append(": ").append(rdn.getValue().toString()).append("</div>");
		return sb.toString();
	}

}
