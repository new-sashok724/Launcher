package launchserver;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.client.ClientProfile;
import launcher.hasher.HashedDir;
import launcher.helper.*;
import launcher.serialize.config.ConfigObject;
import launcher.serialize.config.TextConfigReader;
import launcher.serialize.config.TextConfigWriter;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.BooleanConfigEntry;
import launcher.serialize.config.entry.IntegerConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launcher.serialize.signed.SignedObjectHolder;
import launchserver.auth.AuthException;
import launchserver.auth.handler.AuthHandler;
import launchserver.auth.handler.CachedAuthHandler;
import launchserver.auth.handler.FileAuthHandler;
import launchserver.auth.provider.AuthProvider;
import launchserver.auth.sqlconfig.MySQLSourceConfig;
import launchserver.binary.EXEL4JLauncherBinary;
import launchserver.binary.EXELauncherBinary;
import launchserver.binary.JARLauncherBinary;
import launchserver.binary.LauncherBinary;
import launchserver.command.Command;
import launchserver.command.CommandException;
import launchserver.command.handler.CommandHandler;
import launchserver.command.handler.JLineCommandHandler;
import launchserver.command.handler.StdCommandHandler;
import launchserver.response.Response;
import launchserver.response.Response.Factory;
import launchserver.response.ServerSocketHandler;
import launchserver.response.ServerSocketHandler.Listener;
import launchserver.texture.TextureProvider;

import javax.script.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

public final class LaunchServer implements Runnable, AutoCloseable {
    // Constant paths
    @LauncherAPI
    public final Path dir;
    @LauncherAPI
    public final Path configFile;
    @LauncherAPI
    public final Path publicKeyFile;
    @LauncherAPI
    public final Path privateKeyFile;
    @LauncherAPI
    public final Path updatesDir;
    @LauncherAPI
    public final Path profilesDir;

    // Server config
    @LauncherAPI
    public final Config config;
    @LauncherAPI
    public final RSAPublicKey publicKey;
    @LauncherAPI
    public final RSAPrivateKey privateKey;
    @LauncherAPI
    public final boolean portable;

    // Launcher binary
    @LauncherAPI
    public final LauncherBinary launcherBinary;
    @LauncherAPI
    public final LauncherBinary launcherEXEBinary;

    // Server
    @LauncherAPI
    public final CommandHandler commandHandler;
    @LauncherAPI
    public final ServerSocketHandler serverSocketHandler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ScriptEngine engine = CommonHelper.newScriptEngine();

    // Updates and profiles
    private volatile List<SignedObjectHolder<ClientProfile>> profilesList;
    private volatile Map<String, SignedObjectHolder<HashedDir>> updatesDirMap;

    public LaunchServer(Path dir, boolean portable) throws IOException, InvalidKeySpecException {
        setScriptBindings();
        this.portable = portable;

        // Setup config locations
        this.dir = dir;
        configFile = dir.resolve("LaunchServer.cfg");
        publicKeyFile = dir.resolve("public.key");
        privateKeyFile = dir.resolve("private.key");
        updatesDir = dir.resolve("updates");
        profilesDir = dir.resolve("profiles");

        // Set command handler
        CommandHandler localCommandHandler;
        if (portable) {
            localCommandHandler = new StdCommandHandler(this, false);
        } else {
            try {
                Class.forName("jline.Terminal");

                // JLine2 available
                localCommandHandler = new JLineCommandHandler(this);
                LogHelper.info("JLine2 terminal enabled");
            } catch (ClassNotFoundException ignored) {
                localCommandHandler = new StdCommandHandler(this, true);
                LogHelper.warning("JLine2 isn't in classpath, using std");
            }
        }
        commandHandler = localCommandHandler;

        // Set key pair
        if (IOHelper.isFile(publicKeyFile) && IOHelper.isFile(privateKeyFile)) {
            LogHelper.info("Reading RSA keypair");
            publicKey = SecurityHelper.toPublicRSAKey(IOHelper.read(publicKeyFile));
            privateKey = SecurityHelper.toPrivateRSAKey(IOHelper.read(privateKeyFile));
            if (!publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IOException("Private and public key modulus mismatch");
            }
        } else {
            LogHelper.info("Generating RSA keypair");
            KeyPair pair = SecurityHelper.genRSAKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();

            // Write key pair files
            LogHelper.info("Writing RSA keypair files");
            IOHelper.write(publicKeyFile, publicKey.getEncoded());
            IOHelper.write(privateKeyFile, privateKey.getEncoded());
        }

        // Print keypair fingerprints
        CRC32 crc = new CRC32();
        crc.update(publicKey.getModulus().toByteArray());
        LogHelper.subInfo("Modulus CRC32: 0x%08x", crc.getValue());

        // Read LaunchServer config
        generateConfigIfNotExists();
        LogHelper.info("Reading LaunchServer config file");
        try (BufferedReader reader = IOHelper.newReader(configFile)) {
            config = new Config(TextConfigReader.read(reader, true));
        }
        config.verify();

        // Set launcher EXE binary
        launcherBinary = new JARLauncherBinary(this);
        launcherEXEBinary = config.launch4J ? new EXEL4JLauncherBinary(this) : new EXELauncherBinary(this);
        syncLauncherBinaries();

        // Sync updates dir
        if (!IOHelper.isDir(updatesDir)) {
            Files.createDirectory(updatesDir);
        }
        syncUpdatesDir(null);

        // Sync profiles dir
        if (!IOHelper.isDir(profilesDir)) {
            Files.createDirectory(profilesDir);
        }
        syncProfilesDir();

        // Set server socket thread
        serverSocketHandler = new ServerSocketHandler(this);
    }

