package net.sashok724.launcher.server.auth.handler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;

public final class BinaryFileAuthHandler extends FileAuthHandler {
	public BinaryFileAuthHandler(BlockConfigEntry block) {
		super(block);
	}

	@Override
	protected void readAuthFile() throws IOException {
		try (HInput input = new HInput(IOHelper.newInput(file))) {
			int count = input.readLength(0);
			for (int i = 0; i < count; i++) {
				UUID uuid = input.readUUID();
				Entry entry = new Entry(input);
				addAuth(uuid, entry);
			}
		}
	}

	@Override
	protected void writeAuthFile() throws IOException {
		Set<Map.Entry<UUID, Entry>> entrySet = entrySet();
		try (HOutput output = new HOutput(IOHelper.newOutput(file))) {
			output.writeLength(entrySet.size(), 0);
			for (Map.Entry<UUID, Entry> entry : entrySet) {
				output.writeUUID(entry.getKey());
				entry.getValue().write(output);
			}
		}
	}
}
