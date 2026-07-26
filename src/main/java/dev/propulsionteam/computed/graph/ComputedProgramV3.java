package dev.propulsionteam.computed.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record ComputedProgramV3(
        long revision,
        ComputedGraph rootGraph,
        Map<String, LuaDefinitionSource> library,
        Map<UUID, CompoundTag> persistentState,
        CompoundTag metadata) {

    public static final int FORMAT_VERSION = 3;
    public static final int MAX_EMBEDDED_DEFINITIONS = 256;

    public ComputedProgramV3 {
        revision = Math.max(0, revision);
        Objects.requireNonNull(rootGraph, "rootGraph");
        library = copyLibrary(library);
        persistentState = copyState(persistentState);
        metadata = metadata == null ? new CompoundTag() : metadata.copy();
        long embeddedCount = library.values().stream()
                .filter(source -> source.origin() == LuaDefinitionSource.Origin.EMBEDDED)
                .count();
        if (embeddedCount > MAX_EMBEDDED_DEFINITIONS) {
            throw new IllegalArgumentException(
                    "Program exceeds the embedded definition limit of " + MAX_EMBEDDED_DEFINITIONS);
        }
    }

    public static ComputedProgramV3 empty(UUID graphId) {
        return new ComputedProgramV3(
                0,
                new ComputedGraph(graphId, java.util.List.of(), java.util.List.of()),
                Map.of(),
                Map.of(),
                new CompoundTag());
    }

    @Override
    public Map<UUID, CompoundTag> persistentState() {
        return copyState(persistentState);
    }

    @Override
    public CompoundTag metadata() {
        return metadata.copy();
    }

    public ComputedProgramV3 withRevision(long revision) {
        return new ComputedProgramV3(revision, rootGraph, library, persistentState, metadata);
    }

    private static Map<String, LuaDefinitionSource> copyLibrary(Map<String, LuaDefinitionSource> source) {
        Map<String, LuaDefinitionSource> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((id, definition) -> {
                if (!id.equals(definition.id())) {
                    throw new IllegalArgumentException("Lua definition library key does not match definition id");
                }
                copy.put(id, definition);
            });
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<UUID, CompoundTag> copyState(Map<UUID, CompoundTag> source) {
        Map<UUID, CompoundTag> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((id, state) -> copy.put(id, state.copy()));
        }
        return java.util.Collections.unmodifiableMap(copy);
    }
}
