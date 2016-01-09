package net.sashok724.launcher.server.response;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.helper.SecurityHelper;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.Request;
import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.auth.AuthResponse;
import net.sashok724.launcher.server.response.auth.CheckServerResponse;
import net.sashok724.launcher.server.response.auth.JoinServerResponse;
import net.sashok724.launcher.server.response.profile.BatchProfileByUsernameResponse;
import net.sashok724.launcher.server.response.profile.ProfileByUUIDResponse;
import net.sashok724.launcher.server.response.profile.ProfileByUsernameResponse;
import net.sashok724.launcher.server.response.update.LauncherResponse;
import net.sashok724.launcher.server.response.update.UpdateListResponse;
import net.sashok724.launcher.server.response.update.UpdateResponse;

public final class ResponseThread implements Runnable {
	private final LaunchServer server;
	private final long id;
	private final Socket socket;

	public ResponseThread(LaunchServer server, long id, Socket socket) throws SocketException {
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
		if (!server.serverSocketHandler.logConnections) {
			LogHelper.debug("Connection #%d from %s", id, IOHelper.getIP(socket.getRemoteSocketAddress()));
		}

		// Process connection
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
				LogHelper.subDebug(String.format("#%d Request error: %s", id, e.getMessage()));
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
		if (!keyModulus.equals(server.privateKey.getModulus())) {
			output.writeBoolean(false);
			throw new IOException(String.format("#%d Key modulus mismatch", id));
		}

		// Read request type
		Request.Type type = Request.Type.read(input);
		if (!server.serverSocketHandler.onHandshake(id, type)) {
			output.writeBoolean(false);
			return null;
		}

		// Protocol successfully verified
		output.writeBoolean(true);
		output.flush();
		return type;
	}

	private void respond(Request.Type type, HInput input, HOutput output) throws Exception {
		if (server.serverSocketHandler.logConnections) {
			LogHelper.info("Connection #%d from %s: %s", id, IOHelper.getIP(socket.getRemoteSocketAddress()), type.name());
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
				response = server.serverSocketHandler.newCustomResponse(name, id, input, output);
				break;
			default:
				throw new AssertionError("Unsupported request type: " + type.name());
		}

		// Reply
		response.reply();
		LogHelper.subDebug("#%d Replied", id);
	}
}
