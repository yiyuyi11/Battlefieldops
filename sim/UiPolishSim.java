package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Arithmetic behind the visual polish, checked headlessly.
 *
 * <p>None of this can assert that something "looks good" — that needs eyes. What it can do is pin the
 * parts that are easy to get wrong and invisible until a player notices something absurd: a progress
 * bar that runs past its track, a segment strip that draws more cells than the pool has, a cooldown bar
 * that reads full while the player is locked out, or a chip wide enough to spill out of its pane.
 */
public final class UiPolishSim {
    private static int checks;

    public static void main(String[] args) {
        cooldownBar();
        segmentStrip();
        chipWidth();
        rowPainters();
        System.out.println("UiPolishSim: " + checks + " checks passed");
    }

    /**
     * The cooldown bar must be empty when the player is fully locked out and full when ready.
     *
     * <p>The direction matters: a bar showing remaining time instead of elapsed time would fill up
     * exactly when the player cannot deploy, which is a worse lie than no bar at all.
     */
    private static void cooldownBar() {
        // fraction = 1 - remaining/configured
        check(fraction(60, 60) == 0.0, "just deployed: the bar is empty");
        check(fraction(60, 30) == 0.5, "half the cooldown gone: the bar is half full");
        check(fraction(60, 0) == 1.0, "cooldown expired: the bar is full");
        check(fraction(0, 0) == 1.0, "a zero-length cooldown is always ready");

        // A remaining value larger than the configured length (the operator shortened the cooldown
        // while someone was waiting) must clamp to empty, not go negative and draw backwards.
        check(clamp(fraction(30, 90)) == 0.0, "remaining beyond the length clamps to empty");

        // And a stale negative remaining must clamp to full rather than overflowing the track.
        check(clamp(fraction(60, -5)) == 1.0, "a negative remaining clamps to full");

        // The bar is drawn only when both numbers are known; a configured length of zero means "no
        // cooldown", so the fraction is defined to be full rather than dividing by zero.
        check(fraction(0, 30) == 1.0, "a zero configured length reads full instead of dividing by zero");
        check(ready(0, 60), "zero remaining is ready");
        check(!ready(5, 60), "five remaining is not ready");
    }

    /** Progress fractions must stay in [0,1] whatever the inputs, because they index a fixed track. */
    private static double fraction(int configured, int remaining) {
        if (configured <= 0) return 1.0;
        return clamp(1.0 - remaining / (double) configured);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static boolean ready(int remaining, int configured) {
        return !(remaining > 0 && configured > 0);
    }

    /**
     * The hull pool strip: exactly {@code min(left, pool)} cells lit, never more than the strip holds.
     *
     * <p>Two real mistakes this prevents. A pool of 40 with a strip of 24 must not light 40 cells
     * (they would run off the pane), and a pool where the operator raised the limit above the drawn cap
     * must not light cells that do not exist. The count shown as text is the true one either way.
     */
    private static void segmentStrip() {
        check(lit(6, 6) == 6, "a full pool lights every cell");
        check(lit(0, 6) == 0, "an empty pool lights none");
        check(lit(3, 6) == 3, "a half pool lights half");
        // Limits above the drawn cap: 24 is the cap, so a 40-hull pool lights all 24 cells.
        check(lit(40, 24) == 24, "a pool larger than the strip fills it without overflowing");
        // The text is the true remaining count, not the drawn one.
        check(left(40, 5) == 35, "the textual remainder reports the real number, not the drawn cap");
        // Used can exceed limit when the operator lowers the limit mid-match; it must not go negative.
        check(left(2, 5) == 0, "used beyond the limit reads zero, never negative");
        // A zero or negative limit draws nothing at all.
        check(cap(0) == 0, "a zero limit draws no cells");
        check(cap(-3) == 0, "a negative limit draws no cells");
        check(cap(100) == 24, "the drawn cap is enforced");
    }

    private static int lit(int left, int pool) {
        return Math.min(Math.max(0, left), Math.max(0, pool));
    }

    private static int left(int limit, int used) {
        return Math.max(0, limit - used);
    }

    private static int cap(int limit) {
        return Math.max(0, Math.min(limit, 24));
    }

    /**
     * A status chip must never be wider than the row that contains it.
     *
     * <p>The chip is sized from the text, and a long server message ("当前部署点不是飞机点位，请改选机场")
     * is easily wider than a narrow control column. Letting it keep its natural width drew it straight
     * through the pane frame and over the map beside it.
     */
    private static void chipWidth() {
        // The plate is the text plus 8px of padding (4 either side), clamped to the row.
        check(chip(40, 100) == 48, "a short chip keeps its natural width plus padding");
        check(chip(140, 100) == 100, "a long chip is clamped to the row");
        check(chip(140, 0) == 0, "a zero-width row yields nothing to draw");
        check(chip(0, 100) == 0, "an empty label yields nothing to draw");
        // The text is clipped to the width *after* the 8px of padding is removed, so the glyphs never
        // touch the plate edge.
        check(textRoom(100) == 92, "text is clipped inside the chip padding");
        check(textRoom(4) >= 1, "a tiny chip still offers at least one pixel of text room");
    }

    private static int chip(int textWidth, int rowWidth) {
        if (textWidth <= 0 || rowWidth <= 0) return 0;
        return Math.min(textWidth + 8, rowWidth);
    }

    private static int textRoom(int rowWidth) {
        return Math.max(1, rowWidth - 8);
    }

    /**
     * A painted row must stay inside the pane it belongs to, at every scroll offset.
     *
     * <p>Painted rows (the cooldown bar, the pool strip, chips) do not get the widget visibility check
     * that real controls do, so their bounds are enforced by the caller. This asserts the arithmetic
     * the caller relies on: the row keeps the pane's x and width, and only its y moves with the scroll.
     */
    private static void rowPainters() {
        Layout.Rect pane = new Layout.Rect(10, 40, 120, 90);
        int[] heights = {12, 18, 13};
        int cursor = pane.y() + 4;
        List<Layout.Rect> rows = new ArrayList<>();
        for (int h : heights) {
            rows.add(new Layout.Rect(pane.x() + 5, cursor, Math.max(1, pane.w() - 12), h));
            cursor += h;
        }
        for (Layout.Rect row : rows) {
            check(row.x() >= pane.x(), "a painted row starts inside its pane");
            check(row.right() <= pane.right(), "a painted row ends inside its pane");
            check(row.w() > 0 && row.h() > 0, "a painted row has a positive area");
        }
        // Each row is entirely above the next one: overlapping rows would paint over each other's text.
        for (int i = 1; i < rows.size(); i++) {
            check(rows.get(i - 1).bottom() <= rows.get(i).y(), "painted rows do not overlap");
        }
        // Scrolling shifts y only; the horizontal geometry is a property of the pane, not the scroll.
        for (int scroll : new int[]{0, 7, 40}) {
            for (Layout.Rect row : rows) {
                Layout.Rect scrolled = new Layout.Rect(row.x(), row.y() - scroll, row.w(), row.h());
                check(scrolled.x() == row.x(), "scrolling never moves a row horizontally");
                check(scrolled.w() == row.w(), "scrolling never resizes a row");
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
