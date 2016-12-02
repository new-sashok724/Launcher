package launcher.hasher;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

import launcher.LauncherAPI;
import launcher.helper.IOHelper;

public final class FileNameMatcher {
    private static final Entry[] NO_ENTRIES = new Entry[0];

    // Instance
    private final Entry[] update;
    private final Entry[] verify;
    private final Entry[] exclusions;

    @LauncherAPI
    public FileNameMatcher(String[] update, String[] verify, String[] exclusions) {
        this.update = toEntries(update);
        this.verify = toEntries(verify);
        this.exclusions = toEntries(exclusions);
    }

    private FileNameMatcher(Entry[] update, Entry[] verify, Entry[] exclusions) {
        this.update = update;
        this.verify = verify;
        this.exclusions = exclusions;
    }

    @LauncherAPI
    public boolean shouldUpdate(Collection<String> path) {
        return (anyMatch(update, path) || anyMatch(verify, path)) && !anyMatch(exclusions, path);
    }

    @LauncherAPI
    public boolean shouldVerify(Collection<String> path) {
        return anyMatch(verify, path) && !anyMatch(exclusions, path);
    }

    @LauncherAPI
    public FileNameMatcher verifyOnly() {
        return new FileNameMatcher(NO_ENTRIES, verify, exclusions);
    }

    private static boolean anyMatch(Entry[] entries, Collection<String> path) {
        return Arrays.stream(entries).anyMatch(e -> e.matches(path));
    }

    private static Entry[] toEntries(String... entries) {
        return Arrays.stream(entries).map(Entry::new).toArray(Entry[]::new);
    }

    private static final class Entry {
        private static final Pattern SPLITTER = Pattern.compile(Pattern.quote(IOHelper.CROSS_SEPARATOR) + '+');
        private final Pattern[] parts;

        private Entry(CharSequence exclusion) {
            parts = SPLITTER.splitAsStream(exclusion).map(Pattern::compile).toArray(Pattern[]::new);
        }

        private boolean matches(Collection<String> path) {
            if (parts.length > path.size()) {
                return false;
            }

            // Verify path parts
            Iterator<String> iterator = path.iterator();
            for (Pattern patternPart : parts) {
                String pathPart = iterator.next();
                if (!patternPart.matcher(pathPart).matches()) {
                    return false;
                }
            }

            // All matches
            return true;
        }
    }
}
