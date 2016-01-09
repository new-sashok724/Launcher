package net.sashok724.launcher.server.response;

import java.io.IOException;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.server.LaunchServer;

public abstract class Response {
	@LauncherAPI protected final LaunchServer server;
	@LauncherAPI protected final long id;
	@LauncherAPI protected final HInput input;
	@LauncherAPI protected final HOutput output;

	@LauncherAPI
	protected Response(LaunchServer server, long id, HInput input, HOutput output) {
		this.server = server;
		this.id = id;
		this.input = input;
		this.output = output;
	}

	@LauncherAPI
	public abstract void reply() throws Exception;

	@LauncherAPI
	protected final void debug(String message) {
		LogHelper.subDebug("#%d %s", id, message);
	}

	@LauncherAPI
	protected final void debug(String message, Object... args) {
		debug(String.format(message, args));
	}

	@LauncherAPI
	protected final void writeNoError(HOutput output) throws IOException {
		output.writeString("", 0);
	}

	@FunctionalInterface
	public interface Factory {
		@LauncherAPI
		Response newResponse(LaunchServer server, long id, HInput input, HOutput output);
	}
}
