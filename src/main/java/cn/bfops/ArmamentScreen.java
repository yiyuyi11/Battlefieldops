package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Operator screen for one vehicle's armament limits: rounds per station, allowed ammunition, attachments.
 *
 * <p><b>Why a separate screen.</b> A vehicle can have several weapon stations, each with its own ammunition
 * and its own round count. Fitting that into the library page would mean an unbounded number of rows inside
 * a page that is already about authorising the vehicle at all. A modal editor scoped to one vehicle keeps
 * both pages readable, and it is also where the "apply / cancel" semantics belong: nothing here touches the
 * draft until the operator confirms.
 *
 * <p><b>Rounds, not magazines.</b> A magazine is not a fixed quantity — one rack slot holds a different number
 * of rounds depending on the ammunition — so a magazine setting cannot be checked against the vehicle's
 * actual container. The operator types a number of rounds and is told immediately whether it fits.
 */
final class ArmamentScreen extends FrontScreen {
    private final OpsScreen parent;
    private final String vehicleId;
    private final VehicleArmament working;

    /** Station being edited, as an index into {@link #working}. */
    private int stationIndex;

    private Form form;
    private Form barForm;

    /** Buffered text of the round field; empty means "use the default". */
    private String roundsText;

    /** Whether the real station list has already been seeded, so it happens once per screen. */
    private boolean seeded;

    private static final List<String> ATTACHMENT_SLOTS =
            List.of("SCOPE", "MAGAZINE", "BARREL", "STOCK", "GRIP");

    /**
     * @param existing the draft's current configuration, copied; {@code null} for a vehicle with none
     */
    ArmamentScreen(OpsScreen parent, String vehicleId, String vehicleName, VehicleArmament existing) {
        super("武器配置 · " + vehicleName);
        this.parent = parent;
        this.vehicleId = vehicleId;
        this.working = copy(existing);
    }

    private static VehicleArmament copy(VehicleArmament source) {
        VehicleArmament out = new VehicleArmament();
        if (source == null) return out;
        for (VehicleArmament.Station entry : source.stations) {
            if (entry == null) continue;
            VehicleArmament.Station copy = new VehicleArmament.Station(entry.station);
            copy.rounds = entry.rounds;
            copy.magazines = entry.magazines;
            copy.ammo = new ArrayList<>(entry.ammo);
            copy.attachments = new LinkedHashMap<>(entry.attachments);
            out.stations.add(copy);
        }
        return out;
    }

    /**
     * Writes the edited configuration back into the draft.
     *
     * @return false when the entered round count is unusable, in which case nothing is written and the
     *         operator stays on this screen to fix it. Silently clamping would save a number the game never
     *         honours, which they would only discover by counting shells mid-match.
     */
    boolean commit() {
        if (!applyRounds()) return false;
        if (working.configured()) parent.draft.armaments.put(vehicleId, working);
        else parent.draft.armaments.remove(vehicleId);
        parent.rebuild();
        return true;
    }

    /** Final check of the typed round count before it is committed. */
    private boolean applyRounds() {
        VehicleArmament.Station station = currentStation();
        if (station == null) return true;
        String text = roundsText == null ? "" : roundsText.trim();
        if (text.isEmpty()) {
            station.rounds = -1;
            station.magazines = -1;
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            fail("弹药数需要是整数");
            return false;
        }
        if (value < 0 || value > VehicleArmament.MAX_ROUNDS) {
            fail("弹药数需在 0 ～ " + VehicleArmament.MAX_ROUNDS + " 之间");
            return false;
        }
        List<Integer> stacks = stackSizes(station);
        int slots = OpsScreen.vehicleSlots(vehicleId);
        int needed = VehicleArmament.slotsNeeded(value, stacks);
        if (slots > 0 && needed > slots) {
            fail("站「" + station.station + "」需要 " + needed + " 格，弹药舱只有 " + slots
                    + " 格；请改成 " + VehicleArmament.maxRounds(slots, stacks) + " 发以内");
            return false;
        }
        station.rounds = value;
        station.magazines = -1;
        return true;
    }

