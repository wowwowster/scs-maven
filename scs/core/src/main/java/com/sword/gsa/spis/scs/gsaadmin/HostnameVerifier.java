package com.sword.gsa.spis.scs.gsaadmin;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import org.apache.log4j.Logger;

public class HostnameVerifier implements javax.net.ssl.HostnameVerifier {
	
	private static final Logger LOG = Logger.getLogger(HostnameVerifier.class);

	private final String gsaAdminHost;
	private final String gsaDefaultHost;
	private final javax.net.ssl.HostnameVerifier dhnv;
	private final KeyStore ks;

	public HostnameVerifier(final Path trustStore, final String gsaDefaultHost, final String gsaAdminHost) throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException {
		this.gsaDefaultHost = gsaDefaultHost;
		this.gsaAdminHost = gsaAdminHost;
		this.dhnv = HttpsURLConnection.getDefaultHostnameVerifier();
		this.ks = KeyStore.getInstance(KeyStore.getDefaultType());
		try (FileInputStream is = new FileInputStream(trustStore.toFile())) {
			ks.load(is, "changeit".toCharArray());
		}
	}

	public HostnameVerifier(final Path trustStore, final GSAInfo gi) throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException {
		this(trustStore, gi.defaultHost, gi.adminHost);
	}

	@Override
	public boolean verify(String hostname, SSLSession session) {
		final boolean defaultVerif = dhnv.verify(hostname, session);
		if (defaultVerif) {
			return defaultVerif;
		} else {
			if (gsaDefaultHost.equals(hostname) || gsaAdminHost.equals(hostname)) {
				LOG.info("Verifying certificate for " + hostname);
				try {
					final CertificateFactory cf = CertificateFactory.getInstance("X.509");
					X509Certificate[] certs = session.getPeerCertificateChain();
					for (X509Certificate cert : certs) {
						java.security.cert.X509Certificate javaX509 = (java.security.cert.X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded()));
						String cn = ReloadableTrustManager.extractCnFromCert(javaX509);
						LOG.info("Verifying " + cn);
						if (ks.containsAlias(cn) && Arrays.equals(javaX509.getEncoded(), ks.getCertificate(cn).getEncoded())) {
							return true;
						}
					}
				} catch (Exception e) {
					LOG.error("Invalid certificates: ", e);
					return false;
				}
			}
			return false;
		}
	}

}
