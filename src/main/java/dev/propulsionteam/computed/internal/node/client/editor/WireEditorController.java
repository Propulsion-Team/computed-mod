/** Wire interaction behavior derived from https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
package dev.propulsionteam.computed.internal.node.client.editor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.propulsionteam.computed.client.editor.EditorDetailLevel;
import dev.propulsionteam.computed.client.editor.GraphPoint;
import dev.propulsionteam.computed.client.editor.GraphRect;
import dev.propulsionteam.computed.client.editor.GraphSegment;
import dev.propulsionteam.computed.client.editor.UniformGridSpatialIndex;
import dev.propulsionteam.computed.client.editor.WireGeometryCache;
import dev.propulsionteam.computed.internal.node.api.WConnection;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Owns the editor's wire geometry, spatial index, viewport culling, picking, hover state, and
 * rendering. Gesture interpretation and command history deliberately remain with the screen.
 */
public final class WireEditorController {
    private static final double INDEX_CELL_SIZE = 256.0;
    private static final int[] WIRE_ARGB_PALETTE = {
        0xAA00FF88, 0xAA6B9CFF, 0xFFFFB84D, 0xFFFF6B9A, 0xAA3DFFDA, 0xFFFF9B6B
    };
    private static final int INSERT_GHOST_ARGB = 0xEE66EEFF;
    private static final float PULSE_SCROLL_SPEED = 0.11f;
    private static final float PULSE_STAGGER_PER_DEPTH = 0.034f;
    private static final int PULSE_DOT_COUNT = 2;
    private static final float PULSE_DOT_SPACING = 0.11f;

    public enum HoverKind {
        NONE,
        WAYPOINT,
        INSERT_GHOST,
        CURVE_ONLY
    }

    public record Hover(
            HoverKind kind,
            int connectionIndex,
            int waypointIndex,
            int insertionSegment,
            int insertionX,
            int insertionY) {
        private static final Hover NONE = new Hover(HoverKind.NONE, -1, -1, -1, 0, 0);

        public boolean is(HoverKind expected) {
            return kind == expected;
        }
    }

    private record SegmentCacheKey(
            UUID sourceNode,
            int sourcePin,
            UUID targetNode,
            int targetPin,
            int segmentIndex,
            EditorDetailLevel detailLevel) {}

    private record SpatialKey(int connectionIndex, int segmentIndex) {}

    private record IndexedSegment(
            int connectionIndex, int segmentIndex, WireGeometryCache.WireGeometry geometry) {}

    private final WireGeometryCache<SegmentCacheKey> geometryCache = new WireGeometryCache<>();
    private final UniformGridSpatialIndex<SpatialKey, IndexedSegment> spatialIndex =
            new UniformGridSpatialIndex<>(INDEX_CELL_SIZE);
    private final TreeSet<Integer> unindexedConnections = new TreeSet<>();

    private WGraph indexedGraph;
    private long indexedEditorRevision = Long.MIN_VALUE;
    private long indexedGeometryRevision = Long.MIN_VALUE;
    private int indexedConnectionCount = -1;
    private EditorDetailLevel indexedDetailLevel;

    private Hover hover = Hover.NONE;
    private WGraph hoverCacheGraph;
    private int hoverCacheX = Integer.MIN_VALUE;
    private int hoverCacheY = Integer.MIN_VALUE;
    private int hoverCacheConnectionCount = -1;
    private int hoverCacheNodeCount = -1;
    private long hoverCacheGeometryRevision = Long.MIN_VALUE;
    private long hoverCacheEditorRevision = Long.MIN_VALUE;
    private float hoverCacheScale = Float.NaN;
    private float pulseScroll;
    private int[] chainXs = new int[4];
    private int[] chainYs = new int[4];

    public Hover hover() {
        return hover;
    }

    public void advanceAnimation(float deltaTime) {
        pulseScroll += deltaTime * PULSE_SCROLL_SPEED;
        if (pulseScroll > 8192f) {
            pulseScroll -= 8192f;
        }
    }

