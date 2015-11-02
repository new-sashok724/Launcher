package launchserver.auth.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.TextConfigReader;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.ConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

public final class FileAuthProvider extends DigestAuthProvider {
	private final Path file;

	// Cache
	private final Map<String, Entry> entries = new HashMap<>(IOHelper.BUFFER_SIZE);
	private final Object cacheLock = new Object();
	private FileTime cacheLastModified;

	public FileAuthProvider(BlockConfigEntry block) {
		super(block);
		file = IOHelper.toPath(block.getEntryValue("file", StringConfigEntry.class));

		// Try to update cache
		try {
			updateCache();
		} catch (IOException e) {
			LogHelper.error(e);
		}
	}

	@Override
	public String auth(String login, String password) throws IOException {
		Entry entry;
		synchronized (cacheLock) {
			updateCache();
			entry = entries.get(CommonHelper.low(login));
		}

		// Verify digest and return true username
		verifyDigest(entry.password, password);
		return entry.username;
	}

	@Override
	public void flush() {
		// Do nothing
	}

	private void updateCache() throws IOException {
		FileTime lastModified = IOHelper.readAttributes(file).lastModifiedTime();
		if (lastModified.equals(cacheLastModified)) {
			return; // Not modified, so cache is up-to-date
		}

		// Read file
		LogHelper.info("Recaching auth provider file: '%s'", file);
		BlockConfigEntry authFile;
		try (BufferedReader reader = IOHelper.newReader(file)) {
			authFile = TextConfigReader.read(reader, false);
		}

		// Read entries from config block
		entries.clear();
		Set<Map.Entry<String, ConfigEntry<?>>> entrySet = authFile.getValue().entrySet();
		for (Map.Entry<String, ConfigEntry<?>> entry : entrySet) {
			String login = entry.getKey();
			ConfigEntry<?> value = VerifyHelper.verify(entry.getValue(), v -> v.getType() == ConfigEntry.Type.BLOCK,
				String.format("Illegal config entry type: '%s'", login));

			// Add auth entry
			Entry auth = new Entry((BlockConfigEntry) value);
			VerifyHelper.putIfAbsent(entries, CommonHelper.low(login), auth,
				String.format("Duplicate login: '%s'", login));
		}

		// Update last modified time
		cacheLastModified = lastModified;
	}

	private static final class Entry extends ConfigObject {
		private final String username;
		private final String password;

		private Entry(BlockConfigEntry block) {
			super(block);
			this.username = VerifyHelper.verifyUsername(block.getEntryValue("username", StringConfigEntry.class));
			this.password = VerifyHelper.verify(block.getEntryValue("password", StringConfigEntry.class),
				VerifyHelper.NOT_EMPTY, String.format("Password can't be empty: '%s'", username));
		}
	}
}
