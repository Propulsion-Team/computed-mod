package dev.propulsionteam.computed.integration.create;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class CreateKineticAccess {
    private static final String KINETIC =
            "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static volatile Class<?> kineticClass;
    private static volatile boolean resolved;

    private CreateKineticAccess() {}

    static double speed(Level level, BlockPos pos) {
        return invoke(blockEntity(level, pos), "getSpeed");
    }

    static double stress(Level level, BlockPos pos) {
        Object blockEntity = blockEntity(level, pos);
        return invoke(blockEntity, "calculateStressApplied")
                * Math.abs(invoke(blockEntity, "getSpeed"));
    }

    static double capacity(Level level, BlockPos pos) {
        Object blockEntity = blockEntity(level, pos);
        return invoke(blockEntity, "calculateAddedStressCapacity")
                * Math.abs(invoke(blockEntity, "getSpeed"));
    }

    private static Object blockEntity(Level level, BlockPos pos) {
        Class<?> type = kineticClass();
        if (level == null || pos == null || type == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && type.isInstance(blockEntity) ? blockEntity : null;
    }

    private static double invoke(Object target, String name) {
        if (target == null) {
            return 0;
        }
        String key = target.getClass().getName() + '#' + name;
        try {
            Method method = METHODS.computeIfAbsent(key, ignored -> find(target.getClass(), name));
            Object result = method.invoke(target);
            return result instanceof Number number ? number.doubleValue() : 0;
        } catch (RuntimeException | ReflectiveOperationException exception) {
            return 0;
        }
    }

    private static Method find(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException exception) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Create kinetic method is unavailable: " + name);
    }

    private static Class<?> kineticClass() {
        if (!resolved) {
            synchronized (CreateKineticAccess.class) {
                if (!resolved) {
                    try {
                        kineticClass = Class.forName(KINETIC);
                    } catch (ClassNotFoundException ignored) {
                        kineticClass = null;
                    }
                    resolved = true;
                }
            }
        }
        return kineticClass;
    }
}
