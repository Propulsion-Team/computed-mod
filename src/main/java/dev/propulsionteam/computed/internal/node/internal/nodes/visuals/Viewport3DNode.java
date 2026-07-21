package dev.propulsionteam.computed.internal.node.internal.nodes.visuals;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WSlider;
import dev.propulsionteam.computed.internal.node.api.elements.WViewport3D;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeCategories;
import dev.propulsionteam.computed.internal.node.internal.BuiltinNodeIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

public final class Viewport3DNode extends WNode {
    public static final ResourceLocation TYPE_ID = BuiltinNodeIds.of("3d_preview");
    public static final ResourceLocation MENU = BuiltinNodeCategories.VISUALS;
    public static final Component LABEL = Component.literal("3D Viewport");

    public Viewport3DNode(int x, int y) {
        super(TYPE_ID, "3D Viewport", x, y);
        setWidth(150);
        WViewport3D viewport = new WViewport3D(140, 100);
        WSlider rotX = new WSlider("Rot X", 0, 360, 130);
        WSlider rotY = new WSlider("Rot Y", 0, 360, 130);
        ItemStack stack = new ItemStack(Blocks.DIAMOND_BLOCK);
        viewport.addModel(stack, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), 1.0f);
        addElement(viewport);
        addElement(rotX);
        addElement(rotY);
        setEvaluator(n -> {
            if (!viewport.getModels().isEmpty()) {
                viewport.getModels().get(0).rot.x = (float) rotX.getValue();
                viewport.getModels().get(0).rot.y = (float) rotY.getValue();
            }
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, Viewport3DNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
