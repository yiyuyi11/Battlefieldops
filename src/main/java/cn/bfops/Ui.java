package cn.bfops;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shared 656-style translucent surfaces, flat controls and semantic tactical colours. */
final class Ui {
    // ---------------------------------------------------------------- palette
    // Palette: a desaturated Battlefield-style military scheme — charcoal panels, a muted sand accent and
    // muted team colours. The previous saturated gold on a saturated blue read as a warning stripe rather
    // than as chrome, and was hard on the eyes over dark maps.
    static final int BACKDROP = 0x80101418;
    static final int PANEL = 0xe01c2024;
    static final int PANEL_ALT = 0x90262b30;
    static final int PANEL_SUNKEN = 0xd0121618;
    static final int FRAME = 0x60707880;
    static final int FRAME_LIGHT = 0x909aa4ac;
    static final int ACCENT = 0xffd9a566;
    static final int ACCENT_DIM = 0x809a7448;
    /**
     * The lit edge above a raised surface.
     *
     * <p>Every panel used to be a flat fill inside a single-colour frame, so a stack of panels read as
     * one grey mass with lines through it. A one-pixel lighter rule on the top inside edge (and a darker
     * one on the bottom) is what makes each pane look like a raised plate rather than a hole cut in the
     * backdrop — the same trick classic toolkits use, and it costs two fills.
     */
    static final int BEVEL_HI = 0x30889098;
    static final int BEVEL_LO = 0x0010181f;

    static final int TEXT = 0xffe6eef2;
    static final int TEXT_DIM = 0xff9db0bb;
    static final int TEXT_FAINT = 0xff8a9aa6;
    static final int TEXT_WARN = 0xffffc24d;
    static final int TEXT_ERROR = 0xffff7a6b;
    static final int TEXT_OK = 0xff7ee08a;

    static final int BLUE = 0xff5c8fc7;
    static final int RED = 0xffc56a6a;
    static final int GOLD = 0xffd9a566;

    private Ui() {}

    // ---------------------------------------------------------------- panels

