package com.sword.gsa.spis.scs.push.databases;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.DateUtils;
import sword.common.utils.streams.StreamUtils;
import sword.gsa.xmlfeeds.builder.acl.Permission;

import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;
import com.sword.gsa.spis.scs.push.tree.Node;
import com.sword.gsa.spis.scs.push.tree.NodeRef;
import com.sword.gsa.spis.scs.push.tree.State;
import com.sword.gsa.spis.scs.push.tree.Type;

/**
 * Methods and fields prefixed with __ are intended for the {@link DBBrowser}.
 */
public class TreeTableManager extends PushDBConnection {

	private static final int SMALL_BATCH_UPDATE_SIZE = 100;// Used when updating ACL Blob
	private static final int LARGE_BATCH_UPDATE_SIZE = 500;// Used for any other batch update

	static final String TREE_TBL_NAME = "TREE";

	static final String ID = "I";
	static final String TYPE = "T";
	static final String PARENT_ID = "PI";
	static final String STATE = "S";
	static final String LAST_INDEXING_DATE = "LID";
	static final String ACL = "A";

	private static final String CHECK_NODE_EXISTS = new StringBuilder("SELECT COUNT(*) FROM %s WHERE ").append(TYPE).append("=? AND ").append(ID).append("=?").toString();
	private static final String ADD_NODE = String.format("INSERT INTO %%s (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?)", TYPE, ID, PARENT_ID, STATE, ACL);
	private static final String UPDATE_NODE = String.format("UPDATE %%s SET %s=?, %s=?, %s=? WHERE %s=? AND %s=?", PARENT_ID, STATE, ACL, TYPE, ID);

	private static final String REMOVE_NODE = new StringBuilder("DELETE FROM %s WHERE ").append(TYPE).append("=? AND ").append(ID).append("=?").toString();

	private static final String GET_CHILD_NODES_IDS = String.format("SELECT %s, %s FROM %%s WHERE %s=?", TYPE, ID, PARENT_ID);
	private static final String GET_CHILD_NODES = String.format("SELECT %s, %s, %s FROM %%s WHERE %s=?", TYPE, ID, ACL, PARENT_ID);
	private static final String GET_CHILD_NODES_WITH_STATE = String.format("SELECT %s, %s, %s, %s FROM %%s WHERE %s=?", TYPE, ID, STATE, ACL, PARENT_ID);

	private static final String GET_STATE = String.format("SELECT %s FROM %%s WHERE %s=? AND %s=?", STATE, TYPE, ID);
	private static final String UPDATE_STATE = String.format("UPDATE %%s SET %s=? WHERE %s=? AND %s=?", STATE, TYPE, ID);
	private static final String UPDATE_STATE_AND_PERMS = String.format("UPDATE %%s SET %s=?, %s=? WHERE %s=? AND %s=?", STATE, ACL, TYPE, ID);

	private static final String GET_LAST_INDEXING_DATE = String.format("SELECT %s FROM %%s WHERE %s=? AND %s=?", LAST_INDEXING_DATE, TYPE, ID);

	private static final String UPDATE_STATE_AND_LAST_INDEXING_DATE = String.format("UPDATE %%s SET %s=?, %s=? WHERE %s=? AND %s=?", STATE, LAST_INDEXING_DATE, TYPE, ID);

	private static final String __GET_ROOT_NODE_CHILDREN = String.format("SELECT %s, %s, %s, %s FROM %%s WHERE %s IS NULL", TYPE, ID, STATE, LAST_INDEXING_DATE, PARENT_ID);
	private static final String __GET_CHILD_NODES = String.format("SELECT %s, %s, %s, %s FROM %%s WHERE %s=?", TYPE, ID, STATE, LAST_INDEXING_DATE, PARENT_ID);
	private static final String __GET_PARENT_ID = String.format("SELECT %s FROM %%s WHERE %s=? AND %s=?", PARENT_ID, TYPE, ID);

	private static String getTableName(final PushType pt) {
		return TREE_TBL_NAME;
	}

	private final PreparedStatement checkNodeExists;
	private final PreparedStatement addNode;
	private final PreparedStatement updateNode;

	private final PreparedStatement removeNode;

	private final PreparedStatement getChildNodesIds;
	private final PreparedStatement getChildNodes;
	private final PreparedStatement getChildNodesWithState;

	private final PreparedStatement getState;
	private final PreparedStatement updateState;
	private final PreparedStatement updateStateAndPerms;

	private final PreparedStatement getLastIndexingDate;

	private final PreparedStatement updateStateAndLastIndexingDate;

