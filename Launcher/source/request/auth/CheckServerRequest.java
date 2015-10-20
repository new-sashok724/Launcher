package launcher.request.auth;

import java.io.IOException;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class CheckServerRequest extends Request<PlayerProfile> {
	private final String username;
	private final String serverID;

	@LauncherAPI
	public CheckServerRequest(Launcher.Config config, String username, String serverID) {
		super(config);
		this.username = VerifyHelper.verifyUsername(username);
		this.serverID = JoinServerRequest.verifyServerID(serverID);
	}

	@LauncherAPI
	public CheckServerRequest(String username, String serverID) {
		this(null, username, serverID);
	}

	@Override
	public Type getType() {
		return Type.CHECK_SERVER;
	}

	@Override
	protected PlayerProfile requestDo(HInput input, HOutput output) throws IOException {
		output.writeASCII(username, 16);
		output.writeASCII(serverID, 41); // 1 char for minus sign
		output.flush();

		// Read response
		return input.readBoolean() ? new PlayerProfile(input) : null;
	}
}