    /** Opaque framed surface. The 1px border is what makes two panes read as one window. */
    static void panel(GuiGraphics g, Layout.Rect r) {
        if (r.w() <= 0 || r.h() <= 0) return;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL);
        g.fill(r.x(),r.y(),r.right(),r.y()+1,FRAME);

    }

    /**
     * One-pixel lit top edge and shadowed bottom edge — what turns a fill into a raised plate.
     *
     * <p>Skipped on surfaces too small to have an inside; a 3px-tall rect would otherwise be all bevel
     * and no body.
     */
    static void bevel(GuiGraphics g, Layout.Rect r) {
        // Flat surfaces follow the source UI; no raised black bevels.

    }

    static void inset(GuiGraphics g, Layout.Rect r) {
        if (r.w() <= 0 || r.h() <= 0) return;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL_ALT);
        frame(g, r, FRAME);
    }

    static void sunken(GuiGraphics g, Layout.Rect r) {
        if (r.w() <= 0 || r.h() <= 0) return;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL_SUNKEN);
        frame(g, r, FRAME);
    }

    static void frame(GuiGraphics g, Layout.Rect r, int color) {
        if (r.w() < 2 || r.h() < 2) return;
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }

    /** The content area a {@link #header} leaves behind. Kept separate so callers can size forms
     *  before painting, and so the paint and the layout can never disagree by a pixel. */
    static Layout.Rect headerBody(Layout.Rect r) {
        int stripHeight = Math.min(r.h(), 14);
        return new Layout.Rect(r.x() + 1, r.y() + stripHeight + 1,
                Math.max(1, r.w() - 2), Math.max(1, r.h() - stripHeight - 2));
    }

    /** Panel with a title strip; returns the area left for content. */
    static Layout.Rect header(GuiGraphics g, Font font, Layout.Rect r, String title) {
        panel(g, r);
        int stripHeight = Math.min(r.h(), 14);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + stripHeight, PANEL_ALT);
        // Accent tick at the start of the title, then the rule. Without the tick the strip was a band of
        // slightly-lighter grey with a word in it; the 2px bar is what makes the eye find the title.
        if (stripHeight >= 10 && !title.isEmpty()) {
            g.fill(r.x() + 1, r.y() + 2, r.x() + 3, r.y() + stripHeight - 1, ACCENT);
        }
        g.fill(r.x() + 1, r.y() + stripHeight, r.right() - 1, r.y() + stripHeight + 1, ACCENT_DIM);
        if (!title.isEmpty() && stripHeight >= 10) {
            g.drawString(font, UiFont.text(clip(font, title, r.w() - 12)), r.x() + 7, r.y() + (stripHeight - 8) / 2,
                    TEXT, false);
        }
        return headerBody(r);
    }

    /**
     * Small tag used for state words ("已加入", "未开局"); tinted background, no border.
     *
     * <p>The tint is derived from the text colour and capped well below opaque: a chip must read as a
     * *mark* on the panel, not as a hole in it. Deriving it means a new meaning cannot accidentally ship
     * with an unreadable colour pair, which is what happened when these were passed by hand.
     */
    static void chip(GuiGraphics g, Font font, int x, int y, String text, int color) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 10, mix(color, PANEL_SUNKEN, 0.82));
        g.fill(x, y + 10, x + w, y + 11, color);
        g.drawString(font, UiFont.text(text), x + 3, y + 1, color, false);
        return;
    }

    /** Blends two ARGB colours; {@code amount} is how much of {@code b} to take. */
    static int mix(int a, int b, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        int aa = (a >>> 24) & 0xff, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = (b >>> 24) & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        int na = (int) Math.round(aa + (ba - aa) * t);
        int nr = (int) Math.round(ar + (br - ar) * t);
        int ng = (int) Math.round(ag + (bg - ag) * t);
        int nb = (int) Math.round(ab + (bb - ab) * t);
        return (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    /** Section caption used above groups of controls. */
    static void section(GuiGraphics g, Font font, int x, int y, int width, String text) {
        g.drawString(font, clip(font, text, width), x, y, TEXT_DIM, false);
        int lineY = y + 10;
        g.fill(x, lineY, x + width, lineY + 1, ACCENT_DIM);
    }

    static void divider(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, ACCENT_DIM);
    }

    // ---------------------------------------------------------------- text

    static String clip(Font font, String text, int width) {
        if (text == null) return "";
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - 6)) + "…";
    }

    /** Left-aligned label that never overflows its pane. */
    static void text(GuiGraphics g, Font font, int x, int y, int width, String text, int color) {
        g.drawString(font, UiFont.text(clip(font, text, width)), x, y, color, false);
    }

    static void textCentered(GuiGraphics g, Font font, int cx, int y, int width, String text, int color) {
        String clipped = clip(font, text, width);
        g.drawString(font, UiFont.text(clipped), cx - font.width(clipped) / 2, y, color, false);
    }

    /** Label / value pair: the label is dim and fixed width so the values line up in a column. */
    static void fact(GuiGraphics g, Font font, int x, int y, int labelWidth, int totalWidth,
                     String label, String value, int color) {
        g.drawString(font, UiFont.text(clip(font, label, labelWidth - 4)), x, y, TEXT_FAINT, false);
        g.drawString(font, UiFont.text(clip(font, value, totalWidth - labelWidth)), x + labelWidth, y, color, false);
    }

    /** Same pair, with the value right-aligned against {@code right} so numbers line up. */
    static void fact(GuiGraphics g, Font font, int x, int y, int labelWidth, int totalWidth,
                     String label, String value) {
        fact(g, font, x, y, labelWidth, totalWidth, label, value, TEXT);
    }

    static void bar(GuiGraphics g, Layout.Rect r, double fraction, int fill, int empty) {
        if (r.w() <= 0 || r.h() <= 0) return;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xff000000);
        int filled = (int) Math.round(r.w() * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fill(r.x(), r.y(), r.x() + filled, r.bottom(), fill);
        if (filled < r.w()) g.fill(r.x() + filled, r.y(), r.right(), r.bottom(), empty);
    }

    // ---------------------------------------------------------------- lines

    /**
     * Integer line drawing. Sampling every pixel keeps diagonal map edges continuous; a
     * {@code fill} based Bresenham is what the reference UI does and it stays crisp at any GUI scale
     * because nothing is scaled up.
     */
    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps > 4096) return;
        int t = Math.max(1, thickness);
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) Math.max(1, steps);
            int x = (int) Math.round(x0 + (x1 - x0) * f);
            int y = (int) Math.round(y0 + (y1 - y0) * f);
            g.fill(x, y, x + t, y + t, color);
        }
    }

    static void rectOutline(GuiGraphics g, Layout.Rect r, int color) {
        frame(g, r, color);
    }

    /**
     * Soft vertical gradient inside a rect, band by band.
     *
     * <p>Rendered as a stack of solid 1px fills rather than a translucent overlay: the whole point of
     * this theme is that nothing is semi-transparent over the world, and a real {@code fillGradient}
     * would reintroduce exactly the compositing seam this mod removed.
     */
    static void gradient(GuiGraphics g, Layout.Rect r, int top, int bottom) {
        if (r.w() <= 0 || r.h() <= 0) return;
        for (int i = 0; i < r.h(); i++) {
            double f = r.h() <= 1 ? 0 : i / (double) (r.h() - 1);
            g.fill(r.x(), r.y() + i, r.right(), r.y() + i + 1, mix(top, bottom, f));
        }
    }

    /** A group box: sunken body with a tiny caption in the top-left, used to cluster related rows. */
    static void group(GuiGraphics g, Font font, Layout.Rect r, String caption, int accent) {
        sunken(g, r);
        if (caption != null && !caption.isEmpty() && r.h() >= 14 && r.w() >= 30) {
            g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 12, PANEL);
            g.fill(r.x() + 1, r.y() + 12, r.right() - 1, r.y() + 13, accent);
            g.drawString(font, clip(font, caption, r.w() - 10), r.x() + 5, r.y() + 3, TEXT_DIM, false);
        }
    }

    /**
     * A thin progress/segmented indicator, e.g. "3 of 6 hulls used".
     *
     * <p>Drawn as discrete cells so the count is readable without reading the number, and so a
     * nearly-empty pool looks different at a glance from a full one.
     */
    static void segments(GuiGraphics g, Layout.Rect r, int filled, int total, int on, int off) {
        if (r.w() <= 0 || r.h() <= 0 || total <= 0) return;
        int gap = total > 12 ? 1 : 2;
        int cell = Math.max(1, (r.w() - gap * (total - 1)) / total);
        for (int i = 0; i < total; i++) {
            int x = r.x() + i * (cell + gap);
            if (x + cell > r.right()) break;
            g.fill(x, r.y(), x + cell, r.bottom(), i < filled ? on : off);
        }
    }

    static void marker(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x - size, y - size, x + size + 1, y + size + 1, color);
    }

    /** Square handle with a dark core: reads as a grabbable point rather than as a lit pixel. */
    static void handle(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x - size, y - size, x + size + 1, y + size + 1, 0xff000000);
        g.fill(x - size + 1, y - size + 1, x + size, y + size, color);
    }

    /** Scroll bar for a list that is longer than its area. Drawn only when it would do something. */
    static void scrollbar(GuiGraphics g, Layout.Rect track, int offset, int limit, int viewport) {
        if (limit <= 0 || track.h() <= 4) return;
        g.fill(track.x(), track.y(), track.right(), track.bottom(), PANEL_SUNKEN);
        int total = Math.max(1, viewport + limit);
        int thumb = Math.max(10, (int) Math.round(track.h() * (viewport / (double) total)));
        int travel = track.h() - thumb;
        int y = track.y() + (int) Math.round(travel * (offset / (double) Math.max(1, limit)));
        g.fill(track.x(), y, track.right(), y + thumb, FRAME_LIGHT);
    }

    // ---------------------------------------------------------------- tabs

    /** A tab strip button: selected tabs get a filled body and an accent underline. */
    static void tab(GuiGraphics g, Font font, Layout.Rect r, String label, boolean selected, boolean hovered) {
        int body = selected ? PANEL_ALT : (hovered ? 0x8a444444 : PANEL_ALT);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), body);
        // A selected tab is joined to the pane below it: lit top edge, no bottom rule, and a full-width
        // accent. An unselected one keeps a dark rule so the strip reads as separate buttons. This is the
        // difference between "tabs" and "a row of words with a line under one of them".
        if (selected) {
            g.fill(r.x(), r.y(), r.right(), r.y() + 1, FRAME_LIGHT);
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), ACCENT);
        } else {
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), hovered ? FRAME_LIGHT : FRAME);
        }
        // A hairline between adjacent tabs, so two unselected neighbours do not merge into one block.
        if (r.w() > 2) {
            g.fill(r.x(), r.y() + 3, r.x() + 1, r.bottom() - 3, BEVEL_LO);
        }
        int color = selected ? TEXT : (hovered ? TEXT_DIM : TEXT_FAINT);
        textCentered(g, font, r.cx(), r.y() + (r.h() - 8) / 2, r.w() - 4, label, color);
    }

    /** Full-window background. Opaque on purpose: nothing else may leak between layers. */
    static void backdrop(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, BACKDROP);
    }
}
