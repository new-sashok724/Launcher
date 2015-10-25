package launchserver.auth.provider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import launcher.helper.CommonHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.ListConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;
import launchserver.helper.MySQLSourceConfig;

public final class MySQLAuthProvider extends AuthProvider {
	private final MySQLSourceConfig mySQLHolder;
	private final String query;
	private final String[] queryParams;

	public MySQLAuthProvider(BlockConfigEntry block) {
		super(block);
		mySQLHolder = new MySQLSourceConfig("authProviderPool", block);
		query = block.getEntryValue("query", StringConfigEntry.class);
		queryParams = block.getEntry("queryParams", ListConfigEntry.class).stream(StringConfigEntry.class).toArray(String[]::new);
	}

	@Override
	public String auth(String login, String password) throws SQLException, AuthException {
		try (Connection c = mySQLHolder.getConnection()) {
			try (PreparedStatement statement = c.prepareStatement(query)) {
				String[] replaceParams = { "login", login, "password", password };
				for (int i = 0; i < queryParams.length; i++) {
					statement.setString(i + 1, CommonHelper.replace(queryParams[i], replaceParams));
				}

				// Execute SQL query
				try (ResultSet set = statement.executeQuery()) {
					return set.next() ? set.getString(1) :
						authError("Incorrect username or password");
				}
			}
		}
	}

	@Override
	public void flush() {
		// Do nothing
	}

	@Override
	public void verify() {
		mySQLHolder.verify();

		// Verify auth provider-specific
		VerifyHelper.verify(query, VerifyHelper.NOT_EMPTY, "MySQL query can't be empty");
	}
}
