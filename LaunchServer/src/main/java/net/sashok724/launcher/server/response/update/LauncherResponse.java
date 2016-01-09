package net.sashok724.launcher.server.response.update;

import java.io.IOException;

import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.request.update.LauncherUpdateRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.signed.SignedBytesHolder;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class LauncherResponse extends Response {
	public LauncherResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		// Resolve launcher binary
		SignedBytesHolder bytes = (input.readBoolean() ? server.launcherEXEBinary : server.launcherBinary).getBytes();
		if (bytes == null) {
			throw new RequestException("error.launcher.missingBinary");
		}
		writeNoError(output);

		// Write request result
		LauncherUpdateRequest.Result result = new LauncherUpdateRequest.Result(bytes.getSign(), server.getProfiles());
		result.write(output);
		output.flush();

		// Send binary if requested
		if (input.readBoolean()) {
			output.writeByteArray(bytes.getBytes(), 0);
		}
	}
}
