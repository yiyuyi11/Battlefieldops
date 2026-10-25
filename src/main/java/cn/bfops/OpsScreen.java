package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Operator console: deployment status, the map line editor, capture points and spawns, scoring rules,
 * kits, the weapon/vehicle library, and platform compatibility.
 *
 * <p>Everything here edits a *draft*. The draft is only persisted when the server accepts it, and the
 * server re-runs {@link Model.Arena#validate()} plus the catalogue whitelist check, so a client that
 * skips validation still cannot save a broken map or an unauthorised kit. The GUI's job is to make the
 * rejection unlikely and legible; the footer carries the server's verdict word for word.
 *
 * <p>The page is built from {@link Layout} / {@link Ui} / {@link Form}, so the left control column and
 * the right canvas are two panels of one opaque window instead of two independently painted layers.
 */
final class OpsScreen extends FrontScreen {
    private static final String[] TABS = {"部署", "地图划线", "据点/出生", "规则", "兵种", "武器/载具库", "兼容"};

    /**
     * The map being edited. Package-private so the modal armament editor can write its result back
     * without the editor having to expose a whole mutation API for one field.
     */
    Model.Arena draft;
    private int tab;
    private int pointIndex, spawnIndex, supplyIndex, kitIndex, rulePage, zone = -1;
    private int catalogPage, catalogLocal;
    private String query = "", entryId = "", entryKind = "";

    private String deployKit = "", deploySpawn = "", deployVehicle = "";

    /**
     * What a left click on the points map places: {@code null}, {@code "point"} or {@code "spawn"}.
     *
     * <p>Set by "新增", cleared once the click lands, so a single click cannot spray markers across the
     * map while the operator is still deciding where the first one goes.
     */
    private String placing;

    /** True until the first server snapshot has been folded into the draft. */
    private boolean pendingLoad = true;
    private String selected = "";

    private Form form;
    /** Right-pane actions (catalogue selection, preview buttons) — never scrolled with the left column. */
    private Form sideForm;
    private Layout.Rect canvasPanel;
    /**
     * One map widget for the whole screen: the line canvas, the deploy preview and the points page all
     * draw through this, so every page shows the real terrain instead of a flat grid.
     */
    private final MapPicker map = new MapPicker();
    private int refreshTicks;
    private int formTab=-1;
    private final Map<String,String> rawFields=new HashMap<>();
    private final DraftHistory history=new DraftHistory();
    private String savedDraft="";
    private String savingId="";
    private long saveStarted;
    private int movingVertex=-1;
    private String jsonDraft(){return BattlefieldOps.GSON.toJson(draft);}
    private boolean dirty(){try{return !invalid.isEmpty()||!jsonDraft().equals(savedDraft);}catch(IllegalArgumentException ex){return true;}}
    private String fieldKey(String label){return tab+":"+pointIndex+":"+spawnIndex+":"+kitIndex+":"+label;}
    void saved(JsonObject arena){
        if(!Engine.str(arena,"id","").equals(savingId))return;
        savedDraft=BattlefieldOps.GSON.toJson(BattlefieldOps.GSON.fromJson(arena,Model.Arena.class));
        savingId="";
        ok(dirty()?"已保存提交的版本；还有后续修改未保存":"保存成功！点击底部「作战房间」创建房间");
        rebuild();
    }
    private void discardThen(Runnable action){
        if(!savingId.isEmpty()){say("正在保存，请等待服务器回执");return;}
        if(!dirty()){action.run();return;}
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
            minecraft.setScreen(this);
            if(yes)action.run();
        },net.minecraft.network.chat.Component.literal("还有未保存的地图修改"),
          net.minecraft.network.chat.Component.literal("继续会丢弃当前草稿。可取消后按 Ctrl+S 保存。")));
    }
    @Override public void onClose(){discardThen(()->minecraft.setScreen(null));}
    private void undo(boolean redo){
        history.record(jsonDraft());String value=redo?history.redo():history.undo();
        if(value!=null){draft=BattlefieldOps.GSON.fromJson(value,Model.Arena.class);rawFields.clear();invalid.clear();rebuild();}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256&&placing!=null){placing=null;rebuild();say("已取消放置，坐标未改变");return true;}
        if(hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_S){guard(this::save);return true;}
        if(!(getFocused() instanceof EditBox)&&hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z){undo(hasShiftDown());return true;}
        if(!(getFocused() instanceof EditBox)&&hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y){undo(true);return true;}
        return super.keyPressed(key,scan,modifiers);
    }

    /** Per-tab scroll offsets, so switching tabs does not lose the operator's place. */
    private final Map<Integer, Integer> scrollMemory = new HashMap<>();

    OpsScreen() {
        super("战地风云 · 作战地图编辑器");
    }

    // ------------------------------------------------------------------ state glue

    private boolean admin() {
        return Client.state.has("admin") && Client.state.get("admin").getAsBoolean();
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static boolean flag(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).getAsBoolean();
    }

    private JsonObject me() {
        return obj(Client.state, "me");
    }

    private int selfTeam() {
        return me() == null ? -1 : Engine.num(me(), "team", -1);
    }

    /** Resolved gun / vehicle display name, falling back to the raw id. */
    private String name(String id) {
        if (id == null || id.isEmpty()) return "未选择";
        JsonObject names = obj(Client.state, "names");
        if (names != null && names.has(id)) return names.get(id).getAsString();
        if (Client.catalog.has("entries")) {
            for (JsonElement element : Client.catalog.getAsJsonArray("entries")) {
                JsonObject entry = element.getAsJsonObject();
                if (Engine.str(entry, "id", "").equals(id)) return Engine.str(entry, "name", id);
            }
        }
        return Catalog.prettify(id);
    }

    /** Called by {@link Client} whenever a state snapshot arrives. */
    void updated() {
        String msg = Engine.str(Client.state, "message", "");
        if (!msg.isEmpty()) {
            if (msg.startsWith("操作失败")) {savingId="";fail(msg);rebuild();}
            else ok(msg);
        }
        if (pendingLoad && (selected.isEmpty() || selected.equals(Engine.str(Client.state,"selected","")))) {
            load();
            rebuild();
        } else if (tab == 0) {
            rebuild();
        }
    }

    void catalogArrived() {
        if (tab == 5) rebuild();
    }

    /** Set by {@link Client} when a request is too large to transmit. */
    String notice = "";

    private void load() {
        selected = Engine.str(Client.state, "selected", "");
        draft = Client.state.has("arena")
                ? BattlefieldOps.GSON.fromJson(Client.state.get("arena"), Model.Arena.class) : null;
        if (draft == null) fresh();
        else {savedDraft=jsonDraft();rawFields.clear();invalid.clear();history.reset(jsonDraft());}
        center();
        pendingLoad = false;
    }

    private void fresh() {
        pendingLoad=false;
        rawFields.clear();invalid.clear();savedDraft="";
        draft = new Model.Arena();
        draft.id = "map_" + (System.currentTimeMillis() % 1000000);
        draft.dimension = minecraft.player == null ? "minecraft:overworld"
                : minecraft.player.level().dimension().location().toString();
        double x = minecraft.player == null ? 0 : minecraft.player.getX();
        double y = minecraft.player == null ? 64 : minecraft.player.getY();
        double z = minecraft.player == null ? 0 : minecraft.player.getZ();
        draft.boundary.addAll(List.of(
                new Model.Vec(x - 128, z - 128), new Model.Vec(x + 128, z - 128),
                new Model.Vec(x + 128, z + 128), new Model.Vec(x - 128, z + 128)));
        Model.Point point = new Model.Point();
        point.x = x;
        point.y = y;
        point.z = z;
        point.id = "A";
        draft.points.add(point);
        for (int i = 0; i < 2; i++) {
            Model.Spawn spawn = new Model.Spawn();
            spawn.id = i == 0 ? "蓝方基地" : "红方基地";
            spawn.team = i;
            spawn.x = x + (i == 0 ? -48 : 48);
            spawn.y = y;
            spawn.z = z;
            draft.spawns.add(spawn);
        }
        for (String role : List.of("突击兵", "支援兵", "工程兵", "侦察兵")) {
            Model.Kit kit = new Model.Kit();
            kit.id = role;
            draft.kits.add(kit);
        }
        selected = draft.id;
        pointIndex = spawnIndex = kitIndex = 0;
        history.reset(jsonDraft());
    }

    /** Frames the whole boundary, so the canvas opens on the map rather than on the player. */
    private void center() {
        map.frame(draft, minecraft.player == null ? 0 : minecraft.player.getX(),
                minecraft.player == null ? 0 : minecraft.player.getZ());
    }

    // ------------------------------------------------------------------ shell hooks

    @Override
    protected String[] tabs() {
        return TABS;
    }

    @Override
    protected int activeTab() {
        return tab;
    }

    @Override
    protected boolean onTab(int index) {
        if (index > 0 && !admin()) {
            warn("打开编辑器需要 OP 权限（等级 2）");
            return true;
        }
        rememberScroll();
        tab = index;
        zone = -1;
        rebuild();
        if (index == 5) fetchCatalog();
        return true;
    }

    @Override
    protected String subtitle() {
        if (draft == null) return "尚未载入地图";
        return draft.name + " [" + draft.id + "] · " + validationVerdict() + " · " + dimensionName() + (dirty()?" · 未保存":" · 已保存");
    }

    /**
     * Cached verdict from {@link Model.Arena#validate()}.
     *
     * <p>Validating on every frame would run the polygon self-intersection check 60 times a second for
     * a string that only changes when the draft does. It is recomputed in {@link #rebuild()}, which is
     * exactly when the draft can have changed.
     */
    private String validation = "";

    private String validationVerdict() {
        return validation.isEmpty() ? "校验通过" : validation;
    }

    private void revalidate() {
        if (draft == null) {
            validation = "尚未载入地图";
            return;
        }
        try {
            draft.validate();
            validation = invalid.isEmpty() ? "" : "有 " + invalid.size() + " 处字段格式错误";
        } catch (Exception ex) {
            validation = ex.getMessage() == null ? "配置无效" : ex.getMessage();
        }
    }

    private String dimensionName() {
        String dim = draft == null ? "" : draft.dimension;
        int colon = dim.indexOf(':');
        return colon >= 0 ? dim.substring(colon + 1) : dim;
    }

    @Override
    protected void poll() {
        if(!savingId.isEmpty()&&System.currentTimeMillis()-saveStarted>10000){savingId="";warn("未收到保存回执，请重试或检查连接；草稿仍保留");rebuild();}

        if (tab == 0 && ++refreshTicks >= 40) {
            refreshTicks = 0;
            action("state");
        }
        // The editor canvas and the deployment map both draw terrain, so ask for the bake on either.
        // Throttled inside; a no-op once this map's bake has arrived.
        Client.wantTerrain(selected);
    }

    @Override
    public void removed() {
        // Releases the terrain texture; leaving it registered leaks a GPU texture per screen close.
        map.close();
    }

    @Override
    protected void init() {
        if (draft == null) load();
        super.init();
    }

    private void rememberScroll() {
        if (form != null) scrollMemory.put(tab, form.scroll());
    }

    // ------------------------------------------------------------------ rebuild

    @Override
    protected void rebuild() {
        if(form!=null&&formTab>=0)scrollMemory.put(formTab,form.scroll());
        clearWidgets();
        if (draft == null) return;
        canvasPanel = layout.right;
        form = newForm(Ui.headerBody(layout.left));
        formTab=tab;
        sideForm = newForm(Ui.headerBody(layout.right));
        if (scrollMemory.containsKey(tab)) form.scrollTo(scrollMemory.get(tab));

        switch (tab) {
            case 0 -> deployment();
            case 1 -> boundary();
            case 2 -> points();
            case 3 -> rules();
            case 4 -> kits();
            case 5 -> catalog();
            default -> compat();
        }
        form.finish();
        sideForm.finish();
        buildBar();
        revalidate();
        try{history.record(jsonDraft());}catch(IllegalArgumentException ex){warn("草稿坐标无效，请修正坐标或重新载入地图");}
        scrollMemory.put(tab, form.scroll());
    }

    // ------------------------------------------------------------------ helpers

    private void action(String op) {
        Client.send(Client.request(op, selected));
    }

    private void cycleMap(int delta) {discardThen(()->cycleMapConfirmed(delta));}
    private void cycleMapConfirmed(int delta) {
        if (me() != null) {
            warn("请先退出当前房间，再切换地图");
            return;
        }
        JsonArray maps = Client.state.has("maps") ? Client.state.getAsJsonArray("maps") : new JsonArray();
        if (maps.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < maps.size(); i++) {
            if (Engine.str(maps.get(i).getAsJsonObject(), "id", "").equals(selected)) index = i;
        }
        selected = Engine.str(maps.get(Math.floorMod(index + delta, maps.size())).getAsJsonObject(), "id", "");
        pendingLoad = true;
        action("state");
    }

    private void save() {
        Model.require(savingId.isEmpty(),"正在保存，请等待服务器回执");
        Model.require(invalidate().isEmpty(), "仍有字段格式错误，请修正后再保存");
        draft.validate();
        JsonObject request = Client.request("save", draft.id);
        request.add("arena", BattlefieldOps.GSON.toJsonTree(draft));
        request.addProperty("baseId", savedDraft.isEmpty()?"":BattlefieldOps.GSON.fromJson(savedDraft,Model.Arena.class).id);
        request.addProperty("baseRevision",MapFiles.revision(savedDraft));
        selected = draft.id;
        savingId=draft.id;saveStarted=System.currentTimeMillis();
        say("正在保存到服务器…");
        try{Client.send(request);}catch(RuntimeException ex){savingId="";rebuild();throw ex;}
        rebuild();
    }

    /** Names of fields the operator typed something invalid into, for the summary line. */
    private final Set<String> invalid = new LinkedHashSet<>();

    private Set<String> invalidate() {
        return invalid;
    }

    /** Integer field with a range check that reports into the footer instead of throwing on each key. */
    private void number(String label, int value, IntConsumer setter, int lo, int hi) {
        Layout.Rect row = form.take(30, 3);
        form.textAt(new Layout.Rect(row.x(), row.y(), row.w(), 10), label, Ui.TEXT_FAINT);
        EditBox box = edit(new Layout.Rect(row.x(), row.y() + 11, row.w(), 16),
                rawFields.getOrDefault(fieldKey(label),Integer.toString(value)), 9, text -> {
                    rawFields.put(fieldKey(label),text);
                    int parsed;
                    try {
                        parsed = Integer.parseInt(text.trim());
                    } catch (NumberFormatException ex) {
                        invalid.add(label);
                        throw new IllegalArgumentException(label + " 需要整数");
                    }
                    if (parsed < lo || parsed > hi) {
                        invalid.add(label);
                        throw new IllegalArgumentException(label + " 范围 " + lo + "～" + hi);
                    }
                    invalid.remove(label);
                    setter.accept(parsed);
                });
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.literal(label + "：" + lo + " ～ " + hi)));
        form.add(box);
    }

    private void decimal(String label, double value, DoubleConsumer setter) {
        Layout.Rect row = form.take(30, 3);
        form.textAt(new Layout.Rect(row.x(), row.y(), row.w(), 10), label, Ui.TEXT_FAINT);
        EditBox box = edit(new Layout.Rect(row.x(), row.y() + 11, row.w(), 16),
                rawFields.getOrDefault(fieldKey(label),String.format(Locale.ROOT, "%.1f", value)), 16, text -> {
                    rawFields.put(fieldKey(label),text);
                    double parsed;
                    try {
                        parsed = Double.parseDouble(text.trim());
                    } catch (NumberFormatException ex) {
                        invalid.add(label);
                        throw new IllegalArgumentException(label + " 需要数字");
                    }
                    if(!Double.isFinite(parsed)){invalid.add(label);throw new IllegalArgumentException(label+" 数值无效");}
                    invalid.remove(label);
                    setter.accept(parsed);
                });
        form.add(box);
    }

    // ------------------------------------------------------------------ tab 0: deployment

    /** Deploy sites the server will accept for this player, including held capture points. */
    private List<Deploy.Site> sites() {
        return Deploy.fromState(Client.state, draft, selfTeam());
    }

    private List<String> allowedSpawns() {
        return sites().stream().map(Deploy.Site::id).toList();
    }

    /** {@code 'A'} for an aircraft, {@code 'G'} otherwise, from the server's classification map. */
    private char vehicleClass(String id) {
        return isAircraft(id) ? 'A' : 'G';
    }

    /** One line describing what the draft's activity zones do, for the deploy tab. */
    private String zoneSummary() {
        if (draft.zones == null || draft.zones.isEmpty()) return "活动区域：无限制（全图可行动）";
        boolean blue = draft.zone(0) != null, red = draft.zone(1) != null;
        if (!blue && !red) return "活动区域：无限制（全图可行动）";
        String label = blue && red ? "双方各自区域" : blue ? "仅蓝方受限" : "仅红方受限";
        return "活动区域：" + label + " · 出界按离开战区处理"
                + (draft.mode.equals("BREAKTHROUGH") ? " · 突破模式不生效" : "");
    }

    private void deployment() {
        JsonObject state = Client.state;
        boolean room = flag(state, "roomExists");
        boolean running = flag(state, "roomRunning");
        int players = Engine.num(state, "roomPlayers", 0);
        int team = selfTeam();

        form.section("房间状态");
        form.caption(room ? (running ? "对局进行中" : "等待开始") : "尚未创建房间",
                room ? (running ? Ui.TEXT_OK : Ui.TEXT_WARN) : Ui.TEXT_DIM);
        form.caption("本图人数 " + players, Ui.TEXT_FAINT);
        // Say up front whether this map confines the teams, and to what. A player who does not know
        // their area exists will walk out of it and get the exit countdown with no idea why.
        form.caption(zoneSummary(), Ui.TEXT_FAINT);

        if (me() == null) {
            // Not in the room yet: the only meaningful actions are join / create / close.
            if (room) primary(form, "进入本图房间", true, () -> action("join"));
            else if (admin()) primary(form, "创建本图房间", true, () -> action("createRoom"));
            else form.paragraph("这张地图还没有房间，请等待管理员创建。", Ui.TEXT_WARN);
            if (admin() && room) row(form, "关闭房间", "结束对局", () -> action("stop"));
        } else {
            form.caption("阵营  " + (team == 0 ? "蓝方" : team == 1 ? "红方" : "未选择"),
                    team < 0 ? Ui.TEXT_WARN : team == 0 ? Ui.BLUE : Ui.RED);
            if (team < 0) {
                primary(form, "选择蓝方 / 进攻方", true, () -> join(0));
                primary(form, "选择红方 / 防守方", true, () -> join(1));
            }
            if (admin() && !running) row(form, "开始本局", "锁定阵营", () -> action("start"));
            row(form, "退出房间", "恢复原背包", () -> action("leave"));
        }

        form.section("部署");
        if (me() != null && team >= 0 && running) {
            if (draft.kits.stream().noneMatch(k -> k.id.equals(deployKit))) {
                deployKit = draft.kits.isEmpty() ? "" : draft.kits.getFirst().id;
            }
            row(form, "兵种", deployKit, "role", deployKit, () -> {
                deployKit = cycle(draft.kits.stream().map(k -> k.id).toList(), deployKit);
                rebuild();
            });
            Model.Kit kit = draft.kits.stream().filter(k -> k.id.equals(deployKit)).findFirst().orElse(null);
            if (kit != null) {
                form.fact("主武器", name(kit.primary), Ui.TEXT_DIM);
                form.fact("副武器", kit.secondary.isEmpty() ? "无" : name(kit.secondary), Ui.TEXT_DIM);
            }
            List<String> spawns = allowedSpawns();
            if (!spawns.contains(deploySpawn)) deploySpawn = spawns.isEmpty() ? "" : spawns.getFirst();
            row(form, "部署点", deploySpawn.isEmpty() ? "无可用" : deploySpawn, () -> {
                deploySpawn = cycle(spawns, deploySpawn);
                rebuild();
            });
            // Vehicles are split by class because an aircraft needs a runway point; mixing them into
            // one cycling list is what made "no plane point to pick" invisible.
            List<String> groundVehicles = new ArrayList<>();
            List<String> airVehicles = new ArrayList<>();
            for (String id : draft.vehicles) {
                if (vehicleClass(id) == 'A') airVehicles.add(id);
                else groundVehicles.add(id);
            }
            Deploy.Site here = sites().stream().filter(s -> s.id().equals(deploySpawn))
                    .findFirst().orElse(null);
            if (!groundVehicles.isEmpty()) {
                List<String> options = new ArrayList<>();
                options.add("");
                options.addAll(groundVehicles);
                String current = vehicleClass(deployVehicle) == 'A' ? "" : deployVehicle;
                if (!options.contains(current)) current = "";
                boolean fits = here == null || here.allows('G');
                row(form, "地面载具", current.isEmpty() ? "无" : name(current),
                        "vehicle", current, fits, () -> {
                            String picked = cycle(options, vehicleClass(deployVehicle) == 'A' ? "" : deployVehicle);
                            deployVehicle = picked.isEmpty()
                                    ? (vehicleClass(deployVehicle) == 'A' ? deployVehicle : "") : picked;
                            // Same reconciliation as the in-match screen: pick the site that can launch it.
                            deploySpawn = Deploy.siteFor(sites(), deploySpawn, deployVehicle,
                                    vehicleClass(deployVehicle));
                            rebuild();
                        });
            }
            if (!airVehicles.isEmpty()) {
                List<String> options = new ArrayList<>();
                options.add("");
                options.addAll(airVehicles);
                String current = vehicleClass(deployVehicle) == 'A' ? deployVehicle : "";
                boolean fits = here == null || here.allows('A');
                row(form, "航空器", current.isEmpty() ? "无" : name(current),
                        "vehicle", current, fits, () -> {
                            String picked = cycle(options, vehicleClass(deployVehicle) == 'A'
                                    ? deployVehicle : "");
                            deployVehicle = picked.isEmpty()
                                    ? (vehicleClass(deployVehicle) == 'A' ? "" : deployVehicle) : picked;
                            deploySpawn = Deploy.siteFor(sites(), deploySpawn, deployVehicle,
                                    vehicleClass(deployVehicle));
                            rebuild();
                        });
                if (draft.spawns.stream().noneMatch(s -> s.allows('A'))) {
                    form.caption("本图没有飞机点位，航空器无法出动", Ui.TEXT_WARN);
                }
            }
            // Hoisted out of the block below: the deploy button needs it. With no vehicle picked there
            // is nothing to cool down, so the restriction is vacuously satisfied.
            boolean ready = true;
            if (!deployVehicle.isEmpty()) {
                JsonObject vehicleState = obj(state, "vehicleState");
                String text = vehicleState == null ? "" : Engine.str(vehicleState, "text", "");
                ready = flag(vehicleState, "deployable");
                // The cooldown lives inside vehicleState, and the key is "remaining". This page used to
                // read "vehicleRemaining" off the root snapshot — a key the server never sends — so the
                // line always claimed "冷却 0s", i.e. it told a locked-out operator they could deploy.
                // Same defect as the in-match screen had; it survived here because the two screens
                // compute this independently.
                int remaining = vehicleState == null ? 0 : Engine.num(vehicleState, "remaining", 0);
                form.caption("冷却 "
                        + remaining + "s / " + draft.vehicleCooldown
                        + "s · 本局 "
                        + Engine.num(vehicleState, "used", 0) + "/"
                        + Engine.num(vehicleState, "limit", draft.vehicleLimitFor(selfTeam())), Ui.TEXT_FAINT);
                form.caption(text.isEmpty() ? "载具状态未知" : text,
                        ready ? Ui.TEXT_OK : Ui.TEXT_WARN);
                if (here != null && !here.allows(vehicleClass(deployVehicle))) {
                    form.caption(vehicleClass(deployVehicle) == 'A'
                            ? "当前部署点不是飞机点位" : "当前部署点不提供载具刷新", Ui.TEXT_WARN);
                }
            }
            boolean siteFits = here == null || deployVehicle.isEmpty()
                    || here.allows(vehicleClass(deployVehicle));
            // The same rule the in-match screen uses. Both screens computing this separately is what
            // let this one omit the cooldown and the hull cap, offering a button the server refused.
            boolean canDeploy = VehicleDeploy.deployable(running, !deploySpawn.isEmpty(), siteFits,
                    !deployKit.isEmpty(), !deployVehicle.isEmpty() && !ready);
            primary(form, "部署", canDeploy, () -> {
                JsonObject request = Client.request("deploy", selected);
                request.addProperty("kit", deployKit);
                request.addProperty("spawn", deploySpawn);
                request.addProperty("vehicle", deployVehicle);
                Client.send(request);
            });
            row(form, "重新部署", "计一次阵亡", () -> action("redeploy"));
        } else {
            form.paragraph(me() == null ? "加入房间并选择阵营后，这里会出现配装与出生点。"
                    : !running ? "对局尚未开始，开始后即可部署。" : "请先选择阵营。", Ui.TEXT_FAINT);
        }

        form.section("地图");
        form.fact("当前", draft.name + " [" + draft.id + "]", Ui.TEXT);
        // Surface the launch-ability of the map's own whitelist while the operator is looking at it.
        int airPoints = (int) draft.spawns.stream().filter(s -> s.allows('A')).count();
        int groundPoints = (int) draft.spawns.stream().filter(s -> s.allows('G')).count();
        long airVehicles = draft.vehicles.stream().filter(id -> vehicleClass(id) == 'A').count();
        long groundVehicles = draft.vehicles.size() - airVehicles;
        form.fact("载具白名单", draft.vehicles.size() + " 种（地面 " + groundVehicles
                + " · 空中 " + airVehicles + "）", Ui.TEXT_DIM);
        form.fact("可刷新点位", "地面 " + groundPoints + " · 飞机 " + airPoints,
                airVehicles > 0 && airPoints == 0 ? Ui.TEXT_WARN : Ui.TEXT_DIM);
        if (airVehicles > 0 && airPoints == 0) {
            form.paragraph("已授权航空器但没有任何飞机点位，这些载具在部署界面上不会出现。"
                    + "请到「据点/出生」把一个出生点的「航空器」打开。", Ui.TEXT_WARN);
        }
        if (groundVehicles > 0 && groundPoints == 0) {
            form.paragraph("已授权地面载具但没有提供载具刷新的出生点。", Ui.TEXT_WARN);
        }
        row(form, "上一张", "", "", "", true, () -> cycleMap(-1));
        row(form, "下一张", "", "", "", true, () -> cycleMap(1));
        if (admin()) {
            row(form, "新建草稿", "以当前位置", () -> discardThen(()->{
                fresh();
                center();
                tab = 1;
                rebuild();
                say("新草稿已生成，请调整边界、据点与出生点");
            }));
            row(form, "重新载入", "丢弃改动", () -> discardThen(()->{pendingLoad=true;action("state");}));
            if (!flag(state, "roomExists")) {
                row(form, "删除地图", "保留删除备份", () -> discardThen(()->{
                    minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
                        minecraft.setScreen(this);
                        if(yes){action("delete");selected="";pendingLoad=true;}
                    },net.minecraft.network.chat.Component.literal("删除这张地图配置？"),
                      net.minecraft.network.chat.Component.literal("此操作不会删除世界地形，服务器会保留配置删除备份。")));
                }));
            }
        }
    }

    private void join(int team) {
        JsonObject request = Client.request("selectTeam", selected);
        request.addProperty("team", team);
        Client.send(request);
    }

    private static String cycle(List<String> list, String value) {
        if (list.isEmpty()) return value;
        return list.get(Math.floorMod(list.indexOf(value) + 1, list.size()));
    }

    // ------------------------------------------------------------------ canvas geometry

    private Layout.Rect canvasBody() {
        return Ui.headerBody(canvasPanel);
    }

    /** Projection uses the canvas body, so a click maps to the pixel the operator actually sees. */
    private int px(double worldX) {
        return map.sx(canvasBody(), worldX);
    }

    private int pz(double worldZ) {
        return map.sz(canvasBody(), worldZ);
    }

    private double wx(double screenX) {
        return map.worldX(canvasBody(), screenX);
    }

    private double wz(double screenY) {
        return map.worldZ(canvasBody(), screenY);
    }

    private boolean inCanvas(double x, double y) {
        return tab == 1 && canvasBody().inside(x, y);
    }

    /** Blocks per pixel; reported so the operator can tell when the view is too coarse to edit. */
    private double blocksPerPixel() {
        return map.blocksPerPixel(canvasBody());
    }

    // ------------------------------------------------------------------ tab 1: boundary

    /** The polygon currently being edited: whole-map boundary, one sector, or one team's activity zone. */
    private List<Model.Vec> polygon() {
        if (zone < 0) return draft.boundary;
        if (zone >= SECTOR_BASE) {
            int index = zone - SECTOR_BASE;
            return draft.zones.stream().filter(z -> z.team == index).findFirst().orElseGet(() -> {
                Model.Zone added = new Model.Zone();
                added.team = index;
                draft.zones.add(added);
                return added;
            }).boundary;
        }
        int index = zone;
        return draft.sectors.stream().filter(s -> s.index == index).findFirst().orElseGet(() -> {
            Model.Sector sector = new Model.Sector();
            sector.index = index;
            draft.sectors.add(sector);
            return sector;
        }).boundary;
    }

    /**
     * Canvas edit targets, in cycle order: the map boundary, the eight stage boundaries, then the two
     * activity zones.
     *
     * <p>Stages are encoded as themselves and zones are offset past {@link #SECTOR_BASE}, rather than
     * adding a second selector: the operator draws polygons one at a time on one canvas, and a single
     * cycling button keeps that as one control instead of two that can disagree.
     */
    private static final int SECTOR_BASE = 16;
    private static final int ZONE_BLUE = SECTOR_BASE;
    private static final int ZONE_RED = SECTOR_BASE + 1;

    private void boundary() {
        form.section("地图信息");
        field(form, "地图 ID（小写英文/数字/下划线）", draft.id, 32, value -> {
            Model.require(value.matches("[a-z0-9_-]{0,32}"), "ID 仅允许小写英文、数字、下划线");
            draft.id = value;
        });
        field(form, "地图名称", draft.name, 48, value -> draft.name = value);

        Layout.Rect[] modeRow = form.pair(Layout.ROW);
        place(form, modeRow[0], "模式", draft.mode.equals("CONQUEST") ? "征服" : "突破",
                "", "", true, () -> {
                    draft.mode = draft.mode.equals("CONQUEST") ? "BREAKTHROUGH" : "CONQUEST";
                    rebuild();
                });
        place(form, modeRow[1], "编辑", zoneLabel(),
                "", "", true, () -> {
                    // -1 -> stages 0..7 -> blue zone -> red zone -> -1
                    zone = zone >= ZONE_RED ? -1 : zone + 1;
                    if (zone < 0) zone = -1;
                    rebuild();
                });
        if (zone >= SECTOR_BASE) {
            form.caption("正在编辑" + (zone == ZONE_BLUE ? "蓝方" : "红方")
                    + "活动区域：画完即为该方的硬边界，出界按离开战区处理", Ui.TEXT_WARN);
            form.caption("活动区域仅用于征服；突破由阶段边界推进", Ui.TEXT_FAINT);
        }

        form.section("画边界");
        form.caption("左键加点 · Shift拖顶点 · 右键撤销 · 中键平移 · 滚轮缩放", Ui.TEXT_FAINT);
        form.caption("当前顶点 " + polygon().size() + " / 64", Ui.TEXT_DIM);
        Layout.Rect[] editRow = form.pair(Layout.ROW);
        place(form, editRow[0], "撤销一点", "", "", "", !polygon().isEmpty(), () -> {
            List<Model.Vec> points = polygon();
            if (!points.isEmpty()) points.removeLast();
            rebuild();
        });
        place(form, editRow[1], "清空", "", "", "", !polygon().isEmpty(), () -> {
            polygon().clear();
            rebuild();
        });

        form.section("视图");
        Layout.Rect[] zoomRow = form.split(Layout.ROW, 3);
        place(form, zoomRow[0], "缩小", "", "", "", map.span < 1_000_000, () -> {
            map.span = Math.min(1_000_000, map.span * 1.5);
            rebuild();
        });
        place(form, zoomRow[1], "放大", "", "", "", map.span > 16, () -> {
            map.span = Math.max(16, map.span / 1.5);
            rebuild();
        });
        place(form, zoomRow[2], "复位", "", "", "", true, () -> {
            center();
            rebuild();
        });
        // Crisp editing: one block per pixel means the drawn polygon cannot be finer than the grid
        // the operator is aiming at, so the line they draw is the line that gets saved.
        row(form, "1 格 / 像素", "最清晰", () -> {
            map.span = Math.max(16, canvasBody().w());
            if (minecraft.player != null) {
                map.cx = minecraft.player.getX();
                map.cz = minecraft.player.getZ();
            }
            rebuild();
        });
        row(form, "定位到脚下", "", () -> {
            if (minecraft.player != null) {
                map.cx = minecraft.player.getX();
                map.cz = minecraft.player.getZ();
            }
            rebuild();
        });
        form.caption(String.format(Locale.ROOT, "视野 %.0f 格 · 每像素 %.2f 格", map.span, blocksPerPixel()),
                blocksPerPixel() > 1.0 ? Ui.TEXT_WARN : Ui.TEXT_OK);
    }

    // ------------------------------------------------------------------ tab 2: points / spawns

    private void points() {
        form.section("地图");
        // The map is the primary input here: a coordinate box asks the operator to know where a flag
        // goes, a click asks them to point at it. Both stay available for fine adjustment.
        form.caption(placing == null
                ? "左键选择标记；移动需先选择放置模式"
                : (placing.equals("point") ? "左键点地图 = 放置据点" : "左键点地图 = 放置出生点"), Ui.TEXT_WARN);
        form.caption("右键点地图 = 选中最近的标记 · 滚轮缩放 · 中键平移", Ui.TEXT_FAINT);
        row(form,"放置当前据点",placing!=null&&placing.equals("point")?"进行中":"点击启用",()->{placing="point";rebuild();});
        row(form,"放置当前出生点",placing!=null&&placing.equals("spawn")?"进行中":"点击启用",()->{placing="spawn";rebuild();});
        if(placing!=null)row(form,"取消放置","Esc",()->{placing=null;rebuild();});

        form.section("据点 " + (draft.points.isEmpty() ? "0 / 0"
                : (Math.min(pointIndex, draft.points.size() - 1) + 1) + " / " + draft.points.size()));
        Layout.Rect[] nav = form.split(Layout.ROW, 4);
        place(form, nav[0], "<", "", "", "", !draft.points.isEmpty(), () -> {
            pointIndex = Math.floorMod(pointIndex - 1, draft.points.size());
            rebuild();
        });
        place(form, nav[1], ">", "", "", "", !draft.points.isEmpty(), () -> {
            pointIndex = (pointIndex + 1) % draft.points.size();
            rebuild();
        });
        place(form, nav[2], "新增", "", "", "", draft.points.size() < 24, () -> {
            Model.Point point = new Model.Point();
            point.id = nextPointId();
            point.x = minecraft.player == null ? map.cx : minecraft.player.getX();
            point.y = minecraft.player == null ? 64 : minecraft.player.getY();
            point.z = minecraft.player == null ? map.cz : minecraft.player.getZ();
            draft.points.add(point);
            pointIndex = draft.points.size() - 1;
            placing = "point";
            rebuild();
            say("新据点已生成，在地图上左键点击即可摆放");
        });
        place(form, nav[3], "删除", "", "", "", !draft.points.isEmpty(), () -> {
            draft.points.remove(Math.min(pointIndex, draft.points.size() - 1));
            pointIndex = 0;
            rebuild();
        });
        if (!draft.points.isEmpty()) {
            Model.Point point = draft.points.get(Math.min(pointIndex, draft.points.size() - 1));
            field(form, "据点名称", point.id, 32, value -> point.id = value);
            decimal("据点 X", point.x, value -> point.x = value);
            decimal("据点 Z", point.z, value -> point.z = value);
            decimal("据点 Y", point.y, value -> point.y = value);
            decimal("占领半径", point.radius, value -> point.radius = value);
            decimal("总高度（Y 上下各一半）", point.height, value -> point.height = value);
            number("所属阶段", point.sector, value -> point.sector = value, 0, 15);
            row(form, "移到脚下", "", () -> {
                if (minecraft.player != null) {
                    point.x = minecraft.player.getX();
                    point.y = minecraft.player.getY();
                    point.z = minecraft.player.getZ();
                }
                rebuild();
            });
        }

        form.section("出生点 " + (draft.spawns.isEmpty() ? "0 / 0" : (Math.min(spawnIndex, draft.spawns.size() - 1) + 1) + " / " + draft.spawns.size()));
        Layout.Rect[] snav = form.split(Layout.ROW, 4);
        place(form, snav[0], "<", "", "", "", !draft.spawns.isEmpty(), () -> {
            spawnIndex = Math.floorMod(spawnIndex - 1, draft.spawns.size());
            rebuild();
        });
        place(form, snav[1], ">", "", "", "", !draft.spawns.isEmpty(), () -> {
            spawnIndex = (spawnIndex + 1) % draft.spawns.size();
            rebuild();
        });
        place(form, snav[2], "新增", "", "", "", draft.spawns.size() < 32, () -> {
            Model.Spawn spawn = new Model.Spawn();
            spawn.id = nextSpawnId();
            spawn.x = minecraft.player == null ? map.cx : minecraft.player.getX();
            spawn.y = minecraft.player == null ? 64 : minecraft.player.getY();
            spawn.z = minecraft.player == null ? map.cz : minecraft.player.getZ();
            draft.spawns.add(spawn);
            spawnIndex = draft.spawns.size() - 1;
            placing = "spawn";
            rebuild();
            say("新出生点已生成，在地图上左键点击即可摆放");
        });
        place(form, snav[3], "删除", "", "", "", !draft.spawns.isEmpty(), () -> {
            draft.spawns.remove(Math.min(spawnIndex, draft.spawns.size() - 1));
            spawnIndex = 0;
            rebuild();
        });
        if (!draft.spawns.isEmpty()) {
            Model.Spawn spawn = draft.spawns.get(Math.min(spawnIndex, draft.spawns.size() - 1));
            field(form, "出生点名称", spawn.id, 32, value -> spawn.id = value);
            row(form, "阵营", spawn.team == 0 ? "蓝方" : "红方", () -> {
                spawn.team = spawn.team == 0 ? 1 : 0;
                rebuild();
            });
            // What kind of site this is. Distinct from the vehicle switches below: the kind is what the
            // deploy list groups by, and picking 机场 here is the one-click way to get a site that
            // launches aircraft and nothing else.
            row(form, "类型", kindLabel(spawn.kind()), () -> {
                spawn.kind = nextKind(spawn.kind());
                // Choosing a kind sets the vehicle mask to match, so the two cannot disagree. A site
                // labelled an airfield that silently refuses aircraft would be worse than no label.
                if (Model.Spawn.AIR.equals(spawn.kind)) spawn.allows = "A";
                else if (Model.Spawn.BASE.equals(spawn.kind) && "A".equals(spawn.allows)) spawn.allows = "G";
                rebuild();
            });
            if (spawn.air()) {
                form.caption("机场：玩家在此连人带机起飞，仅提供航空器", Ui.TEXT_WARN);
            }
            decimal("出生 X", spawn.x, value -> spawn.x = value);
            decimal("出生 Z", spawn.z, value -> spawn.z = value);
            decimal("出生 Y", spawn.y, value -> spawn.y = value);
            number("所属阶段", spawn.sector, value -> spawn.sector = value, 0, 15);
            // What this point is allowed to launch. Aircraft need a runway point of their own, which
            // is the whole reason this switch exists: an air base is somewhere you take off from, not
            // somewhere a tank appears.
            row(form, "地面载具", spawn.allows('G') ? "可刷新" : "不可", () -> {
                spawn.allows = toggle(spawn.allows, 'G');
                rebuild();
            });
            row(form, "航空器", spawn.allows('A') ? "飞机点位" : "不可", () -> {
                spawn.allows = toggle(spawn.allows, 'A');
                rebuild();
            });
            form.caption(describeAllows(spawn),
                    spawn.allows('A') ? Ui.TEXT_WARN : Ui.TEXT_FAINT);
            form.paragraph(slotHint(spawn), Ui.TEXT_FAINT);
            row(form, "移到脚下并面向", "", () -> {
                if (minecraft.player != null) {
                    spawn.x = minecraft.player.getX();
                    spawn.y = minecraft.player.getY();
                    spawn.z = minecraft.player.getZ();
                    spawn.yaw = minecraft.player.getYRot();
                }
                rebuild();
            });
        }
        supplies();
    }

    /** Supply pad editor: where they are, who they serve, and what they restore. */
    private void supplies() {
        if (draft.supplies == null) draft.supplies = new ArrayList<>();
        form.section("补给点 " + (draft.supplies.isEmpty() ? "0 / 0"
                : (Math.min(supplyIndex, draft.supplies.size() - 1) + 1) + " / " + draft.supplies.size()));
        form.caption("补给点会持续为范围内的本方人员与载具补充弹药、电量或血量", Ui.TEXT_FAINT);
        row(form, "放置当前补给点",
                placing != null && placing.equals("supply") ? "进行中" : "点击启用", () -> {
                    placing = "supply";
                    rebuild();
                });
        Layout.Rect[] snav = form.split(Layout.ROW, 4);
        place(form, snav[0], "<", "", "", "", !draft.supplies.isEmpty(), () -> {
            supplyIndex = Math.floorMod(supplyIndex - 1, draft.supplies.size());
            rebuild();
        });
        place(form, snav[1], ">", "", "", "", !draft.supplies.isEmpty(), () -> {
            supplyIndex = (supplyIndex + 1) % draft.supplies.size();
            rebuild();
        });
        place(form, snav[2], "新增", "", "", "", draft.supplies.size() < 32, () -> {
            String id = nextSupplyId();
            double x = minecraft.player == null ? map.cx : minecraft.player.getX();
            double y = minecraft.player == null ? 64 : minecraft.player.getY();
            double z = minecraft.player == null ? map.cz : minecraft.player.getZ();
            // Default to servicing both sides with ammo and energy: a pad that does nothing is the one
            // state validation rejects, so the default must be a working pad.
            draft.supplies.add(new VehicleSupply.Supply(id, -1, x, y, z, 16, true, true, false));
            supplyIndex = draft.supplies.size() - 1;
            placing = "supply";
            rebuild();
            say("新补给点已生成，在地图上左键点击即可摆放");
        });
        place(form, snav[3], "删除", "", "", "", !draft.supplies.isEmpty(), () -> {
            draft.supplies.remove(Math.min(supplyIndex, draft.supplies.size() - 1));
            supplyIndex = 0;
            rebuild();
        });
        if (draft.supplies.isEmpty()) {
            form.caption("没有补给点。载具部署时仍会按载具类型自动配好弹药与电量。", Ui.TEXT_FAINT);
            return;
        }
        int index = Math.min(supplyIndex, draft.supplies.size() - 1);
        VehicleSupply.Supply supply = draft.supplies.get(index);
        final int at = index;
        field(form, "补给点名称", supply.id(), 32, value -> setSupply(at, supply.withId(value)));
        row(form, "阵营", supply.team() < 0 ? "双方" : supply.team() == 0 ? "仅蓝方" : "仅红方",
                () -> setSupply(at, supply.withTeam(supply.team() < 0 ? 0 : supply.team() == 0 ? 1 : -1)));
        decimal("补给点 X", supply.x(), value -> setSupply(at, supply.withX(value)));
        decimal("补给点 Z", supply.z(), value -> setSupply(at, supply.withZ(value)));
        decimal("补给点 Y", supply.y(), value -> setSupply(at, supply.withY(value)));
        number("补给半径", (int) supply.radius(),
                value -> setSupply(at, supply.withRadius(value)), 2, 128);
        row(form, "补充弹药", supply.ammo() ? "开" : "关",
                () -> setSupply(at, supply.withAmmo(!supply.ammo())));
        row(form, "补充电量", supply.energy() ? "开" : "关",
                () -> setSupply(at, supply.withEnergy(!supply.energy())));
        row(form, "恢复血量", supply.health() ? "开" : "关",
                () -> setSupply(at, supply.withHealth(!supply.health())));
        form.caption(describeSupply(supply),
                supply.restoresAnything() ? Ui.TEXT_FAINT : Ui.TEXT_WARN);
        row(form, "移到脚下", "", () -> {
            if (minecraft.player != null) {
                setSupply(at, supply.withX(minecraft.player.getX())
                        .withY(minecraft.player.getY())
                        .withZ(minecraft.player.getZ()));
            }
            rebuild();
        });
    }

    private void setSupply(int index, VehicleSupply.Supply supply) {
        if (index < 0 || index >= draft.supplies.size()) return;
        draft.supplies.set(index, supply);
        rebuild();
    }

    private String describeSupply(VehicleSupply.Supply supply) {
        if (!supply.restoresAnything()) return "该补给点没有开启任何项目，保存时会被校验拒绝";
        List<String> parts = new ArrayList<>();
        if (supply.ammo()) parts.add("弹药");
        if (supply.energy()) parts.add("电量");
        if (supply.health()) parts.add("血量");
        return "作用：" + String.join(" / ", parts) + " · 半径 " + (int) supply.radius() + " 格";
    }

    /** Next free supply pad id. */
    private String nextSupplyId() {
        for (int i = 1; i <= 32; i++) {
            String id = "补给" + i;
            boolean taken = draft.supplies.stream().anyMatch(s -> id.equals(s.id()));
            if (!taken) return id;
        }
        return "补给";
    }

    /** Adds or removes a class character from a spawn's launch mask, never leaving it empty. */
    /** Operator-facing name for a spawn kind. */
    private static String kindLabel(String kind) {
        if (Model.Spawn.AIR.equals(kind)) return "机场（仅航空器）";
        if (Model.Spawn.POINT.equals(kind)) return "前线据点";
        return "总部";
    }

    /** Headquarters -> forward post -> airfield -> headquarters. */
    private static String nextKind(String kind) {
        if (Model.Spawn.BASE.equals(kind)) return Model.Spawn.POINT;
        if (Model.Spawn.POINT.equals(kind)) return Model.Spawn.AIR;
        return Model.Spawn.BASE;
    }

    private static String toggle(String allows, char vehicleClass) {        String current = allows == null || allows.isEmpty() ? "G" : allows.toUpperCase(Locale.ROOT);
        String cleaned = current.replace("N", "");
        boolean has = cleaned.indexOf(vehicleClass) >= 0;
        String next = has ? cleaned.replace(String.valueOf(vehicleClass), "") : cleaned + vehicleClass;
        return next.isEmpty() ? "N" : next;
    }

    /** One-line summary of what a point launches, in the operator's words. */
    private static String describeAllows(Model.Spawn spawn) {
        boolean ground = spawn.allows('G');
        boolean air = spawn.allows('A');
        if (ground && air) return "地面载具 + 航空器";
        if (air) return "仅航空器（飞机点位）";
        if (ground) return "仅地面载具";
        return "仅步兵";
    }

    /** Warns when an air point looks like it will not actually work. */
    private static String slotHint(Model.Spawn spawn) {
        if (spawn.allows('A')) {
            return "飞机点位请留出足够长的跑道与净空高度，刷新时飞机出现在该点正上方";
        }
        if (!spawn.allows('G') && !spawn.allows('A')) {
            return "纯步兵点：玩家在此部署时不会获得载具";
        }
        return "地面载具出现在该点原位，玩家直接坐进驾驶位";
    }

    private String nextPointId() {
        for (char c = 'A'; c <= 'Z'; c++) {
            String id = String.valueOf(c);
            if (draft.points.stream().noneMatch(p -> p.id.equals(id))) return id;
        }
        return "P" + (draft.points.size() + 1);
    }

    private String nextSpawnId() {
        for (int i = 1; i <= 64; i++) {
            String id = "出生点" + i;
            if (draft.spawns.stream().noneMatch(s -> s.id.equals(id))) return id;
        }
        return "出生点" + (draft.spawns.size() + 1);
    }

    // ------------------------------------------------------------------ tab 3: rules

    private void rules() {
        form.section("计分与兵力");
        number("起始兵力", draft.tickets, value -> draft.tickets = value, 1, 100000);
        number("击杀个人分", draft.killScore, value -> draft.killScore = value, 0, 10000);
        number("占领个人分", draft.captureScore, value -> draft.captureScore = value, 0, 10000);
        number("阵亡扣兵力", draft.deathCost, value -> draft.deathCost = value, 0, 100);
        number("据点优势每秒扣票", draft.bleed, value -> draft.bleed = value, 0, 100);
        number("单人占领秒数", draft.captureSeconds, value -> draft.captureSeconds = value, 1, 600);
        number("突破阶段奖励兵力", draft.sectorBonus, value -> draft.sectorBonus = value, 0, 10000);

        form.section("节奏");
        number("重生等待秒数", draft.respawnSeconds, value -> draft.respawnSeconds = value, 0, 120);
        number("越界宽限秒数", draft.outsideSeconds, value -> draft.outsideSeconds = value, 1, 120);
        number("局时（分钟）", draft.roundMinutes, value -> draft.roundMinutes = value, 1, 240);
        number("立即退出边界距离（格）", draft.exitMargin, value -> draft.exitMargin = value, 0, 512);

        form.section("载具与高度");
        // Vehicles are rationed by cooldown and the match-wide cap. There is deliberately no "score
        // price" input: spending score on a hull made the scoreboard read like a wallet, and the
        // cooldown already paces vehicle use.
        number("载具冷却（秒）", draft.vehicleCooldown, value -> draft.vehicleCooldown = value, 0, 600);
        // Mark lifetime: long enough to act on, short enough that the marker is still true when read.
        number("敌军标记持续秒数", draft.markSeconds, value -> draft.markSeconds = value, 1, 60);
        // Award tuning. Each is score-only: none of them spends anything, they just make the score
        // reflect what actually happened in the fight.
        number("\u6807\u8bb0\u5f97\u5206", draft.markScore, value -> draft.markScore = value, 0, 1000);
        number("\u52a9\u653b\u5f97\u5206", draft.assistScore, value -> draft.assistScore = value, 0, 1000);
        number("\u7206\u5934\u52a0\u6210", draft.headshotBonus, value -> draft.headshotBonus = value, 0, 1000);
        number("\u8fde\u6740\u52a0\u6210", draft.comboBonus, value -> draft.comboBonus = value, 0, 1000);
        number("\u590d\u4ec7\u52a0\u6210", draft.revengeBonus, value -> draft.revengeBonus = value, 0, 1000);
        number("\u8fde\u6740\u7a97\u53e3\u79d2\u6570", draft.comboSeconds, value -> draft.comboSeconds = value, 1, 30);
        number("\u52a9\u653b\u7a97\u53e3\u79d2\u6570", draft.assistSeconds, value -> draft.assistSeconds = value, 1, 60);
        number("\u5012\u5730\u51fa\u8840\u79d2\u6570", draft.downSeconds, value -> draft.downSeconds = value, 3, 120);
        number("\u6551\u63f4\u6240\u9700\u79d2\u6570", draft.reviveSeconds, value -> draft.reviveSeconds = value, 1, 30);
        number("\u6551\u63f4\u540e\u751f\u547d\u503c", draft.reviveHealth, value -> draft.reviveHealth = value, 0, 1000);
        number("蓝方载具名额（0=禁用）", draft.vehicleLimitFor(0), value -> draft.blueVehicleLimit = value, 0, 32);
        number("红方载具名额（0=禁用）", draft.vehicleLimitFor(1), value -> draft.redVehicleLimit = value, 0, 32);
        number("战区最低 Y", draft.minY, value -> draft.minY = value, -2048, 2048);
        number("战区最高 Y", draft.maxY, value -> draft.maxY = value, -2048, 2048);

        form.section("人机");
        row(form, "人机模式", draft.botMode ? "开" : "关", () -> {
            draft.botMode = !draft.botMode;
            rebuild();
        });
        number("每队人机数量", draft.botsPerTeam, value -> draft.botsPerTeam = value, 1, 32);

        form.section("开关");
        row(form, "友军伤害", draft.friendlyFire ? "开" : "关", () -> {
            draft.friendlyFire = !draft.friendlyFire;
            rebuild();
        });
        row(form, "隐藏敌方名牌", draft.hideEnemyNametags ? "开" : "关", () -> {
            draft.hideEnemyNametags = !draft.hideEnemyNametags;
            rebuild();
        });
        row(form, "队友地图标记", draft.glowTeammates ? "开" : "关", () -> {
            draft.glowTeammates = !draft.glowTeammates;
            rebuild();
        });
        row(form, "敌人地图标记", draft.glowEnemies ? "开" : "关", () -> {
            draft.glowEnemies = !draft.glowEnemies;
            rebuild();
        });
        field(form, "维度 ID", draft.dimension, 96, value -> draft.dimension = value);
    }

    // ------------------------------------------------------------------ tab 4: kits

    private void kits() {
        form.section("兵种 " + (draft.kits.isEmpty() ? "0 / 0"
                : (Math.min(kitIndex, draft.kits.size() - 1) + 1) + " / " + draft.kits.size()));
        Layout.Rect[] nav = form.split(Layout.ROW, 4);
        place(form, nav[0], "<", "", "", "", !draft.kits.isEmpty(), () -> {
            kitIndex = Math.floorMod(kitIndex - 1, draft.kits.size());
            rebuild();
        });
        place(form, nav[1], ">", "", "", "", !draft.kits.isEmpty(), () -> {
            kitIndex = (kitIndex + 1) % draft.kits.size();
            rebuild();
        });
        place(form, nav[2], "新增", "", "", "", draft.kits.size() < 16, () -> {
            Model.Kit kit = new Model.Kit();
            kit.id = "配装" + (draft.kits.size() + 1);
            draft.kits.add(kit);
            kitIndex = draft.kits.size() - 1;
            rebuild();
        });
        place(form, nav[3], "删除", "", "", "", !draft.kits.isEmpty(), () -> {
            draft.kits.remove(Math.min(kitIndex, draft.kits.size() - 1));
            kitIndex = 0;
            rebuild();
        });
        if (draft.kits.isEmpty()) {
            form.paragraph("至少需要一个兵种，否则玩家无法部署。", Ui.TEXT_WARN);
            return;
        }
        Model.Kit kit = draft.kits.get(Math.min(kitIndex, draft.kits.size() - 1));
        field(form, "兵种名称", kit.id, 32, value -> kit.id = value);
        number("每把枪备用弹药组数", kit.ammoStacks, value -> kit.ammoStacks = value, 0, 12);

        form.section("武器库");
        long mains = kit.options(true).stream().filter(id -> !id.isEmpty()).count();
        long subs = kit.options(false).stream().filter(id -> !id.isEmpty()).count();
        row(form, "主武器库", mains + " 把", "gun", kit.primary, () -> {
            tab = 5;
            rebuild();
            fetchCatalog();
        });
        row(form, "副武器库", subs + " 把", "gun", kit.secondary, () -> {
            tab = 5;
            rebuild();
            fetchCatalog();
        });
        row(form, "移除主武器选项", "", "", "", kit.options(true).stream().anyMatch(id -> !id.isEmpty()),
                () -> removeOption(kit, true));
        row(form, "移除副武器选项", "", "", "", kit.options(false).stream().anyMatch(id -> !id.isEmpty()),
                () -> removeOption(kit, false));

        gearSection(kit);
        form.release();
    }

    /**
     * The kit's non-weapon loadout: arbitrary items, armour, and accessories.
     *
     * <p>Lives on the kit page rather than a separate tab because it is a property of the kit — an
     * operator editing "medic" expects to see the medic's bandages in the same place as its rifle, and
     * splitting them across tabs is how a loadout ends up half-configured.
     */
    private void gearSection(Model.Kit kit) {
        KitGear gear = kit.gear();
        form.section("个人物品 · " + gear.items.size() + " 项");

        int shown = Math.min(gear.items.size(), 6);
        for (int i = 0; i < shown; i++) {
            final int index = i;
            KitGear.Entry entry = gear.items.get(i);
            Layout.Rect row = form.take(Layout.ROW);
            Layout.Rect[] cells = Layout.columns(row, 3, 3);
            place(form, cells[0], KiItems_name(entry.id),
                    "×" + entry.count, "item", entry.id, true, () -> {
                        // Cycle the count in place: the common edit is "more of this", not "replace this".
                        entry.count = entry.count >= KitGear.MAX_COUNT ? 1 : entry.count + 1;
                        rebuild();
                    });
            place(form, cells[1], "−", "", "", "", entry.count > 1, () -> {
                entry.count = Math.max(1, entry.count - 1);
                rebuild();
            });
            place(form, cells[2], "移除", "", "", "", true, () -> {
                gear.items.remove(index);
                rebuild();
            });
        }
        if (gear.items.isEmpty()) {
            form.caption("还没有发放任何物品。用下面的「添加物品」从全部物品里挑选。", Ui.TEXT_FAINT);
        }
        Layout.Rect[] add = form.pair(Layout.ROW);
        place(form, add[0], "添加物品", "任意种类", "item", "", true, () -> pickItem(id -> {
            KitGear.Entry entry = new KitGear.Entry(id, 1);
            gear.items.add(entry);
            say("已添加 " + KiItems_name(id) + "，记得保存地图");
            rebuild();
        }));
        place(form, add[1], "清空物品", "", "", "", !gear.items.isEmpty(), () -> {
            gear.items.clear();
            rebuild();
        });

        form.section("盔甲");
        for (String slot : KitGear.ARMOUR_SLOTS) {
            String id = gear.armour.getOrDefault(slot, "");
            String label = switch (slot) {
                case "HEAD" -> "头盔";
                case "CHEST" -> "胸甲";
                case "LEGS" -> "护腿";
                case "FEET" -> "靴子";
                default -> slot;
            };
            row(form, label, id.isEmpty() ? "未配置" : KiItems_name(id), "item", id, () -> {
                pickItem(chosen -> {
                    gear.armour.put(slot, chosen);
                    say(label + " 已设为 " + KiItems_name(chosen) + "，记得保存地图");
                    rebuild();
                });
            });
            if (!id.isEmpty()) {
                row(form, "清除" + label, KiItems_name(id), () -> {
                    gear.armour.remove(slot);
                    rebuild();
                });
            }
        }

        form.section("配饰 · " + gear.accessories.size() + " 件");
        boolean curios = CuriosSupport.available();
        if (!curios) {
            form.caption("未检测到配饰模组（Curios），配饰配置会保留但不会生效。", Ui.TEXT_WARN);
        }
        List<String> slots = CuriosSupport.slotIds();
        if (slots.isEmpty()) {
            form.caption("没有可用的配饰槽位。", Ui.TEXT_FAINT);
        }
        int shownAcc = Math.min(slots.size(), 6);
        for (int i = 0; i < shownAcc; i++) {
            String slot = slots.get(i);
            String id = gear.accessories.getOrDefault(slot, "");
            row(form, CuriosSupport.slotName(slot), id.isEmpty() ? "未配置" : KiItems_name(id),
                    "item", id, () -> {
                        pickItem(chosen -> {
                            gear.accessories.put(slot, chosen);
                            say(CuriosSupport.slotName(slot) + " 已设为 " + KiItems_name(chosen) + "，记得保存地图");
                            rebuild();
                        });
                    });
            if (!id.isEmpty()) {
                row(form, "清除 " + CuriosSupport.slotName(slot), "", "", "", true, () -> {
                    gear.accessories.remove(slot);
                    rebuild();
                });
            }
        }
        if (slots.size() > shownAcc) {
            form.caption("还有 " + (slots.size() - shownAcc) + " 个槽位未显示（配饰模组提供了很多槽位）。",
                    Ui.TEXT_FAINT);
        }
        form.paragraph("物品、盔甲与配饰合计最多 " + KitGear.MAX_ENTRIES + " 项。服务端只发放这里授权的物品。",
                Ui.TEXT_FAINT);
    }

    /** Item display name through the shared resolver, so a missing item shows its raw id here too. */
    private static String KiItems_name(String id) {
        return id == null || id.isEmpty() ? "未配置" : KitItems.name(id);
    }

    /**
     * Opens the item picker.
     *
     * <p>The catalogue is built on the client from its own synced item registry, so there is no request to
     * make and no payload to wait for. The earlier version fetched pages from the server and then relied on
     * a callback that only fired when this screen was the open one — which it never is while the picker is
     * showing, so the list stayed empty.
     */
    private void pickItem(java.util.function.Consumer<String> chosen) {
        minecraft.setScreen(new ItemPickerScreen(this, id -> {
            if (id == null || id.isEmpty()) return;
            chosen.accept(id);
            minecraft.setScreen(this);
        }));
    }

    private void removeOption(Model.Kit kit, boolean main) {
        List<ChoiceScreen.Choice> choices = kit.options(main).stream()
                .filter(id -> !id.isEmpty())
                .map(id -> new ChoiceScreen.Choice(id, name(id), "gun"))
                .toList();
        if (choices.isEmpty()) {
            warn("这个库还没有任何武器");
            return;
        }
        minecraft.setScreen(new ChoiceScreen(this, "选择要移除的武器", choices, id -> {
            List<String> options = main ? kit.primaryOptions : kit.secondaryOptions;
            options.remove(id);
            if (main && kit.primary.equals(id)) kit.primary = options.isEmpty() ? "" : options.getFirst();
            if (!main && kit.secondary.equals(id)) kit.secondary = options.isEmpty() ? "" : options.getFirst();
            say("已移除选项，记得保存地图");
        }));
    }

    // ------------------------------------------------------------------ tab 5: catalogue

    private void fetchCatalog() {
        JsonObject request = Client.request("catalog", selected);
        request.addProperty("query", query);
        request.addProperty("page", catalogPage);
        Client.send(request);
    }

    private void catalog() {
        field(form, "搜索：名称 / @模组名", query, 96, value -> query = value);
        Layout.Rect[] top = form.pair(Layout.ROW);
        place(form, top[0], "搜索", "", "", "", true, () -> {
            catalogPage = 0;
            catalogLocal = 0;
            fetchCatalog();
        });
        place(form, top[1], "重扫资源库", "", "", "", true, () -> action("scan"));

        JsonArray entries = Client.catalog.has("entries")
                ? Client.catalog.getAsJsonArray("entries") : new JsonArray();
        int visible = Math.max(1, Layout.fitRows(form.area(), 26, 3));
        catalogLocal = Math.min(catalogLocal, Math.max(0, (entries.size() - 1) / visible));
        int from = catalogLocal * visible;
        form.section("资源库 " + Engine.num(Client.catalog, "total", entries.size()) + " 项");
        for (int i = from; i < Math.min(entries.size(), from + visible); i++) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            String id = Engine.str(entry, "id", "");
            String kind = Engine.str(entry, "kind", "");
            row(form, Engine.str(entry, "name", id), id.equals(entryId) ? "●" : "",
                    kind, id, () -> {
                        entryId = id;
                        entryKind = kind;
                        rebuild();
                    });
        }
        Layout.Rect[] nav = form.pair(Layout.ROW);
        place(form, nav[0], "上一页", "", "", "", catalogLocal > 0 || catalogPage > 0, () -> {
            if (catalogLocal > 0) catalogLocal--;
            else if (catalogPage > 0) {
                catalogPage--;
                catalogLocal = Integer.MAX_VALUE;
                fetchCatalog();
            }
            rebuild();
        });
        place(form, nav[1], "下一页", "", "", "", true, () -> {
            if ((catalogLocal + 1) * visible < entries.size()) catalogLocal++;
            else {
                catalogPage++;
                catalogLocal = 0;
                fetchCatalog();
            }
            rebuild();
        });
    }

    /** The catalogue's own actions live in the footer bar, because they act on the selection. */
    private Form barForm;

    private void buildBar() {
        Layout.Rect bar = layout.bar;
        barForm = null;
        if (bar.h() <= 0) return;
        barForm = newForm(bar.inset(2));
        int columns = admin() ? 4 : 2;
        Layout.Rect[] cells = Layout.columns(bar.inset(2), columns, 3);
        place(barForm, cells[0], "关闭", "", "", "", true, this::onClose);
        if (admin()) {
            var saveButton=place(barForm, cells[1], savingId.isEmpty()?"保存地图":"保存中…", "", "", "", savingId.isEmpty(), this::save);
            saveButton.primary=true;
            place(barForm,cells[2],"作战房间","","","",true,()->{
                Model.require(!dirty(),"请先点击保存地图，收到成功回执后再进入作战房间");
                minecraft.setScreen(new BattleScreen());Client.send(Client.request("state",selected));
            });
            place(barForm, cells[columns - 1], "新建", "", "", "", true, () -> discardThen(()->{
                fresh();
                center();
                tab = 1;
                rebuild();
            }));
        }
        barForm.finish();
    }

    // ------------------------------------------------------------------ tab 6: compat

    private void compat() {
        JsonObject info = obj(Client.state, "compat");
        JsonObject hud = obj(Client.state, "hud");
        form.section("群组服兼容");
        if (info == null) {
            form.paragraph("尚未连接服务器，无法读取兼容状态。", Ui.TEXT_WARN);
            return;
        }
        row(form, "侧边栏", zh(Engine.str(info, "sidebar", "AUTO")), () -> compatMode("sidebar", Engine.str(info, "sidebar", "AUTO")));
        row(form, "队伍/名牌", zh(Engine.str(info, "teams", "AUTO")), () -> compatMode("teams", Engine.str(info, "teams", "AUTO")));
        row(form, "小地图", zh(Engine.str(info, "minimap", "AUTO")), () -> compatMode("minimap", Engine.str(info, "minimap", "AUTO")));
        // Position is a separate axis from "should we draw it at all": the minimap can be welcome and still
        // be in the way. Cycling rather than a text field because there are exactly four corners.
        String corner = Briefing.normaliseCorner(Engine.str(info, "minimapCorner", "TOP_LEFT"));
        row(form, "小地图位置", Briefing.minimapCornerName(corner), () -> {
            JsonObject request = Client.request("compatCorner", selected);
            request.addProperty("value", Briefing.nextCorner(corner));
            Client.send(request);
        });
        form.paragraph("自动 = 发现被占用就让出；总是 = 强制接管（会与插件互抢）；从不 = 完全不碰。",
                Ui.TEXT_FAINT);

        form.section("实际情况");
        boolean foreignSidebar = flag(info, "foreignSidebar");
        boolean foreignTeams = flag(info, "foreignTeams");
        form.caption(foreignSidebar ? "侧边栏：已让给 TAB（改由 HUD 显示）" : "侧边栏：由本模组显示",
                foreignSidebar ? Ui.TEXT_WARN : Ui.TEXT_OK);
        form.caption(foreignTeams ? "队伍：已让给 TAB（名牌颜色由 TAB 决定）" : "队伍：由本模组显示",
                foreignTeams ? Ui.TEXT_WARN : Ui.TEXT_OK);
        boolean xaero = flag(info, "xaeroAssumed");
        boolean minimap = hud == null || !hud.has("minimap") || hud.get("minimap").getAsBoolean();
        form.caption(xaero ? (minimap ? "小地图：Xaero 在场但已强制开启（会重叠）" : "小地图：已让给 Xaero")
                        : "小地图：本模组显示",
                xaero && minimap ? Ui.TEXT_WARN : Ui.TEXT_OK);
        // Hiding Xaero during a match is a separate decision from yielding the corner outside one.
        boolean hideXaero = hud != null && hud.has("hideXaero") && hud.get("hideXaero").getAsBoolean();
        form.caption(hideXaero
                        ? "对局中：隐藏 Xaero 小地图（仅显示本模组地图）"
                        : "对局中：Xaero 小地图照常显示（会与本模组重叠）",
                hideXaero ? Ui.TEXT_OK : Ui.TEXT_WARN);
        if (admin()) {
            row(form, "对局隐藏 Xaero", hideXaero ? "已开启" : "已关闭",
                    () -> compatSend("compatMode", "hideXaero=" + (hideXaero ? "false" : "true")));
        }
        form.caption("检测：TaCZ " + (flag(info, "tacz") ? "已装" : "未装")
                + " · 卓越前线 " + (flag(info, "superbwarfare") ? "已装" : "未装")
                + " · 平台 " + Engine.str(info, "serverModName", "?"), Ui.TEXT_FAINT);
        form.caption("配置：" + Engine.str(info, "file", ""), Ui.TEXT_FAINT);

        JsonArray notes = info.has("notes") ? info.getAsJsonArray("notes") : new JsonArray();
        if (!notes.isEmpty()) {
            form.section("已作出的让步");
            for (JsonElement note : notes) form.caption("· " + note.getAsString(), Ui.TEXT_WARN);
        }
        if (admin()) {
            row(form, "重新检测", "恢复自检", () -> compatSend("compatReset", ""));
            row(form, "写入配置文件", "", () -> compatSend("compatSave", ""));
        } else {
            form.caption("只读：仅管理员可修改兼容设置。", Ui.TEXT_FAINT);
        }
    }

    private void compatMode(String key, String current) {
        String next = switch (current) {
            case "AUTO" -> "NEVER";
            case "NEVER" -> "ALWAYS";
            default -> "AUTO";
        };
        compatSend("compatMode", key + "=" + next);
    }

    private void compatSend(String op, String extra) {
        JsonObject request = Client.request(op, "");
        if (!extra.isEmpty()) request.addProperty("extra", extra);
        Client.send(request);
    }

    private static String zh(String mode) {
        return switch (mode) {
            case "ALWAYS" -> "总是";
            case "NEVER" -> "从不";
            default -> "自动";
        };
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (form == null) {
            graphics.drawCenteredString(font, "正在载入地图…", layout.content.cx(), layout.content.cy(),
                    Ui.TEXT_DIM);
            return;
        }
        Ui.header(graphics, font, layout.left, leftTitle());
        Layout.Rect body = form.area();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        form.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        if (tab == 1) canvas(graphics, mouseX, mouseY);
        else panel(graphics, mouseX, mouseY, partialTick);

        Layout.Rect side = sideForm.area();
        graphics.enableScissor(side.x(), side.y(), side.right(), side.bottom());
        sideForm.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        if (barForm != null) barForm.render(graphics, mouseX, mouseY, partialTick);
        // A transport notice outranks the ordinary footer message: if a request could not even be sent,
        // that is the thing the operator needs to see, and it used to be drawn straight over the same
        // two lines with no backing, so the two strings overlapped into an unreadable smear.
        if (!notice.isEmpty()) {
            Layout.Rect stripe = layout.footer.withHeight(Math.min(12, layout.footer.h()));
            if (stripe.h() > 0) {
                graphics.fill(stripe.x(), stripe.y(), stripe.right(), stripe.bottom(),
                        Ui.mix(Ui.TEXT_ERROR, Ui.PANEL_SUNKEN, 0.86));
                graphics.fill(stripe.x(), stripe.y(), stripe.x() + 2, stripe.bottom(), Ui.TEXT_ERROR);
                graphics.drawString(font, Ui.clip(font, notice, stripe.w() - 8),
                        stripe.x() + 5, stripe.y() + 2, Ui.TEXT_ERROR, false);
            }
        }
    }

    private String leftTitle() {
        return switch (tab) {
            case 0 -> "部署";
            case 1 -> "划线工具";
            case 2 -> "据点与出生点";
            case 3 -> "计分与规则";
            case 4 -> "兵种配装";
            case 5 -> "武器 / 载具库";
            default -> "兼容";
        };
    }

    /** Non-canvas tabs get a preview pane on the right: the reference UI's large right panel. */
    private void panel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String title = switch (tab) {
            case 0 -> "部署地图";
            case 2 -> "据点 / 出生点";
            case 3 -> "规则摘要";
            case 4 -> "配装预览";
            case 5 -> "资源预览";
            default -> "兼容报告";
        };
        Layout.Rect body = Ui.header(graphics, font, canvasPanel, title);
        switch (tab) {
            case 0 -> deployMap(graphics);
            case 5 -> catalogPreview(graphics, body);
            case 4 -> kitPreview(graphics, body);
            case 2 -> pointsPreview(graphics, body);
            case 3 -> rulesPreview(graphics, body);
            default -> compatReport(graphics, body);
        }
    }

    /**
     * The deployment page's live map: the same terrain + overlay stack as the editor canvas, but with
     * match ownership and only this player's spawns. Living in the right panel, beside the controls,
     * is what makes them read as one window.
     */
    private void deployMap(GuiGraphics graphics) {
        Layout.Rect body = canvasBody();
        int highlight = -1;
        for (int i = 0; i < draft.spawns.size(); i++) {
            if (!draft.spawns.get(i).id.equals(deploySpawn)) continue;
            highlight = i;
            break;
        }
        map.highlightPoint = -1;
        map.highlightSpawn = highlight;
        map.highlightSector = -1;
        map.showSpawns = true;
        double selfX = minecraft.player == null ? 0 : minecraft.player.getX();
        double selfZ = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        JsonArray position = Client.state.has("position") ? Client.state.getAsJsonArray("position") : null;
        if (position != null && position.size() >= 3) {
            selfX = position.get(0).getAsDouble();
            selfZ = position.get(2).getAsDouble();
        }
        map.draw(graphics, font, body, draft, Client.state, selfTeam(), selfX, selfZ, yaw);
        if (!allowedSpawns().isEmpty()) {
            graphics.drawString(font, "点击地图上的部署点可直接选择",
                    body.x() + 4, body.bottom() - 10, Ui.TEXT_FAINT, false);
        }
    }

    /** Right-pane summary for the compatibility tab: the same facts as the left, laid out for reading. */
    private void compatReport(GuiGraphics graphics, Layout.Rect body) {
        JsonObject info = obj(Client.state, "compat");
        int y = body.y() + 6;
        int line = 12;
        if (info == null) {
            graphics.drawString(font, "尚未连接服务器", body.x() + 6, y, Ui.TEXT_FAINT, false);
            return;
        }
        String[][] rows = {
                {"侧边栏", zh(Engine.str(info, "sidebar", "AUTO"))},
                {"队伍名牌", zh(Engine.str(info, "teams", "AUTO"))},
                {"小地图", zh(Engine.str(info, "minimap", "AUTO"))},
                {"TaCZ", flag(info, "tacz") ? "已安装" : "未安装"},
                {"卓越前线", flag(info, "superbwarfare") ? "已安装" : "未安装"},
                {"平台", Engine.str(info, "serverModName", "?")},
        };
        for (String[] row : rows) {
            if (y + line > body.bottom() - 40) break;
            Ui.fact(graphics, font, body.x() + 6, y, 70, body.w() - 12, row[0], row[1], Ui.TEXT);
            y += line;
        }
        y += 4;
        JsonArray notes = info.has("notes") ? info.getAsJsonArray("notes") : new JsonArray();
        if (!notes.isEmpty() && y + line <= body.bottom()) {
            graphics.drawString(font, "已作出的让步", body.x() + 6, y, Ui.TEXT_DIM, false);
            y += line;
            for (JsonElement note : notes) {
                if (y + line > body.bottom() - 4) break;
                graphics.drawString(font, Ui.clip(font, "· " + note.getAsString(), body.w() - 12),
                        body.x() + 10, y, Ui.TEXT_WARN, false);
                y += line;
            }
        }
    }

    private void rulesPreview(GuiGraphics graphics, Layout.Rect body) {
        int y = body.y() + 4;
        String[][] rows = {
                {"起始兵力", "" + draft.tickets}, {"阵亡扣票", "" + draft.deathCost},
                {"据点每秒扣票", "" + draft.bleed}, {"占领秒数", "" + draft.captureSeconds},
                {"重生秒数", "" + draft.respawnSeconds}, {"越界宽限", "" + draft.outsideSeconds},
                {"局时（分）", "" + draft.roundMinutes}, {"退出边界", draft.exitMargin + " 格"},
                {"载具冷却", draft.vehicleCooldown + "s"}, {"载具名额", "蓝 "+draft.vehicleLimitFor(0)+" / 红 "+draft.vehicleLimitFor(1)},
        };
        for (String[] row : rows) {
            if (y + 10 > body.bottom()) break;
            Ui.fact(graphics, font, body.x() + 6, y, 78, body.w() - 12, row[0], row[1], Ui.TEXT);
            y += 12;
        }
    }

    /**
     * Points / spawns page: a real map instead of a text list.
     *
     * <p>This page exists to place things, so it needs the same terrain canvas as the line editor.
     * Selection is by clicking the marker — the list is gone — and the summary line under the map says
     * which marker is selected and where the cursor is, so a click is never a blind guess.
     */
    private void pointsPreview(GuiGraphics graphics, Layout.Rect body) {
        map.highlightPoint = draft.points.isEmpty() ? -1
                : Math.min(pointIndex, draft.points.size() - 1);
        map.highlightSupply = draft.supplies == null || draft.supplies.isEmpty() ? -1
                : Math.min(supplyIndex, draft.supplies.size() - 1);
        map.highlightSpawn = draft.spawns.isEmpty() ? -1
                : Math.min(spawnIndex, draft.spawns.size() - 1);
        map.highlightSector = -1;
        map.showSpawns = true;
        double selfX = minecraft.player == null ? 0 : minecraft.player.getX();
        double selfZ = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        map.draw(graphics, font, body, draft, Client.state, -1, selfX, selfZ, yaw);

        String selected = draft.points.isEmpty() ? "未选据点"
                : "据点 " + draft.points.get(Math.min(pointIndex, draft.points.size() - 1)).id;
        String spawnSelected = draft.spawns.isEmpty() ? "未选出生点"
                : "出生点 " + draft.spawns.get(Math.min(spawnIndex, draft.spawns.size() - 1)).id;
        graphics.drawString(font, selected, body.x() + 5, body.bottom() - 20, Ui.GOLD, false);
        graphics.drawString(font, spawnSelected, body.x() + 5, body.bottom() - 10, Ui.TEXT_DIM, false);
    }

    private void kitPreview(GuiGraphics graphics, Layout.Rect body) {
        if (draft.kits.isEmpty()) return;
        Model.Kit kit = draft.kits.get(Math.min(kitIndex, draft.kits.size() - 1));
        Layout.Rect[] halves = Layout.columns(body.pad(), 2, 6);
        slots(graphics, halves[0], "主武器", kit.primary);
        slots(graphics, halves[1], "副武器", kit.secondary);
    }

    /** Big icon with its name under it: the reference UI's weapon-library preview. */
    private void slots(GuiGraphics graphics, Layout.Rect rect, String caption, String id) {
        Ui.sunken(graphics, rect);
        graphics.drawString(font, caption, rect.x() + 5, rect.y() + 4, Ui.TEXT_DIM, false);
        int top = rect.y() + 16;
        int room = rect.h() - 32;
        if (!id.isEmpty()) AssetIcons.draw(graphics, "gun", id, rect.x() + 5, top, rect.w() - 10, room);
        else graphics.drawCenteredString(font, "未选择", rect.cx(), top + room / 2 - 4, Ui.TEXT_FAINT);
        graphics.drawString(font, Ui.clip(font, name(id), rect.w() - 10),
                rect.x() + 5, rect.bottom() - 12, Ui.TEXT, false);
    }

    private void catalogPreview(GuiGraphics graphics, Layout.Rect body) {
        Ui.sunken(graphics, body.pad());
        Layout.Rect inner = body.pad();
        if (entryId.isEmpty()) {
            graphics.drawCenteredString(font, "从左侧列表选择武器或载具", inner.cx(), inner.cy() - 4,
                    Ui.TEXT_FAINT);
            return;
        }
        int nameY = inner.bottom() - 12;
        int buttonH = admin() ? Layout.ROW : 0;
        AssetIcons.draw(graphics, entryKind, entryId, inner.x() + 6, inner.y() + 6,
                inner.w() - 12, Math.max(16, nameY - inner.y() - 40 - buttonH));
        graphics.drawString(font, Ui.clip(font, name(entryId), inner.w() - 12),
                inner.x() + 6, nameY, Ui.TEXT, false);
        // The classification is shown here so the operator can tell an aircraft from a tank before
        // authorising it — the map's air points depend on getting this right.
        if (entryKind.equals("vehicle")) {
            String type = vehicleType(entryId);
            char cls = isAircraft(entryId) ? 'A' : 'G';
            int color = cls == 'A' ? Ui.TEXT_WARN : Ui.TEXT_FAINT;
            String label = (cls == 'A' ? "航空器 · " : "地面 · ") + type;
            graphics.drawString(font, Ui.clip(font, label, inner.w() - 12),
                    inner.x() + 6, inner.y() + inner.h() - buttonH - 22, color, false);
            if (cls == 'A' && !hasAirPoint()) {
                graphics.drawString(font, "本图还没有飞机点位", inner.x() + 6,
                        inner.y() + inner.h() - buttonH - 32, Ui.TEXT_WARN, false);
            }
            // Armament state, shown right here because this is the only place the "武器配置" button
            // exists. Without it the button reads as dead: it is disabled until the vehicle is
            // authorised, and nothing on the page said so.
            int slots = vehicleSlots(entryId);
            if (slots > 0) {
                int stations = vehicleStations(entryId).size();
                VehicleArmament arm = draft.armaments.get(entryId);
                int configuredStations = 0;
                if (arm != null) {
                    for (var station : arm.stations) if (station != null && station.configured()) configuredStations++;
                }
                String line = "弹药舱 " + slots + " 格 · 武器站 " + stations + " 个"
                        + (configuredStations > 0 ? " · 已配置 " + configuredStations : "");
                graphics.drawString(font, Ui.clip(font, line, inner.w() - 12),
                        inner.x() + 6, inner.y() + inner.h() - buttonH - 44, Ui.TEXT_FAINT, false);
            }
            if (!draft.vehicles.contains(entryId)) {
                graphics.drawString(font, "先「允许此载具」，才能配置弹药",
                        inner.x() + 6, inner.y() + inner.h() - buttonH - 56, Ui.TEXT_WARN, false);
            }
        }
        if (!admin()) return;
        Layout.Rect[] actions = Layout.columns(
                new Layout.Rect(inner.x() + 6, inner.bottom() - 6 - buttonH, inner.w() - 12, buttonH), 2, 4);
        if (entryKind.equals("gun")) {
            place(sideForm, actions[0], "加入主武器库", "", "", "",
                    !draft.kits.isEmpty(), () -> assign(true));
            place(sideForm, actions[1], "加入副武器库", "", "", "",
                    !draft.kits.isEmpty(), () -> assign(false));
        } else if (entryKind.equals("vehicle")) {
            boolean allowed = draft.vehicles.contains(entryId);
            place(sideForm, actions[0], allowed ? "禁止此载具" : "允许此载具", "", "", "", true, () -> {
                if (allowed) draft.vehicles.remove(entryId);
                else draft.vehicles.add(entryId);
                warnIfUnlaunchable();
                rebuild();
            });
            // Armament limits are edited from the vehicle's own entry, because that is where the
            // operator is already thinking about this specific vehicle — a separate "armament" tab
            // would make them look the vehicle up twice.
            place(sideForm, actions[1], "武器配置", configured(entryId) ? "已设" : "默认",
                    "vehicle", entryId, allowed, () -> armamentScreen(entryId));
        }
    }

    /**
     * Warns, in the footer, about whitelisted vehicles that no point can launch.
     *
     * <p>This is the designer-side half of the "vehicles cannot be selected" report: authorising an
     * aircraft on a map with no air point looks like it worked, but the deploy screen will show
     * nothing. Saying so here — while the operator is still on the library page — is the only place
     * the fix is obvious.
     */
    private void warnIfUnlaunchable() {
        String added = null;
        if (isAircraft(entryId) && !hasAirPoint()) {
            added = "「" + name(entryId) + "」是航空器，但本图还没有飞机点位，玩家将无法出动";
        } else if (!isAircraft(entryId) && !hasGroundPoint()) {
            added = "「" + name(entryId) + "」需要地面出生点，但本图所有出生点都不刷新载具";
        }
        if (added != null) warn(added);
        else say("已修改载具白名单，记得保存地图");
    }

    /** True when some spawn point in the draft is marked as an air point. */
    /** The armament description the catalogue sent for a vehicle, or {@code null} when there is none. */
    static com.google.gson.JsonObject vehicleArmament(String vehicleId) {
        if (vehicleId == null || vehicleId.isEmpty()) return null;
        if (!Client.catalog.has("vehicleArmament")) return null;
        com.google.gson.JsonObject table = Client.catalog.getAsJsonObject("vehicleArmament");
        return table.has(vehicleId) ? table.getAsJsonObject(vehicleId) : null;
    }

    /** The vehicle's real weapon station names, as the addon keys them. */
    static List<String> vehicleStations(String vehicleId) {
        com.google.gson.JsonObject info = vehicleArmament(vehicleId);
        if (info == null || !info.has("stations")) return List.of();
        List<String> out = new ArrayList<>();
        for (var entry : info.getAsJsonObject("stations").entrySet()) out.add(entry.getKey());
        return out;
    }

    /** Slots in the vehicle's ammunition rack, or 0 when the data was not available. */
    static int vehicleSlots(String vehicleId) {
        com.google.gson.JsonObject info = vehicleArmament(vehicleId);
        return info == null ? 0 : Engine.num(info, "slots", 0);
    }

    /** One station's ammunition entries ({@code id} + {@code stack}) as the satellite data sent them. */
    static com.google.gson.JsonArray vehicleStationAmmo(String vehicleId, String station) {
        com.google.gson.JsonObject info = vehicleArmament(vehicleId);
        if (info == null || !info.has("stations")) return new com.google.gson.JsonArray();
        com.google.gson.JsonObject stations = info.getAsJsonObject("stations");
        return stations.has(station) ? stations.getAsJsonArray(station) : new com.google.gson.JsonArray();
    }

    /** Ammunition ids one station accepts; used to render the allow-list. */
    static List<String> vehicleAmmo(String vehicleId, String station) {
        List<String> out = new ArrayList<>();
        for (var element : vehicleStationAmmo(vehicleId, station)) {
            out.add(Engine.str(element.getAsJsonObject(), "id", ""));
        }
        return out;
    }

    /** Maximum stack size per ammunition id, for the station; drives capacity arithmetic. */
    static java.util.Map<String, Integer> vehicleStackSizes(String vehicleId, String station) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (var element : vehicleStationAmmo(vehicleId, station)) {
            var one = element.getAsJsonObject();
            out.put(Engine.str(one, "id", ""), Engine.num(one, "stack", 1));
        }
        return out;
    }

    /** Whether this vehicle has any non-default armament configuration in the draft. */
    private boolean configured(String vehicleId) {
        VehicleArmament armament = draft.armaments.get(vehicleId);
        return armament != null && armament.configured();
    }

    /** Opens the armament editor for one vehicle. */
    private void armamentScreen(String vehicleId) {
        minecraft.setScreen(new ArmamentScreen(this, vehicleId, name(vehicleId),
                draft.armaments.get(vehicleId)));
    }

    private boolean hasAirPoint() {
        return draft != null && draft.spawns.stream().anyMatch(s -> s.allows('A'));
    }

    private boolean hasGroundPoint() {
        return draft != null && draft.spawns.stream().anyMatch(s -> s.allows('G'));
    }

    /** Vehicle classification from the catalogue the server already scanned. */
    private boolean isAircraft(String id) {
        JsonObject classes = Client.catalog.has("vehicleClasses")
                ? Client.catalog.getAsJsonObject("vehicleClasses") : null;
        if (classes == null && Client.state.has("vehicleClasses")) {
            classes = Client.state.getAsJsonObject("vehicleClasses");
        }
        if (classes != null && classes.has(id)) {
            return classes.get(id).getAsString().startsWith("A");
        }
        return false;
    }

    private String vehicleType(String id) {
        JsonObject types = Client.catalog.has("vehicleTypes")
                ? Client.catalog.getAsJsonObject("vehicleTypes") : null;
        if (types != null && types.has(id)) return types.get(id).getAsString();
        return isAircraft(id) ? "航空器" : "地面载具";
    }

    private void assign(boolean main) {
        Model.require(entryKind.equals("gun") && !draft.kits.isEmpty(), "请先建立兵种");
        Model.Kit kit = draft.kits.get(Math.min(kitIndex, draft.kits.size() - 1));
        List<String> options = main ? kit.primaryOptions : kit.secondaryOptions;
        if (!options.contains(entryId)) options.add(entryId);
        if (main && kit.primary.isEmpty()) kit.primary = entryId;
        if (!main && kit.secondary.isEmpty()) kit.secondary = entryId;
        say("已加入「" + kit.id + "」可选武器库，记得保存地图");
    }

    /** The map line editor's canvas: terrain, polygon, points, spawns, player. */
    private void canvas(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout.Rect body = Ui.header(graphics, font, canvasPanel, "地图划线（" + zoneLabel() + "）");
        map.highlightSector = zone >= 0 && zone < SECTOR_BASE ? zone : -1;
        map.highlightZone = zone >= SECTOR_BASE ? zone - SECTOR_BASE : -1;
        map.highlightPoint = draft.points.isEmpty() ? -1
                : Math.min(pointIndex, draft.points.size() - 1);
        map.highlightSupply = draft.supplies == null || draft.supplies.isEmpty() ? -1
                : Math.min(supplyIndex, draft.supplies.size() - 1);
        map.highlightSpawn = draft.spawns.isEmpty() ? -1
                : Math.min(spawnIndex, draft.spawns.size() - 1);
        map.pendingBoundary = null;
        double selfX = minecraft.player == null ? 0 : minecraft.player.getX();
        double selfZ = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        map.draw(graphics, font, body, draft, Client.state, -1, selfX, selfZ, yaw);

        boolean inWorld = minecraft.player != null && draft.dimension.equals(
                minecraft.player.level().dimension().location().toString());
        String info = inWorld
                ? String.format(Locale.ROOT, "X %.0f  Z %.0f", selfX, selfZ) : "不在此维度";
        if (body.inside(mouseX, mouseY)) {
            info = String.format(Locale.ROOT, "X %.0f  Z %.0f", wx(mouseX), wz(mouseY));
        }
        String scale = String.format(Locale.ROOT, "视野 %.0f 格 · %.2f 格/像素", map.span, blocksPerPixel());
        graphics.drawString(font, info, body.x() + 5, body.bottom() - 20, Ui.TEXT, false);
        graphics.drawString(font, scale, body.x() + 5, body.bottom() - 10,
                blocksPerPixel() > 1.0 ? Ui.TEXT_WARN : Ui.TEXT_OK, false);
    }

    private String zoneLabel() {
        if (zone >= SECTOR_BASE) return zone == ZONE_BLUE ? "蓝方活动区域" : "红方活动区域";
        return zone < 0 ? "全图边界" : "阶段 " + zone;
    }

    /** Round grid spacing near 64 px apart, never finer than 1 block per pixel. */
    private double gridStep() {
        return Layout.gridStep(map.span, Math.max(1, canvasBody().w()));
    }

    private void poly(GuiGraphics graphics, List<Model.Vec> points, int color, int thickness) {
        map.polygon(graphics, canvasBody(), points, color, thickness);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inCanvas(mouseX, mouseY)) {
            List<Model.Vec> points = polygon();
            if(button==0&&hasShiftDown()){
                for(int i=0;i<points.size();i++)if(Math.hypot(map.sx(canvasBody(),points.get(i).x())-mouseX,map.sz(canvasBody(),points.get(i).z())-mouseY)<10){history.record(jsonDraft());movingVertex=i;return true;}
            }
            if (button == 0 && points.size() < 64) {
                Model.Vec vertex=new Model.Vec(Math.rint(wx(mouseX)),Math.rint(wz(mouseY)));
                if(points.contains(vertex)){warn("该顶点已经存在，边界会自动闭合");return true;}
                points.add(vertex);
                rebuild();
                return true;
            }
            if (button == 1 && !points.isEmpty()) {
                points.removeLast();
                rebuild();
                return true;
            }
            return true;
        }
        if (button == 0) {
            // Clicking a marker on the points map places the pending object there, or selects the
            // nearest marker; on the deploy map it picks a launch site.
            if (placeOnMap(mouseX, mouseY, true)) return true;
        }
        if (button == 1) {
            if (placeOnMap(mouseX, mouseY, false)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Left/right click handling for the maps that are not the line canvas.
     *
     * <p>Left click places the pending point or spawn exactly where the operator clicked, snapped to
     * whole blocks and sitting on the surface rather than at y=64 — a flag placed inside a hill would
     * otherwise never be capturable. Right click selects the nearest marker without moving anything,
     * which is how "which of these am I editing" is answered.
     */
    private boolean placeOnMap(double mouseX, double mouseY, boolean left) {
        if (draft == null || tab > 2) return false;
        Layout.Rect body = canvasBody();
        if (!body.inside(mouseX, mouseY)) return false;

        if (tab == 2) {
            if (!left) {
                int point = map.nearestPoint(body, draft, mouseX, mouseY, 12);
                if (point >= 0) {
                    pointIndex = point;
                    rebuild();
                    return true;
                }
                int spawn = map.nearestSpawn(body, draft, mouseX, mouseY, 12);
                if (spawn >= 0) {
                    spawnIndex = spawn;
                    rebuild();
                    return true;
                }
                return false;
            }
            double x = Math.floor(worldXAt(body, mouseX));
            double z = Math.floor(worldZAt(body, mouseY));
            double y = map.groundY(body, mouseX, mouseY);
            if(placing!=null&&!Double.isFinite(y)){warn("该区块尚未加载或维度不一致，请靠近后再放置");return true;}
            if ("point".equals(placing)) {
                if (draft.points.isEmpty()) return false;
                Model.Point point = draft.points.get(Math.min(pointIndex, draft.points.size() - 1));
                PointPlacement.move(point,x,y,z);
                placing = null;
                rebuild();
                say("据点 " + point.id + " 已放置在 " + (int) x + ", " + (int) z);
                return true;
            }
            if ("spawn".equals(placing)) {
                if (draft.spawns.isEmpty()) return false;
                Model.Spawn spawn = draft.spawns.get(Math.min(spawnIndex, draft.spawns.size() - 1));
                PointPlacement.move(spawn,x,y,z);
                placing = null;
                rebuild();
                say("出生点 " + spawn.id + " 已放置在 " + (int) x + ", " + (int) z);
                return true;
            }
            if ("supply".equals(placing)) {
                if (draft.supplies.isEmpty()) return false;
                VehicleSupply.Supply supply = draft.supplies.get(
                        Math.min(supplyIndex, draft.supplies.size() - 1));
                draft.supplies.set(Math.min(supplyIndex, draft.supplies.size() - 1),
                        new VehicleSupply.Supply(supply.id(), supply.team(), x, y, z,
                                supply.radius(), supply.ammo(), supply.energy(), supply.health()));
                placing = null;
                rebuild();
                say("补给点 " + supply.id() + " 已放置在 " + (int) x + ", " + (int) z);
                return true;
            }
            // Selection mode never mutates coordinates.
            int point = map.nearestPoint(body, draft, mouseX, mouseY, 12);
            if (point >= 0) {
                pointIndex = point;
                rebuild();
                return true;
            }
            int spawn = map.nearestSpawn(body, draft, mouseX, mouseY, 12);
            if (spawn >= 0) {
                spawnIndex = spawn;
                rebuild();
                return true;
            }
            say("未移动任何标记；请先点击左侧「放置当前据点」或「放置当前出生点」");
            return true;
        }

        // tab 0: pick a deploy site.
        if (!left) return false;
        String picked = pickSpawn(mouseX, mouseY);
        if (picked == null) return false;
        deploySpawn = picked;
        rebuild();
        return true;
    }

    private double worldXAt(Layout.Rect body, double screenX) {
        return map.worldX(body, screenX);
    }

    private double worldZAt(Layout.Rect body, double screenY) {
        return map.worldZ(body, screenY);
    }

    /** Nearest same-team deploy site to the cursor, in the projection the deploy map was drawn with. */
    private String pickSpawn(double mouseX, double mouseY) {
        if (draft == null) return null;
        List<String> allowed = allowedSpawns();
        if (allowed.isEmpty()) return null;
        Layout.Rect body = canvasBody();
        String best = null;
        double bestDistance = 16;
        for (Deploy.Site site : sites()) {
            if (!allowed.contains(site.id())) continue;
            double distance = Math.hypot(map.sx(body, site.x()) - mouseX,
                    map.sz(body, site.z()) - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = site.id();
            }
        }
        return best;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Middle-drag pans the canvas: at 1 block per pixel the operator needs to slide the view
        // rather than zoom out and lose the detail they were editing. The right-hand map on the
        // points page pans the same way, because placing a flag needs the same precision.
        if(button==0&&movingVertex>=0&&inCanvas(mouseX,mouseY)){
            polygon().set(movingVertex,new Model.Vec(Math.rint(wx(mouseX)),Math.rint(wz(mouseY))));return true;
        }
        if (button == 2 && (inCanvas(mouseX, mouseY)
                || (tab == 2 && canvasBody().inside(mouseX, mouseY)))) {
            map.pan(canvasBody(), dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double x,double y,int button){
        if(movingVertex>=0){movingVertex=-1;rebuild();return true;}
        return super.mouseReleased(x,y,button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        Layout.Rect body = canvasBody();
        if ((tab == 1 || tab == 2) && body.inside(mouseX, mouseY)) {
            if(vertical==0)return false;
            map.zoom(body, mouseX, mouseY, vertical > 0);
            return true;
        }
        if(sideForm!=null&&sideForm.wheel(mouseX,mouseY,vertical))return true;
        if (form != null && form.wheel(mouseX, mouseY, vertical)) {rememberScroll();return true;}
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
