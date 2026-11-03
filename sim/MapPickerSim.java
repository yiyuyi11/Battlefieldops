package cn.bfops;

/**
 * Offline checks for the shared map widget's projection.
 *
 * <p>A map that draws markers in one place and hit-tests them in another is worse than no map: the
 * operator aims at a flag and moves a different one. Every screen now draws and picks through
 * {@link MapPicker}, and both go through {@link MapGeometry.MapProjection}, so these checks pin the
 * property that makes that safe — screen and world conversion are exact inverses, at any window size,
 * zoom or centre.
 *
 * <p>The projection is a pure value, so it is checked here without a client level.
 */
public final class MapPickerSim {
    private static int checks;

    public static void main(String[] args) {
        roundTrip();
        markerHitIsStable();
        zoomKeepsTheCursorFixed();
        panIsProportionalToScale();
        distantMarkerIsNotClickable();
        System.out.println("MapPickerSim: " + checks + " checks passed");
    }

    /** Screen -> world -> screen must return the pixel it started from. */
    private static void roundTrip() {
        int[][] sizes = {{120, 90}, {200, 150}, {377, 211}, {640, 480}};
        double[][] views = {{0, 0, 256}, {1234.5, -987.25, 64}, {-500, 750, 4096}};
        for (int[] size : sizes) {
            Layout.Rect body = new Layout.Rect(17, 29, size[0], size[1]);
            for (double[] view : views) {
                MapGeometry.MapProjection projection = MapGeometry.of(body, view[0], view[1], view[2]);
                for (int sx = body.x(); sx < body.right(); sx += 7) {
                    for (int sy = body.y(); sy < body.bottom(); sy += 7) {
                        double worldX = projection.worldX(sx);
                        double worldZ = projection.worldZ(sy);
                        int backX = projection.sx(worldX);
                        int backZ = projection.sz(worldZ);
                        // Rounding to whole pixels means the inverse can land one pixel away; anything
                        // larger is a projection bug that would put a marker under the wrong cursor.
                        require(Math.abs(backX - sx) <= 1,
                                "x round-trip " + sx + " -> " + backX + " in " + body);
                        require(Math.abs(backZ - sy) <= 1,
                                "z round-trip " + sy + " -> " + backZ + " in " + body);
                    }
                }
            }
        }
        checks++;
    }

    /** A marker must be selectable by clicking the pixel it was painted on. */
    private static void markerHitIsStable() {
        Layout.Rect body = new Layout.Rect(40, 20, 320, 240);
        MapGeometry.MapProjection projection = MapGeometry.of(body, 100, 200, 512);
        double[][] markers = {{100, 200}, {60, 180}, {140, 260}, {30, 210}};
        for (double[] marker : markers) {
            int px = projection.sx(marker[0]);
            int pz = projection.sz(marker[1]);
            require(body.inside(px, pz), "marker must be inside the body: " + px + "," + pz);
            // Clicking the exact pixel recovers the marker's world position.
            require(Math.abs(projection.worldX(px) - marker[0]) <= projection.span() / body.w(),
                    "hit-test x for " + marker[0]);
            require(Math.abs(projection.worldZ(pz) - marker[1]) <= projection.span() / body.w(),
                    "hit-test z for " + marker[1]);
        }
        checks++;
    }

    /** Zooming about the cursor must leave the world point under the cursor where it was. */
    private static void zoomKeepsTheCursorFixed() {
        Layout.Rect body = new Layout.Rect(0, 0, 300, 200);
        double cx = 500, cz = -300, span = 400;
        double mouseX = 210, mouseY = 60;
        MapGeometry.MapProjection before = MapGeometry.of(body, cx, cz, span);
        double beforeX = before.worldX(mouseX), beforeZ = before.worldZ(mouseY);

        // Same arithmetic the widget applies when zooming in.
        double nextSpan = span / 1.25;
        MapGeometry.MapProjection scaled = MapGeometry.of(body, cx, cz, nextSpan);
        double nextCx = beforeX - (mouseX - body.x() - body.w() / 2.0) * scaled.blocksPerPixel();
        double nextCz = beforeZ - (mouseY - body.y() - body.h() / 2.0) * scaled.blocksPerPixel();

        MapGeometry.MapProjection zoomed = MapGeometry.of(body, nextCx, nextCz, nextSpan);
        require(Math.abs(zoomed.worldX(mouseX) - beforeX) < 1e-9, "zoom keeps x under the cursor");
        require(Math.abs(zoomed.worldZ(mouseY) - beforeZ) < 1e-9, "zoom keeps z under the cursor");
        checks++;
    }

    /** A drag of N pixels must move the view by N pixels' worth of blocks, in the drag direction. */
    private static void panIsProportionalToScale() {
        Layout.Rect body = new Layout.Rect(0, 0, 256, 256);
        MapGeometry.MapProjection projection = MapGeometry.of(body, 0, 0, 256);
        require(Math.abs(projection.blocksPerPixel() - 1.0) < 1e-9,
                "256 blocks over 256 px is 1 block per pixel");
        // Dragging right by 10 px pans the view left by 10 blocks, so the map content follows the hand.
        double cx = 0 - 10 * projection.blocksPerPixel();
        MapGeometry.MapProjection panned = MapGeometry.of(body, cx, 0, 256);
        require(panned.sx(0) > projection.sx(0), "content moves right when dragging right");
        checks++;
    }

    /** Marker selection must not treat markers outside the visible window as clickable. */
    private static void distantMarkerIsNotClickable() {
        Layout.Rect body = new Layout.Rect(10, 10, 100, 100);
        MapGeometry.MapProjection projection = MapGeometry.of(body, 0, 0, 128);
        int far = projection.sx(1_000_000);
        require(!body.inside(far, projection.sz(0)), "a distant marker projects outside the body");
        require(body.inside(projection.sx(0), projection.sz(0)), "the centre marker projects inside");
        checks++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