    /**
     * Pushes the typed text onto the station immediately when it is usable.
     *
     * <p>Written through on every keystroke rather than held until a commit, so the operator can never type a
     * number, click something else, and silently lose it. An unusable value is deliberately <em>not</em>
     * written: the station keeps its last good setting instead of being zeroed by a half-typed number, and the
     * caption says which of the two it is.
     */
    private void applyLive(VehicleArmament.Station station) {
        String text = roundsText == null ? "" : roundsText.trim();
        if (text.isEmpty()) {
            station.rounds = -1;
            station.magazines = -1;
            return;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return;
        }
        if (value < 0 || value > VehicleArmament.MAX_ROUNDS) return;
        List<Integer> stacks = stackSizes(station);
        int slots = OpsScreen.vehicleSlots(vehicleId);
        if (slots > 0 && VehicleArmament.slotsNeeded(value, stacks) > slots) return;
        station.rounds = value;
        station.magazines = -1;
    }

    @Override
    protected String subtitle() {
        return vehicleId;
    }

    /**
     * Seeds one entry per real weapon station the first time the screen opens.
     *
     * <p>Doing this up front is what makes the page usable: the operator sees the vehicle's actual stations
     * ("Cannon", "MachineGun") and can edit each, instead of having to invent names that would never match
     * what the addon uses.
     */
    private void seedStations() {
        if (seeded) return;
        seeded = true;
        for (String name : OpsScreen.vehicleStations(vehicleId)) {
            if (working.forStation(name) == null) {
                working.stations.add(new VehicleArmament.Station(name));
            }
        }
    }

    @Override
    protected void rebuild() {
        clearWidgets();
        seedStations();
        form = newForm(Ui.headerBody(layout.left));

        List<String> realStations = OpsScreen.vehicleStations(vehicleId);
        form.section("武器站 · " + working.stations.size() + " 个");
        if (realStations.isEmpty()) {
            // Two very different causes, and telling them apart is the difference between "press rescan" and
            // "this vehicle genuinely has no weapons".
            boolean scanned = Client.catalog.has("vehicleArmament");
            form.caption(scanned
                            ? "这台载具没有可读取的武器站数据（可能本身无武装，或插件数据不可读）。"
                            : "资源库尚未扫描，无法读取武器站。请到「武器/载具库」点一次「重扫资源库」。",
                    Ui.TEXT_WARN);
        }
        int shown = Math.min(working.stations.size(), 8);
        for (int i = 0; i < shown; i++) {
            final int index = i;
            VehicleArmament.Station station = working.stations.get(i);
            String summary = station.rounds >= 0 ? station.rounds + " 发"
                    : station.magazines >= 0 ? station.magazines + " 组" : "默认弹药";
            if (!station.ammo.isEmpty()) summary += " · " + station.ammo.size() + " 种弹";
            if (!station.attachments.isEmpty()) summary += " · " + station.attachments.size() + " 配件";
            place(form, form.take(Layout.ROW),
                    (index == stationIndex ? "● " : "") + "武器站 " + station.station,
                    summary, "vehicle", vehicleId, true, () -> {
                        stationIndex = index;
                        roundsText = null;
                        rebuild();
                    });
        }
        Layout.Rect[] addRemove = form.pair(Layout.ROW);
        place(form, addRemove[0], "添加武器站", "", "", "", working.stations.size() < 16, () -> {
            working.stations.add(new VehicleArmament.Station(nextStation()));
            stationIndex = working.stations.size() - 1;
            roundsText = null;
            rebuild();
        });
        place(form, addRemove[1], "删除武器站", "", "", "", !working.stations.isEmpty(), () -> {
            working.stations.remove(Math.min(stationIndex, working.stations.size() - 1));
            stationIndex = 0;
            roundsText = null;
            rebuild();
        });

        VehicleArmament.Station station = currentStation();
        if (station == null) {
            form.finish();
            buildBar();
            return;
        }

        form.section("弹药 · 武器站 " + station.station);
        roundsField(station);

        form.section("允许弹种 · 本站 " + candidatesCountHint());
        if (station.ammo.isEmpty()) {
            form.caption("不限：这台载具支持的全部弹种都可用。", Ui.TEXT_OK);
        } else {
            form.caption("已限制为 " + station.ammo.size() + " 种，其余弹种不允许装填。", Ui.TEXT_WARN);
        }
        List<String> candidates = ammoCandidates();
        if (candidates.isEmpty() && !station.ammo.isEmpty()) {
            form.caption("已配置的弹种：" + String.join("、", station.ammo), Ui.TEXT_FAINT);
        }
        int shownAmmo = Math.min(candidates.size(), 8);
        for (int i = 0; i < shownAmmo; i++) {
            String id = candidates.get(i);
            boolean allowed = station.allowsAmmo(id);
            place(form, form.take(Layout.ROW), (allowed ? "✓ " : "✗ ") + Catalog.prettify(id),
                    allowed ? "允许" : "禁止", "item", id, true, () -> {
                        if (!station.ammo.remove(id)) station.ammo.add(id);
                        rebuild();
                    });
        }
        if (candidates.size() > shownAmmo) {
            form.caption("还有 " + (candidates.size() - shownAmmo) + " 种未显示。", Ui.TEXT_FAINT);
        }
        place(form, form.take(Layout.ROW), "恢复为不限弹种", "", "", "", !station.ammo.isEmpty(), () -> {
            station.ammo.clear();
            rebuild();
        });

        form.section("配件");
        for (String slot : ATTACHMENT_SLOTS) {
            Integer value = station.attachments.get(slot);
            place(form, form.take(Layout.ROW), VehicleArmament.Station.slotLabel(slot),
                    value == null ? "默认" : "编号 " + value, "", "", true, () -> {
                        // Cycle through "unset" and 0..3. Attachment values are indices into the gun's own
                        // attachment table; four steps covers what Superb Warfare defines without pretending
                        // to know a per-weapon maximum this mod cannot read.
                        int next = value == null ? 0 : value + 1;
                        if (next > 3) station.attachments.remove(slot);
                        else station.attachments.put(slot, Integer.valueOf(next));
                        rebuild();
                    });
        }
        form.paragraph("配件编号对应插件自己的配件表；不确定时保持「默认」，部署时会沿用武器原始配置。",
                Ui.TEXT_FAINT);
        form.finish();
        buildBar();
    }

