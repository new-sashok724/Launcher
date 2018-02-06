package launchserver.auth.provider;

import launcher.helper.SecurityHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

public final class AcceptAuthProvider extends AuthProvider {
    public AcceptAuthProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) {
        return new AuthProviderResult(login, SecurityHelper.randomStringToken()); // Same as login
    }

    @Override
    public void close() {
        // Do nothing
    }
}
