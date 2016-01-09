package net.sashok724.launcher.server.response.auth;

import java.io.IOException;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.helper.SecurityHelper;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.request.auth.JoinServerRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.auth.AuthException;
import net.sashok724.launcher.server.response.Response;

public final class JoinServerResponse extends Response {
	public JoinServerResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		String username = VerifyHelper.verifyUsername(input.readASCII(16));
		String accessToken = SecurityHelper.verifyToken(input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH));
		String serverID = JoinServerRequest.verifyServerID(input.readASCII(41)); // With minus sign

		// Try join server with auth handler
		debug("Username: '%s', Access token: %s, Server ID: %s", username, accessToken, serverID);
		boolean success;
		try {
			success = server.config.authHandler.joinServer(username, accessToken, serverID);
		} catch (AuthException e) {
			throw new RequestException(e.getMessage());
		} catch (Exception e) {
			LogHelper.error(e);
			throw new RequestException("error.auth.internalError");
		}
		writeNoError(output);

		// Write response
		output.writeBoolean(success);
	}
}
