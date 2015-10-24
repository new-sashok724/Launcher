package launchserver.response;

import java.io.IOException;
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
import launchserver.response.update.UpdateListResponse;
import launchserver.response.update.UpdateResponse;

public final class ResponseThread implements Runnable {
	private static final boolean LOG_CONNECTIONS = Boolean.getBoolean("launcher.logConnections");

	// Instance
	private final LaunchServer server;
	private final int id;
	private final Socket socket;

	public ResponseThread(LaunchServer server, int id, Socket socket) throws SocketException {
		this.server = server;
		this.id = id;
		this.socket = socket;

		// Fix socket flags
		IOHelper.setSocketFlags(socket);
	}

	@Override
	public void run() {
		boolean cancelled = false;
		Exception savedError = null;

		// Process connection
		LogHelper.debug("Connection #%d from %s", id,
			IOHelper.getIP(socket.getRemoteSocketAddress()));
		try (HInput input = new HInput(socket.getInputStream());
			 HOutput output = new HOutput(socket.getOutputStream())) {
			Request.Type type = readHandshake(input, output);
			if (type == null) { // Not accepted
				cancelled = true;
				return;
			}

			// Start response
			try {
				respond(type, input, output);
			} catch (RequestException e) {
				LogHelper.debug(String.format("#%d Request error: %s", id, e.getMessage()));
				output.writeString(e.getMessage(), 0);
			}
		} catch (Exception e) {
			savedError = e;
			LogHelper.error(e);
		} finally {
			IOHelper.close(socket);
			if (!cancelled) {
				server.serverSocketHandler.onDisconnect(id, savedError);
			}
		}
	}

	private Request.Type readHandshake(HInput input, HOutput output) throws IOException {
		// Verify magic number
		int magicNumber = input.readInt();
		if (magicNumber != Launcher.PROTOCOL_MAGIC) {
			output.writeBoolean(false);
			throw new IOException(String.format("#%d Protocol magic mismatch", id));
		}

		// Verify key modulus
		BigInteger keyModulus = input.readBigInteger(SecurityHelper.RSA_KEY_LENGTH + 1);
		if (!keyModulus.equals(server.getPrivateKey().getModulus())) {
			output.writeBoolean(false);
			throw new IOException(String.format("#%d Key modulus mismatch", id));
		}

		// Read request type
		Request.Type type = Request.Type.read(input);
		if (!server.serverSocketHandler.onHandshake(id, type)) {
			output.writeBoolean(false);
			output.flush();
			return null;
		}

		// Protocol successfully verified
		output.writeBoolean(true);
		output.flush();
		return type;
	}

	private void respond(Request.Type type, HInput input, HOutput output) throws Exception {
		if (LOG_CONNECTIONS) {
			LogHelper.info("Connection from %s: %s", IOHelper.getIP(socket.getRemoteSocketAddress()), type.name());
		} else {
			LogHelper.subDebug("#%d Type: %s", id, type.name());
		}

		// Choose response based on type
		Response response;
		switch (type) {
			case PING:
				response = new PingResponse(server, id, input, output);
				break;
			case AUTH:
				response = new AuthResponse(server, id, input, output);
				break;
			case JOIN_SERVER:
				response = new JoinServerResponse(server, id, input, output);
				break;
			case CHECK_SERVER:
				response = new CheckServerResponse(server, id, input, output);
				break;
			case LAUNCHER:
				response = new LauncherResponse(server, id, input, output);
				break;
			case UPDATE:
				response = new UpdateResponse(server, id, input, output);
				break;
			case UPDATE_LIST:
				response = new UpdateListResponse(server, id, input, output);
				break;
			case PROFILE_BY_USERNAME:
				response = new ProfileByUsernameResponse(server, id, input, output);
				break;
			case PROFILE_BY_UUID:
				response = new ProfileByUUIDResponse(server, id, input, output);
				break;
			case BATCH_PROFILE_BY_USERNAME:
				response = new BatchProfileByUsernameResponse(server, id, input, output);
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
		LogHelper.subDebug("#%d Replied", id);
	}
}
