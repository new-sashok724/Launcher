package launcher.request.auth;

import java.io.IOException;
import java.util.Arrays;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class AuthRequest extends Request<AuthRequest.Result> {
	private final String login;
	private final byte[] encryptedPassword;

	@LauncherAPI
	public AuthRequest(Launcher.Config config, String login, byte[] encryptedPassword) {
		super(config);
		this.login = VerifyHelper.verify(login, VerifyHelper.NOT_EMPTY, "Login can't be empty");
		this.encryptedPassword = Arrays.copyOf(encryptedPassword, encryptedPassword.length);
	}

	@LauncherAPI
	public AuthRequest(String login, byte[] encryptedPassword) {
		this(null, login, encryptedPassword);
	}

	@Override
	public Type getType() {
		return Type.AUTH;
	}

	@Override
	protected Result requestDo(HInput input, HOutput output) throws IOException {
		output.writeString(login, 255);
		output.writeByteArray(encryptedPassword, IOHelper.BUFFER_SIZE);
		output.flush();

		// Read UUID and access token
		readError(input);
		PlayerProfile pp = new PlayerProfile(input);
		String accessToken = input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH);
		return new Result(pp, accessToken);
	}

	public static final class Result {
		@LauncherAPI public final PlayerProfile pp;
		@LauncherAPI public final String accessToken;

		private Result(PlayerProfile pp, String accessToken) {
			this.pp = pp;
			this.accessToken = accessToken;
		}
	}
}
