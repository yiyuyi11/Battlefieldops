package cn.bfops;

import java.util.Arrays;

/**
 * Headless checks for the shared map projection and its clipping helper.
 *
 * <p>The projection is the one thing every map — minimap, tactical map, editor canvas — converts through,
 * so a marker that paints at one pixel and hit-tests another, or a shade that drifts off its outline on a
 * non-square panel, is a failure of this one record. The round-trip assertions pin that {@code worldX(sx)}
 * and {@code worldZ(sz)} invert each other on a panel where {@code w != h}, which is exactly where a
 * {@code span/h} typo in the inverse would drift.
 */
public final class MapGeometrySim {
    private static int passed;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("FAIL " + label);
        passed++;
    }

    public static void main(String[] args) {
        clipping();
        projectionRoundTrip();
        System.out.println("MAP GEOMETRY: " + passed + " assertions passed");
    }

    /** Liang-Barsky clipping, hand-computed. */
    private static void clipping() {
        check(Arrays.equals(MapGeometry.clip(-100000, 50, 100000, 50, 0, 0, 100, 100), new int[]{0, 50, 100, 50}),
                "a horizontal line crossing the whole viewport clips to the viewport");
        check(Arrays.equals(MapGeometry.clip(50, -100000, 50, 100000, 0, 0, 100, 100), new int[]{50, 0, 50, 100}),
                "a vertical line crossing the whole viewport clips to the viewport");
        check(MapGeometry.clip(-10, 0, -10, 100, 0, 0, 100, 100) == null,
                "a line entirely left of the viewport is rejected");
        check(Arrays.equals(MapGeometry.clip(10, 10, 90, 90, 0, 0, 100, 100), new int[]{10, 10, 90, 90}),
                "a line fully inside passes through unchanged");
        check(Arrays.equals(MapGeometry.clip(200, 200, -100, -100, 0, 0, 100, 100), new int[]{100, 100, 0, 0}),
                "a line through the viewport clips to both edges");
        check(MapGeometry.clip(110, 120, 200, 130, 0, 0, 100, 100) == null,
                "a line entirely right of the viewport is rejected");
        check(Arrays.equals(MapGeometry.clip(0, 0, 0, 0, 0, 0, 100, 100), new int[]{0, 0, 0, 0}),
                "a degenerate point inside passes through");
        check(MapGeometry.clip(-1, -1, -1, -1, 0, 0, 100, 100) == null,
                "a degenerate point outside is rejected");
    }

    /**
     * The projection's forward and inverse must be each other's inverse, on a non-square panel.
     *
     * <p>Both world axes are scaled by the panel <em>width</em>; the extra height is padding. A non-square
     * body is therefore the exact case that exposes an inverse that accidentally divides by the height.
     */
    private static void projectionRoundTrip() {
        // 200x150 panel: height differs from width, so span/h would disagree with span/w.
        MapGeometry.MapProjection p = new MapGeometry.MapProjection(10, 20, 200, 150, 1000, -500, 256);
        check(Math.abs(p.worldX(p.sx(1200)) - 1200) <= 0.5,
                "worldX inverts sx within half a block (got " + p.worldX(p.sx(1200)) + ")");
        check(Math.abs(p.worldZ(p.sz(-600)) + 600) <= 0.5,
                "worldZ inverts sz within half a block (got " + p.worldZ(p.sz(-600)) + ")");
        // The vertical axis is centred on y + h/2 and scaled by w, so a row off the vertical centre must
        // still invert exactly — this is what span/h would get wrong.
        check(Math.abs(p.worldZ(p.sz(0)) - 0) <= 0.5,
                "worldZ inverts a non-centre row within half a block (got " + p.worldZ(p.sz(0)) + ")");
        // Backward compatibility: the map is square in world terms, so sx and sz share one scale.
        check(p.sx(1000) - p.x() == p.w() / 2 && p.sz(-500) - p.y() == p.h() / 2,
                "the world centre lands on the panel centre");
    }
}
