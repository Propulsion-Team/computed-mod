package dev.propulsionteam.computed.node.program;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistent node instance. {@code rawTag} is deliberately retained so a missing addon node can be
 * loaded, moved, reconnected, saved, and later recovered without understanding its private data.
 */
public record NodeModel(
        UUID id,
        String typeId,
        String originalTypeId,
        String title,
        int x,
        int y,
        CompoundTag properties,
        CompoundTag state,
        List<PortModel> ports,
        PlaceholderStatus placeholderStatus,
        CompoundTag rawTag) {

    public NodeModel {
        Objects.requireNonNull(id, "id");
        typeId = typeId == null || typeId.isBlank() ? "computed:missing" : typeId;
        originalTypeId = originalTypeId == null ? typeId : originalTypeId;
        title = title == null ? "" : title;
        properties = properties == null ? new CompoundTag() : properties.copy();
        state = state == null ? new CompoundTag() : state.copy();
        ports = ports == null ? List.of() : List.copyOf(ports);
        placeholderStatus = placeholderStatus == null ? PlaceholderStatus.RESOLVED : placeholderStatus;
        rawTag = rawTag == null ? new CompoundTag() : rawTag.copy();
    }

    @Override
    public CompoundTag properties() {
        return properties.copy();
    }

    @Override
    public CompoundTag state() {
        return state.copy();
    }

    @Override
    public CompoundTag rawTag() {
        return rawTag.copy();
    }

    public boolean isPlaceholder() {
        return placeholderStatus != PlaceholderStatus.RESOLVED;
    }

    public Optional<PortModel> port(PortId portId, PortModel.Direction direction) {
        return ports.stream()
                .filter(port -> port.direction() == direction && port.id().equals(portId))
                .findFirst();
    }

    public enum PlaceholderStatus {
        RESOLVED,
        MISSING_TYPE,
        MALFORMED_TYPE
    }
}
