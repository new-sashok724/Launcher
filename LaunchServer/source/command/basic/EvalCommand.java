package launchserver.command.basic;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;
import launchserver.command.CommandException;
import launchserver.helper.LineReader;

public final class EvalCommand extends Command {
	public EvalCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<file>";
	}

	@Override
	public String getUsageDescription() {
		return "Evaluate input file (external flag enabled)";
	}

	@Override
	public void invoke(String... args) throws IOException, CommandException {
		verifyArgs(args, 1);

		// Evaluate input file
		Path file = IOHelper.toPath(args[0]);
		LogHelper.subInfo("Evaluating file: '%s'", file);
		try (BufferedReader reader = new LineReader(IOHelper.newReader(file))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				server.commandHandler.eval(line, false);
			}
		}
	}
}
