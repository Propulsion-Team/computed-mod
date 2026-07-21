package dev.propulsionteam.computed.client.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Bounded command history with save-point-aware dirty tracking.
 *
 * <p>Revisions identify editor states rather than operations. Consequently, undoing back to the
 * saved revision makes the history clean, while undoing past it makes the history dirty again. The
 * class is intended for a single editor thread.
 *
 * @param <C> editor context mutated by commands
 */
public final class EditorHistory<C> {
    public static final int DEFAULT_CAPACITY = 256;

    private final C context;
    private final int capacity;
    private final Deque<Entry<C>> undoStack = new ArrayDeque<>();
    private final Deque<Entry<C>> redoStack = new ArrayDeque<>();

    private long revisionSequence;
    private long currentRevision;
    private long savedRevision;

    public EditorHistory(C context) {
        this(context, DEFAULT_CAPACITY);
    }

    public EditorHistory(C context, int capacity) {
        this.context = Objects.requireNonNull(context, "context");
        if (capacity < 1) {
            throw new IllegalArgumentException("History capacity must be at least one");
        }
        this.capacity = capacity;
    }

    /** Executes and records a command, discarding the redo branch after successful execution. */
    public void execute(EditorCommand<C> command) {
        Objects.requireNonNull(command, "command");
        long beforeRevision = currentRevision;
        command.execute(context);

        long afterRevision = nextRevision();
        currentRevision = afterRevision;
        redoStack.clear();
        undoStack.addLast(new Entry<>(command, beforeRevision, afterRevision));
        trimUndoStack();
    }

    /** Undoes the latest command, returning {@code false} when no command is available. */
    public boolean undo() {
        Entry<C> entry = undoStack.peekLast();
        if (entry == null) {
            return false;
        }

        entry.command().undo(context);
        undoStack.removeLast();
        redoStack.addLast(entry);
        currentRevision = entry.beforeRevision();
        return true;
    }

    /** Redoes the latest undone command, returning {@code false} when no command is available. */
    public boolean redo() {
        Entry<C> entry = redoStack.peekLast();
        if (entry == null) {
            return false;
        }

        entry.command().redo(context);
        redoStack.removeLast();
        undoStack.addLast(entry);
        trimUndoStack();
        currentRevision = entry.afterRevision();
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public Optional<String> undoDescription() {
        Entry<C> entry = undoStack.peekLast();
        return entry == null ? Optional.empty() : Optional.of(entry.command().description());
    }

    public Optional<String> redoDescription() {
        Entry<C> entry = redoStack.peekLast();
        return entry == null ? Optional.empty() : Optional.of(entry.command().description());
    }

    /** Marks the current state as successfully persisted. */
    public void markSaved() {
        savedRevision = currentRevision;
    }

    public boolean isDirty() {
        return currentRevision != savedRevision;
    }

    /** Returns the current revision only when it differs from the saved revision. */
    public OptionalLong dirtyRevision() {
        return isDirty() ? OptionalLong.of(currentRevision) : OptionalLong.empty();
    }

    public long currentRevision() {
        return currentRevision;
    }

    public long savedRevision() {
        return savedRevision;
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    public int capacity() {
        return capacity;
    }

    public C context() {
        return context;
    }

    /**
     * Drops undo and redo commands without changing the current or saved revision. This is useful
     * after replacing the backing graph while retaining its dirty state.
     */
    public void discardCommands() {
        undoStack.clear();
        redoStack.clear();
    }

    private long nextRevision() {
        if (revisionSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Editor revision counter exhausted");
        }
        return ++revisionSequence;
    }

    private void trimUndoStack() {
        while (undoStack.size() > capacity) {
            undoStack.removeFirst();
        }
    }

    private record Entry<C>(EditorCommand<C> command, long beforeRevision, long afterRevision) {}
}
