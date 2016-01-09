package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class BuildCommand extends Command {
	public BuildCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Build launcher binaries";
	}

	@Override
	public void invoke(String... args) throws Exception {
		server.buildLauncherBinaries();
		server.syncLauncherBinaries();
	}
}
