package launcher.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.hasher.DirWatcher;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.update.LauncherRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.StreamObject;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ClientLauncher {
	private static final Set<PosixFilePermission> BIN_POSIX_PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
		PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, // Owner
		PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE, // Group
		PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE // Others
	));

	// Constants
	private static final Path NATIVES_DIR = IOHelper.toPath("natives");
	private static final Path RESOURCEPACKS_DIR = IOHelper.toPath("resourcepacks");
	private static final Pattern UUID_PATTERN = Pattern.compile("-", Pattern.LITERAL);

	// Authlib constants
	@LauncherAPI public static final String SKIN_URL_PROPERTY = "skinURL";
	@LauncherAPI public static final String SKIN_DIGEST_PROPERTY = "skinDigest";
	@LauncherAPI public static final String CLOAK_URL_PROPERTY = "cloakURL";
	@LauncherAPI public static final String CLOAK_DIGEST_PROPERTY = "cloakDigest";

	// Used to determine from clientside is launched from launcher
	private static final AtomicBoolean LAUNCHED = new AtomicBoolean(false);

	private ClientLauncher() {
	}

	@LauncherAPI
	public static boolean isLaunched() {
		return LAUNCHED.get();
	}

	@LauncherAPI
	public static Process launch(Path jvmDir, SignedObjectHolder<HashedDir> jvmHDir, SignedObjectHolder<HashedDir> clientHDir, SignedObjectHolder<ClientProfile> profile, Params params, boolean pipeOutput) throws Throwable {
		// Write params file (instead of CLI; Mustdie32 API can't handle command line > 32767 chars)
		LogHelper.debug("Writing ClientLauncher params file");
		Path paramsFile = Files.createTempFile("ClientLauncherParams", ".bin");
		try (HOutput output = new HOutput(IOHelper.newOutput(paramsFile))) {
			params.write(output);
			jvmHDir.write(output);
			clientHDir.write(output);
			profile.write(output);
		}

		// Resolve java bin and set permissions
		LogHelper.debug("Resolving JVM binary");
		Path javaBin = IOHelper.resolveJavaBin(jvmDir);
		if (IOHelper.POSIX) {
			Files.setPosixFilePermissions(javaBin, BIN_POSIX_PERMISSIONS);
		}

		// Fill CLI arguments
		List<String> args = new LinkedList<>();
		args.add(javaBin.toString());
		if (params.ram > 0 && params.ram <= JVMHelper.RAM) {
			args.add("-Xmx" + params.ram + 'M');
		}
		args.add(jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())));

		// Add classpath and main class
		Collections.addAll(args, profile.object.getJvmArgs());
		Collections.addAll(args, "-classpath", IOHelper.getCodeSource(ClientLauncher.class).toString(),
			ClientLauncher.class.getName());
		args.add(paramsFile.toString()); // Add params file path to args

		// Build client process
		LogHelper.debug("Launching client instance");
		ProcessBuilder builder = new ProcessBuilder(args);
		builder.directory(params.clientDir.toFile());
		builder.inheritIO();
		if (pipeOutput) {
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
		}

		// Let's rock!
		return builder.start();
	}

	@LauncherAPI
	public static void main(String... args) throws Throwable {
		JVMHelper.verifySystemProperties(ClientLauncher.class);
		SecurityHelper.verifyCertificates(ClientLauncher.class);
		LogHelper.printVersion("Client Launcher");

		// Resolve params file
		VerifyHelper.verifyInt(args.length, l -> l >= 1, "Missing args: <paramsFile>");
		Path paramsFile = IOHelper.toPath(args[0]);

		// Read and delete params file
		LogHelper.debug("Reading ClientLauncher params file");
		Params params;
		SignedObjectHolder<HashedDir> jvmHDir;
		SignedObjectHolder<HashedDir> clientHDir;
		SignedObjectHolder<ClientProfile> profile;
		RSAPublicKey publicKey = Launcher.getConfig().publicKey;
		try (HInput input = new HInput(IOHelper.newInput(paramsFile))) {
			params = new Params(input);

			// Read signed params
			jvmHDir = new SignedObjectHolder<>(input, publicKey, HashedDir::new);
			clientHDir = new SignedObjectHolder<>(input, publicKey, HashedDir::new);
			profile = new SignedObjectHolder<>(input, publicKey, ClientProfile.RO_ADAPTER);
		} finally {
			Files.delete(paramsFile);
		}

		// Verify ClientLauncher sign and classpath
		LogHelper.debug("Verifying ClientLauncher sign and classpath");
		SecurityHelper.verifySign(LauncherRequest.BINARY_PATH, params.launcherSign, publicKey);
		URL[] classpath = JVMHelper.LOADER.getURLs();
		for (URL classpathURL : classpath) {
			Path file = Paths.get(classpathURL.toURI());
			if (!file.startsWith(IOHelper.JVM_DIR) && !file.equals(LauncherRequest.BINARY_PATH)) {
				throw new SecurityException(String.format("Forbidden classpath entry: '%s'", file));
			}
		}

		// Start client with WatchService monitoring
		LogHelper.debug("Starting JVM and client WatchService");
		FileNameMatcher profileMatcher = profile.object.getUpdateMatcher();
		try (DirWatcher jvmWatcher = new DirWatcher(IOHelper.JVM_DIR, jvmHDir.object, null); // JVM Watcher
			 DirWatcher clientWatcher = new DirWatcher(params.clientDir, clientHDir.object, profileMatcher)) {
			// Verify current state of JVM and client dirs
			HashedDir currentJVM = new HashedDir(IOHelper.JVM_DIR, null);
			if (!jvmHDir.object.diff(currentJVM, null).isSame()) {
				throw new SecurityException("Forbidden JVM modification");
			}
			HashedDir currentClient = new HashedDir(params.clientDir, profileMatcher);
			if (!clientHDir.object.diff(currentClient, profileMatcher).isSame()) {
				throw new SecurityException("Forbidden client modification");
			}

			// Start WatchService, and only then client
			CommonHelper.newThread("JVM Directory Watcher", true, jvmWatcher).start();
			CommonHelper.newThread("Client Directory Watcher", true, clientWatcher).start();
			launch(profile.object, params);
		}
	}

	@LauncherAPI
	public static String toHash(UUID uuid) {
		return UUID_PATTERN.matcher(uuid.toString()).replaceAll("");
	}

	private static void addClientArgs(Collection<String> args, ClientProfile profile, Params params) {
		PlayerProfile pp = params.pp;

		// Add version-dependent args
		ClientProfile.Version version = profile.getVersion();
		Collections.addAll(args, "--username", pp.username);
		if (version.compareTo(ClientProfile.Version.MC172) >= 0) {
			Collections.addAll(args, "--uuid", toHash(pp.uuid));
			Collections.addAll(args, "--accessToken", params.accessToken);

			// Add 1.7.10+ args (user properties, asset index)
			if (version.compareTo(ClientProfile.Version.MC1710) >= 0) {
				// Add user properties
				Collections.addAll(args, "--userType", "mojang");
				JSONObject properties = new JSONObject();
				if (pp.skin != null) {
					properties.put(SKIN_URL_PROPERTY, new JSONArray(Collections.singleton(pp.skin.url)));
					properties.put(SKIN_DIGEST_PROPERTY, new JSONArray(Collections.singleton(
						SecurityHelper.toHex(pp.skin.digest))));
				}
				if (pp.skin != null) {
					properties.put(CLOAK_URL_PROPERTY, new JSONArray(Collections.singleton(pp.cloak.url)));
					properties.put(CLOAK_DIGEST_PROPERTY, new JSONArray(Collections.singleton(
						SecurityHelper.toHex(pp.skin.digest))));
				}
				Collections.addAll(args, "--userProperties", properties.toString());

				// Add asset index
				Collections.addAll(args, "--assetIndex", profile.getAssetIndex());
			}
		} else {
			Collections.addAll(args, "--session", params.accessToken);
		}

		// Add version and dirs args
		Collections.addAll(args, "--version", profile.getVersion().name);
		Collections.addAll(args, "--gameDir", params.clientDir.toString());
		Collections.addAll(args, "--assetsDir", params.assetDir.toString());
		Collections.addAll(args, "--resourcePackDir", params.clientDir.resolve(RESOURCEPACKS_DIR).toString());

		// Add server args
		if (params.autoEnter) {
			Collections.addAll(args, "--server", profile.getServerAddress());
			Collections.addAll(args, "--port", Integer.toString(profile.getServerPort()));
		}

		// Add window size args
		if (params.fullScreen) {
			Collections.addAll(args, "--fullscreen", Boolean.toString(true));
		}
		if (params.width > 0 && params.height > 0) {
			Collections.addAll(args, "--width", Integer.toString(params.width));
			Collections.addAll(args, "--height", Integer.toString(params.height));
		}
	}

	private static String jvmProperty(String name, String value) {
		return String.format("-D%s=%s", name, value);
	}

	private static void launch(ClientProfile profile, Params params) throws Throwable {
		// Add natives path
		JVMHelper.addNativePath(params.clientDir.resolve(NATIVES_DIR));

		// Add client args
		Collection<String> args = new LinkedList<>();
		addClientArgs(args, profile, params);
		Collections.addAll(args, profile.getClientArgs());

		// Add client classpath
		URL[] classPath = resolveClassPath(params.clientDir, profile.getClassPath());
		for (URL url : classPath) {
			JVMHelper.UCP.addURL(url);
		}

		// Resolve main class and method
		Class<?> mainClass = Class.forName(profile.getMainClass());
		Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);

		// Invoke main method with exception wrapping
		LAUNCHED.set(true);
		JVMHelper.fullGC();
		try {
			mainMethod.invoke(null, (Object) args.toArray(new String[args.size()]));
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} finally {
			LAUNCHED.set(false);
		}
	}

	private static URL[] resolveClassPath(Path clientDir, String... classPath) throws IOException {
		Collection<Path> result = new LinkedList<>();
		for (String classPathEntry : classPath) {
			Path path = clientDir.resolve(IOHelper.toPath(classPathEntry));
			if (IOHelper.isDir(path)) { // Recursive walking and adding
				IOHelper.walk(path, new ClassPathFileVisitor(result), false);
				continue;
			}
			result.add(path);
		}
		return result.stream().map(IOHelper::toURL).toArray(URL[]::new);
	}

	public static final class Params extends StreamObject {
		private final byte[] launcherSign;

		// Client paths
		@LauncherAPI public final Path assetDir;
		@LauncherAPI public final Path clientDir;

		// Client params
		@LauncherAPI public final PlayerProfile pp;
		@LauncherAPI public final String accessToken;
		@LauncherAPI public final boolean autoEnter;
		@LauncherAPI public final boolean fullScreen;
		@LauncherAPI public final int ram;
		@LauncherAPI public final int width;
		@LauncherAPI public final int height;

		@LauncherAPI
		public Params(byte[] launcherSign, Path assetDir, Path clientDir, PlayerProfile pp, String accessToken, boolean autoEnter, boolean fullScreen, int ram, int width, int height) {
			this.launcherSign = Arrays.copyOf(launcherSign, launcherSign.length);

			// Client paths
			this.assetDir = assetDir;
			this.clientDir = clientDir;

			// Client params
			this.pp = pp;
			this.accessToken = SecurityHelper.verifyToken(accessToken);
			this.autoEnter = autoEnter;
			this.fullScreen = fullScreen;
			this.ram = ram;
			this.width = width;
			this.height = height;
		}

		@LauncherAPI
		public Params(HInput input) throws IOException {
			launcherSign = input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH);

			// Client paths
			assetDir = IOHelper.toPath(input.readString(IOHelper.BUFFER_SIZE));
			clientDir = IOHelper.toPath(input.readString(IOHelper.BUFFER_SIZE));

			// Client params
			pp = new PlayerProfile(input);
			accessToken = SecurityHelper.verifyToken(input.readASCII(-SecurityHelper.TOKEN_STRING_LENGTH));
			autoEnter = input.readBoolean();
			fullScreen = input.readBoolean();
			ram = input.readVarInt();
			width = input.readVarInt();
			height = input.readVarInt();
		}

		@Override
		public void write(HOutput output) throws IOException {
			output.writeByteArray(launcherSign, -SecurityHelper.RSA_KEY_LENGTH);

			// Client paths
			output.writeString(assetDir.toString(), IOHelper.BUFFER_SIZE);
			output.writeString(clientDir.toString(), IOHelper.BUFFER_SIZE);

			// Client params
			pp.write(output);
			output.writeASCII(accessToken, -SecurityHelper.TOKEN_STRING_LENGTH);
			output.writeBoolean(autoEnter);
			output.writeBoolean(fullScreen);
			output.writeVarInt(ram);
			output.writeVarInt(width);
			output.writeVarInt(height);
		}
	}

	private static final class ClassPathFileVisitor extends SimpleFileVisitor<Path> {
		private final Collection<Path> result;

		private ClassPathFileVisitor(Collection<Path> result) {
			this.result = result;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (IOHelper.hasExtension(file, "jar") || IOHelper.hasExtension(file, "zip")) {
				result.add(file);
			}
			return super.visitFile(file, attrs);
		}
	}
}

