package com.sword.gsa.connectors.jive.apiwrap.security;

import java.io.Serializable;

public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String id;
	public final String name;
	public final String email;
	public final String apiSelf;

	public User(final String id, final String name, final String email, final String apiSelf) {
		super();
		this.id = id;
		this.name = name;
		this.email = email;
		this.apiSelf = apiSelf;
	}

	@Override
	public String toString() {
		return new StringBuilder(name).append(" #").append(id).append(" - mailto:").append(email).toString();
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof User) return apiSelf.equals(((User) obj).apiSelf);
		return false;
	}

}
