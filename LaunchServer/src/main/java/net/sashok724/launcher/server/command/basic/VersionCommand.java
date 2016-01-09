package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class VersionCommand extends Command {
	public VersionCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Print LaunchServer version";
	}

	@Override
	public void invoke(String... args) throws Exception {
		LogHelper.subInfo("LaunchServer version: %s (build #%s)", Launcher.VERSION, Launcher.BUILD);
	}
}
