package launchserver.auth.provider;

import java.io.IOException;
import java.util.Objects;

import launcher.LauncherAPI;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

public class DelegateAuthProvider extends AuthProvider {
    private volatile AuthProvider delegate;

    public DelegateAuthProvider(BlockConfigEntry block) {
        super(block);
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws Throwable {
        return getDelegate().auth(login, password, ip);
    }

    @Override
    public void close() throws IOException {
        AuthProvider delegate = this.delegate;
        if (delegate != null) {
            delegate.close();
        }
    }

    @LauncherAPI
    public void setDelegate(AuthProvider delegate) {
        this.delegate = delegate;
    }

    private AuthProvider getDelegate() {
        return VerifyHelper.verify(delegate, Objects::nonNull, "Delegate auth provider wasn't set");
    }
}
