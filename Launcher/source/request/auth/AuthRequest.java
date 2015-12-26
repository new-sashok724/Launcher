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
import launcher.serialize.stream.StreamObject;

public final class AuthRequest extends Request<AuthRequest.Result> {
	private final String login;
	private final byte[] password;

	@LauncherAPI
	public AuthRequest(Launcher.Config config, String login, byte[] password) {
		super(config);
		this.login = VerifyHelper.verify(login, VerifyHelper.NOT_EMPTY, "Login can't be empty");
		this.password = Arrays.copyOf(password, password.length);
	}

	@LauncherAPI
	public AuthRequest(String login, byte[] password) {
		this(null, login, password);
	}

	@Override
	public Type getType() {
		return Type.AUTH;
	}

	@Override
	protected Result requestDo(HInput input, HOutput output) throws IOException {
		output.writeString(login, 255);
		output.writeByteArray(password, IOHelper.BUFFER_SIZE);
		output.flush();

		// Read UUID and access token
		readError(input);
		return new Result(input);
	}

	public static final class Result extends StreamObject {
		@LauncherAPI public final PlayerProfile pp;
		@LauncherAPI public final String accessToken;

		@LauncherAPI
		public Result(HInput input) throws IOException {
			pp = new PlayerProfile(input);
			accessToken = input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH);
		}

		@LauncherAPI
		public Result(PlayerProfile pp, String accessToken) {
			this.pp = pp;
			this.accessToken = accessToken;
		}

		@Override
		public void write(HOutput output) throws IOException {
			pp.write(output);
			output.writeASCII(accessToken, -SecurityHelper.TOKEN_STRING_LENGTH);
		}
	}
}
