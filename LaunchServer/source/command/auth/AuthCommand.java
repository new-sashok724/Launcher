package launchserver.command.auth;

import java.util.UUID;

import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class AuthCommand extends Command {
    public AuthCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<login> <password>";
    }

    @Override
    public String getUsageDescription() {
        return "Try to auth with specified login and password";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 2);
        String login = args[0];
        String password = args[1];

        // Authenticate
        String username = server.config.authProvider.auth(login, password, "127.0.0.1");

        // Authenticate on server (and get UUID)
        String accessToken = SecurityHelper.randomStringToken();
        UUID uuid = server.config.authHandler.auth(username, accessToken);

        // Print auth successful message
        LogHelper.subInfo("UUID: %s, Username: '%s', Access Token: '%s'", uuid, username, accessToken);
    }
}
