package launcher.hasher;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.transport.HInput;
import launcher.transport.stream.EnumSerializer;
import launcher.transport.stream.StreamObject;

public abstract class HashedEntry extends StreamObject {
	@LauncherAPI public boolean flag; // For external usage

	@LauncherAPI
	public abstract Type getType();

	@LauncherAPI
	public abstract long size();

	@LauncherAPI
	public enum Type implements EnumSerializer.Serializable {
		DIR(1), FILE(2);
		private static final EnumSerializer<Type> SERIALIZER = new EnumSerializer<>(Type.class);
		private final int n;

		Type(int n) {
			this.n = n;
		}

		@Override
		public int getNumber() {
			return n;
		}

		public static Type read(HInput input) throws IOException {
			return SERIALIZER.read(input);
		}
	}
}
