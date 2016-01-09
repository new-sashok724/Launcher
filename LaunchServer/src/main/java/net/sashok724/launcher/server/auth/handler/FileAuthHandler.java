package net.sashok724.launcher.server.auth.handler;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.helper.CommonHelper;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.helper.SecurityHelper;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.auth.JoinServerRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.BooleanConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;
import net.sashok724.launcher.client.serialize.stream.StreamObject;

public abstract class FileAuthHandler extends AuthHandler {
	@LauncherAPI public final Path file;
	@LauncherAPI public final boolean offlineUUIDs;

	// Instance
	private final SecureRandom random = SecurityHelper.newRandom();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	// Storage
	private final Map<UUID, Entry> entryMap = new HashMap<>(IOHelper.BUFFER_SIZE);
	private final Map<String, UUID> usernamesMap = new HashMap<>(IOHelper.BUFFER_SIZE);

	@LauncherAPI
	protected FileAuthHandler(BlockConfigEntry block) {
		super(block);
		file = IOHelper.toPath(block.getEntryValue("file", StringConfigEntry.class));
		offlineUUIDs = block.getEntryValue("offlineUUIDs", BooleanConfigEntry.class);

		// Read auth handler file
		if (IOHelper.isFile(file)) {
			LogHelper.info("Reading auth handler file: '%s'", file);
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
			Entry entry = entryMap.get(uuid);

			// Not registered? Fix it!
			if (entry == null) {
				entry = new Entry(username);

				// Generate UUID
				uuid = genUUIDFor(username);
				entryMap.put(uuid, entry);
				usernamesMap.put(CommonHelper.low(username), uuid);
			}

			// Authenticate
			entry.auth(username, accessToken);
			return uuid;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public final UUID checkServer(String username, String serverID) {
		lock.readLock().lock();
		try {
			UUID uuid = usernameToUUID(username);
			Entry entry = entryMap.get(uuid);

			// Check server (if has such account of course)
			return entry != null && entry.checkServer(username, serverID) ? uuid : null;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final void close() throws IOException {
		lock.readLock().lock();
		try {
			LogHelper.info("Writing auth handler file (%d entries)", entryMap.size());
			writeAuthFile();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final boolean joinServer(String username, String accessToken, String serverID) {
		lock.writeLock().lock();
		try {
			Entry entry = entryMap.get(usernameToUUID(username));
			return entry != null && entry.joinServer(username, accessToken, serverID);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public final UUID usernameToUUID(String username) {
		lock.readLock().lock();
		try {
			return usernamesMap.get(CommonHelper.low(username));
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public final String uuidToUsername(UUID uuid) {
		lock.readLock().lock();
		try {
			Entry entry = entryMap.get(uuid);
			return entry == null ? null : entry.username;
		} finally {
			lock.readLock().unlock();
		}
	}

	@LauncherAPI
	protected final void addAuth(UUID uuid, Entry entry) throws IOException {
		lock.writeLock().lock();
		try {
			Entry previous = entryMap.put(uuid, entry);
			if (previous != null) { // In case of username changing
				usernamesMap.remove(CommonHelper.low(previous.username));
			}
			usernamesMap.put(CommonHelper.low(entry.username), uuid);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@LauncherAPI
	protected final Set<Map.Entry<UUID, Entry>> entrySet() {
		return Collections.unmodifiableMap(entryMap).entrySet();
	}

	@LauncherAPI
	protected abstract void readAuthFile() throws IOException;

	@LauncherAPI
	protected abstract void writeAuthFile() throws IOException;

	private UUID genUUIDFor(String username) {
		if (offlineUUIDs) {
			UUID md5UUID = PlayerProfile.offlineUUID(username);
			if (!entryMap.containsKey(md5UUID)) {
				return md5UUID;
			}
			LogHelper.warning("Offline UUID collision, using random: '%s'", username);
		}

		// Pick random UUID
		UUID uuid;
		do {
			uuid = new UUID(random.nextLong(), random.nextLong());
		} while (entryMap.containsKey(uuid));
		return uuid;
	}

	public static final class Entry extends StreamObject {
		private String username;
		private String accessToken;
		private String serverID;

		@LauncherAPI
		public Entry(String username) {
			this.username = VerifyHelper.verifyUsername(username);
		}

		@LauncherAPI
		public Entry(String username, String accessToken, String serverID) {
			this(username);
			if (accessToken == null && serverID != null) {
				throw new IllegalArgumentException("Can't set access token while server ID is null");
			}

			// Set and verify access token
			this.accessToken = accessToken == null ? null : SecurityHelper.verifyToken(accessToken);
			this.serverID = serverID == null ? null : JoinServerRequest.verifyServerID(serverID);
		}

		@LauncherAPI
		public Entry(HInput input) throws IOException {
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