	private final PreparedStatement __getRootNodeChildren;
	private final PreparedStatement __getChildNodes;
	private final PreparedStatement __getParentId;

	public TreeTableManager(final PushConfig conf, final boolean preliminaryChecks) throws SQLException, ClassNotFoundException, IOException {
		super(conf, TreeTableManager.getTableName(conf.pushType));

		if (preliminaryChecks) {

			checkMissingColumn();

			if ("true".equals(System.getProperty("sword.indexer.MigrateOldData", "false"))) {
				migrateOldData();
			}

			checlAclFormat();

		}

		checkNodeExists = dbc.getPreparedStatement(String.format(CHECK_NODE_EXISTS, tableName));
		addNode = dbc.getPreparedStatement(String.format(ADD_NODE, tableName));
		updateNode = dbc.getPreparedStatement(String.format(UPDATE_NODE, tableName));

		removeNode = dbc.getPreparedStatement(String.format(REMOVE_NODE, tableName));

		getChildNodesIds = dbc.getPreparedStatement(String.format(GET_CHILD_NODES_IDS, tableName));
		getChildNodes = dbc.getPreparedStatement(String.format(GET_CHILD_NODES, tableName));
		getChildNodesWithState = dbc.getPreparedStatement(String.format(GET_CHILD_NODES_WITH_STATE, tableName));

		getState = dbc.getPreparedStatement(String.format(GET_STATE, tableName));
		updateState = dbc.getPreparedStatement(String.format(UPDATE_STATE, tableName));
		updateStateAndPerms = dbc.getPreparedStatement(String.format(UPDATE_STATE_AND_PERMS, tableName));

		getLastIndexingDate = dbc.getPreparedStatement(String.format(GET_LAST_INDEXING_DATE, tableName));
		updateStateAndLastIndexingDate = dbc.getPreparedStatement(String.format(UPDATE_STATE_AND_LAST_INDEXING_DATE, tableName));

		__getRootNodeChildren = dbc.getPreparedStatement(String.format(__GET_ROOT_NODE_CHILDREN, tableName));
		__getChildNodes = dbc.getPreparedStatement(String.format(__GET_CHILD_NODES, tableName));
		__getParentId = dbc.getPreparedStatement(String.format(__GET_PARENT_ID, tableName));

	}

