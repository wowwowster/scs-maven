package com.sword.gsa.spis.scs.saml.authorization;

/**
 * Copyright (C) 2008 Google - Enterprise EMEA SE
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import sword.common.utils.enums.EnumFactory;

import com.sword.gsa.spis.scs.commons.http.ErrorManager;
import com.sword.gsa.spis.scs.commons.http.SCSServlet;
import com.sword.gsa.spis.scs.commons.utils.HttpUtils;
import com.sword.gsa.spis.scs.saml.SAMLRequestParser;
import com.sword.gsa.spis.scs.saml.SAMLResponseBuilder;
import com.sword.gsa.spis.scs.saml.SAMLSPIConstants;

/**
 * It implements the server process that treats the SAML authorization requests coming from the appliance. Each request includes the username and the URL, so that this servlet uses the user
 * information internally stored in a session in order to authorize the access to that content.
 * <p>
 * It invokes the root authorization process to get the authorization class that is going to process it. It gets that result and sends it back to the caller (appliance) in SAML format. If it's a 20X
 * response (OK), it returns a "Permit" message and if not, it'll return a "Deny". It doesn't care about the content sent from the content source as it only checks security.
 * <p>
 * In the case the root authorization would not have any URL pattern that matches with the content URL, it sends a -1 error code that is treated here as an "Indeterminate" response or a "Deny"
 * response depending on the configuration parameter type for Unknown URLs.
 */
public class GAuthZService extends SCSServlet {

	private static final Logger log = Logger.getLogger(GAuthZService.class);

	private static final long serialVersionUID = 1L;

	@Override
	protected boolean checkConfig(final ServletConfig config) {
		return true;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final long start = System.currentTimeMillis();
		log.info("Processing request: " + req.getMethod() + " on " + req.getRequestURL().toString());
		processRequest(req, resp);
		log.info("Request processed in " + (System.currentTimeMillis() - start) + " milliseconds.");
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		doGet(req, resp);
	}

	/**
	 * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods. It gets the SAML authorization request from the appliance and processes it accordingly.
	 */
	private void processRequest(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		try {
			String statusCode = SAMLSPIConstants.SAML_SC_SUCCESS;
			List<DecisionQuery> queries = null;
			try {
				queries = new SAMLRequestParser(HttpUtils.getRequestCharset(request)).extractQueriesFromRequest(request);
				log.debug("Extracted " + queries.size() + " queries from SAML request.");
			} catch (final Exception ex) {
				log.error("Bad input XML string - unable to respond");
				statusCode = SAMLSPIConstants.SAML_SC_REQUESTER_FAILURE;
				queries = null;
			}

			if (queries != null) {

				if (log.isTraceEnabled()) for (int i = 0; i < queries.size(); i++) {
					final DecisionQuery dq = queries.get(i);
					log.trace("Query #" + i + ": " + dq.getId() + " ; " + dq.getAction() + " ; " + dq.getActionNS() + " ; " + dq.getResource() + " ; " + dq.getSubject());
				}

				try {
					sendRequests(queries);
				} catch (final Throwable ex) {
					log.error("Problems getting decision - will respond " + SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE, ex);
					statusCode = SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE;
				}
			} else queries = new ArrayList<>();

			if (log.isTraceEnabled()) for (int i = 0; i < queries.size(); i++) {
				final DecisionQuery dq = queries.get(i);
				log.trace("Query #" + i + ": " + dq.getId() + " obtained " + EnumFactory.getLabel(dq.getAuthZDecision()));
			}

			try {
				final byte[] ab = SAMLResponseBuilder.buildAuthorizationResponse(statusCode, queries, conf.authnConf.samlEntityId);

				response.setContentType("text/xml;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.setContentLength(ab.length);
				try (final BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
					bos.write(ab);
				}
			} catch (final Exception ex) {
				log.error("Problems generating SOAP response - unable to respond", ex);
				throw ex;
			}
		} catch (final Throwable t) {
			final String mess = t.getClass().getName();
			log.error("Fatal Exception", t);
			t.printStackTrace();
			ErrorManager.processError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mess, t);
			return;
		}
	}

	private void sendRequests(final List<DecisionQuery> queries) throws Exception {
		final GlobalAuthZRequest gar = new GlobalAuthZRequest(conf, mappings);
		gar.authorize(queries);
	}
}
