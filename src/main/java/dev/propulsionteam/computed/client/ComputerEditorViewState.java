package dev.propulsionteam.computed.client;

import com.mojang.logging.LogUtils;
import dev.propulsionteam.computed.Computed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * Persists node editor pan/zoom per client player and per computer block (dimension + position).
 */
public final class ComputerEditorViewState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "computer_editor_view.nbt";
    private static final String ENTRIES = "entries";
    private static final String PLAYER = "player";
    private static final String DIM = "dim";
    private static final String POS = "pos";
    private static final String ZOOM = "zoom";
    private static final String PAN_X = "panX";
    private static final String PAN_Y = "panY";
    /** View slot; {@link #ROOT_CTX} for the main graph, else function UUID string (must match UI constant). */
    private static final String CTX = "ctx";
    /** Same string as {@link dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen#EDITOR_VIEWPORT_ROOT}. */
    private static final String ROOT_CTX = "root";

    public record Viewport(float zoom, double panX, double panY) {}

    private ComputerEditorViewState() {}

    private static Path storagePath() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve(Computed.MODID)
                .resolve(FILE);
    }

    /**
     * @param contextKey root graph ({@code "root"}) or inner-function id ({@link UUID#toString()}).
     */
    public static Optional<Viewport> load(
            UUID player, ResourceKey<Level> dimension, BlockPos computerPos, String contextKey) {
        Path path = storagePath();
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList(ENTRIES, Tag.TAG_COMPOUND);
            String dimStr = dimension.location().toString();
            long posL = computerPos.asLong();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (!e.hasUUID(PLAYER) || !e.contains(DIM, Tag.TAG_STRING) || !e.contains(POS, Tag.TAG_LONG)) {
                    continue;
                }
                if (!e.getUUID(PLAYER).equals(player)
                        || !e.getString(DIM).equals(dimStr)
                        || e.getLong(POS) != posL) {
                    continue;
                }
                if (!entryContextMatches(e, contextKey)) {
                    continue;
                }
                float z = e.contains(ZOOM, Tag.TAG_FLOAT) ? e.getFloat(ZOOM) : 1.0f;
                double px = e.contains(PAN_X, Tag.TAG_DOUBLE) ? e.getDouble(PAN_X) : 0.0;
                double py = e.contains(PAN_Y, Tag.TAG_DOUBLE) ? e.getDouble(PAN_Y) : 0.0;
                z = Mth.clamp(z, 0.1f, 3.0f);
                return Optional.of(new Viewport(z, px, py));
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read {}: {}", path, ex.getMessage());
        }
        return Optional.empty();
    }

    /** Legacy single-slot files (no {@link #CTX}) are treated as the root graph viewport. */
    private static boolean entryContextMatches(CompoundTag e, String contextKey) {
        String ctx = e.contains(CTX, Tag.TAG_STRING) ? e.getString(CTX) : ROOT_CTX;
        return ctx.equals(contextKey);
    }

    /**
     * @param contextKey root graph ({@code "root"}) or inner-function id ({@link UUID#toString()}).
     */
    public static void save(
            UUID player,
            ResourceKey<Level> dimension,
            BlockPos computerPos,
            String contextKey,
            double panX,
            double panY,
            float zoom) {
        Path path = storagePath();
        try {
            Files.createDirectories(path.getParent());
            CompoundTag root = Files.isRegularFile(path)
                    ? NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
                    : new CompoundTag();
            ListTag list = root.getList(ENTRIES, Tag.TAG_COMPOUND);
            ListTag newList = new ListTag();
            String dimStr = dimension.location().toString();
            long posL = computerPos.asLong();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (!e.hasUUID(PLAYER) || !e.contains(DIM, Tag.TAG_STRING) || !e.contains(POS, Tag.TAG_LONG)) {
                    newList.add(e);
                    continue;
                }
                if (e.getUUID(PLAYER).equals(player)
                        && e.getString(DIM).equals(dimStr)
                        && e.getLong(POS) == posL
                        && entryContextMatches(e, contextKey)) {
                    continue;
                }
                newList.add(e);
            }
            CompoundTag n = new CompoundTag();
            n.putUUID(PLAYER, player);
            n.putString(DIM, dimStr);
            n.putLong(POS, posL);
            n.putString(CTX, contextKey);
            n.putFloat(ZOOM, Mth.clamp(zoom, 0.1f, 3.0f));
            n.putDouble(PAN_X, panX);
            n.putDouble(PAN_Y, panY);
            newList.add(n);
            root.put(ENTRIES, newList);
            NbtIo.writeCompressed(root, path);
        } catch (IOException ex) {
            LOGGER.warn("Failed to write {}: {}", path, ex.getMessage());
        }
    }
}