    /** Round count for the current station, typed by hand, with the rack's real capacity reported. */
    private void roundsField(VehicleArmament.Station station) {
        List<Integer> stacks = stackSizes(station);
        int slots = OpsScreen.vehicleSlots(vehicleId);
        int capacity = VehicleArmament.maxRounds(slots, stacks);
        int auto = VehicleArmament.defaultRounds(stacks);

        // Displayed from the typed text when there is any. Reading it back from station.rounds instead is what
        // made a typed number appear to vanish: the box is rebuilt on every screen refresh.
        String shown = roundsText != null ? roundsText
                : station.rounds >= 0 ? Integer.toString(station.rounds)
                : station.magazines >= 0 ? Integer.toString(station.magazines * 32 * Math.max(1, stacks.size()))
                : "";
        form.text(station.rounds >= 0 ? "弹药数（发）" : "弹药数（发）· 留空按默认 " + auto,
                Ui.TEXT_FAINT);
        var box = edit(form.take(16, 3), shown, 6, text -> {
            roundsText = text;
            applyLive(station);
        });
        box.setHint(net.minecraft.network.chat.Component.literal("留空=默认 " + auto));
        form.add(box);

        if (slots > 0) {
            int smallest = stacks.isEmpty() ? 0 : stacks.stream().mapToInt(Integer::intValue).min().orElse(0);
            form.caption("弹药舱 " + slots + " 格 · 每格约 " + smallest + " 发 · 最多 " + capacity + " 发",
                    Ui.TEXT_FAINT);
        } else {
            form.caption("读不到弹药舱格数，无法校验容量（仍可配置）。", Ui.TEXT_WARN);
        }

        int typed = parseRounds(station);
        if (typed < 0) {
            form.caption("弹药数需要是 0 ～ " + VehicleArmament.MAX_ROUNDS + " 的整数。", Ui.TEXT_ERROR);
        } else if (slots > 0 && VehicleArmament.slotsNeeded(typed, stacks) > slots) {
            form.caption("超出弹药舱容量：需要 "
                    + VehicleArmament.slotsNeeded(typed, stacks) + " 格，只有 " + slots
                    + " 格。请改成 " + capacity + " 发以内。", Ui.TEXT_ERROR);
        } else if (typed == 0) {
            form.caption("0 = 本站空仓出场，必须靠补给。已写入草稿。", Ui.TEXT_WARN);
        } else {
            form.caption("已写入草稿：" + typed + " 发，占用 "
                    + VehicleArmament.slotsNeeded(typed, stacks) + " / " + slots + " 格。", Ui.TEXT_OK);
        }
        form.caption("留空 = 按弹种堆叠自动决定（轻弹数百发、炮弹数十发）。", Ui.TEXT_FAINT);
        // The two-step reminder sits right under the field, where the operator is looking when they finish
        // typing.
        form.caption("改完点底部「应用配置」，回到编辑器再点「保存地图」才会生效。", Ui.TEXT_WARN);
    }

