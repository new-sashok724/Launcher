package launchserver.response;

import java.io.IOException;

import launcher.request.PingRequest;
import launcher.transport.HInput;
import launcher.transport.HOutput;
import launchserver.LaunchServer;

public final class PingResponse extends Response {
	public PingResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		output.writeUnsignedByte(PingRequest.EXPECTED_BYTE);
	}
}
