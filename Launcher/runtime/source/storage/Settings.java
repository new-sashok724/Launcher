package launcher.runtime.storage;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import launcher.Launcher;
import launcher.hasher.HashedDir;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.update.LauncherUpdateRequest;
import launcher.runtime.Mainclass;
import launcher.transport.HInput;
import launcher.transport.HOutput;
import launcher.transport.signed.SignedObjectHolder;
import launcher.transport.stream.StreamObject;

public final class Settings extends StreamObject {
	private static final int SETTINGS_MAGIC = 0x724724_E4;

	// Lock
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	// Dialog data
	private String login = null;
	private byte[] password = null;
	private int profileIndex = 0;

	// Client launch data
	private Path updatesDir;
	private boolean autoEnter;
	private boolean fullScreen;
	private int heap;

	// Offline cache
	private LauncherUpdateRequest.Result launcherUpdateRequest = null;
	private final Map<String, SignedObjectHolder<HashedDir>> offlineHDirs;

	public Settings() {
		boolean debug = Boolean.parseBoolean(Mainclass.CONFIG.getProperty("settings.default.debug"));
		if (!LogHelper.isDebugEnabled() && debug) {
			LogHelper.setDebugEnabled(true);
		}

		// Client launch data
		updatesDir = Mainclass.DIR.resolve("updates");
		autoEnter = Boolean.parseBoolean(Mainclass.CONFIG.getProperty("settings.default.autoEnter"));
		fullScreen = Boolean.parseBoolean(Mainclass.CONFIG.getProperty("settings.default.fullScreen"));
		heap = Math.min(Integer.parseInt(Mainclass.CONFIG.getProperty(
			"settings.default.heap", "1024")), JVMHelper.RAM);

		// Offline cache
		offlineHDirs = new HashMap<>(4);
	}

	public Settings(HInput input) throws IOException, SignatureException {
		int magic = input.readInt();
		if (magic != SETTINGS_MAGIC) {
			throw new IOException("Settings magic mismatch");
		}

		// Read debug state
		boolean debug = input.readBoolean();
		if (!LogHelper.isDebugEnabled() && debug) {
			LogHelper.setDebugEnabled(true);
		}

		// Read dialog data
		login = input.readBoolean() ? input.readString(255) : null;
		password = input.readBoolean() ? input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH) : null;
		profileIndex = input.readLength(0);

		// Read cient launch data
		updatesDir = IOHelper.toPath(input.readString(0));
		autoEnter = input.readBoolean();
		fullScreen = input.readBoolean();
		setHeap(input.readLength(0));

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

			// Write debug state
			output.writeBoolean(LogHelper.isDebugEnabled());

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

			// Write client launch data
			output.writeString(IOHelper.toString(updatesDir), 0);
			output.writeBoolean(autoEnter);
			output.writeBoolean(fullScreen);
			output.writeLength(heap, 0);

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

	public byte[] encryptPassword(String password) {
		byte[] passwordEncrypted;
		try {
			RSAPublicKey publicKey = Launcher.Config.getDefault().publicKey;
			passwordEncrypted = SecurityHelper.newRSAEncryptCipher(publicKey).
				doFinal(password.getBytes(IOHelper.UNICODE_CHARSET));
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			throw new AssertionError(e);
		}
		return passwordEncrypted;
	}

	public SignedObjectHolder<HashedDir> getHDir(String name) {
		lock.readLock().lock();
		try {
			return offlineHDirs.get(name);
		} finally {
			lock.readLock().unlock();
		}
	}

	public int getHeap() {
		lock.readLock().lock();
		try {
			return heap;
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

	public int getProfileIndex(int size) {
		lock.readLock().lock();
		try {
			return Math.min(profileIndex, size - 1);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Path getUpdatesDir() {
		lock.readLock().lock();
		try {
			return updatesDir;
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean isAutoEnter() {
		lock.readLock().lock();
		try {
			return autoEnter;
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean isFullScreen() {
		lock.readLock().lock();
		try {
			return fullScreen;
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

	public void setAutoEnter(boolean autoEnter) {
		lock.writeLock().lock();
		try {
			this.autoEnter = autoEnter;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setFullScreen(boolean fullScreen) {
		lock.writeLock().lock();
		try {
			this.fullScreen = fullScreen;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean setHeap(int heap) {
		if (heap <= 0 || heap > JVMHelper.RAM || JVMHelper.RAM % 256 != 0) {
			return false;
		}

		// Apply changes
		lock.writeLock().lock();
		try {
			this.heap = heap;
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

	public boolean setUpdatesDir(Path updatesDir) {
		if (updatesDir == null || !IOHelper.isDir(updatesDir)) {
			return false;
		}

		// Apply changes
		lock.readLock().lock();
		try {
			this.updatesDir = updatesDir;
		} finally {
			lock.readLock().unlock();
		}
		return true;
	}
}
