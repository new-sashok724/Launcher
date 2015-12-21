package launchserver.auth.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import launcher.LauncherAPI;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.request.auth.JoinServerRequest;
import launcher.serialize.config.entry.BlockConfigEntry;
import launchserver.auth.AuthException;

public abstract class CachedAuthHandler extends AuthHandler {
	private final Map<UUID, Entry> entryCache = new HashMap<>(IOHelper.BUFFER_SIZE);
	private final Map<String, UUID> usernamesCache = new HashMap<>(IOHelper.BUFFER_SIZE);

	@LauncherAPI
	protected CachedAuthHandler(BlockConfigEntry block) {
		super(block);
	}

	@Override
	public final synchronized UUID auth(String username, String accessToken) throws IOException {
		Entry entry = getEntry(username);
		if (entry == null || !updateAuth(entry.uuid, entry.username, accessToken)) {
			throw new AuthException(String.format("UUID is null for username '%s'", username));
		}

		// Update cached access token (and username case)
		entry.username = username;
		entry.accessToken = accessToken;
		entry.serverID = null;
		return entry.uuid;
	}

	@Override
	public final synchronized UUID checkServer(String username, String serverID) throws IOException {
		Entry entry = getEntry(username);
		return entry != null && username.equals(entry.username) &&
			serverID.equals(entry.serverID) ? entry.uuid : null;
	}

	@Override
	public final synchronized boolean joinServer(String username, String accessToken, String serverID) throws IOException {
		Entry entry = getEntry(username);
		if (entry == null || !username.equals(entry.username) || !accessToken.equals(entry.accessToken) ||
			!updateServerID(entry.uuid, serverID)) {
			return false; // Account doesn't exist or invalid access token
		}

		// Update cached server ID
		entry.serverID = serverID;
		return true;
	}

	@Override
	public final synchronized UUID usernameToUUID(String username) throws IOException {
		Entry entry = getEntry(username);
		return entry == null ? null : entry.uuid;
	}

	@Override
	public final synchronized String uuidToUsername(UUID uuid) throws IOException {
		Entry entry = getEntry(uuid);
		return entry == null ? null : entry.username;
	}

	@LauncherAPI
	protected void addEntry(Entry entry) {
		Entry previous = entryCache.put(entry.uuid, entry);
		if (previous != null) { // In case of username changing
			usernamesCache.remove(CommonHelper.low(previous.username));
		}
		usernamesCache.put(CommonHelper.low(entry.username), entry.uuid);
	}

	@LauncherAPI
	protected abstract Entry fetchEntry(UUID uuid) throws IOException;

	@LauncherAPI
	protected abstract Entry fetchEntry(String username) throws IOException;

	@LauncherAPI
	protected abstract boolean updateAuth(UUID uuid, String username, String accessToken) throws IOException;

	@LauncherAPI
	protected abstract boolean updateServerID(UUID uuid, String serverID) throws IOException;

	private Entry getEntry(UUID uuid) throws IOException {
		Entry entry = entryCache.get(uuid);
		if (entry == null) {
			entry = fetchEntry(uuid);
			if (entry != null) {
				addEntry(entry);
			}
		}
		return entry;
	}

	private Entry getEntry(String username) throws IOException {
		UUID uuid = usernamesCache.get(CommonHelper.low(username));
		if (uuid != null) {
			return getEntry(uuid);
		}

		// Fetch entry by username
		Entry entry = fetchEntry(username);
		if (entry != null) {
			addEntry(entry);
		}

		// Return what we got
		return entry;
	}

	public final class Entry {
		@LauncherAPI public final UUID uuid;
		private String username;
		private String accessToken;
		private String serverID;

		@LauncherAPI
		public Entry(UUID uuid, String username, String accessToken, String serverID) {
			this.uuid = Objects.requireNonNull(uuid, "uuid");
			this.username = Objects.requireNonNull(username, "username");
			this.accessToken = accessToken == null ? null : SecurityHelper.verifyToken(accessToken);
			this.serverID = serverID == null ? null : JoinServerRequest.verifyServerID(serverID);
		}
	}
}
