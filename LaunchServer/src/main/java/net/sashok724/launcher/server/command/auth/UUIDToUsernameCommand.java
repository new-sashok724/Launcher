package net.sashok724.launcher.server.command.auth;

import java.io.IOException;
import java.util.UUID;

import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;
import net.sashok724.launcher.server.command.CommandException;

public final class UUIDToUsernameCommand extends Command {
	public UUIDToUsernameCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<uuid>";
	}

	@Override
	public String getUsageDescription() {
		return "Convert player UUID to username";
	}

	@Override
	public void invoke(String... args) throws CommandException, IOException {
		verifyArgs(args, 1);
		UUID uuid = parseUUID(args[0]);

		// Get UUID by username
		String username = server.config.authHandler.uuidToUsername(uuid);
		if (username == null) {
			throw new CommandException("Unknown UUID: " + uuid);
		}

		// Print username
		LogHelper.subInfo("Username of player %s: '%s'", uuid, username);
	}
}
