package com.sword.gsa.spis.scs.push.databases;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;

import com.sword.gsa.spis.scs.push.config.PushConfig;

public abstract class PushDBConnection implements AutoCloseable {

	protected static final Logger LOG = Logger.getLogger(PushDBConnection.class);

	protected static final String SYSTBL_NAME = "SYS.SYSTABLES";
	protected static final String TBL_NAME_COL = "TABLENAME";
	protected static final String INTERNAL_ID_VARCHAR_MAX_LENGTH = "16384";

	private static final String TBL_EXISTS = new StringBuilder("SELECT COUNT(*) FROM ").append(SYSTBL_NAME).append(" WHERE UPPER(").append(TBL_NAME_COL).append(")=?").toString();

	protected final String tableName;
	protected final DataBaseConnection dbc;

	public PushDBConnection(final PushConfig conf, final String tableName) throws ClassNotFoundException, SQLException {
		this.tableName = tableName;
		dbc = new DataBaseConnection(DBType.JAVA_DB, conf.connectorCtx.getIndexerDBDir(conf.connectorId).toString(), "gpushuser", "drow$$ap");
		dbc.connect(false);

		synchronized (SYSTBL_NAME) {
			if (!tableExists(tableName)) {
				createTable();
			}
		}

	}

	protected final boolean tableExists(final String tableName) throws SQLException {
		try (PreparedStatement st = dbc.getPreparedStatement(TBL_EXISTS)) {
			st.setString(1, tableName.toUpperCase());
			try (ResultSet rs = st.executeQuery()) {
				if (rs.next()) return rs.getInt(1) > 0;
				else throw new SQLException("COUNT(*) FROM " + SYSTBL_NAME + " returned no result");
			}
		} catch (final SQLException e) {
			if ("42Y07".equals(e.getSQLState())) // Schema does not exist; means
			// no table have been
			// created yet with this db
			// user
			return false;
			else throw e;
		}
	}

	protected abstract void createTable() throws SQLException;

	public final void commit() throws SQLException {
		dbc.commit();
	}

	public final void dropTable() throws SQLException {
		try (Statement st = dbc.getStatement()) {
			st.executeUpdate("DROP TABLE " + tableName);
		}
		commit();
	}

	@Override
	public void close() throws SQLException {
		dbc.close();
	}

	public static void shutdownDerby(final PushConfig conf) throws SQLException, ClassNotFoundException {
		try (DataBaseConnection dbc = new DataBaseConnection(DBType.JAVA_DB, conf.connectorCtx.getIndexerDBDir(conf.connectorId).toString(), "gpushuser", "drow$$ap")) {
			dbc.shutdown();
		} catch (final SQLException snte) {
			System.out.println(snte.getSQLState());
			if ("08006".equals(snte.getSQLState()) || "XJ015".equals(snte.getSQLState())) {
				// OK - normal on shutdown
			} else throw snte;
		}

		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true").close();
		} catch (final SQLException snte) {
			System.out.println(snte.getSQLState());
			if ("08006".equals(snte.getSQLState()) || "XJ015".equals(snte.getSQLState())) {
				// OK - normal on shutdown
			} else throw snte;
		}
	}

}
