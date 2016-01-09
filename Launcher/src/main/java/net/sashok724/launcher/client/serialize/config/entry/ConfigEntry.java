package net.sashok724.launcher.client.serialize.config.entry;

import java.io.IOException;
import java.util.Objects;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.stream.EnumSerializer;
import net.sashok724.launcher.client.serialize.stream.StreamObject;

public abstract class ConfigEntry<V> extends StreamObject {
	@LauncherAPI public final boolean ro;
	private final String[] comments;
	private V value;

	protected ConfigEntry(V value, boolean ro, int cc) {
		this.ro = ro;
		comments = new String[cc];
		uncheckedSetValue(value);
	}

	@LauncherAPI
	public final String getComment(int i) {
		if (i < 0) {
			i += comments.length;
		}
		return i >= comments.length ? null : comments[i];
	}

	@LauncherAPI
	public abstract Type getType();

	@LauncherAPI
	@SuppressWarnings("DesignForExtension")
	public V getValue() {
		return value;
	}

	@LauncherAPI
	public final void setComment(int i, String comment) {
		comments[i] = comment;
	}

	@LauncherAPI
	public final void setValue(V value) {
		ensureWritable();
		uncheckedSetValue(value);
	}

	protected final void ensureWritable() {
		if (ro) {
			throw new UnsupportedOperationException("Read-only");
		}
	}

	@SuppressWarnings("DesignForExtension")
	protected void uncheckedSetValue(V value) {
		this.value = Objects.requireNonNull(value, "value");
	}

	protected static ConfigEntry<?> readEntry(HInput input, boolean ro) throws IOException {
		Type type = Type.read(input);
		switch (type) {
			case BOOLEAN:
				return new BooleanConfigEntry(input, ro);
			case INTEGER:
				return new IntegerConfigEntry(input, ro);
			case STRING:
				return new StringConfigEntry(input, ro);
			case LIST:
				return new ListConfigEntry(input, ro);
			case BLOCK:
				return new BlockConfigEntry(input, ro);
			default:
				throw new AssertionError("Unsupported config entry type: " + type.name());
		}
	}

	protected static void writeEntry(ConfigEntry<?> entry, HOutput output) throws IOException {
		EnumSerializer.write(output, entry.getType());
		entry.write(output);
	}

	@LauncherAPI
	public enum Type implements EnumSerializer.Itf {
		BLOCK(1), BOOLEAN(2), INTEGER(3), STRING(4), LIST(5);
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
