package net.sashok724.launcher.server.response.profile;

import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class ProfileByUUIDResponse extends Response {
	public ProfileByUUIDResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		UUID uuid = input.readUUID();
		debug("UUID: " + uuid);

		// Verify has such profile
		String username = server.config.authHandler.uuidToUsername(uuid);
		if (username == null) {
			output.writeBoolean(false);
			return;
		}

		// Write profile
		output.writeBoolean(true);
		getProfile(server, uuid, username).write(output);
	}

	public static PlayerProfile getProfile(LaunchServer server, UUID uuid, String username) {
		// Get skin texture
		PlayerProfile.Texture skin;
		try {
			skin = server.config.textureProvider.getSkinTexture(uuid, username);
		} catch (IOException e) {
			LogHelper.error(new IOException(String.format("Can't get skin texture: '%s'", username), e));
			skin = null;
		}

		// Get cloak texture
		PlayerProfile.Texture cloak;
		try {
			cloak = server.config.textureProvider.getCloakTexture(uuid, username);
		} catch (IOException e) {
			LogHelper.error(new IOException(String.format("Can't get cloak texture: '%s'", username), e));
			cloak = null;
		}

		// Return combined profile
		return new PlayerProfile(uuid, username, skin, cloak);
	}
}
