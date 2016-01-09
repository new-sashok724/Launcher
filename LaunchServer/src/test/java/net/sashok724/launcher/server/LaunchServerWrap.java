package net.sashok724.launcher.server;

import net.sashok724.launcher.server.LaunchServer;

public final class LaunchServerWrap {
	private LaunchServerWrap() {
	}

	public static void main(String... args) throws Exception {
		LaunchServer.main(args); // Just for test runtime
	}
}
