package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** One folder in the editor's hierarchical node palette. */
public record NodeCategory(ResourceLocation id, Component title, Optional<ResourceLocation> parentId) {
    public NodeCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        parentId = Objects.requireNonNull(parentId, "parentId");
    }

    public static NodeCategory root(ResourceLocation id, Component title) {
        return new NodeCategory(id, title, Optional.empty());
    }

    public static NodeCategory child(ResourceLocation id, Component title, ResourceLocation parentId) {
        return new NodeCategory(id, title, Optional.of(Objects.requireNonNull(parentId, "parentId")));
    }
}
