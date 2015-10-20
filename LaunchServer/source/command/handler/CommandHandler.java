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
import launcher.helper.IOHelper;
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
import launchserver.command.basic.EvalCommand;
import launchserver.command.basic.GCCommand;
import launchserver.command.basic.HelpCommand;
import launchserver.command.basic.RebindCommand;
import launchserver.command.basic.ReloadConfigCommand;
import launchserver.command.basic.ReloadKeyPairCommand;
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
		register("help", new HelpCommand(server));
		register("version", new VersionCommand(server));
		register("build", new BuildCommand(server));
		register("stop", new StopCommand(server));
		register("rebind", new RebindCommand(server));
		register("reloadConfig", new ReloadConfigCommand(server));
		register("reloadKeyPair", new ReloadKeyPairCommand(server));
		register("eval", new EvalCommand(server));
		register("debug", new DebugCommand(server));
		register("clear", new ClearCommand(server));
		register("gc", new GCCommand(server));

		// Register sync commands
		register("indexAsset", new IndexAssetCommand(server));
		register("unindexAsset", new UnindexAssetCommand(server));
		register("downloadAsset", new DownloadAssetCommand(server));
		register("downloadClient", new DownloadClientCommand(server));
		register("syncBinaries", new SyncBinariesCommand(server));
		register("syncUpdates", new SyncUpdatesCommand(server));
		register("syncProfiles", new SyncProfilesCommand(server));

		// Register auth commands
		register("auth", new AuthCommand(server));
		register("usernameToUUID", new UsernameToUUIDCommand(server));
		register("uuidToUsername", new UUIDToUsernameCommand(server));
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
	public final void register(String name, Command command) {
		VerifyHelper.verifyIDName(name);
		VerifyHelper.verify(commands.putIfAbsent(name, Objects.requireNonNull(command, "command")),
			c -> c == null, String.format("Command has been already registered: '%s'", name));
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
		StringBuilder builder = new StringBuilder(IOHelper.BUFFER_SIZE);
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
