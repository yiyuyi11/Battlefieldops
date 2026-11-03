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
    @Override public boolean shouldCloseOnEsc(){return !DeploymentLock.required(Client.state);}
    @Override public void onClose(){if(!DeploymentLock.required(Client.state))super.onClose();}
    private static final String[] TABS = {"角色配装 / 部署", "退出至大厅", "本局统计", "地图与规则管理"};

    Map<String,String> fitting(String kind,String id){
        if(Client.fittingMemory.size()>512)Client.fittingMemory.clear();
        return Client.fittingMemory.computeIfAbsent(kind+":"+id,k->new LinkedHashMap<>());
    }
    private void customize(String kind,String id){
        Model.require(!id.isEmpty(),"请先选择装备");
        minecraft.setScreen(FittingScreen.open(this,selected,id,kind,fitting(kind,id),kit()==null?"":kit().id));
    }
    private boolean tacticalMap;
    private boolean overviewOnly=true;
    private Form overviewControls;
    private Form overviewDeploy;
    private Form overviewLoadout;
    private Form overviewSquad;
    private Form overviewRight;
    private Form overviewLeave;
    private Form overviewAmmo;
    boolean fullBattlefield(){return overviewOnly && tab==0 && DeploymentLock.room(Client.state) && !alive() && !tacticalMap;}
    Layout.Rect overviewViewport(){return fullBattlefield()?new Layout.Rect(0,0,width,height):Ui.headerBody(layout.right);}

    @Override protected boolean horizontalTabs(){return true;}
    @Override protected boolean showBottomBar(){return !fullBattlefield();}
    @Override protected boolean showFooter(){return !fullBattlefield();}
    @Override protected boolean showChrome(){return !fullBattlefield();}
    @Override protected boolean worldBackdrop() { return tab==0 && DeploymentLock.room(Client.state) && !alive() && !tacticalMap; }

    private int tab;
    private int formTab;
    private final int[] scrollMemory = new int[4];
    private int roomPage;
    private int scorePage;
    /** Page count from the last paint, used to clamp wheel paging. */
    private int scorePages = 1;

    String selected = "";
    String kitId = "", primary = "", secondary = "", melee = "", throwable = "", tactical1 = "", tactical2 = "";
    private String selectionMap="";
    /** Emits one ammunition-type row per equipped Superb Warfare gun, used by every loadout view. */
    void ammoRows(Form form) {
        for (String gunId : new String[]{tactical1, tactical2}) {
            if (gunId == null || gunId.isEmpty()) continue;
            if (!SuperbAmmo.isGunId(gunId)) {
                // TaCZ guns have a single ammunition item, so there is nothing to choose; log it once so a
                // missing row can be told apart from a gun that simply has no alternatives.
                BattlefieldOps.LOG.debug("[bfops] no ammo choice for {} (not a Superb Warfare gun)", gunId);
                continue;
            }
            // Nothing to choose means nothing to show: one candidate is not a choice, and no launcher in
            // either tactical slot means the strip should not exist at all.
            var candidates = kit() == null ? java.util.List.<String>of() : ammoCandidates(kit(), gunId);
            if (candidates.size() < 2) {
                BattlefieldOps.LOG.debug("[bfops] ammo choice for {} has {} candidates: {}", gunId, candidates.size(), candidates);
                continue;
            }
            row(form, "弹种 · " + meleeName(gunId), ammoRowText(gunId), () -> pickAmmo(gunId));
        }
    }

    /** True when at least one equipped launcher offers more than one ammunition type. */
    private boolean hasAmmoChoice() {
        Model.Kit k = kit();
        if (k == null) return false;
        for (String gunId : new String[]{tactical1, tactical2}) {
            if (gunId == null || gunId.isEmpty() || !SuperbAmmo.isGunId(gunId)) continue;
            if (ammoCandidates(k, gunId).size() > 1) return true;
        }
        return false;
    }

    /** Types the player may pick: the map's whitelist, or everything the gun itself declares when unset. */
    private java.util.List<String> ammoCandidates(Model.Kit kit, String gunId) {
        var whitelisted = kit.ammoWhitelist(gunId);
        if (!whitelisted.isEmpty()) return whitelisted;
        var probe = SuperbAmmo.gunBundle(gunId, 0);
        return probe.isEmpty() ? java.util.List.of() : SuperbAmmo.ammoIds(probe.getFirst());
    }

    /** Text for the ammunition row of one gun on the deploy screen. */
    private String ammoRowText(String gunId) {
        Model.Kit k = kit();
        if (k == null) return "未设置";
        var allowed = ammoCandidates(k, gunId);
        if (allowed.isEmpty()) return "默认";
        String picked = Client.deployedAmmo.getOrDefault(gunId, allowed.getFirst());
        return allowed.contains(picked) ? KitItems.name(picked) : KitItems.name(allowed.getFirst());
    }

    /** Cycles this gun's ammunition through the map's whitelist; the choice travels with the deploy request. */
    private void pickAmmo(String gunId) {
        Model.Kit k = kit();
        if (k == null) return;
        var allowed = ammoCandidates(k, gunId);
        if (allowed.isEmpty()) return;
        String current = Client.deployedAmmo.getOrDefault(gunId, allowed.getFirst());
        int next = (allowed.indexOf(current) + 1) % allowed.size();
        Client.deployedAmmo.put(gunId, allowed.get(next));
        rememberLoadout();
        rebuild();
    }

    /** " ×3" when the map authored a count above one; nothing otherwise, so single items stay clean. */
    private String countSuffix(String id) {
        Model.Kit k = kit();
        if (k == null || id == null || id.isEmpty()) return "";
        int count = k.countFor(id);
        if (count <= 1) return "";
        // A gun's number is rounds, not objects: saying "×3 发" removes the ambiguity for rocket launchers.
        return ItemCatalog.isGun(id) ? " ×" + count + " 发" : " ×" + count + " 个";
    }

    void rememberLoadout(){
        // rebuildContent() calls this before the snapshot has filled the fields, and writing that blank state
        // over the stored loadout is what made the throwable and both tactical slots read "none" again.
        boolean blank=primary.isEmpty()&&secondary.isEmpty()&&melee.isEmpty()
                &&throwable.isEmpty()&&tactical1.isEmpty()&&tactical2.isEmpty();
        if(!kitId.isEmpty()&&!blank){
            if(Client.loadoutMemory.size()>512)Client.loadoutMemory.clear();
            String[] loadout={kitId,primary,secondary,melee,throwable,tactical1,tactical2};
            Client.loadoutMemory.put(selected+"/"+kitId,loadout);
        }
    }
    void selectKit(String id){
        // Do NOT remember the current fields here: on a freshly opened screen they are still empty, and
        // saving them first overwrote the stored loadout with blanks - that is what made the tactical slots
        // (and previously the throwable) read "none" after a redeploy. Pickers save on their own edits.
        String previousMelee=melee;
        kitId=id;
        String[] saved=Client.loadoutMemory.getOrDefault(selected+"/"+id,Client.lobbyLoadouts.get(id));
        Model.Kit next=kit();
        primary=saved==null||saved.length<2?"":saved[1];
        secondary=saved==null||saved.length<3?"":saved[2];
        melee=saved==null||saved.length<4?"":saved[3];
        throwable=saved==null||saved.length<5?"":saved[4];
        tactical1=saved==null||saved.length<6?"":saved[5];
        tactical2=saved==null||saved.length<7?"":saved[6];
        if(saved==null){
            // No local memory (fresh client): adopt whatever the server says is actually carried.
            var me=Client.state.has("me")?Client.state.getAsJsonObject("me"):new com.google.gson.JsonObject();
            tactical1=Engine.str(me,"tactical1","");
            tactical2=Engine.str(me,"tactical2","");
        }
        if(next!=null){
            if(!gunAllowed(next,true,primary))primary=next.options(true).getFirst();
            if(!gunAllowed(next,false,secondary))secondary=next.options(false).getFirst();
            boolean prunesMelee=arena!=null&&!arena.isTraining();
            if(saved==null||(prunesMelee&&!melee.isEmpty()&&!meleeAllowed(next,melee)))melee=next.melee==null?"":next.melee;
            // Never leave the melee slot empty by accident: memory, then the class default, then the first
            // authorised option, then whatever the player already had.
            if(melee.isEmpty()){
                // Last resort before defaults: what the server says this player actually deployed with. That
                // record survives a client restart, so a loadout is never lost just because the memory file is.
                var me=Client.state.has("me")?Client.state.getAsJsonObject("me"):new com.google.gson.JsonObject();
                String carried=Engine.str(me,"lastMelee","");
                if(!carried.isEmpty()&&meleeAllowed(next,carried))melee=carried;
            }
            if(melee.isEmpty()){
                if(next.melee!=null&&!next.melee.isEmpty())melee=next.melee;
                else if(!next.meleeOptions().isEmpty())melee=next.meleeOptions().getFirst();
                else if(meleeAllowed(next,previousMelee))melee=previousMelee;
            }
            // Only a normal map prunes an unauthorised throwable. On a firing range (or before the arena has
            // arrived, which happens right after respawning) the player's own choice is kept: the server is
            // the authority on authorisation, and wiping here is what made the slot read "none" again.
            boolean prunesThrowable=arena!=null&&!arena.isTraining();
            if(prunesThrowable&&!throwable.isEmpty()&&!next.throwableOptions().contains(throwable))throwable=next.throwable==null?"":next.throwable;
        }
        rememberLoadout();
    }
    private String spawn = "";
    String vehicle = "";
    /** Extra items the player may take only in a firing range; ignored by every normal map. */
    private final java.util.List<String> trainingItems = new java.util.ArrayList<>();

    Model.Arena arena;
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
        // The tactical map rotates with the player's heading; the editor's own MapPicker does not.
        map.rotating = true;
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
        return Factions.name(arena,team());
    }

    private void chooseVehicle(List<String> options,String title){
        var choices=options.stream().map(id->new ChoiceScreen.Choice(id,id.isEmpty()?"不部署载具":name(id),"vehicle")).toList();
        minecraft.setScreen(new ChoiceScreen(this,title,choices,id->{
            selectVehicle(id);
            rebuild();
        }));
    }

    /** Sets the vehicle pick and keeps the spawn usable for it, so the two rows never disagree. */
    String selectedSite(){return spawn;}
    boolean vehicleSpawnAllowed(){return DeploymentRules.canSpawnNew(site(spawn));}
    void selectSite(String id){
        Deploy.Site target=site(id);Model.require(target!=null,"部署目标已失效");
        spawn=id;
        if(!vehicle.isEmpty()&&(!DeploymentRules.canSpawnNew(target)||!target.allows(vehicleClass(vehicle)))){
            vehicle="";say("已选择 "+target.label()+"，本次按步兵增援部署");
        }
        DeployMarkers.select(id);
        ok("已选择："+DeploymentText.label(target.id(),target.kind(),target.label())+" · 空格确认入场");
    }
    void selectVehicle(String id) {
        Model.require(id.isEmpty()||vehicleSpawnAllowed(),"小队、信标和登车只能步兵部署，请先选择固定载具刷新点");
        vehicle=id;String previous=spawn;
        spawn=Deploy.siteFor(sites(),spawn,vehicle,vehicleClass(vehicle));
        if(!previous.equals(spawn)){var target=site(spawn);say("新载具将从 "+(target==null?spawn:target.label())+" 刷新");}
    }

    String name(String id) {
        if (id == null || id.isEmpty()) return "未选择";
        JsonObject names = Client.state.has("names") ? Client.state.getAsJsonObject("names") : null;
        return names != null && names.has(id) ? names.get(id).getAsString() : Catalog.prettify(id);
    }

    /** Every scanned gun, used by the firing-range loadout where the kit's authorisation does not apply. */
    static java.util.List<String> allGuns() {
        var out = new java.util.ArrayList<String>();
        JsonObject catalog = Client.catalog;
        if (catalog != null && catalog.has("entries")) {
            for (var element : catalog.getAsJsonArray("entries")) {
                var entry = element.getAsJsonObject();
                if ("gun".equals(Engine.str(entry, "kind", ""))) out.add(Engine.str(entry, "id", ""));
            }
        }
        return out;
    }

    /** True when the id may be taken in this slot: any gun at a firing range, otherwise the kit's list. */
    boolean gunAllowed(Model.Kit kit, boolean main, String id) {
        if(kit==null)return false;
        if (arena != null && arena.isTraining()) return allGuns().contains(id);
        return kit.options(main,team()).contains(id);
    }

    boolean meleeAllowed(Model.Kit kit, String id) {
        if(kit==null)return false;
        if (arena != null && arena.isTraining()) return !id.isEmpty();
        return kit.meleeOptions(team()).contains(id);
    }

    Model.Kit kit() {
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
    List<Deploy.Site> sites() {
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
    char vehicleClass(String id) {
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
        return arena.vehiclesFor(team());
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
        if(index==1){LobbyScreen.open();return true;}
        if (index == 3) {
            minecraft.setScreen(new OpsScreen());
            return true;
        }
        tab = index;
        rebuild();
        return true;
    }

    @Override protected boolean bf1Style(){return true;}
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
        spaceArmed=!com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
        // The firing-range weapon picker lists every scanned gun, which needs the catalog on the client.
        // It is only sent on request, so ask for it as soon as the deploy screen opens.
        if(!Client.catalog.has("entries"))Client.send(Client.request("catalog",""));
        sync();
    }

    @Override
    public void removed() {
        // Releases the terrain texture registered by the map widget.
        map.close();
    }

    /** Called by {@link Client} whenever a server snapshot arrives. */
    private JsonObject lastControls;
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
        JsonObject controls=UiSnapshot.controls(Client.state);
        if(controls.equals(lastControls))return;
        lastControls=controls;
        sync();
    }

    private void sync() {
        selected = Engine.str(Client.state, "selected", "");
        arena = FactionArt.arena();
        if (arena != null) {
            if(!selectionMap.equals(selected)){
                selectionMap=selected;
                JsonObject who=Client.state.has("me")?Client.state.getAsJsonObject("me"):new JsonObject();
                kitId=Engine.str(who,"lastKit",Engine.str(who,"kit",""));
                String[] saved=Client.loadoutMemory.getOrDefault(selected+"/"+kitId,Client.lobbyLoadouts.get(kitId));
                primary=saved!=null?saved[1]:Engine.str(who,"lastPrimary","");
                secondary=saved!=null?saved[2]:Engine.str(who,"lastSecondary","");
                melee=saved!=null?saved[3]:Engine.str(who,"lastMelee","");
                // These three were never restored here, which is why a redeploy showed "none": the screen is
                // rebuilt with empty fields and only the class dropdown used to re-read the memory.
                throwable=saved!=null&&saved.length>4&&!saved[4].isEmpty()?saved[4]:Engine.str(who,"throwable","");
                tactical1=saved!=null&&saved.length>5&&!saved[5].isEmpty()?saved[5]:Engine.str(who,"tactical1","");
                tactical2=saved!=null&&saved.length>6&&!saved[6].isEmpty()?saved[6]:Engine.str(who,"tactical2","");
            }
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
                if (!gunAllowed(kit,true,primary)) primary = kit.options(true,team()).getFirst();
                if (!gunAllowed(kit,false,secondary)) secondary = kit.options(false,team()).getFirst();
                if(!melee.isEmpty()&&!meleeAllowed(kit,melee))melee=kit.melee==null?"":kit.melee;
                // A firing range accepts any item, so the whitelist must not reset the choice back to empty.
                if(!throwable.isEmpty()&&!(arena!=null&&arena.isTraining())&&!kit.throwableOptions(team()).contains(throwable))throwable=kit.throwable==null?"":kit.throwable;
            }
            List<String> spawns = spawns();
            if (!spawns.contains(spawn)) spawn = spawns.isEmpty() ? "" : spawns.getFirst();
            if (!arena.vehiclesFor(team()).contains(vehicle)) vehicle = "";
            // Re-reconcile after the snapshot: a site can disappear (a capture point lost, a stage
            // advanced) or the vehicle list can change, either of which can leave the pair inconsistent.
            vehicle=DeploymentRules.keepVehicle(site(spawn),vehicle);
            spawn = Deploy.siteFor(sites(), spawn, vehicle, vehicleClass(vehicle));
        }
        DeployMarkers.syncSelection(DeploymentLock.room(Client.state)&&!alive()?spawn:"");
        rebuild();
    }

    // ------------------------------------------------------------------ rebuild

    @Override
    protected void rebuildContent() {
        deployReady=false;
        rememberLoadout();
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
        overviewControls=null;overviewDeploy=null;overviewLoadout=null;overviewSquad=null;overviewRight=null;overviewLeave=null;overviewAmmo=null;
        if(fullBattlefield()){
            clearWidgets(); // Hidden controls must not remain clickable or keyboard-focusable.
            // Battlefield layout: squad by the squad list, loadout options stacked on the right above
            // Deploy, and the leave action at bottom-left, separated from deployment.
            var bfLayout=new Bf1Layout(width,height);
            var squadRect=bfLayout.squad;
            overviewSquad=newForm(squadRect);
            place(overviewSquad,squadRect.inset(2),"小队 "+SquadRules.label(Engine.num(me(),"squad",0)),"","","",!alive(),()->minecraft.setScreen(new SquadSelectScreen(this)));
            overviewSquad.finish();
            overviewRight=newForm(bfLayout.options);
            boolean training=arena!=null&&arena.isTraining();
            if(training)row(overviewRight,"切换配装","",!alive(),this::rearm);
            row(overviewRight,"兵种配装","",kit()!=null,()->minecraft.setScreen(new LoadoutScreen(this)));
            row(overviewRight,"部署选项","",()->{overviewOnly=false;rebuild();});
            row(overviewRight,"战术地图","",()->{tacticalMap=true;overviewOnly=false;rebuild();});
            if(hasAmmoChoice())ammoRows(overviewRight);
            overviewRight.finish();
            // Leave room: bottom-left, far from the deploy action.
            var leaveRect=new Layout.Rect(8,height-30,100,20);
            overviewLeave=newForm(leaveRect);
            place(overviewLeave,leaveRect.inset(2),"离开房间","","","",true,()->minecraft.setScreen(new ConfirmActionScreen(this,"退出当前房间？","退出后返回大厅并恢复原背包。","退出房间",()->send("lobbyReturn"))));
            overviewLeave.finish();
            // Fullscreen weapon strip: class dropdown at the left, then the five weapon slots, like
            // Battlefield's bottom bar.
            var stripRect=bfLayout.loadout;
            overviewLoadout=newForm(stripRect);
            var stripCells=new Layout.Rect[8];
            for(int i=0;i<8;i++)stripCells[i]=bfLayout.slot(i);
            dropdown(overviewLoadout,stripCells[0],"兵种",kit()==null?"":kit().id,kitChoices(),id->{selectKit(id);rebuild();});
            place(overviewLoadout,stripCells[1],"主 "+name(first(kit(),true)),"",AssetIcons.silhouetteKind(first(kit(),true)),first(kit(),true),kit()!=null,()->weapon(kit(),true)).preview=true;
            place(overviewLoadout,stripCells[2],"副 "+name(first(kit(),false)),"",AssetIcons.silhouetteKind(first(kit(),false)),first(kit(),false),kit()!=null,()->weapon(kit(),false)).preview=true;
            place(overviewLoadout,stripCells[3],"近身 "+meleeName(melee),"",AssetIcons.silhouetteKind(melee),melee,kit()!=null,this::pickMelee).preview=true;
            place(overviewLoadout,stripCells[4],"投掷 "+meleeName(throwable)+countSuffix(throwable),"",AssetIcons.silhouetteKind(throwable),throwable,kit()!=null,this::pickThrowable).preview=true;
            place(overviewLoadout,stripCells[5],"战术1 "+meleeName(tactical1)+countSuffix(tactical1),"",AssetIcons.silhouetteKind(tactical1),tactical1,kit()!=null,()->pickTactical(false)).preview=true;
        place(overviewLoadout,stripCells[6],"战术2 "+meleeName(tactical2)+countSuffix(tactical2),"",AssetIcons.silhouetteKind(tactical2),tactical2,kit()!=null,()->pickTactical(true)).preview=true;
        place(overviewLoadout,stripCells[7],"全部配装","","","",true,()->minecraft.setScreen(new LoadoutScreen(this)));
        // Ammunition choice needs its own strip: the loadout bar above is a single row of cells, and the
        // left panel does not exist yet on this page (it is built for the list view).
            overviewLoadout.finish();
            // Battlefield pins the primary Deploy action to the bottom-right, apart from the smaller controls.
            var deployRect=bfLayout.deploy;
            overviewDeploy=newForm(deployRect);
            var deployButton=place(overviewDeploy,deployRect.inset(2),"部署","","","",deployReady,this::deploy);
            deployButton.primary=true;
            overviewDeploy.finish();
        }else if(tab==0&&DeploymentLock.room(Client.state)&&!alive()){
            row(leftForm,"全屏查看战场","收起侧栏",()->{overviewOnly=true;tacticalMap=false;rebuild();});
            leftForm.finish();
        }
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
            primary(f, "前往游戏大厅", true, LobbyScreen::open);
            return;
        }
        if (me() == null) {
            f.paragraph("你还没有加入这张地图的房间。加入后才能选择阵营并部署。", Ui.TEXT_DIM);
            f.skip(4);
            primary(f, "前往游戏大厅", true, LobbyScreen::open);
            return;
        }
        if (team() < 0) {
            f.paragraph("房间已加入，但还没有选择阵营。阵营决定出生点、队伍颜色和计分板归属。", Ui.TEXT_WARN);
            f.skip(2);
            primary(f, "选择 "+Factions.name(arena,0)+" / 进攻方", true, () -> chooseTeam(0));
            primary(f, "选择 "+Factions.name(arena,1)+" / 防守方", true, () -> chooseTeam(1));
            f.skip(2);
            row(f,"退出房间","返回游戏大厅",LobbyScreen::open);
            return;
        }

        f.caption("阵营  " + teamName(), team() == 0 ? Ui.BLUE : Ui.RED);
        if (!flag("roomRunning")) {
            f.paragraph("对局尚未开始，房主或管理员开始后即可部署。", Ui.TEXT_WARN);
            if(flag("canManageRoom"))primary(f,"开始本局",true,()->send("start"));
        }

        row(f,"服务器总榜与荣誉","长期累计战绩 · 查看个人勋章",true,()->CareerScreen.open(this));
        row(f,"详细排行榜","击杀 / 得分 / 助攻 / 救援与个人分类榜",true,()->NativeKillIcons.openScoreboard(this));
        row(f,"操作设置","趴下：切换 / 按住",()->minecraft.setScreen(new ControlSettingsScreen(this)));
        row(f,"小地图设置","范围 / 图层 / 名单",()->minecraft.setScreen(new MinimapSettingsScreen(this)));
        f.section("小队 / 复活信标");
        int squad=Engine.num(me(),"squad",0);
        row(f,"我的小队",SquadRules.label(squad),!alive(),()->{
            List<ChoiceScreen.Choice> choices=new ArrayList<>();
            choices.add(new ChoiceScreen.Choice("0","退出小队",""));
            if(Client.state.has("squads"))for(var item:Client.state.getAsJsonArray("squads")){
                var one=item.getAsJsonObject();int id=Engine.num(one,"id",0),count=Engine.num(one,"count",0);
                if(count<SquadRules.CAPACITY||id==squad)choices.add(new ChoiceScreen.Choice(""+id,
                    SquadRules.label(id)+" · "+count+"/4 · "+Engine.str(one,"members",""),""));
            }
            pick("加入小队（待部署时可切换）",choices,id->{var request=Client.request("squad",selected);request.addProperty("squad",Integer.parseInt(id));Client.send(request);});
        });
        if(squad>0){
            if(Client.state.has("squads"))for(var item:Client.state.getAsJsonArray("squads")){
                var one=item.getAsJsonObject();if(Engine.num(one,"id",0)==squad)f.paragraph("成员："+Engine.str(one,"members",""),Ui.TEXT_DIM);
            }
            row(f,"放置复活信标","当前位置 · 每小队一个",alive()&&!me().get("downed").getAsBoolean(),()->send("placeBeacon"));
            row(f,"回收我的信标","到期 / 离场自动移除",()->send("removeBeacon"));
            f.caption("携带信标装备 · 6 次部署 · 冷却 60 秒",Ui.TEXT_FAINT);
        }
        // --- loadout
        f.section("配装");
        Model.Kit kit = kit();
        if (kit == null) {
            f.caption("本图没有可用兵种，请管理员在编辑器中添加。", Ui.TEXT_WARN);
        } else {
            dropdown(f,"兵种",kit.id,arena.kits.stream().map(k->new ChoiceScreen.Choice(k.id,k.id,"role",k.id)).toList(),id->{selectKit(id);rebuild();});
            row(f, "主武器", name(first(kit,true)), "gun", first(kit, true), () -> weapon(kit, true));
            row(f,"改装主武器",fitting("gun",first(kit,true)).size()+" 项",!first(kit,true).isEmpty(),()->customize("gun",first(kit,true)));
            row(f, "副武器", first(kit,false).isEmpty() ? "无" : name(first(kit,false)),
                    "gun", first(kit, false), () -> weapon(kit, false));
            row(f,"改装副武器",fitting("gun",first(kit,false)).size()+" 项",!first(kit,false).isEmpty(),()->customize("gun",first(kit,false)));
            row(f, "近身武器", meleeName(melee), melee.isEmpty() ? "" : "gun", melee, this::pickMelee);
            row(f, "投掷武器", throwable.isEmpty() ? "无" : meleeName(throwable) + countSuffix(throwable), throwable.isEmpty() ? "" : "item", throwable, this::pickThrowable);
        row(f, "战术道具 1", tactical1.isEmpty() ? "无" : meleeName(tactical1) + countSuffix(tactical1), tactical1.isEmpty() ? "" : "item", tactical1, () -> pickTactical(false));
            ammoRows(f);
        row(f, "战术道具 2", tactical2.isEmpty() ? "无" : meleeName(tactical2) + countSuffix(tactical2), tactical2.isEmpty() ? "" : "item", tactical2, () -> pickTactical(true));
            // Firing range: re-arm live from the side panel too, so it is reachable while alive as well.
            if(arena!=null&&arena.isTraining())row(f,"切换配装（靶场）","立即生效 · 无需重生",this::rearm);
            if (arena != null && arena.isTraining()) {
                // Firing range only: any item can be taken for testing. Never sent for a normal map.
                row(f, "靶场临时物品", trainingItems.isEmpty() ? "无" : trainingItems.size() + " 项",
                        () -> minecraft.setScreen(new ItemPickerScreen(this, id -> {
                            if (id != null && !id.isEmpty() && !trainingItems.contains(id) && trainingItems.size() < 8)
                                trainingItems.add(id);
                            rebuild();
                        })));
                if (!trainingItems.isEmpty())
                    row(f, "清空靶场物品", "", () -> { trainingItems.clear(); rebuild(); });
            }
            gearFacts(f, kit);
            f.skip(2);
            primary(f, "编辑配装（卡片视图）", true, () -> minecraft.setScreen(new LoadoutScreen(this)));
        }

        // --- deployment
        f.section("部署点");
        List<Deploy.Site> allSites = sites();
        if (allSites.isEmpty()) {
            f.caption("本阶段没有你可用的部署点，请联系管理员检查地图。", Ui.TEXT_WARN);
        } else {
            if (allSites.stream().noneMatch(s -> s.id().equals(spawn))) spawn = allSites.getFirst().id();
            Deploy.Site current = site(spawn);
            row(f,"部署点",current==null?"请选择":current.label(),()->pick("选择部署目标",
                    allSites.stream().map(site->new ChoiceScreen.Choice(site.id(),site.label()+" · "+site.kind(),"")).toList(),
                    id->{selectSite(id);rebuild();}));
            if(current!=null)f.caption(SquadRules.dynamic(current.kind())?"步兵增援 / 现有载具，不刷新新载具":"基地 / 已占领据点",Ui.TEXT_FAINT);
        }
        if(Client.state.has("blockedDeploySites"))for(var item:Client.state.getAsJsonArray("blockedDeploySites")){
            var blocked=item.getAsJsonObject();f.caption(Engine.str(blocked,"label","")+"："+Engine.str(blocked,"reason",""),Ui.TEXT_WARN);
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
        if(!vehicleSpawnAllowed()){
            vehicle="";
            f.paragraph("当前为小队/信标步兵增援或登上现有载具，不生成新载具。",Ui.TEXT_DIM);
            List<Deploy.Site> fixed=allSites.stream().filter(DeploymentRules::canSpawnNew).filter(s->s.allows('G')||s.allows('A')).toList();
            row(f,"选择载具刷新点","基地 / 机场 / 允许刷新据点",!fixed.isEmpty(),()->pick("选择固定载具刷新点",fixed.stream().map(s->new ChoiceScreen.Choice(s.id(),s.label(),"")).toList(),id->{selectSite(id);rebuild();}));
        } else if (ground.isEmpty() && air.isEmpty()) {
            f.caption("本图没有授权任何载具。管理员可在「武器/载具库」中开启。", Ui.TEXT_FAINT);
        } else {
            row(f, "载具卡片", "图片选择", "", "", () -> minecraft.setScreen(new VehicleDeployScreen(this)));
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
                ? site(spawn)!=null&&(site(spawn).kind().equals("VEHICLE")||arena.deploymentArea(team(),"I",site(spawn).x(),site(spawn).z(),Engine.num(Client.state,"stage",0),arena.isStaged()))
                : sitesFor(need).stream().anyMatch(s -> s.id().equals(spawn)));
        // Never promise a deploy the server will refuse. The server has two extra blockers the client
        // cannot see (the hull cap, and a site camped by the enemy), so this predicate stays strictly
        // narrower than the server's: it only removes cases the server would reject outright.
        boolean ready = VehicleDeploy.deployable(running, siteKnown, siteFits,
                kit() != null, vehicleBlocked());
        deployReady=ready;
        if (!vehicle.isEmpty() && !siteFits) {
            f.caption(need == 'A' ? "当前部署点不是飞机点位" : "当前部署点不提供载具刷新", Ui.TEXT_WARN);
        }
        if (alive()) {
            // A firing range has no deployment cycle, and redeploying would hand the player to the deploy
            // observer, which teleports the body. Alive on a range means "edit your loadout", so the primary
            // action is the instant re-arm instead.
            boolean trainingRange = arena != null && arena.isTraining();
            if (trainingRange) {
                primary(f, "切换配装（立即生效）", true, this::rearm);
            } else {
                primary(f, "重新部署（计一次阵亡）", ready, () -> send("redeploy"));
            }
        } else if (respawnRemaining() > 0) {
            primary(f, "重生等待 " + respawnRemaining() + "s", false, () -> { });
            if(arena.allowRespawnSkip)row(f,"跳过部署等待","仍需选择部署点并确认部署",true,()->send("skipRespawn"));
        } else {
            f.caption(ready?"选择目标后，按空格确认入场":"请先选择有效的配装和部署点",ready?Ui.TEXT_OK:Ui.TEXT_WARN);
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
        if(kit==null)return "";
        String chosen = main ? primary : secondary;
        if (chosen != null && !chosen.isEmpty()) return chosen;
        return kit.options(main,team()).stream().filter(id -> !id.isEmpty()).findFirst().orElse("");
    }

    /** Class choices for the bottom-bar dropdown, so the class can be swapped without leaving the page. */
    private List<ChoiceScreen.Choice> kitChoices() {
        List<ChoiceScreen.Choice> list = new ArrayList<>();
        if (arena == null) return list;
        for (var kit : arena.kits) list.add(new ChoiceScreen.Choice(kit.id, kit.id, "role", kit.id));
        return list;
    }

    /** Class picker, so the deploy screen can switch class without leaving the battlefield view. */
    void pickClass() {
        if(arena==null||arena.kits.isEmpty()){warn("当前地图没有可用兵种");return;}
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        for(var kit:arena.kits)choices.add(new ChoiceScreen.Choice(kit.id,kit.id,"role",kit.id));
        pick("选择兵种",choices,id->{selectKit(id);rebuild();});
    }

    /** Squad picker, shared by the side panel and the fullscreen deploy bar. */
    void pickSquad() {
        int squad=Engine.num(me(),"squad",0);
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("0","退出小队",""));
        if(Client.state.has("squads"))for(var item:Client.state.getAsJsonArray("squads")){
            var one=item.getAsJsonObject();int id=Engine.num(one,"id",0),count=Engine.num(one,"count",0);
            if(count<SquadRules.CAPACITY||id==squad)choices.add(new ChoiceScreen.Choice(""+id,
                    SquadRules.label(id)+" · "+count+"/4 · "+Engine.str(one,"members",""),""));
        }
        pick("加入小队（待部署时可切换）",choices,id->{var request=Client.request("squad",selected);request.addProperty("squad",Integer.parseInt(id));Client.send(request);});
    }

    /**
     * Items this client knows are throwables, beyond whatever the kit author listed.
     *
     * <p>LRTactical grenades are addressed by their own bridge ids, and Superb Warfare ships ordinary items
     * whose registry path is the only hint that they are thrown. Both are offered on a firing range so a
     * player can test them without an operator editing the map.
     */
    List<String> detectedThrowables() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (Client.catalog.has("lrtEntries")) {
            for (var value : Client.catalog.getAsJsonArray("lrtEntries")) {
                var e = value.getAsJsonObject();
                if (Engine.str(e, "category", "").equals("throwable")) out.add(Engine.str(e, "id", ""));
            }
        }
        for (ItemCatalog.Entry entry : ItemCatalog.all()) {
            String id = entry.id();
            if (!id.startsWith("superbwarfare:")) continue;
            String path = id.substring(id.indexOf(':') + 1);
            if (path.matches(".*(grenade|bomb|c4|rpg|rocket|missile|throwable|molotov|dynamite).*")) out.add(id);
        }
        out.remove("");
        return List.copyOf(out);
    }

    /** Knives and other melee items LRTactical publishes, for the same reason as the throwables above. */
    List<String> detectedMelee() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (Client.catalog.has("lrtEntries")) {
            for (var value : Client.catalog.getAsJsonArray("lrtEntries")) {
                var e = value.getAsJsonObject();
                if (Engine.str(e, "category", "").equals("melee")) out.add(Engine.str(e, "id", ""));
            }
        }
        out.remove("");
        return List.copyOf(out);
    }

    /** Throwable picker: whitelist entries plus, on a firing range, any item at all. */
    private void pickThrowable() {
        Model.Kit kit=kit();
        if(kit==null)return;
        boolean training=arena!=null&&arena.isTraining();
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("","无 / 不装备","item"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for(String id:kit.throwableOptions(team())){
            if(id.isEmpty()||!seen.add(id))continue;
            choices.add(new ChoiceScreen.Choice(id, meleeName(id), "item", id));
        }
        if (training) {
            for (String id : detectedThrowables()) {
                if (seen.add(id)) choices.add(new ChoiceScreen.Choice(id, meleeName(id), "item", id));
            }
            choices.add(new ChoiceScreen.Choice("@item", "任意物品…", "item"));
        }
        if(choices.size()<=1){warn("当前兵种没有可用的投掷武器，请在编辑器中添加");return;}
        pick("投掷武器", choices, id -> {
            if("@item".equals(id)){
                minecraft.setScreen(new ItemPickerScreen(this,true,chosen->{
                    if(chosen==null||chosen.isEmpty())return;
                    throwable=chosen;rememberLoadout();rebuild();
                }));
                return;
            }
            throwable=id;rememberLoadout();rebuild();
        });
    }

    /** Tactical slots: the kit's whitelist, plus anything at all on a firing range. */
    private void pickTactical(boolean second) {
        Model.Kit kit = kit();
        if (kit == null) return;
        boolean training = arena != null && arena.isTraining();
        List<ChoiceScreen.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("", "无 / 不装备", "item"));
        for (String id : kit.tacticalChoices()) {
            if (id.isEmpty()) continue;
            choices.add(new ChoiceScreen.Choice(id, meleeName(id), "item", id));
        }
        if (training) choices.add(new ChoiceScreen.Choice("@item", "任意物品…", "item"));
        pick(second ? "战术道具 2" : "战术道具 1", choices, id -> {
            if ("@item".equals(id)) {
                minecraft.setScreen(new ItemPickerScreen(this, true, chosen -> {
                    if (chosen == null || chosen.isEmpty()) return;
                    if (second) tactical2 = chosen; else tactical1 = chosen;
                    rememberLoadout(); rebuild();
                }));
                return;
            }
            if (second) tactical2 = id; else tactical1 = id;
            rememberLoadout(); rebuild();
        });
    }

    private void weapon(Model.Kit kit, boolean main) {
        if(kit==null){warn("兵种数据尚未就绪，请先选择兵种");return;}
        // A firing range offers every scanned gun; a normal map still offers only the kit's list.
        java.util.List<String> ids = arena != null && arena.isTraining()
                ? allGuns()
                : kit.options(main,team()).stream().filter(id -> !id.isEmpty()).toList();
        // The catalog may not have arrived yet: fall back to the kit's own list rather than showing nothing.
        if (ids.isEmpty()) ids = kit.options(main,team()).stream().filter(id -> !id.isEmpty()).toList();
        List<ChoiceScreen.Choice> choices = ids.stream()
                .map(id -> new ChoiceScreen.Choice(id, name(id), "gun"))
                .toList();
        if (choices.isEmpty()) {
            warn("该兵种没有授权" + (main ? "主" : "副") + "武器，请在编辑器中添加");
            return;
        }
        pick((main ? "主武器库" : "副武器库") + (arena != null && arena.isTraining() ? "（靶场）" : ""), choices, id -> {
            if (main) primary = id;
            else secondary = id;
            rebuild();
        });
    }

    /** Melee display name: guns resolve through the synced name table, items through the registry. */
    String meleeName(String id) {
        if (id == null || id.isEmpty()) return "无";
        if (Client.state.has("names") && Client.state.getAsJsonObject("names").has(id)) return name(id);
        String n = KitItems.name(id);
        return n == null || n.isEmpty() || n.equals(id) ? Catalog.prettify(id) : n;
    }

    private void pickMelee() {
        Model.Kit kit=kit();if(kit==null)return;
        var choices=new ArrayList<ChoiceScreen.Choice>();choices.add(new ChoiceScreen.Choice("","无 / 不装备","item"));
        if(arena!=null&&arena.isTraining()){
            // Firing range: any scanned gun or any item, not only the whitelist.
            for(String id:allGuns())choices.add(new ChoiceScreen.Choice(id,name(id),"gun"));
            // LRTactical publishes its knives as bridge ids, which never appear in a plain item list.
            for(String id:detectedMelee())choices.add(new ChoiceScreen.Choice(id,meleeName(id),"item"));
            choices.add(new ChoiceScreen.Choice("@item","任意物品…","item"));
        } else {
            for(String id:kit.meleeOptions(team()))choices.add(new ChoiceScreen.Choice(id,meleeName(id),ItemCatalog.isGun(id)?"gun":"item"));
        }
        minecraft.setScreen(new ChoiceScreen(this,"地图授权近身武器",choices,id->{
            if("@item".equals(id)){
                minecraft.setScreen(new ItemPickerScreen(this,true,chosen->{
                    if(chosen==null||chosen.isEmpty())return;
                    melee=chosen;rememberLoadout();rebuild();
                }));
                return;
            }
            melee=id;rememberLoadout();rebuild();
        }));
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

    /** Firing-range live re-arm: send the current loadout and have the server re-issue it without a respawn. */
    private void rearm() {
        JsonObject request=Client.request("trainingLoadout",selected);
        request.addProperty("kit",kitId);
        request.addProperty("primary",first(kit(),true));
        request.addProperty("secondary",first(kit(),false));
        request.addProperty("melee",melee);
        request.addProperty("throwable",throwable);
            request.addProperty("tactical1",tactical1);
            request.addProperty("tactical2",tactical2);
        for (var entry : Client.deployedAmmo.entrySet()) request.addProperty("ammo_" + entry.getKey(), entry.getValue());
        request.add("trainingItems",BattlefieldOps.GSON.toJsonTree(trainingItems));
        Client.send(request);
        say("正在切换配装…");
    }

    private void deploy() {
        JsonObject request = Client.request("deploy", selected);
        request.addProperty("kit", kitId);
        request.addProperty("spawn", spawn);
        request.addProperty("vehicle", vehicle);
        request.addProperty("primary", first(kit(),true));
        request.addProperty("secondary", first(kit(),false));
        request.addProperty("melee", melee);
        request.addProperty("throwable", throwable);
        request.addProperty("tactical1", tactical1);
        request.addProperty("tactical2", tactical2);
        Model.Kit selectedKit=kit();
        request.add("primaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,true))));
        request.add("secondaryFittings",BattlefieldOps.GSON.toJsonTree(fitting("gun",first(selectedKit,false))));
        request.add("vehicleFittings",BattlefieldOps.GSON.toJsonTree(fitting("vehicle",vehicle)));
        if (arena != null && arena.isTraining()) request.add("trainingItems", BattlefieldOps.GSON.toJsonTree(trainingItems));
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
        primary(f,"服务器游戏大厅",true,LobbyScreen::open);
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
            String state = Engine.str(entry,"modeName","")+" · "+(running ? "对局中" : room ? "已建房" : "未建房");
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
                f.paragraph("每张地图只能有一个房间。进入后即可选择红机械轴心。", Ui.TEXT_FAINT);
            } else {
                primary(f, "创建本图房间", true, () -> send("createRoom"));
                f.paragraph("每张地图只能创建一个房间；同一维度内边界重叠的地图也无法同时开局。",
                        Ui.TEXT_FAINT);
            }
            if (flag("canManageRoom") && room) row(f, "关闭房间", "结束对局", () -> minecraft.setScreen(new ConfirmActionScreen(this,"关闭并结算房间？","将结束当前房间，结算其中所有玩家；其他房间不受影响。","关闭并结算",()->send("stop"))));
            return;
        }

        f.caption("当前阵营  " + teamName(), team() < 0 ? Ui.TEXT_WARN
                : team() == 0 ? Ui.BLUE : Ui.RED);
        if (team() < 0) {
            primary(f, "选择 "+Factions.name(arena,0)+" / 进攻方", true, () -> chooseTeam(0));
            primary(f, "选择 "+Factions.name(arena,1)+" / 防守方", true, () -> chooseTeam(1));
        }
        if (inMatch()) primary(f, "前往部署", true, () -> { tab = 0; rebuild(); });
        row(f,"退出房间","返回游戏大厅",LobbyScreen::open);
        if (flag("canManageRoom")) {
            if (!running) row(f, "开始本局", "锁定阵营", () -> send("start"));
            row(f, "关闭房间", "结束并结算", () -> minecraft.setScreen(new ConfirmActionScreen(this,"关闭并结算房间？","将结束当前房间，结算其中所有玩家；其他房间不受影响。","关闭并结算",()->send("stop"))));
        }
    }

    // ------------------------------------------------------------------ tab 2: stats

    private void buildStats() {
        rightTitle = "本局统计";
        // A real control, not a painted rectangle. The out-of-match state needs a way to reach the full
        // leaderboard from here, and drawing a button-shaped fill that does nothing is worse than
        // drawing nothing: it is the single most common way a screen reads as broken. The button lives
        // in the same content pane the summary text is painted into, anchored to its bottom-left so it
        // never overlaps the rows.
        leftForm = newForm(Ui.headerBody(layout.content));
        leftForm.skip(Math.max(0, leftForm.free() - 18));
        row(leftForm, "打开详细排行榜", "支持对局外查看 · 显示本服记录",
                () -> NativeKillIcons.openScoreboard(this));
        leftForm.finish();
    }

    private void paintStats(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.header(g, font, layout.content, "本局统计");
        Layout.Rect body = Ui.headerBody(layout.content);
        JsonArray scores = Client.state.has("scores") ? Client.state.getAsJsonArray("scores") : new JsonArray();
        // Out of a match there is no live roster, but refusing to draw anything threw away the one thing
        // this page is for. It now shows the same empty state the detailed leaderboard uses and points at it,
        // instead of a dead "只有在本局内的玩家可以查看计分板" line that described a code path rather than a
        // rule.
        if (!inMatch() || scores.isEmpty()) {
            // Text only. The button that opens the leaderboard is a real widget built by buildStats and
            // painted by Form; a painted rectangle here would sit underneath it and do nothing.
            String hint = inMatch()
                    ? "本局暂未发布战绩。分数在有人得分后由服务器推送。"
                    : "当前不在对局内。离开对局前推送的战绩会保留在详细排行榜里。";
            int y = body.y() + 3;
            for (var line : font.split(net.minecraft.network.chat.Component.literal(hint), Math.max(1, body.w() - 10))) {
                if (y + 9 > body.bottom()) break;
                g.drawString(font, line, body.x() + 5, y, Ui.TEXT_DIM, false);
                y += 10;
            }
            return;
        }
        Model.Arena arena=FactionArt.arena();
        int y=body.y()+3;
        g.drawString(font,Ui.clip(font,MatchScore.line(arena,Client.state),body.w()-10),body.x()+5,y,Ui.TEXT,false);
        y+=12;
        if(!arena.isStaged()){
            int blue=MatchScore.value(arena,Client.state,0),red=MatchScore.value(arena,Client.state,1);
            int most=arena.isDeathmatch()?MatchScore.maximum(arena):Math.max(1,Math.max(blue,red));
            int half=Math.max(1,(body.w()-12)/2);
            Ui.bar(g,new Layout.Rect(body.x()+5,y,half,3),blue/(double)most,Ui.BLUE,Ui.PANEL_SUNKEN);
            Ui.bar(g,new Layout.Rect(body.x()+7+half,y,half,3),red/(double)most,Ui.RED,Ui.PANEL_SUNKEN);
            y+=6;
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
        if(dropdownOpen())return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
        if(fullBattlefield()){
            if(overviewRight!=null&&overviewRight.wheel(mouseX,mouseY,vertical))return true;
            // Wheel zooms the overhead deploy camera: larger height = zoomed out.
            if(vertical!=0)Client.overviewZoom=Math.max(0.25,Math.min(4.0,Client.overviewZoom*(vertical>0?0.88:1.14)));
            return true;
        }
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

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(fullBattlefield()&&button==0){
            var geometry=new Bf1Layout(width,height);
            if(geometry.weapon.inside(mouseX,mouseY)||geometry.loadout.inside(mouseX,mouseY)||geometry.options.inside(mouseX,mouseY)||geometry.squad.inside(mouseX,mouseY)||geometry.deploy.inside(mouseX,mouseY))return super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
            // Drag pans the overhead camera across the ground. At pitch +90/yaw 0 screen-right is world -X
            // and screen-down is world -Z, so the offsets invert the drag on both axes.
            double blocksPerPixel=0.5*Client.overviewZoom;
            Client.overviewPanX-=dragX*blocksPerPixel;
            Client.overviewPanZ-=dragY*blocksPerPixel;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(fullBattlefield()){
            DeployMarkers.draw(graphics,minecraft);
            Bf1Ui.deployShade(graphics,width,height);
            // Top-left: the faction the player is on, with its flag.
            int side=team();
            String faction=Ui.clip(font,side<0?"未选择阵营":Factions.name(arena,side),Math.max(40,width/4-30));
            int factionColor=side<0?Ui.TEXT_DIM:side==0?Ui.BLUE:Ui.RED;
            graphics.fill(8,8,8+font.width(faction)+24,24,0x90101820);
            Bf1Ui.icon(graphics,FactionArt.icon(side),12,9,16);
            graphics.drawString(font,faction,28,14,factionColor,false);
            // Top-right: map name in large type, mode underneath in dim small type.
            if(arena!=null){
                float mapScale=width>=620?1.6f:1f;
                String mapName=Ui.clip(font,arena.name,Math.max(40,(int)((width/4-16)/mapScale))),modeName=arena.modeName();
                graphics.pose().pushPose();
                try{
                    float scale=mapScale;
                    int textW=(int)(font.width(mapName)*scale);
                    graphics.pose().translate(width-10-textW,4,200);
                    graphics.pose().scale(scale,scale,1);
                    graphics.drawString(font,mapName,0,0,Ui.TEXT,false);
                }finally{graphics.pose().popPose();}
                graphics.drawString(font,modeName,width-10-font.width(modeName),22,Ui.TEXT_DIM,false);
            }
            // Battlefield-style score strip along the top: both ticket counts and the round clock.
            if(arena!=null){
                int[] tickets={MatchScore.value(arena,Client.state,0),MatchScore.value(arena,Client.state,1)};
                int friendly=team()==1?1:0,enemy=1-friendly;
                long remaining=Math.max(0,arena.roundMinutes*60L-Engine.num(Client.state,"elapsed",0));
                String clock=String.format(java.util.Locale.ROOT,"%02d:%02d",remaining/60,remaining%60);
                int center=width/2,barWidth=Math.max(24,Math.min(72,width/10)),scoreY=width<620?30:4;
                String left=String.valueOf(tickets[friendly]),right=String.valueOf(tickets[enemy]);
                // Symmetric: [score][bar]  clock  [bar][score], both bars growing outward from the centre.
                int leftBarX=center-24-barWidth,rightBarX=center+24;
                graphics.fill(leftBarX-6,scoreY-2,rightBarX+barWidth+6,scoreY+16,0x90101820);
                graphics.drawString(font,left,leftBarX-6-font.width(left),scoreY+3,0xff9bc3ff,true);
                CombatHud.scoreBar(graphics,leftBarX,scoreY+5,barWidth,tickets[friendly]/(double)MatchScore.maximum(arena),Ui.BLUE,true,true);
                graphics.drawCenteredString(font,clock,center,scoreY+2,0xffe4edf2);
                CombatHud.scoreBar(graphics,rightBarX,scoreY+5,barWidth,tickets[enemy]/(double)MatchScore.maximum(arena),Ui.RED,false,true);
                graphics.drawString(font,right,rightBarX+barWidth+6,scoreY+3,0xffff9b9b,true);
                if(width>=620)graphics.drawCenteredString(font,"先达到 "+MatchScore.maximum(arena)+" 分获胜",center,21,0xffa8b0b8);
            }
            // Only a real countdown or an error is worth a status line; the deploy action is the button now.
            String status=awaitingDeploy?"正在部署…":respawnRemaining()>0?"部署倒计时 "+respawnRemaining()+" 秒":!flag("roomRunning")?"等待房主开始对局":"";
            boolean error=message.startsWith("操作失败");
            if(error)status=message;
            if(!status.isEmpty()){
                int statusY=width<620?48:28;
                graphics.fill(width/2-150,statusY,width/2+150,statusY+16,0xc010181f);
                graphics.drawCenteredString(font,Ui.clip(font,status,292),width/2,statusY+4,error?Ui.TEXT_ERROR:Ui.TEXT);
            }
            // Battlefield keeps the squad list along the bottom-left of the deploy screen.
            var roster=SquadRoster.rows(Client.state);
            if(!roster.isEmpty()){
                int squad=Client.state.has("me")?Engine.num(Client.state.getAsJsonObject("me"),"squad",0):0;
                // Same clean row style as the HUD: no "· 你", no squad header; nothing when unassigned.
                var geometry=new Bf1Layout(width,height);
                int rosterY=geometry.squad.bottom()+4;
                int visibleRows=Math.max(0,Math.min(roster.size(),(geometry.loadout.y()-rosterY-2)/14));
                SquadRoster.drawAt(graphics,font,roster.subList(0,visibleRows),10,rosterY,squad);
            }
            if(overviewSquad!=null)overviewSquad.render(graphics,mouseX,mouseY,partialTick);
            if(overviewLoadout!=null)overviewLoadout.render(graphics,mouseX,mouseY,partialTick);
            if(overviewRight!=null)overviewRight.render(graphics,mouseX,mouseY,partialTick);
            if(overviewLeave!=null)overviewLeave.render(graphics,mouseX,mouseY,partialTick);
            if(overviewDeploy!=null)overviewDeploy.render(graphics,mouseX,mouseY,partialTick);
            return;
        }
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

        // In live-overview mode the world renders full-screen behind this screen, and the deploy markers
        // are projected against that full-screen camera. They must therefore be drawn full-screen — before
        // the opaque control column — rather than scissored to the right pane: scissoring to the pane
        // clipped every marker that projected to the screen centre (which is between the two panes), while
        // pickSpawn still hit-tested them against the full screen, producing markers that were clickable
        // but invisible. Drawing them under the UI restores the invariant that a marker you can see is one
        // you can click, and a marker you cannot see is covered by a panel.
        if (worldBackdrop()) {
            DeployMarkers.draw(graphics, minecraft);
        }

        Ui.header(graphics, font, leftPanel, header);
        graphics.enableScissor(leftForm.area().x(), leftForm.area().y(),
                leftForm.area().right(), leftForm.area().bottom());
        leftForm.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        Layout.Rect body;
        if(tab==0 && DeploymentLock.room(Client.state) && !alive()) {
            graphics.fill(rightPanel.x(),rightPanel.y(),rightPanel.right(),rightPanel.y()+20,0xe0101c28);
            graphics.drawString(font,tacticalMap?"切换至实时俯视 ▸":"切换至战术地图 ▸",rightPanel.x()+8,rightPanel.y()+6,0xffd8dde0,false);
        }
        if(worldBackdrop()) {
            graphics.drawString(font,"实时战场 · 选中目标后按空格",rightPanel.x()+6,rightPanel.y()+28,0xffd8dde0,true);
            if(!Client.Events.overviewWarning.isEmpty())graphics.drawString(font,Client.Events.overviewWarning,rightPanel.x()+6,rightPanel.y()+40,Ui.TEXT_WARN,true);
            return;
        }
        body = Ui.header(graphics, font, rightPanel, rightTitle);
        if (arena == null) {
            graphics.drawCenteredString(font, "暂无地图", body.cx(), body.cy() - 4, Ui.TEXT_FAINT);
            return;
        }
        drawMap(graphics, body);
        if(tab==0 && DeploymentLock.room(Client.state) && !alive()) {
            graphics.fill(rightPanel.x(),rightPanel.y(),rightPanel.right(),rightPanel.y()+20,0xe0101c28);
            graphics.drawString(font,"切换至实时俯视 ▸",rightPanel.x()+8,rightPanel.y()+6,0xffd8dde0,false);
        }
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
        map.showGrid = false;
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
        map.deploymentSelection=tab==0&&!alive()?spawn:"";
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
    private boolean deployReady;
    private boolean spaceArmed;
    @Override public void tick(){
        super.tick();
        if(!com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE))spaceArmed=true;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)spaceArmed=true;
        return super.keyReleased(key,scan,modifiers);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(dropdownOpen())return super.keyPressed(key,scan,modifiers);
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && tab==0 && inMatch()
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox)){
            if(spaceArmed&&!alive()&&deployReady&&respawnRemaining()==0&&!awaitingDeploy)deploy();
            spaceArmed=false;
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }


    private void buildBar() {
        Layout.Rect bar = layout.bar;
        if (bar.h() <= 0) {
            barForm = null;
            return;
        }
        barForm = newForm(bar.inset(2));
        // A firing range gets a dedicated re-arm action in the always-visible bottom bar, so it can be
        // found without scrolling the side panel.
        boolean training = tab == 0 && arena != null && arena.isTraining();
        Layout.Rect[] cells = Layout.columns(bar.inset(2), training ? 4 : 3, 3);
        int cell = 0;
        if (training) place(barForm, cells[cell++], "切换配装（靶场）", "", "", "", true, this::rearm);
        barForm.skip(0);
        place(barForm, cells[cell++], DeploymentLock.required(Client.state)?"离开房间":"关闭", "", "", "", true,
                ()->{if(DeploymentLock.required(Client.state))send("leave");else onClose();});
        place(barForm, cells[cell++], "刷新", "", "", "", true, () -> send("state"));
        place(barForm, cells[cell++], "编辑器", "", "", "", admin(),
                () -> minecraft.setScreen(new OpsScreen()));
        barForm.finish();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {if(dropdownOpen())return super.mouseClicked(mouseX,mouseY,button);
        if(fullBattlefield()){
            if(super.mouseClicked(mouseX,mouseY,button))return true;
            var geometry=new Bf1Layout(width,height);
            // Disabled widgets and spaces between controls must not select a marker behind the UI.
            if(geometry.weapon.inside(mouseX,mouseY)||geometry.loadout.inside(mouseX,mouseY)||geometry.options.inside(mouseX,mouseY)
                    ||geometry.squad.inside(mouseX,mouseY)||geometry.deploy.inside(mouseX,mouseY))return true;
            if(button==0&&mouseY>layout.title.bottom()+34&&mouseY<height-42){
                String picked=pickSpawn(new Layout.Rect(0,0,width,height),mouseX,mouseY);
                if(picked!=null){selectSite(picked);rebuild();return true;}
            }
            return false;
        }
        if(button==0 && tab==0 && DeploymentLock.room(Client.state) && !alive() && rightPanel!=null
                && mouseX>=rightPanel.x() && mouseX<rightPanel.right()
                && mouseY>=rightPanel.y() && mouseY<rightPanel.y()+20) {
            tacticalMap=!tacticalMap;
            return true;
        }
        if (button == 0 && tab == 0 && arena != null) {
            // In live-overview mode the markers are drawn full-screen (the world is full-screen), so the
            // clickable area is everything right of the control column — not just the right pane. Scoping
            // to the right pane here would reproduce the very inconsistency this round fixed: a marker you
            // can see but cannot click.
            boolean clickable = worldBackdrop()
                    ? (mouseX >= leftPanel.right() && mouseY >= leftPanel.y() && mouseY < leftPanel.bottom())
                    : Ui.headerBody(rightPanel).inside(mouseX, mouseY);
            if (clickable) {
                Layout.Rect body = Ui.headerBody(rightPanel);
                String picked = pickSpawn(body, mouseX, mouseY);
                if (picked != null) {
                    selectSite(picked);
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
        // The tactical map may be rotated; hit-test in north-up coordinates. The live-overview path is a
        // full-screen 3D projection, not a rotated 2D map, so its mouse position stays raw.
        double[] um = worldBackdrop() ? new double[]{mouseX, mouseY} : map.unrotate(body, mouseX, mouseY);
        String best = null;
        double bestDistance = 16;
        for (Deploy.Site site : sites) {
            var at=worldBackdrop()?Projector.project(minecraft,Projector.camera(minecraft),site.x(),site.y()+2,site.z(),1):null;
            if(worldBackdrop()&&at==null)continue;
            double distance = Math.hypot((worldBackdrop()?at.x():map.sx(body, site.x())) - um[0],
                    (worldBackdrop()?at.y():map.sz(body, site.z())) - um[1]);
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
