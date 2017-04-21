package launcher.request.update;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.zip.InflaterInputStream;

import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.hasher.HashedDir.Diff;
import launcher.hasher.HashedEntry;
import launcher.hasher.HashedFile;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.SecurityHelper.DigestAlgorithm;
import launcher.request.Request;
import launcher.request.update.UpdateRequest.State.Callback;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launcher.serialize.stream.EnumSerializer;
import launcher.serialize.stream.EnumSerializer.Itf;
import launcher.serialize.stream.StreamObject;

public final class UpdateRequest extends Request<SignedObjectHolder<HashedDir>> {
    @LauncherAPI public static final int MAX_QUEUE_SIZE = 128;

    // Instance
    private final String dirName;
    private final Path dir;
    private final FileNameMatcher matcher;
    private final boolean digest;
    private volatile Callback stateCallback;

    // State
    private boolean compress;
    private HashedDir localDir;
    private long totalDownloaded;
    private long totalSize;
    private Instant startTime;

    @LauncherAPI
    public UpdateRequest(Config config, String dirName, Path dir, FileNameMatcher matcher, boolean digest) {
        super(config);
        this.dirName = IOHelper.verifyFileName(dirName);
        this.dir = Objects.requireNonNull(dir, "dir");
        this.matcher = matcher;
        this.digest = digest;
    }

