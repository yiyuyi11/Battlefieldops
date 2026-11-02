package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Card-based vehicle selection, matching the reference pack's vehicle row layout (see
 * 参考端-配装与载具界面-结构提取.md): 46-pixel rows with a status strip, a real vehicle image and an
 * 86-pixel deploy button.
 *
 * <p>The reference has a dedicated vehicle deploy page; this is the equivalent. It lists every vehicle the
 * map authorises, shows the actual hull art (read at runtime from Superb Warfare's {@code VehicleIcon} via
 * {@link AssetIcons}), and selecting one records it on the owning {@link BattleScreen} — which keeps the
 * spawn site consistent — before returning. The per-vehicle health/seat lines the reference draws are
 * omitted because this mod's snapshot does not send that data yet; the quota and cooldown summary it does
 * send is shown at the top instead.
 */
final class VehicleDeployScreen extends FrontScreen {
    private static final int ROW_H = 46;
    private static final int ROW_GAP = 6;
    private static final int BUTTON_W = 86;
    private static final int ROW_BG = 0x33151A20;
    private static final int ROW_SELECTED = 0x3A22374E;
    private static final int STRIP = 0xFF7EC8FF;
    private static final int NAME_COLOR = 0xFFEDEDED;
    private static final int CLASS_COLOR = 0xFF8FB3CF;

    private final BattleScreen parent;
    private Form listForm, barForm;

    VehicleDeployScreen(BattleScreen parent) {
        super("载具");
        this.parent = parent;
    }

    @Override
    protected String subtitle() {
        return (parent.arena == null ? "未选择地图" : parent.arena.name) + " · 载具专页";
    }

    private int team() {
        JsonObject me = Client.state.has("me") ? Client.state.getAsJsonObject("me") : null;
        return me == null ? -1 : Engine.num(me, "team", -1);
    }

    @Override
    protected void rebuildContent() {
        int scroll = listForm == null ? 0 : listForm.scroll();
        clearWidgets();
        listForm = newForm(Ui.headerBody(layout.content));

        if(!parent.vehicleSpawnAllowed())listForm.paragraph("当前部署目标只允许步兵增援/登车。请返回后先选择固定载具刷新点。",Ui.TEXT_WARN);
        summary();
        List<String> vehicles = parent.arena == null ? List.of() : parent.arena.vehicles;
        if (vehicles.isEmpty()) {
            listForm.paragraph("本图没有授权任何载具。管理员可在「武器/载具库」中开启。", Ui.TEXT_FAINT);
        } else {
            // Button and artwork use the exact same scrolling row, including variable summary height.
            for (int i = 0; i < vehicles.size(); i++) {
                String id = vehicles.get(i);
                Layout.Rect card=listForm.paintRow(ROW_H, (g, r) -> drawRow(g, r, id));
                int rowTop=card.y();
                listForm.skip(ROW_GAP);
                int btnX = listForm.x() + listForm.width() - BUTTON_W;
                place(listForm, new Layout.Rect(btnX, rowTop + 14, BUTTON_W, 18),
                        "选择", "", "", "", parent.vehicleSpawnAllowed(), () -> { parent.selectVehicle(id); onClose(); });
            }
        }
        listForm.scrollTo(scroll);
        listForm.finish();
        buildBar();
    }

    /** Quota and cooldown summary, the two numbers this mod's snapshot actually carries for vehicles. */
    private void summary() {
        JsonObject state = Client.state.has("vehicleState") ? Client.state.getAsJsonObject("vehicleState") : null;
        int used = state == null ? 0 : Engine.num(state, "used", 0);
        int limit = parent.arena == null ? 0 : Engine.num(state, "limit", parent.arena.vehicleLimitFor(team()));
        int left = Math.max(0, limit - used);
        listForm.caption("载具名额 " + left + " / " + limit + " 可用", left <= 0 ? Ui.TEXT_ERROR : Ui.TEXT_OK);
        int remaining = state == null ? 0 : Engine.num(state, "remaining", 0);
        int configured = parent.arena == null ? 0 : parent.arena.vehicleCooldown;
        if (remaining > 0 && configured > 0) {
            listForm.caption("冷却 " + remaining + "s / " + configured + "s", Ui.TEXT_WARN);
        }
        listForm.skip(2);
    }