    @Override
    public void close() {
        serverSocketHandler.close();

        // Close handlers & providers
        try {
            config.authHandler.close();
        } catch (IOException e) {
            LogHelper.error(e);
        }
        try {
            config.authProvider.close();
        } catch (IOException e) {
            LogHelper.error(e);
        }
        try {
            config.textureProvider.close();
        } catch (IOException e) {
            LogHelper.error(e);
        }

        // Notify script about closing
        try {
            ((Invocable) engine).invokeFunction("close");
        } catch (NoSuchMethodException ignored) {
            // Do nothing if method simply doesn't exist
        } catch (Exception e) {
            LogHelper.error(e);
        }

        // Print last message before death :(
        LogHelper.info("LaunchServer stopped");
    }

    @Override
    public void run() {
        if (started.getAndSet(true)) {
            throw new IllegalStateException("LaunchServer has been already started");
        }

        // Load plugin script if exist
        Path scriptFile = dir.resolve("plugin.js");
        if (IOHelper.isFile(scriptFile)) {
            LogHelper.info("Loading plugin.js script");
            try {
                loadScript(IOHelper.toURL(scriptFile));
            } catch (Exception e) {
                throw new RuntimeException("Error while loading plugin.js", e);
            }
        }

        // Add shutdown hook, then start LaunchServer
        if (!portable) {
            JVMHelper.RUNTIME.addShutdownHook(CommonHelper.newThread(null, false, this::close));
            CommonHelper.newThread("Command Thread", true, commandHandler).start();
        }
        rebindServerSocket();
    }

    @LauncherAPI
    public void buildLauncherBinaries() throws IOException {
        launcherBinary.build();
        launcherEXEBinary.build();
    }

    @LauncherAPI
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public Collection<SignedObjectHolder<ClientProfile>> getProfiles() {
        return profilesList;
    }

    @LauncherAPI
    public SignedObjectHolder<HashedDir> getUpdateDir(String name) {
        return updatesDirMap.get(name);
    }

    @LauncherAPI
    public Set<Entry<String, SignedObjectHolder<HashedDir>>> getUpdateDirs() {
        return updatesDirMap.entrySet();
    }

    @LauncherAPI
    public Object loadScript(URL url) throws IOException, ScriptException {
        LogHelper.debug("Loading server script: '%s'", url);
        try (BufferedReader reader = IOHelper.newReader(url)) {
            return engine.eval(reader);
        }
    }

    @LauncherAPI
    public void rebindServerSocket() {
        serverSocketHandler.close();
        CommonHelper.newThread("Server Socket Thread", false, serverSocketHandler).start();
    }