// It's here since first commit, there's no any reasons to remove :D
// ++oyyysssssssssssooooooo++++++++/////:::::-------------................----:::----::+osssso+///+++++ooys/:/+ssssyyssyooooooo+++////:::::::::-::///++ossyhdddddddhhys/----::::::::::::::/:///////////////
// ++oyyssssssssssoooooooo++++++++//////:::::--------------------------------:::::-:::/+oo+//://+oo+//syysssssyyyyyhyyyssssssoo+++///::--:----:--:://++osyyhdddmddmdhys/------::::::::::::::://////////////
// ++syyssssssssssoooooooo++++++++///////:::::::::::::::-----------------------::--::/++++/:--::/+++//osysshhhhyhhdyyyyyssyssoo++//::-------------::/+++oyhyhdddmddmdhy+--------:::::::::::::::////////////
// ++ssssssssssssooooooooo++++++///////::::::::-------------------------------------::/+//:----://+//+oyhhhddhhdhhhyhyyyyhysoo+//::----------------://++oshhhhmddmddmhhs:---------::::::::::::::://////////
// ++sssssssssssooooooooo++++////::::::::::------------------------------------:---:::///:-----://++osyhddddhdddhhhhhyyhdysso+/::-------------------://+ooyhhddmddmdmdhy/----------::::::::::::::://///////
// ++sssssssssoooooooo+++////::::::::::-------------------------------------::///::::///::--::/++osyhhddmmdhddddhhhhhyhdhyso+/:----------------------::/++oyhdhdmmmmmddho-------------:::::::::::::////////
// /+ssssssssosooo+++/////::::::::::----------------------------------:::::://+++ooossso////++oosyhddddmmdhddddhhhhhhhdhyddhs+:-----------------------::/++syhddmmmmdddho--------------:::::::::::::://////
// /+sssssssooo+++//////::::::::::-------------------------------:::://++++ooooooooooooo+/+oosyyhhddddmmddddddhhhhhhhdhyyssyyhs+::-------------::://++/+/o++shddddmmmddh+----------------::::::::::::://///
// /+sssooso++++/////::::::::::::------------------------::::::///++o++//:--...............-/osyyhddmmmmddddhhhhhyyhdhhdmddhysoso::--------::/osyyyssyyssssoosdmddddddy+:-------------------:::::::::::////
// /+sssooso+++////::::::::::::--------------------:::::////++o+//:-......................--+ossydddmmdmmdddhhhhyyhddyyydmhhooo++/:-------::/+oshhhyso+/://++ohmmmdhhy+:---------------------:::::::::::://
// /+sooooso++/////:::::::::::--------------:::::///+++oo+//:-............................-/+oyydddmmddddddhhhyyyhhhyysosss+:/+///:--.---://+o/ohhdhoyyo+/://+ymmdhsyo:------------------------:::::::::://
// /+ooooooso+////:::::::::------------:::::///++oooo/:-..................................:/oyyyhdmdddddddhhhhyyhyss++///:::/::::/:-...--::://-/shy+-:+s+/::/+hdhysos/--------------------------::::::::::/
// /+oooooooo++///::::::::::-:----:::::::///+oosyy/-.....................................-/+syhhddddddddhhhhhhhysso+/::::-----:::::-..------:::::////:::::::/sdysso+o:..----.--------------------:::::::::/
// /+ooooooooo++///:::::::::::::::::::///++osyhmmdo:-...................................-/+syyhdddddddddhdddmmhys+/::----------:::--..---------::::::-----::+dhsoso++:....--.--..----------------:::::::::/
// /ooooooooooo++///::::::://///++oooosssyydddhdhs+so/+++:-............................./+syyhdddddddddddddmmmhs+//:------------:--..---------------------:/hdsooso+/-....--.....------------------:::::::/
// /oooooooo++++o++//:::::::///++ossso+ooosysos++o/so//////--..........................:yhddhdddmmdddmmmdddmddhs+/::----------:::---.--------------------:/hdhsooso+/-.....-........----------------::::://
// /ooooooo+++++++++//:::::::::/+o+ooooyyso+oo/++o/oo///://////::--...................-sdhddhddmmdhdmmmddhhdmNds+/:::--------:/:------------------------::sdhyooyso+:-...............---------------::::://
// /ooooooo+++++++++++//:::://+++++ssyys+/+y+///++/so///:://://::::/---://............-smdddddmmhyhhdddhhsydmNmyo//:::------://:--------:::------------::odhysshyo++-................---------------:::::/+
// /oooooo+++++++++++/+o+/++++++++ohhoo+//yo//:/+//so:/:////::+////+///+so/++/:--------:hdddmdhyssyhhddhsosdmNmho+/:::------////:------::::-----------::+hdhhhyyo+o/-................----------------::://+
// /ooo++o++++++++++//+oooooo++++oyyoo+//os+////+//so/://::////+///+//+ssooo+++++++/++//odhhhyoooosyhhhysoodmmNds+//:::-----:++o+/:::/+//:----------::/+ydhyyyyo+oo:...................--------------:::/++
// /oooo+++++++++++/////+ossoooooss++////+so/::++//os://:/::://////++/os+++++++/++++++++osyso++++osyyyyssoodmmmmyo+/::::-----::/++++////:--------::::/+shhysoso+ss/.....................------------:::/+++
// +ooo++++++++++/////////+oooosso////:::+oso/:/+//oy+//:///://////+//o+/++////////++++ooooo++++ossyhhsoosodmmmmds+//////::::::::::::---------::::://+shhyso+++ss:........................----------:://+++
// +ooo++++++++++///////////////++++//:::/++o////+/+yo//////////////:/o:/+//////////+oooo+oooo++ooyyyyysssshmmmmmds+//////+++++///:::::::::::::::://+syhhso+++oo/..........................---------::/++++
// +o+o+++++++++///////////////::///++//:::/+///////oy+///://///////-++:+/://://::/++so++ooooooossysyyhhssyhmmmmNmds+//:::/++++oooooooo+++//:::::/+oosyys++oso+/-..........................--------:://+++/
// +oo+++++++++/////////////////:::://++//////:/+/:/+ss+//////++///:://///::://:://++o++ossssyyssosyyhhyssydmmmmNNmmyo//////++///////////::::::/+oooosossssyyo/-...........................--------://+++/:
// +++++++++++//////////////::/::::::::///++/:///:://oss//:////++//::+/////:::/:://+so++ssyyhhhoosyyyysoosydmmmNNmmmmho+//::////+++++///:::::/+osssssshmdssyyo-............................-------::/++++::
// +oo++++++++++/////////////:/::::::::::://+/////:/:+oso+:////++/://+++++++/::///+oooosyyyhhhsossssso+osshmmmmNmmmmmdys+/::::---:::::::::/+osyyyssyddmmhsyhy+-.............................-----:://+++/::
// +o++++++++++///////////:/::::::::::::---::/+++/::::+oso/::/://:://++++o+:::+//++ssooyyyhhyyssssoo++sssymmmmNmmmmmmhysso/:::::-----:::/+yhhhyyyhddmmdhhyhys+:-............................-----://+++/:::
// ++++++++++++/////////////::::::::----------://////:/+oso+/:::::://+++o+//+ooooo+++oshyhhhysssso++ososhmNNNNNmmmmhhyoooooo+/////://+oyhmmdhhyhdddddhyyysssoo+:--.........................-----://++//::::
// o++++++++++/////////////:::::::::-------------:////::/ooo/:::::///+os++ssyyyyssssssyhhhysssssoooooosdmNNNNNmmmdhsss+++ooooooossyyhdmmmmdhyysyhhyssooooossooys+//:--.....................----::/+++/::::/
// o+++++++++//////////////::::::::----------.....-://////+oo+:://://ooooyhhhhdddhhhyhhhhyssssssssooshmNNNNNNNmmmdooo++//++oooossshddmmmddys++ooo+/+++ossyhhyyyyysoo+//:--.................----://++/::::/o
// ++++++++++//////////////:::::::--------..........-::///:/+++::::/+o+/ohhhhhhyyyyyyhhhysyyysyyyyhdmNNNNNNNNmmmhso++//:///+ooossydddmmmddhyysso+/++ossshddddddhyyso++++////::::::--.......---://++/:-:::o/
// o+++++++++///////////////:::::::------..............-:////++/:///+osoyhhhhyssssyhhdddhhhyyhhdmmmmmmmmmmmmmmmho+oo+/::::///+++oyhhdmmmddhhysoo+oosyhhdmmmddddhhyssoo+++++++++++///:-....---://++//-:::o/:
// +++++++++//+///////////:/::::::------.................-://////////+ossyyyyyhhhhddddhhhhddmmmmddddmmmmmmmmmdyo+++os/::::::://+ydhhmmmddddddddddmmmmmmmmmmmmddhyyysoo+o+++//++////::::-----::/++//--::++::
// +++++++++///////////////:::::::------....................-:///++/::+syyyhhhhhhhhhhhhddmmmddddddddmmmmmmmmdso++++++++/:::://ohdhydmmddddhdmmmmmmhhdmmmmmmmmmhhyyyysooso+/+////////////:::::/+++/:-::++:::
// ++++++++++//////////////:/:::::------......................-:/+/:::+shhdhhhhhhhhhdddmmmddddhhhhhddmmmmmmdyo+++++/+/++++++oydmdhyhdddhhsssydmdmmddddddmmmmmdhhsyysoso++++///+//////://+//////+/:-::/o::::
// ++++++++++/////////////://:::::------.........................::::/osdmmmmmddddddddddhhysssooosyhdmmmmmdsooo+o++/+++++++shmmddhyyhmhso/+shhdmddhhhdmmmmddhhyyysoooo//+++/://///:/+//+///////::-::/o/:-::
// +++++++++++////////////:/::::::-----..........................-:://+oymmmmmddddhhhhhhhhyysoo+osyhhdmdhyoo/++so++++oooooohmmmmdhhyhdh+//+ydhyssshddmmdhhhyyssso+/+o+//+////:////+//+////+////+++////:--::
// +++++++++//////////////://:::::-----.........................-::::///oyddddhhhhhdmdddhddhhysssyhhdmdyo++o/+/+oooo+++o+osdmmmmdhhyhdd+/+syo++oshddhyyhhyssooo+///+/++/+++/://:/+/++++o++oooooso++oo++/:::
// o+++++++++///////////////::::::-----.........................::::::++o+shmdyhhsyhdddmmddddhsosyhhmmh++/+o+/+/++++ooososhmddddhyyso++oosso++osyyhhddmdysooo+//+/o+++/+/o//o+/++++/+ooosssooooo+++++/+++//
// o+++++++++///////////////:::::------........................-::::::+ossyyhyo+++shddmmdyyhyo//osysdds//++++o+/++o+o++oosdhdddhhyyysoooysoosyyyhdddhmmss+o+o++//+/+++//+++++oo++/oossssssssoooooooo+++++o+
// o++++++++++/////////////:/::::-----.........................:::::/oyysyysooo+oyhdddmdhhddh+++osyhdh//////++o+///++ooooyshdmmmmdddhdhssshhysydmmysshyoo++++++/+++////+o+ooso+++ssyyyyyyyyysssooooo+o+++++
// ooo+++++++++//////////////::::-----.........................:-:::ohdhhhhysssssyhhhyhhhdmmd+++oyddho////:/++++++/+/+++oshmmmmmdhyyyssyhdysshmmmdhyysoo+/+o+//++/+//++ososso++syhyyyhyyyyyyyysssysssysssss
// ooo+++++++++////////////::::::------.......................---::+hddhhyysoosoooosssooosyyyssyhdhho+///////////+/+++++symmmdys+//+oyhhyssyhdmmmmmhyo+++++++/+++++oo++ohssoooyhhhhhhhyyyhyhhyyhyyhyyyyyyyy
// ooo++++++/++/////////////::::::-----.......................:::/:/+sssyyysssyo++oosyhhhhyyssoo+oooo+///////////+++++oyhddhysssyyhdddhysoshmNNNNmmyoo+++++++/+/++++o+osdyssoyhhhhhhhhhhhhhhyhhhhhhhhhhhhhh
// oooo++++++++/////////////::::::-----.......................:::::///++ooooo++/:/+syhhddys++/:/+++o+++////++////+++osysydhydmddddyshysoshmmmmmNNmyooo+oo+++++/++oooooshdyoyyhhhhhhhhhhdhhhhhdhhhhdhhhhdhdd
// ooo+++++++++/////////////::::::----.......................-://::::://////:///://ooooo+////////++oo++++////++//oosyhyyyyshddmmhyhyysyhmNmdddmNdysooooosooooooooooso+shdysyhdhhhhhhhdddhdhdddddddhhhdddddd
// oooo+++++++++////////////::::::----.......................::/:::::///////://::::::::::://:/::/++oo+++////++++oohyhhhhhhsydmdhhhyyhhyymmmdddmmyssoooosysossosoo+ssossddysydhhddhhddhhddddddddddddhddddddd
// oooo+++++++++/++//////////::::-----......................-:::::::///////::/:::::::::::::::::/:/++oo++++++/++ssyhhddddhyyohmddhhddhyhhmmmddmmdsssoosssosssossooossoyhmhyyhdhddddhhhdhhddddddddhddddmddddh
// oooo+++++++++//+//////////::::----.......................-:::::///:/::::::/::::-::::/:-:::::///++ooooo+++++shhdddddhhyyhysdddhdmdhhddmdhhydmhssosssysssssssoosssssyhmysyddddddhdhdddddddddddddddmddddhmm
// ooooo+++++++++++//////////::::----.......................::::::://://:::://::-:::-::::::::::////++oosoo+o+oyhhdddddhhhhddyyhyhddhhhdmmmhyyddhssssssyyysssssssssysyyddyyhddddddddhhhhhddddddddmddddddmmNN
// ooooo+o++++++++++/////////::::----.......................::::::/:///::::::::::::::::::::/:::::///++osssososssyhddddhhhhdddssoossyhdmmmmhyyhhhyssyyyyysyyyssyssssshhmhyhdddddddddddhdhhddmddmdddddhdmmmNN
// ooooooo++++++++++/////////:::-----......................-:::::/::::/:/::://::::/::::::::::::::/:://+ooosssyysyyyyyyyhddmmysssssyyhhddmdhysyhhyyyyyyyysyyyyysssyysyydhydddddddhddddhddddddddddddhddmmmmNm
// ooooooo+++++++++++////////:::-----......................:::::/:::://:-::/:://::/::::-:::::::::/:/::/++oosssyyysssssyhdddhsyysssyyhhddhhyssyyyyyyyyysyyssyyyyyyyyyhhdyhdddhddddhddddddmmddddddhdddmmmmmmd
// ooooooo+o+++++++++++//////:::-----.....................-::::::/:///:::::::::/::/::::::::::/::/:///::////+oosyysssyssyyyysssyhyyhddmmdyysoossyssyyysssyyssysyyyyshyddhddhhhddhdddddddddmddhdddhhdhhdmmmmd
// ooooooooo++++++++++///////:::-----.....................-::::////:::::::::::::::::::://::::/-:::::/:://::///+osyyssosossssssysyhdddddhysoosssyyssssyssyyyyyysssyshddddddhddddddddddmddddhdhhhddddmmmmmmdm
// oooooooooo+o++++++++++////::::----.....................::://::::/::::::::::::::/:://::::::::::/::::::///////++ooooosoo+ososssshddhhysssooossssssssyssysyyyyyysysyhddddddddddddddddddddhddhhhddddmmmmdmdd
// soooooooooo+++++++++++////::::----....................-:::::////:::/:::::::/::://///:/::::::::/:///://:/://+//+++++++oo+ooooooshdyyyssossoosssyssyysssssyyyyysssshdddddddddddddddddddhhhdhhhddmmmmdmdmmd
// sssooooooooo++++++++++////::::----....................-:::/:://///:::::::::::::/://:/::://::::::://:::/://///++/+++/+++o+oosssyyysssyysosoososysssyysyyyyhysyssyyhdddddddddddddddddhddhddhddmmmmmdmmmmNm
// ssssoooooooo++++++++++////:::::----...................:::::://::::::::::::-::://///:////::/:/:::://///:://://+o++++++oooosoosoossoososssooososssssyyyyyssyyyssyyyhmmdddhhhhhhhddhdddddddddmmmmmmddmmmNNm
// sssoooooooooooo+++++++/////::::----...................:://///:/:::::::::::::::://///////////+/:/://://::////+++++++ooossoossososososssoososossssssyssyyyyyyyyyysyhdhhhhhhhhhhhdhhhhhdddddmmmmmmddmmNNNmN
// ssssoooooooooooo+++++++////::::----..................-::::/:/:::::::::::::://:::://///:/:///////////:://+/++++++oooooooosssssoosssssssosooosooossssyyysyyysyyyysoyyooossssyyhhhddhddhhhhhddmmmmmmmmmmmmm
// ssssssoooooooooooo++++++///:::------.................::::::://:::::::-:::::/::://////:///////////////////++o++oo+ooooosoossossosssossssoosoosoosssyysyyyysyyssysoyo+oooo//+osossosyhhhhhhddmmddmmmmdmmmm
// sssssssosooooooooooo++++///::::-----................./::::/:/:::::::::-::/:::/://///////////+//++/++//+oo+oo++ooo+ooooosssosoossssysosssssooooossysyyyyysyyyyyssoo++ooo+//+s/:---::/+oosyyyhhhhdmmmmmmmm
// yssssssssssoooooooo+++++///::::-----................://::::::/:/::::::-::/::::://///////////////+/++++++oo+++oooooo+oooosssossosysosssosssoooosssyyyyyyyyyyyysso++++++//:/o/:--::::::::::/++oossymmmmmmm
// yysssssssssssooooooo++++////:::------..............-///:::/:::/:::/::::://:::::::///////////////++++++o++++o++oo+ooooooooosysosssssoosssssosoossyyyyyssyyyyysss++oooo+///oo/::::-::::::::::::////sdmmmNN
// yyysssssssssssooooooo++++///:::-------.............:/:::/:/:::::::::://://::/::::/:////////+/////++++++oo+ooo++oooo+oooossssssssssoossoooooossssyyyyyyyyyyyysso++ooo/--:/:.::::..::::-..-::://////ohmmNN
// yyyssssssssssssoooooo++++///::::------............-/:::/://///:::::::::://::/::::::////:///+/+++/++++++/++++++o+o++oooooooosssssoossssssssosssssyyyyyyyyyyyyso++ooo:./.-+-.---...--:::.--::::://////oydm
// yyyysyssssssssssoooooo+++///::::--------.........-/:::::////::/::::::::::/:://:/:::///////////+/+++++//++++++++oo+++ooosoooossssssossoosssssssssyyyyyyyyyyysoo+oooo`:+.+.--.--.-::.-.----::-:.////////+s
// yyyyyyyyyssssssssooooo+++///:::::----------......:///:::://///:::::::::::://:/:::://///////+////++++////+++++++++o++oooosssososossossososssssysyyysyyyyyyysso+oooo+--:-+-.-.-------::----:--:-:////////+
// yyyyyyyyyyssssssssoooo++++//:::::-------------.--////://::/:::::/::::/:::://:/::::/:/:///////+//+++///+//++/++++o++++ooooooosssossssosoossssssssssyyyyyyyyso++ooso++/o+/::::::::::::::://////////////+++
// yyyyyyyyyyyssssssssoooo+++///::::---------------:////////////:/:///::::://////::::::://///////+/++////+//+/++++++++ooosooooooososoosososssosyyyssyysyyyyysso+oso+//+oo/:::::::::::::/:::///////////+++++
// yyyyyyyyyyyyysssssssooo+++///:::::---------------+////:///:///:::::///::://///:::/:::///////////++/++++///++++/++o+oooooooooosoososoossssssssysssyyyyyyysoo+ooso+++ss/::::::::::::::://///////////+++++o
// hhyyyyyyyyyyyyyssssssooo++///:::::---------------/////////:////://:///::://///://::://////////++////+///+///++o++++ooooooooooooooooooooososososssyssssysooo+oso+++os+:::::///////////////////////+++++os
// hhhyyyyyyyyyyyyyyssssooo+++///::::---------------:////////////:////////:://///::://////////++/+++//////+++++++++++++++oo+++o+ooososooosssoossssssssssssooo+osso+oos+:::////////////////////////++++++osy
// hhhhhhyyyyyyyyyyyyssssoo+++////:::::--------------:///////////:///://///:///////:///://///++/++++//++/++++/++++++++++++++o+oooooooosoosssssoosssssssssso++ossooooyo/:///////////////////////++++++++osyh
