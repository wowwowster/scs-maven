package com.sword.gsa.spis.scs.push.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import com.sword.gsa.spis.scs.push.config.PushConfig;

public class PushTableManager extends PushDBConnection {

	private static final String TABLE_NAME = "GPUSHINFO";
	private static final String LAST_PUSH_DATE = "LPD";
	private static final String COMPLETED_ONCE = "CO";
	private static final String LAST_ACL_CLEANUP = "LAC";
	private static final String LAST_DELETION_PUSH_DATE = "LDPD";
	private static final String DELETION_PUSH_STATUS = "DPS";

	public PushTableManager(final PushConfig conf) throws ClassNotFoundException, SQLException {
		super(conf, TABLE_NAME);
		checkTableFormat();
	}

	private void checkTableFormat() throws SQLException {
		{
			boolean addCleanupColumn = false;
			try (Statement st = dbc.getStatement()) {
				try {
					try (ResultSet rs = st.executeQuery("SELECT " + LAST_ACL_CLEANUP + " FROM " + TABLE_NAME)) {}
				} catch (final SQLException e) {
					if ("42X04".equals(e.getSQLState())) {
						addCleanupColumn = true;
					} else throw e;
				}
			}
			if (addCleanupColumn) {
				try (Statement st = dbc.getStatement()) {
					st.executeUpdate("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + LAST_ACL_CLEANUP + " " + dbc.getDatatype(Types.TIMESTAMP).name);
				}
				dbc.commit();
			}
		}
		{
			boolean addDelPushColumn = false;
			try (Statement st = dbc.getStatement()) {
				try {
					try (ResultSet rs = st.executeQuery("SELECT " + LAST_DELETION_PUSH_DATE + " FROM " + TABLE_NAME)) {}
				} catch (final SQLException e) {
					if ("42X04".equals(e.getSQLState())) {
						addDelPushColumn = true;
					} else throw e;
				}
			}
			if (addDelPushColumn) {
				try (Statement st = dbc.getStatement()) {
					st.executeUpdate("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + LAST_DELETION_PUSH_DATE + " " + dbc.getDatatype(Types.TIMESTAMP).name);
				}
				dbc.commit();
			}
		}
		{
			boolean addDelPushStatusCol = false;
			try (Statement st = dbc.getStatement()) {
				try {
					try (ResultSet rs = st.executeQuery("SELECT " + DELETION_PUSH_STATUS + " FROM " + TABLE_NAME)) {}
				} catch (final SQLException e) {
					if ("42X04".equals(e.getSQLState())) {
						addDelPushStatusCol = true;
					} else throw e;
				}
			}
			if (addDelPushStatusCol) {
				try (Statement st = dbc.getStatement()) {
					st.executeUpdate("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + DELETION_PUSH_STATUS + " " + dbc.getDatatype(Types.BOOLEAN).name);
				}
				setLastDeletionPushDate(new Date(0L));//will commit transaction
			}
		}
	}

	@Override
	protected void createTable() throws SQLException {
		try (Statement st = dbc.getStatement()) {
			st.executeUpdate("CREATE TABLE " + TABLE_NAME + " (" + LAST_PUSH_DATE + " " + dbc.getDatatype(Types.TIMESTAMP).name + ", " + COMPLETED_ONCE + " " + dbc.getDatatype(Types.BOOLEAN).name + ", " + LAST_ACL_CLEANUP + " " + dbc.getDatatype(Types.TIMESTAMP).name + ", " + LAST_DELETION_PUSH_DATE + " " + dbc.getDatatype(Types.TIMESTAMP).name + ")");
		}
		try (PreparedStatement st = dbc.getPreparedStatement("INSERT INTO " + TABLE_NAME + " (" + LAST_PUSH_DATE + ", " + LAST_DELETION_PUSH_DATE + ", " + COMPLETED_ONCE + ") VALUES (?, ?, ?)")) {
			Timestamp now = new Timestamp(System.currentTimeMillis());
			st.setTimestamp(1, now);
			st.setTimestamp(2, now);//Set last push date to <now> because initial push will not feed any deleted entry
			st.setBoolean(3, false);
			st.executeUpdate();
		}
		commit();
	}

	public boolean isInitialPush() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT " + COMPLETED_ONCE + " FROM " + TABLE_NAME)) {
			if (rs.next()) return !rs.getBoolean(1);
			else throw new IllegalStateException("GPush table is not correctly formed. Delete the database and restart the push process");
		}
	}

	public Date getLastPushDate() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT " + LAST_PUSH_DATE + " FROM " + TABLE_NAME)) {
			if (rs.next()) return rs.getTimestamp(1);
			else throw new IllegalStateException("GPush table is not correctly formed. Delete the database and restart the push process");
		}
	}

	public Date getLastAclCleanupDate() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT " + LAST_ACL_CLEANUP + " FROM " + TABLE_NAME)) {
			if (rs.next()) {
				final Timestamp ts = rs.getTimestamp(1);
				if (ts == null) return new Date(0L);
				else return ts;
			} else return null;
		}
	}

	public Date getLastDeletionPushDate() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT " + LAST_DELETION_PUSH_DATE + " FROM " + TABLE_NAME)) {
			if (rs.next()) {
				final Timestamp ts = rs.getTimestamp(1);
				if (ts == null) return new Date(0L);
				else return ts;
			} else return null;
		}
	}

	public boolean hasUnfinishedDeletionPush() throws SQLException {
		try (Statement st = dbc.getStatement(); ResultSet rs = st.executeQuery("SELECT " + DELETION_PUSH_STATUS + " FROM " + TABLE_NAME)) {
			if (rs.next()) return rs.getBoolean(1);
			else return false;
		}
	}

	public void setLastPushDate(final Date dolp) throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement("UPDATE " + TABLE_NAME + " SET " + LAST_PUSH_DATE + "=?")) {
			st.setTimestamp(1, new Timestamp(dolp.getTime()));
			st.executeUpdate();
		}
		commit();
	}

	public void setLastAclCleanupDate(final Date lastAclCleanupDate) throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement("UPDATE " + TABLE_NAME + " SET " + LAST_ACL_CLEANUP + "=?")) {
			st.setTimestamp(1, new Timestamp(lastAclCleanupDate.getTime()));
			st.executeUpdate();
		}
		commit();
	}

	public void registerDeletionPushStart() throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement("UPDATE " + TABLE_NAME + " SET " + DELETION_PUSH_STATUS + "=?")) {
			st.setBoolean(1, true);
			st.executeUpdate();
		}
		commit();
	}

	public void setLastDeletionPushDate(final Date lastDeletionPushDate) throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement("UPDATE " + TABLE_NAME + " SET " + LAST_DELETION_PUSH_DATE + "=?, " + DELETION_PUSH_STATUS + "=?")) {
			st.setTimestamp(1, new Timestamp(lastDeletionPushDate.getTime()));
			st.setBoolean(2, false);
			st.executeUpdate();
		}
		commit();
	}

	public void notifyPushCompletion() throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement("UPDATE " + TABLE_NAME + " SET " + COMPLETED_ONCE + "=?")) {
			st.setBoolean(1, true);
			st.executeUpdate();
		}
		commit();
	}

}