    @LauncherAPI
    public void syncLauncherBinaries() throws IOException {
        LogHelper.info("Syncing launcher binaries");

        // Syncing launcher binary
        LogHelper.subInfo("Syncing launcher binary file");
        if (!launcherBinary.sync()) {
            LogHelper.subWarning("Missing launcher binary file");
        }

        // Syncing launcher EXE binary
        LogHelper.subInfo("Syncing launcher EXE binary file");
        if (!launcherEXEBinary.sync()) {
            LogHelper.subWarning("Missing launcher EXE binary file");
        }
    }

    @LauncherAPI
    public void syncProfilesDir() throws IOException {
        LogHelper.info("Syncing profiles dir");
        List<SignedObjectHolder<ClientProfile>> newProfies = new LinkedList<>();
        IOHelper.walk(profilesDir, new ProfilesFileVisitor(newProfies), false);

        // Sort and set new profiles
        newProfies.sort(Comparator.comparing(a -> a.object));
        profilesList = Collections.unmodifiableList(newProfies);
    }

    @LauncherAPI
    public void syncUpdatesDir(Collection<String> dirs) throws IOException {
        LogHelper.info("Syncing updates dir");
        Map<String, SignedObjectHolder<HashedDir>> newUpdatesDirMap = new HashMap<>(16);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(updatesDir)) {
            for (Path updateDir : dirStream) {
                if (Files.isHidden(updateDir)) {
                    continue; // Skip hidden
                }

                // Resolve name and verify is dir
                String name = IOHelper.getFileName(updateDir);
                if (!IOHelper.isDir(updateDir)) {
                    LogHelper.subWarning("Not update dir: '%s'", name);
                    continue;
                }

                // Add from previous map (it's guaranteed to be non-null)
                if (dirs != null && !dirs.contains(name)) {
                    SignedObjectHolder<HashedDir> hdir = updatesDirMap.get(name);
                    if (hdir != null) {
                        newUpdatesDirMap.put(name, hdir);
                        continue;
                    }
                }

                // Sync and sign update dir
                LogHelper.subInfo("Syncing '%s' update dir", name);
                HashedDir updateHDir = new HashedDir(updateDir, null, true, true);
                newUpdatesDirMap.put(name, new SignedObjectHolder<>(updateHDir, privateKey));
            }
        }
        updatesDirMap = Collections.unmodifiableMap(newUpdatesDirMap);
    }

    private void generateConfigIfNotExists() throws IOException {
        if (IOHelper.isFile(configFile)) {
            return;
        }

        // Create new config
        Config newConfig;
        LogHelper.info("Creating LaunchServer config");
        try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL("launchserver/defaults/config.cfg"))) {
            newConfig = new Config(TextConfigReader.read(reader, false));
        }

        // Set server address
        if (portable) {
            LogHelper.warning("Setting LaunchServer address to 'localhost'");
            newConfig.setAddress("localhost");
        } else {
            LogHelper.println("LaunchServer address: ");
            newConfig.setAddress(commandHandler.readLine());
        }

        // Write LaunchServer config
        LogHelper.info("Writing LaunchServer config file");
        try (BufferedWriter writer = IOHelper.newWriter(configFile)) {
            TextConfigWriter.write(newConfig.block, writer, true);
        }
    }

    private void setScriptBindings() {
        LogHelper.info("Setting up server script engine bindings");
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("server", this);

        // Add launcher and launchserver class bindings
        Launcher.addLauncherClassBindings(bindings);
        addLaunchServerClassBindings(bindings);
    }

    public static void main(String... args) throws Throwable {
        SecurityHelper.verifyCertificates(LaunchServer.class);
        JVMHelper.verifySystemProperties(LaunchServer.class, true);
        LogHelper.addOutput(IOHelper.WORKING_DIR.resolve("LaunchServer.log"));
        LogHelper.printVersion("LaunchServer");

        // Start LaunchServer
        Instant start = Instant.now();
        try {
            new LaunchServer(IOHelper.WORKING_DIR, false).run();
        } catch (Throwable exc) {
            LogHelper.error(exc);
            return;
        }
        Instant end = Instant.now();
        LogHelper.debug("LaunchServer started in %dms", Duration.between(start, end).toMillis());
    }

    private static void addLaunchServerClassBindings(Map<String, Object> bindings) {
        bindings.put("LaunchServerClass", LaunchServer.class);

        // Set auth class bindings
        bindings.put("AuthHandlerClass", AuthHandler.class);
        bindings.put("FileAuthHandlerClass", FileAuthHandler.class);
        bindings.put("CachedAuthHandlerClass", CachedAuthHandler.class);
        bindings.put("AuthProviderClass", AuthProvider.class);
        bindings.put("DigestAuthProviderClass", AuthProvider.class);
        bindings.put("MySQLSourceConfigClass", MySQLSourceConfig.class);
        bindings.put("AuthExceptionClass", AuthException.class);
        bindings.put("TextureProviderClass", TextureProvider.class);

        // Set command class bindings
        bindings.put("CommandClass", Command.class);
        bindings.put("CommandHandlerClass", CommandHandler.class);
        bindings.put("CommandExceptionClass", CommandException.class);

        // Set response class bindings
        bindings.put("ResponseClass", Response.class);
        bindings.put("ResponseFactoryClass", Factory.class);
        bindings.put("ServerSocketHandlerListenerClass", Listener.class);
    }

    private final class ProfilesFileVisitor extends SimpleFileVisitor<Path> {
        private final Collection<SignedObjectHolder<ClientProfile>> result;

        private ProfilesFileVisitor(Collection<SignedObjectHolder<ClientProfile>> result) {
            this.result = result;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            LogHelper.subInfo("Syncing '%s' profile", IOHelper.getFileName(file));

            // Read profile
            ClientProfile profile;
            try (BufferedReader reader = IOHelper.newReader(file)) {
                profile = new ClientProfile(TextConfigReader.read(reader, true));
            }
            profile.verify();

            // Add SIGNED profile to result list
            result.add(new SignedObjectHolder<>(profile, privateKey));
            return super.visitFile(file, attrs);
        }
    }

    public static final class Config extends ConfigObject {
        @LauncherAPI
        public final int port;

        // Handlers & Providers
        @LauncherAPI
        public final AuthHandler authHandler;
        @LauncherAPI
        public final AuthProvider authProvider;
        @LauncherAPI
        public final TextureProvider textureProvider;

        // Misc options
        @LauncherAPI
        public final boolean launch4J;
        @LauncherAPI
        public final boolean compress;
        private final StringConfigEntry address;
        private final String bindAddress;

        private Config(BlockConfigEntry block) {
            super(block);
            address = block.getEntry("address", StringConfigEntry.class);
            port = VerifyHelper.verifyInt(block.getEntryValue("port", IntegerConfigEntry.class),
                    VerifyHelper.range(0, 65535), "Illegal LaunchServer port");
            bindAddress = block.hasEntry("bindAddress") ?
                    block.getEntryValue("bindAddress", StringConfigEntry.class) : getAddress();

            // Set handlers & providers
            authHandler = AuthHandler.newHandler(block.getEntryValue("authHandler", StringConfigEntry.class),
                    block.getEntry("authHandlerConfig", BlockConfigEntry.class));
            authProvider = AuthProvider.newProvider(block.getEntryValue("authProvider", StringConfigEntry.class),
                    block.getEntry("authProviderConfig", BlockConfigEntry.class));
            textureProvider = TextureProvider.newProvider(block.getEntryValue("textureProvider", StringConfigEntry.class),
                    block.getEntry("textureProviderConfig", BlockConfigEntry.class));

            // Set misc config
            launch4J = block.getEntryValue("launch4J", BooleanConfigEntry.class);
            compress = block.getEntryValue("compress", BooleanConfigEntry.class);
        }

        @LauncherAPI
        public String getAddress() {
            return address.getValue();
        }

        @LauncherAPI
        public void setAddress(String address) {
            this.address.setValue(address);
        }

        @LauncherAPI
        public String getBindAddress() {
            return bindAddress;
        }

        @LauncherAPI
        public SocketAddress getSocketAddress() {
            return new InetSocketAddress(bindAddress, port);
        }

        @LauncherAPI
        public void verify() {
            VerifyHelper.verify(getAddress(), VerifyHelper.NOT_EMPTY, "LaunchServer address can't be empty");
        }
    }
}
