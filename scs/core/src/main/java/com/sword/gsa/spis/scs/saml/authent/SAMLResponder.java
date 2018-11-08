package com.sword.gsa.spis.scs.saml.authent;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sword.gsa.spis.scs.commons.http.ErrorManager;
import com.sword.gsa.spis.scs.commons.http.SCSServlet;
import com.sword.gsa.spis.scs.commons.utils.HttpUtils;
import com.sword.gsa.spis.scs.saml.SAMLRequestParser;
import com.sword.gsa.spis.scs.saml.SAMLResponseBuilder;
import com.sword.gsa.spis.scs.saml.SAMLSPIConstants;
import com.sword.gsa.spis.scs.saml.authent.SAMLCache.CachedUser;

/**
 * Checks whether a VerifiedIdentity was stored for given artifact and builds the artifact binding response.
 */
public final class SAMLResponder extends SCSServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * This servlet does not need any configuration
	 */
	@Override
	protected boolean checkConfig(final ServletConfig config) {
		return true;
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {
			LOG.info("Responder request");
			final ArtifactRequest ar = new SAMLRequestParser(HttpUtils.getRequestCharset(req)).extractArtifactFromRequest(req);
			LOG.debug("Artifact: " + ar.artifact);
			final CachedUser cUser = SAMLCache.get().getCachedUser(ar.artifact);
			String respStr = null;
			if (cUser != null) if (cUser.getUsername() != null) {
				LOG.debug("Found corresponding user: " + cUser.getUsername());
				respStr = SAMLResponseBuilder.buildArtifactResponse(SAMLSPIConstants.SAML_SC_SUCCESS, cUser.getUsername(), ar.id, cUser.getSamlid(), cUser.getIssueInstant(), cUser.getConsumer(), cUser.getSecMngrIssuer(), cUser.getGroups(),
					conf.authnConf.trustDuration, conf.authnConf.samlEntityId);
			}
			if (respStr == null) {
				LOG.info("No corresponding user.");
				respStr = SAMLResponseBuilder.buildArtifactResponse(SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE, null, ar.id, "", new Date(), "", "", null, conf.authnConf.trustDuration, conf.authnConf.samlEntityId);
			}
			if (LOG.isTraceEnabled()) LOG.trace("Sending SOAP response: " + respStr);

			final byte[] responseBytes = respStr.getBytes(StandardCharsets.UTF_8);
			resp.setContentType("text/xml");
			resp.setContentLength(responseBytes.length);
			resp.setCharacterEncoding("UTF-8");
			try (final BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream())) {
				bos.write(responseBytes);
			}
			LOG.info("Responder request answered correctly");
		} catch (final Exception t) {
			final String mess = t.getClass().getName();
			LOG.error("Fatal Exception", t);
			t.printStackTrace();
			ErrorManager.processError(req, resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mess, t);
			return;
		}
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		doPost(req, resp);
	}

}
