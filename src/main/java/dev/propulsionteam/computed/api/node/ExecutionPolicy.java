package dev.propulsionteam.computed.api.node;

/** Determines when a compiled graph schedules a node for evaluation. */
public enum ExecutionPolicy {
    /** Evaluate when an input value or property changes. */
    INPUT_DRIVEN,
    /** Evaluate once for each Minecraft game tick. */
    EVERY_GAME_TICK,
    /** Evaluate during every graph step, including multiple steps in one game tick. */
    EVERY_GRAPH_STEP
}
