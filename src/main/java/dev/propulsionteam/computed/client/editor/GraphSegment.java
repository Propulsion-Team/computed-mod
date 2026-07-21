package dev.propulsionteam.computed.client.editor;

import java.util.Objects;

/** An immutable line segment in graph space. */
public record GraphSegment(GraphPoint start, GraphPoint end) {
    public GraphSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    public GraphRect bounds() {
        return GraphRect.fromPoints(start, end);
    }

    public double lengthSquared() {
        return start.distanceSquared(end);
    }

    /** Squared distance from a point to the closest point on this segment. */
    public double distanceSquared(GraphPoint point) {
        Objects.requireNonNull(point, "point");
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return start.distanceSquared(point);
        }
        double projection = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / lengthSquared;
        double amount = Math.max(0.0, Math.min(1.0, projection));
        double closestX = start.x() + dx * amount;
        double closestY = start.y() + dy * amount;
        double pointDx = point.x() - closestX;
        double pointDy = point.y() - closestY;
        return pointDx * pointDx + pointDy * pointDy;
    }
}
