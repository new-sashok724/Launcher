package launchserver.auth.sqlconfig;

import com.zaxxer.hikari.HikariDataSource;
import launcher.LauncherAPI;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.IntegerConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class PostgreSQLSourceConfig extends ConfigObject implements AutoCloseable, SQLSourceConfig {
    @LauncherAPI
    public static final int TIMEOUT = VerifyHelper.verifyInt(
            Integer.parseUnsignedInt(System.getProperty("launcher.postgresql.idleTimeout", Integer.toString(5000))),
            VerifyHelper.POSITIVE, "launcher.postgresql.idleTimeout can't be <= 5000");
    private static final int MAX_POOL_SIZE = VerifyHelper.verifyInt(
            Integer.parseUnsignedInt(System.getProperty("launcher.postgresql.maxPoolSize", Integer.toString(3))),
            VerifyHelper.POSITIVE, "launcher.postgresql.maxPoolSize can't be <= 0");

    // Instance
    private final String poolName;

    // Config
    private final String address;
    private final int port;
    private final String username;
    private final String password;
    private final String database;

    // Cache
    private DataSource source;
    private boolean hikari;

    @LauncherAPI
    public PostgreSQLSourceConfig(String poolName, BlockConfigEntry block) {
        super(block);
        this.poolName = poolName;
        address = VerifyHelper.verify(block.getEntryValue("address", StringConfigEntry.class),
                VerifyHelper.NOT_EMPTY, "PostgreSQL address can't be empty");
        port = VerifyHelper.verifyInt(block.getEntryValue("port", IntegerConfigEntry.class),
                VerifyHelper.range(0, 65535), "Illegal MySQL port");
        username = VerifyHelper.verify(block.getEntryValue("username", StringConfigEntry.class),
                VerifyHelper.NOT_EMPTY, "PostgreSQL username can't be empty");
        password = block.getEntryValue("password", StringConfigEntry.class);
        database = VerifyHelper.verify(block.getEntryValue("database", StringConfigEntry.class),
                VerifyHelper.NOT_EMPTY, "PostgreSQL database can't be empty");

        // Password shouldn't be verified
    }

    @Override
    public synchronized void close() {
        if (hikari) { // Shutdown hikari pool
            ((HikariDataSource) source).close();
        }
    }

    @LauncherAPI
    public synchronized Connection getConnection() throws SQLException {
        if (source == null) { // New data source
            PGSimpleDataSource postgresqlSource = new PGSimpleDataSource();

            // Set credentials
            postgresqlSource.setServerName(address);
            postgresqlSource.setPortNumber(port);
            postgresqlSource.setUser(username);
            postgresqlSource.setPassword(password);
            postgresqlSource.setDatabaseName(database);

            // Try using HikariCP
            source = postgresqlSource;

            //noinspection Duplicates
            try {
                Class.forName("com.zaxxer.hikari.HikariDataSource");
                hikari = true; // Used for shutdown. Not instanceof because of possible classpath error

                // Set HikariCP pool
                HikariDataSource hikariSource = new HikariDataSource();
                hikariSource.setDataSource(source);

                // Set pool settings
                hikariSource.setPoolName(poolName);
                hikariSource.setMinimumIdle(0);
                hikariSource.setMaximumPoolSize(MAX_POOL_SIZE);
                hikariSource.setIdleTimeout(TIMEOUT * 1000L);

                // Replace source with hds
                source = hikariSource;
                LogHelper.info("HikariCP pooling enabled for '%s'", poolName);
            } catch (ClassNotFoundException ignored) {
                LogHelper.warning("HikariCP isn't in classpath for '%s'", poolName);
            }
        }
        return source.getConnection();
    }
}
