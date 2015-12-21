package launcher.runtime.storage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import launcher.hasher.HashedDir;
import launcher.request.update.LauncherRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.StreamObject;

public final class Settings extends StreamObject {
	private static final int SETTINGS_MAGIC = 0x724724_E4;

	// Dialog
	private String login;
	private byte[] password;
	private int profileIndex;

	// Offline cache
	private LauncherRequest.Result lastResult = null;
	private Map<String, SignedObjectHolder<HashedDir>> lastHDirs = new HashMap<>(16);

	public Settings(HInput input) throws IOException {
		int magic = input.readInt();
		if (magic != SETTINGS_MAGIC) {
			throw new IOException("");
		}
	}

	@Override
	public void write(HOutput output) throws IOException {

	}
}
