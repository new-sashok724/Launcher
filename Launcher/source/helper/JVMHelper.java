package launcher.helper;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Locale;

import com.sun.management.OperatingSystemMXBean;
import launcher.LauncherAPI;
import sun.misc.Unsafe;

public final class JVMHelper {
    // MXBeans exports
    @LauncherAPI public static final RuntimeMXBean RUNTIME_MXBEAN = ManagementFactory.getRuntimeMXBean();
    @LauncherAPI public static final OperatingSystemMXBean OPERATING_SYSTEM_MXBEAN =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // System properties
    @LauncherAPI public static final OS OS_TYPE = OS.byName(OPERATING_SYSTEM_MXBEAN.getName());
    @LauncherAPI public static final String OS_VERSION = OPERATING_SYSTEM_MXBEAN.getVersion();
    @LauncherAPI public static final int OS_BITS = getCorrectOSArch();
    @LauncherAPI public static final int JVM_BITS = Integer.parseInt(System.getProperty("sun.arch.data.model"));
    @LauncherAPI public static final int RAM = getRAMAmount();

    // Public static fields
    @LauncherAPI public static final Unsafe UNSAFE;
    @LauncherAPI public static final Lookup LOOKUP;
    @LauncherAPI public static final Runtime RUNTIME = Runtime.getRuntime();
    @LauncherAPI public static final ClassLoader LOADER = ClassLoader.getSystemClassLoader();

    // Useful internal fields and constants
    private static final String JAVA_LIBRARY_PATH = "java.library.path";
    private static final MethodHandle MH_SET_USR_PATHS;
    private static final MethodHandle MH_SET_SYS_PATHS;
    private static final Object UCP;
    private static final MethodHandle MH_UCP_ADDURL_METHOD;
    private static final MethodHandle MH_UCP_GETURLS_METHOD;
    private static final MethodHandle MH_UCP_GETRESOURCE_METHOD;
    private static final MethodHandle MH_RESOURCE_GETCERTS_METHOD;

    private JVMHelper() {
    }

