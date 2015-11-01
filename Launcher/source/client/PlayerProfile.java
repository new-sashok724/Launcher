package launcher.client;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;

public final class PlayerProfile extends StreamObject {
	// Instance
	@LauncherAPI public final UUID uuid;
	@LauncherAPI public final String username;

	// Textures properties
	@LauncherAPI public final String skinURL;
	@LauncherAPI public final String cloakURL;

	@LauncherAPI
	public PlayerProfile(HInput input) throws IOException {
		uuid = input.readUUID();
		username = VerifyHelper.verifyUsername(input.readASCII(16));

		// Read textures
		skinURL = input.readBoolean() ? IOHelper.verifyURL(input.readASCII(2048)) : null;
		cloakURL = input.readBoolean() ? IOHelper.verifyURL(input.readASCII(2048)) : null;
	}

	@LauncherAPI
	public PlayerProfile(UUID uuid, String username, String skinURL, String cloakURL) {
		this.uuid = Objects.requireNonNull(uuid, "uuid");
		this.username = VerifyHelper.verifyUsername(username);
		this.skinURL = skinURL == null ? null : IOHelper.verifyURL(skinURL);
		this.cloakURL = cloakURL == null ? null : IOHelper.verifyURL(cloakURL);
	}

	@Override
	public void write(HOutput output) throws IOException {
		output.writeUUID(uuid);
		output.writeASCII(username, 16);

		// Write textures
		output.writeBoolean(skinURL != null);
		if (skinURL != null) {
			output.writeASCII(skinURL, 2048);
		}
		output.writeBoolean(cloakURL != null);
		if (cloakURL != null) {
			output.writeASCII(cloakURL, 2048);
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
}
