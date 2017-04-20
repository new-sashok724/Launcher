package launcher.client;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;
import launcher.Launcher;
import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.ClientProfile.Version;
import launcher.hasher.DirWatcher;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.JVMHelper.OS;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.update.LauncherRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.StreamObject;

public final class ClientLauncher {
    private static final String MAGICAL_INTEL_OPTION = "-XX:HeapDumpPath=ThisTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump";
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

    public static String jvmProperty(String name, String value) {
        return String.format("-D%s=%s", name, value);
    }

    @LauncherAPI
    public static Process launch(Path jvmDir, SignedObjectHolder<HashedDir> jvmHDir,
        SignedObjectHolder<HashedDir> assetHDir, SignedObjectHolder<HashedDir> clientHDir,
        SignedObjectHolder<ClientProfile> profile, Params params, boolean pipeOutput) throws Throwable {
        // Write params file (instead of CLI; Mustdie32 API can't handle command line > 32767 chars)
        LogHelper.debug("Writing ClientLauncher params file");
        Path paramsFile = Files.createTempFile("ClientLauncherParams", ".bin");
        try (HOutput output = new HOutput(IOHelper.newOutput(paramsFile))) {
            params.write(output);
            profile.write(output);

            // Write hdirs
            jvmHDir.write(output);
            assetHDir.write(output);
            clientHDir.write(output);
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
        args.add(MAGICAL_INTEL_OPTION);
        if (params.ram > 0 && params.ram <= JVMHelper.RAM) {
            args.add("-Xms" + params.ram + 'M');
            args.add("-Xmx" + params.ram + 'M');
        }
        args.add(jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())));
        if (Config.ADDRESS_OVERRIDE != null) {
            args.add(jvmProperty(Config.ADDRESS_OVERRIDE_PROPERTY, Config.ADDRESS_OVERRIDE));
        }
        if (JVMHelper.OS_TYPE == OS.MUSTDIE && JVMHelper.OS_VERSION.startsWith("10.")) {
            LogHelper.debug("MustDie 10 fix is applied");
            args.add(jvmProperty("os.name", "Windows 10"));
            args.add(jvmProperty("os.version", "10.0"));
        }

        // Add classpath and main class
        Collections.addAll(args, profile.object.getJvmArgs());
        Collections.addAll(args, "-classpath", IOHelper.getCodeSource(ClientLauncher.class).toString(), ClientLauncher.class.getName());
        args.add(paramsFile.toString()); // Add params file path to args

        // Print commandline debug message
        LogHelper.debug("Commandline: " + args);

