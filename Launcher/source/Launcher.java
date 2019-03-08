package launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javafx.application.Application;

import launcher.client.ClientLauncher;
import launcher.client.ClientLauncher.Params;
import launcher.client.ClientProfile;
import launcher.client.ClientProfile.Version;
import launcher.client.PlayerProfile;
import launcher.client.PlayerProfile.Texture;
import launcher.client.ServerPinger;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.hasher.HashedEntry;
import launcher.hasher.HashedFile;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.JVMHelper.OS;
import launcher.helper.LogHelper;
import launcher.helper.LogHelper.Output;
import launcher.helper.SecurityHelper;
import launcher.helper.SecurityHelper.DigestAlgorithm;
import launcher.helper.VerifyHelper;
import launcher.helper.js.JSApplication;
import launcher.request.CustomRequest;
import launcher.request.PingRequest;
import launcher.request.Request;
import launcher.request.RequestException;
import launcher.request.auth.AuthRequest;
import launcher.request.auth.CheckServerRequest;
import launcher.request.auth.JoinServerRequest;
import launcher.request.update.LauncherRequest;
import launcher.request.update.UpdateRequest;
import launcher.request.uuid.BatchProfileByUsernameRequest;
import launcher.request.uuid.ProfileByUUIDRequest;
import launcher.request.uuid.ProfileByUsernameRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.ConfigObject.Adapter;
import launcher.serialize.config.TextConfigReader;
import launcher.serialize.config.TextConfigWriter;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.BooleanConfigEntry;
import launcher.serialize.config.entry.ConfigEntry.Type;
import launcher.serialize.config.entry.IntegerConfigEntry;
import launcher.serialize.config.entry.ListConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launcher.serialize.signed.SignedBytesHolder;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.EnumSerializer;
import launcher.serialize.stream.StreamObject;

public final class Launcher {
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>();

    // Version info
    @LauncherAPI public static final String VERSION = "15.4";
    @LauncherAPI public static final String BUILD = readBuildNumber();
    @LauncherAPI public static final int PROTOCOL_MAGIC = 0x724724_00 + 23;

    // Constants
    @LauncherAPI public static final String RUNTIME_DIR = "runtime";
    @LauncherAPI public static final String CONFIG_FILE = "config.bin";
    @LauncherAPI public static final String INIT_SCRIPT_FILE = "init.js";

    // Instance
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ScriptEngine engine = CommonHelper.newScriptEngine();

    private Launcher() {
        setScriptBindings();
    }

    @LauncherAPI
    public Object loadScript(URL url) throws IOException, ScriptException {
        LogHelper.debug("Loading script: '%s'", url);
        try (BufferedReader reader = IOHelper.newReader(url)) {
            return engine.eval(reader);
        }
    }

    @LauncherAPI
    public void start(String... args) throws Throwable {
        Objects.requireNonNull(args, "args");
        if (started.getAndSet(true)) {
            throw new IllegalStateException("Launcher has been already started");
        }

        // Load init.js script
        loadScript(getResourceURL(INIT_SCRIPT_FILE));
        LogHelper.info("Invoking start() function");
        ((Invocable) engine).invokeFunction("start", (Object) args);
    }

    private void setScriptBindings() {
        LogHelper.info("Setting up script engine bindings");
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("launcher", this);

        // Add launcher class bindings
        addLauncherClassBindings(engine, bindings);
    }

