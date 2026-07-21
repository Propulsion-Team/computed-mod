package dev.propulsionteam.computed.api.node;

/** Evaluates a node using immutable prior state and returns the state committed after the graph step. */
@FunctionalInterface
public interface NodeExecutor<S> {
    S execute(S priorState, NodeExecutionContext context) throws Exception;
}
