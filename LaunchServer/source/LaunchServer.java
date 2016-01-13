package launchserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import launcher.LauncherAPI;
import launcher.client.ClientProfile;
import launcher.hasher.HashedDir;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.transport.config.ConfigObject;
import launcher.transport.config.TextConfigReader;
import launcher.transport.config.TextConfigWriter;
import launcher.transport.config.entry.BlockConfigEntry;
import launcher.transport.config.entry.BooleanConfigEntry;
import launcher.transport.config.entry.IntegerConfigEntry;
import launcher.transport.config.entry.StringConfigEntry;
import launcher.transport.signed.SignedObjectHolder;
import launchserver.auth.handler.AuthHandler;
import launchserver.auth.provider.AuthProvider;
import launchserver.binary.EXEL4JLauncherBinary;
import launchserver.binary.EXELauncherBinary;
import launchserver.binary.JARLauncherBinary;
import launchserver.binary.LauncherBinary;
import launchserver.command.handler.CommandHandler;
import launchserver.command.handler.JLineCommandHandler;
import launchserver.command.handler.StdCommandHandler;
import launchserver.response.ServerSocketHandler;
import launchserver.texture.TextureProvider;

public final class LaunchServer implements Runnable {
	// Constant paths
	@LauncherAPI public static final Path CONFIG_FILE = IOHelper.WORKING_DIR.resolve("LaunchServer.cfg");
	@LauncherAPI public static final Path PUBLIC_KEY_FILE = IOHelper.WORKING_DIR.resolve("public.key");
	@LauncherAPI public static final Path PRIVATE_KEY_FILE = IOHelper.WORKING_DIR.resolve("private.key");
	@LauncherAPI public static final Path UPDATES_DIR = IOHelper.WORKING_DIR.resolve("updates");
	@LauncherAPI public static final Path PROFILES_DIR = IOHelper.WORKING_DIR.resolve("profiles");

	// Server config
	@LauncherAPI public final Config config;
	@LauncherAPI public final RSAPublicKey publicKey;
	@LauncherAPI public final RSAPrivateKey privateKey;

	// Launcher binary
	@LauncherAPI public final LauncherBinary launcherBinary = new JARLauncherBinary(this);
	@LauncherAPI public final LauncherBinary launcherEXEBinary;

	// Server
	@LauncherAPI public final CommandHandler commandHandler;
	@LauncherAPI public final ServerSocketHandler serverSocketHandler;
	private final AtomicBoolean started = new AtomicBoolean(false);

	// Updates and profiles
	private volatile List<SignedObjectHolder<ClientProfile>> profilesList;
	private volatile Map<String, SignedObjectHolder<HashedDir>> updatesDirMap;

	private LaunchServer() throws IOException, InvalidKeySpecException {
		// Set command handler
		CommandHandler localCommandHandler;
		try {
			Class.forName("jline.Terminal");

			// JLine2 available
			localCommandHandler = new JLineCommandHandler(this);
			LogHelper.info("JLine2 terminal enabled");
		} catch (ClassNotFoundException ignored) {
			localCommandHandler = new StdCommandHandler(this);
			LogHelper.warning("JLine2 isn't in classpath, using std");
		}
		commandHandler = localCommandHandler;

		// Set key pair
		if (IOHelper.isFile(PUBLIC_KEY_FILE) && IOHelper.isFile(PRIVATE_KEY_FILE)) {
			LogHelper.info("Reading RSA keypair");
			publicKey = SecurityHelper.toPublicRSAKey(IOHelper.read(PUBLIC_KEY_FILE));
			privateKey = SecurityHelper.toPrivateRSAKey(IOHelper.read(PRIVATE_KEY_FILE));
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
			IOHelper.write(PUBLIC_KEY_FILE, publicKey.getEncoded());
			IOHelper.write(PRIVATE_KEY_FILE, privateKey.getEncoded());
		}

		// Print keypair fingerprints
		CRC32 crc = new CRC32();
		crc.update(publicKey.getModulus().toByteArray());
		LogHelper.subInfo("Modulus CRC32: 0x%08x", crc.getValue());

		// Read LaunchServer config
		generateConfigIfNotExists();
		LogHelper.info("Reading LaunchServer config file");
		try (BufferedReader reader = IOHelper.newReader(CONFIG_FILE)) {
			config = new Config(TextConfigReader.read(reader, true));
		}
		config.verify();

		// Set launcher EXE binary
		launcherEXEBinary = config.launch4J ? new EXEL4JLauncherBinary(this) : new EXELauncherBinary(this);
		syncLauncherBinaries();

		// Sync updates dir
		if (!IOHelper.isDir(UPDATES_DIR)) {
			Files.createDirectory(UPDATES_DIR);
		}
		syncUpdatesDir(null);

		// Sync profiles dir
		if (!IOHelper.isDir(PROFILES_DIR)) {
			Files.createDirectory(PROFILES_DIR);
		}
		syncProfilesDir();

		// Set server socket thread
		serverSocketHandler = new ServerSocketHandler(this);
	}

