package com.sword.gsa.connectors.jive.apiwrap.places;

import java.io.Serializable;

public class Group extends Place implements Serializable {

	private static final long serialVersionUID = 1L;

	public final GroupType groupType;

	public Group(final String id, final String name, final String apiSelf, final String apiPlaces, final Place parent, final String groupType) {
		super(id, name, apiSelf, apiPlaces, parent);
		this.groupType = GroupType.parse(groupType);
	}

	public boolean canBeViewedByEntireCommunity() {
		return !(groupType == GroupType.PRIVATE || groupType == GroupType.SECRET);
	}

}
