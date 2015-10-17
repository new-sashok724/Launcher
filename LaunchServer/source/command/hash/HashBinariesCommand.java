package launchserver.command.hash;

import java.io.IOException;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class HashBinariesCommand extends Command {
	public HashBinariesCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Rehash launcher binaries";
	}

	@Override
	public void invoke(String... args) throws IOException {
		server.hashLauncherBinaries();
		LogHelper.subInfo("Binaries successfully rehashed");
	}
}
