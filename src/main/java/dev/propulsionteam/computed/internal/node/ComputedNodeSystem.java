package dev.propulsionteam.computed.internal.node;

import dev.propulsionteam.computed.api.node.ComputedNodeApi;
import dev.propulsionteam.computed.api.node.NodeCategory;
import dev.propulsionteam.computed.api.node.NodeType;
import dev.propulsionteam.computed.internal.node.api.InternalNodeTypeAdapter;
import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Computed's built-in node system. Derived portions retain the upstream MIT attribution. */
public final class ComputedNodeSystem {
    private ComputedNodeSystem() {}

    public static void bootstrap() {
        dev.propulsionteam.computed.internal.node.internal.BuiltinNodes.register();
    }

    /** Bridges built-ins and third-party public API registrations, then freezes the public registry. */
    public static void finalizeRegistrations() {
        publishInternalCategories();
        Map<ResourceLocation, ResourceLocation> paletteCategories = new HashMap<>();
        for (NodeMenuRegistry.MenuEntry entry : NodeMenuRegistry.getExplicitEntries()) {
            paletteCategories.putIfAbsent(entry.nodeType(), publicCategory(entry.categoryId()));
        }

        for (Map.Entry<ResourceLocation, NodeRegistry.NodeFactory> entry :
                NodeRegistry.getRegistry().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (ComputedNodeApi.nodeType(id).isPresent()) {
                throw new IllegalStateException(
                        "Public node type " + id + " conflicts with a Computed built-in or JSON node");
            }
            WNode sample = entry.getValue().create(0, 0);
            ComputedNodeApi.register(InternalNodeTypeAdapter.describe(
                    entry.getValue(),
                    sample,
                    paletteCategories.getOrDefault(id, ComputedNodeApi.UNCATEGORIZED_CATEGORY)));
        }

        publishPublicCategoriesToEditor();
        for (NodeType<?> type : ComputedNodeApi.nodeTypes().values()) {
            if (NodeRegistry.isRegistered(type.id())) {
                continue;
            }
            NodeRegistry.register(type.id(), (x, y) -> new ApiBackedWNode(type, x, y));
            NodeMenuRegistry.addNodeEntry(internalCategory(type.category()), type.id(), type.title());
        }
        ComputedNodeApi.freeze();
    }

    private static void publishInternalCategories() {
        for (NodeMenuRegistry.Category category : NodeMenuRegistry.getCategories()) {
            ResourceLocation publicId = publicCategory(category.id());
            if (ComputedNodeApi.category(publicId).isPresent()) {
                continue;
            }
            ComputedNodeApi.registerCategory(NodeCategory.child(
                    publicId,
                    category.title(),
                    publicCategory(category.parentId())));
        }
    }

    private static void publishPublicCategoriesToEditor() {
        for (NodeCategory category : ComputedNodeApi.categories().values()) {
            if (category.id().equals(ComputedNodeApi.ROOT_CATEGORY)
                    || category.id().equals(ComputedNodeApi.UNCATEGORIZED_CATEGORY)
                    || NodeMenuRegistry.getCategory(category.id()) != null) {
                continue;
            }
            ResourceLocation parent = category.parentId()
                    .map(ComputedNodeSystem::internalCategory)
                    .orElse(NodeMenuRegistry.ROOT);
            NodeMenuRegistry.registerCategory(category.id(), category.title(), parent);
        }
    }

    private static ResourceLocation publicCategory(ResourceLocation internal) {
        if (internal.equals(NodeMenuRegistry.ROOT)) return ComputedNodeApi.ROOT_CATEGORY;
        if (internal.equals(NodeMenuRegistry.UNCATEGORIZED)) return ComputedNodeApi.UNCATEGORIZED_CATEGORY;
        return internal;
    }

    private static ResourceLocation internalCategory(ResourceLocation publicId) {
        if (publicId.equals(ComputedNodeApi.ROOT_CATEGORY)) return NodeMenuRegistry.ROOT;
        if (publicId.equals(ComputedNodeApi.UNCATEGORIZED_CATEGORY)) return NodeMenuRegistry.UNCATEGORIZED;
        return publicId;
    }

}
