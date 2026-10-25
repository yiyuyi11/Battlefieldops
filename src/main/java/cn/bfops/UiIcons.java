package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Small monochrome glyphs for the control rows and the tactical markers.
 *
 * <p>The sheet is 16x16 cells with <b>no anti-aliasing at all</b>. That is the fix for the reported
 * blurriness: the previous sheet was 24x24 art with soft edges drawn at 14px on buttons and 11px on map
 * markers, i.e. scaled by ~0.58 and ~0.46. Minecraft blits with nearest-neighbour filtering, so a
 * fractional scale of soft-edged art drops and duplicates whole pixel rows — which is exactly what
 * "图标发虚" looks like. Crisp art shrunk by a fraction would still shimmer; the cure is integer-scaled
 * art, not better art.
 *
 * <p>So the target box is snapped to a whole-number multiple of 16 where possible, and the icon is
 * centred with integer offsets inside whatever box it was given. No sub-pixel placement, no stretching.
 */
final class UiIcons {
    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath("battlefield_ops", "textures/gui/tactical_icons.png");

    /** One 16x16 cell per icon, in sheet order. */
    static final int CELL = 16;
    static final int SAVE = 0;
    static final int ROOM = 1;
    static final int SEARCH = 2;
    static final int ADD = 3;
    static final int BACK = 4;
    static final int DELETE = 5;
    static final int START = 6;
    static final int STOP = 7;
    static final int NEXT = 8;
    static final int PREV = 9;
    /** Map marker for a teammate or oneself. */
    static final int MARKER_FRIEND = 10;
    /** Map marker for an enemy. */
    static final int MARKER_HOSTILE = 11;
    static final int VEHICLE_GROUND = 12;
    static final int VEHICLE_AIR = 13;
    static final int COUNT = 14;

    private UiIcons() { }

    /**
     * Picks the glyph for a row from its label.
     *
     * <p>Matching on the label rather than passing an icon from every call site keeps the call sites
     * unchanged and guarantees consistency: two rows with the same verb get the same glyph. Order
     * matters — the more specific verbs are tested first, so "删除地图" reads as delete rather than as
     * the map/room glyph.
     */
    static int action(String label) {
        if (label == null) return -1;
        if (label.contains("删除") || label.contains("丢弃") || label.contains("移除")) return DELETE;
        if (label.contains("搜索")) return SEARCH;
        if (label.contains("保存") || label.contains("写入")) return SAVE;
        if (label.contains("重新载入")) return BACK;
        if (label.contains("开始")) return START;
        if (label.contains("结束") || label.contains("关闭房间")) return STOP;
        if (label.contains("上一张") || label.contains("上一")) return PREV;
        if (label.contains("下一张") || label.contains("下一")) return NEXT;
        if (label.contains("返回") || label.contains("退出") || label.equals("关闭")) return BACK;
        if (label.contains("新增") || label.contains("新建") || label.contains("创建")) return ADD;
        if (label.contains("房间")) return ROOM;
        if (label.contains("航空器") || label.contains("飞机")) return VEHICLE_AIR;
        if (label.contains("载具")) return VEHICLE_GROUND;
        // Deployment verbs. "部署" covers 部署点 and 重新部署; 兵种 gets the marker glyph because a class
        // is a person, which is also how it reads on the map.
        if (label.contains("兵种") || label.contains("阵营")) return MARKER_FRIEND;
        if (label.contains("部署")) return MARKER_FRIEND;
        if (label.contains("重新检测") || label.contains("定位") || label.contains("移到")) return SEARCH;
        return -1;
    }

    /**
     * Draws {@code icon} into the {@code size x size} box at {@code x,y}.
     *
     * <p>The cell is 16px, so the only clean scales are 1:1 (16px boxes and up) and the integer
     * downscales. Boxes between 11 and 15 pixels get the icon at 1:1 with the overflow trimmed and
     * centred — a 12px box showing the middle 12 rows of a 16px glyph is legible, whereas the same
     * glyph squeezed to 0.75 would be mush.
     */
    static void draw(GuiGraphics g, int icon, int x, int y, int size) {
        if (icon < 0 || icon >= COUNT || size <= 0) return;
        int u = icon * CELL;
        if (size >= CELL) {
            // Integer multiples only: 32, 48... anything else reintroduces the fractional-scale blur.
            int scale = Math.max(1, size / CELL);
            int draw = CELL * scale;
            g.blit(ATLAS, x + (size - draw) / 2, y + (size - draw) / 2, draw, draw,
                    u, 0, CELL, CELL, CELL * COUNT, CELL);
            return;
        }
        // Smaller than the cell: crop the middle instead of scaling. The glyphs are designed with a
        // one-pixel margin, so the trimmed middle still reads as the icon.
        int trim = (CELL - size) / 2;
        g.blit(ATLAS, x, y, size, size,
                u + trim, trim, size, size, CELL * COUNT, CELL);
    }

    /**
     * Draws a map marker centred on {@code x,y}, as the tactical maps want.
     *
     * <p>Separate from {@link #draw} because markers are positioned by their centre and must land on
     * exact pixels: a half-pixel offset here is the difference between a dot and a smudge on a map.
     */
    static void marker(GuiGraphics g, int icon, int centerX, int centerY, int size) {
        if (size <= 0) return;
        draw(g, icon, centerX - size / 2, centerY - size / 2, size);
    }
}
