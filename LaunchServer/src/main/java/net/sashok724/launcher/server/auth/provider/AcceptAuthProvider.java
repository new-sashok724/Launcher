package net.sashok724.launcher.server.auth.provider;

import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;

public final class AcceptAuthProvider extends AuthProvider {
	public AcceptAuthProvider(BlockConfigEntry block) {
		super(block);
	}

	@Override
	public String auth(String login, String password) {
		return login; // Same as login
	}

	@Override
	public void close() {
		// Do nothing
	}
}
