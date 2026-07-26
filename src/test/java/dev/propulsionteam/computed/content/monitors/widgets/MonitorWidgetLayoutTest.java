package dev.propulsionteam.computed.content.monitors.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitorWidgetLayoutTest {
    @Test
    void preservesRawWidgetCoordinates() {
        TextWidget widget = new TextWidget(
                UUID.randomUUID(), 5, 7, 20, 9, "raw", 0xFFFFFFFF, TextAlignment.LEFT);

        List<Widget> resolved = MonitorWidgetLayout.resolve(List.of(widget), 128, 64);

        assertEquals(1, resolved.size());
        assertSame(widget, resolved.getFirst());
    }

    @Test
    void resolvesLineManagedWidgetsAgainstMonitorDimensions() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 0, 0, 1, 1, "line", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.AUTO);

        List<Widget> resolved = MonitorWidgetLayout.resolve(List.of(managed), 128, 64);

        assertEquals(
                new TextWidget(id, 8, 8, 112, 20, "line", 0xFFFFFFFF, TextAlignment.CENTER),
                resolved.getFirst());
    }

    @Test
    void stretchesTheSameLineWidgetAcrossDifferentMonitorWidths() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 27, 19, 3, 4, "responsive", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.AUTO);

        TextWidget oneBlock = (TextWidget) MonitorWidgetLayout.resolve(List.of(managed), 64, 64).getFirst();
        TextWidget threeBlocks = (TextWidget) MonitorWidgetLayout.resolve(List.of(managed), 192, 64).getFirst();

        assertEquals(8, oneBlock.x());
        assertEquals(48, oneBlock.w());
        assertEquals(8, threeBlocks.x());
        assertEquals(176, threeBlocks.w());
        assertEquals(oneBlock.y(), threeBlocks.y());
        assertEquals(oneBlock.h(), threeBlocks.h());
    }
}
