package com.sword.gsa.connectors.jive.apiwrap.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String id;
	public final String name;
	public final String apiSelf;
	public final String apiMembers;
	public final List<User> members = new ArrayList<>();

	public Group(final String id, final String name, final String apiSelf, final String apiMembers) {
		super();
		this.id = id;
		this.name = name;
		this.apiSelf = apiSelf;
		this.apiMembers = apiMembers;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(name).append(" #").append(id);
		for (final User u : members)
			sb.append("\n\t- ").append(u.toString());
		return sb.toString();
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Group) return id.equals(((Group) obj).id);
		return false;
	}

}
