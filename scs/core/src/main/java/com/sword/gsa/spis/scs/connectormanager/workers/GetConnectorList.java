package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.ConnectorContext;
import com.sword.gsa.spis.scs.connectormanager.ConnectorSPIConstants;

public class GetConnectorList extends XMLWorker {

	private static final MessageFormat RESPONSE = new MessageFormat(BASE_RESPONSE.format(new String[] {ConnectorSPIConstants.SCS_PSEUDO_CONNECTOR_MGR_INFO + "\n  " + STATUS_0 + "\n  <ConnectorTypes>{0}</ConnectorTypes>"}));

	public GetConnectorList(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {

		final StringBuilder sb = new StringBuilder();
		final List<ConnectorContext> installedConnectors = conf.scsCtx.installedConnectors;
		for (final ConnectorContext cc : installedConnectors) {
			if (cc.getClassName().startsWith("sword.gsp.connector.webservices.")) continue;
			sb.append("\n    <ConnectorType version=\"" + cc.getSpec().version() + "\">" + cc.getSpec().name() + "</ConnectorType>");
		}
		return RESPONSE.format(new String[] {sb.toString()});
	}
}