package dev.propulsionteam.computed.client.editor;

/** A finite point in graph space. */
public record GraphPoint(double x, double y) {
    public GraphPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Graph coordinates must be finite");
        }
    }

    public GraphPoint lerp(GraphPoint other, double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Interpolation amount must be finite");
        }
        return new GraphPoint(x + (other.x - x) * amount, y + (other.y - y) * amount);
    }

    public double distanceSquared(GraphPoint other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }
}
