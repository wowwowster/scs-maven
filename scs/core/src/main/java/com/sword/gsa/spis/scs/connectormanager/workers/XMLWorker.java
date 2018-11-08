package com.sword.gsa.spis.scs.connectormanager.workers;

import java.text.MessageFormat;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;

public abstract class XMLWorker {

	static final MessageFormat BASE_RESPONSE = new MessageFormat("<CmResponse>\n  {0}\n</CmResponse>");
	static final String STATUS_0 = "<StatusId>0</StatusId>";
	static final String STATUS_ONLY_RESP = BASE_RESPONSE.format(new String[] {STATUS_0});

	final SCSConfiguration conf;
	final ServletContext ctx;

	public XMLWorker(final SCSConfiguration conf, final ServletContext ctx) {
		this.conf = conf;
		this.ctx = ctx;
	}

	public abstract String getXMLResponse(HttpServletRequest req) throws Exception;

}
