package net.sashok724.launcher.client.serialize.config;

import java.io.IOException;
import java.util.Objects;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.stream.StreamObject;

public abstract class ConfigObject extends StreamObject {
	@LauncherAPI public final BlockConfigEntry block;

	@LauncherAPI
	protected ConfigObject(BlockConfigEntry block) {
		this.block = Objects.requireNonNull(block, "block");
	}

	@Override
	public final void write(HOutput output) throws IOException {
		block.write(output);
	}

	@FunctionalInterface
	public interface Adapter<O extends ConfigObject> {
		@LauncherAPI
		O convert(BlockConfigEntry entry);
	}
}
