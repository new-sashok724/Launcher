package launcher.serialize.config.entry;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class IntegerConfigEntry extends ConfigEntry<Integer> {
	@LauncherAPI
	public IntegerConfigEntry(int value, boolean ro, int cc) {
		super(value, ro, cc);
	}

	@LauncherAPI
	public IntegerConfigEntry(HInput input, boolean ro) throws IOException {
		this(input.readVarInt(), ro, 0);
	}

	@Override
	public Type getType() {
		return Type.INTEGER;
	}

	@Override
	public void write(HOutput output) throws IOException {
		output.writeVarInt(getValue());
	}
}