package launchserver.command.hash;

import java.io.IOException;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class SyncBinariesCommand extends Command {
	public SyncBinariesCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Resync launcher binaries";
	}

	@Override
	public void invoke(String... args) throws IOException {
		server.syncLauncherBinaries();
		LogHelper.subInfo("Binaries successfully resynced");
	}
}
