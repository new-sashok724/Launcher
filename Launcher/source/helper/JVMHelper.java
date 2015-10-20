package launcher.helper;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Locale;

import com.sun.management.OperatingSystemMXBean;
import launcher.LauncherAPI;
import sun.misc.URLClassPath;

public final class JVMHelper {
	// System properties
	@LauncherAPI public static final OS OS_TYPE = OS.byName(System.getProperty("os.name"));
	@LauncherAPI public static final int OS_BITS = getCorrectOSArch();
	@LauncherAPI public static final int JVM_BITS = Integer.parseInt(System.getProperty("sun.arch.data.model"));
	@LauncherAPI public static final int RAM = getRAMAmount();

	// Public static fields
	@LauncherAPI public static final Runtime RUNTIME = Runtime.getRuntime();
	@LauncherAPI public static final URLClassLoader LOADER = (URLClassLoader) ClassLoader.getSystemClassLoader();
	@LauncherAPI public static final URLClassPath UCP = getURLClassPath();

	// Useful internal fields and constants
	private static final String JAVA_LIBRARY_PATH = "java.library.path";
	private static final Field USR_PATHS_FIELD = getUsrPathsField();
	private static final Field SYS_PATHS_FIELD = getSysPathsField();

	private JVMHelper() {
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
			USR_PATHS_FIELD.set(null, null);
			SYS_PATHS_FIELD.set(null, null);
		} catch (IllegalAccessException e) {
			throw new InternalError(e);
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
	public static void halt0(int status) {
		try {
			getMethod(Class.forName("java.lang.Shutdown"), "halt0", int.class).invoke(null, status);
		} catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new InternalError(e);
		}
	}

	@LauncherAPI
	public static boolean isJVMMatchesSystemArch() {
		return JVM_BITS == OS_BITS;
	}

	@LauncherAPI
	public static void verifySystemProperties(Class<?> mainClass) {
		Locale.setDefault(Locale.US);

		// Verify class loader
		LogHelper.debug("Verifying class loader");
		if (!mainClass.getClassLoader().equals(LOADER)) {
			throw new SecurityException("ClassLoader should be system");
		}

		// Verify system and java architecture
		LogHelper.debug("Verifying JVM architecture");
		if (!isJVMMatchesSystemArch()) {
			LogHelper.warning("Java and OS architecture mismatch");
			LogHelper.warning("It's recommended to download %d-bit JRE", OS_BITS);
		}

		// Disable SNI extensions (fixes some sites SSL unrecognized_name)
		LogHelper.debug("Disabling SNI extensions (SSL fix)");
		System.setProperty("jsse.enableSNIExtension", Boolean.toString(false));
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

	private static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
		Field field = clazz.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Method getMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
		Method method = clazz.getDeclaredMethod(name, params);
		method.setAccessible(true);
		return method;
	}

	private static int getRAMAmount() {
		int physicalRam = (int) (((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() >> 20);
		return Math.min(physicalRam, OS_BITS == 32 ? 1536 : 4096); // Limit 32-bit OS to 1536 MiB, and 64-bit OS to 4096 MiB (because it's enough)
	}

	private static Field getSysPathsField() {
		try {
			return getField(ClassLoader.class, "sys_paths");
		} catch (NoSuchFieldException e) {
			throw new InternalError(e);
		}
	}

	private static URLClassPath getURLClassPath() {
		try {
			return (URLClassPath) getField(URLClassLoader.class, "ucp").get(LOADER);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new InternalError(e);
		}
	}

	private static Field getUsrPathsField() {
		try {
			return getField(ClassLoader.class, "usr_paths");
		} catch (NoSuchFieldException e) {
			throw new InternalError(e);
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
