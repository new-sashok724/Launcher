package launchserver.helper;

import javax.sql.DataSource;
import java.io.Flushable;
import java.sql.Connection;
import java.sql.SQLException;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import com.zaxxer.hikari.HikariDataSource;
import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.IntegerConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

public final class MySQLSourceConfig extends ConfigObject implements Flushable {
	private static final int MAX_POOL_SIZE = VerifyHelper.verifyInt(
		Integer.parseUnsignedInt(System.getProperty("launcher.mysql.maxPoolSize", Integer.toString(10))),
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
		address = block.getEntryValue("address", StringConfigEntry.class);
		port = block.getEntryValue("port", IntegerConfigEntry.class);
		username = block.getEntryValue("username", StringConfigEntry.class);
		password = block.getEntryValue("password", StringConfigEntry.class);
		database = block.getEntryValue("database", StringConfigEntry.class);
	}

	@Override
	public synchronized void flush() {
		if (hikari) { // Shutdown hikari pool
			((HikariDataSource) source).close();
		}
	}

	@Override
	public void verify() {
		// Verify MySQL address
		VerifyHelper.verify(address, VerifyHelper.NOT_EMPTY, "MySQL address can't be empty");
		VerifyHelper.verify(username, VerifyHelper.NOT_EMPTY, "MySQL username can't be empty");
		VerifyHelper.verify(database, VerifyHelper.NOT_EMPTY, "MySQL database can't be empty");
		VerifyHelper.verifyInt(port, VerifyHelper.range(0, 65535), "Illegal MySQL port: " + port);

		// Don't verify password, it can be empty
	}

	@LauncherAPI
	public synchronized Connection getConnection() throws SQLException {
		if (source == null) { // New data source
			MysqlDataSource mysqlSource = new MysqlDataSource();
			mysqlSource.setUseUnicode(true);
			mysqlSource.setLoginTimeout(IOHelper.TIMEOUT);
			mysqlSource.setCachePrepStmts(true);
			mysqlSource.setPrepStmtCacheSize(STMT_CACHE_SIZE);
			mysqlSource.setPrepStmtCacheSqlLimit(IOHelper.BUFFER_SIZE);

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
