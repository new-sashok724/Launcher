package launchserver.auth.provider;

import launcher.LauncherAPI;
import launcher.helper.SecurityHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

public abstract class DigestAuthProvider extends AuthProvider {
	private final String digest;

	@LauncherAPI
	protected DigestAuthProvider(BlockConfigEntry block) {
		super(block);
		digest = block.getEntryValue("digest", StringConfigEntry.class);
	}

	@Override
	@SuppressWarnings("DesignForExtension")
	public void verify() {
		getDigest();
	}

	@LauncherAPI
	public final SecurityHelper.DigestAlgorithm getDigest() {
		return SecurityHelper.DigestAlgorithm.byName(digest);
	}

	@LauncherAPI
	protected final void verifyDigest(String validDigest, String password) throws AuthException {
		boolean valid;
		SecurityHelper.DigestAlgorithm algorithm = getDigest();
		if (algorithm == SecurityHelper.DigestAlgorithm.PLAIN) {
			valid = password.equals(validDigest);
		} else if (validDigest == null) {
			valid = false;
		} else {
			byte[] actualDigest = SecurityHelper.digest(getDigest(), password);
			valid = SecurityHelper.toHex(actualDigest).equals(validDigest);
		}

		// Verify is valid
		if (!valid) {
			authError("Incorrect username or password");
		}
	}
}