    @LauncherAPI
    public UpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest) {
        this(null, dirName, dir, matcher, digest);
    }

    @Override
    public Type getType() {
        return Type.UPDATE;
    }

    @Override
    public SignedObjectHolder<HashedDir> request() throws Exception {
        Files.createDirectories(dir);
        localDir = new HashedDir(dir, matcher, false, digest);

        // Start request
        return super.request();
    }

    @Override
    protected SignedObjectHolder<HashedDir> requestDo(HInput input, HOutput output) throws IOException, SignatureException {
        compress = input.readBoolean();

        // Write update dir name
        output.writeString(dirName, 255);
        output.flush();
        readError(input);

        // Get diff between local and remote dir
        SignedObjectHolder<HashedDir> remoteHDirHolder = new SignedObjectHolder<>(input, config.publicKey, HashedDir::new);
        Diff diff = remoteHDirHolder.object.diff(localDir, matcher);
        totalSize = diff.mismatch.size();

        // Build actions queue
        Queue<Action> queue = new LinkedList<>();
        fillActionsQueue(queue, diff.mismatch);
        queue.add(Action.FINISH);

        // Download missing first
        // (otherwise it will cause mustdie indexing bug)
        startTime = Instant.now();
        Path currentDir = dir;
        Action[] actionsSlice = new Action[MAX_QUEUE_SIZE];
        while (!queue.isEmpty()) {
            int length = Math.min(queue.size(), MAX_QUEUE_SIZE);

            // Write actions slice
            output.writeLength(length, MAX_QUEUE_SIZE);
            for (int i = 0; i < length; i++) {
                Action action = queue.remove();
                actionsSlice[i] = action;
                action.write(output);
            }
            output.flush();

            // Perform actions
            // noinspection IOResourceOpenedButNotSafelyClosed
            InputStream fileInput = compress ? new InflaterInputStream(input.stream, IOHelper.newInflater(), IOHelper.BUFFER_SIZE) : input.stream;
            for (int i = 0; i < length; i++) {
                Action action = actionsSlice[i];
                switch (action.type) {
                    case CD:
                        currentDir = currentDir.resolve(action.name);
                        Files.createDirectories(currentDir);
                        break;
                    case GET:
                        Path targetFile = currentDir.resolve(action.name);
                        if (fileInput.read() != 0xFF) {
                            throw new IOException("Serverside cached size mismath for file " + action.name);
                        }
                        downloadFile(targetFile, (HashedFile) action.entry, fileInput);
                        break;
                    case CD_BACK:
                        currentDir = currentDir.getParent();
                        break;
                    case FINISH:
                        break;
                    default:
                        throw new AssertionError(String.format("Unsupported action type: '%s'", action.type.name()));
                }
            }
        }

        // Write update completed packet
        deleteExtraDir(dir, diff.extra, diff.extra.flag);
        return remoteHDirHolder;
    }

    @LauncherAPI
    public void setStateCallback(Callback callback) {
        stateCallback = callback;
    }

    private void deleteExtraDir(Path subDir, HashedDir subHDir, boolean flag) throws IOException {
        for (Entry<String, HashedEntry> mapEntry : subHDir.map().entrySet()) {
            String name = mapEntry.getKey();
            Path path = subDir.resolve(name);

            // Delete files and dirs based on type
            HashedEntry entry = mapEntry.getValue();
            HashedEntry.Type entryType = entry.getType();
            switch (entryType) {
                case FILE:
                    updateState(IOHelper.toString(path), 0, 0);
                    Files.delete(path);
                    break;
                case DIR:
                    deleteExtraDir(path, (HashedDir) entry, flag || entry.flag);
                    break;
                default:
                    throw new AssertionError("Unsupported hashed entry type: " + entryType.name());
            }
        }

        // Delete!
        if (flag) {
            updateState(IOHelper.toString(subDir), 0, 0);
            Files.delete(subDir);
        }
    }

    private void downloadFile(Path file, HashedFile hFile, InputStream input) throws IOException {
        String filePath = IOHelper.toString(dir.relativize(file));
        updateState(filePath, 0L, hFile.size);

        // Start file update
        MessageDigest digest = this.digest ? SecurityHelper.newDigest(DigestAlgorithm.MD5) : null;
        try (OutputStream fileOutput = IOHelper.newOutput(file)) {
            long downloaded = 0L;

            // Download with digest update
            byte[] bytes = IOHelper.newBuffer();
            while (downloaded < hFile.size) {
                int remaining = (int) Math.min(hFile.size - downloaded, bytes.length);
                int length = input.read(bytes, 0, remaining);
                if (length < 0) {
                    throw new EOFException(String.format("%d bytes remaining", hFile.size - downloaded));
                }

                // Update file
                fileOutput.write(bytes, 0, length);
                if (digest != null) {
                    digest.update(bytes, 0, length);
                }

                // Update state
                downloaded += length;
                totalDownloaded += length;
                updateState(filePath, downloaded, hFile.size);
            }
        }

        // Verify digest
        if (digest != null) {
            byte[] digestBytes = digest.digest();
            if (!hFile.isSameDigest(digestBytes)) {
                throw new SecurityException(String.format("File digest mismatch: '%s'", filePath));
            }
        }
    }

    private void fillActionsQueue(Queue<Action> queue, HashedDir mismatch) {
        for (Entry<String, HashedEntry> mapEntry : mismatch.map().entrySet()) {
            String name = mapEntry.getKey();
            HashedEntry entry = mapEntry.getValue();
            HashedEntry.Type entryType = entry.getType();
            switch (entryType) {
                case DIR: // cd - get - cd ..
                    queue.add(new Action(Action.Type.CD, name, entry));
                    fillActionsQueue(queue, (HashedDir) entry);
                    queue.add(Action.CD_BACK);
                    break;
                case FILE: // get
                    queue.add(new Action(Action.Type.GET, name, entry));
                    break;
                default:
                    throw new AssertionError("Unsupported hashed entry type: " + entryType.name());
            }
        }
    }

    private void updateState(String filePath, long fileDownloaded, long fileSize) {
        if (stateCallback != null) {
            stateCallback.call(new State(filePath, fileDownloaded, fileSize,
                totalDownloaded, totalSize, Duration.between(startTime, Instant.now())));
        }
    }

    public static final class Action extends StreamObject {
        public static final Action CD_BACK = new Action(Type.CD_BACK, null, null);
        public static final Action FINISH = new Action(Type.FINISH, null, null);

        // Instance
        public final Type type;
        public final String name;
        public final HashedEntry entry;

        public Action(Type type, String name, HashedEntry entry) {
            this.type = type;
            this.name = name;
            this.entry = entry;
        }

        public Action(HInput input) throws IOException {
            type = Type.read(input);
            name = type == Type.CD || type == Type.GET ? IOHelper.verifyFileName(input.readString(255)) : null;
            entry = null;
        }

        @Override
        public void write(HOutput output) throws IOException {
            EnumSerializer.write(output, type);
            if (type == Type.CD || type == Type.GET) {
                output.writeString(name, 255);
            }
        }

        public enum Type implements Itf {
            CD(1), CD_BACK(2), GET(3), FINISH(255);
            private static final EnumSerializer<Type> SERIALIZER = new EnumSerializer<>(Type.class);
            private final int n;

            Type(int n) {
                this.n = n;
            }

            @Override
            public int getNumber() {
                return n;
            }

            public static Type read(HInput input) throws IOException {
                return SERIALIZER.read(input);
            }
        }
    }

    public static final class State {
        @LauncherAPI public final long fileDownloaded;
        @LauncherAPI public final long fileSize;
        @LauncherAPI public final long totalDownloaded;
        @LauncherAPI public final long totalSize;
        @LauncherAPI public final String filePath;
        @LauncherAPI public final Duration duration;

        public State(String filePath, long fileDownloaded, long fileSize, long totalDownloaded, long totalSize, Duration duration) {
            this.filePath = filePath;
            this.fileDownloaded = fileDownloaded;
            this.fileSize = fileSize;
            this.totalDownloaded = totalDownloaded;
            this.totalSize = totalSize;

            // Also store time of creation
            this.duration = duration;
        }

        @LauncherAPI
        public double getBps() {
            long seconds = duration.getSeconds();
            if (seconds == 0) {
                return -1.0D; // Otherwise will throw /0 exception
            }
            return totalDownloaded / (double) seconds;
        }

        @LauncherAPI
        public Duration getEstimatedTime() {
            double bps = getBps();
            if (bps <= 0.0D) {
                return null; // Otherwise will throw /0 exception
            }
            return Duration.ofSeconds((long) (getTotalRemaining() / bps));
        }

        @LauncherAPI
        public double getFileDownloadedKiB() {
            return fileDownloaded / 1024.0D;
        }

        @LauncherAPI
        public double getFileDownloadedMiB() {
            return getFileDownloadedKiB() / 1024.0D;
        }

        @LauncherAPI
        public double getFileDownloadedPart() {
            if (fileSize == 0) {
                return 0.0D;
            }
            return (double) fileDownloaded / fileSize;
        }

        @LauncherAPI
        public long getFileRemaining() {
            return fileSize - fileDownloaded;
        }

        @LauncherAPI
        public double getFileRemainingKiB() {
            return getFileRemaining() / 1024.0D;
        }

        @LauncherAPI
        public double getFileRemainingMiB() {
            return getFileRemainingKiB() / 1024.0D;
        }

        @LauncherAPI
        public double getFileSizeKiB() {
            return fileSize / 1024.0D;
        }

        @LauncherAPI
        public double getFileSizeMiB() {
            return getFileSizeKiB() / 1024.0D;
        }

        @LauncherAPI
        public double getTotalDownloadedKiB() {
            return totalDownloaded / 1024.0D;
        }

        @LauncherAPI
        public double getTotalDownloadedMiB() {
            return getTotalDownloadedKiB() / 1024.0D;
        }

        @LauncherAPI
        public double getTotalDownloadedPart() {
            if (totalSize == 0) {
                return 0.0D;
            }
            return (double) totalDownloaded / totalSize;
        }

        @LauncherAPI
        public long getTotalRemaining() {
            return totalSize - totalDownloaded;
        }

        @LauncherAPI
        public double getTotalRemainingKiB() {
            return getTotalRemaining() / 1024.0D;
        }

        @LauncherAPI
        public double getTotalRemainingMiB() {
            return getTotalRemainingKiB() / 1024.0D;
        }

        @LauncherAPI
        public double getTotalSizeKiB() {
            return totalSize / 1024.0D;
        }

        @LauncherAPI
        public double getTotalSizeMiB() {
            return getTotalSizeKiB() / 1024.0D;
        }

        @FunctionalInterface
        public interface Callback {
            void call(State state);
        }
    }
}
