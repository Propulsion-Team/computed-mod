package dev.propulsionteam.computed.internal.node.internal;

import net.minecraft.resources.ResourceLocation;

public final class BuiltinNodeIds {
    private BuiltinNodeIds() {}

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath("computed", path);
    }
}
