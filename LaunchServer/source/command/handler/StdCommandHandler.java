package launchserver.command.handler;

import java.io.IOException;

import launcher.helper.IOHelper;
import launchserver.LaunchServer;
import launchserver.helper.LineReader;

public final class StdCommandHandler extends CommandHandler {
	private final LineReader reader;

	public StdCommandHandler(LaunchServer server) {
		super(server);
		reader = new LineReader(IOHelper.newReader(System.in));
	}

	@Override
	public void bell() {
		// Do nothing, unsupported
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("clear terminal");
	}

	@Override
	public String readLine() throws IOException {
		return reader.readLine();
	}
}
