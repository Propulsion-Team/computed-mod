package dev.propulsionteam.computed.integration;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Touches Sable Companion types directly. Only invoked by {@link SableBridge} after a positive
 * {@code ModList.isLoaded("sablecompanion")} check, so the classloader never resolves these
 * symbols when the mod is absent.
 */
final class SableBridgeImpl {
    private SableBridgeImpl() {}

    static SableBridge.SubLevelHandle containing(Level level, BlockPos pos) {
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, pos);
        return access == null ? null : new SableBridge.SubLevelHandle(access);
    }

    static List<SableBridge.SubLevelHandle> allSubLevels(Level level) {
        BoundingBox3dc everywhere =
                new BoundingBox3d(-3.0e7, -2048.0, -3.0e7, 3.0e7, 2048.0, 3.0e7);
        Iterable<? extends SubLevelAccess> hits = SableCompanion.INSTANCE.getAllIntersecting(level, everywhere);
        List<SableBridge.SubLevelHandle> out = new ArrayList<>();
        for (SubLevelAccess a : hits) {
            out.add(new SableBridge.SubLevelHandle(a));
        }
        return out;
    }

    static BlockPos representativePos(Object rawHandle) {
        SubLevelAccess access = (SubLevelAccess) rawHandle;
        BoundingBox3dc bb = access.boundingBox();
        double cx = 0.5 * (bb.minX() + bb.maxX());
        double cy = 0.5 * (bb.minY() + bb.maxY());
        double cz = 0.5 * (bb.minZ() + bb.maxZ());
        return BlockPos.containing(cx, cy, cz);
    }
}
