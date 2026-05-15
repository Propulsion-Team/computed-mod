package dev.propulsionteam.computed.content.nodes.create;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WFrequencySlotPair;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.propulsionteam.computed.Computed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Transmits Create redstone link strength from Level when Tick is high. */
public final class CreateRedstoneLinkSenderNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "create_redstone_link_sender");

    private final WFrequencySlotPair freqPair;

    public CreateRedstoneLinkSenderNode(int x, int y) {
        super(TYPE_ID, "Sender", x, y);
        addInput("Tick", 0xFF00FF88);
        addInput("Level", 0xFFFF6655);
        addElement(new WLabel("Redstone Link"));
        freqPair = new WFrequencySlotPair();
        addElement(freqPair);
        setEvaluator(n -> {});
    }

    public ItemStack redFrequency() {
        return freqPair.getRed();
    }

    public ItemStack blueFrequency() {
        return freqPair.getBlue();
    }

    public int readTransmitStrength() {
        if (getInputs().size() < 2) {
            return 0;
        }
        if (getInputs().get(0).getValue() <= 0.5) {
            return 0;
        }
        return Mth.clamp((int) Math.round(getInputs().get(1).getValue()), 0, 15);
    }
}
