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
import launchserver.helper.MySQLSourceConfig;

public final class MySQLAuthHandler extends CachedAuthHandler {
	private final MySQLSourceConfig mySQLHolder;
	private final String table;
	private final String uuidColumn;
	private final String usernameColumn;
	private final String accessTokenColumn;
	private final String serverIDColumn;

	// Prepared SQL queries
	private final String queryAllSQL;
	private final String queryByUUIDSQL;
	private final String queryByUsernameSQL;
	private final String updateServerIDSQL;
	private final String updateAccessTokenSQL;

	public MySQLAuthHandler(BlockConfigEntry block) {
		super(block);
		mySQLHolder = new MySQLSourceConfig("authHandlerPool", block);
		table = block.getEntryValue("table", StringConfigEntry.class);
		uuidColumn = block.getEntryValue("uuidColumn", StringConfigEntry.class);
		usernameColumn = block.getEntryValue("usernameColumn", StringConfigEntry.class);
		accessTokenColumn = block.getEntryValue("accessTokenColumn", StringConfigEntry.class);
		serverIDColumn = block.getEntryValue("serverIDColumn", StringConfigEntry.class);

		// Prepare SQL queries
		queryAllSQL = String.format("SELECT %s, %s, %s, %s FROM %s",
			uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table);
		queryByUUIDSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=?",
			uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, uuidColumn);
		queryByUsernameSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=?",
			uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, usernameColumn);
		updateServerIDSQL = String.format("UPDATE %s SET %s=? WHERE %s=?", table, serverIDColumn, uuidColumn);
		updateAccessTokenSQL = String.format("UPDATE %s SET %s=? WHERE %s=?", table, accessTokenColumn, uuidColumn);

		// Fetch all entries
		if (block.getEntryValue("fetchAll", BooleanConfigEntry.class)) {
			LogHelper.info("Fetching all AuthHandler entries");
			try (Connection c = mySQLHolder.getConnection(); ResultSet set = c.createStatement().executeQuery(queryAllSQL)) {
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
	public void verify() {
		mySQLHolder.verify();
		VerifyHelper.verifyIDName(table);
		VerifyHelper.verifyIDName(uuidColumn);
		VerifyHelper.verifyIDName(usernameColumn);
		VerifyHelper.verifyIDName(accessTokenColumn);
		VerifyHelper.verifyIDName(serverIDColumn);
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
	protected boolean updateAccessToken(UUID uuid, String accessToken) throws IOException {
		return update(updateAccessTokenSQL, uuid.toString(), accessToken);
	}

	@Override
	protected boolean updateServerID(UUID uuid, String serverID) throws IOException {
		return update(updateServerIDSQL, uuid.toString(), serverID);
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

	private boolean update(String sql, String key, String newValue) throws IOException {
		try (Connection c = mySQLHolder.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
			s.setString(1, newValue);
			s.setString(2, key);
			return s.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
}
