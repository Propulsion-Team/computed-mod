package dev.propulsionteam.computed.content.blocks;

import org.jetbrains.annotations.Nullable;

/** Thread-local host for node evaluators running inside {@link ComputerBlockEntity}'s graph tick. */
public final class ComputedGraphExecution {
    private static final ThreadLocal<ComputerBlockEntity> HOST = new ThreadLocal<>();

    private ComputedGraphExecution() {}

    public static void withHost(ComputerBlockEntity host, Runnable runnable) {
        ComputerBlockEntity previous = HOST.get();
        HOST.set(host);
        try {
            runnable.run();
        } finally {
            if (previous != null) {
                HOST.set(previous);
            } else {
                HOST.remove();
            }
        }
    }

    /** Runs without inheriting a server host, used to make preview/validation evaluations side-effect free. */
    public static void withoutHost(Runnable runnable) {
        ComputerBlockEntity previous = HOST.get();
        HOST.remove();
        try {
            runnable.run();
        } finally {
            if (previous != null) {
                HOST.set(previous);
            }
        }
    }

    @Nullable
    public static ComputerBlockEntity hostOrNull() {
        return HOST.get();
    }
}
