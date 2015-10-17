package launcher.request;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class PingRequest extends Request<Void> {
	@LauncherAPI
	public PingRequest() {
	}

	@Override
	public Type getType() {
		return Type.PING;
	}

	@Override
	protected Void requestDo(HInput input, HOutput output) throws IOException {
		int pong = input.readUnsignedByte();
		if (pong != 123) {
			throw new IOException("Illegal ping response: " + pong);
		}
		return null;
	}
}
