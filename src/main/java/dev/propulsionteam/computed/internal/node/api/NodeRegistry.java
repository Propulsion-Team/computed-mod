package dev.propulsionteam.computed.internal.node.api;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;

public class NodeRegistry {
    private static final String LEGACY_NAMESPACE = "websnodelib";
    private static final String COMPUTED_NAMESPACE = "computed";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, NodeFactory> REGISTRY = new LinkedHashMap<>();

    public static void register(ResourceLocation id, NodeFactory factory) {
        ResourceLocation canonicalId = canonicalize(id);
        if (REGISTRY.putIfAbsent(canonicalId, factory) != null) {
            throw new IllegalStateException("Duplicate node type registration: " + canonicalId);
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return REGISTRY.containsKey(canonicalize(id));
    }

    public static void unregister(ResourceLocation id) {
        REGISTRY.remove(canonicalize(id));
    }

    public static WNode createNode(ResourceLocation id, int x, int y) {
        ResourceLocation canonicalId = canonicalize(id);
        NodeFactory factory = REGISTRY.get(canonicalId);
        if (factory != null) {
            try {
                return factory.create(x, y);
            } catch (Throwable t) {
                LOGGER.error("Failed to create node type {} at ({}, {})", canonicalId, x, y, t);
                return null;
            }
        }
        return null;
    }

    /** Maps node IDs written by the vendored pre-rewrite engine to their Computed-owned IDs. */
    public static ResourceLocation canonicalize(ResourceLocation id) {
        if (id != null && LEGACY_NAMESPACE.equals(id.getNamespace())) {
            return ResourceLocation.fromNamespaceAndPath(COMPUTED_NAMESPACE, id.getPath());
        }
        return id;
    }

    public static java.util.Set<ResourceLocation> getRegisteredTypes() {
        return REGISTRY.keySet();
    }

    public static Map<ResourceLocation, NodeFactory> getRegistry() {
        return REGISTRY;
    }

    @FunctionalInterface
    public interface NodeFactory {
        WNode create(int x, int y);
    }
}
