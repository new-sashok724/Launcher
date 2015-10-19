package launchserver.binary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;

public final class EXELauncherBinary extends LauncherBinary {
	@LauncherAPI public static final Path EXE_BINARY_FILE = IOHelper.toPath("Launcher.exe");

	@LauncherAPI
	public EXELauncherBinary(LaunchServer server) throws IOException {
		super(server, IOHelper.WORKING_DIR.resolve(EXE_BINARY_FILE));
	}

	@Override
	public void build() throws IOException {
		if (IOHelper.isFile(binaryFile)) {
			LogHelper.subWarning("Deleting obsolete launcher EXE binary file");
			Files.delete(binaryFile);
		}
	}
}
