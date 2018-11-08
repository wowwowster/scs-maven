package com.sword.gsa.connectors.jive.apiwrap.places;

import java.io.Serializable;

public class Space extends Place implements Serializable {

	private static final long serialVersionUID = 1L;

	public Space(final String id, final String name, final String apiSelf, final String apiPlaces, final Place parent) {
		super(id, name, apiSelf, apiPlaces, parent);
	}

}
