package net.sashok724.launcher.server.command.auth;

import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;
import net.sashok724.launcher.server.command.CommandException;

public final class UsernameToUUIDCommand extends Command {
	public UsernameToUUIDCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<username>";
	}

	@Override
	public String getUsageDescription() {
		return "Convert player username to UUID";
	}

	@Override
	public void invoke(String... args) throws CommandException, IOException {
		verifyArgs(args, 1);
		String username = parseUsername(args[0]);

		// Get UUID by username
		UUID uuid = server.config.authHandler.usernameToUUID(username);
		if (uuid == null) {
			throw new CommandException(String.format("Unknown username: '%s'", username));
		}

		// Print UUID
		LogHelper.subInfo("UUID of player '%s': %s", username, uuid);
	}
}
