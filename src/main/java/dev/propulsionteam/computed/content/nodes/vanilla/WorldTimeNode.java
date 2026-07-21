package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Exposes the host level's day-time as three outputs: raw ticks in [0, 24000), normalized phase in [0, 1),
 * and a daylight boolean (1.0 when ticks &lt; 12000).
 */
public final class WorldTimeNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "world_time");

    public WorldTimeNode(int x, int y) {
        super(TYPE_ID, "World Time", x, y);
        addOutput("Ticks", 0xFFFFBB00);
        addOutput("Phase", 0xFF88CCFF);
        addOutput("IsDay", 0xFFFFFF66);
        addElement(new WLabel("Day-time: ticks, 0-1 phase, isDay"));
        setEvaluator(n -> {
            long ticks = readDayTime();
            long inDay = ((ticks % 24000L) + 24000L) % 24000L;
            n.getOutputs().get(0).setValue(inDay);
            n.getOutputs().get(1).setValue(inDay / 24000.0);
            n.getOutputs().get(2).setValue(inDay < 12000 ? 1.0 : 0.0);
        });
    }

    private long readDayTime() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return 0;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return 0;
        return lvl.getDayTime();
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.VANILLA;
    public static final Component LABEL = Component.literal("World Time");

    public static void register() {
        NodeRegistry.register(TYPE_ID, WorldTimeNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
