package launcher.request.update;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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
import launcher.serialize.stream.StreamObject;

public final class LauncherUpdateRequest extends Request<LauncherUpdateRequest.Result> {
	@LauncherAPI public static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
	@LauncherAPI public static final boolean EXE_BINARY = IOHelper.hasExtension(BINARY_PATH, "exe");

	@LauncherAPI
	public LauncherUpdateRequest(Launcher.Config config) {
		super(config);
	}

	@LauncherAPI
	public LauncherUpdateRequest() {
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

		// Read request result
		Result result = new Result(input, config.publicKey);
		boolean shouldUpdate = !SecurityHelper.isValidSign(BINARY_PATH, result.sign, config.publicKey);

		// Update launcher if need
		output.writeBoolean(shouldUpdate);
		output.flush();
		if (shouldUpdate) {
			byte[] binary = input.readByteArray(0);
			SecurityHelper.verifySign(binary, result.sign, config.publicKey);

			// Prepare process builder to start new instance (java -jar works for Launch4J's EXE too)
			ProcessBuilder builder = new ProcessBuilder(IOHelper.resolveJavaBin(null).toString(),
				ClientLauncher.jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())),
				"-jar", BINARY_PATH.toString());
			builder.inheritIO();

			// Rewrite and start new instance
			IOHelper.write(BINARY_PATH, binary);
			builder.start();

			// Kill current instance
			JVMHelper.RUNTIME.exit(255);
			throw new AssertionError("Why Launcher wasn't restarted?!");
		}

		// Return request result
		return result;
	}

	public static final class Result extends StreamObject {
		private final byte[] sign;
		@LauncherAPI public final Collection<SignedObjectHolder<ClientProfile>> profiles;

		@LauncherAPI
		public Result(HInput input, RSAPublicKey publicKey) throws IOException, SignatureException {
			sign = input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH);

			// Read profiles list
			int count = input.readLength(0);
			profiles = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				profiles.add(new SignedObjectHolder<>(input, publicKey, ClientProfile.RO_ADAPTER));
			}
		}

		@LauncherAPI
		public Result(byte[] sign, Collection<SignedObjectHolder<ClientProfile>> profiles) {
			this.sign = Arrays.copyOf(sign, sign.length);
			this.profiles = Collections.unmodifiableCollection(profiles);
		}

		@Override
		public void write(HOutput output) throws IOException {
			output.writeByteArray(sign, -SecurityHelper.RSA_KEY_LENGTH);

			// Write profiles list
			output.writeLength(profiles.size(), 0);
			for (SignedObjectHolder<ClientProfile> profile : profiles) {
				profile.write(output);
			}
		}

		@LauncherAPI
		public byte[] getSign() {
			return Arrays.copyOf(sign, sign.length);
		}
	}
}
