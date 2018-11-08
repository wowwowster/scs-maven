package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;

public class GetConnectorConfigToEdit extends XMLWorker {

	private static final MessageFormat RESPONSE = new MessageFormat(BASE_RESPONSE.format(new String[] {STATUS_0
		+ "\n  <ConfigureResponse>\n    <FormSnippet><![CDATA[{0}]]></FormSnippet>\n    <ConnectorConfigXml><![CDATA[{1}]]></ConnectorConfigXml>\n  </ConfigureResponse>"}));

	public GetConnectorConfigToEdit(final SCSConfiguration conf, final ServletContext ctx) {
		super(conf, ctx);
	}

	@Override
	public String getXMLResponse(final HttpServletRequest req) {
		final String ru = req.getRequestURL().toString();
		final String managerUrl = new StringBuilder(ru.substring(0, ru.indexOf(req.getContextPath()))).append("/SCS/secure/manager.html").toString();
		final StringBuilder paramXml = new StringBuilder("\n<div style=\"padding: 40px 12px; font-size: 110%; color: red\" >SCS connectors must be configured using the <a target=\"_blank\" href=\"").append(managerUrl).append(
			"\" >SCS configuration web interface</a>.</div>\n");
		return RESPONSE.format(new String[] {paramXml.toString(), ""});
	}

}