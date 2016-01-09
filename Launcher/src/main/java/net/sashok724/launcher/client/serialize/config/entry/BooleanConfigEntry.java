package net.sashok724.launcher.client.serialize.config.entry;

import java.io.IOException;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

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