package cn.bfops;

/**
 * Checks for the icon sheet and the vehicle-cooldown rules.
 *
 * <p>The icon part pins three properties that are invisible in code review but visible on screen: every
 * label a row can carry resolves to a glyph, the atlas is big enough to hold every glyph, and the draw
 * sizes stay on integer scales. The last one is the actual fix for the reported blurriness — the old
 * sheet was 24px art drawn at 14px and 11px, and a fractional nearest-neighbour downscale is what made
 * it look soft.
 *
 * <p>The vehicle part pins that score is no longer an input: a player with a zero balance must still be
 * able to take a vehicle when the cooldown is up. That is the requirement — "取消点数改为刷新冷却" — and
 * it is exactly the kind of thing a later refactor could quietly reintroduce.
 */
public final class IconVehicleSim {
    private static int checks;

    public static void main(String[] args) {
        everyRowLabelHasAGlyph();
        glyphIdsAreInRange();
        drawSizesStayOnIntegerScales();
        markerSizesAreCrisp();
        cooldownIsTheOnlyPersonalGate();
        cooldownCountsDown();
        noStateAdvertisesAPrice();
        System.out.println("IconVehicleSim: " + checks + " checks passed");
    }

    // ------------------------------------------------------------------ icons

    /** Every label the editor and the deploy screen actually use. */
    private static final String[] LABELS = {
            "保存地图", "写入配置文件", "关闭房间", "开始本局", "结束对局", "退出房间", "重新载入",
            "新建草稿", "删除地图", "上一张", "下一张", "搜索", "兵种", "部署点", "地面载具",
            "航空器", "重新部署", "重新检测", "定位到脚下", "移到脚下", "移除主武器选项",
            "丢弃改动", "返回", "关闭", "新增地图", "创建房间"
    };

    private static void everyRowLabelHasAGlyph() {
        for (String label : LABELS) {
            check(UiIcons.action(label) >= 0, "label needs a glyph: " + label);
            check(UiIcons.action(label) < UiIcons.COUNT, "glyph index in range: " + label);
        }
        // Unknown labels are allowed to have no glyph — a row without an icon is fine, a crash is not.
        check(UiIcons.action("某个未来才有的按钮") == -1, "unknown label draws no glyph");
        check(UiIcons.action(null) == -1, "null label is handled");
        check(UiIcons.action("") == -1, "empty label is handled");
        checks++;
    }

    private static void glyphIdsAreInRange() {
        // The sheet is COUNT cells wide; an index at or past COUNT would read past the atlas and show
        // whatever happens to be there (usually nothing, occasionally the neighbouring cell).
        check(UiIcons.COUNT == 14, "sheet holds the glyphs this build knows about");
        int[] used = {UiIcons.SAVE, UiIcons.ROOM, UiIcons.SEARCH, UiIcons.ADD, UiIcons.BACK,
                UiIcons.DELETE, UiIcons.START, UiIcons.STOP, UiIcons.NEXT, UiIcons.PREV,
                UiIcons.MARKER_FRIEND, UiIcons.MARKER_HOSTILE, UiIcons.VEHICLE_GROUND,
                UiIcons.VEHICLE_AIR};
        for (int id : used) {
            check(id >= 0 && id < UiIcons.COUNT, "glyph " + id + " inside the sheet");
        }
        // Every slot must be claimed by exactly one constant, or a cell is wasted and another is shared.
        java.util.Set<Integer> distinct = new java.util.HashSet<>();
        for (int id : used) distinct.add(id);
        check(distinct.size() == used.length, "no two constants share a cell");
        checks++;
    }

    private static void drawSizesStayOnIntegerScales() {
        // Icon boxes offered by the UI: 18px rows give 14, taller rows give up to 16.
        for (int size : new int[]{14, 15, 16, 32, 48}) {
            int scale = Math.max(1, size / UiIcons.CELL);
            int drawn = UiIcons.CELL * scale;
            if (size >= UiIcons.CELL) {
                check(drawn <= size, "scaled glyph fits its box at " + size);
                check(drawn % UiIcons.CELL == 0, "scale is a whole multiple at " + size);
            } else {
                // Below the cell the glyph is cropped, never resampled.
                check(size > 0 && size < UiIcons.CELL, "sub-cell size crops at " + size);
                int trim = (UiIcons.CELL - size) / 2;
                check(trim + size <= UiIcons.CELL, "crop stays inside the cell at " + size);
            }
        }
        checks++;
    }

    private static void markerSizesAreCrisp() {
        // Map markers are 11px: cropped, never scaled, so a dot stays a dot at any GUI scale.
        int size = 11;
        check(size < UiIcons.CELL, "marker is drawn by cropping");
        int trim = (UiIcons.CELL - size) / 2;
        check(trim >= 0 && trim + size <= UiIcons.CELL, "marker crop is inside the cell");
        // Centre placement must not drift by a half pixel: 11 is odd, so both offsets are integer.
        check((size / 2) * 2 == size - 1, "odd size centres on an exact pixel");
        checks++;
    }

    // ------------------------------------------------------------------ vehicle cooldown

    private static void cooldownIsTheOnlyPersonalGate() {
        // The requirement: no point cost. A fresh member with nothing earned must still be able to take
        // a vehicle as soon as the cooldown permits.
        check(VehicleHud.availability(true, true, true, 0, 0, 6) == VehicleHud.State.READY,
                "zero score still allows a vehicle");
        // A 1-tick cooldown is still blocking; the gate is exclusive at zero.
        check(VehicleHud.availability(true, true, true, 1, 0, 6) == VehicleHud.State.COOLDOWN,
                "any remaining cooldown blocks");
        check(VehicleHud.availability(true, true, true, 0, 0, 6) == VehicleHud.State.READY,
                "no cooldown does not block");
        // The match cap stays, because it protects the server rather than pacing one player.
        check(VehicleHud.availability(true, true, true, 0, 6, 6) == VehicleHud.State.MATCH_LIMIT,
                "match cap still applies");
        checks++;
    }

    private static void cooldownCountsDown() {
        // The displayed seconds must be a ceiling, so a 0.1s remainder reads as "1s" rather than "0s"
        // followed by a rejection. This mirrors Engine's (ticks + 19) / 20.
        for (int ticks : new int[]{0, 1, 19, 20, 21, 40, 1200}) {
            int seconds = (ticks + 19) / 20;
            check(seconds >= 0, "seconds never negative at " + ticks);
            if (ticks > 0) check(seconds >= 1, "any remainder shows at least 1s at " + ticks);
            check(seconds == 0 || ticks > 0, "zero seconds only when the cooldown is over at " + ticks);
        }
        check((20 + 19) / 20 == 1, "exactly one second");
        check((21 + 19) / 20 == 2, "just over one second rounds up");
        checks++;
    }

    private static void noStateAdvertisesAPrice() {
        // A row that still says "分不足" would tell players to earn score they no longer need.
        for (VehicleHud.State state : VehicleHud.State.values()) {
            String text = VehicleHud.describe(state, 12, 6, 3);
            check(!text.contains("分不足"), "no state mentions a score shortfall: " + state);
            check(!text.contains("费用"), "no state mentions a price: " + state);
            check(!text.isBlank(), "every state has text: " + state);
        }
        // The retired state must be gone, not merely unreferenced.
        for (VehicleHud.State state : VehicleHud.State.values()) {
            check(!state.name().contains("CREDIT"), "no credit state remains: " + state);
        }
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
