package net.sashok724.launcher.server.binary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.server.LaunchServer;

public final class EXELauncherBinary extends LauncherBinary {
	@LauncherAPI public static final Path EXE_BINARY_FILE = IOHelper.toPath("Launcher.exe");

	@LauncherAPI
	public EXELauncherBinary(LaunchServer server) {
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
