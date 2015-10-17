package launcher.request;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.EnumSerializer;

public abstract class Request<R> {
	private final AtomicBoolean started = new AtomicBoolean(false);

	@LauncherAPI
	public abstract Type getType();

	@LauncherAPI
	@SuppressWarnings("DesignForExtension")
	public R request() throws Exception {
		if (!started.compareAndSet(false, true)) {
			throw new IllegalStateException("Request already started");
		}

		// Make request to LaunchServer
		try (Socket socket = IOHelper.newSocket()) {
			socket.connect(IOHelper.resolve(Launcher.getConfig().address));
			try (InputStream is = socket.getInputStream(); OutputStream os = socket.getOutputStream(); HInput input = new HInput(is); HOutput output = new HOutput(os)) {
				writeHandshake(input, output);

				// Start request
				EnumSerializer.write(output, getType());
				output.flush();
				return requestDo(input, output);
			}
		}
	}

	@LauncherAPI
	protected final void readError(HInput input) throws IOException {
		String error = input.readString(0);
		if (!error.isEmpty()) {
			throw new RequestException(error);
		}
	}

	@LauncherAPI
	protected abstract R requestDo(HInput input, HOutput output) throws Exception;

	private static void writeHandshake(HInput input, HOutput output) throws IOException {
		output.writeInt(Launcher.PROTOCOL_MAGIC);

		// Write license & key info
		Launcher.Config config = Launcher.getConfig();
		output.writeBigInteger(config.publicKey.getModulus(), SecurityHelper.RSA_KEY_LENGTH + 1);
		output.flush();

		// Verify is accepted
		if (!input.readBoolean()) {
			throw new RequestException("Serverside not accepted this connection");
		}
	}

	@LauncherAPI
	public enum Type implements EnumSerializer.Itf {
		PING(0), // Ping request
		LAUNCHER(1), UPDATE(2), // Update requests
		AUTH(3), JOIN_SERVER(4), CHECK_SERVER(5), // Auth requests
		PROFILE_BY_USERNAME(6), PROFILE_BY_UUID(7), BATCH_PROFILE_BY_USERNAME(8), // Profile requests
		CUSTOM(255); // Custom requests
		private static final EnumSerializer<Type> SERIALIZER = new EnumSerializer<>(Type.class);
		private final int n;

		Type(int n) {
			this.n = n;
		}

		@Override
		public int getNumber() {
			return n;
		}

		@LauncherAPI
		public static Type read(HInput input) throws IOException {
			return SERIALIZER.read(input);
		}
	}
}
