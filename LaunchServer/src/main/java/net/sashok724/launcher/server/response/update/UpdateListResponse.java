package net.sashok724.launcher.server.response.update;

import java.util.Map;
import java.util.Set;

import net.sashok724.launcher.client.hasher.HashedDir;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.signed.SignedObjectHolder;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class UpdateListResponse extends Response {
	public UpdateListResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws Exception {
		Set<Map.Entry<String, SignedObjectHolder<HashedDir>>> updateDirs = server.getUpdateDirs();

		// Write all update dirs names
		output.writeLength(updateDirs.size(), 0);
		for (Map.Entry<String, SignedObjectHolder<HashedDir>> entry : updateDirs) {
			output.writeString(entry.getKey(), 255);
		}
	}
}
