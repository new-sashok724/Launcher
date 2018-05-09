package launcher.request.update;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import launcher.Launcher;
import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.ClientLauncher;
import launcher.client.ClientProfile;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.Request;
import launcher.request.update.LauncherRequest.Result;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;

public final class LauncherRequest extends Request<Result> {
    @LauncherAPI public static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
    @LauncherAPI public static final boolean EXE_BINARY = IOHelper.hasExtension(BINARY_PATH, "exe");

    @LauncherAPI
    public LauncherRequest(Config config) {
        super(config);
    }

    @LauncherAPI
    public LauncherRequest() {
        this(null);
    }

    @Override
    public Type getType() {
        return Type.LAUNCHER;
    }

    @Override
    @SuppressWarnings("CallToSystemExit")
    protected Result requestDo(HInput input, HOutput output) throws Exception {
        output.writeBoolean(EXE_BINARY);
        output.flush();
        readError(input);

        // Verify launcher sign
        RSAPublicKey publicKey = config.publicKey;
        byte[] sign = input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH);
        boolean shouldUpdate = !SecurityHelper.isValidSign(BINARY_PATH, sign, publicKey);

        // Update launcher if need
        output.writeBoolean(shouldUpdate);
        output.flush();
        if (shouldUpdate) {
            byte[] binary = input.readByteArray(0);
            SecurityHelper.verifySign(binary, sign, config.publicKey);
            return new Result(binary, sign, Collections.emptyList());
        }

        // Read clients profiles list
        int count = input.readLength(0);
        List<SignedObjectHolder<ClientProfile>> profiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            profiles.add(new SignedObjectHolder<>(input, publicKey, ClientProfile.RO_ADAPTER));
        }

        // Return request result
        return new Result(null, sign, profiles);
    }

    @LauncherAPI
    public static void update(Config config, Result result) throws SignatureException, IOException {
        SecurityHelper.verifySign(result.binary, result.sign, config.publicKey);

        // Prepare process builder to start new instance (java -jar works for Launch4J's EXE too)
        List<String> args = new ArrayList<>(8);
        args.add(IOHelper.resolveJavaBin(null).toString());
        if (LogHelper.isDebugEnabled()) {
            args.add(ClientLauncher.jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())));
        }
        if (Config.ADDRESS_OVERRIDE != null) {
            args.add(ClientLauncher.jvmProperty(Config.ADDRESS_OVERRIDE_PROPERTY, Config.ADDRESS_OVERRIDE));
        }
        args.add("-jar");
        args.add(BINARY_PATH.toString());
        ProcessBuilder builder = new ProcessBuilder(args.toArray(new String[args.size()]));
        builder.inheritIO();

        // Rewrite and start new instance
        IOHelper.write(BINARY_PATH, result.binary);
        builder.start();

        // Kill current instance
        JVMHelper.RUNTIME.exit(255);
        throw new AssertionError("Why Launcher wasn't restarted?!");
    }

    public static final class Result {
        @LauncherAPI public final List<SignedObjectHolder<ClientProfile>> profiles;
        private final byte[] binary;
        private final byte[] sign;

        private Result(byte[] binary, byte[] sign, List<SignedObjectHolder<ClientProfile>> profiles) {
            this.binary = binary == null ? null : binary.clone();
            this.sign = sign.clone();
            this.profiles = Collections.unmodifiableList(profiles);
        }

        @LauncherAPI
        public byte[] getBinary() {
            return binary == null ? null : binary.clone();
        }

        @LauncherAPI
        public byte[] getSign() {
            return sign.clone();
        }
    }
}
