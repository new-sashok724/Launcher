package net.sashok724.launcher.server.command.hash;

import java.io.IOException;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;

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
