package launcher.request;

import java.io.IOException;

import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class PingRequest extends Request<Void> {
	@LauncherAPI public static final byte EXPECTED_BYTE = 0b01010101;

	@LauncherAPI
	public PingRequest(Launcher.Config config) {
		super(config);
	}

	@LauncherAPI
	public PingRequest() {
		this(null);
	}

	@Override
	public Type getType() {
		return Type.PING;
	}

	@Override
	protected Void requestDo(HInput input, HOutput output) throws IOException {
		byte pong = (byte) input.readUnsignedByte();
		if (pong != EXPECTED_BYTE) {
			throw new IOException("Illegal ping response: " + pong);
		}
		return null;
	}
}
