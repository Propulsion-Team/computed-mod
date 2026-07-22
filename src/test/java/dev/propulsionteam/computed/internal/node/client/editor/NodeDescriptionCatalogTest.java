package dev.propulsionteam.computed.internal.node.client.editor;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NodeDescriptionCatalogTest {
    @Test void coversTheCompleteBuiltInPaletteAndFallsBackForAddons() {
        assertEquals(85, NodeDescriptionCatalog.builtInDescriptionCount());
        ResourceLocation add = ResourceLocation.fromNamespaceAndPath("computed", "math_add");
        assertTrue(NodeDescriptionCatalog.hasBuiltInDescription(add));
        assertEquals("Adds the Teleport node.", NodeDescriptionCatalog.description(
                ResourceLocation.fromNamespaceAndPath("addon", "teleport"), Component.literal("Teleport")));
    }
}