    @LauncherAPI
    public static void addClassPath(URL url) {
        try {
            MH_UCP_ADDURL_METHOD.invoke(UCP, url);
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    public static void addNativePath(Path path) {
        String stringPath = path.toString();

        // Add to library path
        String libraryPath = System.getProperty(JAVA_LIBRARY_PATH);
        if (libraryPath == null || libraryPath.isEmpty()) {
            libraryPath = stringPath;
        } else {
            libraryPath += File.pathSeparatorChar + stringPath;
        }
        System.setProperty(JAVA_LIBRARY_PATH, libraryPath);

        // Reset usrPaths and sysPaths cache
        try {
            MH_SET_USR_PATHS.invoke((Object) null);
            MH_SET_SYS_PATHS.invoke((Object) null);
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    @SuppressWarnings("CallToSystemGC")
    public static void fullGC() {
        RUNTIME.gc();
        RUNTIME.runFinalization();
        LogHelper.debug("Used heap: %d MiB", RUNTIME.totalMemory() - RUNTIME.freeMemory() >> 20);
    }

    @LauncherAPI
    public static Certificate[] getCertificates(String resource) {
        try {
            Object resource0 = MH_UCP_GETRESOURCE_METHOD.invoke(UCP, resource);
            return resource0 == null ? null : (Certificate[]) MH_RESOURCE_GETCERTS_METHOD.invoke(resource0);
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    public static URL[] getClassPath() {
        try {
            return (URL[]) MH_UCP_GETURLS_METHOD.invoke(UCP);
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    public static void halt0(int status) {
        LogHelper.debug("Trying to halt JVM");
        try {
            LOOKUP.findStatic(Class.forName("java.lang.Shutdown"), "halt0", MethodType.methodType(void.class, int.class)).invokeExact(status);
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    public static boolean isJVMMatchesSystemArch() {
        return JVM_BITS == OS_BITS;
    }

    @LauncherAPI
    public static void verifySystemProperties(Class<?> mainClass, boolean requireSystem) {
        Locale.setDefault(Locale.US);

        // Verify class loader
        LogHelper.debug("Verifying class loader");
        if (requireSystem && !mainClass.getClassLoader().equals(LOADER)) {
            throw new SecurityException("ClassLoader should be system");
        }

        // Verify system and java architecture
        LogHelper.debug("Verifying JVM architecture");
        if (!isJVMMatchesSystemArch()) {
            LogHelper.warning("Java and OS architecture mismatch");
            LogHelper.warning("It's recommended to download %d-bit JRE", OS_BITS);
        }
    }

    @SuppressWarnings("CallToSystemGetenv")
    private static int getCorrectOSArch() {
        // As always, mustdie must die
        if (OS_TYPE == OS.MUSTDIE) {
            return System.getenv("ProgramFiles(x86)") == null ? 32 : 64;
        }

        // Or trust system property (maybe incorrect)
        return System.getProperty("os.arch").contains("64") ? 64 : 32;
    }

    private static int getRAMAmount() {
        int physicalRam = (int) (OPERATING_SYSTEM_MXBEAN.getTotalPhysicalMemorySize() >> 20);
        return Math.min(physicalRam, OS_BITS == 32 ? 1536 : 4096); // Limit 32-bit OS to 1536 MiB, and 64-bit OS to 4096 MiB (because it's enough)
    }

    public static Class<?> firstClass(String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name, false, LOADER);
            } catch (ClassNotFoundException ignored) {
                // Expected
            }
        }
        throw new ClassNotFoundException(Arrays.toString(names));
    }

    static {
        try {
            MethodHandles.publicLookup(); // Just to initialize class

            // Get unsafe to get trusted lookup
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);

            // Get trusted lookup and other stuff
            Field implLookupField = Lookup.class.getDeclaredField("IMPL_LOOKUP");
            LOOKUP = (Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), UNSAFE.staticFieldOffset(implLookupField));
            MH_SET_USR_PATHS = LOOKUP.findStaticSetter(ClassLoader.class, "usr_paths", String[].class);
            MH_SET_SYS_PATHS = LOOKUP.findStaticSetter(ClassLoader.class, "sys_paths", String[].class);

            // Get UCP stuff1
            Class<?> ucpClass = firstClass("jdk.internal.loader.URLClassPath", "sun.misc.URLClassPath");
            Class<?> loaderClass = firstClass("jdk.internal.loader.ClassLoaders$AppClassLoader", "java.net.URLClassLoader");
            Class<?> resourceClass = firstClass("jdk.internal.loader.Resource", "sun.misc.Resource");
            UCP = LOOKUP.findGetter(loaderClass, "ucp", ucpClass).invoke(LOADER);
            MH_UCP_ADDURL_METHOD = LOOKUP.findVirtual(ucpClass, "addURL", MethodType.methodType(void.class, URL.class));
            MH_UCP_GETURLS_METHOD = LOOKUP.findVirtual(ucpClass, "getURLs", MethodType.methodType(URL[].class));
            MH_UCP_GETRESOURCE_METHOD = LOOKUP.findVirtual(ucpClass, "getResource", MethodType.methodType(resourceClass, String.class));
            MH_RESOURCE_GETCERTS_METHOD = LOOKUP.findVirtual(resourceClass, "getCertificates", MethodType.methodType(Certificate[].class));
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    @LauncherAPI
    public enum OS {
        MUSTDIE("mustdie"), LINUX("linux"), MACOSX("macosx");
        public final String name;

        OS(String name) {
            this.name = name;
        }

        public static OS byName(String name) {
            if (name.startsWith("Windows")) {
                return MUSTDIE;
            }
            if (name.startsWith("Linux")) {
                return LINUX;
            }
            if (name.startsWith("Mac OS X")) {
                return MACOSX;
            }
            throw new RuntimeException(String.format("This shit is not yet supported: '%s'", name));
        }
    }
}
