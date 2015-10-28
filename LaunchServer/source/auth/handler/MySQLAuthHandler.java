package launchserver.auth.handler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.BooleanConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.MySQLSourceConfig;

public final class MySQLAuthHandler extends CachedAuthHandler {
	private final MySQLSourceConfig mySQLHolder;
	private final String table;
	private final String uuidColumn;
	private final String usernameColumn;
	private final String accessTokenColumn;
	private final String serverIDColumn;

	// Prepared SQL queries
	private final String queryByUUIDSQL;
	private final String queryByUsernameSQL;
	private final String updateAuthSQL;
	private final String updateServerIDSQL;

	public MySQLAuthHandler(BlockConfigEntry block) {
		super(block);
		mySQLHolder = new MySQLSourceConfig("authHandlerPool", block);

		// Read query params
		table = VerifyHelper.verifyIDName(
			block.getEntryValue("table", StringConfigEntry.class));
		uuidColumn = VerifyHelper.verifyIDName(
			block.getEntryValue("uuidColumn", StringConfigEntry.class));
		usernameColumn = VerifyHelper.verifyIDName(
			block.getEntryValue("usernameColumn", StringConfigEntry.class));
		accessTokenColumn = VerifyHelper.verifyIDName(
			block.getEntryValue("accessTokenColumn", StringConfigEntry.class));
		serverIDColumn = VerifyHelper.verifyIDName(
			block.getEntryValue("serverIDColumn", StringConfigEntry.class));

		// Prepare SQL queries
		queryByUUIDSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=?",
			uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, uuidColumn);
		queryByUsernameSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=?",
			uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, usernameColumn);
		updateAuthSQL = String.format("UPDATE %s SET %s=?, %s=? WHERE %s=?",
			table, usernameColumn, accessTokenColumn, uuidColumn);
		updateServerIDSQL = String.format("UPDATE %s SET %s=? WHERE %s=?",
			table, serverIDColumn, uuidColumn);

		// Fetch all entries
		if (block.getEntryValue("fetchAll", BooleanConfigEntry.class)) {
			LogHelper.info("Fetching all AuthHandler entries");
			String query = String.format("SELECT %s, %s, %s, %s FROM %s",
				uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table);
			try (Connection c = mySQLHolder.getConnection(); ResultSet set = c.createStatement().executeQuery(query)) {
				for (Entry entry = constructEntry(set); entry != null; entry = constructEntry(set)) {
					addEntry(entry);
				}
			} catch (SQLException e) {
				LogHelper.error(e);
			}
		}
	}

	@Override
	public void flush() {
		mySQLHolder.flush();
	}

	@Override
	protected Entry fetchEntry(String username) throws IOException {
		return query(queryByUsernameSQL, username);
	}

	@Override
	protected Entry fetchEntry(UUID uuid) throws IOException {
		return query(queryByUUIDSQL, uuid.toString());
	}

	@Override
	protected boolean updateAuth(UUID uuid, String username, String accessToken) throws IOException {
		try (Connection c = mySQLHolder.getConnection();
			 PreparedStatement s = c.prepareStatement(updateAuthSQL)) {
			s.setString(1, username); // Username case
			s.setString(2, accessToken);
			s.setString(3, uuid.toString());
			return s.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected boolean updateServerID(UUID uuid, String serverID) throws IOException {
		try (Connection c = mySQLHolder.getConnection();
			 PreparedStatement s = c.prepareStatement(updateServerIDSQL)) {
			s.setString(1, serverID);
			s.setString(2, uuid.toString());
			return s.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}

	private Entry constructEntry(ResultSet set) throws SQLException {
		return set.next() ? new Entry(UUID.fromString(set.getString(uuidColumn)), set.getString(usernameColumn),
			set.getString(accessTokenColumn), set.getString(serverIDColumn)) : null;
	}

	private Entry query(String sql, String value) throws IOException {
		try (Connection c = mySQLHolder.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
			s.setString(1, value);
			try (ResultSet set = s.executeQuery()) {
				return constructEntry(set);
			}
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
}
