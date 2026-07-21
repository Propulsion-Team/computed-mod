package dev.propulsionteam.computed.api.node;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Startup registry for public node types and palette categories. */
public final class ComputedNodeApi {
    public static final ResourceLocation ROOT_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("computed", "root");
    public static final ResourceLocation UNCATEGORIZED_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("computed", "uncategorized");

    private static final LinkedHashMap<ResourceLocation, NodeType<?>> NODE_TYPES = new LinkedHashMap<>();
    private static final LinkedHashMap<ResourceLocation, NodeCategory> CATEGORIES = new LinkedHashMap<>();
    private static volatile boolean frozen;

    static {
        CATEGORIES.put(ROOT_CATEGORY, NodeCategory.root(ROOT_CATEGORY, Component.literal("Nodes")));
        CATEGORIES.put(
                UNCATEGORIZED_CATEGORY,
                NodeCategory.child(UNCATEGORIZED_CATEGORY, Component.literal("Uncategorized"), ROOT_CATEGORY));
    }

    private ComputedNodeApi() {}

    public static synchronized <S> NodeType<S> register(NodeType<S> type) {
        ensureMutable();
        Objects.requireNonNull(type, "type");
        NodeType<?> previous = NODE_TYPES.putIfAbsent(type.id(), type);
        if (previous != null) {
            throw new IllegalStateException(
                    "Cannot register node type '" + type.id() + "': that id is already registered as '"
                            + previous.title().getString() + "'");
        }
        return type;
    }

    public static synchronized NodeCategory registerCategory(NodeCategory category) {
        ensureMutable();
        Objects.requireNonNull(category, "category");
        NodeCategory previous = CATEGORIES.putIfAbsent(category.id(), category);
        if (previous != null) {
            throw new IllegalStateException(
                    "Cannot register node category '" + category.id() + "': that id is already registered as '"
                            + previous.title().getString() + "'");
        }
        return category;
    }

    public static NodeCategory registerCategory(
            ResourceLocation id, Component title, ResourceLocation parentId) {
        return registerCategory(NodeCategory.child(id, title, parentId));
    }

    public static synchronized Optional<NodeType<?>> nodeType(ResourceLocation id) {
        return Optional.ofNullable(NODE_TYPES.get(Objects.requireNonNull(id, "id")));
    }

    public static synchronized NodeType<?> requireNodeType(ResourceLocation id) {
        return nodeType(id).orElseThrow(() -> new IllegalArgumentException("Unknown node type '" + id + "'"));
    }

    public static synchronized Optional<NodeCategory> category(ResourceLocation id) {
        return Optional.ofNullable(CATEGORIES.get(Objects.requireNonNull(id, "id")));
    }

    public static synchronized Map<ResourceLocation, NodeType<?>> nodeTypes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(NODE_TYPES));
    }

    public static synchronized Map<ResourceLocation, NodeCategory> categories() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CATEGORIES));
    }

    /** Validates category references and permanently closes both registries. */
    public static synchronized void freeze() {
        if (frozen) {
            return;
        }
        validateCategories();
        for (NodeType<?> type : NODE_TYPES.values()) {
            if (!CATEGORIES.containsKey(type.category())) {
                throw new IllegalStateException(
                        "Node type '" + type.id() + "' references unknown category '" + type.category() + "'");
            }
        }
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static void validateCategories() {
        for (NodeCategory category : CATEGORIES.values()) {
            category.parentId().ifPresent(parent -> {
                if (!CATEGORIES.containsKey(parent)) {
                    throw new IllegalStateException(
                            "Node category '" + category.id() + "' references unknown parent '" + parent + "'");
                }
            });
        }

        for (ResourceLocation start : CATEGORIES.keySet()) {
            Deque<ResourceLocation> path = new ArrayDeque<>();
            ResourceLocation current = start;
            while (current != null) {
                if (path.contains(current)) {
                    throw new IllegalStateException("Node category cycle detected: " + path + " -> " + current);
                }
                path.addLast(current);
                current = CATEGORIES.get(current).parentId().orElse(null);
            }
        }
    }

    private static void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Computed node registry is frozen; registrations must occur during startup");
        }
    }
}
