package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class ClearCommand extends Command {
	public ClearCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Clear terminal";
	}

	@Override
	public void invoke(String... args) throws Exception {
		server.commandHandler.clear();
		LogHelper.subInfo("Terminal cleared");
	}
}
