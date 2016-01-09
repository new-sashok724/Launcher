package net.sashok724.launcher.server.auth.provider;

import java.io.IOException;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;

public final class NullAuthProvider extends AuthProvider {
	private volatile AuthProvider provider;

	public NullAuthProvider(BlockConfigEntry block) {
		super(block);
	}

	@Override
	public String auth(String login, String password) throws Exception {
		return getProvider().auth(login, password);
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
		return VerifyHelper.verify(provider, a -> a != null, "Backend auth provider wasn't set");
	}
}
