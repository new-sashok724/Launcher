package launchserver.response.profile;

import java.io.IOException;
import java.util.Arrays;

import launcher.helper.VerifyHelper;
import launcher.request.uuid.BatchProfileByUsernameRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.response.Response;

public final class BatchProfileByUsernameResponse extends Response {
	public BatchProfileByUsernameResponse(LaunchServer server, int id, HInput input, HOutput output) {
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
