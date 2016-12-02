package launchserver.command.basic;

import launcher.helper.JVMHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class StopCommand extends Command {
    public StopCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return null;
    }

    @Override
    public String getUsageDescription() {
        return "Stop LaunchServer";
    }

    @Override
    @SuppressWarnings("CallToSystemExit")
    public void invoke(String... args) {
        JVMHelper.RUNTIME.exit(0);
    }
}
