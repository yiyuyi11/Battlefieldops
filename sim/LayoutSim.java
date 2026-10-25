package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline checks for the window geometry.
 *
 * <p>These exist because the reported bug — "the right-hand map looks like it is on a different layer
 * than the left column" — was a geometry and Z-order problem, and geometry is testable without a
 * running client. Each assertion below corresponds to one way the old ad-hoc layout could break:
 *
 * <ul>
 *   <li>a band escaping the window (controls drawn off-screen at small resolutions),</li>
 *   <li>two bands overlapping (a control painted over the pane beside it),</li>
 *   <li>the map pane shrinking below legibility while the control column kept its size,</li>
 *   <li>a grid finer than one block per pixel (the "blurry map" complaint).</li>
 * </ul>
 */
public final class LayoutSim {
    private static int checks;

    public static void main(String[] args) {
        List<int[]> sizes = new ArrayList<>();
        // Hand-picked extremes plus a sweep across the range players actually use. 320x240 is the
        // practical floor for Minecraft's GUI, and 3840x2160 is a 4K window at GUI scale 1.
        for (int[] size : new int[][]{
                {320, 240}, {320, 200}, {400, 300}, {427, 240}, {640, 480}, {854, 480},
                {960, 540}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080},
                {2560, 1440}, {3840, 2160}, {512, 256}, {300, 400}}) {
            sizes.add(size);
        }
        for (int w = 320; w <= 1920; w += 37) {
            for (int h = 240; h <= 1080; h += 53) sizes.add(new int[]{w, h});
        }

        for (int[] size : sizes) check(size[0], size[1]);

        crispness();
        grid();
        columns();
        fitRows();

        System.out.println("LayoutSim: " + checks + " checks passed across " + sizes.size() + " window sizes");
    }

    private static void check(int width, int height) {
        Layout layout = Layout.of(width, height);
        String at = width + "x" + height;

        // Every band is inside the window. The old layout could place a button at a hard-coded y that
        // fell past the bottom edge on short windows.
        for (Layout.Rect band : new Layout.Rect[]{
                layout.tabs, layout.title, layout.content, layout.bar, layout.footer}) {
            require(band.x() >= 0 && band.y() >= 0, at + ": band starts outside the window");
            require(band.right() <= width, at + ": band overflows the right edge: " + band);
            require(band.bottom() <= height, at + ": band overflows the bottom edge: " + band);
        }

        // Vertical order holds and nothing overlaps: this is what makes the two panes read as one
        // window instead of two layers.
        require(layout.tabs.y() < layout.title.y(), at + ": title above tabs");
        require(layout.title.y() < layout.content.y(), at + ": content below title");
        require(layout.content.bottom() <= layout.bar.y(), at + ": content overlaps the action bar");
        require(layout.bar.bottom() <= layout.footer.y(), at + ": action bar overlaps the footer");
        require(!layout.content.overlaps(layout.bar), at + ": content/bar overlap");
        require(!layout.bar.overlaps(layout.footer), at + ": bar/footer overlap");

        // The two panes of the working area never overlap and together stay inside it.
        require(!layout.left.overlaps(layout.right), at + ": the two panes overlap");
        require(layout.left.x() >= layout.content.x(), at + ": left pane outside content");
        require(layout.right.right() <= layout.content.right(), at + ": right pane outside content");
        require(layout.left.w() >= 1 && layout.right.w() >= 1, at + ": a pane has no width");

        // The map keeps the majority of the width: a canvas under ~60% of the content is not a map.
        if (width >= 320) {
            require(layout.right.w() >= layout.content.w() * 55 / 100,
                    at + ": right pane only " + layout.right.w() + "px of " + layout.content.w());
        }

        // The control column stays usable but never eats the canvas.
        require(layout.left.w() <= Math.max(Layout.LEFT_MIN, Layout.LEFT_MAX) || width < 320,
                at + ": left pane wider than its cap");
        if (height >= 260) {
            require(layout.content.h() >= 60, at + ": content only " + layout.content.h() + "px tall");
            require(layout.bar.h() > 0, at + ": action bar missing on a tall window");
        }

        // Both panes have to fit at least one control row, or the form silently draws nothing.
        if (layout.left.h() >= Layout.ROW) {
            require(Layout.fitRows(layout.left, Layout.ROW, Layout.GAP) >= 1,
                    at + ": left pane cannot fit a single row");
        }
        checks++;
    }

    /** A drawn polygon must not be finer than the pixel grid, or it looks like a smear. */
    private static void crispness() {
        for (int width = 120; width <= 1200; width += 7) {
            double atOnePixel = Layout.blocksPerPixel(width, width);
            require(Math.abs(atOnePixel - 1.0) < 1e-9, "1 block per pixel is not 1: " + atOnePixel);
            require(Layout.crisp(width, width), "span == width should be crisp at " + width);
            require(!Layout.crisp(width * 2, width), "span == 2*width should not be crisp at " + width);
            require(Layout.blocksPerPixel(width, 0) == width,
                    "zero width must not divide by zero");
            checks++;
        }
        // The editor's "1 格 / 像素" button sets exactly this, so the two must agree.
        int canvas = 329;
        require(Layout.crisp(canvas, canvas), "canvas-width span should be crisp");
    }

    /** The grid has to be a power of two, coarse enough to read, and never sub-pixel. */
    private static void grid() {
        int[][] cases = {{256, 400}, {512, 400}, {1024, 320}, {16, 400}, {4096, 160}};
        for (int[] c : cases) {
            int span = c[0], width = c[1];
            double step = Layout.gridStep(span, width);
            require(step > 0, "grid step must be positive");
            require(step >= Layout.blocksPerPixel(span, width) - 1e-9,
                    "grid finer than a pixel at " + span + "/" + width + " -> " + step);
            boolean powerOfTwo = Math.abs(step - Math.pow(2, Math.round(Math.log(step) / Math.log(2)))) < 1e-6;
            require(powerOfTwo, "grid step not a power of two: " + step);
            // At least ~4 lines across the view, otherwise the grid is invisible noise.
            require(span / step >= 4, "grid too coarse to read: " + (span / step) + " lines");
            checks++;
        }
    }

    private static void columns() {
        Layout.Rect area = new Layout.Rect(10, 20, 300, 40);
        Layout.Rect[] three = Layout.columns(area, 3, 5);
        require(three.length == 3, "three columns expected");
        require(three[0].right() <= three[1].x(), "columns overlap");
        require(three[2].right() <= area.right(), "last column overflows");
        require(three[2].right() > area.right() - 5, "last column leaves a big gap");
        Layout.Rect[] none = Layout.columns(area, 0, 5);
        require(none.length == 0, "zero columns expected");
        // Degenerate widths must not produce negative rects.
        Layout.Rect[] tight = Layout.columns(new Layout.Rect(0, 0, 4, 10), 3, 5);
        for (Layout.Rect rect : tight) require(rect.w() >= 0, "negative column width");
        checks++;
    }

    private static void fitRows() {
        // 4 rows of 18 with 3 gaps of 5 is 87px; a 5th would need 110px.
        require(Layout.fitRows(new Layout.Rect(0, 0, 100, 100), 18, 5) == 4, "fitRows(100,18,5)");
        require(Layout.fitRows(new Layout.Rect(0, 0, 100, 110), 18, 5) == 5, "fitRows(110,18,5)");
        require(Layout.fitRows(new Layout.Rect(0, 0, 100, 10), 18, 5) == 1, "fitRows floors at one");
        require(Layout.fitRows(new Layout.Rect(0, 0, 100, 0), 18, 5) == 1, "fitRows on empty area");
        checks++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
