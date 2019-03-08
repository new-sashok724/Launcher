package launchserver.command.basic;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class EvalCommand extends Command {
    public EvalCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<code>";
    }

    @Override
    public String getUsageDescription() {
        return "Evaluate JavaScript code snippet";
    }

    @Override
    public void invoke(String... args) throws Throwable {
        LogHelper.subInfo("Eval result: " + server.engine.eval(String.join(" ", args)));
    }
}
