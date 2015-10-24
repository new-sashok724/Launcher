package launchserver.response;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.helper.LogHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

public abstract class Response {
	@LauncherAPI protected final LaunchServer server;
	@LauncherAPI protected final int id;
	@LauncherAPI protected final HInput input;
	@LauncherAPI protected final HOutput output;

	@LauncherAPI
	protected Response(LaunchServer server, int id, HInput input, HOutput output) {
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
		Response newResponse(LaunchServer server, int id, HInput input, HOutput output);
	}
}