    @LauncherAPI
    public static void addLauncherClassBindings(ScriptEngine engine, Map<String, Object> bindings) {
        addClassBinding(engine, bindings, "Launcher", Launcher.class);
        addClassBinding(engine, bindings, "Config", Config.class);

        // Set client class bindings
        addClassBinding(engine, bindings, "PlayerProfile", PlayerProfile.class);
        addClassBinding(engine, bindings, "PlayerProfileTexture", Texture.class);
        addClassBinding(engine, bindings, "ClientProfile", ClientProfile.class);
        addClassBinding(engine, bindings, "ClientProfileVersion", Version.class);
        addClassBinding(engine, bindings, "ClientLauncher", ClientLauncher.class);
        addClassBinding(engine, bindings, "ClientLauncherParams", Params.class);
        addClassBinding(engine, bindings, "ServerPinger", ServerPinger.class);

        // Set request class bindings
        addClassBinding(engine, bindings, "Request", Request.class);
        addClassBinding(engine, bindings, "RequestType", Request.Type.class);
        addClassBinding(engine, bindings, "RequestException", RequestException.class);
        addClassBinding(engine, bindings, "CustomRequest", CustomRequest.class);
        addClassBinding(engine, bindings, "PingRequest", PingRequest.class);
        addClassBinding(engine, bindings, "AuthRequest", AuthRequest.class);
        addClassBinding(engine, bindings, "JoinServerRequest", JoinServerRequest.class);
        addClassBinding(engine, bindings, "CheckServerRequest", CheckServerRequest.class);
        addClassBinding(engine, bindings, "UpdateRequest", UpdateRequest.class);
        addClassBinding(engine, bindings, "LauncherRequest", LauncherRequest.class);
        addClassBinding(engine, bindings, "ProfileByUsernameRequest", ProfileByUsernameRequest.class);
        addClassBinding(engine, bindings, "ProfileByUUIDRequest", ProfileByUUIDRequest.class);
        addClassBinding(engine, bindings, "BatchProfileByUsernameRequest", BatchProfileByUsernameRequest.class);

        // Set hasher class bindings
        addClassBinding(engine, bindings, "FileNameMatcher", FileNameMatcher.class);
        addClassBinding(engine, bindings, "HashedDir", HashedDir.class);
        addClassBinding(engine, bindings, "HashedFile", HashedFile.class);
        addClassBinding(engine, bindings, "HashedEntryType", HashedEntry.Type.class);

        // Set serialization class bindings
        addClassBinding(engine, bindings, "HInput", HInput.class);
        addClassBinding(engine, bindings, "HOutput", HOutput.class);
        addClassBinding(engine, bindings, "StreamObject", StreamObject.class);
        addClassBinding(engine, bindings, "StreamObjectAdapter", StreamObject.Adapter.class);
        addClassBinding(engine, bindings, "SignedBytesHolder", SignedBytesHolder.class);
        addClassBinding(engine, bindings, "SignedObjectHolder", SignedObjectHolder.class);
        addClassBinding(engine, bindings, "EnumSerializer", EnumSerializer.class);

        // Set config serialization class bindings
        addClassBinding(engine, bindings, "ConfigObject", ConfigObject.class);
        addClassBinding(engine, bindings, "ConfigObjectAdapter", Adapter.class);
        addClassBinding(engine, bindings, "BlockConfigEntry", BlockConfigEntry.class);
        addClassBinding(engine, bindings, "BooleanConfigEntry", BooleanConfigEntry.class);
        addClassBinding(engine, bindings, "IntegerConfigEntry", IntegerConfigEntry.class);
        addClassBinding(engine, bindings, "ListConfigEntry", ListConfigEntry.class);
        addClassBinding(engine, bindings, "StringConfigEntry", StringConfigEntry.class);
        addClassBinding(engine, bindings, "ConfigEntryType", Type.class);
        addClassBinding(engine, bindings, "TextConfigReader", TextConfigReader.class);
        addClassBinding(engine, bindings, "TextConfigWriter", TextConfigWriter.class);

        // Set helper class bindings
        addClassBinding(engine, bindings, "CommonHelper", CommonHelper.class);
        addClassBinding(engine, bindings, "IOHelper", IOHelper.class);
        addClassBinding(engine, bindings, "JVMHelper", JVMHelper.class);
        addClassBinding(engine, bindings, "JVMHelperOS", OS.class);
        addClassBinding(engine, bindings, "LogHelper", LogHelper.class);
        addClassBinding(engine, bindings, "LogHelperOutput", Output.class);
        addClassBinding(engine, bindings, "SecurityHelper", SecurityHelper.class);
        addClassBinding(engine, bindings, "DigestAlgorithm", DigestAlgorithm.class);
        addClassBinding(engine, bindings, "VerifyHelper", VerifyHelper.class);

        // Load JS API if available
        try {
            addClassBinding(engine, bindings, "Application", Application.class);
            addClassBinding(engine, bindings, "JSApplication", JSApplication.class);
        } catch (Throwable ignored) {
            LogHelper.warning("JavaFX API isn't available");
        }
    }

    @LauncherAPI
    public static void addClassBinding(ScriptEngine engine, Map<String, Object> bindings, String name, Class<?> clazz) {
        bindings.put(name + "Class", clazz); // Backwards-compatibility
        try {
            engine.eval("var " + name + " = " + name + "Class.static;");
        } catch (ScriptException e) {
            throw new AssertionError(e);
        }
    }

