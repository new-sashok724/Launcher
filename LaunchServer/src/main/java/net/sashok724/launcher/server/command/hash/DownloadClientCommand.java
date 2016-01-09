package net.sashok724.launcher.server.command.hash;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import net.sashok724.launcher.client.client.ClientProfile;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.serialize.config.TextConfigReader;
import net.sashok724.launcher.client.serialize.config.TextConfigWriter;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.command.Command;
import net.sashok724.launcher.server.command.CommandException;

public final class DownloadClientCommand extends Command {
	private static final String CLIENT_URL_MASK = "https://launcher.sashok724.net/download/clients/%s.zip";

	public DownloadClientCommand(LaunchServer server) {
		super(server);
	}

	@Override
	public String getArgsDescription() {
		return "<version> <dir>";
	}

	@Override
	public String getUsageDescription() {
		return "Download client dir";
	}

	@Override
	public void invoke(String... args) throws IOException, CommandException {
		verifyArgs(args, 2);
		ClientProfile.Version version = ClientProfile.Version.byName(args[0]);
		String dirName = IOHelper.verifyFileName(args[1]);
		Path clientDir = LaunchServer.UPDATES_DIR.resolve(args[1]);

		// Create client dir
		LogHelper.subInfo("Creating client dir: '%s'", dirName);
		Files.createDirectory(clientDir);

		// Download required client
		LogHelper.subInfo("Downloading client, it may take some time");
		DownloadAssetCommand.unpack(new URL(String.format(CLIENT_URL_MASK,
			IOHelper.urlEncode(version.name))), clientDir);

		// Create profile file
		LogHelper.subInfo("Creaing profile file: '%s'", dirName);
		ClientProfile client;
		String profilePath = String.format("launcherserver/defaults/profile%s.cfg", version.name);
		try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL(profilePath))) {
			client = new ClientProfile(TextConfigReader.read(reader, false));
		}
		client.setTitle(dirName);
		client.block.getEntry("dir", StringConfigEntry.class).setValue(dirName);
		try (BufferedWriter writer = IOHelper.newWriter(IOHelper.resolveIncremental(LaunchServer.PROFILES_DIR,
			dirName, "cfg"))) {
			TextConfigWriter.write(client.block, writer, true);
		}

		// Finished
		server.syncProfilesDir();
		server.syncUpdatesDir(Collections.singleton(dirName));
		LogHelper.subInfo("Client successfully downloaded: '%s'", dirName);
	}
}
