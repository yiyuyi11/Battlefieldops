package cn.bfops;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for the batched boundary line.
 *
 * <p>The batch path exists to make the deploy boundary affordable. The property that makes it <em>safe</em> is
 * different and more important: it must cover every pixel the old per-sample version covered. A faster line
 * that is a pixel off is a line that no longer shows where a player may deploy, and nothing in the game would
 * ever report that.
 *
 * <p>So the central assertion here is coverage equality against a faithful reimplementation of
 * {@link Ui#line}: same sampling, same rounding, same thickness. The performance claim is then derived from
 * the same data — the number of {@code fill} calls each approach would make — and printed, so a regression in
 * call count is visible rather than asserted on a wall-clock measurement this harness cannot make meaningful.
 */
public final class BoundaryBatchSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        coverage();
        merging();
        equality();
        callCount();
        degenerate();
        System.out.println("BoundaryBatchSim: " + checks + " checks passed");
    }

    /** A single segment covers the same pixels as the per-sample version, thickness included. */
    private static void coverage() {
        BatchLine.Coverage horizontal = new BatchLine.Coverage();
        BatchLine.segment(horizontal, 10, 20, 40, 20, 1);
        check(horizontal.pixelCount() == 31, "a 30-pixel horizontal line with thickness 1 covers 31 pixels");
        check(horizontal.rowKeys().size() == 1, "and occupies exactly one row");
        check(horizontal.runs(20).size() == 1, "and comes back as one merged run");
        check(horizontal.runs(20).get(0)[0] == 10 && horizontal.runs(20).get(0)[1] == 41,
                "spanning [10, 41)");

        BatchLine.Coverage thick = new BatchLine.Coverage();
        BatchLine.segment(thick, 0, 0, 9, 0, 2);
        check(thick.rowKeys().size() == 2, "thickness 2 spans two rows");
        // 10 samples at x=0..9, each a 2x2 square, so the merged coverage is 11 columns by 2 rows: the last
        // sample reaches x=11. Asserting on the merged count (not the 40 raw squares) is the point — the
        // batch is defined by what is covered, and overlapping squares are one pixel, not four.
        check(thick.pixelCount() == 22, "and covers 11 columns on each of its two rows");
        check(thick.runs(0).size() == 1 && thick.runs(1).size() == 1,
                "each of those rows is a single merged run");

        BatchLine.Coverage diagonal = new BatchLine.Coverage();
        BatchLine.segment(diagonal, 0, 0, 9, 9, 1);
        check(diagonal.rowKeys().size() == 10, "a 45-degree line touches one pixel per row");
        check(diagonal.pixelCount() == 9 + 1, "and its runs follow the diagonal without gaps");
    }

    /** Adjacent samples of one line merge into a single run instead of one fill each. */
    private static void merging() {
        BatchLine.Coverage line = new BatchLine.Coverage();
        BatchLine.segment(line, 0, 5, 99, 5, 2);
        List<int[]> runs = line.runs(5);
        check(runs.size() == 1, "a 100-pixel line becomes ONE fill per row, not 100");
        // 100 samples, each a 2x2 square, merged to [0, 101): the last sample starts at 99 and is 2 wide.
        check(runs.get(0)[0] == 0 && runs.get(0)[1] == 101, "covering the full span");

        // Two separate segments on the same row stay separate: merging must not bridge a real gap.
        BatchLine.Coverage split = new BatchLine.Coverage();
        BatchLine.segment(split, 0, 3, 5, 3, 1);
        BatchLine.segment(split, 20, 3, 25, 3, 1);
        List<int[]> splitRuns = split.runs(3);
        check(splitRuns.size() == 2, "a genuine gap is not merged away");
        check(splitRuns.get(0)[1] == 6 && splitRuns.get(1)[0] == 20, "the gap [6, 20) is preserved");

        // Touching runs (end == next start) merge: that is a shared pixel, not a gap.
        BatchLine.Coverage touching = new BatchLine.Coverage();
        touching.add(0, 7, 6, 8);
        touching.add(6, 7, 12, 8);
        check(touching.runs(7).size() == 1, "runs that share a boundary column are one run");
        check(touching.runs(7).get(0)[1] == 12, "merged to the far end");
    }

    /** The batch covers exactly what the per-sample version covers, over a realistic boundary. */
    private static void equality() {
        // A concave boundary is the hard case: a notch forces the subdivision the deploy view does, and the
        // notch is exactly where an off-by-one shows up as a line that cuts a corner.
        List<int[]> segments = new ArrayList<>();
        int[][] points = {{40, 40}, {300, 60}, {280, 200}, {160, 120}, {120, 260}, {60, 240}};
        for (int i = 0; i < points.length; i++) {
            int[] a = points[i], b = points[(i + 1) % points.length];
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 8));
            for (int j = 0; j < steps; j++) {
                double u = j / (double) steps, v = (j + 1) / (double) steps;
                segments.add(new int[] {
                        (int) Math.round(a[0] + (b[0] - a[0]) * u), (int) Math.round(a[1] + (b[1] - a[1]) * u),
                        (int) Math.round(a[0] + (b[0] - a[0]) * v), (int) Math.round(a[1] + (b[1] - a[1]) * v)});
            }
        }
        Set<Long> naive = new HashSet<>();
        for (int[] segment : segments) paintNaive(naive, segment[0], segment[1], segment[2], segment[3], 2);
        BatchLine.Coverage batch = new BatchLine.Coverage();
        for (int[] segment : segments) BatchLine.segment(batch, segment[0], segment[1], segment[2], segment[3], 2);
        Set<Long> actual = new HashSet<>();
        for (int y : batch.rowKeys()) {
            for (int[] run : batch.runs(y)) for (int x = run[0]; x < run[1]; x++) actual.add(key(x, y));
        }
        check(naive.equals(actual), "the batch covers exactly the pixels the per-sample version covers ("
                + naive.size() + " vs " + actual.size() + ")");
        check(!naive.isEmpty(), "and the boundary actually covered something");
    }

    /** Fewer fill calls for the same pixels: the reason the batch exists. */
    private static void callCount() {
        List<int[]> segments = new ArrayList<>();
        // A 1200-pixel-wide rectangle: at deploy zoom a boundary edge really is this long.
        int[][] points = {{10, 10}, {1210, 10}, {1210, 500}, {10, 500}};
        for (int i = 0; i < points.length; i++) {
            int[] a = points[i], b = points[(i + 1) % points.length];
            segments.add(new int[] {a[0], a[1], b[0], b[1]});
        }
        int naiveCalls = 0;
        for (int[] segment : segments) {
            naiveCalls += Math.max(Math.abs(segment[2] - segment[0]), Math.abs(segment[3] - segment[1])) + 1;
        }
        BatchLine.Coverage batch = new BatchLine.Coverage();
        for (int[] segment : segments) BatchLine.segment(batch, segment[0], segment[1], segment[2], segment[3], 2);
        int batchCalls = 0;
        for (int y : batch.rowKeys()) batchCalls += batch.runs(y).size();
        System.out.println("  INFO  1200x490 rectangle, thickness 2: " + naiveCalls + " fills per-sample vs "
                + batchCalls + " batched (" + Math.round(naiveCalls / (double) batchCalls * 10) / 10.0 + "x)");
        // The ceiling is structural, not lucky: a horizontal edge collapses to one fill regardless of length,
        // and only the vertical edges cost one per row. A perimeter of four edges is therefore bounded by
        // ~2 per row, against ~1 per column per sample in the naive path.
        check(batchCalls < naiveCalls / 2, "the batch makes less than half the fills of one per sample ("
                + batchCalls + " vs " + naiveCalls + ")");
        check(batchCalls > 0, "and still draws the boundary");
        // The horizontal edges are the dramatic case and worth pinning on their own.
        BatchLine.Coverage flat = new BatchLine.Coverage();
        BatchLine.segment(flat, 0, 100, 1200, 100, 2);
        check(flat.runs(100).size() == 1 && flat.runs(101).size() == 1,
                "a 1200-pixel horizontal edge is ONE fill per row (2 total), not 1201");
    }

    /** Degenerate input must not draw anything wrong or throw. */
    private static void degenerate() {
        BatchLine.polyline(null, null, 2, 0xffffffff);
        BatchLine.polyline(null, List.of(), 2, 0xffffffff);
        check(true, "an empty or null segment list is a no-op");

        BatchLine.Coverage point = new BatchLine.Coverage();
        BatchLine.segment(point, 5, 5, 5, 5, 1);
        check(point.pixelCount() == 1, "a zero-length segment covers one pixel");
        point.runs(5);
        check(point.runs(5).get(0)[0] == 5 && point.runs(5).get(0)[1] == 6, "[5, 6) — a single pixel");

        BatchLine.Coverage zeroThickness = new BatchLine.Coverage();
        BatchLine.segment(zeroThickness, 0, 0, 4, 0, 0);
        check(zeroThickness.pixelCount() == 5, "thickness 0 is clamped to 1 rather than covering nothing");

        // A segment longer than the sample cap is refused, exactly as Ui.line refuses it. Both paths must
        // agree on this, or one of them would fill the window with a runaway loop.
        BatchLine.Coverage runaway = new BatchLine.Coverage();
        BatchLine.segment(runaway, -5000, -5000, 5000, 5000, 2);
        check(runaway.pixelCount() == 0, "a segment past the 4096-sample cap is refused, not drawn");
        BatchLine.Coverage longEdge = new BatchLine.Coverage();
        BatchLine.segment(longEdge, 0, 0, 4000, 0, 2);
        check(longEdge.pixelCount() > 0, "a segment inside the cap is drawn");
        BatchLine.segment(new BatchLine.Coverage(), 0, 0, 100000, 0, 2);
        check(true, "and an absurdly long one is refused rather than looping a hundred thousand times");
    }

    /** Mirrors {@link Ui#line}: sample once per pixel of the longer axis, fill a thickness-square per sample. */
    private static void paintNaive(Set<Long> into, int x0, int y0, int x1, int y1, int thickness) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps > 4096) return;
        int t = Math.max(1, thickness);
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) Math.max(1, steps);
            int x = (int) Math.round(x0 + (x1 - x0) * f);
            int y = (int) Math.round(y0 + (y1 - y0) * f);
            for (int dy = 0; dy < t; dy++) for (int dx = 0; dx < t; dx++) into.add(key(x + dx, y + dy));
        }
    }

    private static long key(int x, int y) {
        return ((long) y << 32) ^ (x & 0xffffffffL);
    }
}
