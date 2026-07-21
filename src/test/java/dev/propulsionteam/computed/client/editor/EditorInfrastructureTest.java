package dev.propulsionteam.computed.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EditorInfrastructureTest {
    @Test
    void deterministicLargeFixtureBuildsIndexedViewportCandidates() {
        EditorPerformanceFixture.Fixture first = EditorPerformanceFixture.create();
        EditorPerformanceFixture.Fixture second = EditorPerformanceFixture.create();

        assertEquals(EditorPerformanceFixture.NODE_COUNT, first.nodes().size());
        assertEquals(EditorPerformanceFixture.WIRE_SEGMENT_COUNT, first.wireSegments().size());
        assertEquals(first, second, "the performance fixture must be reproducible");

        UniformGridSpatialIndex<Integer, EditorPerformanceFixture.NodeRect> nodeIndex = first.nodeIndex(256.0D);
        UniformGridSpatialIndex<Integer, EditorPerformanceFixture.WireSegment> wireIndex =
                first.wireSegmentIndex(256.0D);
        GraphRect viewport = GraphRect.fromPositionAndSize(0.0D, 0.0D, 1_920.0D, 1_080.0D);

        int visibleNodes = nodeIndex.query(viewport).size();
        int visibleWireSegments = wireIndex.query(viewport).size();
        assertEquals(EditorPerformanceFixture.NODE_COUNT, nodeIndex.size());
        assertEquals(EditorPerformanceFixture.WIRE_SEGMENT_COUNT, wireIndex.size());
        assertTrue(visibleNodes > 0 && visibleNodes < EditorPerformanceFixture.NODE_COUNT);
        assertTrue(visibleWireSegments > 0 && visibleWireSegments < EditorPerformanceFixture.WIRE_SEGMENT_COUNT);
    }

    @Test
    void wireGeometryCacheReusesStableGeometryAndInvalidatesByConnection() {
        WireGeometryCache<String> cache = new WireGeometryCache<>(4);
        GraphPoint start = new GraphPoint(0.0D, 10.0D);
        GraphPoint end = new GraphPoint(240.0D, 80.0D);
        int bucket = WireGeometryCache.zoomBucket(1.0D);

        WireGeometryCache.WireGeometry first = cache.getBezier("wire-a", 1L, bucket, start, end);
        WireGeometryCache.WireGeometry hit = cache.getBezier("wire-a", 1L, bucket, start, end);
        WireGeometryCache.WireGeometry changed = cache.getBezier("wire-a", 2L, bucket, start, end);

        assertSame(first, hit);
        assertNotSame(first, changed);
        assertEquals(1L, cache.stats().hits());
        assertEquals(2L, cache.stats().misses());
        cache.invalidate("wire-a");
        assertEquals(0, cache.size());
    }

    @Test
    void commandHistoryTracksSavePointAcrossUndoAndRedo() {
        AtomicInteger value = new AtomicInteger();
        EditorHistory<AtomicInteger> history = new EditorHistory<>(value, 8);
        EditorCommand<AtomicInteger> increment = new EditorCommand<>() {
            @Override
            public void execute(AtomicInteger context) {
                context.incrementAndGet();
            }

            @Override
            public void undo(AtomicInteger context) {
                context.decrementAndGet();
            }

            @Override
            public String description() {
                return "Increment";
            }
        };

        history.execute(increment);
        history.markSaved();
        history.execute(increment);
        assertEquals(2, value.get());
        assertTrue(history.isDirty());
        assertEquals("Increment", history.undoDescription().orElseThrow());

        assertTrue(history.undo());
        assertEquals(1, value.get());
        assertFalse(history.isDirty(), "undoing to the saved revision must clear dirty state");
        assertTrue(history.redo());
        assertEquals(2, value.get());
        assertTrue(history.isDirty());
    }
}
