package cn.bfops;

import java.util.List;

/**
 * Headless checks for the deploy-area mask's scanline fill.
 *
 * <p>The one failure this class must never have is the one the reference pack names explicitly: a concave
 * polygon filled the wrong way. "Filled the wrong way" does not throw and does not log — it dims the land the
 * player is allowed to deploy on and leaves forbidden land bright, which inverts the rule on screen. The
 * even-odd intersection logic is therefore tested against hand-computed crossings, not just "it ran".
 */
public final class PolygonMaskSim {
    private static int checks;

    public static void main(String[] args) {
        convexTriangle();
        concaveArrow();
        lShape();
        vertexNotDoubleCounted();
        horizontalEdges();
        outsideFillDirection();
        System.out.println("PolygonMaskSim: " + checks + " checks passed");
    }

    /** A horizontal line through a convex triangle crosses exactly twice. */
    private static void convexTriangle() {
        // (0,0), (10,0), (5,10) — apex up. y=5 crosses the two slanted edges.
        List<int[]> tri = List.of(p(0, 0), p(10, 0), p(5, 10));
        List<Integer> xs = PolygonMask.intersections(tri, 5);
        check(xs.size() == 2, "a convex triangle is crossed twice (got " + xs.size() + ")");
        check(xs.get(0) == 3 && xs.get(1) == 8,
                "crossings are at the two slanted edges (got " + xs + ")");
    }

    /**
     * The case the reference pack calls out by name.
     *
     * <p>An arrowhead/concave quadrilateral whose vertex (5,5) is the notch. A scanline through the notch
     * crosses four edges, and the even-odd rule must pair them so the two arms are inside and the notch is
     * outside — not the reverse.
     */
    private static void concaveArrow() {
        // (5,0) bottom tip, (10,10) right, (5,5) notch, (0,10) left.
        List<int[]> arrow = List.of(p(5, 0), p(10, 10), p(5, 5), p(0, 10));
        List<Integer> xs = PolygonMask.intersections(arrow, 7);
        check(xs.size() == 4, "a scanline through the notch crosses four edges (got " + xs.size() + ")");
        // Exact crossings: 1.5, 3, 7, 8.5. Math.round (half-up) turns them into 2, 3, 7, 9. Even-odd pairs
        // them into [2,3] and [7,9], leaving the notch [3,7] outside — which is the whole point of the
        // even-odd rule on a concave shape.
        check(xs.get(0) == 2 && xs.get(1) == 3 && xs.get(2) == 7 && xs.get(3) == 9,
                "the four crossings are ordered arm-notch-arm (got " + xs + ")");
    }

    /** An L-shape: one wide arm low, one narrow arm rising. Each horizontal band has the right width. */
    private static void lShape() {
        // (0,0)->(10,0)->(10,3)->(3,3)->(3,10)->(0,10)
        List<int[]> l = List.of(p(0, 0), p(10, 0), p(10, 3), p(3, 3), p(3, 10), p(0, 10));
        // In the low band (y=1) the shape spans the full width.
        List<Integer> low = PolygonMask.intersections(l, 1);
        check(low.size() == 2 && low.get(0) == 0 && low.get(1) == 10,
                "the low band spans the full width (got " + low + ")");
        // In the tall band (y=5) only the left arm exists.
        List<Integer> high = PolygonMask.intersections(l, 5);
        check(high.size() == 2 && high.get(0) == 0 && high.get(1) == 3,
                "the tall band spans only the left arm (got " + high + ")");
    }

    /**
     * A scanline landing exactly on a vertex must count it once, not twice.
     *
     * <p>The classic scanline bug: a vertex shared by two edges counts as two crossings, which shifts every
     * even-odd pair after it by one and inverts the fill. The half-open edge test prevents that by assigning
     * the vertex to exactly one edge.
     */
    private static void vertexNotDoubleCounted() {
        // A diamond whose left and right vertices sit exactly on y=5. Without the half-open rule each of
        // those vertices would be counted by both of its edges, giving four crossings and inverting the fill.
        List<int[]> diamond = List.of(p(5, 0), p(10, 5), p(5, 10), p(0, 5));
        List<Integer> atVertex = PolygonMask.intersections(diamond, 5);
        // The half-open rule assigns each vertex to exactly one of its two edges: the left vertex to the
        // (5,10)->(0,5) edge, the right vertex to the (10,5)->(5,10) edge. Two crossings, not four.
        check(atVertex.size() == 2, "a scanline through two vertices counts two crossings, not four (got "
                + atVertex.size() + ")");
        check(atVertex.get(0) == 0 && atVertex.get(1) == 10,
                "the crossings sit at the left and right vertices (got " + atVertex + ")");
    }

