package net.sashok724.launcher.client.request.uuid;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.request.Request;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;

public final class ProfileByUUIDRequest extends Request<PlayerProfile> {
	private final UUID uuid;

	@LauncherAPI
	public ProfileByUUIDRequest(Launcher.Config config, UUID uuid) {
		super(config);
		this.uuid = Objects.requireNonNull(uuid, "uuid");
	}

	@LauncherAPI
	public ProfileByUUIDRequest(UUID uuid) {
		this(null, uuid);
	}

	@Override
	public Type getType() {
		return Type.PROFILE_BY_UUID;
	}

	@Override
	protected PlayerProfile requestDo(HInput input, HOutput output) throws IOException {
		output.writeUUID(uuid);
		output.flush();

		// Return profile
		return input.readBoolean() ? new PlayerProfile(input) : null;
	}
}