    private void drawRow(GuiGraphics g, Layout.Rect r, String id) {
        boolean selected = id.equals(parent.vehicle);
        VehicleRow.Live live = liveOf(id);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), selected ? ROW_SELECTED : ROW_BG);
        // The status strip used to be a fixed accent on every row, which made it decorative: it was the
        // same colour whether the hull was fine, wrecked or not on the field at all. It now carries that
        // state — accent for "nothing out", green-ish for healthy, amber for a hull that needs repair.
        g.fill(r.x(), r.y(), r.x() + 4, r.bottom(), live.present()
                ? (live.damaged() ? Ui.TEXT_WARN : Ui.TEXT_OK) : STRIP);
        AssetIcons.draw(g, "vehicle", id, r.x() + 8, r.y() + 3, 40, 40);
        g.drawString(font, clip(parent.name(id), r.w() - 130), r.x() + 54, r.y() + 8, NAME_COLOR, false);
        g.drawString(font, clip(factsOf(id, live), r.w() - 130), r.x() + 54, r.y() + 26,
                live.damaged() ? Ui.TEXT_WARN : CLASS_COLOR, false);
        // Health bar on the row's right edge, above the deploy button. Only for a hull that is actually
        // out: a bar at 100% for a vehicle nobody has spawned would be a lie about the battlefield.
        if (live.present() && live.health() >= 0) {
            int barX = r.right() - 8 - 40, barY = r.y() + r.h() - 9;
            Ui.bar(g, new Layout.Rect(barX, barY, 40, 3), live.fraction(),
                    live.damaged() ? Ui.TEXT_WARN : Ui.TEXT_OK, Ui.PANEL_SUNKEN);
        }
    }

    /** This authorised type's live state, from the snapshot plus the server's start-up seat scan. */
    private VehicleRow.Live liveOf(String id) {
        return VehicleRow.live(Client.state, id, team(), seatOf(id));
    }

    /**
     * Static seat capacity, from the structured {@code vehicleSeats} map.
     *
     * <p>Structured rather than parsed back out of {@code vehicleFacts}: that field is a human-readable line
     * ("4 座 · 血量 150 · 2 武器站") assembled for display, and re-parsing it would make a wording change a
     * silent data change. {@code vehicleSeats} is additive, so an older server simply has no entry and the
     * row falls back to the occupant count.
     */
    private int seatOf(String id) {
        if (Client.state == null || !Client.state.has("vehicleSeats")
                || !Client.state.get("vehicleSeats").isJsonObject()) return 0;
        JsonObject seats = Client.state.getAsJsonObject("vehicleSeats");
        return seats.has(id) && seats.get(id).isJsonPrimitive() ? seats.get(id).getAsInt() : 0;
    }

    /** "地面载具 · 场上 1 · 血 35% · 2/4 座", from the server's startup scan plus the live snapshot. */
    private String factsOf(String id, VehicleRow.Live live) {
        String cls = parent.vehicleClass(id) == 'A' ? "航空器" : "地面载具";
        JsonObject facts = Client.state != null && Client.state.has("vehicleFacts")
                ? Client.state.getAsJsonObject("vehicleFacts") : null;
        String extra = facts != null && facts.has(id) && facts.get(id).isJsonPrimitive()
                ? facts.get(id).getAsString() : "";
        return VehicleRow.describe(cls, live, extra);
    }

    private String clip(String text, int width) {
        return Ui.clip(font, text, Math.max(1, width));
    }

    private void buildBar() {
        barForm = newForm(layout.bar.inset(2));
        Layout.Rect[] cells = Layout.columns(layout.bar.inset(2), 1, 3);
        place(barForm, cells[0], "返回部署", "", "", "", true, this::onClose);
        barForm.finish();
    }

    @Override
    protected void paint(GuiGraphics g, int mx, int my, float dt) {
        Ui.header(g, font, layout.content, "载具选择");
        listForm.render(g, mx, my, dt);
        barForm.render(g, mx, my, dt);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {if(dropdownOpen())return super.mouseScrolled(x,y,h,v);
        if (listForm != null && listForm.wheel(x, y, v)) return true;
        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
