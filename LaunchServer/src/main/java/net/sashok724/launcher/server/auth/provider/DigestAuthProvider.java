package net.sashok724.launcher.server.auth.provider;

import net.sashok724.launcher.client.LauncherAPI;
import net.sashok724.launcher.client.helper.SecurityHelper;
import net.sashok724.launcher.client.serialize.config.entry.BlockConfigEntry;
import net.sashok724.launcher.client.serialize.config.entry.StringConfigEntry;
import net.sashok724.launcher.server.auth.AuthException;

public abstract class DigestAuthProvider extends AuthProvider {
	private final SecurityHelper.DigestAlgorithm digest;

	@LauncherAPI
	protected DigestAuthProvider(BlockConfigEntry block) {
		super(block);
		digest = SecurityHelper.DigestAlgorithm.byName(block.getEntryValue("digest", StringConfigEntry.class));
	}

	@LauncherAPI
	protected final void verifyDigest(String validDigest, String password) throws AuthException {
		boolean valid;
		if (digest == SecurityHelper.DigestAlgorithm.PLAIN) {
			valid = password.equals(validDigest);
		} else if (validDigest == null) {
			valid = false;
		} else {
			byte[] actualDigest = SecurityHelper.digest(digest, password);
			valid = SecurityHelper.toHex(actualDigest).equals(validDigest);
		}

		// Verify is valid
		if (!valid) {
			throw new AuthException("error.auth.incorrectCredentials");
		}
	}
}
