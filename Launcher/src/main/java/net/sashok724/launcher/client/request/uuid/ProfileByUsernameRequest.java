package net.sashok724.launcher.client.request.uuid;

import java.io.IOException;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.Request;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

public final class ProfileByUsernameRequest extends Request<PlayerProfile> {
	private final String username;

	@LauncherAPI
	public ProfileByUsernameRequest(Launcher.Config config, String username) {
		super(config);
		this.username = VerifyHelper.verifyUsername(username);
	}

	@LauncherAPI
	public ProfileByUsernameRequest(String username) {
		this(null, username);
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
