package launchserver.auth.handler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import launcher.helper.IOHelper;
import launcher.transport.HInput;
import launcher.transport.HOutput;
import launcher.transport.config.entry.BlockConfigEntry;

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
