package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.List;

import com.sword.gsa.spis.scs.saml.authorization.DecisionQuery;

public interface IAuthorizer extends IConnector {

	public void authorize(List<DecisionQuery> decisionQueries);
	
}
