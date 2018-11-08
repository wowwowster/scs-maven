package com.sword.gsa.spis.scs.saml.authorization;

import java.util.List;

import javax.servlet.http.Cookie;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.commons.connector.models.IAuthorizer;

/**
 * A BulkAuthzProcess aims at calling {@link AuthorizationProcess#authorize(Cookie[], List, String)} in the method {@link Runnable#run()} in order to let the processes controlling this class abort
 * process when reaching timeout.
 */
public class RepositoryProcessor implements Runnable {

	private static final Logger LOG = Logger.getLogger(GAuthZService.class);

	public final String id;
	private final RepositoryDecisions repDec;

	public RepositoryProcessor(final RepositoryDecisions rep) {
		id = rep.authorizerConf.authorizer.uniqueId;
		repDec = rep;
	}

	@Override
	public void run() {
		final List<DecisionQuery> decisionQueries = repDec.getDecisionQueries();
		LOG.info("Starting Thread for repository: " + id + " ; Authorizing " + decisionQueries.size() + " URLs for user: " + decisionQueries.get(0).getSubject());
		final IAuthorizer authProcess = (IAuthorizer) repDec.authorizerConf.authorizer;
		final long startTime = System.currentTimeMillis();
		try {
			if (authProcess == null) {
				LOG.error("Repository " + id + " is not configured properly. Denying access to documents.");
				return;
			} else authProcess.authorize(decisionQueries);
		} catch (final Exception ex) {
			LOG.error("Thread " + id + " threw an Exception", ex);
		} finally {
			if (authProcess != null && LOG.isInfoEnabled()) {
				final StringBuilder sb = new StringBuilder("Thread ").append(id).append(" completed in ");
				sb.append(Long.toString(System.currentTimeMillis() - startTime)).append(" milliseconds");
				LOG.info(sb.toString());
			}
		}
	}

	public boolean hasWorkToDo() {
		return repDec.getDecisionQueries().size() != 0;
	}

}
