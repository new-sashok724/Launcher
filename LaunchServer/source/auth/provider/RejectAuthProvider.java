package launchserver.auth.provider;

import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

public final class RejectAuthProvider extends AuthProvider {
	private final String message;

	public RejectAuthProvider(BlockConfigEntry block) {
		super(block);
		message = block.getEntryValue("message", StringConfigEntry.class);
	}

	@Override
	public String auth(String login, String password) throws AuthException {
		throw new AuthException(message);
	}

	@Override
	public void flush() {
		// Do nothing
	}

	@Override
	public void verify() {
		VerifyHelper.verify(message, VerifyHelper.NOT_EMPTY, "Auth error message can't be empty");
	}
}
