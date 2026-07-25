package dev.propulsionteam.computed.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record GraphNode(
        UUID id,
        String definitionId,
        String definitionHash,
        int x,
        int y,
        List<PortSnapshot> ports,
        Map<String, CompoundTag> fields) {

    public GraphNode {
        Objects.requireNonNull(id, "id");
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("definitionId is required");
        }
        definitionHash = definitionHash == null ? "" : definitionHash;
        ports = ports == null ? List.of() : List.copyOf(ports);
        Map<String, CompoundTag> copiedFields = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> copiedFields.put(key, value.copy()));
        }
        fields = java.util.Collections.unmodifiableMap(copiedFields);
    }

    @Override
    public Map<String, CompoundTag> fields() {
        Map<String, CompoundTag> copied = new LinkedHashMap<>();
        fields.forEach((key, value) -> copied.put(key, value.copy()));
        return java.util.Collections.unmodifiableMap(copied);
    }
}
