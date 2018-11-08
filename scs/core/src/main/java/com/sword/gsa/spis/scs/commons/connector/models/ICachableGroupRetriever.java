package com.sword.gsa.spis.scs.commons.connector.models;

import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;

import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;

public interface ICachableGroupRetriever extends IGroupRetriever {

	public static CP CP_CACHE_REFRESH_INTERVAL = new CP(CPType.DECIMAL, "CacheRefreshInterval", "Cache refresh interval <i>(minutes)</i>", "Determines how often the group retriever cache cache will be updated.");

	public GroupCache getNewCache() throws Exception;

}