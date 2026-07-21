package dev.propulsionteam.computed.client.editor;

/** An immutable, inclusive axis-aligned rectangle in graph space. */
public record GraphRect(double minX, double minY, double maxX, double maxY) {
    public GraphRect {
        if (!Double.isFinite(minX)
                || !Double.isFinite(minY)
                || !Double.isFinite(maxX)
                || !Double.isFinite(maxY)) {
            throw new IllegalArgumentException("Graph bounds must be finite");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Minimum bounds must not exceed maximum bounds");
        }
    }

    public static GraphRect fromPositionAndSize(double x, double y, double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 0.0 || height < 0.0) {
            throw new IllegalArgumentException("Graph rectangle size must be finite and non-negative");
        }
        return new GraphRect(x, y, x + width, y + height);
    }

    public static GraphRect fromPoints(GraphPoint first, GraphPoint second) {
        return new GraphRect(
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()));
    }

    public static GraphRect around(GraphPoint point, double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Radius must be finite and non-negative");
        }
        return new GraphRect(point.x() - radius, point.y() - radius, point.x() + radius, point.y() + radius);
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public GraphPoint center() {
        return new GraphPoint((minX + maxX) * 0.5, (minY + maxY) * 0.5);
    }

    public boolean contains(GraphPoint point) {
        return contains(point.x(), point.y());
    }

    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(GraphRect other) {
        return maxX >= other.minX && minX <= other.maxX && maxY >= other.minY && minY <= other.maxY;
    }

    public GraphRect expanded(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Expansion must be finite and non-negative");
        }
        return new GraphRect(minX - amount, minY - amount, maxX + amount, maxY + amount);
    }

    public GraphRect union(GraphRect other) {
        return new GraphRect(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY));
    }
}
