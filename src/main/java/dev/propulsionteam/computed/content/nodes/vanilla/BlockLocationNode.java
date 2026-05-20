package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class BlockLocationNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "block_location");

    public BlockLocationNode(int x, int y) {
        super(TYPE_ID, "Block Location", x, y);
        addOutput("X",  0xFFFF0000);
        addOutput("Y",  0xFF00FF00);
        addOutput("Z",  0xFF0000FF);

        addElement(new WLabel("Position of this computer"));

        setEvaluator(n -> {
            ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
            if (host == null) return;

            BlockPos pos = host.getBlockPos();

            Vec3 worldPos = SableCompanion.INSTANCE.projectOutOfSubLevel(
                    host.getLevel(),
                    (net.minecraft.core.Position) Vec3.atCenterOf(pos)
            );

            n.getOutputs().get(0).setValue(worldPos.x);
            n.getOutputs().get(1).setValue(worldPos.y);
            n.getOutputs().get(2).setValue(worldPos.z);
        });
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("Block Location");

    public static void register() {
        NodeRegistry.register(TYPE_ID, BlockLocationNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}