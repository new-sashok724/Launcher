package launcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;

public final class Launcher {
	@LauncherAPI public static final String VERSION = "15.1";
	@LauncherAPI public static final String BUILD = readBuildNumber();
	@LauncherAPI public static final int PROTOCOL_MAGIC = 0x724724_16;

	private Launcher() {
	}

	public static void main(String... args) throws Throwable {
		JVMHelper.verifySystemProperties(Launcher.class);
		SecurityHelper.verifyCertificates(Launcher.class);
		LogHelper.printVersion("Launcher");

		// Launch runtime
		LogHelper.debug("Launching runtime");
		Class<?> runtimeClass = Class.forName("launcher.runtime.Mainclass");
		try {
			runtimeClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) args);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		}
	}

	private static String readBuildNumber() {
		try {
			return IOHelper.request(IOHelper.getResourceURL("buildnumber"));
		} catch (IOException ignored) {
			return "dev"; // Maybe dev env?
		}
	}

	public static final class Config extends StreamObject {
		private static final AtomicReference<Config> DEFAULT = new AtomicReference<>();
		private static final String ADDRESS_OVERRIDE = System.getProperty("launcher.addressOverride", null);

		// Instance
		@LauncherAPI public final InetSocketAddress address;
		@LauncherAPI public final RSAPublicKey publicKey;
		@LauncherAPI public final byte[] runtimeSign;

		@LauncherAPI
		@SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
		public Config(String address, int port, RSAPublicKey publicKey, byte[] runtimeSign) {
			this.address = InetSocketAddress.createUnresolved(address, port);
			this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
			this.runtimeSign = Arrays.copyOf(runtimeSign, runtimeSign.length);
		}

		@LauncherAPI
		public Config(HInput input) throws IOException, InvalidKeySpecException {
			String localAddress = input.readASCII(255);
			address = InetSocketAddress.createUnresolved(
				ADDRESS_OVERRIDE == null ? localAddress : ADDRESS_OVERRIDE, input.readLength(65535));
			publicKey = SecurityHelper.toPublicRSAKey(input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH));
			runtimeSign = input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH);

			// Print warning if address override has been enabled
			if (ADDRESS_OVERRIDE != null) {
				LogHelper.warning("Address override enabled: '%s'", ADDRESS_OVERRIDE);
			}
		}

		@Override
		public void write(HOutput output) throws IOException {
			output.writeASCII(address.getHostString(), 255);
			output.writeLength(address.getPort(), 65535);
			output.writeByteArray(publicKey.getEncoded(), SecurityHelper.CRYPTO_MAX_LENGTH);
			output.writeByteArray(runtimeSign, -SecurityHelper.RSA_KEY_LENGTH);
		}

		@LauncherAPI
		public static Config getDefault() {
			Config config = DEFAULT.get();
			if (config == null) {
				try (HInput input = new HInput(IOHelper.newInput(IOHelper.getResourceURL("config.bin")))) {
					config = new Config(input);
				} catch (IOException | InvalidKeySpecException e) {
					throw new SecurityException(e);
				}
				DEFAULT.set(config);
			}
			return config;
		}
	}
}