        // Build client process
        LogHelper.debug("Launching client instance");
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(params.clientDir.toFile());
        builder.inheritIO();
        if (pipeOutput) {
            builder.redirectErrorStream(true);
            builder.redirectOutput(Redirect.PIPE);
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
        SignedObjectHolder<ClientProfile> profile;
        SignedObjectHolder<HashedDir> jvmHDir, assetHDir, clientHDir;
        RSAPublicKey publicKey = Launcher.getConfig().publicKey;
        try (HInput input = new HInput(IOHelper.newInput(paramsFile))) {
            params = new Params(input);
            profile = new SignedObjectHolder<>(input, publicKey, ClientProfile.RO_ADAPTER);

            // Read hdirs
            jvmHDir = new SignedObjectHolder<>(input, publicKey, HashedDir::new);
            assetHDir = new SignedObjectHolder<>(input, publicKey, HashedDir::new);
            clientHDir = new SignedObjectHolder<>(input, publicKey, HashedDir::new);
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
        boolean digest = !profile.object.isUpdateFastCheck();
        LogHelper.debug("Starting JVM and client WatchService");
        FileNameMatcher assetMatcher = profile.object.getAssetUpdateMatcher();
        FileNameMatcher clientMatcher = profile.object.getClientUpdateMatcher();
        try (DirWatcher jvmWatcher = new DirWatcher(IOHelper.JVM_DIR, jvmHDir.object, null, digest); // JVM Watcher
            DirWatcher assetWatcher = new DirWatcher(params.assetDir, assetHDir.object, assetMatcher, digest);
            DirWatcher clientWatcher = new DirWatcher(params.clientDir, clientHDir.object, clientMatcher, digest)) {
            // Verify current state of all dirs
            verifyHDir(IOHelper.JVM_DIR, jvmHDir.object, null, digest);
            verifyHDir(params.assetDir, assetHDir.object, assetMatcher, digest);
            verifyHDir(params.clientDir, clientHDir.object, clientMatcher, digest);

            // Start WatchService, and only then client
            CommonHelper.newThread("JVM Directory Watcher", true, jvmWatcher).start();
            CommonHelper.newThread("Asset Directory Watcher", true, assetWatcher).start();
            CommonHelper.newThread("Client Directory Watcher", true, clientWatcher).start();
            launch(profile.object, params);
        }
    }

    @LauncherAPI
    public static String toHash(UUID uuid) {
        return UUID_PATTERN.matcher(uuid.toString()).replaceAll("");
    }

    @LauncherAPI
    public static void verifyHDir(Path dir, HashedDir hdir, FileNameMatcher matcher, boolean digest) throws IOException {
        if (matcher != null) {
            matcher = matcher.verifyOnly();
        }

        // Hash directory and compare (ignore update-only matcher entries, it will break offline-mode)
        HashedDir currentHDir = new HashedDir(dir, matcher, false, digest);
        if (!hdir.diff(currentHDir, matcher).isSame()) {
            throw new SecurityException(String.format("Forbidden modification: '%s'", IOHelper.getFileName(dir)));
        }
    }

    private static void addClientArgs(Collection<String> args, ClientProfile profile, Params params) {
        PlayerProfile pp = params.pp;

        // Add version-dependent args
        Version version = profile.getVersion();
        Collections.addAll(args, "--username", pp.username);
        if (version.compareTo(Version.MC172) >= 0) {
            Collections.addAll(args, "--uuid", toHash(pp.uuid));
            Collections.addAll(args, "--accessToken", params.accessToken);

            // Add 1.7.10+ args (user properties, asset index)
            if (version.compareTo(Version.MC1710) >= 0) {
                // Add user properties
                Collections.addAll(args, "--userType", "mojang");
                JsonObject properties = Json.object();
                if (pp.skin != null) {
                    properties.add(SKIN_URL_PROPERTY, Json.array(pp.skin.url));
                    properties.add(SKIN_DIGEST_PROPERTY, Json.array(SecurityHelper.toHex(pp.skin.digest)));
                }
                if (pp.cloak != null) {
                    properties.add(CLOAK_URL_PROPERTY, Json.array(pp.cloak.url));
                    properties.add(CLOAK_DIGEST_PROPERTY, Json.array(SecurityHelper.toHex(pp.cloak.digest)));
                }
                Collections.addAll(args, "--userProperties", properties.toString(WriterConfig.MINIMAL));

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
        if (version.compareTo(Version.MC194) >= 0) { // Just to show it in debug screen
            Collections.addAll(args, "--versionType", "Launcher v" + Launcher.VERSION);
        }

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

    private static void addClientLegacyArgs(Collection<String> args, ClientProfile profile, Params params) {
        args.add(params.pp.username);
        args.add(params.accessToken);

        // Add args for tweaker
        Collections.addAll(args, "--version", profile.getVersion().name);
        Collections.addAll(args, "--gameDir", params.clientDir.toString());
        Collections.addAll(args, "--assetsDir", params.assetDir.toString());
    }

    private static void launch(ClientProfile profile, Params params) throws Throwable {
        // Add natives path
        JVMHelper.addNativePath(params.clientDir.resolve(NATIVES_DIR));

        // Add client args
        Collection<String> args = new LinkedList<>();
        if (profile.getVersion().compareTo(Version.MC164) >= 0) {
            addClientArgs(args, profile, params);
        } else {
            addClientLegacyArgs(args, profile, params);
        }
        Collections.addAll(args, profile.getClientArgs());
        LogHelper.debug("Args: " + args);

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
            System.setProperty("minecraft.applet.TargetDirectory", params.clientDir.toString()); // For 1.5.2
            mainMethod.invoke(null, (Object) args.toArray(new String[args.size()]));
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
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
        private final byte[] launcherSign;

        @LauncherAPI
        public Params(byte[] launcherSign, Path assetDir, Path clientDir, PlayerProfile pp, String accessToken,
            boolean autoEnter, boolean fullScreen, int ram, int width, int height) {
            this.launcherSign = launcherSign.clone();

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
            assetDir = IOHelper.toPath(input.readString(0));
            clientDir = IOHelper.toPath(input.readString(0));

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
            output.writeString(assetDir.toString(), 0);
            output.writeString(clientDir.toString(), 0);

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
