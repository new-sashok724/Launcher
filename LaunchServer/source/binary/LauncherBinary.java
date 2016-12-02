package launchserver.binary;

import java.io.IOException;
import java.nio.file.Path;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.serialize.signed.SignedBytesHolder;
import launchserver.LaunchServer;

public abstract class LauncherBinary {
    @LauncherAPI protected final LaunchServer server;
    @LauncherAPI protected final Path binaryFile;
    private volatile SignedBytesHolder binary;

    @LauncherAPI
    protected LauncherBinary(LaunchServer server, Path binaryFile) {
        this.server = server;
        this.binaryFile = binaryFile;
    }

    @LauncherAPI
    public abstract void build() throws IOException;

    @LauncherAPI
    public final boolean exists() {
        return IOHelper.isFile(binaryFile);
    }

    @LauncherAPI
    public final SignedBytesHolder getBytes() {
        return binary;
    }

    @LauncherAPI
    public final boolean sync() throws IOException {
        boolean exists = exists();
        binary = exists ? new SignedBytesHolder(IOHelper.read(binaryFile), server.privateKey) : null;
        return exists;
    }
}
