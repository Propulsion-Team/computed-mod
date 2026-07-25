package dev.propulsionteam.computed.integration.create;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class CreateRedstoneLinks {
    private static final String CREATE = "com.simibubi.create.Create";
    private static final String HANDLER =
            "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler";
    private static final String FREQUENCY =
            "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency";
    private static final String LINKABLE =
            "com.simibubi.create.content.redstone.link.IRedstoneLinkable";
    private static final String COUPLE = "net.createmod.catnip.data.Couple";
    private static final Map<ComputerBlockEntity, Map<Key, Actor>> ACTORS = new WeakHashMap<>();

    private CreateRedstoneLinks() {}

    static synchronized int receive(
            ComputerBlockEntity computer,
            UUID nodeId,
            String first,
            String second) {
        Key key = new Key(nodeId, false);
        Actor actor = actor(computer, key, first, second);
        return actor == null ? 0 : actor.received;
    }

    static synchronized void transmit(
            ComputerBlockEntity computer,
            UUID nodeId,
            String first,
            String second,
            int strength) {
        Key key = new Key(nodeId, true);
        Actor actor = actor(computer, key, first, second);
        if (actor == null) {
            return;
        }
        int clamped = Mth.clamp(strength, 0, 15);
        if (actor.transmitted != clamped) {
            actor.transmitted = clamped;
            update(computer.getLevel(), actor.proxy);
        }
    }

    static synchronized void clear(ComputerBlockEntity computer) {
        Map<Key, Actor> actors = ACTORS.remove(computer);
        if (actors == null || computer.getLevel() == null) {
            return;
        }
        Object handler = handler();
        if (handler == null) {
            return;
        }
        for (Actor actor : actors.values()) {
            remove(handler, computer.getLevel(), actor.proxy);
        }
    }

    private static Actor actor(
            ComputerBlockEntity computer,
            Key key,
            String first,
            String second) {
        if (computer.getLevel() == null || computer.getLevel().isClientSide) {
            return null;
        }
        Object networkKey = networkKey(first, second);
        if (networkKey == null) {
            return null;
        }
        Map<Key, Actor> actors = ACTORS.computeIfAbsent(computer, ignored -> new LinkedHashMap<>());
        Actor current = actors.get(key);
        String signature = first + '\u0000' + second;
        if (current != null && current.signature.equals(signature)) {
            return current;
        }
        if (current != null) {
            Object handler = handler();
            if (handler != null) {
                remove(handler, computer.getLevel(), current.proxy);
            }
        }
        Actor created = new Actor(signature);
        Object proxy = proxy(
                computer,
                networkKey,
                key.transmit(),
                () -> created.transmitted,
                value -> created.received = Mth.clamp(value, 0, 15));
        if (proxy == null) {
            return null;
        }
        created.proxy = proxy;
        Object handler = handler();
        if (handler == null || !add(handler, computer.getLevel(), proxy)) {
            return null;
        }
        actors.put(key, created);
        return created;
    }

    private static Object proxy(
            ComputerBlockEntity computer,
            Object networkKey,
            boolean transmit,
            IntSupplier transmitted,
            IntConsumer received) {
        try {
            Class<?> type = Class.forName(LINKABLE);
            BlockPos position = computer.getBlockPos().immutable();
            InvocationHandler invocation = (proxy, method, arguments) -> switch (method.getName()) {
                case "getTransmittedStrength" -> transmit ? Mth.clamp(transmitted.getAsInt(), 0, 15) : 0;
                case "setReceivedStrength" -> {
                    if (!transmit && arguments != null && arguments[0] instanceof Number number) {
                        received.accept(number.intValue());
                    }
                    yield null;
                }
                case "isListening" -> !transmit;
                case "isAlive" -> computer.getLevel() != null
                        && !computer.isRemoved()
                        && computer.getLevel().getBlockEntity(computer.getBlockPos()) == computer;
                case "getNetworkKey" -> networkKey;
                case "getLocation" -> position;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "ComputedLuaRedstoneLink";
                default -> defaultValue(method.getReturnType());
            };
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocation);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object networkKey(String first, String second) {
        try {
            Object firstFrequency = frequency(first);
            Object secondFrequency = frequency(second);
            if (firstFrequency == null || secondFrequency == null) {
                return null;
            }
            return Class.forName(COUPLE)
                    .getMethod("create", Object.class, Object.class)
                    .invoke(null, firstFrequency, secondFrequency);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object frequency(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            return Class.forName(FREQUENCY).getMethod("of", ItemStack.class).invoke(null, stack);
        } catch (RuntimeException | ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object handler() {
        try {
            return Class.forName(CREATE).getField("REDSTONE_LINK_NETWORK_HANDLER").get(null);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean add(Object handler, Level level, Object proxy) {
        try {
            handler.getClass()
                    .getMethod(
                            "addToNetwork",
                            net.minecraft.world.level.LevelAccessor.class,
                            Class.forName(LINKABLE))
                    .invoke(handler, level, proxy);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static void remove(Object handler, Level level, Object proxy) {
        try {
            handler.getClass()
                    .getMethod(
                            "removeFromNetwork",
                            net.minecraft.world.level.LevelAccessor.class,
                            Class.forName(LINKABLE))
                    .invoke(handler, level, proxy);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void update(Level level, Object proxy) {
        Object handler = handler();
        if (handler == null || level == null) {
            return;
        }
        try {
            handler.getClass()
                    .getMethod(
                            "updateNetworkOf",
                            net.minecraft.world.level.LevelAccessor.class,
                            Class.forName(LINKABLE))
                    .invoke(handler, level, proxy);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private record Key(UUID nodeId, boolean transmit) {}

    private static final class Actor {
        private final String signature;
        private Object proxy;
        private int transmitted;
        private int received;

        private Actor(String signature) {
            this.signature = signature;
        }
    }
}
