package launchserver.binary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import net.sf.launch4j.Builder;
import net.sf.launch4j.Log;
import net.sf.launch4j.config.Config;
import net.sf.launch4j.config.ConfigPersister;
import net.sf.launch4j.config.Jre;

public final class EXEL4JLauncherBinary extends LauncherBinary {
	// URL constants
	private static final String DOWNLOAD_URL = "http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html"; // Oracle JRE 8

	// File constants
	private static final Path EXE_BINARY_FILE = IOHelper.WORKING_DIR.resolve(EXELauncherBinary.EXE_BINARY_FILE);
	private static final Path FAVICON_FILE = IOHelper.WORKING_DIR.resolve("favicon.ico");

	@LauncherAPI
	public EXEL4JLauncherBinary(LaunchServer server) throws IOException {
		super(server, EXE_BINARY_FILE);
	}

	@Override
	public void build() throws IOException {
		LogHelper.info("Building launcher EXE binary file (Using Launch4J)");

		// Set favicon path
		Config config = ConfigPersister.getInstance().getConfig();
		if (IOHelper.isFile(FAVICON_FILE)) {
			config.setIcon(new File("favicon.ico"));
		} else {
			config.setIcon(null);
			LogHelper.warning("Missing favicon.ico file");
		}

		// Start building
		Builder builder = new Builder(Launch4JLog.INSTANCE);
		try {
			builder.build();
		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	static {
		Config config = new Config();

		// Set string options
		config.setChdir(".");
		config.setErrTitle("JVM Error");
		config.setDownloadUrl(DOWNLOAD_URL);

		// Set boolean options
		config.setPriorityIndex(0);
		config.setHeaderTypeIndex(0);
		config.setStayAlive(false);
		config.setRestartOnCrash(false);

		// Prepare JRE
		Jre jre = new Jre();
		jre.setMinVersion("1.8.0");
		jre.setRuntimeBits(Jre.RUNTIME_BITS_64_AND_32);
		jre.setJdkPreference(Jre.JDK_PREFERENCE_PREFER_JRE);
		config.setJre(jre);

		// Set JAR wrapping options
		config.setDontWrapJar(false);
		config.setJar(JARLauncherBinary.JAR_BINARY_FILE.toFile());
		config.setOutfile(EXE_BINARY_FILE.toFile());

		// Return prepared config
		ConfigPersister.getInstance().setAntConfig(config, null);
	}

	private static final class Launch4JLog extends Log {
		private static final Launch4JLog INSTANCE = new Launch4JLog();

		@Override
		public void append(String s) {
			LogHelper.subInfo(s);
		}

		@Override
		public void clear() {
			// Do nothing
		}
	}
}
