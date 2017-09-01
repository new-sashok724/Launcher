package launchserver.auth.provider;

import launcher.serialize.config.entry.BlockConfigEntry;

public final class AcceptAuthProvider extends AuthProvider {
    public AcceptAuthProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public String auth(String login, String password, String ip) {
        return login; // Same as login
    }

    @Override
    public void close() {
        // Do nothing
    }
}
