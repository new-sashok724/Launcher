package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class LogConnectionsCommand extends Command {
	public LogConnectionsCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "[true/false]";
	}

	@Override
	public String getUsageDescription() {
		return "Enable or disable logging connections";
	}

	@Override
	public void invoke(String... args) {
		boolean newValue;
		if (args.length >= 1) {
			newValue = Boolean.parseBoolean(args[0]);
			server.serverSocketHandler.logConnections = newValue;
		} else {
			newValue = server.serverSocketHandler.logConnections;
		}
		LogHelper.subInfo("Log connections: " + newValue);
	}
}
