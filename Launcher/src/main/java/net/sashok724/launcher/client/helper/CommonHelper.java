package net.sashok724.launcher.client.helper;

import java.util.Locale;

import net.sashok724.launcher.client.LauncherAPI;

public final class CommonHelper {
	private CommonHelper() {
	}

	@LauncherAPI
	public static String low(String s) {
		return s.toLowerCase(Locale.US);
	}

	@LauncherAPI
	public static Thread newThread(String name, boolean daemon, Runnable runnable) {
		Thread thread = new Thread(runnable);
		thread.setDaemon(daemon);
		if (name != null) {
			thread.setName(name);
		}
		return thread;
	}

	@LauncherAPI
	public static String replace(String source, String... params) {
		for (int i = 0; i < params.length; i += 2) {
			source = source.replace('%' + params[i] + '%', params[i + 1]);
		}
		return source;
	}
}
