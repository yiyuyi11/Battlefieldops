package cn.bfops;

/**
 * Window geometry shared by every screen in this mod.
 *
 * <p>The previous layout hard-coded pixel offsets inside each tab, so the left controls floated as
 * translucent rows straight over the world while the right-hand map was an opaque rectangle painted
 * in a different pass. That is what read as "the two sides are on different layers". The other
 * complaint, blur, came from thin alpha strokes over terrain and from vanilla's blurred menu
 * background being painted *after* the map.
 *
 * <p>So the geometry lives here once. Every screen asks for the same rectangles and paints them with
 * the same theme ({@link Ui}). Nothing in this class touches Minecraft, which lets
 * {@code sim/LayoutSim} assert the invariants headlessly across many window sizes.
 *
 * <p>Vertical order, top to bottom: {@code tabs}, {@code title}, {@code content}, {@code bar},
 * {@code footer}. The bands never overlap and are always inside the window, which is the invariant
 * that stops a control from ever floating outside its pane.
 */
final class Layout {
    /** Height of one control row. */
    static final int ROW = 18;
    static final int GAP = 5;
    static final int PAD = 4;
    static final int LEFT_MIN = 108;
    static final int LEFT_MAX = 184;

    record Rect(int x, int y, int w, int h) {
        Rect {
            w = Math.max(0, w);
            h = Math.max(0, h);
        }

        int right() { return x + w; }
        int bottom() { return y + h; }
        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }

        boolean inside(double mx, double my) {
            return mx >= x && mx < right() && my >= y && my < bottom();
        }

        Rect inset(int d) { return new Rect(x + d, y + d, w - 2 * d, h - 2 * d); }

        Rect pad() { return inset(PAD); }

        Rect withHeight(int height) { return new Rect(x, y, w, height); }

        Rect withY(int newY) { return new Rect(x, newY, w, h); }

        /** Same left edge and height, starting {@code amount} pixels lower. */
        Rect below(int amount) { return new Rect(x, y + amount, w, h); }

        boolean overlaps(Rect other) {
            return w > 0 && h > 0 && other.w > 0 && other.h > 0
                    && x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }

        Rect clippedTo(Rect bounds) {
            int x0 = Math.max(x, bounds.x()), y0 = Math.max(y, bounds.y());
            int x1 = Math.min(right(), bounds.right()), y1 = Math.min(bottom(), bounds.bottom());
            return new Rect(x0, y0, x1 - x0, y1 - y0);
        }

