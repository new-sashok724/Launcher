package launcher.request.uuid;

import java.io.IOException;

import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.PlayerProfile;
import launcher.helper.IOHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

public final class BatchProfileByUsernameRequest extends Request<PlayerProfile[]> {
    @LauncherAPI public static final int MAX_BATCH_SIZE = 128;
    private final String[] usernames;

    @LauncherAPI
    public BatchProfileByUsernameRequest(Config config, String... usernames) throws IOException {
        super(config);
        this.usernames = usernames.clone();
        IOHelper.verifyLength(this.usernames.length, MAX_BATCH_SIZE);
        for (String username : this.usernames) {
            VerifyHelper.verifyUsername(username);
        }
    }

    @LauncherAPI
    public BatchProfileByUsernameRequest(String... usernames) throws IOException {
        this(null, usernames);
    }

    @Override
    public Type getType() {
        return Type.BATCH_PROFILE_BY_USERNAME;
    }

    @Override
    protected PlayerProfile[] requestDo(HInput input, HOutput output) throws IOException {
        output.writeLength(usernames.length, MAX_BATCH_SIZE);
        for (String username : usernames) {
            output.writeASCII(username, 16);
        }
        output.flush();

        // Read profiles response
        PlayerProfile[] profiles = new PlayerProfile[usernames.length];
        for (int i = 0; i < profiles.length; i++) {
            profiles[i] = input.readBoolean() ? new PlayerProfile(input) : null;
        }

        // Return result
        return profiles;
    }
}