    /** Stack sizes of the current station's ammunition, restricted to the types it may load. */
    private List<Integer> stackSizes(VehicleArmament.Station station) {
        if (station == null) return List.of();
        List<Integer> out = new ArrayList<>();
        var sizes = OpsScreen.vehicleStackSizes(vehicleId, station.station);
        for (String id : OpsScreen.vehicleAmmo(vehicleId, station.station)) {
            if (station.allowsAmmo(id)) out.add(sizes.getOrDefault(id, 1));
        }
        return out;
    }

    /**
     * The round count the field currently represents, or {@code -1} when the text is not a usable number.
     *
     * <p>Empty text means "use the default", which is a valid state and is reported as such rather than being
     * confused with zero.
     */
    private int parseRounds(VehicleArmament.Station station) {
        String text = roundsText == null ? "" : roundsText.trim();
        if (text.isEmpty()) return VehicleArmament.defaultRounds(stackSizes(station));
        try {
            int value = Integer.parseInt(text);
            return value < 0 || value > VehicleArmament.MAX_ROUNDS ? -1 : value;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * A station name not already used.
     *
     * <p>Prefers a real station of this vehicle that has no entry yet, so "add" continues the vehicle's own
     * list. Falls back to a numeric placeholder only when the vehicle data is unavailable.
     */
    private String nextStation() {
        for (String name : OpsScreen.vehicleStations(vehicleId)) {
            if (working.forStation(name) == null) return name;
        }
        for (int i = 0; i < 16; i++) {
            String candidate = Integer.toString(i);
            if (working.forStation(candidate) == null) return candidate;
        }
        return Integer.toString(working.stations.size());
    }

    private List<String> ammoCandidates() {
        VehicleArmament.Station station = currentStation();
        if (station == null) return List.of();
        return OpsScreen.vehicleAmmo(vehicleId, station.station);
    }

    /** The station currently being edited, or {@code null} when none exists. */
    private VehicleArmament.Station currentStation() {
        if (working.stations.isEmpty()) return null;
        return working.stations.get(Math.min(stationIndex, working.stations.size() - 1));
    }

    /** "N 种可用", or a warning when the catalogue has no data for this station. */
    private String candidatesCountHint() {
        int count = ammoCandidates().size();
        return count == 0 ? "（无数据）" : "共 " + count + " 种";
    }

    private void buildBar() {
        Layout.Rect bar = layout.bar;
        if (bar.h() <= 0) return;
        Form barRow = newForm(bar.inset(2));
        Layout.Rect[] cells = Layout.columns(bar.inset(2), 3, 3);
        place(barRow, cells[0], "取消", "", "", "", true, this::onClose);
        place(barRow, cells[1], "恢复全部默认", "", "", "", true, () -> {
            working.stations.clear();
            stationIndex = 0;
            roundsText = null;
            rebuild();
        });
        var apply = place(barRow, cells[2], "应用配置", "", "", "", true, () -> {
            if (commit()) onCloseToParent();
        });
        // The confirm step is the one the operator was missing, so it is the highlight of the bar.
        apply.primary = true;
        barRow.finish();
        this.barForm = barRow;
    }

    private void onCloseToParent() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (form == null) return;
        Ui.header(graphics, font, layout.left, "武器配置");
        Layout.Rect body = form.area();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        form.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        Layout.Rect right = Ui.header(graphics, font, layout.right, "载具武器");
        int y = right.y() + 6;
        graphics.drawString(font, Ui.clip(font, "载具：" + vehicleId, right.w() - 12),
                right.x() + 6, y, Ui.TEXT, false);
        y += 14;
        graphics.drawString(font, "弹药数按「发」输入，留空按弹种默认。", right.x() + 6, y,
                Ui.TEXT_DIM, false);
        y += 12;
        int slots = OpsScreen.vehicleSlots(vehicleId);
        graphics.drawString(font, Ui.clip(font, "弹药舱 " + slots + " 格；一格能放几发取决于弹种。",
                right.w() - 12), right.x() + 6, y, Ui.TEXT_DIM, false);
        y += 12;
        graphics.drawString(font, Ui.clip(font, "限制弹种后，部署与补给都只给允许的弹药。",
                right.w() - 12), right.x() + 6, y, Ui.TEXT_FAINT, false);
        y += 16;
        graphics.drawString(font, "① 点底部「应用配置」", right.x() + 6, y, Ui.TEXT_WARN, false);
        y += 12;
        graphics.drawString(font, "② 回编辑器点「保存地图」", right.x() + 6, y, Ui.TEXT_WARN, false);

        if (barForm != null) barForm.render(graphics, mouseX, mouseY, partialTick);
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
