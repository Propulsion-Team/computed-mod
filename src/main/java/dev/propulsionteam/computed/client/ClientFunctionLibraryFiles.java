package dev.propulsionteam.computed.client;

import com.mojang.logging.LogUtils;
import dev.propulsionteam.computed.Computed;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * Client-only folder under {@code config/computed/functions} for exporting/importing inner function graphs as
 * {@code .nbt} files (same structure as stored {@link dev.devce.websnodelib.api.FunctionDefinitionStore} bodies).
 */
public final class ClientFunctionLibraryFiles {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientFunctionLibraryFiles() {}

    public static Path rootPath() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve(Computed.MODID)
                .resolve("functions");
    }

    public static Path ensureRoot() {
        Path p = rootPath();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            LOGGER.warn("Could not create {}", p, e);
        }
        return p;
    }

    public static String safeBaseName(String displayName) {
        String s = displayName == null ? "" : displayName.trim();
        if (s.isEmpty()) {
            return "function";
        }
        return s.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    public static void saveInnerGraphTag(String displayName, CompoundTag innerGraphTag) {
        Path dir = ensureRoot();
        Path file = dir.resolve(safeBaseName(displayName) + ".nbt");
        try {
            NbtIo.writeCompressed(innerGraphTag, file);
        } catch (IOException e) {
            LOGGER.warn("Failed to save {}", file, e);
        }
    }

    public static List<Path> listNbtFiles(Path root) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(out::add);
        } catch (IOException e) {
            LOGGER.warn("Failed to list {}", root, e);
        }
        return out;
    }

    /** Opens the folder in the OS shell (Explorer / Finder / xdg-open). */
    public static void openFolder(Path dir) {
        Path abs = dir.toAbsolutePath();
        try {
            Files.createDirectories(abs);
        } catch (IOException e) {
            LOGGER.warn("Could not mkdir {}", abs, e);
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(abs.toFile());
                return;
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOGGER.warn("Desktop.open failed for {}", abs, e);
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", abs.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", abs.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", abs.toString()).start();
            }
        } catch (IOException e) {
            LOGGER.warn("Could not open folder {}", abs, e);
        }
    }
}
