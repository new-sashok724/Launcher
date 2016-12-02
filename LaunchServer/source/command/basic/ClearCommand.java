package launchserver.command.basic;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class ClearCommand extends Command {
    public ClearCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return null;
    }

    @Override
    public String getUsageDescription() {
        return "Clear terminal";
    }

    @Override
    public void invoke(String... args) throws Exception {
        server.commandHandler.clear();
        LogHelper.subInfo("Terminal cleared");
    }
}
