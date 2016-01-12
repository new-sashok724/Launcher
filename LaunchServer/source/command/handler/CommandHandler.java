package launchserver.command.handler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import launcher.LauncherAPI;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;
import launchserver.command.CommandException;
import launchserver.command.auth.AuthCommand;
import launchserver.command.auth.UUIDToUsernameCommand;
import launchserver.command.auth.UsernameToUUIDCommand;
import launchserver.command.basic.BuildCommand;
import launchserver.command.basic.ClearCommand;
import launchserver.command.basic.DebugCommand;
import launchserver.command.basic.GCCommand;
import launchserver.command.basic.HelpCommand;
import launchserver.command.basic.LogConnectionsCommand;
import launchserver.command.basic.RebindCommand;
import launchserver.command.basic.StopCommand;
import launchserver.command.basic.VersionCommand;
import launchserver.command.hash.DownloadAssetCommand;
import launchserver.command.hash.DownloadClientCommand;
import launchserver.command.hash.IndexAssetCommand;
import launchserver.command.hash.SyncBinariesCommand;
import launchserver.command.hash.SyncProfilesCommand;
import launchserver.command.hash.SyncUpdatesCommand;
import launchserver.command.hash.UnindexAssetCommand;

public abstract class CommandHandler implements Runnable {
	private final Map<String, Command> commands = new ConcurrentHashMap<>(32);

	protected CommandHandler(LaunchServer server) {
		// Register basic commands
		registerCommand("help", new HelpCommand(server));
		registerCommand("version", new VersionCommand(server));
		registerCommand("build", new BuildCommand(server));
		registerCommand("stop", new StopCommand(server));
		registerCommand("rebind", new RebindCommand(server));
		registerCommand("debug", new DebugCommand(server));
		registerCommand("clear", new ClearCommand(server));
		registerCommand("gc", new GCCommand(server));
		registerCommand("logConnections", new LogConnectionsCommand(server));

		// Register sync commands
		registerCommand("indexAsset", new IndexAssetCommand(server));
		registerCommand("unindexAsset", new UnindexAssetCommand(server));
		registerCommand("downloadAsset", new DownloadAssetCommand(server));
		registerCommand("downloadClient", new DownloadClientCommand(server));
		registerCommand("syncBinaries", new SyncBinariesCommand(server));
		registerCommand("syncUpdates", new SyncUpdatesCommand(server));
		registerCommand("syncProfiles", new SyncProfilesCommand(server));

		// Register auth commands
		registerCommand("auth", new AuthCommand(server));
		registerCommand("usernameToUUID", new UsernameToUUIDCommand(server));
		registerCommand("uuidToUsername", new UUIDToUsernameCommand(server));
	}

	@Override
	public final void run() {
		try {
			readLoop();
		} catch (IOException e) {
			LogHelper.error(e);
		}
	}

	@LauncherAPI
	public abstract void bell() throws IOException;

	@LauncherAPI
	public abstract void clear() throws IOException;

	@LauncherAPI
	public final Map<String, Command> commandsMap() {
		return Collections.unmodifiableMap(commands);
	}

	@LauncherAPI
	public final void eval(String line, boolean bell) {
		Instant startTime = Instant.now();
		try {
			String[] args = parse(line);
			if (args.length == 0) {
				return;
			}

			// Invoke command
			LogHelper.info("Command '%s'", line);
			lookup(args[0]).invoke(Arrays.copyOfRange(args, 1, args.length));
		} catch (Exception e) {
			LogHelper.error(e);
		}

		// Bell if invocation took > 1s
		Instant endTime = Instant.now();
		if (bell && Duration.between(startTime, endTime).getSeconds() >= 5) {
			try {
				bell();
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}
	}

	@LauncherAPI
	public final Command lookup(String name) throws CommandException {
		Command command = commands.get(name);
		if (command == null) {
			throw new CommandException(String.format("Unknown command: '%s'", name));
		}
		return command;
	}

	@LauncherAPI
	public abstract String readLine() throws IOException;

	@LauncherAPI
	public final void registerCommand(String name, Command command) {
		VerifyHelper.verifyIDName(name);
		VerifyHelper.putIfAbsent(commands, name, Objects.requireNonNull(command, "command"),
			String.format("Command has been already registered: '%s'", name));
	}

	private void readLoop() throws IOException {
		for (String line = readLine(); line != null; line = readLine()) {
			eval(line, true);
		}
	}

	private static String[] parse(CharSequence line) throws CommandException {
		boolean quoted = false;
		boolean wasQuoted = false;

		// Read line char by char
		Collection<String> result = new LinkedList<>();
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < line.length() + 1; i++) {
			boolean end = i >= line.length();
			char ch = end ? 0 : line.charAt(i);

			// Maybe we should read next argument?
			if (end || !quoted && Character.isWhitespace(ch)) {
				if (end && quoted) { // Quotes should be closed
					throw new CommandException("Quotes wasn't closed");
				}

				// Empty args are ignored (except if was quoted)
				if (wasQuoted || builder.length() > 0) {
					result.add(builder.toString());
				}

				// Reset string builder
				wasQuoted = false;
				builder.setLength(0);
				continue;
			}

			// Append next char
			switch (ch) {
				case '"': // "abc"de, "abc""de" also allowed
					quoted = !quoted;
					wasQuoted = true;
					break;
				case '\\': // All escapes, including spaces etc
					char next = line.charAt(i + 1);
					builder.append(next);
					i++;
					break;
				default: // Default char, simply append
					builder.append(ch);
					break;
			}
		}

		// Return result as array
		return result.toArray(new String[result.size()]);
	}
}
