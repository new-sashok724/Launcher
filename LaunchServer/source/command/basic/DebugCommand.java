package launchserver.command.basic;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

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
		if (args.length >= 1) {
			LogHelper.setDebugEnabled(Boolean.parseBoolean(args[0]));
		}
		LogHelper.subInfo("Debug enabled: " + LogHelper.isDebugEnabled());
	}
}
