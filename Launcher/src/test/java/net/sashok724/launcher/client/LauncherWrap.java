package net.sashok724.launcher.client;

import net.sashok724.launcher.client.Launcher;

public final class LauncherWrap {
	private LauncherWrap() {
	}

	public static void main(String... args) throws Throwable {
		Launcher.main(args); // Just for test runtime
	}
}