	@Override
	public void run() {
		if (started.getAndSet(true)) {
			throw new IllegalStateException("LaunchServer has been already started");
		}

		// Add shutdown hook, then start LaunchServer
		JVMHelper.RUNTIME.addShutdownHook(CommonHelper.newThread(null, false, this::shutdownHook));
		CommonHelper.newThread("Command Thread", true, commandHandler).start();
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
	public Set<Map.Entry<String, SignedObjectHolder<HashedDir>>> getUpdateDirs() {
		return updatesDirMap.entrySet();
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
		IOHelper.walk(PROFILES_DIR, new ProfilesFileVisitor(newProfies), false);

		// Sort and set new profiles
		Collections.sort(newProfies, (a, b) -> a.object.compareTo(b.object));
		profilesList = Collections.unmodifiableList(newProfies);
	}

	@LauncherAPI
	public void syncUpdatesDir(Collection<String> dirs) throws IOException {
		LogHelper.info("Syncing updates dir");
		Map<String, SignedObjectHolder<HashedDir>> newUpdatesDirMap = new HashMap<>(16);
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(UPDATES_DIR)) {
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
				HashedDir updateHDir = new HashedDir(updateDir, null, true);
				newUpdatesDirMap.put(name, new SignedObjectHolder<>(updateHDir, privateKey));
			}
		}
		updatesDirMap = Collections.unmodifiableMap(newUpdatesDirMap);
	}

	private void generateConfigIfNotExists() throws IOException {
		if (IOHelper.isFile(CONFIG_FILE)) {
			return;
		}

		// Create new config
		Config newConfig;
		LogHelper.info("Creating LaunchServer config");
		try (BufferedReader reader = IOHelper.newReader(IOHelper.getResourceURL("launchserver/defaults/config.cfg"))) {
			newConfig = new Config(TextConfigReader.read(reader, false));
		}

		// Set server address
		LogHelper.println("LaunchServer address: ");
		newConfig.setAddress(commandHandler.readLine());

		// Write LaunchServer config
		LogHelper.info("Writing LaunchServer config file");
		try (BufferedWriter writer = IOHelper.newWriter(CONFIG_FILE)) {
			TextConfigWriter.write(newConfig.block, writer, true);
		}
	}

	private void shutdownHook() {
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

		// Print last message before death :(
		LogHelper.info("LaunchServer stopped");
	}

	public static void main(String... args) throws Exception {
		JVMHelper.verifySystemProperties(LaunchServer.class);
		SecurityHelper.verifyCertificates(LaunchServer.class);
		LogHelper.addOutput(IOHelper.WORKING_DIR.resolve("LaunchServer.log"));
		LogHelper.printVersion("LaunchServer");

		// Start LaunchServer
		Instant start = Instant.now();
		try {
			new LaunchServer().run();
		} catch (Exception e) {
			LogHelper.error(e);
			return;
		}
		Instant end = Instant.now();
		LogHelper.debug("LaunchServer started in %dms", Duration.between(start, end).toMillis());
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
		@LauncherAPI public final int port;
		private final StringConfigEntry address;
		private final String bindAddress;

		// Handlers & Providers
		@LauncherAPI public final AuthHandler authHandler;
		@LauncherAPI public final AuthProvider authProvider;
		@LauncherAPI public final TextureProvider textureProvider;

		// EXE binary building
		@LauncherAPI public final boolean launch4J;

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

			// Set launch4J config
			launch4J = block.getEntryValue("launch4J", BooleanConfigEntry.class);
		}

		@LauncherAPI
		public String getAddress() {
			return address.getValue();
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
		public void setAddress(String address) {
			this.address.setValue(address);
		}

		@LauncherAPI
		public void verify() {
			VerifyHelper.verify(getAddress(), VerifyHelper.NOT_EMPTY, "LaunchServer address can't be empty");
		}
	}
}
