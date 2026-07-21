package dev.propulsionteam.computed.client.editor;

import java.util.ArrayList;
import java.util.List;

/** Deterministic 1,000-node/5,000-wire-segment fixture for editor performance checks. */
public final class EditorPerformanceFixture {
    public static final int NODE_COUNT = 1_000;
    public static final int WIRE_SEGMENT_COUNT = 5_000;
    public static final long DEFAULT_SEED = 0x434f4d5055544544L;

    private EditorPerformanceFixture() {}

    public static Fixture create() {
        return create(DEFAULT_SEED);
    }

    public static Fixture create(long seed) {
        List<NodeRect> nodes = new ArrayList<>(NODE_COUNT);
        int columns = 40;
        for (int id = 0; id < NODE_COUNT; id++) {
            int column = id % columns;
            int row = id / columns;
            long mixed = mix(seed + id * 0x9e3779b97f4a7c15L);
            double jitterX = unit(mixed) * 24.0 - 12.0;
            double jitterY = unit(mix(mixed)) * 18.0 - 9.0;
            double width = 112.0 + Math.floor(unit(mix(mixed + 1L)) * 48.0);
            double height = 62.0 + Math.floor(unit(mix(mixed + 2L)) * 44.0);
            GraphRect bounds = GraphRect.fromPositionAndSize(
                    column * 190.0 + jitterX, row * 125.0 + jitterY, width, height);
            nodes.add(new NodeRect(id, bounds));
        }

        List<WireSegment> wireSegments = new ArrayList<>(WIRE_SEGMENT_COUNT);
        for (int id = 0; id < WIRE_SEGMENT_COUNT; id++) {
            long mixed = mix(seed ^ (id * 0xd1b54a32d192ed03L));
            int sourceId = Math.floorMod((int) mixed, NODE_COUNT);
            int targetId = Math.floorMod((int) (mixed >>> 32), NODE_COUNT);
            if (targetId == sourceId) {
                targetId = (targetId + 1 + id % 31) % NODE_COUNT;
            }
            GraphRect source = nodes.get(sourceId).bounds();
            GraphRect target = nodes.get(targetId).bounds();
            GraphPoint start = new GraphPoint(source.maxX(), source.center().y());
            GraphPoint end = new GraphPoint(target.minX(), target.center().y());
            wireSegments.add(new WireSegment(id, sourceId, targetId, new GraphSegment(start, end)));
        }
        return new Fixture(nodes, wireSegments);
    }

    public record Fixture(List<NodeRect> nodes, List<WireSegment> wireSegments) {
        public Fixture {
            nodes = List.copyOf(nodes);
            wireSegments = List.copyOf(wireSegments);
            if (nodes.size() != NODE_COUNT || wireSegments.size() != WIRE_SEGMENT_COUNT) {
                throw new IllegalArgumentException("Performance fixture has an unexpected size");
            }
        }

        public UniformGridSpatialIndex<Integer, NodeRect> nodeIndex(double cellSize) {
            UniformGridSpatialIndex<Integer, NodeRect> index = new UniformGridSpatialIndex<>(cellSize);
            for (NodeRect node : nodes) {
                index.insert(node.id(), node, node.bounds());
            }
            return index;
        }

        public UniformGridSpatialIndex<Integer, WireSegment> wireSegmentIndex(double cellSize) {
            UniformGridSpatialIndex<Integer, WireSegment> index = new UniformGridSpatialIndex<>(cellSize);
            for (WireSegment wireSegment : wireSegments) {
                index.insert(wireSegment.id(), wireSegment, wireSegment.segment().bounds());
            }
            return index;
        }
    }

    public record NodeRect(int id, GraphRect bounds) {}

    public record WireSegment(int id, int sourceNodeId, int targetNodeId, GraphSegment segment) {}

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
