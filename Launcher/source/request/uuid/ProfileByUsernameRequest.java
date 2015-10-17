package launcher.request.uuid;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class ProfileByUsernameRequest extends Request<PlayerProfile> {
	private final String username;

	@LauncherAPI
	public ProfileByUsernameRequest(String username) {
		this.username = VerifyHelper.verifyUsername(username);
	}

	@Override
	public Type getType() {
		return Type.PROFILE_BY_USERNAME;
	}

	@Override
	protected PlayerProfile requestDo(HInput input, HOutput output) throws IOException {
		output.writeASCII(username, 16);
		output.flush();

		// Return profile
		return input.readBoolean() ? new PlayerProfile(input) : null;
	}
}
