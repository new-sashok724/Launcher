package launchserver.response;

import java.io.IOException;

import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

public final class PingResponse extends Response {
	public PingResponse(LaunchServer server, HInput input, HOutput output) {
		super(server, input, output);
	}

	@Override
	public void reply() throws IOException {
		output.writeUnsignedByte(123);
	}
}