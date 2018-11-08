package com.sword.gsa.spis.scs.saml.authorization;

import java.util.List;

/**
 * Holds the authorization decision query/response to take a security decision about whether to let the user access the resource or not.
 */
public class DecisionQuery {

	private static final Decision DEFAULT_DECISION = Decision.valueOf(System.getProperty("sword.gsa.saml.bulk.authZ.default-decision", Decision.DENY.name()));

	private final String id;// kmdlialcpmfphjkmjhcfpmgmmopfnbcehdfbinpm
	private final String resource;// http://swp-vm-dctm651.parisgsa.lan:8080/da/DCTM651/dm_document/000004
	private final String subject;// jpasquon
	private final String action;// GET
	private final String actionNamespace;// urn:oasis:names:tc:SAML:1.0:action:ghpp

	/**
	 * If a {@link MapperReference} is defined, the subject that is user for authorization is different from the verified identity the GSA knows. Therefore both name need to be stored (one for
	 * building the authorization request, one for building the authorization response)
	 */
	private String subject4authz;

	private Decision authZDecision;

	public DecisionQuery(final String id, final String resource, final String subject, final String action, final String actionNamespace) {
		this.id = id;
		this.resource = resource;
		this.subject = subject;
		subject4authz = subject;
		this.action = action;
		this.actionNamespace = actionNamespace;
		authZDecision = DEFAULT_DECISION;
	}

	public String getId() {
		return id;
	}

	public String getResource() {
		return resource;
	}

	/**
	 * @return The verified ID the GSA knows
	 */
	public String getOriginalSubject() {
		return subject;
	}

	/**
	 * @return The user name to use for authorization after applying {@link MapperReference}. The method name remains getSubject to avoid having to change the connectors code.
	 */
	public String getSubject() {
		return subject4authz;
	}

	public void switchSubject(final String alternativeSubject) {
		subject4authz = alternativeSubject;
	}

	public String getAction() {
		return action;
	}

	public String getActionNS() {
		return actionNamespace;
	}

	public Decision getAuthZDecision() {
		return authZDecision;
	}

	/**
	 * Sets the authZDecision.
	 */
	public void setAuthZDecision(final Decision authZDecision) {
		if (authZDecision == null) this.authZDecision = DEFAULT_DECISION;
		else this.authZDecision = authZDecision;
	}

	/**
	 * Implement {@link Object#equals(Object)} in order be able to retrieve a {@link DecisionQuery} from a {@link List} using its ID. Indeed {@link List#indexOf(Object)} loops over Collection elements
	 * and calls Object's <code>equals</code> method to determine if the current element is the one it is looking for.
	 */
	@Override
	public boolean equals(final Object obj) {
		if (DecisionQuery.class.isInstance(obj)) return id.equals(((DecisionQuery) obj).id);
		else return false;
	}
}
