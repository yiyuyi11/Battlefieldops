package cn.bfops;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * One scrollable pane: a vertical cursor that hands out rows, plus the widgets and text drawn in
 * those rows.
 *
 * <p>Every control in this mod lives inside a {@code Form}, never at a free-floating screen
 * coordinate. That is the fix for the "the two sides are on different layers" report: before, the
 * left column's buttons were positioned with ad-hoc offsets that could run past the pane (or off the
 * window) while the right pane's map was drawn independently, so a stray label or an unclipped
 * button visually belonged to neither. Now the pane knows its own height, its content height and its
 * scroll offset, clips its text to itself, and hides rows scrolled out of view.
 */
final class Form {
    private record Placed(AbstractWidget widget, int baseY, int height) {}

    private record Line(String text, int x, int baseY, int color) {}

    private final Layout.Rect area;
    private final Font font;
    private final int x;
    private final int width;
    private final List<Placed> widgets = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int cursor;
    private int scroll;
    private int limit;

    Form(Layout.Rect area, Font font) {
        this.area = area;
        this.font = font;
        this.x = area.x() + Layout.PAD + 1;
        this.width = Math.max(1, area.w() - 2 * (Layout.PAD + 1));
        this.cursor = area.y() + Layout.PAD + 1;
    }

    Layout.Rect area() {
        return area;
    }

    int width() {
        return width;
    }

    int x() {
        return x;
    }

    int scroll() {
        return scroll;
    }

    int limit() {
        return limit;
    }

    boolean scrollable() {
        return limit > 0;
    }

    /** Hands out the next row. Pass the height the control actually needs. */
    Layout.Rect take(int height) {
        return take(height, 2);
    }

    Layout.Rect take(int height, int gap) {
        int h = Math.max(1, Math.min(height, Math.max(1, area.h())));
        Layout.Rect rect = new Layout.Rect(x, cursor, width, h);
        cursor += h + gap;
        return rect;
    }

    /** Two controls side by side in one row. */
    Layout.Rect[] pair(int height) {
        Layout.Rect row = take(height);
        return Layout.columns(row, 2, Layout.GAP);
    }

    Layout.Rect[] split(int height, int count) {
        return Layout.columns(take(height), count, Layout.GAP);
    }

    void skip(int dy) {
        cursor += dy;
    }

    /** Empty space left in the pane below the cursor, for footers pinned to the bottom. */
    int free() {
        return Math.max(0, (area.bottom() - Layout.PAD - 1) - cursor);
    }

    void text(String text, int color) {
        lines.add(new Line(text, x, cursor, color));
        cursor += 10;
    }

    void caption(String text, int color) {
        lines.add(new Line(text, x, cursor, color));
        cursor += 10;
    }

    void textAt(Layout.Rect rect, String text, int color) {
        lines.add(new Line(text, rect.x(), rect.y() + Math.max(1, (rect.h() - 8) / 2), color));
    }

    /** Caption on the left, value on the right, aligned to the pane's own width. */
    void fact(String label, String value, int color) {
        int cy = cursor;
        lines.add(new Line(label, x, cy + Math.max(0, (Layout.ROW - 8) / 2), Ui.TEXT_DIM));
        int valueWidth = font.width(value == null ? "" : value);
        int valueX = Math.max(x + font.width(label) + 6, area.right() - Layout.PAD - 4 - valueWidth);
        lines.add(new Line(value == null ? "" : value, valueX,
                cy + Math.max(0, (Layout.ROW - 8) / 2), color));
        cursor += Layout.ROW;
    }

    void section(String text) {
        lines.add(new Line(text, x, cursor + 1, Ui.TEXT_DIM));
        cursor += 11;
        cursor += 2;
    }

