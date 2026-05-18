package dev.propulsionteam.computed.integration;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Thin entry point for Sable Companion. All Sable Companion symbols are kept inside {@link SableBridgeImpl}
 * so this class is safe to reference even when the {@code sablecompanion} mod is absent — the JVM will not
 * try to resolve those types until {@link #isLoaded()} returns true.
 */
public final class SableBridge {
    private static Boolean present;

    private SableBridge() {}

    public static boolean isLoaded() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        boolean loaded = ModList.get().isLoaded("sablecompanion");
        present = loaded;
        return loaded;
    }

    /** Opaque handle to a Sable sub-level. Treat as an identity token. */
    public static final class SubLevelHandle {
        private final Object access;

        SubLevelHandle(Object access) {
            this.access = access;
        }

        Object raw() {
            return access;
        }
    }

    /** Returns a handle to the sub-level containing {@code pos}, or null if {@code pos} is in the host world. */
    public static SubLevelHandle containing(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || level.isClientSide || pos == null) {
            return null;
        }
        return SableBridgeImpl.containing(level, pos);
    }

    /** All currently-loaded sub-levels in {@code level}. Empty when Sable is absent. */
    public static List<SubLevelHandle> allSubLevels(Level level) {
        if (!isLoaded() || level == null || level.isClientSide) {
            return List.of();
        }
        return SableBridgeImpl.allSubLevels(level);
    }

    /** A {@link BlockPos} inside the sub-level's plot-grid bounds, suitable for use as a network anchor. */
    public static BlockPos representativePos(SubLevelHandle handle) {
        if (handle == null || !isLoaded()) {
            return null;
        }
        return SableBridgeImpl.representativePos(handle.raw());
    }
}
