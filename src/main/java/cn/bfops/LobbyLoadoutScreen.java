package cn.bfops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Lobby-side loadout editor: pick a class's two weapons and their fittings before joining a room.
 *
 * <p>The lobby has no map, so the weapon list is the whole scanned catalogue rather than one map's
 * authorised options. A map still applies its own authorisation at deploy time — a weapon the map does
 * not allow for that class falls back to the first allowed one — so the choice made here is a
 * preference, not a bypass.
 *
 * <p>State is stored in {@link Client#loadoutMemory} (per class) and {@link Client#fittingMemory} (per
 * weapon), the same stores the in-match {@link BattleScreen} reads, so a pick made here is exactly the
 * pick a later deployment uses.
 */
final class LobbyLoadoutScreen extends FrontScreen {
    private final Screen parent;
    private String kitId = "";
    private Form form, bar;

    LobbyLoadoutScreen(Screen parent) {
        super("配装与配件");
        this.parent = parent;Client.send(Client.request("catalogAll",""));
    }

    private static List<String> guns() {
        List<String> out = new ArrayList<>();
        if (Client.catalog == null || !Client.catalog.has("entries")) return out;
        for (var element : Client.catalog.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            if ("gun".equals(Engine.str(entry, "kind", ""))) out.add(Engine.str(entry, "id", ""));
        }
        return out;
    }

    private String primary() { String[] s = Client.lobbyLoadouts.get(kitId); return s == null ? "" : s[1]; }
    private String secondary() { String[] s = Client.lobbyLoadouts.get(kitId); return s == null ? "" : s[2]; }
    private String melee() { String[] s = Client.lobbyLoadouts.get(kitId); return s == null ? "" : s[3]; }
    private String throwable() { String[] s = Client.lobbyLoadouts.get(kitId); return s == null || s.length < 5 ? "" : s[4]; }
    /** Single writer so every loadout array keeps the same five-slot shape. */
    private void writeLoadout(String primary, String secondary, String melee, String throwable) {
        Client.lobbyLoadouts.put(kitId, new String[]{kitId, primary, secondary, melee, throwable});
    }

    private static List<String> knives() {
        List<String> out = new ArrayList<>();
        if (Client.catalog != null && Client.catalog.has("knives")) {
            for (var element : Client.catalog.getAsJsonArray("knives")) out.add(element.getAsString());
        }
        return out;
    }

    private Map<String, String> fitting(String kind, String id) {
        if (Client.fittingMemory.size() > 512) Client.fittingMemory.clear();
        return Client.fittingMemory.computeIfAbsent(kind + ":" + id, k -> new LinkedHashMap<>());
    }

    private String name(String id) {
        if (id == null || id.isEmpty()) return "未选择";
        if(LrtEquipment.explicit(id))return KitItems.name(id);
        if (Client.state != null && Client.state.has("names")) {
            var names = Client.state.getAsJsonObject("names");
            if (names != null && names.has(id)) return names.get(id).getAsString();
        }
        if (Client.catalog != null && Client.catalog.has("entries")) {
            for (var element : Client.catalog.getAsJsonArray("entries")) {
                var entry = element.getAsJsonObject();
                if (Engine.str(entry, "id", "").equals(id)) return Engine.str(entry, "name", id);
            }
        }
        return Catalog.prettify(id);
    }

    private void save() {
        if (kitId.isEmpty()) return;
        if (Client.lobbyLoadouts.size() > 512) Client.lobbyLoadouts.clear();
        Client.lobbyLoadouts.put(kitId, new String[]{kitId, primary(), secondary(), melee(), throwable()});LoadoutProfiles.save();
    }

