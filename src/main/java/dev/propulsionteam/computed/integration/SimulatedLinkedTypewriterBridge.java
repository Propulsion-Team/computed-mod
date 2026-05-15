package dev.propulsionteam.computed.integration;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Optional Simulated (Create Aeronautics) linked typewriter: reads {@code simulated:linked_typewriter} block
 * entities via reflection so Computed does not register that block and loads without Simulated.
 */
public final class SimulatedLinkedTypewriterBridge {
    public static final ResourceLocation LINKED_TYPEWRITER_ID =
            ResourceLocation.fromNamespaceAndPath("simulated", "linked_typewriter");

    private static final String TYPEWRITER_BE =
            "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity";

    private static Boolean modPresent;

    private SimulatedLinkedTypewriterBridge() {}

    public static boolean isSimulatedLoaded() {
        if (modPresent == null) {
            modPresent = ModList.get().isLoaded("simulated");
        }
        return modPresent;
    }

    public static boolean isLinkedTypewriterBlockEntity(BlockEntity be) {
        return be != null && TYPEWRITER_BE.equals(be.getClass().getName());
    }

    /**
     * Currently held GLFW key codes on the typewriter (may be empty).
     */
    public static java.util.List<Integer> getPressedKeys(BlockEntity be) {
        if (!isSimulatedLoaded() || !isLinkedTypewriterBlockEntity(be)) {
            return java.util.List.of();
        }
        try {
            Method m = be.getClass().getMethod("getPressedKeys");
            Object o = m.invoke(be);
            if (o instanceof java.util.List<?> list) {
                java.util.List<Integer> out = new java.util.ArrayList<>(list.size());
                for (Object e : list) {
                    if (e instanceof Integer i) {
                        out.add(i);
                    }
                }
                return java.util.List.copyOf(out);
            }
        } catch (Throwable ignored) {
        }
        return java.util.List.of();
    }
}
