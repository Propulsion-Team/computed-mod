package dev.propulsionteam.computed.content;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.nodes.RedstonePortNode;
import net.minecraft.resources.ResourceLocation;

public final class ComputedNodes {
    private static final ResourceLocation MENU_COMPUTED =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_computed");

    private ComputedNodes() {}

    public static void register() {
        NodeMenuRegistry.registerCategory(
                MENU_COMPUTED, net.minecraft.network.chat.Component.literal("Computed"), NodeMenuRegistry.ROOT);

        ResourceLocation antennaId = ResourceLocation.fromNamespaceAndPath(Computed.MODID, "antenna");
        NodeRegistry.register(antennaId, (x, y) -> {
            WNode node = new WNode(
                    ResourceLocation.fromNamespaceAndPath(Computed.MODID, "antenna"),
                    "Antenna",
                    x,
                    y);
            node.addOutput("Signal", 0xFF88AAFF);
            node.addElement(new WLabel("Receives / emits RF."));
            node.setEvaluator(n -> n.getOutputs().get(0).setValue(1.0));
            return node;
        });

        NodeMenuRegistry.addNodeEntry(
                MENU_COMPUTED, antennaId, net.minecraft.network.chat.Component.literal("Antenna"));

        NodeRegistry.register(RedstonePortNode.TYPE_ID, RedstonePortNode::new);
        NodeMenuRegistry.addNodeEntry(
                MENU_COMPUTED,
                RedstonePortNode.TYPE_ID,
                net.minecraft.network.chat.Component.literal("Redstone"));
    }
}
