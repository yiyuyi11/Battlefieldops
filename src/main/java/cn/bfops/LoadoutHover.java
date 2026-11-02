package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The weapon property overlay shown when the pointer rests on an attachment slot.
 *
 * <h2>What it reproduces, and what it refuses to invent</h2>
 *
 * <p>The reference pack has {@code renderWeaponPropertyPanel} and {@code resolveDisplayedAttachmentPreviewStack}:
 * a hover panel that previews what a slot is, and what it does, <em>without</em> changing the gun. The second
 * half is the important one — a hover that applied the attachment would turn browsing into editing, and the
 * player would end up with an uncommitted change they never meant to make.
 *
 * <p>The detailed charts use the installed mod's native attachment evaluation. This compact overlay
 * shows the chosen part's localized name and a few current values; it never installs a part by hovering.
 *
 * <h2>Hit-testing and drawing are the same rectangle</h2>
 *
 * <p>The panel is placed by {@link #place}, which is a pure function of the anchor and the panel size, so
 * `LoadoutHoverSim` can assert it never leaves the window at any resolution — the failure that made the
 * earlier floating tooltips unreadable was a panel placed past the right edge because the code assumed a
 * window wide enough to hold it.
 */
final class LoadoutHover {
    /** Padding inside the panel, and the margin kept from the window edge. */
    static final int PAD = 5;
    static final int MARGIN = 3;
    /** Line height for the panel's body text. */
    static final int LINE = 10;

    private static final int BG = 0xf2101820;
    private static final int BORDER = 0xff7fd4ff;
    private static final int TITLE = 0xff7fd4ff;
    private static final int BODY = 0xffc7d3dd;
    private static final int HINT = 0xff8fb3cf;

    /** One panel's contents, already resolved to strings. */
    record Panel(String title, List<String> lines, int width, int height) { }

    private LoadoutHover() { }

    /**
     * Builds the panel for one attachment slot.
     *
     * @param slotName  the slot's display name, e.g. 瞄具
     * @param installed the attachment currently configured for this slot, empty for default
     * @param options   how many compatible parts the weapon reports for this slot
     * @param stats     the weapon's headline property line, may be empty
     */
    static Panel build(String slotName, String installed, int options, String stats) {
        List<String> lines = new ArrayList<>();
        lines.add("兼容配件 " + options + " 种");
        lines.add(installed == null || installed.isEmpty() ? "当前：默认（不加装）" : "当前：" + installed);
        if (stats != null && !stats.isEmpty()) lines.add(stats);
        lines.add("点击打开改装台 · 悬停不改变装备");
        // Width from the longest line is not available here (no Font), so the caller measures. Kept as a
        // fixed content list so the geometry function below can be tested with any width.
        return new Panel(slotName, List.copyOf(lines), 0, 0);
    }

    /**
     * Places the panel next to its anchor without leaving the window.
     *
     * <p>Preferred position is to the right of and below the anchor. When that would overflow, the panel
     * flips to the other side rather than being clamped to the edge: a clamped panel covers the anchor it
     * describes, and the pointer is usually still over the anchor, so the panel would sit under the cursor.
     *
     * @param anchorX pointer x, the panel's preferred left edge
     * @param anchorY pointer y, the panel's preferred top edge
     * @param width   panel width including padding
     * @param height  panel height including padding
     * @param windowW window width in GUI pixels
     * @param windowH window height in GUI pixels
     * @return the panel rect, always fully inside the window when it fits at all
     */
    static Layout.Rect place(int anchorX, int anchorY, int width, int height, int windowW, int windowH) {
        int w = Math.min(width, Math.max(1, windowW - 2 * MARGIN));
        int h = Math.min(height, Math.max(1, windowH - 2 * MARGIN));
        int x = anchorX + 6;
        if (x + w > windowW - MARGIN) x = anchorX - w - 6;
        if (x < MARGIN) x = Math.max(MARGIN, windowW - MARGIN - w);
        int y = anchorY + 6;
        if (y + h > windowH - MARGIN) y = anchorY - h - 6;
        if (y < MARGIN) y = Math.max(MARGIN, windowH - MARGIN - h);
        return new Layout.Rect(x, y, w, h);
    }

    /** Draws a built panel at a placed rect. */
    static void draw(GuiGraphics g, Font font, Layout.Rect rect, Panel panel) {
        if (rect.w() <= 0 || rect.h() <= 0) return;
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), BG);
        Ui.frame(g, rect, BORDER);
        // One accent cell in the corner, so the panel reads as a tooltip rather than as another card.
        g.fill(rect.x() + 1, rect.y() + 1, rect.x() + 4, rect.y() + rect.h() - 1, BORDER);
        int textX = rect.x() + PAD + 2;
        int y = rect.y() + PAD;
        g.drawString(font, Ui.clip(font, panel.title(), rect.w() - PAD * 2 - 2), textX, y, TITLE, false);
        y += LINE;
        for (String line : panel.lines()) {
            if (y + 8 > rect.bottom() - PAD) break;
            g.drawString(font, Ui.clip(font, line, rect.w() - PAD * 2 - 2), textX, y,
                    line.startsWith("点击") ? HINT : BODY, false);
            y += LINE;
        }
    }

    /** Panel size for a built panel, measured with the live font. */
    static Layout.Rect size(Font font, Panel panel) {
        int widest = font.width(panel.title());
        for (String line : panel.lines()) widest = Math.max(widest, font.width(line));
        int width = widest + PAD * 2 + 2 + 4;
        int height = PAD * 2 + LINE * (panel.lines().size() + 1);
        return new Layout.Rect(0, 0, width, height);
    }

    /**
     * The hover text for one slot, from the slot's configured value.
     *
     * <p>Separated from the drawing so the same wording is used by the card's inline summary and by this
     * panel: two spellings of "default" in one screen is how a player concludes the panel describes a
     * different slot.
     */
    static String installedLabel(Map<String, String> fitting, String slot) {
        String value = fitting == null ? "" : fitting.getOrDefault(slot, "");
        if (value == null || value.isEmpty()) return "";
        String name = Catalog.prettify(value);
        return name.isEmpty() ? value : name;
    }
}
