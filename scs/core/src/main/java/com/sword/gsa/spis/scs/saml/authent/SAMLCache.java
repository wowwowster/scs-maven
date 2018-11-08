package com.sword.gsa.spis.scs.saml.authent;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sword.gsa.xmlfeeds.builder.acl.Group;

/**
 * Stores artifact that were generated for user who successfully passed the authentication.
 */
public final class SAMLCache {

	private static final SAMLCache instance = new SAMLCache();

	public static SAMLCache get() {
		return instance;
	}

	private final Map<String, CachedUser> cache = new HashMap<>();

	private SAMLCache() {}

	public synchronized CachedUser registerArtifact(final String artifactValue, final String samlid, final Date issueInstant, final String consumer, final String secMngrIssuer, final String username, final Collection<Group> groups) {
		return cache.put(artifactValue, buildUser(samlid, issueInstant, consumer, secMngrIssuer, username, groups));
	}

	public synchronized CachedUser getCachedUser(final String artifactValue) {
		return cache.remove(artifactValue);
	}

	public static CachedUser buildUser(final String samlid, final Date issueInstant, final String consumer, final String secMngrIssuer, final String username, final Collection<Group> groups) {
		return new CachedUser(samlid, issueInstant, consumer, secMngrIssuer, username, groups);
	}

	public static class CachedUser {

		final String samlid;
		final Date issueInstant;
		final String consumer;
		final String secMngrIssuer;
		final String username;
		final Collection<Group> groups;

		public CachedUser(final String samlid, final Date issueInstant, final String consumer, final String secMngrIssuer, final String username, final Collection<Group> group) {
			this.samlid = samlid;
			this.issueInstant = issueInstant;
			this.consumer = consumer;
			this.secMngrIssuer = secMngrIssuer;
			this.username = username;
			groups = group;
		}

		public String getSamlid() {
			return samlid;
		}

		public Date getIssueInstant() {
			return issueInstant;
		}

		public String getConsumer() {
			return consumer;
		}

		public String getSecMngrIssuer() {
			return secMngrIssuer;
		}

		public String getUsername() {
			return username;
		}

		public Collection<Group> getGroups() {
			return groups;
		}

	}

}