    @LauncherAPI
    public static Config getConfig() {
        Config config = CONFIG.get();
        if (config == null) {
            try (HInput input = new HInput(IOHelper.newInput(IOHelper.getResourceURL(CONFIG_FILE)))) {
                config = new Config(input);
            } catch (IOException | InvalidKeySpecException e) {
                throw new SecurityException(e);
            }
            CONFIG.set(config);
        }
        return config;
    }

    @LauncherAPI
    public static URL getResourceURL(String name) throws IOException {
        Config config = getConfig();
        byte[] validDigest = config.runtime.get(name);
        if (validDigest == null) { // No such resource digest
            throw new NoSuchFileException(name);
        }

        // Resolve URL and verify digest
        URL url = IOHelper.getResourceURL(RUNTIME_DIR + '/' + name);
        if (!Arrays.equals(validDigest, SecurityHelper.digest(DigestAlgorithm.MD5, url))) {
            throw new NoSuchFileException(name); // Digest mismatch
        }

        // Return verified URL
        return url;
    }

    @LauncherAPI
    @SuppressWarnings({ "SameReturnValue", "MethodReturnAlwaysConstant" })
    public static String getVersion() {
        return VERSION; // Because Java constants are known at compile-time
    }

    public static void main(String... args) throws Throwable {
        SecurityHelper.verifyCertificates(Launcher.class);
        JVMHelper.verifySystemProperties(Launcher.class, true);
        LogHelper.printVersion("Launcher");

        // Start Launcher
        long start = System.currentTimeMillis();
        try {
            new Launcher().start(args);
        } catch (Throwable exc) {
            LogHelper.error(exc);
            return;
        }
        long end = System.currentTimeMillis();
        LogHelper.debug("Launcher started in %dms", end - start);
    }

    private static String readBuildNumber() {
        try {
            return IOHelper.request(IOHelper.getResourceURL("buildnumber"));
        } catch (IOException ignored) {
            return "dev"; // Maybe dev env?
        }
    }

    public static final class Config extends StreamObject {
        @LauncherAPI
        public static final String ADDRESS_OVERRIDE_PROPERTY = "launcher.addressOverride";
        @LauncherAPI
        public static final String ADDRESS_OVERRIDE = System.getProperty(ADDRESS_OVERRIDE_PROPERTY, null);

        // Instance
        @LauncherAPI
        public final InetSocketAddress address;
        @LauncherAPI
        public final RSAPublicKey publicKey;
        @LauncherAPI
        public final Map<String, byte[]> runtime;

        @LauncherAPI
        @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
        public Config(String address, int port, RSAPublicKey publicKey, Map<String, byte[]> runtime) {
            this.address = InetSocketAddress.createUnresolved(address, port);
            this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
            this.runtime = Collections.unmodifiableMap(new HashMap<>(runtime));
        }

        @LauncherAPI
        public Config(HInput input) throws IOException, InvalidKeySpecException {
            String localAddress = input.readASCII(255);
            address = InetSocketAddress.createUnresolved(
                ADDRESS_OVERRIDE == null ? localAddress : ADDRESS_OVERRIDE, input.readLength(65535));
            publicKey = SecurityHelper.toPublicRSAKey(input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH));

            // Read signed runtime
            int count = input.readLength(0);
            Map<String, byte[]> localResources = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                String name = input.readString(255);
                VerifyHelper.putIfAbsent(localResources, name,
                    input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH),
                    String.format("Duplicate runtime resource: '%s'", name));
            }
            runtime = Collections.unmodifiableMap(localResources);

            // Print warning if address override is enabled
            if (ADDRESS_OVERRIDE != null) {
                LogHelper.warning("Address override is enabled: '%s'", ADDRESS_OVERRIDE);
            }
        }

        @Override
        public void write(HOutput output) throws IOException {
            output.writeASCII(address.getHostString(), 255);
            output.writeLength(address.getPort(), 65535);
            output.writeByteArray(publicKey.getEncoded(), SecurityHelper.CRYPTO_MAX_LENGTH);

            // Write signed runtime
            Set<Entry<String, byte[]>> entrySet = runtime.entrySet();
            output.writeLength(entrySet.size(), 0);
            for (Entry<String, byte[]> entry : runtime.entrySet()) {
                output.writeString(entry.getKey(), 255);
                output.writeByteArray(entry.getValue(), SecurityHelper.CRYPTO_MAX_LENGTH);
            }
        }
    }
}
