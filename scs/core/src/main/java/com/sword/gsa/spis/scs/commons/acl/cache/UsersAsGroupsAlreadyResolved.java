package com.sword.gsa.spis.scs.commons.acl.cache;

/**
 * By default, for every user <code>UserX</code> with namespace <code>MyNS</code>, the connector indexes him as <code>UserX</code> with namespace <code>MyNS</code> and as <code>UserX</code> with namespace <code>Default</code>, both member of group <code>UserX</code> with namespace <code>MyNS</code>:
 * <ul>
 * <li>Feeding users as groups is necessary when a person's username differs from a system to another (John Doe is john.doe@mydomain.com in Google Apps and jdoe in Aflresco for instance) because the GSA only knows one name for an authenticated user.</li>
 * <li>Giving the original namespace and the default namespace for the same user is necessary because SAML is not able to return user a namespace for authenticated users.</li>
 * </ul>
 * When a user can have several names within the same application (for example Google Apps allows multiple aliases per user), the SCS will not be able to handle that automatically and the connector will have to implement the fact that <code>UserX</code> and <code>UserX-Alias</code> belong to group <code>UserX</code>.<br>
 * When the connector does that, it must throw this throwable to indicate to the SCS that it won't have to perform automatic users-as-groups transformation.
 */
public class UsersAsGroupsAlreadyResolved extends Throwable {

	private static final long serialVersionUID = 1L;

	public UsersAsGroupsAlreadyResolved() { }

}
