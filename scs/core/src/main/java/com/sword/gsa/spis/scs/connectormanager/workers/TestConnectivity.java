package com.sword.gsa.spis.scs.connectormanager.workers;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.connectormanager.ConnectorSPIConstants;

public class TestConnectivity extends XMLWorker {

	private static final String RESPONSE = BASE_RESPONSE.format(new String[] {ConnectorSPIConstants.SCS_PSEUDO_CONNECTOR_MGR_INFO + "\n  " + STATUS_0});

	public TestConnectivity(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {
		return RESPONSE;
	}
}