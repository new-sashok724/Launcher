package launcher.serialize.config;

import java.io.IOException;
import java.util.Objects;

import launcher.LauncherAPI;
import launcher.serialize.HOutput;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.stream.StreamObject;

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
