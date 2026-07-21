package dev.propulsionteam.computed.api.node;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Runtime services and typed I/O for one node evaluation. */
public interface NodeExecutionContext {
    NodePropertyBag properties();

    <T> T input(PortKey<T> key);

    <T> void output(PortKey<T> key, T value);

    boolean isInputConnected(PortKey<?> key);

    long gameTick();

    long graphStep();

    /** True while the client is evaluating a read-only editor preview. */
    boolean isPreview();

    /** Empty during client previews and other evaluations that have no server world. */
    Optional<ServerLevel> level();

    Optional<BlockPos> origin();

    /** False for previews and whenever runtime side effects are intentionally suppressed. */
    boolean sideEffectsAllowed();

    DiagnosticSink diagnostics();

    default void report(NodeDiagnostic diagnostic) {
        diagnostics().report(Objects.requireNonNull(diagnostic, "diagnostic"));
    }

    /**
     * Runs a world mutation only when the runtime permits side effects. This is the preferred way for
     * addon nodes to perform effects because previews cannot accidentally invoke the action.
     */
    default boolean runSideEffect(Consumer<? super ServerLevel> action) {
        Objects.requireNonNull(action, "action");
        if (!sideEffectsAllowed()) {
            return false;
        }
        Optional<ServerLevel> level = level();
        if (level.isEmpty()) {
            return false;
        }
        action.accept(level.get());
        return true;
    }
}
