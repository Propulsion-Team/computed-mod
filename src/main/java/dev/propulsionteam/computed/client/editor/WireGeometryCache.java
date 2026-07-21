package dev.propulsionteam.computed.client.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bounded LRU cache of tessellated wire geometry.
 *
 * <p>The caller owns geometry revisions: moving either endpoint or changing routing must increment
 * the revision. Zoom buckets avoid rebuilding geometry for every fractional zoom change.
 *
 * @param <K> stable connection key
 */
public final class WireGeometryCache<K> {
    public static final int DEFAULT_MAX_ENTRIES = 8_192;
    private static final int BUCKETS_PER_OCTAVE = 4;

    private final int maxEntries;
    private final LinkedHashMap<CacheKey<K>, WireGeometry> cache = new LinkedHashMap<>(128, 0.75f, true);
    private long hitCount;
    private long missCount;

    public WireGeometryCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public WireGeometryCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Wire cache capacity must be at least one");
        }
        this.maxEntries = maxEntries;
    }

    /**
     * Gets or constructs an immutable polyline. The supplier must return at least two finite graph
     * points and is called only on a cache miss.
     */
    public WireGeometry getOrCompute(
            K connectionKey, long geometryRevision, int zoomBucket, Supplier<? extends List<GraphPoint>> points) {
        Objects.requireNonNull(connectionKey, "connectionKey");
        Objects.requireNonNull(points, "points");
        CacheKey<K> key = new CacheKey<>(connectionKey, geometryRevision, zoomBucket);
        WireGeometry cached = cache.get(key);
        if (cached != null) {
            hitCount++;
            return cached;
        }

        WireGeometry computed = WireGeometry.fromPoints(points.get());
        missCount++;
        cache.put(key, computed);
        trim();
        return computed;
    }

    /** Gets or tessellates the editor's default horizontal cubic-bezier wire. */
    public WireGeometry getBezier(
            K connectionKey, long geometryRevision, int zoomBucket, GraphPoint start, GraphPoint end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return getOrCompute(
                connectionKey,
                geometryRevision,
                zoomBucket,
                () -> tessellateBezier(start, end, zoomBucket));
    }

    /** Quantizes a positive zoom into four stable buckets per doubling. */
    public static int zoomBucket(double zoom) {
        if (!Double.isFinite(zoom) || zoom <= 0.0) {
            throw new IllegalArgumentException("Zoom must be finite and greater than zero");
        }
        double bucket = Math.floor(Math.log(zoom) / Math.log(2.0) * BUCKETS_PER_OCTAVE);
        if (bucket < Integer.MIN_VALUE || bucket > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Zoom is outside the supported range");
        }
        return (int) bucket;
    }

    /** Produces the default wire polyline without interacting with the cache. */
    public static List<GraphPoint> tessellateBezier(GraphPoint start, GraphPoint end, int zoomBucket) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double directDistance = Math.hypot(dx, dy);
        double handleLength = Math.max(24.0, Math.max(Math.abs(dx) * 0.5, directDistance * 0.35));
        GraphPoint controlOne = new GraphPoint(start.x() + handleLength, start.y());
        GraphPoint controlTwo = new GraphPoint(end.x() - handleLength, end.y());

        double controlLength = Math.hypot(controlOne.x() - start.x(), controlOne.y() - start.y())
                + Math.hypot(controlTwo.x() - controlOne.x(), controlTwo.y() - controlOne.y())
                + Math.hypot(end.x() - controlTwo.x(), end.y() - controlTwo.y());
        double zoomScale = Math.pow(2.0, Math.max(-16, Math.min(16, zoomBucket)) / (double) BUCKETS_PER_OCTAVE);
        int segmentCount = Math.max(8, Math.min(96, (int) Math.ceil(controlLength * zoomScale / 16.0)));

        List<GraphPoint> points = new ArrayList<>(segmentCount + 1);
        for (int index = 0; index <= segmentCount; index++) {
            double t = index / (double) segmentCount;
            double inverse = 1.0 - t;
            double x = inverse * inverse * inverse * start.x()
                    + 3.0 * inverse * inverse * t * controlOne.x()
                    + 3.0 * inverse * t * t * controlTwo.x()
                    + t * t * t * end.x();
            double y = inverse * inverse * inverse * start.y()
                    + 3.0 * inverse * inverse * t * controlOne.y()
                    + 3.0 * inverse * t * t * controlTwo.y()
                    + t * t * t * end.y();
            points.add(new GraphPoint(x, y));
        }
        return List.copyOf(points);
    }

    public void invalidate(K connectionKey) {
        Objects.requireNonNull(connectionKey, "connectionKey");
        cache.keySet().removeIf(key -> key.connectionKey().equals(connectionKey));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public CacheStats stats() {
        return new CacheStats(hitCount, missCount, cache.size(), maxEntries);
    }

    private void trim() {
        while (cache.size() > maxEntries) {
            CacheKey<K> eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    public record CacheKey<K>(K connectionKey, long geometryRevision, int zoomBucket) {
        public CacheKey {
            Objects.requireNonNull(connectionKey, "connectionKey");
        }
    }

    public record CacheStats(long hits, long misses, int size, int capacity) {}

    /** Immutable points, line segments, and bounds produced for a connection. */
    public record WireGeometry(List<GraphPoint> points, List<GraphSegment> segments, GraphRect bounds) {
        public WireGeometry {
            points = List.copyOf(points);
            segments = List.copyOf(segments);
            Objects.requireNonNull(bounds, "bounds");
            if (points.size() < 2) {
                throw new IllegalArgumentException("Wire geometry requires at least two points");
            }
            if (segments.size() != points.size() - 1) {
                throw new IllegalArgumentException("Wire segments must connect every adjacent point");
            }
        }

        public static WireGeometry fromPoints(List<GraphPoint> sourcePoints) {
            Objects.requireNonNull(sourcePoints, "sourcePoints");
            List<GraphPoint> points = List.copyOf(sourcePoints);
            if (points.size() < 2) {
                throw new IllegalArgumentException("Wire geometry requires at least two points");
            }

            List<GraphSegment> segments = new ArrayList<>(points.size() - 1);
            GraphRect bounds = GraphRect.fromPoints(points.getFirst(), points.getFirst());
            for (int index = 1; index < points.size(); index++) {
                GraphPoint previous = points.get(index - 1);
                GraphPoint current = points.get(index);
                segments.add(new GraphSegment(previous, current));
                bounds = bounds.union(GraphRect.fromPoints(current, current));
            }
            return new WireGeometry(points, segments, bounds);
        }
    }
}
