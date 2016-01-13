package launchserver.auth.provider;

import launcher.helper.VerifyHelper;
import launcher.transport.config.entry.BlockConfigEntry;
import launcher.transport.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

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
