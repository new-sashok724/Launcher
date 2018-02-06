package launchserver.auth.provider;

import java.io.IOException;
import java.util.Objects;

import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

public final class NullAuthProvider extends AuthProvider {
    private volatile AuthProvider provider;

    public NullAuthProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws Exception {
        return getProvider().auth(login, password, ip);
    }

    @Override
    public void close() throws IOException {
        AuthProvider provider = this.provider;
        if (provider != null) {
            provider.close();
        }
    }

    @LauncherAPI
    public void setBackend(AuthProvider provider) {
        this.provider = provider;
    }

    private AuthProvider getProvider() {
        return VerifyHelper.verify(provider, Objects::nonNull, "Backend auth provider wasn't set");
    }
}
