package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.client.helper.JVMHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class StopCommand extends Command {
	public StopCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Stop LaunchServer";
	}

	@Override
	@SuppressWarnings("CallToSystemExit")
	public void invoke(String... args) {
		JVMHelper.RUNTIME.exit(0);
	}
}
