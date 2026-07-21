package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;

public final class BlockRotationNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "block_rotation");

    public BlockRotationNode(int x, int y) {
        super(TYPE_ID, "Block Rotation", x, y);
        addOutput("Yaw",  0xFFFF0000);
        addOutput("Pitch",  0xFF00FF00);
        addOutput("Roll",  0xFF0000FF);

        addElement(new WLabel("rotation of this computer"));

        setEvaluator(n -> {
            ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
            if (host == null) return;

            SableCompanion sable = SableCompanion.INSTANCE;

            SubLevelAccess subLevel = sable.getContaining(host);
            if (subLevel == null) return;

            Vector3d euler = new Vector3d();
            subLevel.logicalPose().orientation().getEulerAnglesYXZ(euler);

            n.getOutputs().get(0).setValue(Math.toDegrees(euler.y));
            n.getOutputs().get(1).setValue(Math.toDegrees(euler.x));
            n.getOutputs().get(2).setValue(Math.toDegrees(euler.z));
        });
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("Block Rotation");

    public static void register() {
        NodeRegistry.register(TYPE_ID, BlockRotationNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}