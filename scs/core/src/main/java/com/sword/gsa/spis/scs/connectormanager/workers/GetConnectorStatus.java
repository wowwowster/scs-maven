package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;

public class GetConnectorStatus extends XMLWorker {

	private static final MessageFormat RESPONSE = new MessageFormat(BASE_RESPONSE.format(new String[] {STATUS_0
		+ "\n  <ConnectorStatus>\n    <ConnectorName>{0}</ConnectorName>\n    <ConnectorType>{1}</ConnectorType>\n    <Status>0</Status>\n    <ConnectorSchedules version=\"3\">{0}:500:300000:0-0</ConnectorSchedules>\n  </ConnectorStatus>"}));

	public GetConnectorStatus(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {

		final String connectorName = req.getParameter("ConnectorName");

		try {
			String ct = null;
			if (conf.authnConf.cmMainConnectorName.equals(connectorName)) ct = "SCS";
			else ct = conf.scsCtx.getConnectorCtx(conf.configuredConnectors.get(connectorName)).getSpec().name();
			return RESPONSE.format(new String[] {connectorName, ct});
		} catch (final Exception e) {
			Logger.getLogger(GetConnectorStatus.class).error("Could not find connector class for: " + connectorName, e);
			return BASE_RESPONSE.format(new String[] {"<StatusId>-1</StatusId>"});
		}

	}
}