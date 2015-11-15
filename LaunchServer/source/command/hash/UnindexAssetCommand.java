package launchserver.command.hash;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;
import launchserver.command.CommandException;

public final class UnindexAssetCommand extends Command {
	public UnindexAssetCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<dir> <index> <output-dir>";
	}

	@Override
	public String getUsageDescription() {
		return "Unindex asset dir (1.7.10+)";
	}

	@Override
	public void invoke(String... args) throws Exception {
		verifyArgs(args, 3);
		String inputAssetDirName = IOHelper.verifyFileName(args[0]);
		String indexFileName = IOHelper.verifyFileName(args[1]);
		String outputAssetDirName = IOHelper.verifyFileName(args[2]);
		Path inputAssetDir = LaunchServer.UPDATES_DIR.resolve(inputAssetDirName);
		Path outputAssetDir = LaunchServer.UPDATES_DIR.resolve(outputAssetDirName);
		if (outputAssetDir.equals(inputAssetDir)) {
			throw new CommandException("Indexed and unindexed asset dirs can't be same");
		}

		// Create new asset dir
		LogHelper.subInfo("Creating unindexed asset dir: '%s'", outputAssetDirName);
		Files.createDirectory(outputAssetDir);

		// Read JSON file
		JsonObject objects;
		LogHelper.subInfo("Reading asset index file: '%s'", indexFileName);
		try (BufferedReader reader = IOHelper.newReader(IndexAssetCommand.resolveIndexFile(inputAssetDir, indexFileName))) {
			objects = Json.parse(reader).asObject().get(IndexAssetCommand.OBJECTS_DIR).asObject();
		}

		// Restore objects
		LogHelper.subInfo("Unindexing %d objects", objects.size());
		for (JsonObject.Member member : objects) {
			String name = member.getName();
			LogHelper.subInfo("Unindexing: '%s'", name);

			// Copy hashed file to target
			String hash = member.getValue().asObject().get("hash").asString();
			Path source = IndexAssetCommand.resolveObjectFile(inputAssetDir, hash);
			IOHelper.copy(source, outputAssetDir.resolve(name));
		}

		// Finished
		server.syncUpdatesDir(Collections.singleton(outputAssetDirName));
		LogHelper.subInfo("Asset successfully unindexed: '%s'", inputAssetDirName);
	}
}
