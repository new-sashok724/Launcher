package launchserver.auth.handler;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.auth.JoinServerRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.BooleanConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launcher.serialize.stream.StreamObject;

public abstract class FileAuthHandler extends AuthHandler {
	@LauncherAPI public final Path file;
	@LauncherAPI public final boolean md5UUIDs;

	// Instance
	private final SecureRandom random = SecurityHelper.newRandom();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	// Storage
	private final Map<UUID, Auth> authsMap = new HashMap<>(IOHelper.BUFFER_SIZE);
	private final Map<String, UUID> usernamesMap = new HashMap<>(IOHelper.BUFFER_SIZE);

	@LauncherAPI
	protected FileAuthHandler(BlockConfigEntry block) {
		super(block);
		file = IOHelper.toPath(block.getEntryValue("file", StringConfigEntry.class));
		md5UUIDs = block.getEntryValue("md5UUIDs", BooleanConfigEntry.class);
		if (IOHelper.isFile(file)) {
			LogHelper.info("Reading auth handler file");
			try {
				readAuthFile();
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}
	}

	@Override
	public final UUID auth(String username, String accessToken) {
		lock.writeLock().lock();
		try {
			UUID uuid = usernameToUUID(username);
			Auth auth = authsMap.get(uuid);

			// Not registered? Fix it!
			if (auth == null) {
				auth = new Auth(username);

				// Generate UUID
				uuid = genUUIDFor(username);
				authsMap.put(uuid, auth);
				usernamesMap.put(low(username), uuid);
			}

			// Authenticate
			auth.auth(username, accessToken);
			return uuid;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public final UUID checkServer(String username, String serverID) {
		lock.writeLock().lock();
		try {
			UUID uuid = usernameToUUID(username);
			Auth auth = authsMap.get(uuid);

			// Check server (if has such account of course)
			return auth != null && auth.checkServer(username, serverID) ? uuid : null;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public final void flush() throws IOException {
		lock.readLock().lock();
		try {
			LogHelper.info("Writing auth handler file (%d entries)", authsMap.size());
			writeAuthFile();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final boolean joinServer(String username, String accessToken, String serverID) {
		lock.writeLock().lock();
		try {
			Auth auth = authsMap.get(usernameToUUID(username));
			return auth != null && auth.joinServer(username, accessToken, serverID);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public final UUID usernameToUUID(String username) {
		lock.readLock().lock();
		try {
			return usernamesMap.get(low(username));
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final String uuidToUsername(UUID uuid) {
		lock.readLock().lock();
		try {
			Auth auth = authsMap.get(uuid);
			return auth == null ? null : auth.username;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final void verify() {
		// Do nothing?
	}

	@LauncherAPI
	public final Set<Map.Entry<UUID, Auth>> entrySet() {
		return Collections.unmodifiableMap(authsMap).entrySet();
	}

	@LauncherAPI
	protected final void addAuth(UUID uuid, Auth entry) throws IOException {
		lock.writeLock().lock();
		try {
			authsMap.put(uuid, entry);
			usernamesMap.put(low(entry.username), uuid);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@LauncherAPI
	protected abstract void readAuthFile() throws IOException;

	@LauncherAPI
	protected abstract void writeAuthFile() throws IOException;

	private UUID genUUIDFor(String username) {
		if (md5UUIDs) {
			UUID md5UUID = PlayerProfile.md5UUID(username);
			if (!authsMap.containsKey(md5UUID)) {
				return md5UUID;
			}
			LogHelper.warning("MD5 UUID has been already registered, using random: '%s'", username);
		}

		// Pick random UUID
		UUID uuid;
		do {
			uuid = new UUID(random.nextLong(), random.nextLong());
		} while (authsMap.containsKey(uuid));
		return uuid;
	}

	private static String low(String username) {
		return username.toLowerCase(Locale.US);
	}

	public static final class Auth extends StreamObject {
		private String username;
		private String accessToken;
		private String serverID;

		@LauncherAPI
		public Auth(String username) {
			this.username = VerifyHelper.verifyUsername(username);
		}

		@LauncherAPI
		public Auth(String username, String accessToken, String serverID) {
			this(username);
			if (accessToken == null && serverID != null) {
				throw new IllegalArgumentException("Can't set accessToken while serverID is null");
			}

			// Set and verify accessToken
			this.accessToken = accessToken == null ?
				null : SecurityHelper.verifyToken(accessToken);
			this.serverID = serverID == null ?
				null : JoinServerRequest.verifyServerID(serverID);
		}

		@LauncherAPI
		public Auth(HInput input) throws IOException {
			username = VerifyHelper.verifyUsername(input.readASCII(16));
			if (input.readBoolean()) {
				accessToken = SecurityHelper.verifyToken(input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH));
				if (input.readBoolean()) {
					serverID = JoinServerRequest.verifyServerID(input.readASCII(41));
				}
			}
		}

		@Override
		public void write(HOutput output) throws IOException {
			output.writeASCII(username, 16);
			output.writeBoolean(accessToken != null);
			if (accessToken != null) {
				output.writeASCII(accessToken, -SecurityHelper.TOKEN_STRING_LENGTH);
				output.writeBoolean(serverID != null);
				if (serverID != null) {
					output.writeASCII(serverID, 41);
				}
			}
		}

		@LauncherAPI
		public String getAccessToken() {
			return accessToken;
		}

		@LauncherAPI
		public String getServerID() {
			return serverID;
		}

		@LauncherAPI
		public String getUsername() {
			return username;
		}

		private void auth(String username, String accessToken) {
			this.username = username; // Update username case
			this.accessToken = accessToken;
			serverID = null;
		}

		private boolean checkServer(String username, String serverID) {
			return username.equals(this.username) && serverID.equals(this.serverID);
		}

		private boolean joinServer(String username, String accessToken, String serverID) {
			if (!username.equals(this.username) || !accessToken.equals(this.accessToken)) {
				return false; // Username or access token mismatch
			}

			// Update server ID
			this.serverID = serverID;
			return true;
		}
	}
}
