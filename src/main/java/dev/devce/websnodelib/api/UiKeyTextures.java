package dev.devce.websnodelib.api;

import net.minecraft.resources.ResourceLocation;

/** Key-cap textures under {@code assets/computed/textures/ui/icons/keys/<name>.png}. */
public final class UiKeyTextures {
    private static final String NS = "computed";
    private static final String PREFIX = "textures/ui/icons/keys/";

    private UiKeyTextures() {}

    public static ResourceLocation key(String basename) {
        return ResourceLocation.fromNamespaceAndPath(NS, PREFIX + basename + ".png");
    }
}
