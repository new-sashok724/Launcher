package net.sashok724.launcher.client.request.update;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.request.Request;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

public final class UpdateListRequest extends Request<Set<String>> {
	@LauncherAPI
	public UpdateListRequest(Launcher.Config config) {
		super(config);
	}

	@LauncherAPI
	public UpdateListRequest() {
		this(null);
	}

	@Override
	public Type getType() {
		return Type.UPDATE_LIST;
	}

	@Override
	protected Set<String> requestDo(HInput input, HOutput output) throws IOException {
		int count = input.readLength(0);

		// Read all update dirs names
		Set<String> result = new HashSet<>(count);
		for (int i = 0; i < count; i++) {
			result.add(IOHelper.verifyFileName(input.readString(255)));
		}

		// We're done. Make it unmodifiable and return
		return Collections.unmodifiableSet(result);
	}
}