	private void migrateOldData() throws SQLException, ClassNotFoundException {
		LOG.warn("Old data migration is requested");

		boolean isEmpty = false;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(new StringBuilder("SELECT COUNT(*) FROM ").append(tableName).toString())) {
				isEmpty = rs.next() && rs.getInt(1) == 0;
			}
		}

		if (isEmpty) {

			LOG.warn("Current table is empty - checking old data table existence");

			final String oldDbPath = System.getProperty("sword.indexer.OldDataPath", "");
			if (StringUtils.isNullOrEmpty(oldDbPath)) {
				LOG.warn("Skipping data migration as specified \"OldDataPath\" was not specified");
			} else {
				final java.io.File oldDbFile = new java.io.File(oldDbPath);
				if (oldDbFile.exists() && oldDbFile.isDirectory()) {

					LOG.warn("Attempting to connect to the old DB");
					try (DataBaseConnection oldDbc = new DataBaseConnection(DBType.JAVA_DB, oldDbFile.getAbsolutePath(), "gpushuser", "drow$$ap")) {
						oldDbc.connect(true);
						LOG.warn("Connection successful - retrieving table names");
						final String getTblNames = new StringBuilder("SELECT ").append(TBL_NAME_COL).append(" FROM ").append(SYSTBL_NAME).toString();
						final List<String> tblNames = new ArrayList<>();
						try (Statement st = oldDbc.getStatement()) {
							try (ResultSet rs = st.executeQuery(getTblNames)) {
								while (rs.next()) {
									final String tblName = rs.getString(1).toUpperCase();
									if (tblName.startsWith("TREE_") && !tblName.endsWith("_DEL")) {
										tblNames.add(tblName);
									}
								}
							}
						}

						if (tblNames.size() == 1) {
							final String oldTblName = tblNames.get(0);
							LOG.warn("Found table " + oldTblName + " - proceeding with migration");
							final String countRecords = new StringBuilder("SELECT COUNT(*) FROM ").append(oldTblName).toString();
							int numRecords = -1;
							try (Statement st = oldDbc.getStatement()) {
								try (ResultSet rs = st.executeQuery(countRecords)) {
									if (rs.next()) {
										numRecords = rs.getInt(1);
									}
								}
							}
							LOG.warn("Found " + numRecords + " records in table " + oldTblName);
							if (numRecords > 0) {
								long start;
								int news = 0;
								int completed = 0;
								int excluded = 0;
								int errors = 0;
								try (PreparedStatement insert = dbc.getPreparedStatement(String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?)", tableName, TYPE, ID, PARENT_ID, STATE, ACL))) {
									try (Statement st = oldDbc.getStatement()) {
										try (ResultSet rs = st.executeQuery("SELECT T, I, PI, S, A FROM " + oldTblName)) {
											LOG.warn("Starting data migration");
											int added = 0;
											start = System.currentTimeMillis();
											while (rs.next()) {
												insert.setShort(1, rs.getShort(1));
												insert.setString(2, rs.getString(2));
												insert.setString(3, rs.getString(3));
												final int state = rs.getInt(4);
												switch (state) {
													case State.COMPLETE:
														completed++;
														break;
													case State.EXCLUDED:
														excluded++;
														break;
													case State.NEW:
														news++;
														break;
													default:
														errors++;
														break;
												}
												insert.setInt(4, state);
												insert.setBlob(5, rs.getBlob(5));
												insert.addBatch();
												added++;
												if (added % SMALL_BATCH_UPDATE_SIZE == 0) {
													insert.executeBatch();
													LOG.warn("Processed " + added + " records in " + DateUtils.toReadableTimeSpan(System.currentTimeMillis() - start));
												}
											}
											if (added % SMALL_BATCH_UPDATE_SIZE != 0) {
												insert.executeBatch();
											}
										}
									}
								}
								final StringBuilder sb = new StringBuilder("Processed all records in ").append(DateUtils.toReadableTimeSpan(System.currentTimeMillis() - start)).append(" - committing changes. Reloaded:\n");
								sb.append("\t- ").append(Integer.toString(news)).append(" new nodes\n");
								sb.append("\t- ").append(Integer.toString(completed)).append(" completed nodes\n");
								sb.append("\t- ").append(Integer.toString(excluded)).append(" excluded nodes\n");
								sb.append("\t- ").append(Integer.toString(errors)).append(" nodes in error state\n");
								LOG.warn(sb.toString());
								dbc.commit();
								LOG.warn("Data migration completed");
							}
						} else {
							LOG.warn("Found " + tblNames.size() + " tables - data migration cannot be performed - aborting");
						}
					}

				} else {
					LOG.warn("Skipping data migration as specified \"OldDataPath\" refers to a non-existing directory or to a file: " + oldDbPath);
				}
			}

		} else {
			LOG.warn("Skipping data migration as current table is not empty");
		}
	}

	@SuppressWarnings("deprecation")
	private void checlAclFormat() throws SQLException, ClassNotFoundException, IOException {

		LOG.info("Checking ACL format");

		boolean convertionNeeded = false;
		boolean aclResetNeeded = false;
		try (final Statement st = dbc.getStatement()) {
			try (final ResultSet rs = st.executeQuery(new StringBuilder("SELECT ").append(ACL).append(" FROM ").append(tableName).append(" WHERE ").append(ACL).append(" IS NOT NULL").toString())) {
				while (rs.next()) {
					final Blob acl = rs.getBlob(1);
					if (acl != null) {
						@SuppressWarnings("resource")
						final InputStream bs = acl.getBinaryStream();
						if (bs != null) {
							try {
								final ByteArrayOutputStream os = new ByteArrayOutputStream();
								StreamUtils.transferBytes(bs, os);
								final byte[] serPerm = os.toByteArray();
								if (!Permission.isSerializedPublicDocPerm(serPerm)) {
									LOG.info("Found non-null ACL - Checking format");
									Permission.deserialize(serPerm);
									convertionNeeded = false;
									break;
								}
							} catch (final ZipException e) {
								if ("Not in GZIP format".equals(e.getMessage())) {
									convertionNeeded = true;
								} else {
									aclResetNeeded = true;
								}
								break;
							} catch (final Throwable t) {
								aclResetNeeded = true;
								break;
							}
						}
					}
				}
			}
		}

		if (convertionNeeded) {
			LOG.info("Internal database uses the legacy ACL format - converting to enhanced format");
			try (final Statement st = dbc.getStatement(); final PreparedStatement update = dbc.getPreparedStatement(new StringBuilder("UPDATE ").append(tableName).append(" SET ").append(ACL).append("=? WHERE ").append(TYPE).append("=? AND ").append(ID)
				.append("=? ").toString()); final ResultSet rs = st.executeQuery(new StringBuilder("SELECT ").append(TYPE).append(", ").append(ID).append(", ").append(ACL).append(" FROM ").append(tableName).append(" WHERE ").append(ACL).append(" IS NOT NULL")
				.toString())) {
				LOG.info("Processing");
				final long start = System.currentTimeMillis();
				short type = -1;
				String id = null;
				Blob acl = null;
				InputStream bs = null;
				int processed = 0;
				while (rs.next()) {
					type = rs.getShort(1);
					id = rs.getString(2);
					acl = rs.getBlob(3);
					if (acl != null) {
						@SuppressWarnings("resource")
						InputStream _bs = acl.getBinaryStream();
						bs = _bs;
						update.setBlob(1, bs == null ? null : new ByteArrayInputStream(Permission.serialize(Permission._deserializeLegacy(bs))));
						update.setShort(2, type);
						update.setString(3, id);
						update.addBatch();
						processed++;
						if (processed % SMALL_BATCH_UPDATE_SIZE == 0) {
							update.executeBatch();
						}
						if (processed % 50_000 == 0) {
							LOG.info("Processed " + processed + " items in " + DateUtils.toReadableTimeSpan(System.currentTimeMillis() - start));
						}
					}
				}
				if (processed % SMALL_BATCH_UPDATE_SIZE != 0) {
					update.executeBatch();
					LOG.info("Processed all " + processed + " items in " + DateUtils.toReadableTimeSpan(System.currentTimeMillis() - start));
				}
			}
			commit();
		} else if (aclResetNeeded) {
			LOG.info("Internal database uses an obsolete ACL format - resetting ACLs");
			try (final Statement st = dbc.getStatement()) {
				final int c = st.executeUpdate(new StringBuilder("UPDATE ").append(tableName).append(" SET ").append(ACL).append("=NULL").toString());
				LOG.info("Reset " + c + " malformed ACLs");
			}
			commit();
		} else {
			LOG.info("ACL format is correct");
		}

	}

	private void checkMissingColumn() throws SQLException {
		boolean addLastIndexDate = false;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(new StringBuilder("SELECT ").append(LAST_INDEXING_DATE).append(" FROM ").append(tableName).append(" WHERE ").append(TYPE).append("=").append(Type.CONTAINER).append(" AND ").append(ID).append("='abcde'")
				.toString())) {
				rs.next();
			} catch (final SQLException e) {
				if ("42X04".equals(e.getSQLState())) {
					LOG.warn("The table uses an old data model (last-indexing-date missing) - altering table");
					addLastIndexDate = true;
				} else throw e;
			}
		}
		if (addLastIndexDate) {
			try (Statement st = dbc.getStatement()) {
				st.executeUpdate(new StringBuilder("ALTER TABLE ").append(tableName).append(" ADD COLUMN ").append(LAST_INDEXING_DATE).append(" ").append(dbc.getDatatype(Types.TIMESTAMP).name).toString());
			}
			commit();
		}
	}

	@Override
	protected void createTable() throws SQLException {
		final String vc = dbc.getDatatype(Types.VARCHAR).name;
		final String q = new StringBuilder("CREATE TABLE ").append(tableName).append(" (").append(TYPE).append(" ").append(dbc.getDatatype(Types.SMALLINT).name).append(" NOT NULL, ").append(ID).append(" ").append(vc).append("(")
			.append(PushDBConnection.INTERNAL_ID_VARCHAR_MAX_LENGTH).append(") NOT NULL, ").append(PARENT_ID).append(" ").append(vc).append("(").append(PushDBConnection.INTERNAL_ID_VARCHAR_MAX_LENGTH).append("), ").append(STATE).append(" ")
			.append(dbc.getDatatype(Types.INTEGER).name).append(", ").append(LAST_INDEXING_DATE).append(" ").append(dbc.getDatatype(Types.TIMESTAMP).name).append(", ").append(ACL).append(" ").append(dbc.getDatatype(Types.BLOB).name).append("(512K), ")
			.append("PRIMARY KEY (").append(TYPE).append(", ").append(ID).append("))").toString();

		final String q_index = new StringBuilder("CREATE INDEX PARINDEX_" + tableName + " ON ").append(tableName).append("(").append(PARENT_ID).append(")").toString();

		try (Statement st = dbc.getStatement()) {
			st.executeUpdate(q);
		}
		try (Statement st = dbc.getStatement()) {
			st.executeUpdate(q_index);
		}
		dbc.commit();
	}

	/**
	 * Looks for any {@link Node} with a status that is not either {@link State#COMPLETE} or {@link State#EXCLUDED}
	 */
	public boolean isRecovering() throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement(String.format("SELECT COUNT(*) FROM %s WHERE %s > ?", tableName, STATE))) {
			st.setInt(1, State.COMPLETE);
			try (ResultSet rs = st.executeQuery()) {
				return rs.next() && rs.getInt(1) > 0;
			}
		}
	}

	public boolean isEmpty() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			return rs.next() && rs.getInt(1) < 1;
		}
	}

	public List<ContainerNode> getRootNodes() throws SQLException {
		final List<ContainerNode> rootNodes = new ArrayList<>();
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(new StringBuilder("SELECT ").append(ID).append(", ").append(STATE).append(" FROM ").append(tableName).append(" WHERE ").append(PARENT_ID).append(" IS NULL").toString())) {
				while (rs.next()) {
					final ContainerNode rn = new ContainerNode(rs.getString(1), null);
					rn.state = rs.getInt(2);
					rootNodes.add(rn);
				}
			}
		}
		return rootNodes;
	}

	public boolean contains(final Node n) throws SQLException {
		return contains(n instanceof ContainerNode, n.id);
	}

	public boolean contains(final NodeRef nr) throws SQLException {
		return contains(nr.isContainer, nr.id);
	}

	public boolean contains(final boolean isContainer, final String nodeId) throws SQLException {
		checkNodeExists.setShort(1, isContainer ? Type.CONTAINER : Type.DOCUMENT);
		checkNodeExists.setString(2, nodeId);
		try (ResultSet rs = checkNodeExists.executeQuery()) {
			return rs.next() && rs.getInt(1) == 1;
		}
	}

	public void reloadState(final Node n) throws SQLException {
		getState.setShort(1, n instanceof DocumentNode ? Type.DOCUMENT : Type.CONTAINER);
		getState.setString(2, n.id);
		try (ResultSet rs = getState.executeQuery()) {
			if (rs.next()) {
				n.state = rs.getInt(1);
			} else {
				n.state = State.NEW;
			}
		}
	}

	public void checkExclusionState(final Node n) throws SQLException {
		getState.setShort(1, n instanceof DocumentNode ? Type.DOCUMENT : Type.CONTAINER);
		getState.setString(2, n.id);
		try (ResultSet rs = getState.executeQuery()) {
			if (rs.next() && rs.getInt(1) == State.EXCLUDED) {
				n.state = State.EXCLUDED;
			}
		}
	}

	public void addNode(final ContainerNode n, final boolean commit) throws SQLException {
		addNode(n, Type.CONTAINER, commit);
	}

	public void addNode(final DocumentNode n, final boolean commit) throws SQLException {
		addNode(n, Type.DOCUMENT, commit);
	}

	private void addNode(final Node n, final short type, final boolean commit) throws SQLException {
		addNode.setShort(1, type);
		addNode.setString(2, n.id);
		addNode.setString(3, n.parent == null ? null : n.parent.id);
		addNode.setInt(4, State.NEW);
		addNode.setBlob(5, n.serPerm == null ? null : new ByteArrayInputStream(n.serPerm));
		addNode.executeUpdate();
		if (commit) {
			dbc.commit();
		}
	}

	public void addNode(final short type, final String id, final String parentId, final int state, final byte[] serPerm) throws SQLException {
		addNode.setShort(1, type);
		addNode.setString(2, id);
		addNode.setString(3, parentId == null ? null : parentId);
		addNode.setInt(4, state);
		addNode.setBlob(5, serPerm == null ? null : new ByteArrayInputStream(serPerm));
		addNode.executeUpdate();
	}

	public List<NodeRef> removeNodeAndChildren(final NodeRef nr) throws SQLException {
		final List<NodeRef> nodesToRemove = new ArrayList<>();
		final List<String> containers = new ArrayList<>();
		if (nr.isContainer) {
			containers.add(nr.id);
		} else {
			nodesToRemove.add(nr);
		}
		searchforIdsToRemove(nodesToRemove, containers);

		int removed = 0;
		for (final NodeRef cnr : nodesToRemove) {
			removeNode.setShort(1, cnr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
			removeNode.setString(2, cnr.id);
			removeNode.addBatch();
			removed++;
			if (removed % LARGE_BATCH_UPDATE_SIZE == 0) {
				removeNode.executeBatch();
			}
		}
		if (removed % LARGE_BATCH_UPDATE_SIZE != 0) {
			removeNode.executeBatch();
		}
		return nodesToRemove;
	}

	public void removeNode(final NodeRef deletedNode, final boolean commit) throws SQLException {
		removeNode.setShort(1, deletedNode.isContainer ? Type.CONTAINER : Type.DOCUMENT);
		removeNode.setString(2, deletedNode.id);
		removeNode.execute();
		if (commit) {
			dbc.commit();
		}
	}

	public List<NodeRef> removeNodeChildren(final NodeRef nr) throws SQLException {
		final List<NodeRef> nodesToRemove = new ArrayList<>();
		if (nr.isContainer) {
			final List<String> containers = new ArrayList<>();
			containers.add(nr.id);
			searchforIdsToRemove(nodesToRemove, containers);
			if (!nodesToRemove.isEmpty()) {
				nodesToRemove.remove(0);// first entry is the connector itself
			}

			int removed = 0;
			for (final NodeRef cnr : nodesToRemove) {
				removeNode.setShort(1, cnr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
				removeNode.setString(2, cnr.id);
				removeNode.addBatch();
				removed++;
				if (removed % LARGE_BATCH_UPDATE_SIZE == 0) {
					removeNode.executeBatch();
				}
			}
			if (removed % LARGE_BATCH_UPDATE_SIZE != 0) {
				removeNode.executeBatch();
			}
		}
		return nodesToRemove;
	}

	private void searchforIdsToRemove(final List<NodeRef> nodesToRemove, final List<String> containers) throws SQLException {
		while (!containers.isEmpty()) {
			final String id = containers.remove(0);
			nodesToRemove.add(new NodeRef(true, id));
			getChildNodes.setString(1, id);
			try (ResultSet rs = getChildNodes.executeQuery()) {
				while (rs.next()) {
					final short ct = rs.getShort(1);
					final String cid = rs.getString(2);
					nodesToRemove.add(new NodeRef(ct == Type.CONTAINER, cid));
				}
			}
		}
	}

	/**
	 * Adds node's children to the table then sets the node state to {@link State#EXPLORED} in order to indicate exploration is not necessary
	 */
	public void saveNewChildren(final ContainerNode parent) throws SQLException {
		try {
			final String parentId = parent.id;

			int added = 0;
			int updated = 0;

			NodeRef nr = null;
			for (final Node cn : parent.children) {
				nr = new NodeRef(cn instanceof ContainerNode, cn.id);
				if (this.contains(nr.isContainer, nr.id)) {
					updateNode.setString(1, parentId);
					updateNode.setInt(2, State.NEW);
					updateNode.setBlob(3, cn.serPerm == null ? null : new ByteArrayInputStream(cn.serPerm));
					updateNode.setShort(4, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
					updateNode.setString(5, nr.id);
					updateNode.addBatch();
					updated++;
					if (updated % SMALL_BATCH_UPDATE_SIZE == 0) {
						updateNode.executeBatch();
					}
				} else {
					addNode.setShort(1, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
					addNode.setString(2, nr.id);
					addNode.setString(3, parent.id);
					addNode.setInt(4, State.NEW);
					addNode.setBlob(5, cn.serPerm == null ? null : new ByteArrayInputStream(cn.serPerm));
					addNode.addBatch();
					added++;
					if (added % SMALL_BATCH_UPDATE_SIZE == 0) {
						addNode.executeBatch();
					}
				}

			}

			if (updated % SMALL_BATCH_UPDATE_SIZE != 0) {
				updateNode.executeBatch();
			}
			if (added % SMALL_BATCH_UPDATE_SIZE != 0) {
				addNode.executeBatch();
			}

			updateState(parent, State.COMPLETE, true);

		} catch (final SQLException e) {
			LOG.warn("Children storage failed. List was: " + parent.children);
			throw e;
		}

	}

	/**
	 * 1- Adds node's children to the table 2- Sets the node state to {@link State#EXPLORED} in order to indicate exploration is not necessary 3- Returns the list of previous children that no longer
	 * exist (does not commit because the removal of old children will happen right after)
	 */
	public List<NodeRef> saveAllChildren(final ContainerNode parent) throws SQLException {

		final List<NodeRef> deletedChildren = new ArrayList<>();

		try {
			final String parentId = parent.id;

			getChildNodesIds.setString(1, parentId);
			try (ResultSet rs = getChildNodesIds.executeQuery()) {
				while (rs.next()) {
					deletedChildren.add(new NodeRef(rs.getShort(1) == Type.CONTAINER, rs.getString(2)));
				}
			}

			int added = 0;
			int updated = 0;

			NodeRef nr = null;
			for (final Node cn : parent.children) {
				nr = new NodeRef(cn instanceof ContainerNode, cn.id);
				deletedChildren.remove(nr);
				if (contains(nr)) {
					updateNode.setString(1, parentId);
					updateNode.setInt(2, State.NEW);
					updateNode.setBlob(3, cn.serPerm == null ? null : new ByteArrayInputStream(cn.serPerm));
					updateNode.setShort(4, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
					updateNode.setString(5, nr.id);
					updateNode.addBatch();
					updated++;
					if (updated % SMALL_BATCH_UPDATE_SIZE == 0) {
						updateNode.executeBatch();
					}
				} else {
					addNode.setShort(1, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
					addNode.setString(2, nr.id);
					addNode.setString(3, parent.id);
					addNode.setInt(4, State.NEW);
					addNode.setBlob(5, cn.serPerm == null ? null : new ByteArrayInputStream(cn.serPerm));
					addNode.addBatch();
					added++;
					if (added % SMALL_BATCH_UPDATE_SIZE == 0) {
						addNode.executeBatch();
					}
				}
			}

			if (updated % SMALL_BATCH_UPDATE_SIZE != 0) {
				updateNode.executeBatch();
			}
			if (added % SMALL_BATCH_UPDATE_SIZE != 0) {
				addNode.executeBatch();
			}

			updateState(parent, State.COMPLETE, false);
		} catch (final SQLException e) {
			LOG.warn("Children storage failed. List was: " + parent.children);
			throw e;
		}

		return deletedChildren;
	}

	public void updateNodeFully(final Node n, final int state, final boolean commit) throws SQLException {
		updateNode.setString(1, n.parent == null ? null : n.parent.id);
		updateNode.setInt(2, state);
		updateNode.setBlob(3, n.serPerm == null ? null : new ByteArrayInputStream(n.serPerm));
		updateNode.setShort(4, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
		updateNode.setString(5, n.id);
		updateNode.executeUpdate();
		if (commit) {
			dbc.commit();
		}
	}

	public void updateState(final Node n, final int state, final boolean commit) throws SQLException {
		updateState.setInt(1, state);
		updateState.setShort(2, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
		updateState.setString(3, n.id);
		updateState.executeUpdate();
		if (commit) {
			dbc.commit();
		}
	}

	public void updateStateAndDate(final DocumentNode dn, final int state, final Date lid, final boolean commit) throws SQLException {
		updateStateAndLastIndexingDate.setInt(1, state);
		if (lid == null) {
			updateStateAndLastIndexingDate.setNull(2, Types.TIMESTAMP);
		} else {
			updateStateAndLastIndexingDate.setTimestamp(2, new Timestamp(lid.getTime()));
		}
		updateStateAndLastIndexingDate.setShort(3, Type.DOCUMENT);
		updateStateAndLastIndexingDate.setString(4, dn.id);
		updateStateAndLastIndexingDate.executeUpdate();
		if (commit) {
			dbc.commit();
		}
	}

	public void updateStatesAndDates(final Map<DocumentNode, Integer> states, final Map<DocumentNode, Date> dates) throws SQLException {

		final Set<DocumentNode> docNodes = states.keySet();
		int statesAndLastIndexingDatesAdded = 0;
		int statesAdded = 0;
		for (final DocumentNode dn : docNodes) {
			if (dates.containsKey(dn)) {
				updateStateAndLastIndexingDate.setInt(1, states.get(dn));
				final Date lid = dates.get(dn);
				if (lid == null) {
					updateStateAndLastIndexingDate.setNull(2, Types.TIMESTAMP);
				} else {
					updateStateAndLastIndexingDate.setTimestamp(2, new Timestamp(lid.getTime()));
				}
				updateStateAndLastIndexingDate.setShort(3, Type.DOCUMENT);
				updateStateAndLastIndexingDate.setString(4, dn.id);
				updateStateAndLastIndexingDate.addBatch();
				statesAndLastIndexingDatesAdded++;
				if (statesAndLastIndexingDatesAdded % LARGE_BATCH_UPDATE_SIZE == 0) {
					updateStateAndLastIndexingDate.executeBatch();
				}
			} else {
				updateState.setInt(1, states.get(dn));
				updateState.setShort(2, Type.DOCUMENT);
				updateState.setString(3, dn.id);
				updateState.addBatch();
				statesAdded++;
				if (statesAdded % LARGE_BATCH_UPDATE_SIZE == 0) {
					updateState.executeBatch();
				}
			}
		}

		if (statesAndLastIndexingDatesAdded % LARGE_BATCH_UPDATE_SIZE != 0) {
			updateStateAndLastIndexingDate.executeBatch();
		}
		if (statesAdded % LARGE_BATCH_UPDATE_SIZE != 0) {
			updateState.executeBatch();
		}

		dbc.commit();
	}

	public void updateStateAndPermissions(final Node n, final int state, final byte[] serPerm, final boolean commit) throws SQLException {
		updateStateAndPerms.setInt(1, state);
		updateStateAndPerms.setBlob(2, serPerm == null ? null : new ByteArrayInputStream(serPerm));
		updateStateAndPerms.setShort(3, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
		updateStateAndPerms.setString(4, n.id);
		updateStateAndPerms.executeUpdate();
		if (commit) {
			dbc.commit();
		}
	}

	public Date getLastIndexingDate(final DocumentNode doc) throws SQLException {
		getLastIndexingDate.setShort(1, Type.DOCUMENT);
		getLastIndexingDate.setString(2, doc.id);
		try (ResultSet rs = getLastIndexingDate.executeQuery()) {
			if (rs.next()) return rs.getTimestamp(1);
		}
		return null;
	}

	public void reloadChildren(final ContainerNode parent) throws SQLException, IOException {
		boolean isCont;
		String id;
		int state;
		Blob acl;

		getChildNodesWithState.setString(1, parent.id);

		Node child = null;

		try (ResultSet rs = getChildNodesWithState.executeQuery()) {
			while (rs.next()) {
				isCont = rs.getShort(1) == Type.CONTAINER;
				id = rs.getString(2);
				state = rs.getInt(3);
				acl = rs.getBlob(4);

				if (isCont) {
					if (acl == null) {
						child = new ContainerNode(id, parent);
					} else {
						child = new ContainerNode(id, parent, acl.getBinaryStream());
					}
					child.state = state;
					parent.children.add(child);
				} else {
					if (acl == null) {
						child = new DocumentNode(id, parent);
					} else {
						child = new DocumentNode(id, parent, acl.getBinaryStream());
					}
					child.state = state;
					parent.children.add(child);
				}
			}
		}
	}

	public void updateChildNodesState(final ContainerNode parent, final int state) throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement(String.format("UPDATE %s SET %s=? WHERE %s=?", tableName, STATE, PARENT_ID))) {
			st.setInt(1, state);
			st.setString(2, parent.id);
			st.executeUpdate();
		}
	}

	public Collection<String> getDocIds() throws SQLException {
		final Collection<String> ids = new HashSet<>();
		try (PreparedStatement st = dbc.getPreparedStatement("SELECT " + ID + " FROM " + tableName + " WHERE " + TYPE + "=?")) {
			st.setShort(1, Type.DOCUMENT);
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getString(1));
				}
			}
		}
		return ids;
	}

	public List<DBBrowserNode> __getChildNodes(final String parentNodeId) throws SQLException {
		final List<DBBrowserNode> res = new ArrayList<>();
		final PreparedStatement ps;
		if (StringUtils.isNullOrEmpty(parentNodeId)) {
			ps = __getRootNodeChildren;
		} else {
			ps = __getChildNodes;
			ps.setString(1, parentNodeId);
		}
		try (ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				res.add(new DBBrowserNode(rs.getShort(1), rs.getString(2), rs.getInt(3), rs.getTimestamp(4)));
			}
		}
		return res;
	}

	public String __getParentNodeId(final NodeRef nr) throws SQLException {
		__getParentId.setShort(1, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
		__getParentId.setString(2, nr.id);
		try (ResultSet rs = __getParentId.executeQuery()) {
			if (rs.next()) return rs.getString(1);
			else return null;
		}
	}

	public List<String> __getParentNodesIds(NodeRef nr) throws SQLException {
		final List<String> pids = new ArrayList<>();

		do {
			pids.add(nr.id);
			__getParentId.setShort(1, nr.isContainer ? Type.CONTAINER : Type.DOCUMENT);
			__getParentId.setString(2, nr.id);
			try (ResultSet rs = __getParentId.executeQuery()) {
				if (rs.next()) {
					final String pid = rs.getString(1);
					if (pid == null) {
						nr = null;
					} else {
						nr = new NodeRef(true, pid);
					}
				} else {
					nr = null;
				}
			}
		} while (nr != null);

		return pids;
	}

	@Override
	public void close() throws SQLException {
		checkNodeExists.close();
		addNode.close();
		updateNode.close();
		removeNode.close();
		getChildNodesIds.close();
		getChildNodes.close();
		getChildNodesWithState.close();
		getState.close();
		updateState.close();
		updateStateAndPerms.close();
		getLastIndexingDate.close();
		updateStateAndLastIndexingDate.close();
		__getRootNodeChildren.close();
		__getChildNodes.close();
		__getParentId.close();
		super.close();
	}
}