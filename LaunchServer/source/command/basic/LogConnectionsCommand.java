package launchserver.command.basic;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class LogConnectionsCommand extends Command {
    public LogConnectionsCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "[true/false]";
    }

    @Override
    public String getUsageDescription() {
        return "Enable or disable logging connections";
    }

    @Override
    public void invoke(String... args) {
        boolean newValue;
        if (args.length >= 1) {
            newValue = Boolean.parseBoolean(args[0]);
            server.serverSocketHandler.logConnections = newValue;
        } else {
            newValue = server.serverSocketHandler.logConnections;
        }
        LogHelper.subInfo("Log connections: " + newValue);
    }
}
