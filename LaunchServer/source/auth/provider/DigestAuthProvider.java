package launchserver.auth.provider;

import launcher.LauncherAPI;
import launcher.helper.SecurityHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

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
