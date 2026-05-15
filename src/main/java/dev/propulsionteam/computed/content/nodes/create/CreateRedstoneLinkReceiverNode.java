package dev.propulsionteam.computed.content.nodes.create;

import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WCheckbox;
import dev.devce.websnodelib.api.elements.WFrequencySlotPair;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
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
                    n.getOutputs().get(1).setValue(linkStrength);
                    boolean on = linkStrength > 0;
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
}
