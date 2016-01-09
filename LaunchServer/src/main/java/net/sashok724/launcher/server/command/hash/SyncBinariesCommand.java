package net.sashok724.launcher.server.command.hash;

import java.io.IOException;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

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
