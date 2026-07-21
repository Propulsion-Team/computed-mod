package dev.propulsionteam.computed.node.program;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** A directed connection between two stable port ids. */
public record ConnectionModel(
        UUID id,
        UUID sourceNode,
        PortId sourcePort,
        UUID targetNode,
        PortId targetPort,
        List<Waypoint> waypoints,
        CompoundTag rawTag) {

    public ConnectionModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceNode, "sourceNode");
        Objects.requireNonNull(sourcePort, "sourcePort");
        Objects.requireNonNull(targetNode, "targetNode");
        Objects.requireNonNull(targetPort, "targetPort");
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        rawTag = rawTag == null ? new CompoundTag() : rawTag.copy();
    }

    @Override
    public CompoundTag rawTag() {
        return rawTag.copy();
    }

    public record Waypoint(double x, double y) {}
}
