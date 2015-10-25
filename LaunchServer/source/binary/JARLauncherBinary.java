package launchserver.binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

public final class JARLauncherBinary extends LauncherBinary {
	@LauncherAPI public static final Path RUNTIME_DIR = IOHelper.WORKING_DIR.resolve(Launcher.RUNTIME_DIR);
	@LauncherAPI public static final Path INIT_SCRIPT_FILE = RUNTIME_DIR.resolve(Launcher.INIT_SCRIPT_FILE);
	@LauncherAPI public static final Path JAR_BINARY_FILE = IOHelper.WORKING_DIR.resolve("Launcher.jar");

	@LauncherAPI
	public JARLauncherBinary(LaunchServer server) throws IOException {
		super(server, JAR_BINARY_FILE);
		tryUnpackRuntime();
	}

	@Override
	public void build() throws IOException {
		LogHelper.info("Building launcher binary file");
		try (JarOutputStream output = new JarOutputStream(IOHelper.newOutput(JAR_BINARY_FILE))) {
			output.setMethod(ZipOutputStream.DEFLATED);
			output.setLevel(Deflater.BEST_COMPRESSION);
			try (InputStream input = new GZIPInputStream(IOHelper.newInput(IOHelper.getResourceURL("Launcher.pack.gz")), IOHelper.BUFFER_SIZE)) {
				Pack200.newUnpacker().unpack(input, output);
			}

			// Verify has init script file
			if (!IOHelper.isFile(INIT_SCRIPT_FILE)) {
				throw new IOException(String.format("Missing init script file ('%s')", Launcher.INIT_SCRIPT_FILE));
			}

			// Write launcher runtime dir
			Map<String, byte[]> runtime = new HashMap<>(IOHelper.BUFFER_SIZE);
			IOHelper.walk(RUNTIME_DIR, new RuntimeDirVisitor(output, runtime), false);

			// Create launcher config file
			byte[] launcherConfigBytes;
			try (ByteArrayOutputStream configArray = IOHelper.newByteArrayOutput()) {
				try (HOutput configOutput = new HOutput(configArray)) {
					new Launcher.Config(server.config.getAddress(), server.config.port,
						server.publicKey, runtime).write(configOutput);
				}
				launcherConfigBytes = configArray.toByteArray();
			}

			// Write launcher config file
			output.putNextEntry(IOHelper.newZipEntry(Launcher.CONFIG_FILE));
			output.write(launcherConfigBytes);
		}
	}

	@LauncherAPI
	public void tryUnpackRuntime() throws IOException {
		// Verify is runtime dir unpacked
		if (IOHelper.isDir(RUNTIME_DIR)) {
			return; // Already unpacked
		}

		// Unpack launcher runtime files
		Files.createDirectory(RUNTIME_DIR);
		LogHelper.info("Unpacking launcher runtime files");
		try (ZipInputStream input = IOHelper.newZipInput(IOHelper.getResourceURL("launchserver/defaults/runtime.zip"))) {
			for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
				if (entry.isDirectory()) {
					continue; // Skip dirs
				}

				// Unpack runtime file
				IOHelper.transfer(input, RUNTIME_DIR.resolve(IOHelper.toPath(entry.getName())));
			}
		}
	}

	private static final class RuntimeDirVisitor extends SimpleFileVisitor<Path> {
		private final ZipOutputStream output;
		private final Map<String, byte[]> runtime;

		private RuntimeDirVisitor(ZipOutputStream output, Map<String, byte[]> runtime) {
			this.output = output;
			this.runtime = runtime;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			String dirName = IOHelper.toString(RUNTIME_DIR.relativize(dir));
			output.putNextEntry(newEntry(dirName + '/'));
			return super.preVisitDirectory(dir, attrs);
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			String fileName = IOHelper.toString(RUNTIME_DIR.relativize(file));
			runtime.put(fileName, SecurityHelper.digest(SecurityHelper.DigestAlgorithm.MD5, file));

			// Create zip entry and transfer contents
			output.putNextEntry(newEntry(fileName));
			IOHelper.transfer(file, output);

			// Return result
			return super.visitFile(file, attrs);
		}

		private static ZipEntry newEntry(String fileName) {
			return IOHelper.newZipEntry(Launcher.RUNTIME_DIR + IOHelper.CROSS_SEPARATOR + fileName);
		}
	}
}
