package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class DebugCommand extends Command {
	public DebugCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "[true/false]";
	}

	@Override
	public String getUsageDescription() {
		return "Enable or disable debug logging at runtime";
	}

	@Override
	public void invoke(String... args) {
		boolean newValue;
		if (args.length >= 1) {
			newValue = Boolean.parseBoolean(args[0]);
			LogHelper.setDebugEnabled(newValue);
		} else {
			newValue = LogHelper.isDebugEnabled();
		}
		LogHelper.subInfo("Debug enabled: " + newValue);
	}
}
