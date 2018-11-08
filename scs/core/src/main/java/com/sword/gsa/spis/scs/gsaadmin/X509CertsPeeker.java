package com.sword.gsa.spis.scs.gsaadmin;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.X509TrustManager;

public class X509CertsPeeker implements X509TrustManager {

	public final List<X509Certificate> certs = new ArrayList<>();

	@Override
	public void checkClientTrusted(final X509Certificate[] arg0, final String arg1) {}

	@Override
	public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
		for (final X509Certificate c : chain) certs.add(c);
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return null;
	}

}
