package com.sword.gsa.connectors.jive.apiwrap.places;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Place implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String id;
	public final String name;
	public final String apiSelf;
	public final String apiPlaces;
	public final Place parent;
	public final List<Place> children = new ArrayList<>();

	public Place(final String id, final String name, final String apiSelf, final String apiPlaces, final Place parent) {
		super();
		this.id = id;
		this.name = name;
		this.apiSelf = apiSelf;
		this.apiPlaces = apiPlaces;
		this.parent = parent;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(this.getClass().getSimpleName()).append(" ").append(name).append(" #").append(id);
		if (this instanceof Group) sb.append(" (group type: ").append(((Group) this).groupType.name()).append(")");
		if (parent == null) sb.append(" - root");
		else sb.append(" - child of ").append(parent.getClass().getSimpleName()).append(" #").append(parent.id);
		return sb.toString();
	}
}