    /** Wraps by words; used for help text so long hints can never run under the next pane. */
    void paragraph(String text, int color) {
        if (text == null || text.isEmpty()) return;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > width && !line.isEmpty()) {
                lines.add(new Line(line.toString(), x, cursor, color));
                cursor += 10;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(new Line(line.toString(), x, cursor, color));
            cursor += 10;
        }
    }

    <T extends AbstractWidget> T add(T widget) {
        widgets.add(new Placed(widget, widget.getY(), widget.getHeight()));
        return widget;
    }

    /** Adds a widget that was built from a rect this form handed out. */
    <T extends AbstractWidget> T place(T widget) {
        return add(widget);
    }

    /** Reserves room for content that is painted rather than widgetised (graphs, icon grids). */
    void reserve(int height) {
        cursor += height;
    }

    /**
     * Reserves a row and registers a painter for it, so custom graphics scroll with the pane.
     *
     * <p>Before this, anything that was not text or a widget had to be painted by the screen at a fixed
     * coordinate — which meant it did not follow the scroll offset and could paint over rows scrolled in
     * from below. The painter receives the row's rect already adjusted for the current scroll.
     */
    void paint(int height, java.util.function.BiConsumer<GuiGraphics, Layout.Rect> painter) {
        Layout.Rect row = take(height, 0);
        painters.add(new Paint(row, painter));
        return;
    }

    private record Paint(Layout.Rect row, java.util.function.BiConsumer<GuiGraphics, Layout.Rect> painter) {}

    private final List<Paint> painters = new ArrayList<>();

    /**
     * Marks the pane as finished without moving anything, for pages whose controls are all painted by
     * the screen itself (the catalogue's preview buttons sit in the right pane, not here).
     */
    void release() {
        finish();
    }

    void finish() {
        int viewportTop = area.y();
        int viewportBottom = area.bottom() - 1;
        int contentBottom = cursor + Layout.PAD;
        limit = Math.max(0, contentBottom - viewportBottom);
        scroll = Math.max(0, Math.min(scroll, limit));
        apply();
    }

    /**
     * Positions and shows/hides the widgets for the current {@link #scroll}.
     *
     * <p>A row is only shown when it fits <em>entirely</em> inside the pane. Partially visible rows
     * would be clipped by the scissor when drawn but remain clickable, because Minecraft's scissor is
     * a rendering hint rather than an input boundary — so the operator could click a control they
     * cannot see. Snapping to whole rows keeps what is drawn and what is clickable the same set.
     */
    private void apply() {
        int viewportTop = area.y();
        int viewportBottom = area.bottom();
        for (Placed placed : widgets) {
            int y = placed.baseY - scroll;
            placed.widget.setY(y);
            placed.widget.visible = y >= viewportTop && y + placed.height <= viewportBottom;
        }
    }

    void resetScroll() {
        scroll = 0;
    }

    /** Restores a remembered offset; clamped by {@link #finish()} once the content height is known. */
    void scrollTo(int offset) {
        scroll = Math.max(0, offset);
    }

    /** Wheel handling; returns true when the pane consumed the scroll. */
    boolean wheel(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        if (limit <= 0 || !area.inside(mouseX, mouseY)) return false;
        int step = Math.max(12, Math.min(40, area.h() / 6));
        scroll = Math.max(0, Math.min(limit, scroll + (delta > 0 ? -step : step)));
        apply();
        return true;
    }

    /** Drags the pane by a pixel delta, for middle/right-drag scrolling inside lists. */
    void drag(double deltaY) {
        if (limit <= 0) return;
        scroll = Math.max(0, Math.min(limit, scroll + (int) Math.round(deltaY)));
        apply();
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Line line : lines) {
            int y = line.baseY - scroll;
            if (y < area.y() || y + 9 > area.bottom() - 1) continue;
            graphics.drawString(font, Ui.clip(font, line.text, area.right() - line.x - 3),
                    line.x, y, line.color, false);
        }
        // Custom graphics go under the widgets, in the same scrolled coordinate space as the text, and
        // are skipped entirely when scrolled out of view — the scissor is a render hint, so painting
        // them anyway would cost nothing visibly but would still be wrong if a painter drew past its row.
        for (Paint p : painters) {
            int y = p.row.y() - scroll;
            Layout.Rect visible = new Layout.Rect(p.row.x(), y, p.row.w(), p.row.h());
            if (visible.bottom() < area.y() || visible.y() > area.bottom()) continue;
            p.painter.accept(graphics, visible);
        }
        for (Placed placed : widgets) {
            if (!placed.widget.visible) continue;
            placed.widget.render(graphics, mouseX, mouseY, partialTick);
        }
        if (limit > 0) {
            Ui.scrollbar(graphics, new Layout.Rect(area.right() - 3, area.y() + 1, 3, area.h() - 2),
                    scroll, limit, area.h());
        }
    }
}
