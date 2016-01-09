package net.sashok724.launcher.client.runtime;

public final class MainclassWrap {
	private MainclassWrap() {
	}

	public static void main(String... args) throws Exception {
		Mainclass.main(args); // Just for test runtime
	}
}
