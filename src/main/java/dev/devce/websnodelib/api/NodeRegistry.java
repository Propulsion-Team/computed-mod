package dev.devce.websnodelib.api;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class NodeRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, NodeFactory> REGISTRY = new HashMap<>();

    public static void register(ResourceLocation id, NodeFactory factory) {
        REGISTRY.put(id, factory);
    }

    public static boolean isRegistered(ResourceLocation id) {
        return REGISTRY.containsKey(id);
    }

    public static void unregister(ResourceLocation id) {
        REGISTRY.remove(id);
    }

    public static WNode createNode(ResourceLocation id, int x, int y) {
        NodeFactory factory = REGISTRY.get(id);
        if (factory != null) {
            try {
                return factory.create(x, y);
            } catch (Throwable t) {
                LOGGER.error("Failed to create node type {} at ({}, {})", id, x, y, t);
                return null;
            }
        }
        return null;
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
