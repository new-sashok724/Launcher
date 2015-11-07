package launchserver.response.auth;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.util.Arrays;
import java.util.UUID;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.auth.AuthException;
import launchserver.auth.provider.AuthProvider;
import launchserver.response.Response;
import launchserver.response.profile.ProfileByUUIDResponse;

public final class AuthResponse extends Response {
	public AuthResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws Exception {
		String login = input.readString(255);
		byte[] encryptedPassword = input.readByteArray(IOHelper.BUFFER_SIZE);

		// Decrypt password
		String password;
		try {
			password = IOHelper.decode(SecurityHelper.newRSADecryptCipher(server.privateKey).
				doFinal(encryptedPassword));
		} catch (IllegalBlockSizeException | BadPaddingException ignored) {
			requestError("Password decryption error");
			return;
		}

		// Authenticate
		debug("Login: '%s', Password: '%s'", login, echo(password.length()));
		String username;
		try {
			username = server.config.authProvider.auth(login, password);
			if (!VerifyHelper.isValidUsername(username)) {
				AuthProvider.authError(String.format("Illegal username: '%s'", username));
				return;
			}
		} catch (AuthException e) {
			requestError(e.getMessage());
			return;
		} catch (Exception e) {
			LogHelper.error(e);
			requestError("Internal auth provider error");
			return;
		}
		debug("Auth: '%s' -> '%s'", login, username);

		// Authenticate on server (and get UUID)
		String accessToken = SecurityHelper.randomStringToken();
		UUID uuid;
		try {
			uuid = server.config.authHandler.auth(username, accessToken);
		} catch (AuthException e) {
			requestError(e.getMessage());
			return;
		} catch (Exception e) {
			LogHelper.error(e);
			requestError("Internal auth handler error");
			return;
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
