package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

/**
 * Player-facing screen: loadout and deployment, the room browser, and this match's scoreboard.
 *
 * <p>Rebuilt on {@link FrontScreen} because the reported bugs were structural. The old version painted
 * a translucent wash over the world, drew the left column's rows wherever their hard-coded offsets
 * happened to land, painted the map as a separate opaque rectangle, and then called
 * {@code super.render} — which re-blurred the world and dropped a dirt texture on top of everything.
 * That is why the two halves of one window looked like two layers, and why the map looked blurry
 * while the text stayed sharp.
 *
 * <p>Now both halves are panels of the same window: shared frame, shared title strip, shared footer,
 * opaque surfaces, and every control living inside a {@link Form} that owns its rows and clips them.
 */
final class BattleScreen extends FrontScreen {
    private static final String[] TABS = {"角色配装 / 部署", "作战房间", "本局统计", "地图与规则管理"};

    private Map<String,String> fitting(String kind,String id){
        if(Client.fittingMemory.size()>512)Client.fittingMemory.clear();
        return Client.fittingMemory.computeIfAbsent(selected+"/"+kind+":"+id,k->new LinkedHashMap<>());
    }
    private void customize(String kind,String id){
        Model.require(!id.isEmpty(),"请先选择装备");
        minecraft.setScreen(new FittingScreen(this,selected,id,kind,fitting(kind,id)));
    }
    private int tab;
    private int formTab;
    private final int[] scrollMemory = new int[4];
    private int roomPage;
    private int scorePage;
    /** Page count from the last paint, used to clamp wheel paging. */
    private int scorePages = 1;

    private String selected = "";
    private String kitId = "", primary = "", secondary = "", spawn = "", vehicle = "";

    private Model.Arena arena;
    /**
     * The shared map widget. The same class paints the editor canvas, so the in-match map and the
     * designer's map cannot drift apart in projection, terrain or overlay.
     */
    private final MapPicker map = new MapPicker();

    private Form leftForm;
    private Layout.Rect leftPanel;
    private Layout.Rect rightPanel;
    private String leftTitle = "", rightTitle = "";

    /** True between sending a deploy request and the server reporting us alive. */
    private boolean awaitingDeploy;
    private int poll;

    BattleScreen() {
        super("战地风云");
    }

    // ------------------------------------------------------------------ state glue

    private JsonObject me() {
        return Client.state.has("me") ? Client.state.getAsJsonObject("me") : null;
    }

    private boolean flag(String key) {
        return Client.state.has(key) && Client.state.get(key).getAsBoolean();
    }

    private boolean admin() {
        return flag("admin");
    }

    private boolean inMatch() {
        return flag("inMatch");
    }

    private int team() {
        return me() == null ? -1 : Engine.num(me(), "team", -1);
    }

    private boolean alive() {
        JsonObject me = me();
        return me != null && me.has("alive") && me.get("alive").getAsBoolean();
    }

    private int respawnRemaining() {
        return Engine.num(Client.state, "respawnRemaining", 0);
    }

    private String teamName() {
        return switch (team()) {
            case 0 -> "蓝方";
            case 1 -> "红方";
            default -> "未选队";
        };
    }

    private void chooseVehicle(List<String> options,String title){
        var choices=options.stream().map(id->new ChoiceScreen.Choice(id,id.isEmpty()?"不部署载具":name(id),"vehicle")).toList();
        minecraft.setScreen(new ChoiceScreen(this,title,choices,id->{
            vehicle=id;
            // Keep the site usable for whatever was just picked. Without this the two rows disagree and
            // the deploy button goes grey with no obvious cause — which is what "only non-admins cannot
            // deploy a vehicle" turned out to be. An admin who tested right after editing the map had
            // usually set the site by hand, so the defect only showed up for ordinary players.
            spawn = Deploy.siteFor(sites(), spawn, vehicle, vehicleClass(vehicle));
            rebuild();
        }));
    }

    private String name(String id) {
        if (id == null || id.isEmpty()) return "未选择";
        JsonObject names = Client.state.has("names") ? Client.state.getAsJsonObject("names") : null;
        return names != null && names.has(id) ? names.get(id).getAsString() : Catalog.prettify(id);
    }

    private Model.Kit kit() {
        if (arena == null) return null;
        return arena.kits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
    }

    /**
     * Deploy sites this player may actually use.
     *
     * <p>Read from the server's {@code deploySites} snapshot rather than recomputed here: the list now
     * includes capture points the team holds, and a client-side guess would offer sites the server
     * refuses (or miss ones it allows). The local computation survives only as a fallback for the
     * handful of ticks before the first snapshot arrives.
     */
    private List<Deploy.Site> sites() {
        return Deploy.fromState(Client.state, arena, team());
    }

    /** Site ids this player may use, for the cycling control rows. */
    private List<String> spawns() {
        return sites().stream().map(Deploy.Site::id).toList();
    }

