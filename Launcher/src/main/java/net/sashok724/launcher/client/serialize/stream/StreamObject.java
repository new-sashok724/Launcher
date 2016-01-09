package net.sashok724.launcher.client.serialize.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

public abstract class StreamObject {
	/* public StreamObject(HInput input) */

	@LauncherAPI
	public abstract void write(HOutput output) throws IOException;

	@LauncherAPI
	public final byte[] write() throws IOException {
		try (ByteArrayOutputStream array = IOHelper.newByteArrayOutput()) {
			try (HOutput output = new HOutput(array)) {
				write(output);
			}
			return array.toByteArray();
		}
	}

	@FunctionalInterface
	public interface Adapter<O extends StreamObject> {
		@LauncherAPI
		O convert(HInput input) throws IOException;
	}
}