        /** A slice of this rect: {@code index} of {@code count} equal bands, no gaps. */
        Rect band(int index, int count) {
            int top = y + h * index / Math.max(1, count);
            int bottom = y + h * (index + 1) / Math.max(1, count);
            return new Rect(x, top, w, bottom - top);
        }
    }

    final int width, height;
    /** Tab strip across the top of the window. */
    final Rect tabs;
    /** One-line window title under the tabs. */
    final Rect title;
    /** Everything between the title and the action bar: the working area. */
    final Rect content;
    /** Narrow control column on the left; every tab puts its buttons and fields here. */
    final Rect left;
    /** The wide pane: map canvas, catalogue list or preview depending on the tab. */
    final Rect right;
    /** Bottom action bar, for 关闭 / 保存 and similar window-level actions. May be empty. */
    final Rect bar;
    /** Message line under the action bar. */
    final Rect footer;

    private Layout(int width, int height, Rect tabs, Rect title, Rect content,
                   Rect left, Rect right, Rect bar, Rect footer) {
        this.width = width;
        this.height = height;
        this.tabs = tabs;
        this.title = title;
        this.content = content;
        this.left = left;
        this.right = right;
        this.bar = bar;
        this.footer = footer;
    }

    static Layout of(int width, int height) {
        int margin = width < 340 || height < 260 ? 2 : 4;
        int tabHeight = height < 210 ? 12 : (height < 300 ? 15 : 18);
        int titleHeight = height < 250 ? 10 : 13;
        int barHeight = 24;
        int footerHeight = 22;

        Rect tabs = new Rect(margin, margin, width - 2 * margin, tabHeight);
        Rect title = new Rect(margin, tabs.bottom() + 2, width - 2 * margin, titleHeight);

        int footerY = height - margin - footerHeight;
        Rect footer = new Rect(margin, footerY, width - 2 * margin, footerHeight);
        int barY = footerY - 2 - barHeight;
        Rect bar = new Rect(margin, Math.max(title.bottom() + 1, barY), width - 2 * margin, barHeight);

        int top = title.bottom() + 3;
        int bottom = Math.max(top + 1, bar.y() - 3);
        Rect content = new Rect(margin, top, width - 2 * margin, bottom - top);

        int leftWidth = Math.max(LEFT_MIN, Math.min(LEFT_MAX, content.w() / 3));
        int widest = Math.min(content.w() - GAP - 1, leftWidth);
        // The wide pane keeps at least 60% of the content width: a canvas squeezed under ~160px is
        // unreadable, so the control column gives way first.
        widest = Math.min(widest, Math.max(1, (content.w() - GAP) * 35 / 100));
        widest = Math.max(1, widest);

        Rect left = new Rect(content.x(), content.y(), widest, content.h());
        Rect right = new Rect(left.right() + GAP, content.y(),
                Math.max(1, content.right() - left.right() - GAP), content.h());
        return new Layout(width, height, tabs, title, content, left, right, bar, footer);
    }

    /** Equal-width columns inside an area, left to right. */
    static Rect[] columns(Rect area, int count, int gap) {
        if (count <= 0) return new Rect[0];
        int width = Math.max(1, (area.w() - gap * (count - 1)) / count);
        Rect[] out = new Rect[count];
        for (int i = 0; i < count; i++) {
            int x = area.x() + i * (width + gap);
            int w = Math.min(width, Math.max(0, area.right() - x));
            out[i] = new Rect(x, area.y(), w, area.h());
        }
        return out;
    }

    /** Equal-height bands inside an area, top to bottom. */
    static Rect[] rows(Rect area, int count, int gap) {
        if (count <= 0) return new Rect[0];
        int height = Math.max(1, (area.h() - gap * (count - 1)) / count);
        Rect[] out = new Rect[count];
        for (int i = 0; i < count; i++) {
            int y = area.y() + i * (height + gap);
            int h = Math.min(height, Math.max(0, area.bottom() - y));
            out[i] = new Rect(area.x(), y, area.w(), h);
        }
        return out;
    }

    /** How many rows of {@code rowHeight} fit in an area, at least one. */
    static int fitRows(Rect area, int rowHeight, int gap) {
        return Math.max(1, (area.h() + gap) / Math.max(1, rowHeight + gap));
    }

    /** Columns of at least {@code min} width, dropping to one column when two would be too tight. */
    static boolean twoColumn(Rect area) {
        return area.w() >= 2 * 96 + GAP;
    }

    // ---------------------------------------------------------------- map crispness

    /**
     * World blocks per screen pixel at the given view span.
     *
     * <p>This is the number behind the "map looks blurry" report. When it exceeds 1, several blocks
     * land on one pixel, so a drawn boundary is a guess at which block the operator pointed at. The
     * editor shows it and turns amber above 1, and {@link #gridStep} refuses to draw a grid finer
     * than it, because a grid line every half pixel is a grey wash rather than a grid.
     */
    static double blocksPerPixel(double span, int width) {
        return span / Math.max(1, width);
    }

    /**
     * Grid spacing in world blocks: the smallest power of two that keeps at least ~8 lines across the
     * view, and never finer than one block per pixel.
     */
    static double gridStep(double span, int width) {
        double target = span / 8;
        double step = Math.pow(2, Math.ceil(Math.log(Math.max(1, target)) / Math.log(2)));
        return Math.max(blocksPerPixel(span, width), step);
    }

    /** True when the view is fine enough that one block is at least one pixel. */
    static boolean crisp(double span, int width) {
        return blocksPerPixel(span, width) <= 1.0 + 1e-9;
    }
}
