package launcher.request.uuid;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import launcher.Launcher;
import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class ProfileByUUIDRequest extends Request<PlayerProfile> {
    private final UUID uuid;

    @LauncherAPI
    public ProfileByUUIDRequest(Config config, UUID uuid) {
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
