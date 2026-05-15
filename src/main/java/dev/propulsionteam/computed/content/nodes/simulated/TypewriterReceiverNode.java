package dev.propulsionteam.computed.content.nodes.simulated;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WButton;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.api.elements.WTextField;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.integration.SimulatedLinkedTypewriterBridge;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Rising-edge listener on a linked Simulated typewriter's held keys. {@code Event} pulses when a new key is pressed;
 * {@code Key} is its GLFW code for that step. Filter picks letter/modifier keys (not digits); {@code Any} fires on
 * every new press.
 */
public final class TypewriterReceiverNode extends WNode {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "typewriter_receiver");

    private record KeyPick(int glfw, String name) {}

    /** {@code glfw == -1} means accept any key. */
    private static final List<KeyPick> KEY_PICKS =
            List.of(
                    new KeyPick(-1, "Any"),
                    new KeyPick(GLFW.GLFW_KEY_Q, "Q"),
                    new KeyPick(GLFW.GLFW_KEY_W, "W"),
                    new KeyPick(GLFW.GLFW_KEY_E, "E"),
                    new KeyPick(GLFW.GLFW_KEY_R, "R"),
                    new KeyPick(GLFW.GLFW_KEY_T, "T"),
                    new KeyPick(GLFW.GLFW_KEY_Y, "Y"),
                    new KeyPick(GLFW.GLFW_KEY_U, "U"),
                    new KeyPick(GLFW.GLFW_KEY_I, "I"),
                    new KeyPick(GLFW.GLFW_KEY_O, "O"),
                    new KeyPick(GLFW.GLFW_KEY_P, "P"),
                    new KeyPick(GLFW.GLFW_KEY_A, "A"),
                    new KeyPick(GLFW.GLFW_KEY_S, "S"),
                    new KeyPick(GLFW.GLFW_KEY_D, "D"),
                    new KeyPick(GLFW.GLFW_KEY_F, "F"),
                    new KeyPick(GLFW.GLFW_KEY_G, "G"),
                    new KeyPick(GLFW.GLFW_KEY_H, "H"),
                    new KeyPick(GLFW.GLFW_KEY_J, "J"),
                    new KeyPick(GLFW.GLFW_KEY_K, "K"),
                    new KeyPick(GLFW.GLFW_KEY_L, "L"),
                    new KeyPick(GLFW.GLFW_KEY_Z, "Z"),
                    new KeyPick(GLFW.GLFW_KEY_X, "X"),
                    new KeyPick(GLFW.GLFW_KEY_C, "C"),
                    new KeyPick(GLFW.GLFW_KEY_V, "V"),
                    new KeyPick(GLFW.GLFW_KEY_B, "B"),
                    new KeyPick(GLFW.GLFW_KEY_N, "N"),
                    new KeyPick(GLFW.GLFW_KEY_M, "M"),
                    new KeyPick(GLFW.GLFW_KEY_SPACE, "Space"),
                    new KeyPick(GLFW.GLFW_KEY_ENTER, "Enter"),
                    new KeyPick(GLFW.GLFW_KEY_ESCAPE, "Esc"),
                    new KeyPick(GLFW.GLFW_KEY_TAB, "Tab"),
                    new KeyPick(GLFW.GLFW_KEY_BACKSPACE, "Backspace"),
                    new KeyPick(GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl"),
                    new KeyPick(GLFW.GLFW_KEY_LEFT_SHIFT, "Shift"),
                    new KeyPick(GLFW.GLFW_KEY_LEFT_ALT, "Alt"),
                    new KeyPick(GLFW.GLFW_KEY_UP, "Up"),
                    new KeyPick(GLFW.GLFW_KEY_DOWN, "Down"),
                    new KeyPick(GLFW.GLFW_KEY_LEFT, "Left"),
                    new KeyPick(GLFW.GLFW_KEY_RIGHT, "Right"));

    private final WTextField instanceField;
    private final WLabel keyLabel;
    private int optionIndex;
    /** Previous snapshot of {@code getPressedKeys()} on the typewriter; null until after first sample. */
    private Set<Integer> prevPressed;
    private int prevParsedInstance = Integer.MIN_VALUE;

    public TypewriterReceiverNode(int x, int y) {
        super(TYPE_ID, "Typewriter RX", x, y);
        addOutput("Event", 0xFF00FF88);
        addOutput("Key", 0xFFFF6655);
        addElement(new WLabel("Instance #", 0xFFAAAAAA));
        instanceField = new WTextField(48);
        instanceField.setValue("1");
        addElement(instanceField);
        keyLabel = new WLabel("", 0xFFCCCCCC);
        addElement(keyLabel);
        addElement(
                new WButton("Cycle key", 72, () -> {
                    optionIndex = (optionIndex + 1) % KEY_PICKS.size();
                    refreshKeyLabel();
                }));
        addElement(new WLabel("Pulse + GLFW on new press", 0xFF888888));
        optionIndex = 0;
        refreshKeyLabel();
        setEvaluator(this::evaluate);
    }

    private void refreshKeyLabel() {
        KeyPick p = KEY_PICKS.get(Mth.clamp(optionIndex, 0, KEY_PICKS.size() - 1));
        keyLabel.setText("Listen: " + p.name());
    }

    private void evaluate(WNode n) {
        n.getOutputs().get(0).setValue(0.0);
        n.getOutputs().get(1).setValue(0.0);

        int id = parseInstanceId();
        if (id != prevParsedInstance) {
            prevParsedInstance = id;
            prevPressed = null;
        }
        if (id <= 0) {
            return;
        }
        KeyPick pick = KEY_PICKS.get(Mth.clamp(optionIndex, 0, KEY_PICKS.size() - 1));

        ComputerBlockEntity computer = ComputedGraphExecution.hostOrNull();
        Level level = computer != null ? computer.getLevel() : null;
        if (computer == null || level == null || level.isClientSide()) {
            return;
        }
        BlockPos target = computer.findActivePlacedHardware(SimulatedLinkedTypewriterBridge.LINKED_TYPEWRITER_ID, id);
        if (target == null) {
            prevPressed = null;
            return;
        }
        BlockEntity be = level.getBlockEntity(target);
        List<Integer> raw = SimulatedLinkedTypewriterBridge.getPressedKeys(be);
        Set<Integer> cur = new HashSet<>(raw);

        if (prevPressed == null) {
            prevPressed = new HashSet<>(cur);
            return;
        }

        int fired = -1;
        for (int k : cur) {
            if (!prevPressed.contains(k)) {
                if (pick.glfw() < 0 || pick.glfw() == k) {
                    fired = k;
                    break;
                }
            }
        }
        prevPressed = new HashSet<>(cur);

        if (fired >= 0) {
            n.getOutputs().get(0).setValue(1.0);
            n.getOutputs().get(1).setValue(fired);
        }
    }

    private int parseInstanceId() {
        String s = instanceField.getValue().trim();
        if (s.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("computedTwRxOpt", optionIndex);
        tag.putString("computedTwRxInst", instanceField.getValue());
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("computedTwRxOpt")) {
            optionIndex = Mth.clamp(tag.getInt("computedTwRxOpt"), 0, KEY_PICKS.size() - 1);
        }
        if (tag.contains("computedTwRxInst")) {
            instanceField.setValue(tag.getString("computedTwRxInst"));
        }
        refreshKeyLabel();
        prevPressed = null;
        prevParsedInstance = Integer.MIN_VALUE;
    }
}
