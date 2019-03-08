package launchserver.response;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.helper.LogHelper;
import launcher.request.RequestException;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

public abstract class Response {
    @LauncherAPI protected final LaunchServer server;
    @LauncherAPI protected final long id;
    @LauncherAPI protected final HInput input;
    @LauncherAPI protected final HOutput output;

    @LauncherAPI
    protected Response(LaunchServer server, long id, HInput input, HOutput output) {
        this.server = server;
        this.id = id;
        this.input = input;
        this.output = output;
    }

    @LauncherAPI
    public abstract void reply() throws Throwable;

    @LauncherAPI
    protected final void debug(String message) {
        LogHelper.subDebug("#%d %s", id, message);
    }

    @LauncherAPI
    protected final void debug(String message, Object... args) {
        debug(String.format(message, args));
    }

    @LauncherAPI
    @SuppressWarnings("MethodMayBeStatic") // Intentionally not static
    protected final void writeNoError(HOutput output) throws IOException {
        output.writeString("", 0);
    }

    @LauncherAPI
    public static void requestError(String message) throws RequestException {
        throw new RequestException(message);
    }

    @FunctionalInterface
    public interface Factory {
        @LauncherAPI
        Response newResponse(LaunchServer server, long id, HInput input, HOutput output);
    }
}
