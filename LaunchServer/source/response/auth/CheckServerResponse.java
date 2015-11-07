package launchserver.response.auth;

import java.io.IOException;
import java.util.UUID;

import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.request.auth.JoinServerRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.auth.AuthException;
import launchserver.response.Response;
import launchserver.response.profile.ProfileByUUIDResponse;

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
			requestError(e.getMessage());
			return;
		} catch (Exception e) {
			LogHelper.error(e);
			requestError("Internal auth handler error");
			return;
		}
		writeNoError(output);

		// Write profile and UUID
		output.writeBoolean(uuid != null);
		if (uuid != null) {
			ProfileByUUIDResponse.getProfile(server, uuid, username).write(output);
		}
	}
}
