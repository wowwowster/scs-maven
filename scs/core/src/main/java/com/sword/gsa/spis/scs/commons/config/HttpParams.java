package com.sword.gsa.spis.scs.commons.config;

public class HttpParams {
	
	public final String defaultHost;
	public final int httpPort;
	public final int httpsPort;
	
	public HttpParams(String defaultHost, int httpPort, int httpsPort) {
		super();
		this.defaultHost = defaultHost;
		this.httpPort = httpPort;
		this.httpsPort = httpsPort;
	}

}
