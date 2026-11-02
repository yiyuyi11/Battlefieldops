package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screen-space polygon fill for the deploy area mask.
 *
 * <h2>What this reproduces</h2>
 *
 * <p>The reference pack's deploy view dims everything <em>outside</em> the team's deployable area with a
 * half-transparent mask ({@code drawOutsideMask}, alpha 0.48), so the player reads at a glance where they may
 * and may not spawn. The boundary is already drawn; the mask is what makes the boundary mean something —
 * without it the line reads as decoration, not as a rule.
 *
 * <h2>Why a scanline fill and not a bounding box</h2>
 *
 * <p>A deploy area is an arbitrary polygon, and the reference pack explicitly supports concave ones
 * ("凹多边形和多区域不能填反"). The fill therefore has to respect the polygon's actual edges, not its
 * bounding box — a bounding box would dim land the player is allowed to deploy on and leave forbidden land
 * bright, which inverts the rule it is supposed to show.
 *
 * <h2>The one rule that makes concave polygons correct</h2>
 *
 * <p>For each screen row the polygon's edges are intersected, the intersection x's are sorted, and the
 * row is filled <em>between every other pair</em> (even-odd rule). That is what handles concavity: a ray
 * through a concave polygon crosses its boundary an even number of times inside the shape, and the even-odd
 * pairing maps those crossings to the correct filled runs. The intersections use a half-open edge test
 * (a vertex belongs to exactly one of its two edges) so a row that lands exactly on a vertex does not
 * double-count it and mis-pair every run after it.
 *
 * <h2>What is filled is the outside</h2>
 *
 * <p>Filling the inside would dim the playable area. Filling the outside does the opposite — the playable
 * area stays bright, everything around it dims — which is the correct emphasis. The two outside runs of a
 * row are {@code [leftEdge, firstCrossing)} and {@code (lastCrossing, rightEdge]}, plus the gaps between
 * the even-odd pairs.
 */
final class PolygonMask {
    private PolygonMask() { }

    /**
     * Dams everything outside {@code polygon} inside the given rectangle.
     *
     * @param polygon screen-space polygon vertices, in order; {@code int[]{x, y}}
     * @param color   the mask colour, already carrying its alpha (the reference pack uses 0.48)
     */
    static void fillOutside(GuiGraphics g, List<int[]> polygon,
                            int minX, int minY, int maxX, int maxY, int color) {
        if (polygon == null || polygon.size() < 3) return;
        for (int y = Math.max(0, minY); y < maxY; y++) {
            for (int[] run : outsideRuns(intersections(polygon, y), minX, maxX)) {
                g.fill(run[0], y, run[1], y + 1, color);
            }
        }
    }

    /**
     * The outside runs for one scanline row, as {@code [start, end)} column ranges to fill.
     *
     * <p>Crossings are sorted. The polygon is inside on {@code [crossings[0],crossings[1])},
     * {@code [crossings[2],crossings[3])}, … (half-open, even-odd). The outside is everything else, clipped
     * to {@code [minX, maxX)}. Returning these ranges rather than filling them directly is what makes the
     * fill direction testable without a {@link GuiGraphics}: {@code PolygonMaskSim} asserts that a concave
     * polygon's notch comes back as <em>outside</em>, which is the exact "filled the wrong way" failure the
     * reference pack names.
     */
    static List<int[]> outsideRuns(List<Integer> crossings, int minX, int maxX) {
        List<int[]> runs = new ArrayList<>();
        if (crossings.size() < 2) {
            // A degenerate row (an edge coincides with the scanline, or no crossings at all). The whole row
            // is outside: for a row the polygon barely touches, dimming it is the safe choice.
            if (minX < maxX) runs.add(new int[] { minX, maxX });
            return runs;
        }
        int cursor = minX;
        for (int i = 0; i + 1 < crossings.size(); i += 2) {
            // The inside run [left, right) clipped to the window. An inside run entirely off-screen leaves
            // the cursor where it is, so the outside continues across it.
            int insideStart = Math.max(minX, Math.min(maxX, crossings.get(i)));
            int insideEnd = Math.max(minX, Math.min(maxX, crossings.get(i + 1)));
            if (insideEnd > insideStart) {
                if (cursor < insideStart) runs.add(new int[] { cursor, insideStart });
                cursor = Math.max(cursor, insideEnd);
            }
        }
        if (cursor < maxX) runs.add(new int[] { cursor, maxX });
        return runs;
    }

    /**
     * The x-coordinates where {@code polygon} crosses the horizontal line {@code y}, sorted ascending.
     *
     * <p>Half-open edge test: an edge contributes a crossing only when its lower endpoint is at or below
     * {@code y} and its upper endpoint is strictly above {@code y}. That assigns a vertex to exactly one
     * of its two edges, so a scanline through a vertex produces the correct, un-doubled crossing count.
     * Horizontal edges contribute nothing, which is correct — a horizontal edge overlaps the scanline and
     * must not count as a crossing.
     *
     * <p>Returned sorted, because the caller immediately needs the crossings in x order for the even-odd
     * pairing; returning them in edge-traversal order would force every caller to remember to sort, and a
     * caller that forgot would pair the wrong crossings and invert the fill.
     */
    static List<Integer> intersections(List<int[]> polygon, int y) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            int[] a = polygon.get(i);
            int[] b = polygon.get((i + 1) % polygon.size());
            int ay = a[1], by = b[1];
            // Lower endpoint at or below the line, upper endpoint strictly above: the half-open rule.
            boolean crosses = (ay <= y && by > y) || (by <= y && ay > y);
            if (!crosses) continue;
            // Linear interpolation along the edge to the scanline.
            double t = (y - ay) / (double) (by - ay);
            out.add((int) Math.round(a[0] + (b[0] - a[0]) * t));
        }
        Collections.sort(out);
        return out;
    }
}
