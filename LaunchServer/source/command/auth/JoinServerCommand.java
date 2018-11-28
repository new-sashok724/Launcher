package launchserver.command.auth;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class JoinServerCommand extends Command {
    public JoinServerCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<username> <accessToken> <serverID>";
    }

    @Override
    public String getUsageDescription() {
        return "Try to join server with specified credentials";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 3);
        String username = args[0];
        String accessToken = args[1];
        String serverID = args[2];

        // Print result message
        boolean success = server.config.authHandler.joinServer(username, accessToken, serverID);
        LogHelper.subInfo(success ? "Join server request succeeded" : "Join server request failed");
    }
}
