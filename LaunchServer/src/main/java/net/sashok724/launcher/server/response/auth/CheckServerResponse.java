package net.sashok724.launcher.server.response.auth;

import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.request.auth.JoinServerRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.auth.AuthException;
import net.sashok724.launcher.server.response.Response;
import net.sashok724.launcher.server.response.profile.ProfileByUUIDResponse;

public final class CheckServerResponse extends Response {
	public CheckServerResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		String username = VerifyHelper.verifyUsername(input.readASCII(16));
		String serverID = JoinServerRequest.verifyServerID(input.readASCII(41)); // With minus sign
		debug("Username: %s, Server ID: %s", username, serverID);

		// Try check server with auth handler
		UUID uuid;
		try {
			uuid = server.config.authHandler.checkServer(username, serverID);
		} catch (AuthException e) {
			throw new RequestException(e.getMessage());
		} catch (Exception e) {
			LogHelper.error(e);
			throw new RequestException("error.auth.internalError");
		}
		writeNoError(output);

		// Write profile and UUID
		output.writeBoolean(uuid != null);
		if (uuid != null) {
			ProfileByUUIDResponse.getProfile(server, uuid, username).write(output);
		}
	}
}
