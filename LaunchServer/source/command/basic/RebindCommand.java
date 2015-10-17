package launchserver.command.basic;

import launchserver.LaunchServer;
import launchserver.command.Command;

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