    /** The site with this id, or null. */
    private Deploy.Site site(String id) {
        return sites().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    /** Sites that can launch the given vehicle class. */
    private List<Deploy.Site> sitesFor(char vehicleClass) {
        return Deploy.sitesFor(sites(), vehicleClass);
    }

    /**
     * {@code 'A'} when this id is an aircraft, {@code 'G'} otherwise.
     *
     * <p>Read from the server's {@code vehicleClasses} map so the client does not need to load
     * superbwarfare's metadata; a missing entry defaults to ground, which is the safe direction.
     */
    private char vehicleClass(String id) {
        if (id == null || id.isEmpty()) return 'G';
        JsonObject classes = Client.state.has("vehicleClasses")
                ? Client.state.getAsJsonObject("vehicleClasses") : null;
        if (classes != null && classes.has(id)) {
            String value = classes.get(id).getAsString();
            return value.isEmpty() ? 'G' : value.charAt(0);
        }
        return 'G';
    }

    private List<String> vehicles() {
        if (arena == null) return List.of();
        return arena.vehicles;
    }

    private void send(String op) {
        Client.send(Client.request(op, selected));
    }

    private void pick(String title, List<ChoiceScreen.Choice> choices, java.util.function.Consumer<String> result) {
        minecraft.setScreen(new ChoiceScreen(this, title, choices, result));
    }

    // ------------------------------------------------------------------ shell hooks

    @Override
    protected String[] tabs() {
        return admin() ? TABS : Arrays.copyOf(TABS, 3);
    }

    @Override
    protected int activeTab() {
        return Math.min(tab, tabs().length - 1);
    }

    @Override
    protected boolean onTab(int index) {
        if (index == 3) {
            minecraft.setScreen(new OpsScreen());
            return true;
        }
        tab = index;
        rebuild();
        return true;
    }

    @Override
    protected String subtitle() {
        String mapName = arena == null ? "未选择地图" : arena.name;
        return mapName + " · " + teamName();
    }

    @Override
    protected void poll() {
        if (++poll >= 40) {
            poll = 0;
            send("state");
        }
        // Ask for the map's baked terrain. Throttled inside, and a no-op once the bake is in hand, so a
        // screen left open costs one packet per map rather than one per repaint.
        Client.wantTerrain(arena != null && arena.id != null && !arena.id.isEmpty() ? arena.id : selected);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void removed() {
        // Releases the terrain texture registered by the map widget.
        map.close();
    }

    /** Called by {@link Client} whenever a server snapshot arrives. */
    void updated() {
        String msg = Engine.str(Client.state, "message", "");
        if (!msg.isEmpty()) {
            if (msg.startsWith("操作失败")) fail(msg);
            else ok(msg);
        }
        if (awaitingDeploy && alive()) {
            awaitingDeploy = false;
            minecraft.setScreen(null);
            return;
        }
        if (msg.startsWith("操作失败")) awaitingDeploy = false;
        sync();
    }

    private void sync() {
        selected = Engine.str(Client.state, "selected", "");
        arena = Client.state.has("arena")
                ? BattlefieldOps.GSON.fromJson(Client.state.get("arena"), Model.Arena.class) : null;
        if (arena != null) {
            // Keep the three selections honest against what the server will accept: a kit that was
            // deleted, a weapon the kit no longer authorises, or a spawn on the wrong team all have
            // to fall back here, where the player can see the result.
            if (arena.kits.stream().noneMatch(k -> k.id.equals(kitId))) {
                kitId = arena.kits.isEmpty() ? "" : arena.kits.getFirst().id;
                primary = "";
                secondary = "";
            }
            Model.Kit kit = kit();
            if (kit != null) {
                if (!kit.options(true).contains(primary)) primary = kit.options(true).getFirst();
                if (!kit.options(false).contains(secondary)) secondary = kit.options(false).getFirst();
            }
            List<String> spawns = spawns();
            if (!spawns.contains(spawn)) spawn = spawns.isEmpty() ? "" : spawns.getFirst();
            if (!arena.vehicles.contains(vehicle)) vehicle = "";
            // Re-reconcile after the snapshot: a site can disappear (a capture point lost, a stage
            // advanced) or the vehicle list can change, either of which can leave the pair inconsistent.
            spawn = Deploy.siteFor(sites(), spawn, vehicle, vehicleClass(vehicle));
        }
        rebuild();
    }

    // ------------------------------------------------------------------ rebuild

    @Override
    protected void rebuild() {
        if(leftForm!=null)scrollMemory[formTab]=leftForm.scroll();
        formTab=tab;
        clearWidgets();
        leftForm = null;
        barForm = null;
        // The panes are assigned before the tab is built: the map is framed to the right pane during
        // the build, so a null pane here would place the projection on top of the control column.
        leftPanel = layout.left;
        rightPanel = layout.right;
        switch (tab) {
            case 0 -> buildDeploy();
            case 1 -> buildRoom();
            case 2 -> buildStats();
            default -> { }
        }
        if(leftForm!=null){leftForm.scrollTo(scrollMemory[tab]);leftForm.finish();}
        buildBar();
    }

    private Form form(Layout.Rect panel, String title) {
        leftForm = newForm(Ui.headerBody(panel));
        leftTitle = title;
        return leftForm;
    }

    /**
     * Frames the match map on the arena, once.
     *
     * <p>Anchored on the arena boundary rather than on the player: a spawn or flag outside the
     * player's immediate surroundings has to be visible, which is the whole point of the map.
     *
     * <p>Framing only happens while the view has never been set, or when the arena changes. Rebuilding
     * the left column — which happens on every button press — must not re-frame, or the zoom and pan
     * the player just made would snap back on the next click.
     */
    private void fitMap() {
        String current = arena == null ? "" : arena.id;
        if (framedArena != null && framedArena.equals(current)) {
            map.showPlayers = true;
            return;
        }
        framedArena = current;
        map.frame(arena, minecraft.player == null ? 0 : minecraft.player.getX(),
                minecraft.player == null ? 0 : minecraft.player.getZ());
        map.showPlayers = true;
    }

    /** Arena id the map was last framed for; {@code null} until the first frame. */
    private String framedArena;

    // ------------------------------------------------------------------ tab 0: deploy

    private void buildDeploy() {
        Form f = form(layout.left, "角色配装");
        rightTitle = "战术地图";
        fitMap();

        if (arena == null) {
            f.paragraph("还没有选中任何地图。先去「作战房间」选择一张地图。", Ui.TEXT_DIM);
            f.skip(4);
            primary(f, "前往作战房间", true, () -> { tab = 1; rebuild(); });
            return;
        }
        if (me() == null) {
            f.paragraph("你还没有加入这张地图的房间。加入后才能选择阵营并部署。", Ui.TEXT_DIM);
            f.skip(4);
            primary(f, "前往作战房间", true, () -> { tab = 1; rebuild(); });
            return;
        }
        if (team() < 0) {
            f.paragraph("房间已加入，但还没有选择阵营。阵营决定出生点、队伍颜色和计分板归属。", Ui.TEXT_WARN);
            f.skip(2);
            primary(f, "选择蓝方 / 进攻方", true, () -> chooseTeam(0));
            primary(f, "选择红方 / 防守方", true, () -> chooseTeam(1));
            f.skip(2);
            row(f, "退出房间", "恢复原背包", () -> send("leave"));
            return;
        }

        f.caption("阵营  " + teamName(), team() == 0 ? Ui.BLUE : Ui.RED);
        if (!flag("roomRunning")) {
            f.paragraph("对局尚未开始，管理员开始后即可部署。", Ui.TEXT_WARN);
        }

        // --- loadout
        f.section("配装");
        Model.Kit kit = kit();
        if (kit == null) {
            f.caption("本图没有可用兵种，请管理员在编辑器中添加。", Ui.TEXT_WARN);
        } else {
            row(f, "兵种", kit.id, "role", kit.id, () -> pick("选择兵种 / 配装",
                    arena.kits.stream().map(k -> new ChoiceScreen.Choice(k.id, k.id, "role")).toList(),
                    id -> { kitId = id; primary = ""; secondary = ""; rebuild(); }));
            row(f, "主武器", name(first(kit,true)), "gun", first(kit, true), () -> weapon(kit, true));
            row(f,"改装主武器",fitting("gun",first(kit,true)).size()+" 项",!first(kit,true).isEmpty(),()->customize("gun",first(kit,true)));
            row(f, "副武器", first(kit,false).isEmpty() ? "无" : name(first(kit,false)),
                    "gun", first(kit, false), () -> weapon(kit, false));
            row(f,"改装副武器",fitting("gun",first(kit,false)).size()+" 项",!first(kit,false).isEmpty(),()->customize("gun",first(kit,false)));
            gearFacts(f, kit);
        }

        // --- deployment
        f.section("部署点");
        List<Deploy.Site> allSites = sites();
        if (allSites.isEmpty()) {
            f.caption("本阶段没有你可用的部署点，请联系管理员检查地图。", Ui.TEXT_WARN);
        } else {
            if (allSites.stream().noneMatch(s -> s.id().equals(spawn))) spawn = allSites.getFirst().id();
            Deploy.Site current = site(spawn);
            row(f, "部署点", spawn, () -> {
                // Move to the next site, preferring one that can still launch the current vehicle so
                // cycling sites does not silently drop the vehicle the player already picked.
                List<String> options = spawns();
                if (!vehicle.isEmpty()) {
                    List<Deploy.Site> capable = sitesFor(vehicleClass(vehicle));
                    List<String> capableIds = capable.stream().map(Deploy.Site::id).toList();
                    if (!capableIds.isEmpty()) options = capableIds;
                }
                spawn = cycle(options, spawn);
                rebuild();
            });
            if (current != null) {
                f.caption(current.spawn() ? "出生点" : "已占领据点（仅地面载具）",
                        current.spawn() ? Ui.TEXT_FAINT : Ui.TEXT_OK);
            }
        }

        // --- vehicles. Aircraft are listed separately because they need a runway point, and mixing
        // them into one list is what made "there is no plane point to select" hard to see.
        List<String> ground = new ArrayList<>();
        List<String> air = new ArrayList<>();
        for (String id : vehicles()) {
            if (vehicleClass(id) == 'A') air.add(id);
            else ground.add(id);
        }
        List<Deploy.Site> airSites = sitesFor('A');

        f.section("载具");
        if (ground.isEmpty() && air.isEmpty()) {
            f.caption("本图没有授权任何载具。管理员可在「武器/载具库」中开启。", Ui.TEXT_FAINT);
        } else {
            // One validation for both lists, through the same rule the tests pin. The two rows used to
            // validate separately — the ground row cleared `vehicle` whenever the pick was not in the
            // ground list, which is every aircraft, so choosing an aircraft set the field and the very
            // next rebuild wiped it back to "无". Aircraft looked unselectable while ground vehicles
            // worked, because only the ground row cleared the shared field.
            vehicle = VehicleDeploy.keepPick(ground, air, vehicle);
            if (!ground.isEmpty()) {
                List<String> options = new ArrayList<>();
                options.add("");
                options.addAll(ground);
                row(f, "地面载具", vehicle.isEmpty() || vehicleClass(vehicle) == 'A'
                                ? "无" : name(vehicle), "vehicle", vehicle, () -> {
                    chooseVehicle(options, "选择地面载具");
                });
            }
            if (!air.isEmpty()) {
                List<String> options = new ArrayList<>();
                options.add("");
                options.addAll(air);
                String current = vehicleClass(vehicle) == 'A' ? vehicle : "";
                row(f, "航空器", current.isEmpty() ? "无" : name(current),
                        "vehicle", current, () -> {
                    chooseVehicle(options, "选择航空器");
                });
                if (airSites.isEmpty()) {
                    f.caption("没有飞机点位，航空器无法出动。请在编辑器中把某个出生点勾选「空中」。",
                            Ui.TEXT_WARN);
                }
            }
            if (!vehicle.isEmpty()){row(f,"载具弹种",fitting("vehicle",vehicle).size()+" 项",()->customize("vehicle",vehicle));vehicleFacts(f);}
        }

        f.skip(3);
        boolean running = flag("roomRunning");
        char need = vehicle.isEmpty() ? 'G' : vehicleClass(vehicle);
        boolean siteKnown = allSites.stream().anyMatch(s -> s.id().equals(spawn));
        boolean siteFits = !siteKnown || (vehicle.isEmpty()
                ? true
                : sitesFor(need).stream().anyMatch(s -> s.id().equals(spawn)));
        // Never promise a deploy the server will refuse. The server has two extra blockers the client
        // cannot see (the hull cap, and a site camped by the enemy), so this predicate stays strictly
        // narrower than the server's: it only removes cases the server would reject outright.
        boolean ready = VehicleDeploy.deployable(running, siteKnown, siteFits,
                kit() != null, vehicleBlocked());
        if (!vehicle.isEmpty() && !siteFits) {
            f.caption(need == 'A' ? "当前部署点不是飞机点位" : "当前部署点不提供载具刷新", Ui.TEXT_WARN);
        }
        if (alive()) {
            primary(f, "重新部署（计一次阵亡）", ready, () -> send("redeploy"));
        } else if (respawnRemaining() > 0) {
            primary(f, "重生等待 " + respawnRemaining() + "s", false, () -> { });
        } else {
            primary(f, "部署", ready, this::deploy);
        }
        if (!running) {
            f.paragraph("你可以先配好装备，对局开始后一键部署。", Ui.TEXT_FAINT);
        }
    }

    /**
     * A read-only summary of what else the kit issues.
     *
     * <p>Read-only on purpose: personal items, armour and accessories are the operator's decision about
     * what a kit <em>is</em>, not a per-deployment choice. Letting a player swap their armour here would
     * turn a configured kit into a free picker and make the item allow-list meaningless. The player still
     * needs to see what they will receive, so the contents are listed rather than hidden.
     */
    private void gearFacts(Form f, Model.Kit kit) {
        KitGear gear = kit.gear();
        if (!gear.configured()) return;
        if (!gear.items.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (KitGear.Entry entry : gear.items) {
                if (text.length() > 0) text.append("、");
                text.append(KitItems.name(entry.id)).append(" ×").append(entry.count);
            }
            f.caption("随身物品  " + text, Ui.TEXT_FAINT);
        }
        for (String slot : gear.armourSlots()) {
            String label = switch (slot) {
                case "HEAD" -> "头盔";
                case "CHEST" -> "胸甲";
                case "LEGS" -> "护腿";
                case "FEET" -> "靴子";
                default -> slot;
            };
            f.caption(label + "  " + KitItems.name(gear.armour.get(slot)), Ui.TEXT_FAINT);
        }
        if (!gear.accessories.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (var entry : gear.accessories.entrySet()) {
                if (text.length() > 0) text.append("、");
                text.append(KitItems.name(entry.getValue()));
            }
            f.caption("配饰  " + text, Ui.TEXT_FAINT);
        }
    }

    /**
     *
     * <p>Only the cooldown and the match cap are reported here, and only when the server has actually
     * sent them. The site-fitting question stays client-side because the client already holds the site
     * list. Being conservative matters in one direction only: a false "ready" produces a rejection
     * message, but a false "not ready" leaves a player staring at a dead button with no way to argue.
     */
    private boolean vehicleBlocked() {
        if (vehicle.isEmpty()) return false;
        JsonObject state = Client.state.has("vehicleState")
                ? Client.state.getAsJsonObject("vehicleState") : null;
        if (state == null || !state.has("state")) return false;
        String name = Engine.str(state, "state", "");
        // A missing site or an unauthorised hull are already covered by the client-side checks above.
        return "COOLDOWN".equals(name) || "MATCH_LIMIT".equals(name);
    }

    /** Cycles the ground-vehicle list, treating an aircraft selection as "none". */
    private String cycleGround(List<String> options) {
        String current = vehicleClass(vehicle) == 'A' ? "" : vehicle;
        return cycle(options, current);
    }

    /** Icon id for a weapon row: the current pick, or the first authorised option. */
    private String first(Model.Kit kit, boolean main) {
        String chosen = main ? primary : secondary;
        if (chosen != null && !chosen.isEmpty()) return chosen;
        return kit.options(main).stream().filter(id -> !id.isEmpty()).findFirst().orElse("");
    }

    private void weapon(Model.Kit kit, boolean main) {
        List<ChoiceScreen.Choice> choices = kit.options(main).stream()
                .filter(id -> !id.isEmpty())
                .map(id -> new ChoiceScreen.Choice(id, name(id), "gun"))
                .toList();
        if (choices.isEmpty()) {
            warn("该兵种没有授权" + (main ? "主" : "副") + "武器，请在编辑器中添加");
            return;
        }
        pick(main ? "主武器库" : "副武器库", choices, id -> {
            if (main) primary = id;
            else secondary = id;
            rebuild();
        });
    }

    private void vehicleFacts(Form f) {
        JsonObject state = Client.state.has("vehicleState") ? Client.state.getAsJsonObject("vehicleState") : null;
        String text = state != null && state.has("text") ? Engine.str(state, "text", "") : "";
        int used = state == null ? 0 : Engine.num(state, "used", 0);
        int limit = arena == null ? 0 : Engine.num(state, "limit", arena.vehicleLimitFor(team()));
        int configured = arena == null ? 0 : arena.vehicleCooldown;
        // The server reports the cooldown as a number of seconds remaining; the configured length is a
        // different number. Reading the remaining value from the wrong key made this line always show
        // "0s / 60s" even while the player was locked out, which is the worst possible display for a
        // cooldown: it says "go ahead" to someone who cannot.
        int remaining = state == null ? 0 : Engine.num(state, "remaining", 0);

        // Cooldown as a draining bar, not just numbers. The bar answers "how long until I can go"
        // without arithmetic, and a bar that is visibly empty is harder to misread than "0s".
        f.paint(12, (g, r) -> {
            boolean cooling = remaining > 0 && configured > 0;
            double fraction = cooling ? 1.0 - remaining / (double) configured : 1.0;
            Ui.bar(g, new Layout.Rect(r.x(), r.y(), r.w(), 4), fraction,
                    cooling ? Ui.TEXT_WARN : Ui.TEXT_OK, Ui.PANEL_SUNKEN);
            String label = cooling ? "冷却 " + remaining + "s / " + configured + "s" : "载具就绪";
            g.drawString(font, Ui.clip(font, label, r.w()), r.x(), r.y() + 5,
                    cooling ? Ui.TEXT_WARN : Ui.TEXT_FAINT, false);
        });

        // Per-match hull pool as discrete segments: how many sorties are left is the question this line
        // is actually asked, and a count of filled cells answers it without reading the number.
        int pool = Math.max(0, Math.min(limit, 24));
        if (pool > 0) {
            f.paint(18, (g, r) -> {
                int left = Math.max(0, limit - used);
                Ui.segments(g, new Layout.Rect(r.x(), r.y() + 1, r.w(), 6),
                        Math.min(left, pool), pool, Ui.ACCENT, Ui.PANEL_SUNKEN);
                g.drawString(font, "载具名额 " + left + " / " + limit + " 可用", r.x(), r.y() + 9,
                        left == 0 ? Ui.TEXT_ERROR : Ui.TEXT_FAINT, false);
            });
        }

        if (!vehicle.isEmpty() && "NO_SITE".equals(Engine.str(state, "state", ""))) {
            // The server judges this across all its sites; the client may still have a fitting one.
            // Say so plainly rather than letting the generic text imply the map has no runway at all.
            f.caption("服务端未找到可放飞该载具的点位", Ui.TEXT_WARN);
        } else if (!text.isEmpty()) {
            boolean deployable = state != null && state.has("deployable")
                    && state.get("deployable").getAsBoolean();
            chip(f, text, deployable ? Ui.TEXT_OK : Ui.TEXT_WARN);
        }
    }

    /**
     * A label with a tinted plate, for a single status word.
     *
     * <p>The plate is opaque and mixed toward the panel colour, so the status reads as a tag rather than
     * as more body text — several statuses used to be plain coloured captions in a column of captions,
     * where "fine" and "blocked" differed only by hue.
     */
    private void chip(Form f, String text, int color) {
        f.paint(13, (g, r) -> {
            String clipped = Ui.clip(font, text, Math.max(1, r.w() - 8));
            int w = Math.min(font.width(clipped) + 8, r.w());
            g.fill(r.x(), r.y(), r.x() + w, r.y() + 12, Ui.mix(color, Ui.PANEL_SUNKEN, 0.82));
            g.fill(r.x(), r.y() + 12, r.x() + w, r.y() + 13, color);
            g.drawString(font, clipped, r.x() + 4, r.y() + 3, color, false);
        });
    }

    private void deploy() {
        JsonObject request = Client.request("deploy", selected);
        request.addProperty("kit", kitId);
        request.addProperty("spawn", spawn);
        request.addProperty("vehicle", vehicle);
        request.addProperty("primary", first(kit(),true));
        request.addProperty("secondary", first(kit(),false));
        Model.Kit selectedKit=kit();
        request.add("primaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,true))));
        request.add("secondaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,false))));
        request.add("vehicleFittings",BattlefieldOps.GSON.toJsonTree(fitting("vehicle",vehicle)));
        awaitingDeploy = true;
        say("正在部署…");
        Client.send(request);
    }

    private void chooseTeam(int value) {
        JsonObject request = Client.request("selectTeam", selected);
        request.addProperty("team", value);
        Client.send(request);
    }

    private static String cycle(List<String> list, String value) {
        if (list.isEmpty()) return value;
        return list.get(Math.floorMod(list.indexOf(value) + 1, list.size()));
    }

    // ------------------------------------------------------------------ tab 1: room

    private void buildRoom() {
        Form f = form(layout.left, "作战房间");
        rightTitle = "地图预览";
        fitMap();

        JsonArray maps = Client.state.has("maps") ? Client.state.getAsJsonArray("maps") : new JsonArray();
        f.section("地图");
        if (maps.isEmpty()) {
            f.caption("服务器还没有地图。", Ui.TEXT_WARN);
            if (admin()) primary(f, "新建地图", true, () -> minecraft.setScreen(new OpsScreen()));
            return;
        }
        int shown = Math.min(maps.size(), 5);
        roomPage = Math.min(roomPage, Math.max(0, maps.size() - shown));
        for (int i = roomPage; i < Math.min(maps.size(), roomPage + shown); i++) {
            JsonObject entry = maps.get(i).getAsJsonObject();
            String id = Engine.str(entry, "id", "");
            boolean room = entry.has("room") && entry.get("room").getAsBoolean();
            boolean running = entry.has("running") && entry.get("running").getAsBoolean();
            String state = running ? "对局中" : room ? "已建房" : "未建房";
            row(f, (id.equals(selected) ? "● " : "") + Engine.str(entry, "name", id), state, () -> {
                if (me() != null) {
                    warn("请先退出当前房间，再切换地图");
                    return;
                }
                selected = id;
                send("state");
            });
        }
        Layout.Rect[] pages = f.pair(Layout.ROW - 2);
        place(f, pages[0], "上一页", "", "", "", roomPage > 0, () -> {
            roomPage = Math.max(0, roomPage - 1);
            rebuild();
        });
        place(f, pages[1], "下一页", "", "", "",
                roomPage + shown < maps.size(), () -> {
                    roomPage++;
                    rebuild();
                });

        f.section("本图房间");
        boolean room = flag("roomExists");
        boolean running = flag("roomRunning");
        int players = Engine.num(Client.state, "roomPlayers", 0);
        f.caption(room ? (running ? "对局进行中" : "等待开始") + " · " + players + " 人"
                : "尚未创建房间", room ? Ui.TEXT_OK : Ui.TEXT_DIM);

        if (me() == null) {
            if (room) {
                primary(f, "进入本图房间", true, () -> send("join"));
                f.paragraph("每张地图只能有一个房间。进入后即可选择红蓝方。", Ui.TEXT_FAINT);
            } else if (admin()) {
                primary(f, "创建本图房间", true, () -> send("createRoom"));
                f.paragraph("每张地图只能创建一个房间；同一维度内边界重叠的地图也无法同时开局。",
                        Ui.TEXT_FAINT);
            } else {
                f.paragraph("这张地图还没有房间，请等待管理员创建。", Ui.TEXT_WARN);
            }
            if (admin() && room) row(f, "关闭房间", "结束对局", () -> send("stop"));
            return;
        }

        f.caption("当前阵营  " + teamName(), team() < 0 ? Ui.TEXT_WARN
                : team() == 0 ? Ui.BLUE : Ui.RED);
        if (team() < 0) {
            primary(f, "选择蓝方 / 进攻方", true, () -> chooseTeam(0));
            primary(f, "选择红方 / 防守方", true, () -> chooseTeam(1));
        }
        if (inMatch()) primary(f, "前往部署", true, () -> { tab = 0; rebuild(); });
        row(f, "退出房间", "恢复原背包", () -> send("leave"));
        if (admin()) {
            if (!running) row(f, "开始本局", "锁定阵营", () -> send("start"));
            row(f, "关闭房间", "结束并结算", () -> send("stop"));
        }
    }

    // ------------------------------------------------------------------ tab 2: stats

    private void buildStats() {
        rightTitle = "本局统计";
    }

    private void paintStats(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.header(g, font, layout.content, "本局统计");
        Layout.Rect body = Ui.headerBody(layout.content);
        if (!inMatch()) {
            g.drawCenteredString(font, "只有在本局内的玩家可以查看计分板",
                    body.cx(), body.cy() - 4, Ui.TEXT_DIM);
            return;
        }
        JsonArray scores = Client.state.has("scores") ? Client.state.getAsJsonArray("scores") : new JsonArray();
        JsonArray tickets = Client.state.has("tickets") ? Client.state.getAsJsonArray("tickets") : new JsonArray();
        JsonArray tracks = Client.state.has("tracksTickets")
                ? Client.state.getAsJsonArray("tracksTickets") : new JsonArray();

        int y = body.y() + 3;
        StringBuilder head = new StringBuilder("兵力  蓝 " + (tickets.isEmpty() ? "—" : tickets.get(0).getAsInt()));
        if (tracks.isEmpty() || tracks.get(1).getAsBoolean()) {
            head.append("  /  红 ").append(tickets.isEmpty() ? "—" : tickets.get(1).getAsInt());
        } else {
            head.append("  （红方无兵力池）");
        }
        head.append("      阶段 ").append(Engine.num(Client.state, "stage", 0) + 1)
                .append(" / ").append(Math.max(1, Engine.num(Client.state, "stages", 1)));
        g.drawString(font, head.toString(), body.x() + 5, y, Ui.TEXT, false);
        y += 12;

        // Two force bars, blue over red, so the ticket balance is a shape rather than two numbers that
        // have to be compared. Only drawn when both pools exist: a one-sided match has nothing to
        // compare, and a full bar against nothing would read as a lopsided score.
        if (!tickets.isEmpty() && tickets.size() >= 2
                && (tracks.isEmpty() || tracks.get(1).getAsBoolean())) {
            int blue = tickets.get(0).getAsInt();
            int red = tickets.get(1).getAsInt();
            int most = Math.max(1, Math.max(blue, red));
            int half = Math.max(1, (body.w() - 12) / 2);
            Ui.bar(g, new Layout.Rect(body.x() + 5, y, half, 3),
                    blue / (double) most, Ui.BLUE, Ui.PANEL_SUNKEN);
            Ui.bar(g, new Layout.Rect(body.x() + 7 + half, y, half, 3),
                    red / (double) most, Ui.RED, Ui.PANEL_SUNKEN);
            y += 6;
        }

        String result = Engine.str(Client.state, "result", "");
        if (!result.isEmpty()) {
            g.drawString(font, "结算：" + result, body.x() + 5, y, Ui.GOLD, false);
            y += 12;
        }

        // Column x positions derived from the pane, so narrow windows never overlap the columns.
        int nameX = body.x() + 6;
        int kdX = body.x() + Math.max(70, body.w() * 46 / 100);
        int capX = body.x() + Math.max(110, body.w() * 68 / 100);
        int scoreX = body.x() + Math.max(150, body.w() * 84 / 100);
        Ui.divider(g, body.x() + 4, y, body.w() - 10);
        y += 1;
        // Header row gets a sunken plate so the column titles do not read as the first data row, which
        // is what happened when both used the same faint colour on the same background.
        g.fill(body.x() + 4, y, body.right() - 6, y + 11, Ui.PANEL_SUNKEN);
        y += 2;
        g.drawString(font, "玩家", nameX, y, Ui.TEXT_FAINT, false);
        g.drawString(font, "击杀/阵亡", kdX, y, Ui.TEXT_FAINT, false);
        g.drawString(font, "占点", capX, y, Ui.TEXT_FAINT, false);
        g.drawString(font, "得分", scoreX, y, Ui.TEXT_FAINT, false);
        y += 12;

        int rows = Math.max(1, (body.bottom() - y - 14) / 12);
        int pages = Math.max(1, (scores.size() + rows - 1) / rows);
        // Recorded so wheel scrolling can clamp to the real last page instead of running past it.
        scorePages = pages;
        scorePage = Math.min(scorePage, pages - 1);
        int from = scorePage * rows;
        for (int i = from; i < Math.min(scores.size(), from + rows); i++) {
            JsonObject row = scores.get(i).getAsJsonObject();
            int color = Engine.num(row, "team", -1) == 0 ? Ui.BLUE
                    : Engine.num(row, "team", -1) == 1 ? Ui.RED : Ui.TEXT_DIM;
            String who = Engine.str(row, "name", "");
            if (who.equals(Engine.str(me(), "name", ""))) who = "▶ " + who;
            g.drawString(font, Ui.clip(font, who, kdX - nameX - 4), nameX, y, color, false);
            g.drawString(font, Engine.num(row, "kills", 0) + " / " + Engine.num(row, "deaths", 0),
                    kdX, y, color, false);
            g.drawString(font, "" + Engine.num(row, "captures", 0), capX, y, color, false);
            g.drawString(font, "" + Engine.num(row, "score", 0), scoreX, y, color, false);
            y += 12;
        }
        if (scores.isEmpty()) {
            g.drawString(font, "本局还没有得分记录", nameX, y, Ui.TEXT_FAINT, false);
        }
        if (pages > 1) {
            String page = "第 " + (scorePage + 1) + " / " + pages + " 页 · 滚轮翻页";
            g.drawString(font, page, body.right() - font.width(page) - 6, body.bottom() - 10,
                    Ui.TEXT_FAINT, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == 2 && inMatch()) {
            // Clamped against the page count the last paint computed, so scrolling past the end does
            // not silently swallow the wheel and need a second scroll to come back.
            scorePage = Math.max(0, Math.min(Math.max(0, scorePages - 1),
                    scorePage + (vertical > 0 ? -1 : 1)));
            return true;
        }
        if (leftForm != null && leftForm.wheel(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        switch (tab) {
            case 0, 1 -> paintMapTab(graphics, mouseX, mouseY, partialTick, leftTitle);
            case 2 -> paintStats(graphics, mouseX, mouseY, partialTick);
            default -> { }
        }
        if (barForm != null) barForm.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Left control column plus right map pane, drawn as one window: same frame, same title strip. */
    private void paintMapTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                             String header) {
        if (leftForm == null) return;
        Ui.header(graphics, font, leftPanel, header);
        graphics.enableScissor(leftForm.area().x(), leftForm.area().y(),
                leftForm.area().right(), leftForm.area().bottom());
        leftForm.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        Layout.Rect body = Ui.header(graphics, font, rightPanel, rightTitle);
        if (arena == null) {
            graphics.drawCenteredString(font, "暂无地图", body.cx(), body.cy() - 4, Ui.TEXT_FAINT);
            return;
        }
        drawMap(graphics, body);
    }

    /**
     * Terrain underneath, arena overlay on top, both projected by the shared map widget so the two
     * layers cannot drift apart. Rendered inside one scissor so nothing bleeds past the frame.
     */
    private void drawMap(GuiGraphics graphics, Layout.Rect body) {
        double selfX = minecraft.player == null ? 0 : minecraft.player.getX();
        double selfZ = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        JsonArray position = Client.state.has("position") ? Client.state.getAsJsonArray("position") : null;
        if (position != null && position.size() >= 3 && alive()) {
            selfX = position.get(0).getAsDouble();
            selfZ = position.get(2).getAsDouble();
        }
        map.highlightPoint = -1;
        map.highlightSector = -1;
        map.highlightSpawn = -1;
        // The editor's own highlights are reset too. They are fields on the shared map widget, so a
        // ring the operator had selected in OpsScreen stayed drawn on the in-match map: a stray supply
        // ring or activity-zone outline with no control anywhere that could turn it off.
        map.highlightSupply = -1;
        map.highlightZone = -1;
        if (!spawn.isEmpty() && tab == 0) {
            for (int i = 0; i < arena.spawns.size(); i++) {
                if (arena.spawns.get(i).id.equals(spawn)) {
                    map.highlightSpawn = i;
                    break;
                }
            }
        }
        map.draw(graphics, font, body, arena, Client.state, team(), selfX, selfZ, yaw);
        if (tab == 0 && !spawns().isEmpty()) {
            // A chip rather than bare text: the hint sits on top of the terrain overlay, and plain
            // faint text there was unreadable over a bright map region.
            String hint = Client.bakedPending && Client.baked == null
                    ? "正在准备全图地形…（只做一次）"
                    : "点击地图上的部署点可直接选择";
            int color = Client.bakedPending && Client.baked == null ? Ui.TEXT_WARN : Ui.TEXT_DIM;
            int w = font.width(hint) + 8;
            Layout.Rect plate = new Layout.Rect(body.x() + 4, body.bottom() - 13,
                    Math.min(w, Math.max(1, body.w() - 8)), 12);
            graphics.fill(plate.x(), plate.y(), plate.right(), plate.bottom(), Ui.PANEL_SUNKEN);
            Ui.frame(graphics, plate, Ui.FRAME);
            graphics.fill(plate.x() + 1, plate.bottom() - 1, plate.right() - 1, plate.bottom(),
                    Ui.ACCENT_DIM);
            graphics.drawString(font, Ui.clip(font, hint, plate.w() - 6), plate.x() + 4,
                    plate.y() + 2, color, false);
        }
    }

    /** Bottom bar: real widgets in their own form, so hit-testing cannot drift from the paint. */
    private Form barForm;

    private void buildBar() {
        Layout.Rect bar = layout.bar;
        if (bar.h() <= 0) {
            barForm = null;
            return;
        }
        barForm = newForm(bar.inset(2));
        Layout.Rect[] cells = Layout.columns(bar.inset(2), 4, 3);
        barForm.skip(0);
        place(barForm, cells[0], "关闭", "", "", "", true, this::onClose);
        place(barForm, cells[1], "刷新", "", "", "", true, () -> send("state"));
        place(barForm, cells[2], "编辑器", "", "", "", admin(),
                () -> minecraft.setScreen(new OpsScreen()));
        place(barForm, cells[3], "部署", "", "", "", tab != 0 || me() == null, () -> {
            tab = 0;
            rebuild();
        });
        barForm.finish();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tab == 0 && arena != null) {
            Layout.Rect body = Ui.headerBody(rightPanel);
            if (body.inside(mouseX, mouseY)) {
                String picked = pickSpawn(body, mouseX, mouseY);
                if (picked != null) {
                    spawn = picked;
                    rebuild();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Nearest launchable deploy site to a map click.
     *
     * <p>Iterates {@link #sites()} rather than the arena's spawn list, so a held capture point — which
     * is a deploy site but not a spawn — is clickable too. The hit test uses the same projection the
     * map was drawn with, so the marker aimed at is the marker selected.
     */
    private String pickSpawn(Layout.Rect body, double mouseX, double mouseY) {
        if (arena == null) return null;
        List<Deploy.Site> sites = sites();
        String best = null;
        double bestDistance = 16;
        for (Deploy.Site site : sites) {
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
    public boolean isPauseScreen() {
        return false;
    }
}
