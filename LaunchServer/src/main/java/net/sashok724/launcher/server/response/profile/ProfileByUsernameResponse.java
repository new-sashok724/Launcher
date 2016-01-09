package net.sashok724.launcher.server.response.profile;

import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class ProfileByUsernameResponse extends Response {
	public ProfileByUsernameResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		String username = VerifyHelper.verifyUsername(input.readASCII(16));
		debug("Username: " + username);

		// Write response
		writeProfile(server, output, username);
	}

	public static void writeProfile(LaunchServer server, HOutput output, String username) throws IOException {
		UUID uuid = server.config.authHandler.usernameToUUID(username);
		if (uuid == null) {
			output.writeBoolean(false);
			return;
		}

		// Write profile
		output.writeBoolean(true);
		ProfileByUUIDResponse.getProfile(server, uuid, username).write(output);
	}
}
