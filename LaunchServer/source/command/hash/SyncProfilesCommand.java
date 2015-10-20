package launchserver.command.hash;

import java.io.IOException;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class SyncProfilesCommand extends Command {
	public SyncProfilesCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return null;
	}

	@Override
	public String getUsageDescription() {
		return "Resync profiles dir";
	}

	@Override
	public void invoke(String... args) throws IOException {
		server.syncProfilesDir();
		LogHelper.subInfo("Profiles successfully resynced");
	}
}
