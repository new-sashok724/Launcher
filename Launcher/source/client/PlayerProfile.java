package launcher.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.UUID;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;

public final class PlayerProfile extends StreamObject {
	@LauncherAPI public static final int TEXTURE_TIMEOUT = VerifyHelper.verifyInt(
		Integer.parseUnsignedInt(System.getProperty("launcher.textureTimeout", Integer.toString(5000))),
		VerifyHelper.POSITIVE, "launcher.textureTimeout can't be <= 0");

	// Instance
	@LauncherAPI public final UUID uuid;
	@LauncherAPI public final String username;
	@LauncherAPI public final Texture skin, cloak;

	@LauncherAPI
	public PlayerProfile(HInput input) throws IOException {
		uuid = input.readUUID();
		username = VerifyHelper.verifyUsername(input.readASCII(16));
		skin = input.readBoolean() ? new Texture(input) : null;
		cloak = input.readBoolean() ? new Texture(input) : null;
	}

	@LauncherAPI
	public PlayerProfile(UUID uuid, String username, Texture skin, Texture cloak) {
		this.uuid = Objects.requireNonNull(uuid, "uuid");
		this.username = VerifyHelper.verifyUsername(username);
		this.skin = skin;
		this.cloak = cloak;
	}

	@Override
	public void write(HOutput output) throws IOException {
		output.writeUUID(uuid);
		output.writeASCII(username, 16);

		// Write textures
		output.writeBoolean(skin != null);
		if (skin != null) {
			skin.write(output);
		}
		output.writeBoolean(cloak != null);
		if (cloak != null) {
			cloak.write(output);
		}
	}

	@LauncherAPI
	public static PlayerProfile newOfflineProfile(String username) {
		return new PlayerProfile(offlineUUID(username), username, null, null);
	}

	@LauncherAPI
	public static UUID offlineUUID(String username) {
		return UUID.nameUUIDFromBytes(IOHelper.encodeASCII("OfflinePlayer:" + username));
	}

	public static final class Texture extends StreamObject {
		@LauncherAPI public final String url;
		@LauncherAPI public final byte[] digest;

		@LauncherAPI
		public Texture(String url, byte[] digest) {
			this.url = IOHelper.verifyURL(url);
			this.digest = Objects.requireNonNull(digest, "digest");
		}

		@LauncherAPI
		public Texture(String url) throws IOException {
			this.url = IOHelper.verifyURL(url);
			try (InputStream input = IOHelper.newInput(new URL(url), TEXTURE_TIMEOUT)) {
				digest = SecurityHelper.digest(SecurityHelper.DigestAlgorithm.SHA256, input);
			}
		}

		@LauncherAPI
		public Texture(HInput input) throws IOException {
			this.url = IOHelper.verifyURL(input.readASCII(2048));
			this.digest = input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH);
		}

		@Override
		public void write(HOutput output) throws IOException {
			output.writeASCII(url, 2048);
			output.writeByteArray(digest, SecurityHelper.CRYPTO_MAX_LENGTH);
		}
	}
}
