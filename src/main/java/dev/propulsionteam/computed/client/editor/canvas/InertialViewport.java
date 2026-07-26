package dev.propulsionteam.computed.client.editor.canvas;

public final class InertialViewport {
    public static final float MIN_ZOOM = 0.1f;
    public static final float MAX_ZOOM = 3.0f;

    private static final double PAN_FRICTION = 7.5;
    private static final double ZOOM_FRICTION = 12.0;
    private static final double STOP_EPSILON = 0.0001;

    private double panX;
    private double panY;
    private double panVelocityX;
    private double panVelocityY;
    private float zoom = 1.0f;
    private double zoomVelocity;
    private double zoomAnchorX;
    private double zoomAnchorY;
    private boolean panning;

    public void restore(double panX, double panY, float zoom) {
        this.panX = panX;
        this.panY = panY;
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        cancelMotion();
    }

    public void beginPan() {
        panning = true;
        panVelocityX = 0;
        panVelocityY = 0;
    }

    public void dragPan(double screenDeltaX, double screenDeltaY, double elapsedSeconds) {
        double graphDeltaX = screenDeltaX / zoom;
        double graphDeltaY = screenDeltaY / zoom;
        panX += graphDeltaX;
        panY += graphDeltaY;
        double elapsed = clamp(elapsedSeconds, 1.0 / 240.0, 1.0 / 20.0);
        double sampleX = graphDeltaX / elapsed;
        double sampleY = graphDeltaY / elapsed;
        panVelocityX = panVelocityX * 0.35 + sampleX * 0.65;
        panVelocityY = panVelocityY * 0.35 + sampleY * 0.65;
    }

    public void endPan() {
        panning = false;
    }

    public void addZoomImpulse(
            double requestedDelta,
            double anchorX,
            double anchorY) {
        zoomAnchorX = anchorX;
        zoomAnchorY = anchorY;
        zoomVelocity += requestedDelta * ZOOM_FRICTION;
    }

    public void advance(double elapsedSeconds, int viewportWidth, int viewportHeight) {
        double elapsed = clamp(elapsedSeconds, 0, 0.1);
        if (!panning) {
            double decay = Math.exp(-PAN_FRICTION * elapsed);
            double travel = (1 - decay) / PAN_FRICTION;
            panX += panVelocityX * travel;
            panY += panVelocityY * travel;
            panVelocityX *= decay;
            panVelocityY *= decay;
            if (Math.abs(panVelocityX) < STOP_EPSILON) {
                panVelocityX = 0;
            }
            if (Math.abs(panVelocityY) < STOP_EPSILON) {
                panVelocityY = 0;
            }
        }
        if (zoomVelocity == 0 || elapsed == 0) {
            return;
        }
        double decay = Math.exp(-ZOOM_FRICTION * elapsed);
        double zoomTravel = zoomVelocity * (1 - decay) / ZOOM_FRICTION;
        float previousZoom = zoom;
        float nextZoom = clamp((float) (zoom + zoomTravel), MIN_ZOOM, MAX_ZOOM);
        if (nextZoom != previousZoom) {
            double centerX = viewportWidth / 2.0;
            double centerY = viewportHeight / 2.0;
            double anchoredGraphX =
                    (zoomAnchorX - centerX) / previousZoom + centerX - panX;
            double anchoredGraphY =
                    (zoomAnchorY - centerY) / previousZoom + centerY - panY;
            zoom = nextZoom;
            panX = (zoomAnchorX - centerX) / zoom + centerX - anchoredGraphX;
            panY = (zoomAnchorY - centerY) / zoom + centerY - anchoredGraphY;
        }
        zoomVelocity *= decay;
        if (Math.abs(zoomVelocity) < STOP_EPSILON
                || zoom == MIN_ZOOM
                || zoom == MAX_ZOOM) {
            zoomVelocity = 0;
        }
    }

    public void cancelMotion() {
        panVelocityX = 0;
        panVelocityY = 0;
        zoomVelocity = 0;
        panning = false;
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    public float zoom() {
        return zoom;
    }

    public double graphX(double screenX, int viewportWidth) {
        return (screenX - viewportWidth / 2.0) / zoom
                + viewportWidth / 2.0
                - panX;
    }

    public double graphY(double screenY, int viewportHeight) {
        return (screenY - viewportHeight / 2.0) / zoom
                + viewportHeight / 2.0
                - panY;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