    public void invalidate() {
        geometryCache.clear();
        spatialIndex.clear();
        unindexedConnections.clear();
        indexedGraph = null;
        indexedEditorRevision = Long.MIN_VALUE;
        indexedGeometryRevision = Long.MIN_VALUE;
        indexedConnectionCount = -1;
        indexedDetailLevel = null;
        clearHover();
        invalidateHoverCache();
    }

    public void clearHover() {
        hover = Hover.NONE;
    }

    public void invalidateHoverCache() {
        hoverCacheGraph = null;
        hoverCacheX = Integer.MIN_VALUE;
        hoverCacheY = Integer.MIN_VALUE;
        hoverCacheGeometryRevision = Long.MIN_VALUE;
        hoverCacheEditorRevision = Long.MIN_VALUE;
    }

    public int ghostPickRadius(float contentScale) {
        return Math.max(5, Mth.ceil(9f / contentScale));
    }

    public Hover updateHover(
            WGraph graph,
            int graphX,
            int graphY,
            float contentScale,
            long editorRevision,
            boolean geometryMoving,
            Predicate<GraphPoint> interactionBlocked) {
        int connectionCount = graph.getConnections().size();
        int nodeCount = graph.getNodes().size();
        long geometryRevision = graph.getConnectionGeometryRevision();
        if (!geometryMoving
                && graph == hoverCacheGraph
                && graphX == hoverCacheX
                && graphY == hoverCacheY
                && connectionCount == hoverCacheConnectionCount
                && nodeCount == hoverCacheNodeCount
                && geometryRevision == hoverCacheGeometryRevision
                && editorRevision == hoverCacheEditorRevision
                && contentScale == hoverCacheScale) {
            return hover;
        }
        hoverCacheGraph = graph;
        hoverCacheX = graphX;
        hoverCacheY = graphY;
        hoverCacheConnectionCount = connectionCount;
        hoverCacheNodeCount = nodeCount;
        hoverCacheGeometryRevision = geometryRevision;
        hoverCacheEditorRevision = editorRevision;
        hoverCacheScale = contentScale;
        clearHover();
        if (interactionBlocked.test(new GraphPoint(graphX, graphY))) {
            return hover;
        }

        int waypointPickRadius = Math.max(6, Mth.ceil(10f / contentScale));
        int curvePickRadius = Math.max(5, Mth.ceil(8f / contentScale));
        int insertionSeparation = Math.max(12, Mth.ceil(14f / contentScale));
        float waypointRadiusSquared = waypointPickRadius * (float) waypointPickRadius;
        float curveRadiusSquared = curvePickRadius * (float) curvePickRadius;
        List<WConnection> connections = graph.getConnections();
        ensureSpatialIndex(graph, contentScale, editorRevision, geometryMoving, EditorDetailLevel.FULL);

        int queryRadius = Math.max(waypointPickRadius, curvePickRadius) + 3;
        GraphRect pickArea = new GraphRect(
                graphX - queryRadius, graphY - queryRadius, graphX + queryRadius, graphY + queryRadius);
        List<UniformGridSpatialIndex.SpatialEntry<SpatialKey, IndexedSegment>> nearbySegments =
                spatialIndex.query(pickArea);
        TreeSet<Integer> nearbyConnections = new TreeSet<>();
        for (var entry : nearbySegments) {
            nearbyConnections.add(entry.value().connectionIndex());
        }

        int bestWaypointConnection = -1;
        int bestWaypointIndex = -1;
        float bestWaypointDistance = waypointRadiusSquared;
        for (int connectionIndex : nearbyConnections) {
            if (connectionIndex < 0 || connectionIndex >= connections.size()) {
                continue;
            }
            WConnection connection = connections.get(connectionIndex);
            for (int waypointIndex = 0; waypointIndex < connection.waypointXs().length; waypointIndex++) {
                float distance = distanceSquared(
                        graphX,
                        graphY,
                        connection.waypointXs()[waypointIndex],
                        connection.waypointYs()[waypointIndex]);
                if (distance <= bestWaypointDistance) {
                    bestWaypointDistance = distance;
                    bestWaypointConnection = connectionIndex;
                    bestWaypointIndex = waypointIndex;
                }
            }
        }
        if (bestWaypointConnection >= 0) {
            hover = new Hover(HoverKind.WAYPOINT, bestWaypointConnection, bestWaypointIndex, -1, 0, 0);
            return hover;
        }

        int bestConnection = -1;
        int bestSegment = 0;
        int bestX = 0;
        int bestY = 0;
        float bestDistance = curveRadiusSquared;
        for (var entry : nearbySegments) {
            IndexedSegment indexed = entry.value();
            for (GraphPoint point : indexed.geometry().points()) {
                float dx = graphX - (float) point.x();
                float dy = graphY - (float) point.y();
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestConnection = indexed.connectionIndex();
                    bestSegment = indexed.segmentIndex();
                    bestX = Math.round((float) point.x());
                    bestY = Math.round((float) point.y());
                }
            }
        }
        if (bestConnection < 0) {
            return hover;
        }

