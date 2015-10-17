package launchserver.command.basic;

import launchserver.LaunchServer;
import launchserver.command.Command;

public final class ReloadConfigCommand extends Command {
	public ReloadConfigCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Reload LaunchServer.cfg file";
	}

	@Override
	public void invoke(String... args) throws Exception {
		server.reloadConfig();
	}
}