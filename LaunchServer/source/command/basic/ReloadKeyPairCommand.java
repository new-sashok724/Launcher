package launchserver.command.basic;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class ReloadKeyPairCommand extends Command {
	public ReloadKeyPairCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Reload public.key and private.key files";
	}

	@Override
	public void invoke(String... args) throws Exception {
		server.reloadKeyPair();
		LogHelper.subInfo("Key pair reloaded");
	}
}