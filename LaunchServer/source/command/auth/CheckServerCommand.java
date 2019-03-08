package launchserver.command.auth;

import java.util.UUID;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class CheckServerCommand extends Command {
    public CheckServerCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<username> <serverID>";
    }

    @Override
    public String getUsageDescription() {
        return "Try to check server with specified credentials";
    }

    @Override
    public void invoke(String... args) throws Throwable {
        verifyArgs(args, 2);
        String username = args[0];
        String serverID = args[1];

        // Print result message
        UUID uuid = server.config.authHandler.checkServer(username, serverID);
        LogHelper.subInfo("Check server request result: " + uuid);
    }
}
