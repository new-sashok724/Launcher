package launchserver.response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;

import launcher.Launcher;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.request.RequestException;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;
import launchserver.response.auth.AuthResponse;
import launchserver.response.auth.CheckServerResponse;
import launchserver.response.auth.JoinServerResponse;
import launchserver.response.profile.BatchProfileByUsernameResponse;
import launchserver.response.profile.ProfileByUUIDResponse;
import launchserver.response.profile.ProfileByUsernameResponse;
import launchserver.response.update.LauncherResponse;
import launchserver.response.update.UpdateResponse;

public final class ResponseThread implements Runnable {
	private static final boolean LOG_CONNECTIONS = Boolean.getBoolean("launcher.logConnections");

	// Instance
	private final LaunchServer server;
	private final Socket socket;

	public ResponseThread(LaunchServer server, Socket socket) throws SocketException {
		this.server = server;
		this.socket = socket;
		IOHelper.setSocketFlags(socket);
	}

	@Override
	public void run() {
		LogHelper.debug("Connection from %s", IOHelper.getIP(socket.getRemoteSocketAddress()));
		try (InputStream is = socket.getInputStream(); OutputStream os = socket.getOutputStream();
			 HInput input = new HInput(is); HOutput output = new HOutput(os)) {
			readHandshake(input, output);
			try {
				respond(input, output);
			} catch (RequestException e) {
				output.writeString(e.toString(), 0);
			}
		} catch (Exception e) {
			LogHelper.error(e);
		} finally {
			server.serverSocketHandler.onDisconnected(socket);
			IOHelper.close(socket);
		}
	}

	private void readHandshake(HInput input, HOutput output) throws IOException {
		// Verify magic number
		int magicNumber = input.readInt();
		if (magicNumber != Launcher.PROTOCOL_MAGIC) {
			output.writeBoolean(false);
			throw new IOException("Protocol magic mismatch");
		}

		// Verify key modulus
		BigInteger keyModulus = input.readBigInteger(SecurityHelper.RSA_KEY_LENGTH + 1);
		if (!keyModulus.equals(server.getPrivateKey().getModulus())) {
			output.writeBoolean(false);
			throw new IOException("Key modulus mismatch");
		}

		// Protocol successfully verified
		output.writeBoolean(true);
		output.flush();
	}

	private void respond(HInput input, HOutput output) throws Exception {
		Request.Type type = Request.Type.read(input);
		if (LOG_CONNECTIONS) {
			LogHelper.info("Connection from %s: %s", IOHelper.getIP(socket.getRemoteSocketAddress()), type.name());
		} else {
			LogHelper.subDebug("Type: " + type.name());
		}

		// Choose response based on type
		Response response;
		switch (type) {
			case PING:
				response = new PingResponse(server, input, output);
				break;
			case AUTH:
				response = new AuthResponse(server, input, output);
				break;
			case JOIN_SERVER:
				response = new JoinServerResponse(server, input, output);
				break;
			case CHECK_SERVER:
				response = new CheckServerResponse(server, input, output);
				break;
			case LAUNCHER:
				response = new LauncherResponse(server, input, output);
				break;
			case UPDATE:
				response = new UpdateResponse(server, input, output);
				break;
			case PROFILE_BY_USERNAME:
				response = new ProfileByUsernameResponse(server, input, output);
				break;
			case PROFILE_BY_UUID:
				response = new ProfileByUUIDResponse(server, input, output);
				break;
			case BATCH_PROFILE_BY_USERNAME:
				response = new BatchProfileByUsernameResponse(server, input, output);
				break;
			case CUSTOM:
				String name = VerifyHelper.verifyIDName(input.readASCII(255));
				response = server.serverSocketHandler.newCustomResponse(name, input, output);
				break;
			default:
				throw new AssertionError("Unsupported request type: " + type.name());
		}

		// Reply
		response.reply();
		LogHelper.subDebug("Successfully replied");
	}
}
