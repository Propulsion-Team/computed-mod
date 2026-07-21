package dev.propulsionteam.computed.client.editor;

/**
 * A reversible editor operation.
 *
 * <p>Implementations should make both {@link #execute(Object)} and {@link #undo(Object)} atomic. A
 * command instance may be executed again after it has been undone.
 *
 * @param <C> editor context mutated by the command
 */
public interface EditorCommand<C> {
    /** Applies this command to the editor context. */
    void execute(C context);

    /** Restores the editor context to its state immediately before execution. */
    void undo(C context);

    /** Reapplies an undone command. Commands with special redo behavior may override this method. */
    default void redo(C context) {
        execute(context);
    }

    /** Human-readable text suitable for an undo/redo menu. */
    default String description() {
        return getClass().getSimpleName();
    }
}
