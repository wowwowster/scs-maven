package com.sword.gsa.spis.scs.saml.authent;

public class ArtifactRequest {

	public final String artifact;
	public final String issueInstant;
	public final String id;

	public ArtifactRequest(final String artifact, final String issueInstant, final String id) {
		super();
		this.artifact = artifact;
		this.issueInstant = issueInstant;
		this.id = id;
	}

}