    void catalogUpdated(){rebuild();}
    @Override
    protected void rebuildContent() {
        int off = form == null ? 0 : form.scroll();
        clearWidgets();
        form = newForm(Ui.headerBody(layout.left));
        if (kitId.isEmpty()) kitId = FixedClasses.Role.values()[0].name;

        form.section("兵种");
        dropdown(form,"编辑兵种",kitId,java.util.Arrays.stream(FixedClasses.Role.values())
                .map(role->new ChoiceScreen.Choice(role.name,role.name,"role",role.stem)).toList(),
                id->{save();kitId=id;rebuild();});

        form.section("武器");
        List<String> gunList = guns();
        row(form, "主武器", name(primary()), "gun", primary(), () -> pickWeapon(true, gunList));
        row(form, "副武器", name(secondary()), "gun", secondary(), () -> pickWeapon(false, gunList));
        row(form, "近身武器", melee().isEmpty() ? "无" : name(melee()), melee().isEmpty() ? "" : "gun", melee(), this::pickMelee);
        row(form, "投掷武器", throwable().isEmpty() ? "无" : name(throwable()), throwable().isEmpty() ? "" : "item", throwable(), this::pickThrowable);
        if (!primary().isEmpty()) row(form, "改装主武器", "选择配件", () -> customize(primary()));
        if (!secondary().isEmpty()) row(form, "改装副武器", "选择配件", () -> customize(secondary()));
        form.section("载具与弹种");
        row(form,"载具库","全部已安装载具",()->{
            var choices=new ArrayList<ChoiceScreen.Choice>();
            if(Client.catalog.has("entries"))for(var value:Client.catalog.getAsJsonArray("entries")){var e=value.getAsJsonObject();if(Engine.str(e,"kind","").equals("vehicle"))choices.add(new ChoiceScreen.Choice(Engine.str(e,"id",""),Engine.str(e,"name",""),"vehicle"));}
            minecraft.setScreen(new ChoiceScreen(this,"大厅载具库",choices,id->minecraft.setScreen(new FittingScreen(this,"",id,"vehicle",fitting("vehicle",id),""))));
        });
        form.paragraph("进入任意房间都会沿用这里的配装；若该地图未授权某武器，会回退到该兵种首个可用武器。", Ui.TEXT_DIM);

        form.scrollTo(off);
        form.finish();

        bar = newForm(layout.bar.inset(2));
        var cells = Layout.columns(layout.bar.inset(2), 2, 4);
        place(bar, cells[0], "返回大厅", "", "", "", true, () -> { save(); minecraft.setScreen(parent); });
        place(bar, cells[1], "保存配装", "", "", "", true, () -> { save(); say("已保存配装"); });
        bar.finish();
    }

    private void pickWeapon(boolean main, List<String> gunList) {
        List<ChoiceScreen.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("", "无", "gun"));
        for (String id : gunList) choices.add(new ChoiceScreen.Choice(id, name(id), "gun"));
        minecraft.setScreen(new ChoiceScreen(this, main ? "主武器" : "副武器", choices, id -> {
            String[] cur = Client.lobbyLoadouts.get(kitId);
            String p = cur == null ? "" : cur[1], s = cur == null ? "" : cur[2], m = cur == null ? "" : cur[3];
            if (main) p = id; else s = id;
            Client.lobbyLoadouts.put(kitId, new String[]{kitId, p, s, m});
            rebuild();
        }));
    }

    private void pickMelee() {
        List<ChoiceScreen.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("", "无 / 不装备", "item"));
        for (String id : knives()) choices.add(new ChoiceScreen.Choice(id, name(id), "gun"));
        choices.add(new ChoiceScreen.Choice("@item", "任意物品…", "item"));
        minecraft.setScreen(new ChoiceScreen(this, "近身武器", choices, id -> {
            String[] cur = Client.lobbyLoadouts.get(kitId);
            String p = cur == null ? "" : cur[1], s = cur == null ? "" : cur[2];
            if ("@item".equals(id)) {
                minecraft.setScreen(new ItemPickerScreen(this,true, chosen -> {
                    if (chosen == null || chosen.isEmpty()) return;
                    writeLoadout(p, s, chosen, throwable());
                    rebuild();
                }));
                return;
            }
            writeLoadout(p, s, id, throwable());
            rebuild();
        }));
    }

    /** Throwables are free-form items, so the lobby offers any item id, not only grenades. */
    private void pickThrowable() {
        List<ChoiceScreen.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("", "无 / 不装备", "item"));
        choices.add(new ChoiceScreen.Choice("@item", "任意物品…", "item"));
        minecraft.setScreen(new ChoiceScreen(this, "投掷武器", choices, id -> {
            if ("@item".equals(id)) {
                minecraft.setScreen(new ItemPickerScreen(this, true, chosen -> {
                    if (chosen == null || chosen.isEmpty()) return;
                    writeLoadout(primary(), secondary(), melee(), chosen);
                    rebuild();
                }));
                return;
            }
            writeLoadout(primary(), secondary(), melee(), id);
            rebuild();
        }));
    }

    private void customize(String id) {
        if (id == null || id.isEmpty()) { warn("请先选择武器"); return; }
        minecraft.setScreen(new FittingScreen(this, "", id, "gun", fitting("gun", id), ""));
    }

    @Override
    protected void paint(GuiGraphics g, int mx, int my, float dt) {
        Ui.header(g, font, layout.left, "大厅配装");
        form.render(g, mx, my, dt);
        bar.render(g, mx, my, dt);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {if(dropdownOpen())return super.mouseScrolled(x,y,h,v);
        return form.wheel(x, y, v) || super.mouseScrolled(x, y, h, v);
    }

    @Override
    public void onClose() { save(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
