package dev.propulsionteam.computed.api.node.client;

import dev.propulsionteam.computed.api.node.NodeType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Client-only startup registry for optional custom node presentations. */
public final class ComputedNodeClientApi {
    private static final LinkedHashMap<ResourceLocation, NodePresentation> PRESENTATIONS = new LinkedHashMap<>();
    private static volatile boolean frozen;

    private ComputedNodeClientApi() {}

    public static synchronized NodePresentation registerPresentation(
            ResourceLocation nodeType, NodePresentation presentation) {
        ensureMutable();
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(presentation, "presentation");
        NodePresentation previous = PRESENTATIONS.putIfAbsent(nodeType, presentation);
        if (previous != null) {
            throw new IllegalStateException(
                    "Cannot register presentation for node type '" + nodeType + "': one is already registered");
        }
        return presentation;
    }

    public static NodePresentation registerPresentation(NodeType<?> nodeType, NodePresentation presentation) {
        return registerPresentation(Objects.requireNonNull(nodeType, "nodeType").id(), presentation);
    }

    public static synchronized Optional<NodePresentation> presentation(ResourceLocation nodeType) {
        return Optional.ofNullable(PRESENTATIONS.get(Objects.requireNonNull(nodeType, "nodeType")));
    }

    public static synchronized Map<ResourceLocation, NodePresentation> presentations() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(PRESENTATIONS));
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException(
                    "Computed client node presentation registry is frozen; registrations must occur during client startup");
        }
    }
}
