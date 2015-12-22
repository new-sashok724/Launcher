package launcher.runtime.storage;

import java.io.IOException;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import launcher.Launcher;
import launcher.hasher.HashedDir;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.request.update.LauncherRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.StreamObject;

public final class Settings extends StreamObject {
	private static final int SETTINGS_MAGIC = 0x724724_E4;

	// Dialog data
	private String login = null;
	private byte[] password = null;
	private int profileIndex = 0;

	// Offline cache
	public LauncherRequest.Result launcherResult = null;
	private Map<String, SignedObjectHolder<HashedDir>> offlineHDirs;

	public Settings() {
		offlineHDirs = new HashMap<>(4);
	}

	public Settings(HInput input) throws IOException, SignatureException {
		int magic = input.readInt();
		if (magic != SETTINGS_MAGIC) {
			throw new IOException("Settings magic mismatch");
		}

		// Read dialog data
		login = input.readBoolean() ? input.readString(255) : null;
		password = input.readBoolean() ? input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH) : null;
		profileIndex = input.readLength(0);

		// Read offline cache
		RSAPublicKey publicKey = Launcher.Config.getDefault().publicKey;
		launcherResult = input.readBoolean() ? new LauncherRequest.Result(input, publicKey) : null;
		int offlineHDirsCount = input.readLength(0);
		offlineHDirs = new HashMap<>(offlineHDirsCount);
		for (int i = 0; i < offlineHDirsCount; i++) {
			offlineHDirs.put(IOHelper.verifyFileName(input.readString(255)),
				new SignedObjectHolder<>(input, publicKey, HashedDir::new));
		}
	}

	@Override
	public void write(HOutput output) throws IOException {
		output.writeInt(SETTINGS_MAGIC);

		// Write dialog data
		output.writeBoolean(login != null);
		if (login != null) {
			output.writeString(login, 255);
		}
		output.writeBoolean(password != null);
		if (password != null) {
			output.writeByteArray(password, SecurityHelper.CRYPTO_MAX_LENGTH);
		}
		output.writeLength(profileIndex, 0);

		// Write offline cache
		output.writeBoolean(launcherResult != null);
		if (launcherResult != null) {
			launcherResult.write(output);
		}
		output.writeLength(offlineHDirs.size(), 0);
		for (Map.Entry<String, SignedObjectHolder<HashedDir>> entry : offlineHDirs.entrySet()) {
			output.writeString(entry.getKey(), 255);
			entry.getValue().write(output);
		}
	}

	public SignedObjectHolder<HashedDir> getHDir(String name) {
		return offlineHDirs.get(name);
	}

	public String getLogin() {
		return login;
	}

	public byte[] getPassword() {
		return password == null ? null : Arrays.copyOf(password, password.length);
	}

	public int getProfileIndex() {
		return profileIndex;
	}

	public boolean isPasswordSaved() {
		return login == null || password != null;
	}

	public boolean rememberHDir(String name, SignedObjectHolder<HashedDir> dir) {
		if (!IOHelper.isValidFileName(name) || dir == null) {
			return false;
		}
		offlineHDirs.put(name, dir);
		return true;
	}

	public boolean setLogin(String login) {
		if (login.length() > 255) {
			return false;
		}
		this.login = login;
		return true;
	}

	public boolean setPassword(byte[] password) {
		if (password.length > SecurityHelper.CRYPTO_MAX_LENGTH) {
			return false;
		}
		this.password = Arrays.copyOf(password, password.length);
		return true;
	}

	public boolean setProfileIndex(int profileIndex) {
		if (profileIndex < 0) {
			return false;
		}
		this.profileIndex = profileIndex;
		return true;
	}
}
