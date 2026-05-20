package dev.propulsionteam.computed.content.nodes.vanilla;

import net.minecraft.core.Direction;

public enum RelativeFace {
    FRONT("Front"),
    BACK("Back"),
    LEFT("Left"),
    RIGHT("Right"),
    TOP("Top"),
    BOTTOM("Bottom");

    private final String displayName;

    RelativeFace(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Resolve to a world {@link Direction} given the block's horizontal facing (front of block points to {@code facing}). */
    public Direction toWorld(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /** Parse from a case-insensitive string; returns {@code null} if unrecognised. */
    public static RelativeFace byName(String name) {
        if (name == null) return null;
        for (RelativeFace f : values()) {
            if (f.name().equalsIgnoreCase(name) || f.displayName.equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    /** Best-effort migration from old world-absolute {@link Direction} dropdown values. */
    public static RelativeFace fromLegacyDirection(Direction d) {
        return switch (d) {
            case NORTH -> FRONT;
            case SOUTH -> BACK;
            case WEST -> LEFT;
            case EAST -> RIGHT;
            case UP -> TOP;
            case DOWN -> BOTTOM;
        };
    }
}
