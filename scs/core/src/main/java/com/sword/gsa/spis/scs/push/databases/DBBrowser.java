package com.sword.gsa.spis.scs.push.databases;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sword.common.utils.StringUtils;
import sword.connectors.commons.config.CPUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;
import com.sword.gsa.spis.scs.push.tree.NodeRef;
import com.sword.gsa.spis.scs.push.tree.State;
import com.sword.gsa.spis.scs.push.tree.Type;

public final class DBBrowser implements AutoCloseable {

	private static final Object LOCK = new Object();
	private static String CACHE_ID = null;

	private static final List<DBBrowserNode> ROOT_CACHE_CHILDREN = new ArrayList<>();
	private static final List<String> ROOT_CACHE_PARENTS = new ArrayList<>();

	private static final List<DBBrowserNode> CACHE_CHILDREN = new ArrayList<>();
	private static final List<String> CACHE_PARENTS = new ArrayList<>();

	public final Object dbBrowsingEndNotifier = new Object();
	private final TreeTableManager ttm;

	public DBBrowser(final PushConfig conf) throws ClassNotFoundException, SQLException, IOException {
		ttm = new TreeTableManager(conf, true);
		loadData(null, false);
	}

	public void listChildren(final OutputStream os, final String pid, final int num, final int page, final String state, final boolean useCache) throws SQLException, IOException {

		final Cache cache = loadData(pid, useCache);
		final int indexStart = num * page;
		final int indexEnd = indexStart + num;
		final int rnSize = cache.cn.size();

		outputChildren(os, pid, cache, rnSize, page, state, indexStart, indexEnd, null);

	}

	private Cache loadData(final String pid, final boolean useCache) throws SQLException {

		final List<DBBrowserNode> cn;
		final List<String> pn;

		synchronized (LOCK) {
			if (useCache) {
				if (StringUtils.isNullOrEmpty(pid)) {// If fetching root children - return root cache
					cn = ROOT_CACHE_CHILDREN;
					pn = ROOT_CACHE_PARENTS;
				} else if (CACHE_ID == null) {// if no cache loaded yet - load cache and return
					CACHE_ID = pid;
					cn = ttm.__getChildNodes(pid);
					CACHE_CHILDREN.clear();
					CACHE_CHILDREN.addAll(cn);
					pn = ttm.__getParentNodesIds(new NodeRef(true, pid));
					CACHE_PARENTS.clear();
					CACHE_PARENTS.addAll(pn);
				} else if (StringUtils.npeProofEquals(pid, CACHE_ID)) {// if fetching any other cached children - return normal cache
					cn = CACHE_CHILDREN;
					pn = CACHE_PARENTS;
				} else {// otherwise overwrite old cache with new cache
					CACHE_ID = pid;
					cn = ttm.__getChildNodes(pid);
					CACHE_CHILDREN.clear();
					CACHE_CHILDREN.addAll(cn);
					pn = ttm.__getParentNodesIds(new NodeRef(true, pid));
					CACHE_PARENTS.clear();
					CACHE_PARENTS.addAll(pn);
				}
			} else {
				if (StringUtils.isNullOrEmpty(pid)) {
					cn = ttm.__getChildNodes(null);
					pn = ttm.__getParentNodesIds(new NodeRef(true, null));
					ROOT_CACHE_CHILDREN.clear();
					ROOT_CACHE_CHILDREN.addAll(cn);
				} else {
					cn = ttm.__getChildNodes(pid);
					pn = ttm.__getParentNodesIds(new NodeRef(true, pid));
					CACHE_CHILDREN.clear();
					CACHE_CHILDREN.addAll(cn);
					CACHE_PARENTS.clear();
					CACHE_PARENTS.addAll(pn);
				}
			}
		}

		return this.new Cache(cn, pn);

	}

	public void findChild(final OutputStream sos, final String pid, final String nid) throws IOException, SQLException {

		final Cache cache = loadData(pid, true);

		final int rnSize = cache.cn.size();

		final int num = 25;
		int page = -1;
		for (int j = 0; j < rnSize; j++) {
			if (cache.cn.get(j).id.equals(nid)) {
				page = j / 25;
				final int indexStart = num * page;
				final int indexEnd = indexStart + num;
				outputChildren(sos, pid, cache, rnSize, page, null, indexStart, indexEnd, nid);
				return;
			}
		}
	}

