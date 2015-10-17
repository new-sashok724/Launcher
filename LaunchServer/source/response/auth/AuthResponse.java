package launchserver.response.auth;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.util.Arrays;
import java.util.UUID;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.RequestException;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.auth.AuthException;
import launchserver.response.Response;
import launchserver.response.profile.ProfileByUUIDResponse;

public final class AuthResponse extends Response {
	public AuthResponse(LaunchServer server, HInput input, HOutput output) {
		super(server, input, output);
	}

	@Override
	public void reply() throws Exception {
		String login = input.readString(255);
		byte[] encryptedPassword = input.readByteArray(IOHelper.BUFFER_SIZE);

		// Decrypt password
		String password;
		try {
			password = IOHelper.decode(SecurityHelper.newRSADecryptCipher(server.getPrivateKey()).doFinal(encryptedPassword));
		} catch (IllegalBlockSizeException | BadPaddingException ignored) {
			throw new RequestException("Password decryption error");
		}

		// Authenticate
		LaunchServer.Config config = server.getConfig();
		LogHelper.subDebug("Login: '%s', password: '%s'", login, echo(password.length()));
		String username;
		try {
			username = VerifyHelper.verifyUsername(config.authProvider.auth(login, password));
		} catch (AuthException e) {
			throw new RequestException(e);
		} catch (Exception e) {
			LogHelper.error(e);
			throw new RequestException("Internal auth error", e);
		}
		LogHelper.subDebug("Auth: '%s' -> '%s'", login, username);

		// Authenticate on server (and get UUID)
		String accessToken = SecurityHelper.randomStringToken();
		UUID uuid = config.authHandler.auth(username, accessToken);
		if (uuid == null) {
			throw new RequestException("Can't assign UUID");
		}
		writeNoError(output);

		// Write profile and UUID
		ProfileByUUIDResponse.getProfile(server, uuid, username).write(output);
		output.writeASCII(accessToken, -SecurityHelper.TOKEN_STRING_LENGTH);
	}

	private static String echo(int length) {
		char[] chars = new char[length];
		Arrays.fill(chars, '*');
		return new String(chars);
	}
}
