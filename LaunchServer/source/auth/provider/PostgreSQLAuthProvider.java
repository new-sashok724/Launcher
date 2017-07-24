package launchserver.auth.provider;

import launcher.helper.CommonHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.ListConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;
import launchserver.auth.sqlconfig.PostgreSQLSourceConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PostgreSQLAuthProvider extends AuthProvider {
    private final PostgreSQLSourceConfig postgreSQLHolder;
    private final String query;
    private final String[] queryParams;

    PostgreSQLAuthProvider(BlockConfigEntry block) {
        super(block);
        postgreSQLHolder = new PostgreSQLSourceConfig("authProviderPool", block);

        // Read query
        query = VerifyHelper.verify(block.getEntryValue("query", StringConfigEntry.class),
                VerifyHelper.NOT_EMPTY, "PostgreSQL query can't be empty");
        queryParams = block.getEntry("queryParams", ListConfigEntry.class).
                stream(StringConfigEntry.class).toArray(String[]::new);
    }

    @Override
    public String auth(String login, String password) throws SQLException, AuthException {
        try (Connection c = postgreSQLHolder.getConnection(); PreparedStatement s = c.prepareStatement(query)) {
            String[] replaceParams = {"login", login, "password", password};
            for (int i = 0; i < queryParams.length; i++) {
                s.setString(i + 1, CommonHelper.replace(queryParams[i], replaceParams));
            }

            // Execute SQL query
            s.setQueryTimeout(PostgreSQLSourceConfig.TIMEOUT);
            try (ResultSet set = s.executeQuery()) {
                return set.next() ? set.getString(1) : authError("Incorrect username or password");
            }
        }
    }

    @Override
    public void close() {
        // Do nothing
    }
}
