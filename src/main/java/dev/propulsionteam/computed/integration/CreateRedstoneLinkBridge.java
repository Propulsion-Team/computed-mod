package dev.propulsionteam.computed.integration;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkReceiverNode;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkSenderNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Registers virtual Create redstone link actors for Computed graph nodes, using reflection so Computed
 * still loads when Create is absent.
 */
public final class CreateRedstoneLinkBridge {
    private static final String CREATE = "com.simibubi.create.Create";
    private static final String HANDLER = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler";
    private static final String FREQ = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency";
    private static final String IRL = "com.simibubi.create.content.redstone.link.IRedstoneLinkable";
    private static final String COUPLE = "net.createmod.catnip.data.Couple";

    private static Boolean createPresent;

    private final List<Registered> registered = new ArrayList<>();

    private record Registered(Object proxy, boolean transmit) {}

    /** Per-node bookkeeping for receivers so we can clear their per-mirror map on resync. */
    private final List<CreateRedstoneLinkReceiverNode> activeReceivers = new ArrayList<>();

    public static boolean isCreateLoaded() {
        if (createPresent != null) {
            return createPresent;
        }
        createPresent = ModList.get().isLoaded("create");
        return createPresent;
    }

    public void clear(Level level) {
        for (CreateRedstoneLinkReceiverNode r : activeReceivers) {
            r.clearMirrors();
        }
        activeReceivers.clear();
        if (!isCreateLoaded() || level == null || level.isClientSide) {
            registered.clear();
            return;
        }
        Object handler = getHandler();
        if (handler == null) {
            registered.clear();
            return;
        }
        try {
            Method remove = handler.getClass().getMethod("removeFromNetwork", net.minecraft.world.level.LevelAccessor.class, Class.forName(IRL));
            for (Registered r : registered) {
                remove.invoke(handler, level, r.proxy);
            }
        } catch (Throwable ignored) {
        }
        registered.clear();
    }

    public void syncFromGraph(Level level, ComputerBlockEntity computer, WGraph graph) {
        clear(level);
        if (!isCreateLoaded() || level == null || level.isClientSide || graph == null) {
            return;
        }
        Object handler = getHandler();
        if (handler == null) {
            return;
        }
        collect(level, computer, graph, handler);
    }

    private void collect(Level level, ComputerBlockEntity computer, WGraph graph, Object handler) {
        List<CreateRedstoneLinkSenderNode> senders = new ArrayList<>();
        List<CreateRedstoneLinkReceiverNode> receivers = new ArrayList<>();
        gather(graph, senders, receivers);
        for (CreateRedstoneLinkReceiverNode r : receivers) {
            tryAddReceiver(level, computer, handler, r);
        }
        for (CreateRedstoneLinkSenderNode s : senders) {
            tryAddSender(level, computer, handler, s);
        }
    }

    private void gather(WGraph graph, List<CreateRedstoneLinkSenderNode> senders, List<CreateRedstoneLinkReceiverNode> receivers) {
        for (WNode n : graph.getNodes()) {
            if (n instanceof CreateRedstoneLinkSenderNode s) {
                senders.add(s);
            } else if (n instanceof CreateRedstoneLinkReceiverNode r) {
                receivers.add(r);
            } else if (n instanceof FunctionCardNode fc) {
                gather(fc.getInnerGraph(), senders, receivers);
            }
        }
    }

    private void tryAddSender(Level level, ComputerBlockEntity computer, Object handler, CreateRedstoneLinkSenderNode s) {
        ItemStack a = s.redFrequency();
        ItemStack b = s.blueFrequency();
        Object couple = makeCouple(a, b);
        if (couple == null) {
            return;
        }
        Object proxy = makeProxy(computer, computer.getBlockPos(), couple, true, s::readTransmitStrength, (key, p) -> {});
        if (proxy == null) {
            return;
        }
        invokeAdd(handler, level, proxy);
        registered.add(new Registered(proxy, true));
        for (BlockPos mirrorPos : sableMirrorAnchors(level, computer.getBlockPos())) {
            Object mirror = makeProxy(computer, mirrorPos, couple, true, s::readTransmitStrength, (key, p) -> {});
            if (mirror != null) {
                invokeAdd(handler, level, mirror);
                registered.add(new Registered(mirror, true));
            }
        }
    }

    private void tryAddReceiver(Level level, ComputerBlockEntity computer, Object handler, CreateRedstoneLinkReceiverNode r) {
        ItemStack a = r.redFrequency();
        ItemStack b = r.blueFrequency();
        Object couple = makeCouple(a, b);
        if (couple == null) {
            return;
        }
        activeReceivers.add(r);
        Object proxy = makeProxy(computer, computer.getBlockPos(), couple, false, () -> 0, (key, p) -> r.setLinkInputStrengthFor(key, p));
        if (proxy == null) {
            return;
        }
        invokeAdd(handler, level, proxy);
        registered.add(new Registered(proxy, false));
        for (BlockPos mirrorPos : sableMirrorAnchors(level, computer.getBlockPos())) {
            Object mirror = makeProxy(computer, mirrorPos, couple, false, () -> 0, (key, p) -> r.setLinkInputStrengthFor(key, p));
            if (mirror != null) {
                invokeAdd(handler, level, mirror);
                registered.add(new Registered(mirror, false));
            }
        }
    }

