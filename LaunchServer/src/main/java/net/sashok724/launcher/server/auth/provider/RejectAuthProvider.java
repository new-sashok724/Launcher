package net.sashok724.launcher.server.auth.provider;

import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;
import net.sashok724.launcher.server.auth.AuthException;

public final class RejectAuthProvider extends AuthProvider {
	private final String message;

	public RejectAuthProvider(BlockConfigEntry block) {
		super(block);
		message = VerifyHelper.verify(block.getEntryValue("message", StringConfigEntry.class), VerifyHelper.NOT_EMPTY,
			"Auth error message can't be empty");
	}

	@Override
	public String auth(String login, String password) throws AuthException {
		throw new AuthException(message);
	}

	@Override
	public void close() {
		// Do nothing
	}
}
