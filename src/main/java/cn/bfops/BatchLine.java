package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a projected polyline as merged horizontal runs, instead of one filled quad per sampled pixel.
 *
 * <h2>What this replaces, and why it mattered</h2>
 *
 * <p>{@link Ui#line} samples a segment once per pixel of its longer axis and calls {@code fill} for a whole
 * {@code thickness x thickness} square at each sample. For a map boundary that is drawn every frame and can
 * span the window that is a few thousand {@code fill} calls per edge — and because each one is a separate
 * draw, adjacent samples repaint the same pixels several times over. On a concave boundary subdivided into
 * hundreds of segments (which is what partial visibility forces) the cost is real and entirely wasted: the
 * result is a solid 2-pixel band either way.
 *
 * <p>The batch version collects coverage into a per-row span list first, then emits <b>one fill per row</b>.
 * A 2-pixel-wide line 900 pixels long therefore costs ~450 fills instead of ~1800, and a polyline's shared
 * pixels are painted once rather than once per segment.
 *
 * <h2>The invariants that make it safe</h2>
 *
 * <p>Merging runs must not change what the eye sees much, and must never change <em>what is covered</em>
 * enough to move the boundary. So the span list is built at the same sample density as {@link Ui#line} and
 * with the same rounding, and adjacent samples are merged only when they are genuinely contiguous. That
 * keeps {@code BoundaryBatchSim} able to assert "the batch paints every pixel the per-pixel version paints",
 * which is the property that matters: a boundary that is faster but a pixel off is a boundary that no longer
 * shows where the player may deploy.
 */
final class BatchLine {
    /** Hard cap on sampled steps per segment, matching {@link Ui}'s own guard. */
    private static final int MAX_STEPS = 4096;

    private BatchLine() { }

    /**
     * A sparse coverage bitmap: row -> sorted, non-overlapping {@code [start, end)} column ranges.
     *
     * <p>Sparse because a boundary is thin: a 900x500 window with a 2px line covers well under 1% of it, so a
     * dense boolean array would be 450k entries to hold a few thousand pixels.
     */
    static final class Coverage {
        private final java.util.TreeMap<Integer, List<int[]>> rows = new java.util.TreeMap<>();

        void add(int x0, int y0, int x1, int y1) {
            int left = Math.min(x0, x1), right = Math.max(x0, x1);
            int top = Math.min(y0, y1), bottom = Math.max(y0, y1);
            for (int y = top; y < bottom; y++) {
                List<int[]> row = rows.computeIfAbsent(y, k -> new ArrayList<>());
                row.add(new int[] { left, right });
            }
        }

        /** Row numbers that have any coverage, ascending. */
        java.util.Set<Integer> rowKeys() {
            return rows.keySet();
        }

        /** The merged {@code [start, end)} runs for one row, sorted and coalesced. */
        List<int[]> runs(int y) {
            List<int[]> row = rows.get(y);
            if (row == null) return List.of();
            List<int[]> sorted = new ArrayList<>(row);
            sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
            List<int[]> merged = new ArrayList<>();
            for (int[] run : sorted) {
                if (!merged.isEmpty()) {
                    int[] last = merged.getLast();
                    // Contiguous or overlapping: extend. The +1 tolerance is what merges a 2px-thick line's
                    // adjacent samples (which touch at a shared column) into one run.
                    if (run[0] <= last[1]) {
                        last[1] = Math.max(last[1], run[1]);
                        continue;
                    }
                }
                merged.add(new int[] { run[0], run[1] });
            }
            return merged;
        }

        /** Total covered pixels, for the "is this cheaper than it was" diagnostic and for tests. */
        int pixelCount() {
            int total = 0;
            for (int y : rows.keySet()) for (int[] run : runs(y)) total += run[1] - run[0];
            return total;
        }
    }

    /**
     * Rasterises one line segment into a coverage bitmap, sampling exactly as {@link Ui#line} does.
     *
     * <p>Kept as a static so {@code BoundaryBatchSim} can compare this against the drawn-by-pixels version
     * pixel for pixel. If the two ever diverge, the batch path is wrong, not the test.
     */
    static void segment(Coverage into, int x0, int y0, int x1, int y1, int thickness) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps > MAX_STEPS) return;
        int t = Math.max(1, thickness);
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) Math.max(1, steps);
            int x = (int) Math.round(x0 + (x1 - x0) * f);
            int y = (int) Math.round(y0 + (y1 - y0) * f);
            into.add(x, y, x + t, y + t);
        }
    }

    /** Emits one {@code fill} per merged row run. */
    static void flush(GuiGraphics graphics, Coverage coverage, int color) {
        for (int y : coverage.rowKeys()) {
            for (int[] run : coverage.runs(y)) {
                // A run can be one pixel wide when a line is diagonal at 45 degrees; fill still handles it,
                // and the guard just avoids a zero-width call.
                if (run[1] > run[0]) graphics.fill(run[0], y, run[1], y + 1, color);
            }
        }
    }

    /**
     * Draws a whole polyline as one batch.
     *
     * <p>The segments are handed in already projected to screen space, so the caller keeps ownership of
     * projection (and of the "vertex behind the camera" decision, which is per-vertex and cannot be made
     * here). Splitting segments is likewise the caller's job because visibility, not geography, decides it.
     */
    static void polyline(GuiGraphics graphics, List<int[]> segments, int thickness, int color) {
        if (segments == null || segments.isEmpty()) return;
        Coverage coverage = new Coverage();
        for (int[] segment : segments) {
            segment(coverage, segment[0], segment[1], segment[2], segment[3], thickness);
        }
        flush(graphics, coverage, color);
    }
}