    /** Horizontal edges must never produce crossings: they overlap, not cross. */
    private static void horizontalEdges() {
        // A rectangle with horizontal top and bottom.
        List<int[]> rect = List.of(p(0, 0), p(10, 0), p(10, 5), p(0, 5));
        List<Integer> bottom = PolygonMask.intersections(rect, 0);
        check(bottom.size() == 2, "the bottom edge itself counts only its two endpoints (got "
                + bottom.size() + ")");
        // A scanline far outside the polygon crosses nothing.
        check(PolygonMask.intersections(rect, 20).isEmpty(), "a scanline above the shape crosses nothing");
        check(PolygonMask.intersections(rect, -5).isEmpty(), "a scanline below the shape crosses nothing");
    }

    /**
     * The fill direction: outside, not inside.
     *
     * <p>This is the exact "填反" failure the reference pack names. A mask filled the wrong way dims the
     * deployable land and leaves forbidden land bright — it inverts the rule on screen. {@code outsideRuns}
     * must return the outside ranges, never the inside ones, including across a concave notch.
     */
    private static void outsideFillDirection() {
        // Convex triangle at y=5: crossings [3,8]. Inside [3,8), outside [0,3) and [8,10).
        List<int[]> tri = PolygonMask.outsideRuns(List.of(3, 8), 0, 10);
        check(runsEqual(tri, List.of(r(0, 3), r(8, 10))),
                "a convex row dims left of and right of the inside run (got " + show(tri) + ")");

        // Concave arrow notch at y=7: crossings [2,3,7,9]. Inside [2,3) and [7,9); the notch [3,7) is OUTSIDE.
        List<int[]> arrow = PolygonMask.outsideRuns(List.of(2, 3, 7, 9), 0, 10);
        check(runsEqual(arrow, List.of(r(0, 2), r(3, 7), r(9, 10))),
                "a concave notch comes back as outside, not inside (got " + show(arrow) + ")");

        // A row fully inside the polygon dims nothing.
        check(PolygonMask.outsideRuns(List.of(0, 10), 0, 10).isEmpty(),
                "a fully-inside row dims nothing");

        // A row with no crossings dims the whole row (degenerate = safe outside).
        List<int[]> empty = PolygonMask.outsideRuns(List.of(), 0, 10);
        check(runsEqual(empty, List.of(r(0, 10))), "a crossing-free row dims the whole row (got " + show(empty) + ")");

        // An inside run straddling the right edge: inside [7,10), outside [0,7).
        List<int[]> straddle = PolygonMask.outsideRuns(List.of(7, 15), 0, 10);
        check(runsEqual(straddle, List.of(r(0, 7))),
                "an inside run past the right edge dims only left of it (got " + show(straddle) + ")");

        // An inside run entirely off-screen left: the whole visible row is outside.
        List<int[]> offLeft = PolygonMask.outsideRuns(List.of(-5, -2), 0, 10);
        check(runsEqual(offLeft, List.of(r(0, 10))),
                "an inside run off-screen left dims the whole row (got " + show(offLeft) + ")");
    }

    // ------------------------------------------------------------------ helpers

    private static int[] p(int x, int y) {
        return new int[] { x, y };
    }

    private static int[] r(int start, int end) {
        return new int[] { start, end };
    }

    private static boolean runsEqual(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) return false;
        return true;
    }

    private static String show(List<int[]> runs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < runs.size(); i++)
            sb.append(i > 0 ? ", " : "").append(runs.get(i)[0]).append('-').append(runs.get(i)[1]);
        return sb.append(']').toString();
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }
}
