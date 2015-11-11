package launcher.request.update;

import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.client.ClientLauncher;
import launcher.client.ClientProfile;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;

public final class LauncherRequest extends Request<LauncherRequest.Result> {
	@LauncherAPI public static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
	@LauncherAPI public static final boolean EXE_BINARY = IOHelper.hasExtension(BINARY_PATH, "exe");

	@LauncherAPI
	public LauncherRequest(Launcher.Config config) {
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
			SecurityHelper.verifySign(binary, sign, publicKey);
			IOHelper.write(BINARY_PATH, binary);

			// Start new launcher instance (java -jar works for Launch4J's EXE too)
			ProcessBuilder builder = new ProcessBuilder(IOHelper.resolveJavaBin(null).toString(),
				ClientLauncher.jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())),
				"-jar", BINARY_PATH.toString());
			builder.inheritIO();
			builder.start();

			// Exit current instance
			JVMHelper.RUNTIME.exit(255);
			throw new AssertionError("Why Launcher wasn't restarted?!");
		}

		// Read clients profiles list
		int count = input.readLength(0);
		List<SignedObjectHolder<ClientProfile>> profiles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			profiles.add(new SignedObjectHolder<>(input, publicKey, ClientProfile.RO_ADAPTER));
		}

		// Return request result
		return new Result(sign, profiles);
	}

	public static final class Result {
		@LauncherAPI public final List<SignedObjectHolder<ClientProfile>> profiles;
		private final byte[] sign;

		private Result(byte[] sign, List<SignedObjectHolder<ClientProfile>> profiles) {
			this.sign = Arrays.copyOf(sign, sign.length);
			this.profiles = Collections.unmodifiableList(profiles);
		}

		@LauncherAPI
		public byte[] getSign() {
			return Arrays.copyOf(sign, sign.length);
		}
	}
}
