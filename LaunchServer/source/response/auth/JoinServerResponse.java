package launchserver.response.auth;

import java.io.IOException;

import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.auth.JoinServerRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.response.Response;

public final class JoinServerResponse extends Response {
	public JoinServerResponse(LaunchServer server, HInput input, HOutput output) {
		super(server, input, output);
	}

	@Override
	public void reply() throws IOException {
		String username = VerifyHelper.verifyUsername(input.readASCII(16));
		String accessToken = SecurityHelper.verifyToken(input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH));
		String serverID = JoinServerRequest.verifyServerID(input.readASCII(41)); // With minus sign

		// Debug print message
		LogHelper.subDebug("Username: '%s', access token: %s, server ID: %s", username, accessToken, serverID);

		// Try join server with auth manager
		output.writeBoolean(server.getConfig().authHandler.joinServer(username, accessToken, serverID));
	}
}
