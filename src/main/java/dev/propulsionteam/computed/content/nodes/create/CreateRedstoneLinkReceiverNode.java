package dev.propulsionteam.computed.content.nodes.create;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WCheckbox;
import dev.devce.websnodelib.api.elements.WFrequencySlotPair;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Listens on a Create redstone link frequency. {@code Level} is the analog link strength (0–15). With
 * {@code Repeat while powered}, {@code Event} is a one-sample pulse on each logical graph step (e.g. once per
 * server tick for the computer graph, independent of the Tick node's rate) while the link is powered; otherwise
 * a one-tick pulse on a rising edge of link power.
 */
public final class CreateRedstoneLinkReceiverNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "create_redstone_link_receiver");

    private final WFrequencySlotPair freqPair;
    private final WCheckbox repeatWhile;
    private boolean prevPowered;
    private int linkStrength;
    /** Last {@link WGraph#getSimulationStepCounter()} for which repeat mode emitted an Event pulse. */
    private int lastRepeatEmitStep = Integer.MIN_VALUE;

    public CreateRedstoneLinkReceiverNode(int x, int y) {
        super(TYPE_ID, "Receiver", x, y);
        addOutput("Event", 0xFF00FF88);
        addOutput("Level", 0xFFFF6655);
        addElement(new WLabel("Redstone Link"));
        freqPair = new WFrequencySlotPair();
        addElement(freqPair);
        repeatWhile = new WCheckbox("Repeat while powered");
        addElement(repeatWhile);
        setEvaluator(
                n -> {
                    // linkStrength is server-only state (Create's network handler calls us via the
                    // bridge). On the client, the editor runs its own copy of this evaluator each
                    // tick — overwriting the pin would clobber the value loaded from the server's
                    // NBT snapshot, leaving Display nodes stuck at 0. Use the snapshotted pin value
                    // when there's no host (= we're on the client).
                    int effective = ComputedGraphExecution.hostOrNull() == null
                            ? (int) Math.round(n.getOutputs().get(1).getValue())
                            : linkStrength;
                    n.getOutputs().get(1).setValue(effective);
                    boolean on = effective > 0;
                    if (repeatWhile.isChecked()) {
                        if (!on) {
                            n.getOutputs().get(0).setValue(0.0);
                            lastRepeatEmitStep = Integer.MIN_VALUE;
                        } else {
                            WGraph g = n.evaluationGraph();
                            if (g == null) {
                                n.getOutputs().get(0).setValue(0.0);
                            } else {
                                int step = g.getSimulationStepCounter();
                                if (step != lastRepeatEmitStep) {
                                    n.getOutputs().get(0).setValue(1.0);
                                    lastRepeatEmitStep = step;
                                } else {
                                    n.getOutputs().get(0).setValue(0.0);
                                }
                            }
                        }
                    } else {
                        n.getOutputs().get(0).setValue(on && !prevPowered ? 1.0 : 0.0);
                    }
                    prevPowered = on;
                });
    }

    public ItemStack redFrequency() {
        return freqPair.getRed();
    }

    public ItemStack blueFrequency() {
        return freqPair.getBlue();
    }

    public void setLinkInputStrength(int strength) {
        linkStrength = Mth.clamp(strength, 0, 15);
    }

    public static final ResourceLocation MENU = ComputedMenuCategories.CREATE_REDSTONE_LINK;
    public static final Component LABEL = Component.literal("Receiver");

    /** Registers only the node type. Use when the Create menu category should not be added (e.g. Create mod absent). */
    public static void registerType() {
        NodeRegistry.register(TYPE_ID, CreateRedstoneLinkReceiverNode::new);
    }

    public static void register() {
        registerType();
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
