package com.sword.gsa.spis.scs.saml.authorization;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.authz.SAMLAuthzConf;
import com.sword.gsa.spis.scs.commons.connector.TransformedNamesCacheMgr;
import com.sword.gsa.spis.scs.commons.connector.models.INameTransformer;

/**
 * Contains the list of {@link RepositoryDecisions} that have to be processed.
 */
public final class GlobalAuthZRequest {

	private static final Logger logger = Logger.getLogger(GAuthZService.class);

	private final Map<SAMLAuthzConf, RepositoryDecisions> repositories = new HashMap<>();
	private final SCSConfiguration config;
	private final TransformedNamesCacheMgr cachedMappings;
	private final AuthzProgressObserver proc;

	public GlobalAuthZRequest(final SCSConfiguration conf, final TransformedNamesCacheMgr cachedMappings) {
		config = conf;
		this.cachedMappings = cachedMappings;
		proc = new AuthzProgressObserver(config.authzConf.globalAuthZTimeout);
		initRepositories();
	}

	/**
	 * Creates one {@link RepositoryDecisions} per repository.
	 */
	private void initRepositories() {
		final Collection<SAMLAuthzConf> repConfs = config.authzConf.authorizers;
		for (final SAMLAuthzConf rc : repConfs)
			if (rc.urlPattern == null) continue;// Not an authorization repository
			else repositories.put(rc, new RepositoryDecisions(rc, config));
	}

	public void authorize(final List<DecisionQuery> authz) throws Exception {
		final int workQueueLength = dispatchWork(authz);
		logger.debug("Authorizing " + workQueueLength + " URLs");
		if (workQueueLength > 0) proc.authorize(repositories.values());
	}

	/**
	 * Fills every {@link RepositoryDecisions} work queue
	 */
	private int dispatchWork(final List<DecisionQuery> authz) throws Exception {
		logger.info("Dispatching work for " + authz.size() + " urls.");
		int pendingWork = 0;
		DQ: for (final DecisionQuery q : authz) {
			for (final SAMLAuthzConf azConf : config.authzConf.authorizers)
				if (azConf.urlPattern.matcher(q.getResource()).find()) {
					logger.debug("URL matches connector #" + azConf.authorizer.uniqueId + ": " + q.getResource());
					if (azConf.nameTransformer != null) {
						logger.debug("A name transformer is configured for this repository: " + azConf.nameTransformer.uniqueId);

						final String subject = q.getSubject();
						String alternativeUsername = cachedMappings.getCachedMapping(azConf.nameTransformer, subject);
						if (alternativeUsername == null) {
							alternativeUsername = ((INameTransformer) azConf.nameTransformer).transformName(subject);
							cachedMappings.addCachedMapping(azConf.nameTransformer, subject, alternativeUsername);
						}
						q.switchSubject(alternativeUsername);
					}

					repositories.get(azConf).addAuthorization(q);
					pendingWork++;
					continue DQ;
				}
			logger.info("Unknown URL: " + q.getResource());
			q.setAuthZDecision(config.authzConf.unknownURLsDefaultAccess);// Out of scope URLs will be authorized using their own authZ process.
		}
		return pendingWork;
	}
}
