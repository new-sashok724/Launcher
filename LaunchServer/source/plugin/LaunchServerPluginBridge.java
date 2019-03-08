package launchserver.plugin;

import java.nio.file.Path;

import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;

public final class LaunchServerPluginBridge implements Runnable, AutoCloseable {
    private final LaunchServer server;

    public LaunchServerPluginBridge(Path dir) throws Throwable {
        LogHelper.addOutput(dir.resolve("LaunchServer.log"));
        LogHelper.printVersion("LaunchServer");

        // Create new LaunchServer
        long start = System.currentTimeMillis();
        try {
            server = new LaunchServer(dir, true);
        } catch (Throwable exc) {
            LogHelper.error(exc);
            throw exc;
        }
        long end = System.currentTimeMillis();
        LogHelper.debug("LaunchServer started in %dms", end - start);
    }

    @Override
    public void close() {
        server.close();
    }

    @Override
    public void run() {
        server.run();
    }

    public void eval(String... command) {
        server.commandHandler.eval(command, false);
    }

    static {
        //SecurityHelper.verifyCertificates(LaunchServer.class);
        JVMHelper.verifySystemProperties(LaunchServer.class, false);
    }
}
