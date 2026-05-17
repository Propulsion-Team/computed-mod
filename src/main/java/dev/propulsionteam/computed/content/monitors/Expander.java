package dev.propulsionteam.computed.content.monitors;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Expands a monitor into available space. Ported from CC: Tweaked.
 */
class Expander {
    private static final Logger LOG = LoggerFactory.getLogger(Expander.class);

    private final Level level;
    private final Direction down;
    private final Direction right;

    private MonitorBlockEntity origin;
    private int width;
    private int height;

    Expander(MonitorBlockEntity origin) {
        this.origin = origin;
        width = origin.getWidth();
        height = origin.getHeight();

        level = Objects.requireNonNull(origin.getLevel(), "level cannot be null");
        down = origin.getDown();
        right = origin.getRight();
    }

    void expand() {
        var changedCount = 0;

        var changeLimit = MonitorBlockEntity.MAX_WIDTH * MonitorBlockEntity.MAX_HEIGHT + 1;
        while (expandIn(true, false) || expandIn(true, true) ||
            expandIn(false, false) || expandIn(false, true)
        ) {
            changedCount++;
            if (changedCount > changeLimit) {
                LOG.error("Monitor has grown too much. This suggests there's an empty monitor in the world.");
                break;
            }
        }

        if (changedCount > 0) origin.resize(width, height);
    }

    private boolean expandIn(boolean useXAxis, boolean isPositive) {
        var pos = origin.getBlockPos();
        int height = this.height, width = this.width;

        var otherOffset = isPositive ? (useXAxis ? width : height) : -1;
        var otherPos = useXAxis ? pos.relative(right, otherOffset) : pos.relative(down, otherOffset);
        var other = level.getBlockEntity(otherPos);
        if (!(other instanceof MonitorBlockEntity otherMonitor) || !origin.isCompatible(otherMonitor)) return false;

        if (useXAxis) {
            if (otherMonitor.getYIndex() != 0 || otherMonitor.getHeight() != height) return false;
            width += otherMonitor.getWidth();
            if (width > MonitorBlockEntity.MAX_WIDTH) return false;
        } else {
            if (otherMonitor.getXIndex() != 0 || otherMonitor.getWidth() != width) return false;
            height += otherMonitor.getHeight();
            if (height > MonitorBlockEntity.MAX_HEIGHT) return false;
        }

        if (!isPositive) {
            var otherOrigin = level.getBlockEntity(otherMonitor.toWorldPos(0, 0));
            if (!(otherOrigin instanceof MonitorBlockEntity originMonitor) || !origin.isCompatible(originMonitor)) {
                return false;
            }

            origin = originMonitor;
        }

        this.width = width;
        this.height = height;

        return true;
    }
}
