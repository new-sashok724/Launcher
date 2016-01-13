package launcher.transport.config.entry;

import java.io.IOException;

import launcher.LauncherAPI;
import launcher.transport.HInput;
import launcher.transport.HOutput;

public final class BooleanConfigEntry extends ConfigEntry<Boolean> {
	@LauncherAPI
	public BooleanConfigEntry(boolean value, boolean ro, int cc) {
		super(value, ro, cc);
	}

	@LauncherAPI
	public BooleanConfigEntry(HInput input, boolean ro) throws IOException {
		this(input.readBoolean(), ro, 0);
	}

	@Override
	public Type getType() {
		return Type.BOOLEAN;
	}

	@Override
	public void write(HOutput output) throws IOException {
		output.writeBoolean(getValue());
	}
}
