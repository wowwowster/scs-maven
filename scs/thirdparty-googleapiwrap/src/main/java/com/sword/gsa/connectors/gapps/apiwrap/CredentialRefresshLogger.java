package com.sword.gsa.connectors.gapps.apiwrap;

import java.io.IOException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;

public class CredentialRefresshLogger implements CredentialRefreshListener {

	@Override
	public void onTokenResponse(Credential credential, TokenResponse tokenResponse) {
		ApiHelper.LOG.info("Refreshed OAuth token successfully");
	}

	@Override
	public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse) {
		if (tokenErrorResponse == null) {
			ApiHelper.LOG.info("Failed to refresh OAuth token (null)");
		} else {
			try {
				ApiHelper.LOG.info("Failed to refresh OAuth token: " + tokenErrorResponse.toPrettyString());
			} catch (IOException e) {
				ApiHelper.LOG.info("Failed to refresh OAuth token: " + tokenErrorResponse.toString());
			}
		}
	}

}
