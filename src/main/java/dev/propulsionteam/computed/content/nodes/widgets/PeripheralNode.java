package dev.propulsionteam.computed.content.nodes.widgets;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.api.elements.WButton;
import dev.propulsionteam.computed.internal.node.api.elements.WDropdown;
import dev.propulsionteam.computed.internal.node.api.elements.WLabel;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.MonitorWidgetLayout;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import dev.propulsionteam.computed.content.nodes.vanilla.RelativeFace;
import dev.propulsionteam.computed.network.ComputedNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds an adjacent monitor (selected by side relative to the computer) to this computer and pushes the
 * widgets connected to its inputs as a draw list every graph tick.
 */
public final class PeripheralNode extends WNode {
    private static final int MIN_INPUTS = 1;
    private static final int MAX_INPUTS = 16;

    private int inputCount = 1;
    private RelativeFace face = RelativeFace.FRONT;
    private WDropdown<RelativeFace> faceDropdown;

    public PeripheralNode(int x, int y) {
        super(WidgetNodeIds.PERIPHERAL, "Monitor", x, y);
        rebuildUiAndPins();
        setEvaluator(this::evaluateNode);
    }

    private void rebuildUiAndPins() {
        getInputs().clear();
        getOutputs().clear();
        markPinSchemaChanged();
        getElements().clear();

        for (int i = 0; i < inputCount; i++) {
            addInput("W" + (i + 1), WPin.DataType.WIDGET, WPin.COLOR_WIDGET_DEFAULT);
        }
        faceDropdown = new WDropdown<>(
                88,
                List.of(RelativeFace.values()),
                f -> "Face: " + f.displayName(),
                face,
                f -> face = f);
        addElement(faceDropdown);
        addElement(new WButton("+ widget", 60, () -> {
            if (inputCount < MAX_INPUTS) {
                inputCount++;
                rebuildUiAndPins();
            }
        }));
        addElement(new WButton("- widget", 60, () -> {
            if (inputCount > MIN_INPUTS) {
                inputCount--;
                rebuildUiAndPins();
            }
        }));
        addElement(new WLabel("Binds to monitor on chosen side", 0xFF888888));
        addElement(new WLabel("Screen px = monitor_blocks * 64", 0xFF888888));
        updateLayout();
    }

    private void evaluateNode(WNode n) {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return;
        Level level = host.getLevel();
        if (level == null || level.isClientSide) return;
        Direction worldFace = face.toWorld(host.getBlockState().getValue(ComputerBlock.FACING));
        BlockPos target = host.getBlockPos().relative(worldFace);
        BlockEntity be = level.getBlockEntity(target);
        if (!(be instanceof MonitorBlockEntity monitor)) return;
        MonitorBlockEntity origin = monitor.findOrigin();
        if (origin == null) return;

        List<Widget> widgets = new ArrayList<>(n.getInputs().size());
        for (var pin : n.getInputs()) {
            if (!pin.isConnected()) continue;
            Object v = pin.getWidgetValue();
            if (v instanceof Widget w) widgets.add(w);
        }
        int screenW = origin.getWidth() * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        int screenH = origin.getHeight() * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        widgets = MonitorWidgetLayout.resolve(widgets, screenW, screenH);
        origin.bindOwner(host.getBlockPos());
        origin.setDrawList(new WidgetDrawList(widgets));
    }

    public RelativeFace getFace() { return face; }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("PeripheralFace", face.name());
        tag.putInt("inputCount", inputCount);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("inputCount")) {
            inputCount = Mth.clamp(tag.getInt("inputCount"), MIN_INPUTS, MAX_INPUTS);
            rebuildUiAndPins();
        }
        super.load(tag);
        if (tag.contains("PeripheralFace")) {
            try {
                face = RelativeFace.valueOf(tag.getString("PeripheralFace"));
                faceDropdown.setSelected(face);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public static final net.minecraft.resources.ResourceLocation TYPE_ID = WidgetNodeIds.PERIPHERAL;
    public static final net.minecraft.resources.ResourceLocation MENU = ComputedMenuCategories.PERIPHERALS;
    public static final net.minecraft.network.chat.Component LABEL =
            net.minecraft.network.chat.Component.literal("Monitor");

    public static void register() {
        NodeRegistry.register(TYPE_ID, PeripheralNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
