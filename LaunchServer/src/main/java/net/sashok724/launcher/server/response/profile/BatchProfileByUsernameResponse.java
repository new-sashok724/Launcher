package net.sashok724.launcher.server.response.profile;

import java.io.IOException;
import java.util.Arrays;

import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.uuid.BatchProfileByUsernameRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class BatchProfileByUsernameResponse extends Response {
	public BatchProfileByUsernameResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		String[] usernames = new String[input.readLength(BatchProfileByUsernameRequest.MAX_BATCH_SIZE)];
		for (int i = 0; i < usernames.length; i++) {
			usernames[i] = VerifyHelper.verifyUsername(input.readASCII(16));
		}
		debug("Usernames: " + Arrays.toString(usernames));

		// Respond with profiles array
		for (String username : usernames) {
			ProfileByUsernameResponse.writeProfile(server, output, username);
		}
	}
}
