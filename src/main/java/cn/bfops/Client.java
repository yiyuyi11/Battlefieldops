package cn.bfops;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Client glue: one key binding, one packet receiver and a minimal HUD.
 *
 * <p>The client keeps no authoritative state. It caches the last server snapshot for rendering and
 * forwards user intent; every decision (who may deploy, with what kit, on which spawn, and what the
 * score is) is made by {@link Engine} on the server.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class Client {
    /** Ammunition type chosen per gun on the deploy screen; carried by the next request. */
    static final java.util.Map<String,String> deployedAmmo = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Opens the deployment / map editing GUI.
     *
     * <p>F4 rather than the more obvious {@code B}: in the target pack {@code B} is already taken by
     * Xaero's "new waypoint", by the fxntstorage backpack and by vsie's force assist, so a B binding
     * would either fight them or be silently overridden. {@code F4} through {@code F10} and
     * {@code F12} are unbound in that pack, and F4 sits next to the other F-key HUD toggles where a
     * player already looks.
     */
    static final KeyMapping ELITE_PICKUP = new KeyMapping("key.battlefield_ops.elite_pickup",
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F12,"key.categories.battlefield_ops");
    static final KeyMapping OPEN = new KeyMapping("key.battlefield_ops.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.battlefield_ops");

    static final KeyMapping MINIMAP_SETTINGS=new KeyMapping("key.battlefield_ops.minimap_settings",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F7,"key.categories.battlefield_ops");
    static final KeyMapping MINIMAP_ZOOM_IN=new KeyMapping("key.battlefield_ops.minimap_zoom_in",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_KP_ADD,"key.categories.battlefield_ops");
    static final KeyMapping MINIMAP_ZOOM_OUT=new KeyMapping("key.battlefield_ops.minimap_zoom_out",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_KP_SUBTRACT,"key.categories.battlefield_ops");

    /**
     * Deploy-camera zoom and pan.
     *
     * <p>{@code overviewZoom} scales the overhead camera's height (larger = zoomed out), and
     * {@code overviewPanX/Z} offset its ground point in world blocks. They live here because the camera
     * framing runs from the client tick, while the input that changes it arrives in the deploy screen.
     */
    static double overviewZoom = 1.0, overviewPanX = 0, overviewPanZ = 0;

    /** Held to read the full scoreboard, exactly like Battlefield's hold-to-view. */
    static final KeyMapping SCORES = new KeyMapping("key.battlefield_ops.scores",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.battlefield_ops");

    /**
     * Cycles the HUD minimap between the four screen corners.
     *
     * <p>A keybind rather than only an editor row because the corner is a per-player display choice, and the
     * player is the one looking at it. The editor still sets the server-wide default for people who never
     * touch this.
     */
    static final KeyMapping MINIMAP_CORNER = new KeyMapping("key.battlefield_ops.minimap_corner",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.battlefield_ops");

    /**
     * Marks what is in front of the player for the team: an enemy under the crosshair, or the nearest
     * teammate when there is none.
     *
     * <p>The client sends only the intent. The server decides who was being aimed at, whether they were
     * visible and how long the mark lasts — a client-supplied position would let anyone mark enemies they
     * cannot see.
     */
    static final KeyMapping MARK = new KeyMapping("key.battlefield_ops.mark",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.battlefield_ops");

    /**
     * Cycles the kill-icon art between the reference pack's style families.
     *
     * <p>A keybind rather than only a config row because it is a taste decision the player wants to settle
     * in one glance at the HUD: press it, watch the feed change, stop when it looks right. A config file
     * that needs a restart to see the result cannot answer that.
     */
    static final KeyMapping KILL_ICON_STYLE = new KeyMapping("key.battlefield_ops.kill_icon_style",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.battlefield_ops");

    /**
     * Opens the preset list.
     *
     * <p>F9 cycles and this opens the picker, because the two answer different questions: "show me the next
     * look" and "show me all of them". Cycling through 37 presets to find one is not a way to find anything,
     * which is why the list exists.
     */
    static final KeyMapping KILL_ICON_PICKER = new KeyMapping("key.battlefield_ops.kill_icon_picker",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.battlefield_ops");

    /**
     * Cycles audio: SFX+voice+music on, then SFX only, then everything off.
     *
     * <p>One key with three states rather than three keys, because the usual reason to press it is "something
     * is too loud right now" and the player wants it quieter in one gesture. Music first, then voice, then
     * everything: that is the order in which people switch things off.
     */
    static final KeyMapping AUDIO = new KeyMapping("key.battlefield_ops.audio",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories.battlefield_ops");

    /** Hides or shows the HUD minimap, without touching the rest of the HUD. */
    static final KeyMapping MINIMAP_TOGGLE = new KeyMapping("key.battlefield_ops.minimap_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.battlefield_ops");

    /** Toggles the map between north-up and rotating with the player's facing. */
    static final KeyMapping MAP_ROTATION = new KeyMapping("key.battlefield_ops.map_rotation",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.battlefield_ops");

    static JsonObject state = new JsonObject(), catalog = new JsonObject();

    /** Wall clock before which the client keeps the death view rather than opening the deploy map. */
    static long deathHoldUntil;

    /** True while the reference's death sequence should still own the screen. */
    static boolean holdingDeathView() {
        return deathHoldUntil > 0 && System.currentTimeMillis() < deathHoldUntil;
    }

    /** Space is the "I have read this" key: it lets the deploy map open immediately. */
    static void releaseDeathHold() {
        deathHoldUntil = 0;
    }
    static JsonObject lobby=new JsonObject();
    private static long snapshotNanos=System.nanoTime();
    static long snapshotElapsed(){return Math.max(0,(System.nanoTime()-snapshotNanos)/1_000_000L);}

    /** Floating award feed, newest first. */
    static java.util.List<Feed.Popup> awards = new java.util.ArrayList<>();
    /** When the last hit landed, and for how much, for the crosshair marker. */
    static long hitMarkerAt;
    static int hitMarkerDamage;
    /** How long the hit marker stays visible. Short enough to read as a flash, not a badge. */
    static final long HIT_MARKER_MS = 180;
    static final java.util.Map<String,String[]> lobbyLoadouts=new java.util.LinkedHashMap<>();
    static final java.util.Map<String,String[]> loadoutMemory=new java.util.LinkedHashMap<>();
    private static boolean downedJumpArmed,wasDowned;
    public static boolean inBattleRoom(){return state.has("inMatch")&&state.get("inMatch").getAsBoolean();}
    public static boolean tacticalCommunicationAllowed(){
        return inBattleRoom()&&state.has("me")&&Engine.num(state.getAsJsonObject("me"),"team",-1)>=0
                &&state.getAsJsonObject("me").has("alive")&&state.getAsJsonObject("me").get("alive").getAsBoolean()
                &&!state.getAsJsonObject("me").get("downed").getAsBoolean();
    }
    public static boolean tacticalVoiceAllowed(){
        return inBattleRoom()&&state.has("me")&&Engine.num(state.getAsJsonObject("me"),"team",-1)>=0
                &&state.getAsJsonObject("me").has("alive")&&state.getAsJsonObject("me").get("alive").getAsBoolean();
    }
    public static void tacticalMark(){if(tacticalVoiceAllowed()&&state.getAsJsonObject("me").get("downed").getAsBoolean())send(request("rescueCall",currentMap()));else if(tacticalCommunicationAllowed())send(request("mark",currentMap()));}
    static final java.util.Map<String,java.util.Map<String,String>> fittingMemory=new java.util.LinkedHashMap<>();

    /**
     * The baked terrain overview for the map currently being viewed, or {@code null} before it arrives.
     *
     * <p>This is the answer to "areas outside the player's view distance do not display": the client only
     * holds chunks the server has sent it, so anything further away simply is not there to draw. The
     * server bakes the whole map once and this holds the result, which every map widget then uses as its
     * fallback layer. Live chunk sampling stays first choice, because it is sharper and reflects changes.
     */
    static TerrainMap baked;
    /** Which map {@link #baked} belongs to. */
    private static String bakedFor = "";
    /** Throttle so a screen that repaints cannot spam the server with terrain requests. */
    private static long bakedAskedAt;
    /** True while the server has told us a bake is running, so the UI can say "preparing". */
    static boolean bakedPending;
    /** Map whose bake failed, and when, so repeated requests back off instead of looping. */
    private static String bakedFailedFor = "";
    private static long bakedFailedAt;

    /**
     * Asks the server for a map's baked terrain, at most once every couple of seconds.
     *
     * <p>Called from the periodic poll of each screen that draws a map. The throttle matters as much as
     * the request: without it, every repaint of a screen waiting on a bake would send another packet.
     */
    /** The operator's minimap corner from the last snapshot, or the default. */
    static String serverMinimapCorner() {
        if (!state.has("hud") || !state.get("hud").isJsonObject()) return "TOP_LEFT";
        return Engine.str(state.getAsJsonObject("hud"), "minimapCorner", "TOP_LEFT");
    }

    static void wantTerrain(String mapId) {
        if (mapId == null || mapId.isEmpty()) return;
        if (baked != null && mapId.equals(bakedFor)) return;
        long now = System.currentTimeMillis();
        // After a failure, stop asking for a while. A bake can fail because the map's dimension is not loaded
        // or its data is unreadable, and retrying every two seconds would keep re-running a failing bake for
        // as long as the map is open.
        if (mapId.equals(bakedFailedFor) && now - bakedFailedAt < 30_000) return;
        if (now - bakedAskedAt < 2000) return;
        bakedAskedAt = now;
        send(request("terrain", mapId));
    }

    /** Clears the cache, e.g. on logout or when the viewed map changes. */
    static void forgetTerrain() {
        baked = null;
        bakedFor = "";
        bakedPending = false;
        bakedAskedAt = 0;
        bakedFailedFor = "";
        bakedFailedAt = 0;
    }


    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);event.register(MINIMAP_SETTINGS);event.register(MINIMAP_ZOOM_IN);event.register(MINIMAP_ZOOM_OUT);
        event.register(ELITE_PICKUP);
        event.register(SCORES);
        event.register(MINIMAP_CORNER);
        event.register(MINIMAP_TOGGLE);
        event.register(MARK);
        event.register(MAP_ROTATION);
        event.register(KILL_ICON_STYLE);
        event.register(KILL_ICON_PICKER);
        event.register(AUDIO);
        // The 656 leaderboard's own key. It lived in KeyBindings but was never registered with the
        // game, so pressing its bound key did nothing at all; registering it here is what makes
        // "open the standings with a key, from any screen or no screen" true.
        for (var mapping : cn.bfops.killicon.client.KeyBindings.all()) event.register(mapping);
    }

    @SubscribeEvent
    public static void referenceReload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{Minecraft.getInstance().execute(()->{FactionArt.clear();AssetIcons.clear();NativeAudioBank.clear();NativeBattleAudio.reset();});ReferenceProps.clear();GearNames.clear();EquipmentStatistics.clear();BoundaryEffect.reset();NearMissEffect.reset();MusicDirector.silence();});
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {Network.receiver = Client::receive;Network.modeReceiver=Client::mode;LoadoutProfiles.load();LobbyAccess.clientOpen=()->{if(serverSupported())LobbyScreen.open();};NativeKillIcons.init();});
    }

    /**
     * The sound cue for an award icon kind.
     *
     * <p>Kept next to the receiver rather than in {@link SoundBank} because it maps this mod's award
     * vocabulary (which is what the server sends) onto the reference pack's clip vocabulary. Putting it in
     * {@code SoundBank} would mix "what a cue is called" with "which award uses it", and the award set
     * changes whenever {@link Honours} does.
     */
    private static String cueFor(String icon) {
        return switch (icon == null ? "" : icon) {
            case "headshot" -> "kill";
            case "explosion", "vehicle", "vehicle_assist" -> "kill";
            case "capture" -> "capture";
            case "medic" -> "heal";
            default -> "kill";
        };
    }

    static void receive(String json) {        try {
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            Minecraft mc = Minecraft.getInstance();
            if(Engine.str(payload,"type","").equals("mapSaveError")){
                if(mc.screen instanceof OpsScreen screen)screen.saveError(Engine.str(payload,"message","地图保存失败"));return;
            }
            if(Engine.str(payload,"type","").equals("medic")){
                if(state.has("me")&&state.getAsJsonObject("me").has("downed")&&state.getAsJsonObject("me").get("downed").getAsBoolean())
                    for(String key:new String[]{"downLeft","reviveProgress","giveUpProgress","respawnRemaining"})
                        if(payload.has(key))state.add(key,payload.get(key));
                return;
            }
            if(Engine.str(payload,"type","").equals("editorFlight")){QuickMapEdit.receive(payload.has("active")&&payload.get("active").getAsBoolean());return;}
            if(Engine.str(payload,"type","").equals("leaderboard")){NativeKillIcons.careerLeaderboard(payload);return;}
            if(Engine.str(payload,"type","").equals("nearMiss")){NearMissEffect.receive(payload);return;}
            if(Engine.str(payload,"type","").equals("career")){if(mc.screen instanceof CareerScreen screen)screen.receive(payload);return;}
            if (Engine.str(payload, "type", "").equals("catalog")) {
                // LRTactical indices only exist client-side, so the server's catalogue arrives without them.
            LrtEquipment.mergeLocal(payload);
            catalog = payload;
                if (mc.screen instanceof OpsScreen screen) screen.catalogArrived();
                if(mc.screen instanceof LobbyLoadoutScreen screen)screen.catalogUpdated();
                return;
            }
            if(Engine.str(payload,"type","").equals("honour")){NativeKillIcons.receive(payload);return;}
            // Award feed and hit marker: cosmetic, server-authored, drawn over the HUD.
            if (Engine.str(payload, "type", "").equals("bonus")) {
                Feed.Popup popup = new Feed.Popup(
                        Engine.str(payload, "label", "奖励"),
                        Engine.num(payload, "points", 0),
                        System.currentTimeMillis(),
                        Engine.str(payload, "detail", ""),
                        Engine.str(payload, "icon", "kill"));
                awards = Feed.add(awards, popup);
                // Client-side half of the "kill feed not showing" diagnosis. If the server logs the award
                // and this line never appears, the channel dropped it; if both appear and the feed is still
                // invisible, the fault is in the HUD draw path or a screen is covering it.
                BattlefieldOps.LOG.debug("award <- {} ({} in queue)", popup.text(), awards.size());
                // Two presentations of the same event, deliberately: the text feed is the readable running log,
                // and the 656 strip is the animated burst of icons the reference pack is known for. A player who
                // wants neither turns the HUD off; a player who finds one too busy can hide just the strip.
                //
                // Which cue fires depends on what was awarded, not on "an award happened": a headshot, a vehicle
                // kill, a capture and an assist are four different pieces of news, and the reference pack has
                // distinct audio for each. `KillIconStore.trigger` owns both the icon and the sound now, so the
                // two cannot drift apart.
                NativeKillIcons.receive(payload);
                return;
            }
            if (Engine.str(payload, "type", "").equals("hit")) {
                hitMarkerAt = payload.has("nativeMarker") && payload.get("nativeMarker").getAsBoolean() ? 0 : System.currentTimeMillis();
                hitMarkerDamage = Engine.num(payload, "damage", 0);
                NativeKillIcons.hit(payload);
                return;
            }
            if(Engine.str(payload,"type","").equals("lobby")){
                if(Engine.str(payload,"membership","").isEmpty())EntryScene.clear();
                lobby=payload;
                if(mc.screen instanceof LobbyScreen screen)screen.received(payload);
                else if(payload.has("open")&&payload.get("open").getAsBoolean())mc.setScreen(new LobbyScreen());
                return;
            }
            if (Engine.str(payload, "type", "").equals("terrain")) {
                String mapId = Engine.str(payload, "map", "");
                String state = Engine.str(payload, "state", "");
                if (state.equals("READY")) {
                    TerrainMap decoded = TerrainMap.decode(mapId,
                            Engine.str(payload, "signature", ""),
                            Engine.str(payload, "data", ""));
                    if (decoded != null && decoded.valid()) {
                        baked = decoded;
                        bakedFor = mapId;
                        bakedPending = false;
                    }
                } else if (state.equals("BAKING")) {
                    // Leave the previous map's bake in place? No: it belongs to a different map, and
                    // drawing one map's terrain under another is worse than showing the placeholder.
                    if (!mapId.equals(bakedFor)) baked = null;
                    bakedPending = true;
                } else {
                    bakedPending = false;
                    // Remember the failure so wantTerrain backs off instead of hammering the server.
                    bakedFailedFor = mapId;
                    bakedFailedAt = System.currentTimeMillis();
                }
                return;
            }
            if (Engine.str(payload,"type","").equals("saved")) {
                if(mc.screen instanceof OpsScreen screen)screen.saved(payload.getAsJsonObject("arena"));
                return;
            }
            if(Engine.str(payload,"type","").equals("result")){
                MusicDirector.result(Engine.str(payload,"outcome",""));mc.setScreen(new ResultScreen(payload,mc.screen));return;
            }
            if(Engine.str(payload,"type","").equals("attachmentCatalog")){
                if(mc.screen instanceof AttachmentRulesScreen screen)screen.receive(payload);return;
            }
            if(Engine.str(payload,"type","").equals("downedCard")){
                DeathCard.show(payload);return;
            }
            if(Engine.str(payload,"type","").equals("deathStats")){
                NativeKillIcons.deathStats(Engine.str(payload,"killer",""));
                DeathCard.show(payload);return;
            }
            if(Engine.str(payload,"type","").equals("fittings")){
                if(mc.screen instanceof FittingScreen screen)screen.receive(payload);
                if(mc.screen instanceof GunModifyScreen screen)screen.receive(payload);return;
            }
            if(Engine.str(payload,"type","").equals("supportSound")){
                String cue=Engine.str(payload,"cue","");
                if(payload.has("x")&&payload.has("y")&&payload.has("z"))SoundCues.playAt(cue,payload.get("x").getAsDouble(),payload.get("y").getAsDouble(),payload.get("z").getAsDouble());
                else SoundCues.play(cue);return;
            }
            boolean leftMatch=NativeKillIcons.inMatch()&&(!payload.has("inMatch")||!payload.get("inMatch").getAsBoolean());
            // Save the roster while the outgoing state is still readable. This has to happen before
            // `state = payload` replaces the scores array with the new (empty) one, and before
            // clearRoomData drops the in-memory copy.
            if(leftMatch)NativeKillIcons.archiveRoom();
            state = payload;
            if(EntryScene.receive(payload))return;
            boolean deployed=Events.confirmDeployment(mc,payload);
            if(leftMatch){MatchView.leftMatch();ProneControls.reset();BoundaryEffect.reset();NearMissEffect.reset();NativeKillIcons.reset();cn.bfops.killicon.client.gui.tabs.ScoreboardTab.clearRoomData();}
            if(!payload.has("inMatch")||!payload.get("inMatch").getAsBoolean())WorldMarkers.reset();
            snapshotNanos=System.nanoTime();
            if(mc.screen instanceof AttachmentRulesScreen screen&&!Engine.str(payload,"message","").isEmpty())screen.error(Engine.str(payload,"message",""));
            if(mc.screen instanceof FittingScreen screen&&!Engine.str(payload,"message","").isEmpty())screen.error(Engine.str(payload,"message",""));
            if(mc.screen instanceof GunModifyScreen screen&&!Engine.str(payload,"message","").isEmpty())screen.error(Engine.str(payload,"message",""));
            if(mc.screen instanceof LobbyScreen screen){
                screen.roomUpdated(payload);
                if(payload.has("open")&&payload.get("open").getAsBoolean()&&payload.has("me")&&mc.screen==screen)mc.setScreen(new BattleScreen());
            } else if (mc.screen instanceof BattleScreen screen) {
                screen.updated();
            } else if (mc.screen instanceof OpsScreen screen) {
                screen.updated();
            } else if (!(mc.screen instanceof DeathPreviewScreen) && !(mc.screen instanceof GunModifyScreen) && !(mc.screen instanceof FittingScreen) && !(mc.screen instanceof LoadoutScreen) && !(mc.screen instanceof ConfirmActionScreen) && !(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) && !deployed && payload.has("open") && payload.get("open").getAsBoolean()
                    && !holdingDeathView()) {
                mc.setScreen(new BattleScreen());
            }
        } catch (Exception ex) {
            BattlefieldOps.LOG.error("Invalid battlefield UI response", ex);
        }
    }

    static void mode(boolean enabled){
        BattleServerMode.clientActive=enabled;if(!enabled)EntryScene.clear();
        if(enabled)return;
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof OpsScreen editor&&editor.draft!=null)try{
            var folder=mc.gameDirectory.toPath().resolve("battlefield_ops");java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve("mode-off-map-draft.json"),BattlefieldOps.GSON.toJson(editor.draft));
        }catch(java.io.IOException ex){BattlefieldOps.LOG.error("Cannot preserve map draft before mode shutdown",ex);}
        state=new JsonObject();lobby=new JsonObject();awards.clear();Cinematic.clear();MatchView.leftMatch();
        NativeKillIcons.reset();NativeBattleAudio.reset();MusicDirector.silence();ProneControls.reset();BoundaryEffect.reset();NearMissEffect.reset();WorldMarkers.reset();
        if(mc.screen instanceof FrontScreen)mc.setScreen(null);
    }
    static boolean serverSupported() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        var listener = (net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)connection;
        return BattleServerMode.clientActive&&listener.hasChannel(Network.Request.TYPE) && listener.hasChannel(Network.State.TYPE);
    }

    public static void send(JsonObject request) {
        if (!serverSupported()) {
            if (Engine.str(request,"op", "").equals("open") && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal("此服务器未启用战地风云，普通游戏不受影响"), true);
            return;
        }
        String json = request.toString();
        if(Engine.str(request,"op","").equals("save")&&(json.length()>20000||json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>28000)){
            PacketDistributor.sendToServer(new Network.MapSave(MapTransfer.pack(json)));return;
        }
        if (json.length()>20000 || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 28000) {
            if (Minecraft.getInstance().screen instanceof OpsScreen screen) {
                throw new IllegalArgumentException("配置过大，请减少顶点 / 配装 / 载具数量；未提交保存");
            }
            return;
        }
        PacketDistributor.sendToServer(new Network.Request(json));
    }

    public static boolean matchMusicActive(){return MusicDirector.active();}
    public static String currentMap(){return Engine.str(state,"selected","");}
    public static JsonObject request(String op, String map) {
        JsonObject json = new JsonObject();
        json.addProperty("op", op);
        json.addProperty("map", map);
        return json;
    }

    @EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
    public static final class Events {
        /** True while the local player is on the death side of the match (dead, not merely downed elsewhere). */
        static boolean dead() {
            return state.has("inMatch") && state.get("inMatch").getAsBoolean()
                    && state.has("me") && state.getAsJsonObject("me").has("alive")
                    && !state.getAsJsonObject("me").get("alive").getAsBoolean();
        }

        @SubscribeEvent
        public static void corpses(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                CorpseRagdoll.render(event);
            }
        }

        @SubscribeEvent public static void deployTravelMovement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event){
            if(!Cinematic.deployTravelLocked())return;
            var input=event.getInput();
            input.forwardImpulse=0.0F;
            input.leftImpulse=0.0F;
            input.jumping=false;
            input.shiftKeyDown=false;
            var player=event.getEntity();
            player.setDeltaMovement(0,player.getDeltaMovement().y,0);
        }
        @SubscribeEvent public static void downedMovement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event){
            if(CrawlPose.downed(event.getEntity()))event.getInput().jumping=false;
        }
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
        public static void enemyName(net.neoforged.neoforge.client.event.RenderNameTagEvent event){
            var mc=Minecraft.getInstance();
            if(mc.player==null||!(event.getEntity() instanceof net.minecraft.world.entity.player.Player other)
                    ||other==mc.player||!state.has("inMatch")||!state.get("inMatch").getAsBoolean()
                    ||!state.has("me")||Engine.num(state.getAsJsonObject("me"),"team",-1)<0)return;
            if(!mc.player.isAlliedTo(other))event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
        @SubscribeEvent
        public static void referenceProps(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?,?> event){
            if(ReferenceProps.render(event))event.setCanceled(true);
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            BattleServerMode.clientActive=false;EntryScene.clear();NativeXaero.reset();
            MatchView.leftMatch();ProneControls.reset();BoundaryEffect.reset();NearMissEffect.reset();DeathCard.reset();CorpseRagdoll.reset();
            CrawlPose.apply(Minecraft.getInstance().player, false);
            LoadoutProfiles.save();fittingMemory.clear();loadoutMemory.clear();lobbyLoadouts.clear();LoadoutProfiles.load();wasDowned=false;downedJumpArmed=false;confirmedDeploy="";
            state = new JsonObject();
            lobby = new JsonObject();
            catalog = new JsonObject();
            // The item catalogue is rebuilt from this client's own registry next session, so a resource
            // pack change is picked up without keeping a stale list alive.
            ItemCatalog.clear();
            awards.clear();
            // The baked terrain belongs to a world/server; keeping it across a session boundary would
            // draw one world's surface under another's map.
            forgetTerrain();
            AssetIcons.clear();GearNames.clear();EquipmentStatistics.clear();
            ReferenceProps.clear();
            // Release the HUD minimap's terrain texture: it is a dynamic GPU texture, and logout is
            // the only point where the client knows the session is over.
            if (hudTerrain != null) {
                hudTerrain.close();
                hudTerrain = null;
            }
            if (briefingMap != null) {
                briefingMap.close();
                briefingMap = null;
            }
            // Same reasoning for the map bake: a dynamic GPU texture per session. Resetting the follow
            // state as well means the next server's map starts centred on the player instead of sliding in
            // from wherever this session left off.
            CombatHud.resetCaches();WorldMarkers.reset();NativeKillIcons.logout();
            Nameplates.reset();
            MuzzleGlow.reset();
            MateGlow.reset();
            // A buffered music or voice stream outlives the world it was started in: leaving it running
            // means the next server's lobby has the previous match's victory bed playing over it.
            MusicDirector.silence();
            NativeBattleAudio.reset();SoundCues.reset();
            cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter.clearClient();
            cn.bfops.crawl.compat.tacz.TaczPronePartService.clearAll();
            Cinematic.clear();
            DeployMarkers.reset();
            wasAlive=true;
            wasEverAlive=false;
            deployLaunched=false;
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            NativeKillIcons.tick();
            CorpseRagdoll.track(Minecraft.getInstance());
            GunBallistics.bakeIfChanged();
            var mapMc=Minecraft.getInstance();
            // Leaderboard key. Polled before the minimap keys because it is the one binding that has to
            // work outside a match as well, and the block below is guarded on `inMatch`.
            if(BattleServerMode.clientActive)cn.bfops.killicon.client.KeyBindings.poll(mapMc);
            if(MINIMAP_SETTINGS.consumeClick()&&BattleServerMode.clientActive&&mapMc.player!=null&&mapMc.screen==null)mapMc.setScreen(new MinimapSettingsScreen(null));
            while(MINIMAP_ZOOM_IN.consumeClick())if(mapMc.screen==null&&mapMc.player!=null)ClientPrefs.zoomMinimap(true);
            while(MINIMAP_ZOOM_OUT.consumeClick())if(mapMc.screen==null&&mapMc.player!=null)ClientPrefs.zoomMinimap(false);
            MatchView.lock();ProneControls.tick();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (!serverSupported() && !state.entrySet().isEmpty()) {
                    state = new JsonObject();
                    awards.clear();
                    forgetTerrain();
                }
                JsonObject me = state.has("me") ? state.getAsJsonObject("me") : null;
                CrawlPose.apply(mc.player, me != null && me.has("downed") && me.get("downed").getAsBoolean());
                boolean downed=CrawlPose.downed(mc.player);
                if(!downed||!wasDowned)downedJumpArmed=false;
                if(downed&&!mc.options.keyJump.isDown())downedJumpArmed=true;
                if(downed&&downedJumpArmed&&mc.screen==null&&mc.options.keyJump.isDown()&&mc.player.tickCount%4==0)
                    send(request("giveUp",currentMap()));
                if(downed){mc.options.keyAttack.setDown(false);mc.player.stopUsingItem();}
                wasDowned=downed;
                if(!EntryScene.active())trackDeathAndDeploy(mc, me);
                if(!EntryScene.active()&&DeploymentLock.required(state)&&!holdingDeathView()
                        &&!Cinematic.inDeathSequence()&&!(mc.screen instanceof FrontScreen)&&!(mc.screen instanceof GunModifyScreen)&&!(mc.screen instanceof DeathPreviewScreen)&&!(mc.screen instanceof ConfirmActionScreen)&&!(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)&&!(mc.screen!=null&&mc.screen.getClass().getName().startsWith("xaero.")))
                    mc.setScreen(new BattleScreen());
                NativeBattleAudio.tick(state,System.currentTimeMillis());
                MusicDirector.tick(state, System.currentTimeMillis());
                TacticalParticles.tick();
                // Crosshair on a player keeps that player's health bar alive. This is the "I just shot
                // them" rule from the reference pack: while aiming at somebody, their bar is legitimate
                // information even if the spotting window has lapsed. Re-armed cheaply on a tick, not in
                // the render loop, because it only needs second-level precision.
                if (mc.screen == null && mc.player.tickCount % 4 == 0
                        && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult aimed
                        && aimed.getEntity() instanceof net.minecraft.world.entity.player.Player target) {
                    Nameplates.recordHit(target);
                }
                if (mc.screen == null && mc.options.keyUse.isDown() && mc.player.getMainHandItem().isEmpty()
                        && !CrawlPose.downed(mc.player) && mc.player.tickCount % 4 == 0) {
                    JsonObject request = request("revive", Engine.str(state,"selected",""));
                    send(request);
                }
            }
            if(mc.player!=null&&mc.screen==null&&mc.options.keyUse.isDown()&&mc.player.getMainHandItem().isEmpty()&&mc.player.tickCount%4==0&&state.has("arena")&&Engine.str(state.getAsJsonObject("arena"),"mode","").equals("RUSH"))send(request("rushInteract",currentMap()));
            if(mc.player!=null&&mc.screen==null&&ELITE_PICKUP.consumeClick())send(request("elitePickup",Engine.str(state,"selected","")));
            if (mc.player != null && OPEN.consumeClick()) {
                send(request("open", Engine.str(state, "selected", "")));
            }
            if (mc.player != null && MINIMAP_CORNER.consumeClick()) {
                String corner = ClientPrefs.cycleCorner(serverMinimapCorner());
                // Action bar rather than chat: it is a display tweak, not something worth a chat line.
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "小地图位置：" + Briefing.minimapCornerName(corner)), true);
            }
            if (mc.player != null && mc.screen == null && mc.options.keyUse.consumeClick()
                    && (cn.bfops.SupportItems.isDeployTerminal(mc.player.getMainHandItem())
                        || cn.bfops.SupportItems.isDeployTerminal(mc.player.getOffhandItem()))) {
                // Same request the F4 key sends. Opening the list is harmless: only "redeploy" moves the body,
                // and the deploy screen refuses that while a firing-range player is still alive.
                send(request("open", Engine.str(state, "selected", "")));
            }
            if (mc.player != null && MARK.consumeClick()) {
                // Immediate local click: the server round trip used to be the first feedback, which read as
                // "the key did not register". The success cue still arrives from the server afterwards.
                if (mc.player != null) mc.player.playSound(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.6F);
                // Marking wins: the wheel has its own key, and even if a player rebinds them together the
                // mark must still fire instead of being swallowed.
                tacticalMark();
            }
            if (mc.player != null && MINIMAP_TOGGLE.consumeClick()) {
                boolean hidden = ClientPrefs.toggleMinimap();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        hidden ? "小地图已隐藏" : "小地图已显示"), true);
            }
            if (mc.player != null && MAP_ROTATION.consumeClick()) {
                boolean rotating = ClientPrefs.toggleMapRotation();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        rotating ? "地图随朝向旋转" : "地图北朝上"), true);
            }
            if (mc.player != null && KILL_ICON_PICKER.consumeClick()) {
                // The picker is a sub-page of the ops screen, which is where every other list in this mod lives.
                NativeKillIcons.open(null);
            }
            if (mc.player != null && KILL_ICON_STYLE.consumeClick()) {
                // Cycles through the presets the pack actually ships, in file order. The message names the
                // preset and the element it configures, because the pack's names are Chinese/English prose
                // ("Battlefield 6 模式", "CS2卡牌模式") and those are what a player will recognise.
                var presets = KillIconStore.corpus();
                String id = ClientPrefs.cycleKillIconPreset(presets.all().stream()
                        .map(KillIconPresets.Preset::id).toList());
                var preset = presets.byId(id);
                String message = preset == null
                        ? "击杀图标：预设 " + id
                        : "击杀图标：" + preset.name() + "（" + id + "）";
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
                // A preset change has to rebuild the live strip, or the player keeps seeing the old look
                // until the current icons expire.
                KillIconStore.applyPreset(id);NativeKillIcons.preset(id);
            }
            if (mc.player != null && AUDIO.consumeClick()) {
                String audio = ClientPrefs.cycleAudio();
                // Turning music off has to actually stop the streamed bed, not just refuse to start the next
                // one -- otherwise "music off" leaves the current track playing to the end of the match.
                if (!ClientPrefs.musicEnabled()) MusicDirector.silence();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(audio), true);
            }
        }

        /**
         * Map and scoreboard together, held rather than toggled.
         *
         * <p>Battlefield's scoreboard is a read-while-moving overlay, so this deliberately does not
         * open a {@code Screen}: that would take the mouse, swallow clicks and force the player to
         * close a menu before they could shoot again. Holding a key means releasing it is the close
         * action, which is one fewer thing to think about mid-firefight.
         *
         * <p>The two questions a player has mid-match — who is winning, and where is everyone — are
         * answered by the same glance here. Reading them from two separate overlays would mean holding
         * one key, releasing it, holding another, and remembering the first answer while looking at the
         * second, which is exactly the wrong thing to ask of someone under fire.
         *
         * <p>Drawn in {@code RenderGuiEvent.Post} so it lands above the HUD but below any real Screen
         * the player might open.
         */
        @SubscribeEvent
        public static void scores(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
            // Only while physically held; isDown() is the hold test, consumeClick() would latch.
            if (!SCORES.isDown()) return;
            if (!state.has("inMatch") || !state.get("inMatch").getAsBoolean() || !state.has("me") || !state.has("arena")) return;

            GuiGraphics graphics = event.getGuiGraphics();
            // A HUD overlay must never be able to end the session. Wrap the whole body: this runs from a render
            // hook, so any exception here propagates into the render loop and kills the game.
            try {
                drawScores(graphics);
            } catch (RuntimeException | LinkageError ex) {
                reportOverlayFailure(ex);
            }
        }

        /** Last overlay failure logged, so a per-frame error does not flood the log. */
        private static String lastOverlayFailure;

        private static void reportOverlayFailure(Throwable ex) {
            String text = FrontScreen.describe(ex);
            if (text.equals(lastOverlayFailure)) return;
            lastOverlayFailure = text;
            BattlefieldOps.LOG.error("Cannot draw the map/scoreboard overlay", ex);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "地图/计分板显示失败：" + text), false);
            }
        }

        private static void drawScores(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();
            JsonObject me = state.getAsJsonObject("me");
            if (!Engine.str(me, "map", "").equals(Engine.str(state, "selected", ""))) return;

            Model.Arena arena = FactionArt.arena();
            // The HUD is suppressed while this overlay is held, so this is the only thing asking for the
            // baked terrain at that moment. Without it, a player who joins a match and immediately holds
            // the key would see the checkerboard outside their view distance until they let go.
            wantTerrain(arena.id);
            List<Scoreboard.Row> rows = rows(me);

            // Inset from the screen edge so the panel never touches it, then split by width.
            Layout.Rect area = new Layout.Rect(14, 26,
                    Math.max(1, graphics.guiWidth() - 28), Math.max(1, graphics.guiHeight() - 52));
            Briefing.Split split = Briefing.split(area.w(), area.h());
            Layout.Rect[] panes = Briefing.panes(split, area);
            Layout.Rect mapRect = panes[0], scoreRect = panes[1];

            // Dim the world behind the panel: reading numbers over a firefight needs contrast, and a
            // translucent wash is cheaper than a solid box that hides context entirely.
            graphics.fill(area.x() - 6, area.y() - 22, area.right() + 6, area.bottom() + 6, 0x7010181f);
            graphics.drawString(mc.font,Ui.clip(mc.font,arena.name+" · "+arena.modeName(),area.w()),area.x(),area.y()-16,Ui.TEXT,true);

            if (mapRect != null) {
                if (briefingMap == null) briefingMap = new MapPicker();
                Layout.Rect body = Ui.header(graphics, mc.font, mapRect, "战术地图");
                // Framed on the arena boundary, not on the player: a flag on the far side of the map is
                // the thing being looked for, so the whole map has to be in the frame. Re-framed only
                // when the arena changes, to keep the boundary scan out of the render loop.
                if (!arena.id.equals(briefingArena)) {
                    briefingMap.frame(arena, mc.player.getX(), mc.player.getZ());
                    briefingArena = arena.id;
                }
                briefingMap.showGrid = false;
                briefingMap.showPlayers = true;
                briefingMap.highlightPoint = -1;
                briefingMap.highlightSpawn = -1;
                briefingMap.highlightSector = -1;
                briefingMap.draw(graphics, mc.font, body, arena, state,
                        Engine.num(me, "team", -1), mc.player.getX(), mc.player.getZ(), mc.player.getYRot());
            }

            int columnWidth = Math.max(40, scoreRect.w() / 2 - 8);
            int friendly=Engine.num(me,"team",0);
            teamPanel(mc, graphics, rows, friendly, scoreRect.x(), scoreRect.y(), columnWidth);
            teamPanel(mc, graphics, rows, 1-friendly, scoreRect.x() + scoreRect.w() / 2 + 4,
                    scoreRect.y(), columnWidth);
            // Objectives under the two team columns: a scores-only panel is half a Battlefield
            // scoreboard, since the flags are what the match is actually about.
            objectives(mc, graphics, scoreRect.x(), scoreRect.bottom() - 26, scoreRect.w());
            graphics.drawString(mc.font, "松开 " + SCORES.getTranslatedKeyMessage().getString() + " 关闭",
                    scoreRect.x(), scoreRect.bottom() - 10, 0xff778088);
        }

        /**
         * Map layer for the combined briefing overlay.
         *
         * <p>Kept separate from the always-on HUD minimap: that one follows the player at a fixed zoom,
         * this one frames the whole arena and is only alive while the key is held.
         */
        private static MapPicker briefingMap;
        /** Which arena {@link #briefingMap} was framed on, so the framing is not recomputed per frame. */
        private static String briefingArena = "";

        /** One line: each objective id, its owner and its capture progress. */
        private static void objectives(Minecraft mc, GuiGraphics graphics, int x, int y, int width) {
            if (!state.has("flags") || !state.has("arena")) return;
            Model.Arena arena = FactionArt.arena();
            JsonArray flags = state.getAsJsonArray("flags");
            int stage = Engine.num(state, "stage", 0);
            StringBuilder line = new StringBuilder("据点  ");
            int used = 0;
            for (int i = 0; i < arena.points.size() && i < flags.size(); i++) {
                Model.Point point = arena.points.get(i);
                // In breakthrough only the current stage's objectives matter; the rest are greyed out
                // noise that would push the useful part off the panel.
                if (!CombatHud.active(arena,point,stage)) continue;
                JsonObject flag = flags.get(i).getAsJsonObject();
                int owner = Engine.num(flag, "owner", -1);
                String entry = point.id + " " + Minimap.holder(owner) + " "
                        + (flag.has("contested") && flag.get("contested").getAsBoolean()
                        ? "争夺" : (int) Math.round(flag.get("progress").getAsDouble()) + "%");
                if (used + mc.font.width(entry) + 12 > width) break;
                graphics.drawString(mc.font, entry, x + used, y, Minimap.color(owner));
                used += mc.font.width(entry) + 12;
            }
        }

        /** Rebuilds row data from the server snapshot; the client never computes scores itself. */
        private static List<Scoreboard.Row> rows(JsonObject me) {
            List<Scoreboard.Row> rows = new ArrayList<>();
            JsonArray scores = state.has("scores") ? state.getAsJsonArray("scores") : new JsonArray();
            String self = Engine.str(me, "name", "");
            for (JsonElement element : scores) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                String name = Engine.str(row, "name", "");
                rows.add(new Scoreboard.Row(name,
                        Engine.num(row, "team", -1),
                        Engine.num(row, "kills", 0),
                        Engine.num(row, "deaths", 0),
                        Engine.num(row, "score", 0),
                        Engine.num(row, "captures", 0),
                        !row.has("alive") || row.get("alive").getAsBoolean(),
                        name.equals(self),Engine.num(row,"assists",0),Engine.num(row,"revives",0),Engine.str(row,"kit",""),Engine.num(row,"squad",0)));
            }
            return rows;
        }

        private static String title() {
            Model.Arena arena = FactionArt.arena();
            return arena.name + "   " + MatchScore.line(arena,state);
        }

        /** One team column: header with alive count and total score, then the ranked rows. */
        private static void teamPanel(Minecraft mc, GuiGraphics graphics, List<Scoreboard.Row> all,
                                      int team, int x, int y, int width) {
            List<Scoreboard.Row> ranked = Scoreboard.ranked(all, team);
            int limit = Math.max(1, (graphics.guiHeight() - y - 85) / 11);
            List<Scoreboard.Row> shown = Scoreboard.top(ranked, limit);

            String label = FactionArt.name(team);
            int selfTeam=Engine.num(state.getAsJsonObject("me"),"team",0);
            int headerColor=team==selfTeam?TacticalColors.FRIEND:TacticalColors.ENEMY;
            graphics.drawString(mc.font,Ui.clip(mc.font,label+"  "+Scoreboard.teamAlive(all,team)+" 人存活 · "+Scoreboard.teamScore(all,team)+" 分",width),x,y,headerColor);
            int statWidth=Math.min(110,Math.max(65,width/2)),nameWidth=width-statWidth;
            String[] headings={"击","死","助","救","得分"};
            graphics.fill(x-2,y+11,x+width+2,y+23,0x50657682);
            graphics.drawString(mc.font,"姓名 / 小队",x,y+13,Ui.TEXT_DIM,true);
            for(int i=0;i<5;i++){int edge=x+nameWidth+statWidth*(i+1)/5;graphics.drawString(mc.font,headings[i],edge-mc.font.width(headings[i]),y+13,Ui.TEXT_DIM,true);}
            int line = y + 25;
            for (Scoreboard.Row row : shown) {
                int color=!row.alive()?Scoreboard.DEAD:row.self()?TacticalColors.SELF:headerColor;
                if(row.self())graphics.fill(x-2,line-1,x+width+2,line+10,0x305f9dd4);
                String numbers=Scoreboard.stats(row);
                AssetIcons.draw(graphics,"role",row.kit(),x,line-1,12,12);
                String who=Scoreboard.label(row)+(row.squad()>0?" ["+row.squad()+"]":"");
                graphics.drawString(mc.font,Ui.clip(mc.font,who,Math.max(8,nameWidth-17)),x+12,line,color);
                // Stats are right-aligned so the digits line up; a ragged column is unreadable fast.
                String stats = Scoreboard.stats(row);
                int[] values={row.kills(),row.deaths(),row.assists(),row.revives(),row.score()};
                for(int i=0;i<5;i++){int edge=x+nameWidth+statWidth*(i+1)/5;String value=Ui.clip(mc.font,String.valueOf(values[i]),Math.max(8,statWidth/5-2));graphics.drawString(mc.font,value,edge-mc.font.width(value),line,color);}
                line += 11;
            }
            int overflow = Scoreboard.overflow(ranked, limit);
            if (overflow > 0) {
                graphics.drawString(mc.font, "还有 " + overflow + " 人", x, line, 0xff778088);
            }
            if (ranked.isEmpty()) {
                graphics.drawString(mc.font, "无人参战", x, line, 0xff778088);
            }
        }

        /**
         * The currently bound key for the deployment GUI, so HUD hints never go stale.
         *
         * <p>These texts used to hard-code "按 B", which became wrong the moment the binding moved to
         * F4 — and would go wrong again for any player who rebinds it. Reading the live binding is the
         * only way the hint stays true.
         */
        private static String openKey() {
            return OPEN.getTranslatedKeyMessage().getString();
        }

        /**
         * Terrain layer for the HUD minimap.
         *
         * <p>Held statically because the HUD is drawn from an event handler with no screen lifetime to
         * hang it on. The texture is re-targeted as the player moves, so it follows them around the
         * map, and it is released on logout so a texture is not leaked per session.
         */
        private static TerrainPreview hudTerrain;

        /**
         * Whether this client was alive on the previous snapshot.
         *
         * <p>The death cinematic has to start on the <em>transition</em> from alive to dead, not on the
         * steady state of being dead: a snapshot arrives five times a second, so every one of those ticks
         * would otherwise look like a fresh death and try to restart the sequence. Keeping the previous
         * value is the whole mechanism.
         */
        private static boolean wasAlive = true;
        private static String overviewKey="";
        static String overviewWarning="";

        /**
         * Whether this client has ever been alive in the current match.
         *
         * <p>Separate from {@link #wasAlive} because the two answer different questions. {@code wasAlive}
         * is the previous snapshot's value and drives edge detection; this one drives <em>whether an edge is
         * a death at all</em>. A fresh member joins with {@code alive == false} (they are a spectator until
         * they deploy), and without this flag the very first snapshot would look like an alive→dead edge and
         * play the death cinematic over a player who has not died yet.
         */
        private static boolean wasEverAlive;

        /** Whether the deploy camera has already been launched for this death. */
        private static boolean deployLaunched;
        private static String confirmedDeploy="";
        private static boolean confirmDeployment(Minecraft mc,JsonObject snapshot){
            if(!snapshot.has("me")){confirmedDeploy="";return false;}
            if(!snapshot.has("inMatch")||!snapshot.get("inMatch").getAsBoolean())return false;
            JsonObject me=snapshot.getAsJsonObject("me");
            if(!me.has("alive")||!me.get("alive").getAsBoolean()||!me.has("deploySequence")||me.get("deploySequence").getAsLong()<=0)return false;
            String key=Engine.str(snapshot,"selected","")+":"+me.get("deploySequence").getAsLong();
            if(key.equals(confirmedDeploy))return false;
            confirmedDeploy=key;wasAlive=true;wasEverAlive=true;deployLaunched=false;overviewWarning="";
            Cinematic.descend();DeployMarkers.select("");
            if(mc.screen instanceof BattleScreen||mc.screen instanceof LoadoutScreen||mc.screen instanceof FittingScreen||mc.screen instanceof GunModifyScreen)mc.setScreen(null);
            return true;
        }

        /**
         * Drives {@link Cinematic} from the snapshot.
         *
         * <p>Everything is decided from the server's own flags rather than from the local gamemode or health,
         * because those disagree during exactly the moments this matters: a dead player is a spectator, so
         * the gamemode changes for other reasons too, and a downed player is alive with low health.
         *
         * <p>The state distinctions are: joining (never alive) shows the overview, not a death; a downed
         * player stays {@code alive} so they keep their crawl view; a true death flips {@code alive} to
         * false and plays the rise; a redeploy flips it back and descends to the body.
         */
        static void previewClosed(){deployLaunched=false;overviewKey="";}
        private static void trackDeathAndDeploy(Minecraft mc, JsonObject me) {
            if(DeathPreviewScreen.active())return;
            if (!DeploymentLock.room(state) || me == null || !me.has("alive")) {
                if(VanillaDeathFlow.active())return;
                // Not in a match at all. Hand the camera back if we were holding it.
                if (Cinematic.active()) Cinematic.clear();
                wasAlive = true;
                wasEverAlive = false;
                deployLaunched = false;
                return;
            }
            boolean alive = me.get("alive").getAsBoolean();

            if (alive) {
                wasEverAlive = true;
            }

            if (wasEverAlive && wasAlive && !alive) {
                NativeKillIcons.death();MusicDirector.death();
                // A genuine death: this client was alive and is now not. Start from the player's own last
                // position and angles, which is what the camera is looking at right now — the alternative is
                // to wait for the death packet to carry a body position, and by then the camera has already
                // been re-parented to the spectator.
                Cinematic.clear();
                Cinematic.startDeath(mc.player.getX(), mc.player.getY() + 1.6, mc.player.getZ(),
                        mc.player.getYRot(), mc.player.getXRot());
                deployLaunched = false;
                // Hold the deploy map back so the camera move and the death card are actually seen.
                deathHoldUntil = System.currentTimeMillis() + 2600L;
            } else if (!wasAlive && alive) {
                // Redeployed: descend to the body the server just placed.
                Cinematic.descend();
                deployLaunched = false;
            } else if (!alive && !deployLaunched && !Cinematic.inDeathSequence()
                    && state.has("arena") && Engine.num(me,"team",-1)>=0) {
                // Dead, and the death sequence has finished (or never ran, e.g. a spectator who just picked
                // a team): rise to the overhead view of the deploy area and hold there.
                Model.Arena arena=FactionArt.arena();
                frameOverview(mc,me,arena);
                deployLaunched = true;
            }

            if(!alive&&deployLaunched&&!Cinematic.inDeathSequence()&&state.has("arena")&&!overviewKey.equals(overviewSignature(mc)))
                frameOverview(mc,me,FactionArt.arena());
            wasAlive = alive;
        }
        private static String overviewSignature(Minecraft mc){return mc.getWindow().getGuiScaledWidth()+":"+mc.getWindow().getGuiScaledHeight()+":"+(ConfirmActionScreen.base(mc.screen) instanceof BattleScreen screen?screen.fullBattlefield():false)+":"+mc.options.fov().get()+":"+overviewZoom+":"+overviewPanX+":"+overviewPanZ;}
        private static void frameOverview(Minecraft mc,JsonObject me,Model.Arena arena){
            int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            Layout.Rect viewport=ConfirmActionScreen.base(mc.screen) instanceof BattleScreen screen?screen.overviewViewport():new Layout.Rect(0,0,w,h);
            double ground=arena.spawns.stream().mapToDouble(s->s.y).max().orElse(64);
            if(state.has("deploySites")&&!state.getAsJsonArray("deploySites").isEmpty()){
                ground=-Double.MAX_VALUE;for(var item:state.getAsJsonArray("deploySites"))ground=Math.max(ground,item.getAsJsonObject().get("y").getAsDouble());
            }
            var view=arena.customDeployView
                ?new DeployView.Placement(arena.deployViewX,arena.deployViewY,arena.deployViewZ,arena.deployViewYaw,arena.deployViewPitch,true,0,0)
                :DeployView.frame(arena.viewArea(Engine.num(me,"team",0)),ground,mc.options.fov().get(),w,h,viewport,mc.options.renderDistance().get()*16);
            // Apply the player's scroll zoom (camera height scale) and drag pan (world-block offset).
            double lift=Math.max(8,view.headroom()*overviewZoom);
            view=new DeployView.Placement(view.x()+overviewPanX,ground+lift,view.z()+overviewPanZ,
                    view.yaw(),view.pitch(),view.fits(),view.overflow(),lift);
            Cinematic.overview(view);overviewKey=overviewSignature(mc);
            overviewWarning=view.fits()?"":"部分战场超出加载视距，建议切换战术地图";
        }

        /**
         * Enemy UUIDs the server has authorised this client to see.
         *
         * <p>Read from the snapshot's hostile blips rather than decided locally. A nameplate for an enemy
         * has to follow the same decision that put the blip on the map, or the two surfaces would disagree
         * and the bar would become a slower, more generous radar.
         *
         * <p>Rebuilt per frame from at most a handful of entries; the allocation is one small set, which is
         * cheaper than keeping it in sync with a snapshot that changes on the network thread.
         */
        private static java.util.Set<java.util.UUID> spottedEnemies() {
            java.util.Set<java.util.UUID> out = new java.util.HashSet<>();
            if (!state.has("mates") || !state.get("mates").isJsonArray()) return out;
            var level = Minecraft.getInstance().level;
            if (level == null) return out;
            for (var element : state.getAsJsonArray("mates")) {
                if (!element.isJsonObject()) continue;
                JsonObject mate = element.getAsJsonObject();
                if (!mate.has("hostile") || !mate.get("hostile").getAsBoolean()) continue;
                if (!mate.has("name")) continue;
                String name = mate.get("name").getAsString();
                for (var player : level.players()) {
                    if (player.getGameProfile().getName().equals(name)) {
                        out.add(player.getUUID());
                        break;
                    }
                }
            }
            return out;
        }

        @SubscribeEvent
        public static void hud(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.options.hideGui || SCORES.isDown()) return;
            boolean inMatch=state.has("inMatch") && state.get("inMatch").getAsBoolean() && state.has("me") && state.has("arena");
            // The music bed is evaluated from the HUD tick rather than from a packet handler: the mood depends
            // on several snapshot fields at once, and a snapshot arrives once a second, so this is the only
            // place that sees a consistent set of them. Outside the match HUD it still needs evaluating, which
            // is why it runs before the inMatch return below.
            try {
                drawAwardFeed(event.getGuiGraphics());
                if (!inMatch) return;
                JsonObject me=state.getAsJsonObject("me");
                if (!Engine.str(me,"map", "").equals(Engine.str(state,"selected",""))) return;
                if (hudTerrain == null) hudTerrain = new TerrainPreview();
                var graphics = event.getGuiGraphics();
                // World-projected objective markers are drawn outside the sway: they are anchored to places
                // in the world, so translating them with the HUD would make them slide against the terrain
                // they are supposed to be pinned to.
                // Friendly halos go under everything else: the glow is background reassurance, and drawing it
                // after the name plates would wash out the very text it is meant to help you find.
                // World-space HUD keeps drawing while dead: objective markers, teammate bodies, name
                // plates and the deploy markers are anchored to places in the world, so they are part of
                // what the death camera is looking at rather than part of your own readout.
                MateGlow.draw(graphics, mc, System.currentTimeMillis());
                if (!me.has("alive") || !me.get("alive").getAsBoolean()) DeployMarkers.draw(graphics, mc);
                else WorldMarkers.draw(graphics, mc);
                Nameplates.draw(graphics, mc, spottedEnemies());
                // What death does hide is your own screen-space HUD: minimap, weapon and equipment bars,
                // squad list, capture queue and tickets.
                boolean dead = me.has("alive") && !me.get("alive").getAsBoolean();
                if (!dead && !(VehicleUi.active() && VehicleUi.scoped())) {
                    // Inertial sway: the whole match HUD trails the view by a few pixels and settles
                    // back. Wrapped in push/pop with a try/finally so a draw failure cannot leave the
                    // pose stack unbalanced, which would displace every later GUI layer in the frame.
                    double[] sway = HudMotion.advance();
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(sway[0], sway[1], 0);
                    try {
                        CombatHud.draw(graphics, hudTerrain, sway[0], sway[1]);
                    } finally {
                        pose.popPose();
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                reportHudFailure(ex);
            }
            // The death card is drawn outside the sway and outside the HUD block: while dead there is no
            // other HUD left, and the card must not slide with the view.
            try {
                DeathCard.draw(event.getGuiGraphics(), mc);
            } catch (RuntimeException | LinkageError ex) {
                reportHudFailure(ex);
            }
        }

        /**
         * Draws the award feed and the hit marker.
         *
         * <p>Both are purely cosmetic and server-authored: nothing here decides whether something was earned,
         * only how it looks. A failure therefore cannot affect the match, which is why it shares the HUD's
         * guard rather than needing its own.
         */
        private static void drawAwardFeed(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            long now = System.currentTimeMillis();
            boolean vehicle = VehicleUi.active();
            if (!vehicle && !GunFeedbackCompat.heldGun(mc.player.getMainHandItem()) && now - hitMarkerAt < HIT_MARKER_MS) {
                int cx = graphics.guiWidth()/2, cy = graphics.guiHeight()/2, size = 5;
                int color = 0xffd9f4f6;
                Ui.line(graphics,cx-size,cy-size,cx-2,cy-2,color,1);
                Ui.line(graphics,cx+size,cy-size,cx+2,cy-2,color,1);
                Ui.line(graphics,cx-size,cy+size,cx-2,cy+2,color,1);
                Ui.line(graphics,cx+size,cy+size,cx+2,cy+2,color,1);
            }
            NativeKillIcons.draw(graphics);
            // The 656 strip draws after the text feed so an icon never sits under a row of text. It is skipped
            // while a vehicle optic is up for the same reason the text feed freezes: a kill made mid-scope must
            // not smear across the optic's view.

        }

        /** Last HUD failure logged, so a per-frame error does not flood the log. */
        private static String lastHudFailure;

        private static void reportHudFailure(Throwable ex) {
            String text = FrontScreen.describe(ex);
            if (text.equals(lastHudFailure)) return;
            lastHudFailure = text;
            BattlefieldOps.LOG.error("Cannot draw the match HUD", ex);
        }

        /** Compact standings block, shown only when the server had to give up the sidebar. */
        private static int scores(Minecraft mc, GuiGraphics graphics, int y) {
            JsonArray scores = state.has("scores") ? state.getAsJsonArray("scores") : new JsonArray();
            if (scores.isEmpty()) return y;
            graphics.drawString(mc.font, "§f比分", 8, y, 0xffffff);
            y += 11;
            for (int i = 0; i < scores.size() && i < 6; i++) {
                JsonObject row = scores.get(i).getAsJsonObject();
                int team = Engine.num(row, "team", -1);
                String prefix = team == 0 ? "§9" : team == 1 ? "§c" : "§7";
                String line = prefix + Engine.str(row, "name", "?")
                        + " §7" + Engine.num(row, "kills", 0) + "/" + Engine.num(row, "deaths", 0)
                        + " §f" + Engine.num(row, "score", 0);
                graphics.drawString(mc.font, line, 8, y, Minimap.color(team));
                y += 11;
                if (y > 150) break;
            }
            return y;
        }

        private static String ticketLine(Model.Arena arena) {
            return arena.name + "  " + MatchScore.line(arena,state);
        }    }
}
