package com.sword.gsa.spis.scs.push.databases;

import java.util.Date;

public final class DBBrowserNode {

	public final int type;
	public final String id;
	public final int state;
	public final Date lastindexingDate;

	public DBBrowserNode(final int type, final String id, final int state, final Date lastindexingDate) {
		super();
		this.type = type;
		this.id = id;
		this.state = state;
		this.lastindexingDate = lastindexingDate;
	}

}
