package dev.propulsionteam.computed.customnodes;

import dev.propulsionteam.computed.Computed;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class ComputedCustomNodes {
    private static final CustomNodeRegistrar REGISTRAR = new CustomNodeRegistrar();

    private ComputedCustomNodes() {}

    public static Path rootPath() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve(Computed.MODID).resolve("nodes");
    }

    public static CustomNodeRegistrar.ReloadSummary reload() {
        Path root = rootPath();
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            Computed.LOGGER.warn("Could not create custom node directory {}", root, e);
        }
        CustomNodeRegistrar.ReloadSummary summary = REGISTRAR.reload(root);
        for (String message : summary.messages()) {
            if (message.startsWith("ERROR ")) {
                Computed.LOGGER.error("[custom-nodes] {}", message.substring(6));
            } else if (message.startsWith("WARN ")) {
                Computed.LOGGER.warn("[custom-nodes] {}", message.substring(5));
            } else {
                Computed.LOGGER.info("[custom-nodes] {}", message);
            }
        }
        Computed.LOGGER.info(
                "[custom-nodes] reload complete: loaded={}, skipped={}, warnings={}, errors={}, root={}",
                summary.loaded(),
                summary.skipped(),
                summary.warnings(),
                summary.errors(),
                root);
        return summary;
    }
}
