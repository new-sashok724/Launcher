package launchserver.auth.handler;

import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.BooleanConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.sqlconfig.PostgreSQLSourceConfig;

import java.io.IOException;
import java.sql.*;
import java.util.UUID;

public final class PostgreSQLAuthHandler extends CachedAuthHandler {
    private final PostgreSQLSourceConfig postgreSQLHolder;
    private final String uuidColumn;
    private final String usernameColumn;
    private final String accessTokenColumn;
    private final String serverIDColumn;


    private final String queryByUUIDSQL;
    private final String queryByUsernameSQL;
    private final String updateAuthSQL;
    private final String updateServerIDSQL;

    PostgreSQLAuthHandler(BlockConfigEntry block) {
        super(block);
        postgreSQLHolder = new PostgreSQLSourceConfig("authHandlerPool", block);

        // Read query params
        String table = VerifyHelper.verifyIDName(
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
        queryByUUIDSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=? LIMIT 1",
                uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, uuidColumn);
        queryByUsernameSQL = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s=? LIMIT 1",
                uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table, usernameColumn);
        updateAuthSQL = String.format("UPDATE %s SET %s=?, %s=?, %s=NULL WHERE %s=? LIMIT 1",
                table, usernameColumn, accessTokenColumn, serverIDColumn, uuidColumn);
        updateServerIDSQL = String.format("UPDATE %s SET %s=? WHERE %s=? LIMIT 1",
                table, serverIDColumn, uuidColumn);

        // Fetch all entries
        if (block.getEntryValue("fetchAll", BooleanConfigEntry.class)) {
            LogHelper.info("Fetching all AuthHandler entries");
            String query = String.format("SELECT %s, %s, %s, %s FROM %s",
                    uuidColumn, usernameColumn, accessTokenColumn, serverIDColumn, table);
            try (Connection c = postgreSQLHolder.getConnection(); Statement statement = c.createStatement();
                 ResultSet set = statement.executeQuery(query)) {
                for (Entry entry = constructEntry(set); entry != null; entry = constructEntry(set)) {
                    addEntry(entry);
                }
            } catch (SQLException e) {
                LogHelper.error(e);
            }
        }
    }

    @Override
    public void close() {
        postgreSQLHolder.close();
    }

    private Entry constructEntry(ResultSet set) throws SQLException {
        return set.next() ? new Entry(UUID.fromString(set.getString(uuidColumn)),
                set.getString(usernameColumn), set.getString(accessTokenColumn), set.getString(serverIDColumn)) : null;
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
        try (Connection c = postgreSQLHolder.getConnection();
             PreparedStatement s = c.prepareStatement(updateAuthSQL)) {
            s.setString(1, username); // Username case
            s.setString(2, accessToken);
            s.setString(3, uuid.toString());

            // Execute update
            s.setQueryTimeout(PostgreSQLSourceConfig.TIMEOUT);
            return s.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected boolean updateServerID(UUID uuid, String serverID) throws IOException {
        try (Connection c = postgreSQLHolder.getConnection();
             PreparedStatement s = c.prepareStatement(updateServerIDSQL)) {
            s.setString(1, serverID);
            s.setString(2, uuid.toString());

            // Execute update
            s.setQueryTimeout(PostgreSQLSourceConfig.TIMEOUT);
            return s.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private Entry query(String sql, String value) throws IOException {
        try (Connection c = postgreSQLHolder.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, value);

            // Execute query
            s.setQueryTimeout(PostgreSQLSourceConfig.TIMEOUT);
            try (ResultSet set = s.executeQuery()) {
                return constructEntry(set);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}

