package com.sword.gsa.spis.scs.connectormanager.workers;

import javax.servlet.http.HttpServletRequest;

public class LazyWorker extends XMLWorker {

	public LazyWorker() {
		super(null, null);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {
		return STATUS_ONLY_RESP;
	}
}