package dev.propulsionteam.computed.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Filesystem boundary for player-visible Computed imports and exports. */
public final class ComputedPackageStorage {
    public enum Kind { GRAPHS("graphs", ".computed"), NODES("nodes", ".lua");
        private final String folder;
        private final String extension;
        Kind(String folder, String extension) {
            this.folder = folder;
            this.extension = extension;
        }
    }

    private static final Pattern ILLEGAL_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private final Path root;

    public ComputedPackageStorage(Path gameDirectory) {
        root = gameDirectory.resolve("computed").normalize();
    }

    public Path directory(Kind kind) throws IOException {
        Path directory = root.resolve(kind.folder).normalize();
        if (!directory.startsWith(root)) throw new IOException("Invalid Computed storage path");
        return Files.createDirectories(directory);
    }

    public List<Path> list(Kind kind) throws IOException {
        try (var paths = Files.list(directory(kind))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(kind.extension))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public Path target(Kind kind, String requestedName) throws IOException {
        String name = sanitizeName(requestedName);
        return directory(kind).resolve(name + kind.extension);
    }

    public Path nextAvailable(Kind kind, String requestedName) throws IOException {
        Path initial = target(kind, requestedName);
        if (!Files.exists(initial)) return initial;
        String base = initial.getFileName().toString()
                .substring(0, initial.getFileName().toString().length() - kind.extension.length());
        for (int number = 1; number < 10_000; number++) {
            Path candidate = directory(kind).resolve(base + " (" + number + ")" + kind.extension);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("Could not find an available export name");
    }

    public static String sanitizeName(String name) {
        String cleaned = ILLEGAL_NAME.matcher(name == null ? "" : name.strip()).replaceAll("_")
                .replaceAll("\\s+", " ").replaceAll("[. ]+$", "");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) throw new IllegalArgumentException("A file name is required");
        return cleaned.length() > 96 ? cleaned.substring(0, 96).strip() : cleaned;
    }
}
