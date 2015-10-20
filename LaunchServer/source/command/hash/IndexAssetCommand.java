package launchserver.command.hash;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;
import launchserver.command.CommandException;
import org.json.JSONObject;

public final class IndexAssetCommand extends Command {
	public static final String INDEXES_DIR = "indexes";
	public static final String OBJECTS_DIR = "objects";
	private static final String JSON_EXTENSION = ".json";

	public IndexAssetCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<dir> <output-dir>";
	}

	@Override
	public String getUsageDescription() {
		return "Index asset dir (1.7.10+)";
	}

	@Override
	public void invoke(String... args) throws Exception {
		verifyArgs(args, 2);
		String inputAssetDirName = IOHelper.verifyFileName(args[0]);
		String outputAssetDirName = IOHelper.verifyFileName(args[1]);
		Path inputAssetDir = LaunchServer.UPDATES_DIR.resolve(inputAssetDirName);
		Path outputAssetDir = LaunchServer.UPDATES_DIR.resolve(outputAssetDirName);
		if (outputAssetDir.equals(inputAssetDir)) {
			throw new CommandException("Unindexed and indexed asset dirs can't be same");
		}

		// Create new asset dir
		LogHelper.subInfo("Creating indexed asset dir: '%s'", outputAssetDirName);
		if (!IOHelper.isDir(outputAssetDir)) {
			Files.createDirectory(outputAssetDir);
		}

		// Index objects
		LogHelper.subInfo("Indexing objects");
		JSONObject objects = new JSONObject();
		IOHelper.walk(inputAssetDir, new IndexAssetVisitor(objects, inputAssetDir, outputAssetDir), false);

		// Write index file
		LogHelper.subInfo("Writing asset index file: '%s'", outputAssetDirName);
		try (BufferedWriter writer = IOHelper.newWriter(resolveIndexFile(outputAssetDir, outputAssetDirName))) {
			JSONObject root = new JSONObject();
			root.put(OBJECTS_DIR, objects);
			root.write(writer);
		}

		// Finished
		server.syncUpdatesDir(Collections.singleton(outputAssetDirName));
		LogHelper.subInfo("Asset successfully indexed: '%s'", inputAssetDirName);
	}

	@LauncherAPI
	public static Path resolveIndexFile(Path assetDir, String name) {
		return assetDir.resolve(INDEXES_DIR).resolve(name + JSON_EXTENSION);
	}

	@LauncherAPI
	public static Path resolveObjectFile(Path assetDir, String hash) {
		return assetDir.resolve(OBJECTS_DIR).resolve(hash.substring(0, 2)).resolve(hash);
	}

	private static final class IndexAssetVisitor extends SimpleFileVisitor<Path> {
		private final JSONObject objects;
		private final Path inputAssetDir;
		private final Path outputAssetDir;

		private IndexAssetVisitor(JSONObject objects, Path inputAssetDir, Path outputAssetDir) {
			this.objects = objects;
			this.inputAssetDir = inputAssetDir;
			this.outputAssetDir = outputAssetDir;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			String name = IOHelper.toString(inputAssetDir.relativize(file));
			LogHelper.subInfo("Indexing: '%s'", name);

			// Calculate SHA-1 hash sum and get size
			String hash = SecurityHelper.toHex(SecurityHelper.digest(SecurityHelper.DigestAlgorithm.SHA1, file));

			// Add to objects
			JSONObject object = new JSONObject();
			object.put("size", attrs.size());
			object.put("hash", hash);
			objects.put(name, object);

			// Copy file
			IOHelper.copy(file, resolveObjectFile(outputAssetDir, hash));
			return super.visitFile(file, attrs);
		}
	}
}
