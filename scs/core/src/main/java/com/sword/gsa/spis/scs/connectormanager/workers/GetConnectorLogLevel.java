package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;

public class GetConnectorLogLevel extends XMLWorker {

	public static Logger LOG = Logger.getLogger("ConnectorLog");

	private static final MessageFormat RESPONSE = new MessageFormat(BASE_RESPONSE.format(new String[] {STATUS_0 + "\n  <Level>{0}</Level>\n  <Info>Connector Logging level is {0}</Info>"}));

	public GetConnectorLogLevel(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {
		return RESPONSE.format(new String[] {LOG.getLevel() == null ? Level.OFF.getName() : LOG.getLevel().getName()});
	}
}