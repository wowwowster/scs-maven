package com.sword.gsa.spis.scs.commons.connector;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;

import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.ConnectorSpec;

public interface IConnectorContext {

	public Path getHomeDir();

	public URLClassLoader getClassLoader();

	public String getClassName();

	public ConnectorSpec getSpec();

	public CP[] getConfParams();

	public boolean isNameTransformer();

	public boolean isGroupRetriever();

	public boolean isCachableGroupRetriever();

	public boolean isAuthorizer();

	public boolean isAuthNHeaders();

	public boolean isAuthNFormData();

	public boolean isAuthNDelegator();

	public boolean isIndexer();

	public Path getInstanceHomeDir(String instanceId);

	public Path getConnectorEndorsedDir();

	public Path getIndexerConfDir(String instanceId);

	public Path getIndexerFeedDir(String instanceId);

	public Path getIndexerDashboardDir(String instanceId);

	public Path getIndexerDBDir(String instanceId);

	public Path getIndexerWorkDir(String instanceId);

	public Path getIndexerNextStartFile(String instanceId);

	public Path getIndexerConfFile(String instanceId) throws IOException;

	public Path getIndexerStopFile(String instanceId);

	public Path getIndexerLockFile(String instanceId);

	public Path getGroupRetrieverCacheDir(String instanceId);

}