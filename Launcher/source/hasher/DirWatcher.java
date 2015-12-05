package launcher.hasher;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;

import com.sun.nio.file.SensitivityWatchEventModifier;
import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;

public final class DirWatcher implements Runnable, AutoCloseable {
	private static final WatchEvent.Modifier[] MODIFIERS = { SensitivityWatchEventModifier.HIGH };
	private static final WatchEvent.Kind<?>[] KINDS = {
		StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE
	};

	// Instance
	private final Path dir;
	private final HashedDir hdir;
	private final FileNameMatcher matcher;
	private final WatchService service;

	@LauncherAPI
	public DirWatcher(Path dir, HashedDir hdir, FileNameMatcher matcher) throws IOException {
		this.dir = Objects.requireNonNull(dir, "dir");
		this.hdir = Objects.requireNonNull(hdir, "hdir");
		this.matcher = matcher;

		// Register dirs recursively
		service = dir.getFileSystem().newWatchService();
		IOHelper.walk(dir, new RegisterFileVisitor(), true);
	}

	@Override
	@LauncherAPI
	public void close() throws IOException {
		service.close();
	}

	@Override
	@LauncherAPI
	public void run() {
		try {
			processLoop();
		} catch (InterruptedException | ClosedWatchServiceException ignored) {
			// Do nothing (closed etc)
		} catch (Throwable exc) {
			handleError(exc);
		}
	}

	private void processKey(WatchKey key) throws IOException {
		Path watchDir = (Path) key.watchable();
		Collection<WatchEvent<?>> events = key.pollEvents();
		for (WatchEvent<?> event : events) {
			WatchEvent.Kind<?> kind = event.kind();
			if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
				throw new IOException("Overflow");
			}

			// Resolve paths and verify is not exclusion
			Path path = watchDir.resolve((Path) event.context());
			Deque<String> stringPath = toPath(dir.relativize(path));
			if (matcher != null && !matcher.shouldVerify(stringPath)) {
				continue; // Exclusion; should not be verified
			}

			// Verify is REALLY modified (not just attributes)
			if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
				HashedEntry entry = hdir.resolve(stringPath);
				if (entry != null && (entry.getType() != HashedEntry.Type.FILE || ((HashedFile) entry).isSame(path))) {
					continue; // Modified attributes, not need to worry :D
				}
			}

			// Forbidden modification!
			throw new SecurityException(String.format("Forbidden modification (%s, %d times): '%s'",
				kind, event.count(), path));
		}
		key.reset();
	}

	private void processLoop() throws IOException, InterruptedException {
		while (!Thread.interrupted()) {
			processKey(service.take());
		}
	}

	private static void handleError(Throwable e) {
		LogHelper.error(e);
		JVMHelper.halt0(0x0BADFEE1);
	}

	private static Deque<String> toPath(Iterable<Path> path) {
		Deque<String> result = new LinkedList<>();
		for (Path pe : path) {
			result.add(pe.toString());
		}
		return result;
	}

	private final class RegisterFileVisitor extends SimpleFileVisitor<Path> {
		private final Deque<String> path = new LinkedList<>();

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			FileVisitResult result = super.postVisitDirectory(dir, exc);
			if (!DirWatcher.this.dir.equals(dir)) {
				path.removeLast();
			}
			return result;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			FileVisitResult result = super.preVisitDirectory(dir, attrs);
			if (DirWatcher.this.dir.equals(dir)) {
				dir.register(service, KINDS, MODIFIERS);
				return result;
			}

			// Maybe it's unnecessary to go deeper
			path.add(IOHelper.getFileName(dir));
			if (matcher != null && !matcher.shouldVerify(path)) {
				return FileVisitResult.SKIP_SUBTREE;
			}

			// Register
			dir.register(service, KINDS, MODIFIERS);
			return result;
		}
	}
}
