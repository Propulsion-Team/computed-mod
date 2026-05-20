package dev.devce.websnodelib.internal;

import net.minecraft.resources.ResourceLocation;

public final class WsId {
    private WsId() {}

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath("websnodelib", path);
    }
}
