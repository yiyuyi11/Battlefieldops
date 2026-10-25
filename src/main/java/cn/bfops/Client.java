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
    /**
     * Opens the deployment / map editing GUI.
     *
     * <p>F4 rather than the more obvious {@code B}: in the target pack {@code B} is already taken by
     * Xaero's "new waypoint", by the fxntstorage backpack and by vsie's force assist, so a B binding
     * would either fight them or be silently overridden. {@code F4} through {@code F10} and
     * {@code F12} are unbound in that pack, and F4 sits next to the other F-key HUD toggles where a
     * player already looks.
     */
    static final KeyMapping OPEN = new KeyMapping("key.battlefield_ops.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.battlefield_ops");

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
     * Marks the enemy under the crosshair for the team.
     *
     * <p>The client sends only the intent. The server decides who was being aimed at, whether they were
     * visible and how long the mark lasts — a client-supplied position would let anyone mark enemies they
     * cannot see.
     */
    static final KeyMapping MARK = new KeyMapping("key.battlefield_ops.mark",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.battlefield_ops");

    /** Hides or shows the HUD minimap, without touching the rest of the HUD. */
    static final KeyMapping MINIMAP_TOGGLE = new KeyMapping("key.battlefield_ops.minimap_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.battlefield_ops");

    static JsonObject state = new JsonObject(), catalog = new JsonObject();

    /** Floating award feed, newest first. */
    static java.util.List<Feed.Popup> awards = new java.util.ArrayList<>();
    /** When the last hit landed, and for how much, for the crosshair marker. */
    static long hitMarkerAt;
    static int hitMarkerDamage;
    /** How long the hit marker stays visible. Short enough to read as a flash, not a badge. */
    static final long HIT_MARKER_MS = 180;
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
        event.register(OPEN);
        event.register(SCORES);
        event.register(MINIMAP_CORNER);
        event.register(MINIMAP_TOGGLE);
        event.register(MARK);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Network.receiver = Client::receive);
    }

    static void receive(String json) {
        try {
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            Minecraft mc = Minecraft.getInstance();
            if (Engine.str(payload, "type", "").equals("catalog")) {
                catalog = payload;
                if (mc.screen instanceof OpsScreen screen) screen.catalogArrived();
                return;
            }
            // Award feed and hit marker: cosmetic, server-authored, drawn over the HUD.
            if (Engine.str(payload, "type", "").equals("bonus")) {
                awards = Feed.add(awards, new Feed.Popup(
                        Engine.str(payload, "label", "奖励"),
                        Engine.num(payload, "points", 0),
                        System.currentTimeMillis(),
                        Engine.str(payload, "detail", ""),
                        Engine.str(payload, "icon", "kill")));
                return;
            }
            if (Engine.str(payload, "type", "").equals("hit")) {
                hitMarkerAt = payload.has("nativeMarker") && payload.get("nativeMarker").getAsBoolean() ? 0 : System.currentTimeMillis();
                hitMarkerDamage = Engine.num(payload, "damage", 0);
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
                mc.setScreen(new ResultScreen(payload,mc.screen));return;
            }
            if(Engine.str(payload,"type","").equals("fittings")){
                if(mc.screen instanceof FittingScreen screen)screen.receive(payload);return;
            }
            state = payload;
            if(mc.screen instanceof FittingScreen screen&&!Engine.str(payload,"message","").isEmpty())screen.error(Engine.str(payload,"message",""));
            if (mc.screen instanceof BattleScreen screen) {
                screen.updated();
            } else if (mc.screen instanceof OpsScreen screen) {
                screen.updated();
            } else if (payload.has("open") && payload.get("open").getAsBoolean()) {
                mc.setScreen(new BattleScreen());
            }
        } catch (Exception ex) {
            BattlefieldOps.LOG.error("Invalid battlefield UI response", ex);
        }
    }

    static boolean serverSupported() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        var listener = (net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)connection;
        return listener.hasChannel(Network.Request.TYPE) && listener.hasChannel(Network.State.TYPE);
    }

    static void send(JsonObject request) {
        if (!serverSupported()) {
            if (Engine.str(request,"op", "").equals("open") && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal("此服务器未启用战地风云，普通游戏不受影响"), true);
            return;
        }
        String json = BattlefieldOps.GSON.toJson(request);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 28000) {
            if (Minecraft.getInstance().screen instanceof OpsScreen screen) {
                throw new IllegalArgumentException("配置过大，请减少顶点 / 配装 / 载具数量；未提交保存");
            }
            return;
        }
        PacketDistributor.sendToServer(new Network.Request(json));
    }

    static JsonObject request(String op, String map) {
        JsonObject json = new JsonObject();
        json.addProperty("op", op);
        json.addProperty("map", map);
        return json;
    }

    @EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            CrawlPose.apply(Minecraft.getInstance().player, false);
            fittingMemory.clear();
            state = new JsonObject();
            catalog = new JsonObject();
            // The item catalogue is rebuilt from this client's own registry next session, so a resource
            // pack change is picked up without keeping a stale list alive.
            ItemCatalog.clear();
            awards.clear();
            // The baked terrain belongs to a world/server; keeping it across a session boundary would
            // draw one world's surface under another's map.
            forgetTerrain();
            AssetIcons.clear();
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
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (!serverSupported() && !state.entrySet().isEmpty()) {
                    state = new JsonObject();
                    awards.clear();
                    forgetTerrain();
                }
                JsonObject me = state.has("me") ? state.getAsJsonObject("me") : null;
                CrawlPose.apply(mc.player, me != null && me.has("downed") && me.get("downed").getAsBoolean());
                if (mc.screen == null && mc.options.keyUse.isDown() && mc.player.getMainHandItem().isEmpty()
                        && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                        && hit.getEntity() instanceof net.minecraft.world.entity.player.Player target
                        && mc.player.tickCount % 4 == 0) {
                    JsonObject request = request("revive", Engine.str(state,"selected",""));
                    request.addProperty("target", target.getUUID().toString());
                    send(request);
                }
            }
            if (mc.player != null && OPEN.consumeClick()) {
                send(request("open", Engine.str(state, "selected", "")));
            }
            if (mc.player != null && MINIMAP_CORNER.consumeClick()) {
                String corner = ClientPrefs.cycleCorner(serverMinimapCorner());
                // Action bar rather than chat: it is a display tweak, not something worth a chat line.
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "小地图位置：" + Briefing.minimapCornerName(corner)), true);
            }
            if (mc.player != null && MARK.consumeClick()) {
                send(request("mark", Engine.str(state, "selected", "")));
            }
            if (mc.player != null && MINIMAP_TOGGLE.consumeClick()) {
                boolean hidden = ClientPrefs.toggleMinimap();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        hidden ? "小地图已隐藏" : "小地图已显示"), true);
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

            Model.Arena arena = BattlefieldOps.GSON.fromJson(state.get("arena"), Model.Arena.class);
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
            graphics.fill(area.x() - 6, area.y() - 22, area.right() + 6, area.bottom() + 6, 0xcc101418);
            graphics.drawCenteredString(mc.font, title(), graphics.guiWidth() / 2, area.y() - 16, 0xffffffff);

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
                briefingMap.showPlayers = true;
                briefingMap.highlightPoint = -1;
                briefingMap.highlightSpawn = -1;
                briefingMap.highlightSector = -1;
                briefingMap.draw(graphics, mc.font, body, arena, state,
                        Engine.num(me, "team", -1), mc.player.getX(), mc.player.getZ(), mc.player.getYRot());
            }

            int columnWidth = Math.max(40, scoreRect.w() / 2 - 8);
            teamPanel(mc, graphics, rows, 0, scoreRect.x(), scoreRect.y(), columnWidth);
            teamPanel(mc, graphics, rows, 1, scoreRect.x() + scoreRect.w() / 2 + 4,
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
            Model.Arena arena = BattlefieldOps.GSON.fromJson(state.get("arena"), Model.Arena.class);
            JsonArray flags = state.getAsJsonArray("flags");
            int stage = Engine.num(state, "stage", 0);
            StringBuilder line = new StringBuilder("据点  ");
            int used = 0;
            for (int i = 0; i < arena.points.size() && i < flags.size(); i++) {
                Model.Point point = arena.points.get(i);
                // In breakthrough only the current stage's objectives matter; the rest are greyed out
                // noise that would push the useful part off the panel.
                if (arena.mode.equals("BREAKTHROUGH") && point.sector != stage) continue;
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
                        name.equals(self)));
            }
            return rows;
        }

        private static String title() {
            Model.Arena arena = BattlefieldOps.GSON.fromJson(state.get("arena"), Model.Arena.class);
            if (!state.has("tickets")) return arena.name;
            JsonArray tickets = state.getAsJsonArray("tickets");
            JsonArray tracks = state.has("tracksTickets") ? state.getAsJsonArray("tracksTickets") : new JsonArray();
            boolean both = tracks.isEmpty() || (tracks.get(0).getAsBoolean() && tracks.get(1).getAsBoolean());
            if (!both) return arena.name + "   进攻方兵力 " + tickets.get(0).getAsInt()
                    + "   阶段 " + (Engine.num(state, "stage", 0) + 1) + "/" + Engine.num(state, "stages", 1);
            return arena.name + "   蓝 " + tickets.get(0).getAsInt() + "  :  " + tickets.get(1).getAsInt() + " 红";
        }

        /** One team column: header with alive count and total score, then the ranked rows. */
        private static void teamPanel(Minecraft mc, GuiGraphics graphics, List<Scoreboard.Row> all,
                                      int team, int x, int y, int width) {
            List<Scoreboard.Row> ranked = Scoreboard.ranked(all, team);
            int limit = Math.max(1, (graphics.guiHeight() - 80) / 11);
            List<Scoreboard.Row> shown = Scoreboard.top(ranked, limit);

            String label = Board.label(team);
            int headerColor = team == 0 ? Scoreboard.BLUE : Scoreboard.RED;
            graphics.drawString(mc.font, label + "  " + Scoreboard.teamAlive(all, team) + " 人存活"
                    + "   总分 " + Scoreboard.teamScore(all, team), x, y, headerColor);
            int line = y + 14;
            for (Scoreboard.Row row : shown) {
                int color = Scoreboard.color(row);
                graphics.drawString(mc.font, Scoreboard.label(row), x, line, color);
                // Stats are right-aligned so the digits line up; a ragged column is unreadable fast.
                String stats = Scoreboard.stats(row);
                graphics.drawString(mc.font, stats, x + width - mc.font.width(stats), line, color);
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

        @SubscribeEvent
        public static void hud(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.options.hideGui || SCORES.isDown()) return;
            boolean inMatch=state.has("inMatch") && state.get("inMatch").getAsBoolean() && state.has("me") && state.has("arena");
            try {
                drawAwardFeed(event.getGuiGraphics());
                if (!inMatch) return;
                JsonObject me=state.getAsJsonObject("me");
                if (!Engine.str(me,"map", "").equals(Engine.str(state,"selected",""))) return;
                if (hudTerrain == null) hudTerrain = new TerrainPreview();
                if (!(VehicleUi.active() && VehicleUi.scoped())) CombatHud.draw(event.getGuiGraphics(), hudTerrain);
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
            AwardHud.draw(graphics, now, vehicle, vehicle && VehicleUi.scoped());
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
            String head = arena.name + "  ";
            if (!state.has("tickets")) return head + "未开局";
            JsonArray tickets = state.getAsJsonArray("tickets");
            JsonArray tracks = state.has("tracksTickets") ? state.getAsJsonArray("tracksTickets") : new JsonArray();
            boolean bothPools = tracks.isEmpty() || (tracks.get(0).getAsBoolean() && tracks.get(1).getAsBoolean());
            String pools = bothPools
                    ? "蓝 " + tickets.get(0).getAsInt() + " / 红 " + tickets.get(1).getAsInt()
                    : "进攻方 " + tickets.get(0).getAsInt();
            return head + pools + "  阶段 " + (Engine.num(state, "stage", 0) + 1)
                    + "/" + Math.max(1, Engine.num(state, "stages", 1));
        }    }
}
