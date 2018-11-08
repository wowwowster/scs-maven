package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;
import java.util.Collection;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.connectormanager.ConnectorSPIConstants;

public class GetConnectorInstanceList extends XMLWorker {

	private static final MessageFormat RESPONSE = new MessageFormat(BASE_RESPONSE.format(new String[] {ConnectorSPIConstants.SCS_PSEUDO_CONNECTOR_MGR_INFO + "\n  " + STATUS_0 + "\n  <ConnectorInstances>\n    {0}\n  </ConnectorInstances>"}));

	public GetConnectorInstanceList(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {
		final Collection<AConnector> repositories = conf.configuredConnectors.values();
		final StringBuilder sb = new StringBuilder();
		for (final AConnector r : repositories) {
			final IConnectorContext cc = conf.scsCtx.getConnectorCtx(r);
			if (sb.length() > 0) sb.append("\n  ");
			sb.append("<ConnectorInstance>\n      <ConnectorName>").append(r.uniqueId).append("</ConnectorName>\n").append("      <ConnectorType>").append(cc.getSpec().name()).append("</ConnectorType>\n").append("      <Version>").append(cc.getSpec().version())
			.append("</Version>\n").append("      <Status>0</Status>\n").append("      <ConnectorSchedules version=\"3\">").append(r.uniqueId).append(":500:300000:0-0</ConnectorSchedules>\n").append("      <ConnectorSchedule version=\"1\">")
			.append(r.uniqueId).append(":500:0-0</ConnectorSchedule>\n").append("    </ConnectorInstance>");
		}
		sb.append("<ConnectorInstance>\n      <ConnectorName>").append(conf.authnConf.cmMainConnectorName).append("</ConnectorName>\n").append("      <ConnectorType>SCS</ConnectorType>\n").append("      <Version>")
		.append(ConnectorSPIConstants.SCS_CONNECTOR_VERSION).append("</Version>\n").append("      <Status>0</Status>\n").append("      <ConnectorSchedules version=\"3\">").append(conf.authnConf.cmMainConnectorName)
		.append(":500:300000:0-0</ConnectorSchedules>\n").append("      <ConnectorSchedule version=\"1\">").append(conf.authnConf.cmMainConnectorName).append(":500:0-0</ConnectorSchedule>\n").append("    </ConnectorInstance>");
		return RESPONSE.format(new String[] {sb.toString()});
	}
}