package launchserver.response.auth;

import java.io.IOException;

import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.auth.JoinServerRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.response.Response;

public final class JoinServerResponse extends Response {
	public JoinServerResponse(LaunchServer server, int id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		String username = VerifyHelper.verifyUsername(input.readASCII(16));
		String accessToken = SecurityHelper.verifyToken(input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH));
		String serverID = JoinServerRequest.verifyServerID(input.readASCII(41)); // With minus sign

		// Try join server with auth manager
		debug("Username: '%s', Access token: %s, Server ID: %s", username, accessToken, serverID);
		output.writeBoolean(server.getConfig().authHandler.joinServer(username, accessToken, serverID));
	}
}
