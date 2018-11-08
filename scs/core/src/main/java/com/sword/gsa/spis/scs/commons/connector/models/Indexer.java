package com.sword.gsa.spis.scs.commons.connector.models;

public interface Indexer extends IConnector {

	public boolean supportsEarlyBinding();

	/**
	 * Indicates whether the connector is able to detect ACL changes or if the push process should store the ACL hash in order to be able to detect future changes by comparing hash values
	 */
	public boolean canDetectAclModification();

	/**
	 * @return The {@link Class} that traverses the source system and returns documents, containers and their ACL.
	 */
	public Class<? extends AExplorer> getExplorerClass();

	/**
	 * @return The {@link Class} that loads documents and returns doc content, metadata and ACLs
	 */
	public Class<? extends ADocLoader> getDocLoaderClass();

}