    /**
     * Anchors for mirror proxies when Sable is loaded. Create's redstone link network groups by chunk/range,
     * so a single proxy at the computer's position can't reach links living on sub-levels.
     * We register one mirror anchored inside each sub-level on the same {@link Level} other than the one
     * containing the computer (if any). The host-world side is already covered by the primary proxy when
     * the computer is in the host world.
     */
    private static List<BlockPos> sableMirrorAnchors(Level level, BlockPos computerPos) {
        if (!SableBridge.isLoaded()) {
            return List.of();
        }
        SableBridge.SubLevelHandle computerSub = SableBridge.containing(level, computerPos);
        BlockPos computerSubAnchor = computerSub == null ? null : SableBridge.representativePos(computerSub);
        List<BlockPos> out = new ArrayList<>();
        for (SableBridge.SubLevelHandle sl : SableBridge.allSubLevels(level)) {
            BlockPos anchor = SableBridge.representativePos(sl);
            if (anchor == null) {
                continue;
            }
            if (computerSubAnchor != null && anchor.equals(computerSubAnchor)) {
                continue;
            }
            out.add(anchor);
        }
        return out;
    }

    private static void invokeAdd(Object handler, Level level, Object proxy) {
        try {
            Method add = handler.getClass().getMethod("addToNetwork", net.minecraft.world.level.LevelAccessor.class, Class.forName(IRL));
            add.invoke(handler, level, proxy);
        } catch (Throwable ignored) {
        }
    }

    public void pushTransmitters(Level level) {
        if (!isCreateLoaded() || level == null || level.isClientSide) {
            return;
        }
        Object handler = getHandler();
        if (handler == null) {
            return;
        }
        try {
            Method update = handler.getClass().getMethod("updateNetworkOf", net.minecraft.world.level.LevelAccessor.class, Class.forName(IRL));
            for (Registered r : registered) {
                if (r.transmit) {
                    update.invoke(handler, level, r.proxy);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getHandler() {
        try {
            Class<?> create = Class.forName(CREATE);
            return create.getField("REDSTONE_LINK_NETWORK_HANDLER").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object frequencyOf(ItemStack stack) {
        try {
            Class<?> fc = Class.forName(FREQ);
            Method of = fc.getMethod("of", ItemStack.class);
            return of.invoke(null, stack);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object makeCouple(ItemStack a, ItemStack b) {
        try {
            Object fa = frequencyOf(a.copyWithCount(1));
            Object fb = frequencyOf(b.copyWithCount(1));
            if (fa == null || fb == null) {
                return null;
            }
            Class<?> coupleC = Class.forName(COUPLE);
            Method create = coupleC.getMethod("create", Object.class, Object.class);
            return create.invoke(null, fa, fb);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object makeProxy(
            ComputerBlockEntity computer,
            BlockPos location,
            Object couple,
            boolean transmit,
            java.util.function.IntSupplier transmitLevel,
            java.util.function.BiConsumer<Object, Integer> receiveConsumer) {
        try {
            Class<?> iface = Class.forName(IRL);
            ClassLoader cl = iface.getClassLoader();
            BlockPos pinnedLocation = location.immutable();
            InvocationHandler h =
                    (Object proxy, Method method, Object[] args) -> {
                        String name = method.getName();
                        if ("getTransmittedStrength".equals(name)) {
                            return transmit
                                    ? net.minecraft.util.Mth.clamp(transmitLevel.getAsInt(), 0, 15)
                                    : 0;
                        }
                        if ("setReceivedStrength".equals(name)) {
                            if (!transmit && args != null && args.length > 0 && args[0] instanceof Number num) {
                                receiveConsumer.accept(proxy, num.intValue());
                            }
                            return null;
                        }
                        if ("isListening".equals(name)) {
                            return !transmit;
                        }
                        if ("isAlive".equals(name)) {
                            Level lvl = computer.getLevel();
                            return lvl != null
                                    && !computer.isRemoved()
                                    && lvl.isLoaded(computer.getBlockPos())
                                    && lvl.getBlockEntity(computer.getBlockPos()) == computer;
                        }
                        if ("getNetworkKey".equals(name)) {
                            return couple;
                        }
                        if ("getLocation".equals(name)) {
                            return pinnedLocation;
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(name)) {
                            return "ComputedVirtualLink";
                        }
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class) return false;
                        if (ret == byte.class) return (byte) 0;
                        if (ret == short.class) return (short) 0;
                        if (ret == int.class) return 0;
                        if (ret == long.class) return 0L;
                        if (ret == float.class) return 0f;
                        if (ret == double.class) return 0d;
                        if (ret == char.class) return (char) 0;
                        return null;
                    };
            return Proxy.newProxyInstance(cl, new Class<?>[] {iface}, h);
        } catch (Throwable t) {
            return null;
        }
    }

    public CreateRedstoneLinkBridge() {}
}
