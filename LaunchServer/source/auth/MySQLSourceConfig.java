package launchserver.auth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import com.zaxxer.hikari.HikariDataSource;
import launcher.LauncherAPI;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.IntegerConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

public final class MySQLSourceConfig extends ConfigObject implements AutoCloseable {
	@LauncherAPI public static final int TIMEOUT = VerifyHelper.verifyInt(
		Integer.parseUnsignedInt(System.getProperty("launcher.mysql.timeout", Integer.toString(5))),
		VerifyHelper.POSITIVE, "launcher.mysql.timeout can't be <= 0");
	private static final int MAX_POOL_SIZE = VerifyHelper.verifyInt(
		Integer.parseUnsignedInt(System.getProperty("launcher.mysql.maxPoolSize", Integer.toString(25))),
		VerifyHelper.POSITIVE, "launcher.mysql.maxPoolSize can't be <= 0");
	private static final int STMT_CACHE_SIZE = VerifyHelper.verifyInt(
		Integer.parseUnsignedInt(System.getProperty("launcher.mysql.stmtCacheSize", Integer.toString(250))),
		VerifyHelper.NOT_NEGATIVE, "launcher.mysql.stmtCacheSize can't be < 0");

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
	public MySQLSourceConfig(String poolName, BlockConfigEntry block) {
		super(block);
		this.poolName = poolName;
		address = VerifyHelper.verify(block.getEntryValue("address", StringConfigEntry.class),
			VerifyHelper.NOT_EMPTY, "MySQL address can't be empty");
		port = VerifyHelper.verifyInt(block.getEntryValue("port", IntegerConfigEntry.class),
			VerifyHelper.range(0, 65535), "Illegal MySQL port");
		username = VerifyHelper.verify(block.getEntryValue("username", StringConfigEntry.class),
			VerifyHelper.NOT_EMPTY, "MySQL username can't be empty");
		password = block.getEntryValue("password", StringConfigEntry.class);
		database = VerifyHelper.verify(block.getEntryValue("database", StringConfigEntry.class),
			VerifyHelper.NOT_EMPTY, "MySQL database can't be empty");

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
			MysqlDataSource mysqlSource = new MysqlDataSource();
			mysqlSource.setUseUnicode(true);
			mysqlSource.setCachePrepStmts(true);

			// Set timeouts and cache
			mysqlSource.setEnableQueryTimeouts(true);
			mysqlSource.setLoginTimeout(TIMEOUT);
			mysqlSource.setConnectTimeout(TIMEOUT * 1000);
			mysqlSource.setPrepStmtCacheSize(STMT_CACHE_SIZE);
			mysqlSource.setPrepStmtCacheSqlLimit(2048);

			// Set credentials
			mysqlSource.setServerName(address);
			mysqlSource.setPortNumber(port);
			mysqlSource.setUser(username);
			mysqlSource.setPassword(password);
			mysqlSource.setDatabaseName(database);

			// Try using HikariCP
			source = mysqlSource;
			try {
				Class.forName("com.zaxxer.hikari.HikariDataSource");
				hikari = true; // Used for shutdown. Not instanceof because of possible classpath error

				// Set HikariCP pool
				HikariDataSource hikariSource = new HikariDataSource();
				hikariSource.setDataSource(source);

				// Set pool settings
				hikariSource.setPoolName(poolName);
				hikariSource.setMaximumPoolSize(MAX_POOL_SIZE);
				hikariSource.setValidationTimeout(TIMEOUT * 1000L);

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