        boolean insertionAllowed = false;
        if (bestConnection < connections.size()) {
            WConnection connection = connections.get(bestConnection);
            WNode source = graph.getNode(connection.sourceNode());
            WNode target = graph.getNode(connection.targetNode());
            if (source != null && target != null) {
                int chainLength = chainLength(connection);
                int[] xs = new int[chainLength];
                int[] ys = new int[chainLength];
                fillChainEndpoints(connection, source, target, xs, ys);
                insertionAllowed = insertionClearOfWaypoints(
                        bestX, bestY, xs, ys, bestSegment, insertionSeparation);
            }
        }
        hover = new Hover(
                insertionAllowed ? HoverKind.INSERT_GHOST : HoverKind.CURVE_ONLY,
                bestConnection,
                -1,
                bestSegment,
                bestX,
                bestY);
        return hover;
    }

    public void render(
            GuiGraphics graphics,
            WGraph graph,
            GraphRect viewport,
            float contentScale,
            long editorRevision,
            boolean geometryMoving,
            EditorDetailLevel detailLevel) {
        ensureSpatialIndex(graph, contentScale, editorRevision, geometryMoving, detailLevel);
        List<WConnection> connections = graph.getConnections();
        TreeSet<Integer> visible = new TreeSet<>(unindexedConnections);
        for (UniformGridSpatialIndex.SpatialEntry<SpatialKey, IndexedSegment> entry : spatialIndex.query(viewport)) {
            visible.add(entry.value().connectionIndex());
        }
        WireBatch batch = WireBatch.forGraphics(graphics);
        for (int connectionIndex : visible) {
            if (connectionIndex >= 0 && connectionIndex < connections.size()) {
                renderConnection(
                        graphics,
                        graph,
                        connections.get(connectionIndex),
                        connectionIndex,
                        contentScale,
                        detailLevel,
                        batch);
            }
        }
    }

    public void renderCurve(
            GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, float thickness) {
        WireBatch batch = WireBatch.forGraphics(graphics);
        float lastX = x1;
        float lastY = y1;
        for (int step = 1; step <= EditorDetailLevel.FULL.curveSamples(); step++) {
            float t = step / (float) EditorDetailLevel.FULL.curveSamples();
            float x = curveX(x1, x2, t);
            float y = curveY(y1, y2, t);
            batch.line(lastX, lastY, x, y, color, thickness);
            lastX = x;
            lastY = y;
        }
    }

    private void ensureSpatialIndex(
            WGraph graph,
            float contentScale,
            long editorRevision,
            boolean geometryMoving,
            EditorDetailLevel detailLevel) {
        List<WConnection> connections = graph.getConnections();
        long geometryRevision = graph.getConnectionGeometryRevision();
        if (!geometryMoving
                && indexedGraph == graph
                && indexedEditorRevision == editorRevision
                && indexedGeometryRevision == geometryRevision
                && indexedConnectionCount == connections.size()
                && indexedDetailLevel == detailLevel) {
            return;
        }

        spatialIndex.clear();
        unindexedConnections.clear();
        for (int connectionIndex = 0; connectionIndex < connections.size(); connectionIndex++) {
            WConnection connection = connections.get(connectionIndex);
            WNode source = graph.getNode(connection.sourceNode());
            WNode target = graph.getNode(connection.targetNode());
            if (!hasValidEndpoints(connection, source, target)) {
                continue;
            }
            int chainLength = chainLength(connection);
            int[] xs = new int[chainLength];
            int[] ys = new int[chainLength];
            fillChainEndpoints(connection, source, target, xs, ys);
            for (int segmentIndex = 0; segmentIndex < chainLength - 1; segmentIndex++) {
                WireGeometryCache.WireGeometry geometry = cachedSegment(
                        connection,
                        segmentIndex,
                        xs[segmentIndex],
                        ys[segmentIndex],
                        xs[segmentIndex + 1],
                        ys[segmentIndex + 1],
                        contentScale,
                        detailLevel);
                try {
                    spatialIndex.insert(
                            new SpatialKey(connectionIndex, segmentIndex),
                            new IndexedSegment(connectionIndex, segmentIndex, geometry),
                            geometry.bounds().expanded(3.0));
                } catch (IllegalArgumentException outOfRange) {
                    // Preserve and render malicious legacy coordinates without allocating an unbounded grid.
                    unindexedConnections.add(connectionIndex);
                }
            }
        }
        indexedGraph = graph;
        indexedEditorRevision = editorRevision;
        indexedGeometryRevision = geometryRevision;
        indexedConnectionCount = connections.size();
        indexedDetailLevel = detailLevel;
    }

    private void renderConnection(
            GuiGraphics graphics,
            WGraph graph,
            WConnection connection,
            int connectionIndex,
            float contentScale,
            EditorDetailLevel detailLevel,
            WireBatch batch) {
        WNode source = graph.getNode(connection.sourceNode());
        WNode target = graph.getNode(connection.targetNode());
        if (source == null || target == null) {
            return;
        }
        int length = chainLength(connection);
        ensureChainCapacity(length);
        fillChainEndpoints(connection, source, target, chainXs, chainYs);
        int color = detailLevel == EditorDetailLevel.OVERVIEW
                ? withAlphaScale(colorFor(connection), 0.55f)
                : colorFor(connection);
        float thickness = detailLevel == EditorDetailLevel.FULL
                ? 1.5f
                : 1.0f / Math.max(0.1f, contentScale);
        for (int segment = 0; segment < length - 1; segment++) {
            WireGeometryCache.WireGeometry geometry = cachedSegment(
                    connection,
                    segment,
                    chainXs[segment],
                    chainYs[segment],
                    chainXs[segment + 1],
                    chainYs[segment + 1],
                    contentScale,
                    detailLevel);
            for (GraphSegment line : geometry.segments()) {
                batch.line(
                        (float) line.start().x(),
                        (float) line.start().y(),
                        (float) line.end().x(),
                        (float) line.end().y(),
                        color,
                        thickness);
            }
        }
        if (detailLevel != EditorDetailLevel.FULL) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        renderPulse(graphics, chainXs, chainYs, length, source.getTopoDepth(), color);
        graphics.pose().popPose();

        int rgb = color & 0xFFFFFF;
        int waypointSize = Math.max(3, Mth.ceil(4f / contentScale));
        for (int waypoint = 0; waypoint < connection.waypointXs().length; waypoint++) {
            int x = connection.waypointXs()[waypoint];
            int y = connection.waypointYs()[waypoint];
            boolean hot = hover.is(HoverKind.WAYPOINT)
                    && connectionIndex == hover.connectionIndex()
                    && waypoint == hover.waypointIndex();
            int ring = hot ? 0xFFFFFFFF : (0xCC000000 | rgb);
            int core = 0xFF000000 | rgb;
            int half = waypointSize / 2;
            graphics.fill(
                    x - waypointSize - 1,
                    y - waypointSize - 1,
                    x + waypointSize + 2,
                    y + waypointSize + 2,
                    ring);
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, core);
        }
        if (connectionIndex == hover.connectionIndex() && hover.is(HoverKind.INSERT_GHOST)) {
            int radius = Math.max(3, Mth.ceil(5f / contentScale));
            int x = hover.insertionX();
            int y = hover.insertionY();
            graphics.fill(x - radius - 2, y - radius - 2, x + radius + 3, y + radius + 3, INSERT_GHOST_ARGB);
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, 0xFF222244);
        }
    }

    private void renderPulse(
            GuiGraphics graphics, int[] xs, int[] ys, int pointCount, int topoDepth, int wireArgb) {
        int segmentCount = pointCount - 1;
        if (segmentCount <= 0) {
            return;
        }
        int rgb = wireArgb & 0xFFFFFF;
        float phase = pulseScroll - Mth.floor(pulseScroll) - topoDepth * PULSE_STAGGER_PER_DEPTH;
        phase -= Mth.floor(phase);
        for (int dot = 0; dot < PULSE_DOT_COUNT; dot++) {
            float progress = phase - dot * PULSE_DOT_SPACING;
            progress -= Mth.floor(progress);
            if (progress < 0) {
                progress += 1f;
            }
            float smooth = progress * progress * (3.0f - 2.0f * progress);
            float graphPosition = smooth * segmentCount;
            int segment = Math.min(segmentCount - 1, (int) graphPosition);
            float localT = Mth.clamp(graphPosition - segment, 0f, 1f);
            float x = curveX(xs[segment], xs[segment + 1], localT);
            float y = curveY(ys[segment], ys[segment + 1], localT);
            int alpha = 235 - dot * 70;
            int core = (alpha << 24) | rgb;
            int glow = ((alpha / 4) << 24) | rgb;
            graphics.fill((int) x - 3, (int) y - 3, (int) x + 4, (int) y + 4, glow);
            graphics.fill((int) x - 1, (int) y - 1, (int) x + 2, (int) y + 2, core);
        }
    }

    private WireGeometryCache.WireGeometry cachedSegment(
            WConnection connection,
            int segmentIndex,
            int x1,
            int y1,
            int x2,
            int y2,
            float contentScale,
            EditorDetailLevel detailLevel) {
        SegmentCacheKey key = new SegmentCacheKey(
                connection.sourceNode(),
                connection.sourcePin(),
                connection.targetNode(),
                connection.targetPin(),
                segmentIndex,
                detailLevel);
        return geometryCache.getOrCompute(
                key,
                geometryRevision(x1, y1, x2, y2),
                WireGeometryCache.zoomBucket(contentScale),
                () -> tessellate(x1, y1, x2, y2, detailLevel.curveSamples()));
    }

    private static boolean hasValidEndpoints(WConnection connection, WNode source, WNode target) {
        return source != null
                && target != null
                && connection.sourcePin() >= 0
                && connection.sourcePin() < source.getOutputs().size()
                && connection.targetPin() >= 0
                && connection.targetPin() < target.getInputs().size();
    }

    private static void fillChainEndpoints(
            WConnection connection, WNode source, WNode target, int[] xs, int[] ys) {
        int index = 0;
        xs[index] = source.getX() + source.getWidth();
        ys[index] = source.getY() + 18 + connection.sourcePin() * 12;
        for (int waypoint = 0; waypoint < connection.waypointXs().length; waypoint++) {
            index++;
            xs[index] = connection.waypointXs()[waypoint];
            ys[index] = connection.waypointYs()[waypoint];
        }
        index++;
        xs[index] = target.getX();
        ys[index] = target.getY() + 18 + connection.targetPin() * 12;
    }

    private static int chainLength(WConnection connection) {
        return 2 + connection.waypointXs().length;
    }

    private void ensureChainCapacity(int length) {
        if (chainXs.length < length) {
            chainXs = new int[length];
            chainYs = new int[length];
        }
    }

    private static List<GraphPoint> tessellate(int x1, int y1, int x2, int y2, int samples) {
        List<GraphPoint> points = new ArrayList<>(samples + 1);
        for (int step = 0; step <= samples; step++) {
            float t = step / (float) samples;
            points.add(new GraphPoint(curveX(x1, x2, t), curveY(y1, y2, t)));
        }
        return List.copyOf(points);
    }

    private static boolean insertionClearOfWaypoints(
            int insertionX, int insertionY, int[] xs, int[] ys, int segment, float minimumDistance) {
        float minimumSquared = minimumDistance * minimumDistance;
        int start = segment;
        int end = segment + 1;
        if (distanceSquared(insertionX, insertionY, xs[start], ys[start]) < minimumSquared
                || distanceSquared(insertionX, insertionY, xs[end], ys[end]) < minimumSquared) {
            return false;
        }
        for (int index = 0; index < xs.length; index++) {
            if (index != start
                    && index != end
                    && distanceSquared(insertionX, insertionY, xs[index], ys[index]) < minimumSquared) {
                return false;
            }
        }
        return true;
    }

    private static float distanceSquared(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static int colorFor(WConnection connection) {
        int hash = Objects.hash(
                connection.sourceNode(), connection.sourcePin(), connection.targetNode(), connection.targetPin());
        return WIRE_ARGB_PALETTE[Math.floorMod(hash, WIRE_ARGB_PALETTE.length)];
    }

    private static int withAlphaScale(int argb, float scale) {
        int alpha = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * scale), 0, 255);
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static long geometryRevision(int x1, int y1, int x2, int y2) {
        long revision = 0xcbf29ce484222325L;
        revision = (revision ^ x1) * 0x100000001b3L;
        revision = (revision ^ y1) * 0x100000001b3L;
        revision = (revision ^ x2) * 0x100000001b3L;
        return (revision ^ y2) * 0x100000001b3L;
    }

    private static float curveX(int x1, int x2, float t) {
        float middle = x1 + (x2 - x1) * 0.5f;
        float inverse = 1.0f - t;
        return inverse * inverse * inverse * x1
                + 3 * inverse * inverse * t * middle
                + 3 * inverse * t * t * middle
                + t * t * t * x2;
    }

    private static float curveY(int y1, int y2, float t) {
        float inverse = 1.0f - t;
        return inverse * inverse * inverse * y1
                + 3 * inverse * inverse * t * y1
                + 3 * inverse * t * t * y2
                + t * t * t * y2;
    }

    /** Emits one GUI quad per line segment into GuiGraphics's managed frame batch. */
    private record WireBatch(Matrix4f matrix, VertexConsumer consumer) {
        static WireBatch forGraphics(GuiGraphics graphics) {
            return new WireBatch(
                    new Matrix4f(graphics.pose().last().pose()),
                    graphics.bufferSource().getBuffer(RenderType.gui()));
        }

        void line(float x1, float y1, float x2, float y2, int argb, float thickness) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = Mth.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001f) {
                return;
            }
            float half = Math.max(0.25f, thickness * 0.5f);
            float nx = -dy / length * half;
            float ny = dx / length * half;
            float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
            float red = ((argb >>> 16) & 0xFF) / 255.0f;
            float green = ((argb >>> 8) & 0xFF) / 255.0f;
            float blue = (argb & 0xFF) / 255.0f;
            consumer.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(red, green, blue, alpha);
            consumer.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(red, green, blue, alpha);
            consumer.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(red, green, blue, alpha);
            consumer.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(red, green, blue, alpha);
        }
    }
}
