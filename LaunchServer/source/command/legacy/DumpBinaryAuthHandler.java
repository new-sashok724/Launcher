package launchserver.command.legacy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.serialize.config.TextConfigWriter;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.ConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.LaunchServer;
import launchserver.auth.handler.BinaryFileAuthHandler;
import launchserver.auth.handler.FileAuthHandler.Entry;
import launchserver.command.Command;

public final class DumpBinaryAuthHandler extends Command {
    public DumpBinaryAuthHandler(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return null;
    }

    @Override
    public String getUsageDescription() {
        return "Dumps BinaryAuthHandler to text file";
    }

    @Override
    public void invoke(String... args) {
        LogHelper.subInfo("Dumping BinaryAuthHandler file...");
        BinaryFileAuthHandler handler = (BinaryFileAuthHandler) server.config.authHandler;
        boolean next = false;

        // Write auth blocks to map
        Set<Map.Entry<UUID, Entry>> entrySet = handler.entrySet();
        Map<String, ConfigEntry<?>> map = new LinkedHashMap<>(entrySet.size());
        for (Map.Entry<UUID, Entry> entry : entrySet) {
            UUID uuid = entry.getKey();
            Entry auth = entry.getValue();

            // Set auth entry data
            Map<String, ConfigEntry<?>> authMap = new LinkedHashMap<>(entrySet.size());
            authMap.put("username", cc(auth.getUsername()));
            String accessToken = auth.getAccessToken();
            if (accessToken != null) {
                authMap.put("accessToken", cc(accessToken));
            }
            String serverID = auth.getServerID();
            if (serverID != null) {
                authMap.put("serverID", cc(serverID));
            }

            // Create and add auth block
            BlockConfigEntry authBlock = new BlockConfigEntry(authMap, true, 5);
            if (next) {
                authBlock.setComment(0, "\n"); // Pre-name
            } else {
                next = true;
            }
            authBlock.setComment(2, " "); // Pre-value
            authBlock.setComment(4, "\n"); // Post-comment
            map.put(uuid.toString(), authBlock);
        }

        // Write auth handler file
        try (BufferedWriter writer = IOHelper.newWriter(Paths.get("authHandler.dump.cfg"))) {
            BlockConfigEntry authFile = new BlockConfigEntry(map, true, 1);
            authFile.setComment(0, "\n");
            TextConfigWriter.write(authFile, writer, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static StringConfigEntry cc(String value) {
        StringConfigEntry entry = new StringConfigEntry(value, true, 4);
        entry.setComment(0, "\n\t"); // Pre-name
        entry.setComment(2, " "); // Pre-value
        return entry;
    }
}
