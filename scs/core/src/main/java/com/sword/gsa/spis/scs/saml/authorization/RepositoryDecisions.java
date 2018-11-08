package com.sword.gsa.spis.scs.saml.authorization;

import java.util.ArrayList;
import java.util.List;

import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.authz.SAMLAuthzConf;

/**
 * This class contains a {@link ConnectorReference} and the set of {@link DecisionQuery} that have to be answered for this repository.
 */
public class RepositoryDecisions {

	public final SAMLAuthzConf authorizerConf;
	private final List<DecisionQuery> authzDecisions = new ArrayList<>();

	public RepositoryDecisions(final SAMLAuthzConf authorizerConf, final SCSConfiguration conf) {
		this.authorizerConf = authorizerConf;
	}

	List<DecisionQuery> getDecisionQueries() {
		return authzDecisions;
	}

	public void addAuthorization(final DecisionQuery authzDecisionQuery) {
		authzDecisions.add(authzDecisionQuery);
	}

	@Override
	public boolean equals(final Object obj) {
		if (RepositoryDecisions.class.isInstance(obj)) return authorizerConf.equals(((RepositoryDecisions) obj).authorizerConf);
		else return false;
	}
}
