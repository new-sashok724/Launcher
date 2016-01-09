package net.sashok724.launcher.server.response;

import java.io.IOException;

import net.sashok724.launcher.client.request.PingRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;

public final class PingResponse extends Response {
	public PingResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		output.writeUnsignedByte(PingRequest.EXPECTED_BYTE);
	}
}