	private static void outputChildren(final OutputStream sos, final String pid, final Cache cache, final int rnSize, final int page, final String state, final int indexStart, final int indexEnd, final String searchedId) throws IOException {
		try (JsonGenerator jg = new JsonFactory().createGenerator(sos, JsonEncoding.UTF8).configure(Feature.AUTO_CLOSE_TARGET, false)) {
			jg.writeStartObject();

			if (!StringUtils.isNullOrEmpty(pid)) {
				jg.writeArrayFieldStart("Parents");
				for (final String _pid : cache.pn) {
					jg.writeString(_pid);
				}
				jg.writeEndArray();
			}

			final List<String> states = new ArrayList<>();
			if (!StringUtils.isNullOrEmpty(state)) {
				final List<String> _states = CPUtils.stringToList(state);
				for (final String s : _states)
					if (StringUtils.isInteger(s)) {
						states.add(s);
					}
			}

			if (states.isEmpty()) {

				jg.writeNumberField("page", page);
				jg.writeNumberField("ChildrenCount", rnSize);
				jg.writeBooleanField("HasPrev", page > 0);
				jg.writeBooleanField("HasNext", indexEnd < rnSize);
				if (searchedId != null) {
					jg.writeStringField("SearchedFor", searchedId);
				}

				jg.writeArrayFieldStart("children");
				if (indexStart < rnSize) {
					DBBrowserNode c = null;
					for (int i = indexStart; i < indexEnd && i < rnSize; i++) {
						jg.writeStartObject();
						c = cache.cn.get(i);
						jg.writeStringField("t", c.type == Type.CONTAINER ? "f" : "d");
						jg.writeStringField("i", c.id);
						jg.writeNumberField("s", c.state);
						if (c.lastindexingDate == null) {
							jg.writeNullField("d");
						} else {
							jg.writeNumberField("d", c.lastindexingDate.getTime());
						}
						jg.writeEndObject();
					}
				}
				jg.writeEndArray();

			} else {

				final List<DBBrowserNode> filteredCache = new ArrayList<>();
				final boolean fetchErrors = states.contains(Integer.toString(State.PSEUDO_STATE_ERROR_RANGE_START));
				for (final DBBrowserNode dbn : cache.cn)
					if (states.contains(Integer.toString(dbn.state)) || fetchErrors && dbn.state >= State.PSEUDO_STATE_ERROR_RANGE_START) {
						filteredCache.add(dbn);
					}
				final int filteredRnSize = filteredCache.size();

				jg.writeNumberField("page", page);
				jg.writeStringField("state", state);
				jg.writeNumberField("ChildrenCount", filteredRnSize);
				jg.writeBooleanField("HasPrev", page > 0);
				jg.writeBooleanField("HasNext", indexEnd < filteredRnSize);
				if (searchedId != null) {
					jg.writeStringField("SearchedFor", searchedId);
				}

				jg.writeArrayFieldStart("children");
				if (indexStart < filteredRnSize) {
					DBBrowserNode c = null;
					for (int i = indexStart; i < indexEnd && i < filteredRnSize; i++) {
						jg.writeStartObject();
						c = filteredCache.get(i);
						jg.writeStringField("t", c.type == Type.CONTAINER ? "f" : "d");
						jg.writeStringField("i", c.id);
						jg.writeNumberField("s", c.state);
						if (c.lastindexingDate == null) {
							jg.writeNullField("d");
						} else {
							jg.writeNumberField("d", c.lastindexingDate.getTime());
						}
						jg.writeEndObject();
					}
				}
				jg.writeEndArray();

			}

			jg.writeEndObject();
		}
	}

	public void reloadState(final OutputStream sos, final String nodeId, final boolean isDir, final boolean exclude) throws SQLException, IOException {
		if (ttm.contains(new NodeRef(isDir, nodeId))) {
			if (isDir) {
				ttm.updateState(new ContainerNode(nodeId, null), exclude ? State.EXCLUDED : State.NEW, false);
			} else {
				ttm.updateStateAndDate(new DocumentNode(nodeId, null), exclude ? State.EXCLUDED : State.NEW, null, false);
			}
			final String pn = ttm.__getParentNodeId(new NodeRef(isDir, nodeId));
			loadData(pn, false);
			findChild(sos, pn, nodeId);
		}
	}

	public void commit() throws SQLException {
		synchronized (LOCK) {
			CACHE_ID = null;
		}
		ttm.commit();
		loadData(null, false);
	}

	@Override
	public void close() throws Exception {
		ttm.close();
	}

	private final class Cache {

		final List<DBBrowserNode> cn;
		final List<String> pn;

		Cache(final List<DBBrowserNode> cn, final List<String> pn) {
			super();
			this.cn = cn;
			this.pn = pn;
		}

	}

}
