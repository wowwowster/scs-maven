package com.sword.gsa.spis.scs.push.databases;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import sword.common.utils.streams.StreamUtils;

import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.Node;
import com.sword.gsa.spis.scs.push.tree.Type;

public class AclHashStore extends PushDBConnection {

	static final String ACL_HASH_TBL_NAME = "ACL_HASHS";

	static final String ID = "I";
	static final String TYPE = "T";
	static final String LAST_INDEXED_ACL_HASH = "LIA";

	private static final String COUNT_LAST_INDEXED_ACL = String.format("SELECT COUNT(*) FROM %%s WHERE %s=? AND %s=?", TYPE, ID);
	private static final String GET_LAST_INDEXED_ACL = String.format("SELECT %s FROM %%s WHERE %s=? AND %s=?", LAST_INDEXED_ACL_HASH, TYPE, ID);
	private static final String ADD_LAST_INDEXED_ACL = String.format("INSERT INTO %%s (%s, %s, %s) VALUES (?, ?, ?)", TYPE, ID, LAST_INDEXED_ACL_HASH);
	private static final String UPDATE_LAST_INDEXED_ACL = String.format("UPDATE %%s SET %s=? WHERE %s=? AND %s=?", LAST_INDEXED_ACL_HASH, TYPE, ID);

	private static final String FETCH_TREETBL_IDS = String.format("SELECT %s, %s FROM %s", TreeTableManager.TYPE, TreeTableManager.ID, TreeTableManager.TREE_TBL_NAME);
	private static final String FETCH_ACLTBL_IDS = String.format("SELECT %s, %s FROM %s", TYPE, ID, ACL_HASH_TBL_NAME);

	private final PreparedStatement countLastIndexedAcl;
	private final PreparedStatement getLastIndexedAcl;
	private final PreparedStatement addLastIndexedAcl;
	private final PreparedStatement updateLastIndexedAcl;

	public AclHashStore(final PushConfig conf) throws SQLException, ClassNotFoundException {
		super(conf, AclHashStore.getTableName());

		countLastIndexedAcl = dbc.getPreparedStatement(String.format(COUNT_LAST_INDEXED_ACL, tableName));
		getLastIndexedAcl = dbc.getPreparedStatement(String.format(GET_LAST_INDEXED_ACL, tableName));
		addLastIndexedAcl = dbc.getPreparedStatement(String.format(ADD_LAST_INDEXED_ACL, tableName));
		updateLastIndexedAcl = dbc.getPreparedStatement(String.format(UPDATE_LAST_INDEXED_ACL, tableName));

		try (PushTableManager ptm = new PushTableManager(conf)) {
			final Date lacd = ptm.getLastAclCleanupDate();
			final long now = System.currentTimeMillis();
			if (lacd == null || now - lacd.getTime() > TimeUnit.DAYS.toMillis(90)) {
				
				LOG.info("Deleting obsolete entries from ACL hash store");

				final Collection<AclKey> allAcls = new HashSet<>();
				try (Statement st = dbc.getStatement()) {//1- fetch all
					try (ResultSet rs = st.executeQuery(FETCH_ACLTBL_IDS)) {
						while (rs.next()) allAcls.add(this.new AclKey(rs.getShort(1), rs.getString(2)));
					}
				}
				LOG.info("Fetched " + allAcls.size() + " ACL entries");

				try (Statement st = dbc.getStatement()) {//2- remove existing form list
					try (ResultSet rs = st.executeQuery(FETCH_TREETBL_IDS)) {
						while (rs.next()) allAcls.remove(this.new AclKey(rs.getShort(1), rs.getString(2)));
					}
				}
				LOG.info("Identified " + allAcls.size() + " obsolete ACL entries - removing");
				
				if (!allAcls.isEmpty()) {
					int count = 0;
					try (PreparedStatement pst = dbc.getPreparedStatement("DELETE FROM " + ACL_HASH_TBL_NAME + " WHERE " + TYPE + "=? AND " + ID + "=?")) {
						for (final AclKey ak : allAcls) {
							count++;
							pst.setShort(1, ak.type);
							pst.setString(2, ak.id);
							pst.addBatch();
							if (count % 500 == 0) pst.executeBatch();
						}
						if (count % 500 != 0) pst.executeBatch();
					}
				}
				LOG.info("ACL hash store cleanup complete");
				ptm.setLastAclCleanupDate(new Date(now));
			}
		}
	}

	private static String getTableName() {
		return ACL_HASH_TBL_NAME;
	}

	@Override
	protected void createTable() throws SQLException {
		final String q = new StringBuilder("CREATE TABLE ").append(tableName).append(" (").append(TYPE).append(" ").append(dbc.getDatatype(Types.SMALLINT).name).append(" NOT NULL, ").append(ID).append(" ").append(dbc.getDatatype(Types.VARCHAR).name)
			.append("(").append(PushDBConnection.INTERNAL_ID_VARCHAR_MAX_LENGTH).append(") NOT NULL, ").append(LAST_INDEXED_ACL_HASH).append(" ").append(dbc.getDatatype(Types.BLOB).name).append("(16), ").append("PRIMARY KEY (").append(TYPE).append(", ")
			.append(ID).append("))").toString();

		try (Statement st = dbc.getStatement()) {
			st.executeUpdate(q);
		}

		dbc.commit();
	}

	public byte[] getNodeLastIndexedAclHash(final Node n) throws SQLException, IOException {
		getLastIndexedAcl.setShort(1, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
		getLastIndexedAcl.setString(2, n.id);
		try (ResultSet rs = getLastIndexedAcl.executeQuery()) {
			if (rs.next()) {
				final Blob blob = rs.getBlob(1);
				if (blob == null) return null;
				else {
					final ByteArrayOutputStream os = new ByteArrayOutputStream(16);
					StreamUtils.transferBytes(blob.getBinaryStream(), os);
					return os.toByteArray();
				}
			}
		}
		return null;
	}

	public void setNodeLastIndexedAclHash(final Node n, final byte[] aclHash) throws SQLException {
		countLastIndexedAcl.setShort(1, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
		countLastIndexedAcl.setString(2, n.id);
		try (ResultSet rs = countLastIndexedAcl.executeQuery()) {
			rs.next();
			if (rs.getInt(1) > 0) {
				updateLastIndexedAcl.setBlob(1, aclHash == null ? null : new ByteArrayInputStream(aclHash));
				updateLastIndexedAcl.setShort(2, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
				updateLastIndexedAcl.setString(3, n.id);
				updateLastIndexedAcl.executeUpdate();
			} else {
				addLastIndexedAcl.setShort(1, n instanceof ContainerNode ? Type.CONTAINER : Type.DOCUMENT);
				addLastIndexedAcl.setString(2, n.id);
				addLastIndexedAcl.setBlob(3, aclHash == null ? null : new ByteArrayInputStream(aclHash));
				addLastIndexedAcl.executeUpdate();
			}
		}
	}

	@Override
	public void close() throws SQLException {
		countLastIndexedAcl.close();
		getLastIndexedAcl.close();
		addLastIndexedAcl.close();
		updateLastIndexedAcl.close();
		super.close();
	}
	
	private class AclKey {
		
		final short type;
		final String id;
		
		public AclKey(short type, String id) {
			super();
			this.type = type;
			this.id = id;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof AclKey) return (type==((AclKey)obj).type) && id.equals(((AclKey)obj).id);
			else return false;
		}
		
		@Override
		public int hashCode() {
			if (type == Type.CONTAINER) return id.hashCode();
			else return -id.hashCode();
		}
		
	}
}