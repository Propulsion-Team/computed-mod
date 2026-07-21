package dev.propulsionteam.computed.api.node;

/** Direction of data flow through a node port. */
public enum PortDirection {
    INPUT,
    OUTPUT;

    public boolean canConnectTo(PortDirection other) {
        return other != null && this != other;
    }
}
