package dev.propulsionteam.computed;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Set from the physical-client entrypoint so common networking code never references client-only types.
 */
public final class ComputerEditorBridge {
    @FunctionalInterface
    public interface Opener {
        void open(BlockPos pos, long serverRevision, CompoundTag graphTag);
    }

    @FunctionalInterface
    public interface SaveResultHandler {
        void handle(BlockPos pos, boolean accepted, long serverRevision, long editorRevision, String message);
    }

    private static Opener opener;
    private static SaveResultHandler saveResultHandler;

    private ComputerEditorBridge() {}

    public static void install(Opener clientOpener, SaveResultHandler clientSaveResultHandler) {
        opener = clientOpener;
        saveResultHandler = clientSaveResultHandler;
    }

    public static void open(BlockPos pos, long serverRevision, CompoundTag graphTag) {
        if (opener != null) {
            opener.open(pos, serverRevision, graphTag);
        }
    }

    public static void saveResult(
            BlockPos pos, boolean accepted, long serverRevision, long editorRevision, String message) {
        if (saveResultHandler != null) {
            saveResultHandler.handle(pos, accepted, serverRevision, editorRevision, message);
        }
    }
}
