package launcher.runtime;

import launcher.helper.LogHelper;

public final class Mainclass {
	private Mainclass() {
	}

	public static void main(String... args) throws Exception {
		LogHelper.info("Greetings from runtime!");
	}
}
