package dev.propulsionteam.computed;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.function.BiConsumer;

/**
 * Set from the physical-client entrypoint so common networking code never references client-only types.
 */
public final class ComputerEditorBridge {
    private static BiConsumer<BlockPos, CompoundTag> opener;

    private ComputerEditorBridge() {}

    public static void install(BiConsumer<BlockPos, CompoundTag> clientOpener) {
        opener = clientOpener;
    }

    public static void open(BlockPos pos, CompoundTag graphTag) {
        if (opener != null) {
            opener.accept(pos, graphTag);
        }
    }
}
