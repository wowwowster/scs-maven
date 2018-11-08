package com.sword.gsa.connectors.gapps.youtube;

import java.util.Map;

import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;
import sword.connectors.commons.config.ConnectorSpec;

import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;

@ConnectorSpec(name = "Youtube", version = "0.1")
public final class Connector extends AConnector implements Indexer {


	static final String PARAM_APIKEY = "API_Key";
	static final String PARAM_APPNAME = "Application_name";
	static final String PARAM_CHANNEL = "channel";
	static final String PARAM_CLIENT_ID = "ClientId";
	static final String PARAM_CLIENT_SECRET = "ClientSecret";
	static final String PARAM_ACCESS_TOKEN = "AccessToken";
	static final String PARAM_REFRESH_TOKEN = "RefreshToken";
	static final String PARAM_PLAYLISTS = "Playlists";

	public static final CP[] CONFIGURATION_PARAMETERS = new CP[] {
		new CP(CPType.STRING, PARAM_APIKEY, "API Key", "API key from the Google console"),
		new CP(CPType.STRING, PARAM_APPNAME, "Application name", "name of the project as defined in the Google console(cf. doc)", CP.MANDATORY),
		new CP(CPType.STRING, PARAM_CHANNEL, "Channel account", "Account name of the channel. Depending on the creation date, it can either be a regular name, or an ID."),
		new CP(CPType.STRING, PARAM_PLAYLISTS, "Additional playlists", "If you wish to index videos that are NOT uploaded by the channel, input their playlist ID here.", CP.MULTIVALUE),
		new CP(CPType.STRING, PARAM_CLIENT_ID, "Client ID", "For full indexation using OAuth - indicate the client ID to use (cf. doc)"),
		new CP(CPType.STRING, PARAM_CLIENT_SECRET, "Client secret", "For full indexation using OAuth - indicate the client secret to use (cf. doc)"),
		new CP(CPType.STRING, PARAM_ACCESS_TOKEN, "Access token", "For full indexation using OAuth - indicate the access token to use (cf. doc)"),
		new CP(CPType.STRING, PARAM_REFRESH_TOKEN, "Refresh token", "For full indexation using OAuth - indicate the refresh token to use (cf. doc)"),		

		
	};
	
	public Connector(String uniqueId, Map<String, String> configurationParameters, String namespace, String nameTransformer) {
		super(uniqueId, configurationParameters, namespace, nameTransformer);
	}

	@Override
	public Class<? extends AExplorer> getExplorerClass() {
		return Explorer.class;
	}

	@Override
	public Class<? extends ADocLoader> getDocLoaderClass() {
		return DocLoader.class;
	}

	@Override
	public boolean supportsEarlyBinding() {
		return true;
	}

	@Override
	public boolean canDetectAclModification() {
		return false;
	}

}
