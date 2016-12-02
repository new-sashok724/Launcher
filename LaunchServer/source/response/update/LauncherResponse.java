package launchserver.response.update;

import java.io.IOException;
import java.util.Collection;

import launcher.client.ClientProfile;
import launcher.helper.SecurityHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedBytesHolder;
import launcher.serialize.signed.SignedObjectHolder;
import launchserver.LaunchServer;
import launchserver.response.Response;

public final class LauncherResponse extends Response {
    public LauncherResponse(LaunchServer server, long id, HInput input, HOutput output) {
        super(server, id, input, output);
    }

    @Override
    public void reply() throws IOException {
        // Resolve launcher binary
        SignedBytesHolder bytes = (input.readBoolean() ? server.launcherEXEBinary : server.launcherBinary).getBytes();
        if (bytes == null) {
            requestError("Missing launcher binary");
            return;
        }
        writeNoError(output);

        // Update launcher binary
        output.writeByteArray(bytes.getSign(), -SecurityHelper.RSA_KEY_LENGTH);
        output.flush();
        if (input.readBoolean()) {
            output.writeByteArray(bytes.getBytes(), 0);
            return; // Launcher will be restarted
        }

        // Write clients profiles list
        Collection<SignedObjectHolder<ClientProfile>> profiles = server.getProfiles();
        output.writeLength(profiles.size(), 0);
        for (SignedObjectHolder<ClientProfile> profile : profiles) {
            profile.write(output);
        }
    }
}
