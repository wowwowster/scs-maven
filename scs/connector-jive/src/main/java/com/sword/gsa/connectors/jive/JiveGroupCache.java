package com.sword.gsa.connectors.jive;

import java.io.Serializable;
import java.util.List;

import com.sword.gsa.connectors.jive.apiwrap.security.Group;
import com.sword.gsa.connectors.jive.apiwrap.security.User;
import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;

public class JiveGroupCache extends GroupCache implements Serializable {

	private static final long serialVersionUID = 1L;

	public final List<User> jiveUsers;
	public final List<Group> jiveGroups;

	public JiveGroupCache(final List<User> jiveUsers, final List<Group> jiveGroups) {
		super();
		this.jiveUsers = jiveUsers;
		this.jiveGroups = jiveGroups;
	}

	@Override
	public String getCacheInfo() {
		return String.format("%d users and %d groups in cache", jiveUsers.size(), jiveGroups.size());
	}

}
