package dev.propulsionteam.computed.api.node.client;

/** Optional custom body renderer for a node; unregistered types use generic property controls. */
@FunctionalInterface
public interface NodePresentation {
    void render(NodePresentationContext context);
}
