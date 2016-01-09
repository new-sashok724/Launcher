package net.sashok724.launcher.server.command.basic;

import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

public final class RebindCommand extends Command {
	public RebindCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Rebind server socket";
	}

	@Override
	public void invoke(String... args) throws Exception {
		server.rebindServerSocket();
	}
}
