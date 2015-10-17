package launchserver.command.hash;

import java.io.IOException;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class HashProfilesCommand extends Command {
	public HashProfilesCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Rehash profiles dir";
	}

	@Override
	public void invoke(String... args) throws IOException {
		server.hashProfilesDir();
		LogHelper.subInfo("Profiles successfully rehashed");
	}
}
