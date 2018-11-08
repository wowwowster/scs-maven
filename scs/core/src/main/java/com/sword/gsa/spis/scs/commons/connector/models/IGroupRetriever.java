package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.Collection;

import sword.gsa.xmlfeeds.builder.acl.Group;

import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.throwables.FailGroupRetrieval;

public interface IGroupRetriever extends IConnector {

	public Collection<Group> getGroups(final String username, final GroupCache cacheObject) throws Exception, FailGroupRetrieval;

}
