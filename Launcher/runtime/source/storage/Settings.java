package launcher.runtime.storage;

import java.io.IOException;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import launcher.Launcher;
import launcher.hasher.HashedDir;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.request.update.LauncherUpdateRequest;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.StreamObject;

public final class Settings extends StreamObject {
	private static final int SETTINGS_MAGIC = 0x724724_E4;

	// Lock
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	// Dialog data
	private String login = null;
	private byte[] password = null;
	private int profileIndex = 0;

	// Offline cache
	private LauncherUpdateRequest.Result launcherUpdateRequest = null;
	private final Map<String, SignedObjectHolder<HashedDir>> offlineHDirs;

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
		launcherUpdateRequest = input.readBoolean() ? new LauncherUpdateRequest.Result(input, publicKey) : null;
		int offlineHDirsCount = input.readLength(0);
		offlineHDirs = new HashMap<>(offlineHDirsCount);
		for (int i = 0; i < offlineHDirsCount; i++) {
			offlineHDirs.put(IOHelper.verifyFileName(input.readString(255)),
				new SignedObjectHolder<>(input, publicKey, HashedDir::new));
		}
	}

	@Override
	public void write(HOutput output) throws IOException {
		lock.readLock().lock();
		try {
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
			output.writeBoolean(launcherUpdateRequest != null);
			if (launcherUpdateRequest != null) {
				launcherUpdateRequest.write(output);
			}
			output.writeLength(offlineHDirs.size(), 0);
			for (Map.Entry<String, SignedObjectHolder<HashedDir>> entry : offlineHDirs.entrySet()) {
				output.writeString(entry.getKey(), 255);
				entry.getValue().write(output);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public SignedObjectHolder<HashedDir> getHDir(String name) {
		lock.readLock().lock();
		try {
			return offlineHDirs.get(name);
		} finally {
			lock.readLock().unlock();
		}
	}

	public LauncherUpdateRequest.Result getLauncherUpdateRequest() {
		lock.readLock().lock();
		try {
			return launcherUpdateRequest;
		} finally {
			lock.readLock().unlock();
		}
	}

	public String getLogin() {
		lock.readLock().lock();
		try {
			return login;
		} finally {
			lock.readLock().unlock();
		}
	}

	public byte[] getPassword() {
		lock.readLock().lock();
		try {
			return password == null ? null : Arrays.copyOf(password, password.length);
		} finally {
			lock.readLock().unlock();
		}
	}

	public int getProfileIndex() {
		lock.readLock().lock();
		try {
			return profileIndex;
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean isPasswordSaved() {
		lock.readLock().lock();
		try {
			return login == null || password != null;
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean rememberHDir(String name, SignedObjectHolder<HashedDir> dir) {
		if (!IOHelper.isValidFileName(name) || dir == null) {
			return false;
		}

		// Apply changes
		lock.writeLock().lock();
		try {
			offlineHDirs.put(name, dir);
		} finally {
			lock.writeLock().unlock();
		}
		return true;
	}

	public void setLauncherUpdateRequest(LauncherUpdateRequest.Result result) {
		lock.writeLock().lock();
		try {
			launcherUpdateRequest = result;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean setLogin(String login) {
		if (login != null && login.length() > 255) {
			return false;
		}

		// Apply changes
		lock.writeLock().lock();
		try {
			this.login = login;
		} finally {
			lock.writeLock().unlock();
		}
		return true;
	}

	public boolean setPassword(byte[] password) {
		if (password != null && password.length > SecurityHelper.CRYPTO_MAX_LENGTH) {
			return false;
		}

		// Apply changes
		lock.writeLock().lock();
		try {
			this.password = password == null ? null : Arrays.copyOf(password, password.length);
		} finally {
			lock.writeLock().unlock();
		}
		return true;
	}

	public boolean setProfileIndex(int profileIndex) {
		if (profileIndex < 0) {
			return false;
		}

		// Apply changes
		lock.writeLock().lock();
		try {
			this.profileIndex = profileIndex;
		} finally {
			lock.writeLock().unlock();
		}
		return true;
	}
}
