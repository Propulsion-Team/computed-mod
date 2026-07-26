package dev.propulsionteam.computed.client.editor.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InertialViewportTest {
    @Test
    void panRetainsAndThenDecaysReleaseVelocity() {
        InertialViewport viewport = new InertialViewport();
        viewport.beginPan();
        viewport.dragPan(24, -12, 1.0 / 60.0);
        viewport.endPan();
        double releasedX = viewport.panX();
        double releasedY = viewport.panY();

        viewport.advance(1.0 / 60.0, 800, 600);
        assertTrue(viewport.panX() > releasedX);
        assertTrue(viewport.panY() < releasedY);

        for (int index = 0; index < 300; index++) {
            viewport.advance(1.0 / 60.0, 800, 600);
        }
        double settledX = viewport.panX();
        viewport.advance(1, 800, 600);
        assertEquals(settledX, viewport.panX(), 0.001);
    }

    @Test
    void zoomKeepsTheGraphPointUnderTheCursor() {
        InertialViewport viewport = new InertialViewport();
        double beforeX = viewport.graphX(640, 800);
        double beforeY = viewport.graphY(180, 600);

        viewport.addZoomImpulse(0.5, 640, 180);
        for (int index = 0; index < 120; index++) {
            viewport.advance(1.0 / 120.0, 800, 600);
        }

        assertEquals(beforeX, viewport.graphX(640, 800), 0.001);
        assertEquals(beforeY, viewport.graphY(180, 600), 0.001);
        assertTrue(viewport.zoom() > 1);
    }

    @Test
    void integrationIsStableAcrossFrameRatesAndClampsZoom() {
        InertialViewport sixtyFps = new InertialViewport();
        InertialViewport oneTwentyFps = new InertialViewport();
        sixtyFps.addZoomImpulse(0.4, 400, 300);
        oneTwentyFps.addZoomImpulse(0.4, 400, 300);
        for (int index = 0; index < 60; index++) {
            sixtyFps.advance(1.0 / 60.0, 800, 600);
        }
        for (int index = 0; index < 120; index++) {
            oneTwentyFps.advance(1.0 / 120.0, 800, 600);
        }
        assertEquals(sixtyFps.zoom(), oneTwentyFps.zoom(), 0.01);

        sixtyFps.addZoomImpulse(100, 400, 300);
        sixtyFps.advance(1, 800, 600);
        assertEquals(InertialViewport.MAX_ZOOM, sixtyFps.zoom());
        sixtyFps.addZoomImpulse(-100, 400, 300);
        sixtyFps.advance(1, 800, 600);
        assertEquals(InertialViewport.MIN_ZOOM, sixtyFps.zoom());
    }

    @Test
    void restoreCancelsAllMotion() {
        InertialViewport viewport = new InertialViewport();
        viewport.beginPan();
        viewport.dragPan(20, 10, 1.0 / 60.0);
        viewport.endPan();
        viewport.addZoomImpulse(0.4, 400, 300);
        viewport.restore(12, -8, 1.5f);
        viewport.advance(1, 800, 600);

        assertEquals(12, viewport.panX());
        assertEquals(-8, viewport.panY());
        assertEquals(1.5f, viewport.zoom());
    }
}
