package launchserver.response.auth;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.util.Arrays;
import java.util.UUID;

import launcher.client.PlayerProfile;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.RequestException;
import launcher.request.auth.AuthRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.auth.AuthException;
import launchserver.response.Response;
import launchserver.response.profile.ProfileByUUIDResponse;

public final class AuthResponse extends Response {
	public AuthResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws Exception {
		String login = input.readString(255);
		byte[] encryptedPassword = input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH);

		// Decrypt password
		String password;
		try {
			password = IOHelper.decode(SecurityHelper.newRSADecryptCipher(server.privateKey).
				doFinal(encryptedPassword));
		} catch (IllegalBlockSizeException | BadPaddingException ignored) {
			throw new RequestException("error.auth.passwordDecryptionError");
		}

		// Authenticate
		debug("Login: '%s', Password: '%s'", login, echo(password.length()));
		String username;
		try {
			username = server.config.authProvider.auth(login, password);
			if (!VerifyHelper.isValidUsername(username)) {
				throw new IllegalArgumentException(String.format("Illegal username: '%s'", username));
			}
		} catch (AuthException e) {
			throw new RequestException(e.getMessage());
		} catch (Exception e) {
			LogHelper.error(e);
			throw new RequestException("error.auth.internalError");
		}
		debug("Auth: '%s' -> '%s'", login, username);

		// Authenticate on server (and get UUID)
		String accessToken = SecurityHelper.randomStringToken();
		UUID uuid;
		try {
			uuid = server.config.authHandler.auth(username, accessToken);
		} catch (AuthException e) {
			throw new RequestException(e.getMessage());
		} catch (Exception e) {
			LogHelper.error(e);
			throw new RequestException("error.auth.internalError");
		}
		writeNoError(output);

		// Write profile and UUID
		PlayerProfile profile = ProfileByUUIDResponse.getProfile(server, uuid, username);
		AuthRequest.Result result = new AuthRequest.Result(profile, accessToken);
		result.write(output);
	}

	private static String echo(int length) {
		char[] chars = new char[length];
		Arrays.fill(chars, '*');
		return new String(chars);
	}
}
