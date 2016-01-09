package net.sashok724.launcher.server.binary;

import java.io.IOException;
import java.nio.file.Path;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.serialize.signed.SignedBytesHolder;
import net.sashok724.launcher.server.LaunchServer;

public abstract class LauncherBinary {
	@LauncherAPI protected final LaunchServer server;
	@LauncherAPI protected final Path binaryFile;
	private volatile SignedBytesHolder binary;

	@LauncherAPI
	protected LauncherBinary(LaunchServer server, Path binaryFile) {
		this.server = server;
		this.binaryFile = binaryFile;
	}

	@LauncherAPI
	public abstract void build() throws IOException;

	@LauncherAPI
	public final boolean exists() {
		return IOHelper.isFile(binaryFile);
	}

	@LauncherAPI
	public final SignedBytesHolder getBytes() {
		return binary;
	}

	@LauncherAPI
	public final boolean sync() throws IOException {
		boolean exists = exists();
		binary = exists ? new SignedBytesHolder(IOHelper.read(binaryFile), server.privateKey) : null;
		return exists;
	}
}
