package cn.bfops;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Server-authoritative match engine.
 *
 * <p>Every client request is re-validated here: the client may only ask, never decide. Maps,
 * teams, kits, spawns, vehicle grants, tickets and personal score all live on this side, so an
 * edited client cannot award itself a kit it is not entitled to, spawn outside its own stage, or
 * push tickets around.
 *
 * <p>Deployment follows the reference flow: joining a match backs the player's inventory and
 * position up to disk, then the kit is granted from the map whitelist. Leaving (or a server stop,
 * or a crash) restores that backup, so testing never eats a real save.
 */
public final class Engine {
    /**
     * How close a living teammate must be for an empty vehicle to count as still in use.
     *
     * <p>Generous on purpose: parking a tank and walking to a nearby ridge is normal play, and the
     * cost of keeping a hull too long is a wasted slot while the cost of reclaiming one too eagerly is
     * destroying a player's vehicle under them. 64 blocks covers a capture point and its approaches.
     */
    private static final double VEHICLE_ORPHAN_RANGE = 64.0;
    public static final class Member {
        public String map, name;
        public int team, score, kills, deaths, captures, outside;
        /**
         * Retired currency. Vehicles are rationed by cooldown and the per-match cap instead.
         *
         * <p>Still accumulated so the scoreboard has a per-player running total to build on, but it is
         * no longer displayed as a spendable balance and nothing is deducted from it. Kept as a field
         * rather than deleted so any persisted match state still loads.
         */
        public int credits;
        public boolean alive;
        /**
         * Downed: alive but out of the fight, bleeding out and revivable.
         *
         * <p>Separate from {@link #alive} because almost everything that checks "is this player in the match"
         * must still see them as present — they are on the map, they can be shot at and revived, and they must
         * not be treated as a deployment candidate.
         */
        public boolean downed;
        public long bleedOutAt;
        public String downedByName = "";
        public java.util.UUID downedBy;
        public int reviveProgress;
        /** The downed teammate this player is currently reviving, or null. */
        public java.util.UUID reviving;
        /** Whether the kill for this member's current down was already credited to the downer. */
        public boolean killCredited;
        public long readyAt, vehicleAt;
        /** Last boundary warning second shown, so the title is not resent every tick. */
        public int warnedSecond = -1;
        /** Set while the player is glowing as a teammate marker, so it can be cleared exactly once. */
        public boolean glowing;

        public Member(String map, String name, int team) {
            this.map = map;
            this.name = name;
            this.team = team;
        }
    }

    static final class Match {
        final Rules rules;
        boolean running;
        final Set<UUID> vehicles = new HashSet<>();
        /**
         * Vehicle entity -> owning team, recorded when it spawns.
         *
         * <p>Ownership is taken from the deploying player, never from who is currently sitting in
         * the vehicle: an empty hull still belongs to the team that bought it, and reading the
         * driver would flip the colour of a parked tank to "enemy" the moment its crew stepped out.
         */
        final Map<UUID, Integer> vehicleTeams = new HashMap<>();
        /** Manual marks for both teams; each carries its own team so one list is enough. */
        final List<Spotting.Mark> marks = new ArrayList<>();
        /** Recent damage per victim, for assists. */
        final Map<UUID, List<Awards.Hit>> hits = new HashMap<>();
        /** Who last killed each player, for revenge. */
        final Map<UUID, UUID> lastKiller = new HashMap<>();
        /** When each player last killed someone, and how many in a row, for streaks. */
        final Map<UUID, Long> lastKillAt = new HashMap<>();
        final Map<UUID, Integer> streak = new HashMap<>();

        Match(Model.Arena map) {
            rules = new Rules(map);
        }
    }

    private final MinecraftServer server;
    private final Path folder;
    /**
     * Bakes in flight, keyed by arena id.
     *
     * <p>One per map, not per player: the work is identical for everyone looking at the same map, and
     * letting each request start its own bake would multiply the chunk loading for no benefit.
     */
    private final Map<String, TerrainBake> bakes = new HashMap<>();
    /** Completed bakes, kept in memory so the second viewer costs no disk read. */
    private final Map<String, TerrainMap> terrainMaps = new HashMap<>();
    /** Players waiting for a bake to finish, so they are told the moment it lands. */
    private final Map<String, Set<java.util.UUID>> terrainWaiters = new HashMap<>();
    private final Map<String, Model.Arena> maps = new TreeMap<>();
    private final Map<String, Match> matches = new HashMap<>();
    public final Map<UUID, Member> members = new HashMap<>();
    public final Set<UUID> vehicles = new HashSet<>();
    private final Map<UUID, Long> requests = new HashMap<>();
    private final Map<UUID, Long> polls = new HashMap<>();
    private final Map<String, String> results = new HashMap<>();
    private final Catalog catalog = new Catalog();
    private final Board board;
    /**
     * Proxy-plugin / HUD-mod coexistence policy. Never null once constructed, so every call site can
     * use it without a null check on the hot path.
     */
    private final Compat compat;
    /**
     * Whether the sidebar is actually being displayed by this mod right now. The HUD mirrors the
     * sidebar's contents when this is false, so it must reflect the real outcome rather than the
     * intent — the write can be skipped because TAB owns the slot or because two matches are live.
     */
    private boolean sidebarShown;
    private long ticks;

    Engine(MinecraftServer server) {
        this.server = server;
        this.board = new Board(server);
        this.folder = server.getWorldPath(LevelResource.ROOT).resolve("battlefield_ops");
        this.compat = Compat.load(folder, BattlefieldOps.LOG);
        this.board.compat(compat);
        try {
            Files.createDirectories(folder.resolve("maps"));
            Files.createDirectories(folder.resolve("players"));
            try (var files = Files.list(folder.resolve("maps"))) {
                files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                    try {
                        Model.Arena arena = BattlefieldOps.GSON.fromJson(Files.readString(path), Model.Arena.class);
                        arena.validate();
                        maps.put(arena.id, arena);
                    } catch (Exception ex) {
                        BattlefieldOps.LOG.error("Invalid battlefield map {}", path, ex);
                    }
                });
            }
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取战地配置目录", ex);
        }
        catalog.scan(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) recover(player);
    }

    ServerLevel level(Model.Arena map) {
        // Explicit rather than letting a null dereference throw: this used to surface as a bare NPE that
        // callers caught and reported as "no data", which hid a real bug behind a plausible-looking message.
        Model.require(map != null, "内部错误：未指定地图，无法定位维度");
        ServerLevel level = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(map.dimension)));
        Model.require(level != null, "地图维度未加载：" + map.dimension);
        return level;
    }

    static String str(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    static int num(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    // ------------------------------------------------------------------ requests

    public void request(ServerPlayer player, String json) {
        if (json.length() > 20000) return;
        String selected = "";
        try {
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            String op = str(request, "op", "");
            Map<UUID, Long> limiter = op.equals("state") || op.equals("open") ? polls : requests;
            if (ticks - limiter.getOrDefault(player.getUUID(), -100L) < 2) return;
            limiter.put(player.getUUID(), ticks);
            selected = str(request, "map", "");
            switch (op) {
                case "open", "state" -> state(player, selected, op.equals("open"), "");
                case "catalog" -> catalog(player, request);
                case "terrain" -> terrain(player, request);
                case "mark" -> mark(player, request);
                case "fittings" -> fittings(player,request);
                // Compatibility state is server-wide, so it is an admin surface like scan/save.
                // Every mode change is re-validated here; the GUI sends a name, never a decision.
                case "compatMode" -> {
                    admin(player);
                    String extra = str(request, "extra", "");
                    int split = extra.indexOf('=');
                    Model.require(split > 0, "兼容参数格式错误");
                    String key = extra.substring(0, split);
                    String raw = extra.substring(split + 1);
                    // The Xaero-in-match switch is a boolean, not a tri-state mode, so it is handled
                    // before the Mode parsing rather than contorting it into one.
                    if (key.equals("hideXaero")) {
                        boolean on = switch (raw.toLowerCase(Locale.ROOT)) {
                            case "true", "1", "on", "yes" -> true;
                            case "false", "0", "off", "no" -> false;
                            default -> throw new IllegalArgumentException("hideXaero 需要 true 或 false");
                        };
                        compat.settings().hideXaeroInMatch = on;
                        compat.save(BattlefieldOps.LOG);
                        state(player, selected, false, on
                                ? "对局中将隐藏 Xaero 小地图" : "对局中保持 Xaero 小地图显示");
                        break;
                    }
                    String value = raw.toUpperCase(Locale.ROOT);
                    CompatPolicy.Mode mode;
                    try {
                        mode = CompatPolicy.Mode.valueOf(value);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("未知的兼容模式：" + value);
                    }
                    switch (key) {
                        case "sidebar" -> compat.settings().sidebar = mode;
                        case "teams" -> compat.settings().teams = mode;
                        case "minimap" -> compat.settings().minimap = mode;
                        default -> throw new IllegalArgumentException("未知的兼容项：" + key);
                    }
                    // Changing a mode is an explicit operator decision, so drop the sticky
                    // observations and let the surfaces be re-evaluated from scratch.
                    compat.reset();
                    compat.save(BattlefieldOps.LOG);
                    state(player, selected, false, "兼容设置已更新：" + key + " = " + value);
                }
                // Minimap corner. Its own op rather than another "compatMode" key because the value is a
                // corner name, not one of the AUTO/ALWAYS/NEVER tri-states that op parses.
                case "compatCorner" -> {
                    admin(player);
                    String value = Briefing.normaliseCorner(str(request, "value", ""));
                    compat.settings().minimapCorner = value;
                    compat.save(BattlefieldOps.LOG);
                    state(player, selected, false,
                            "小地图位置：" + Briefing.minimapCornerName(value));
                }
                case "compatReset" -> {
                    admin(player);
                    compat.reset();
                    state(player, selected, false, "已清除兼容自检结果，下次对局将重新检测");
                }
                case "compatSave" -> {
                    admin(player);
                    compat.save(BattlefieldOps.LOG);
                    state(player, selected, false, "兼容配置已写入 " + folder.resolve("compat.json"));
                }
                case "scan" -> {
                    admin(player);
                    catalog.scan(server);
                    // The station table is derived from the scan, so a rescan must drop it or the editor
                    // would keep showing weapons of mods that are no longer installed.
                    armamentCache = null;
                    state(player, selected, false,
                            "扫描完成：" + catalog.guns.size() + " 把武器，"
                                    + catalog.vehicles.size() + " 种载具；" + catalog.diagnostics);
                }
                case "save" -> {
                    admin(player);
                    Model.Arena arena = BattlefieldOps.GSON.fromJson(request.get("arena"), Model.Arena.class);
                    arena.validate();
                    if(request.has("baseRevision")){
                        String baseId=str(request,"baseId","");
                        Model.Arena base=maps.get(baseId);
                        MapFiles.expect(base==null?"":BattlefieldOps.GSON.toJson(base),str(request,"baseRevision",""));
                        Model.require(arena.id.equals(baseId)||!maps.containsKey(arena.id),"地图 ID 已存在，请换一个 ID");
                    }
                    Model.require(!matches.containsKey(arena.id), "请先结束这张地图的对局");
                    Model.require(maps.containsKey(arena.id) || maps.size() < 64, "最多 64 张地图");
                    level(arena);
                    catalog.check(arena);
                    save(arena);
                    maps.put(arena.id, arena);
                    Network.send(player,Map.of("type","saved","arena",arena));
                    state(player, arena.id, false, "地图已保存");
                }
                case "delete" -> {
                    admin(player);
                    Model.require(!matches.containsKey(selected), "请先结束对局");
                    Model.require(maps.containsKey(selected), "地图不存在");
                    Files.move(folder.resolve("maps").resolve(selected + ".json"),
                            folder.resolve("maps").resolve(selected + ".json.deleted"),
                            StandardCopyOption.REPLACE_EXISTING);
                    maps.remove(selected);
                    state(player, "", false, "已移到 .deleted 备份");
                }
                case "createRoom" -> {
                    admin(player);
                    createRoom(selected);
                    state(player, selected, false, "房间已创建，请先进入房间再选队");
                }
                case "selectTeam" -> {
                    selectTeam(player, num(request, "team", -1));
                    state(player, member(player).map, false, "阵营已选择");
                }
                case "start" -> {
                    admin(player);
                    // A new match re-observes every contested surface: TAB may have been restarted
                    // or reconfigured since the last one, and a stale "yielded" flag would silently
                    // keep the sidebar off for the whole match.
                    compat.reset();
                    Model.require(member(player).map.equals(selected), "请先进入该房间");
                    Match room = matches.get(selected);
                    Model.require(room != null && !room.running, "房间不存在或已经开始");
                    room.running = true;
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        Member m = members.get(p.getUUID());
                        if (m != null && m.map.equals(selected)) state(p, selected, false, "对局开始，可以部署");
                    }
                    state(player, selected, false, "对局开始，请选择阵营");
                }
                case "stop" -> {
                    admin(player);
                    finish(selected, "管理员结束对局");
                    state(player, selected, false, "对局已结束");
                }
                case "join" -> {
                    join(player, selected);
                    state(player, selected, false, "已进入房间，请选择红蓝方");
                }
                case "leave" -> {
                    leave(player);
                    state(player, selected, false, "已退出，原背包与位置已恢复");
                }
                case "redeploy" -> {
                    Member member = member(player);
                    Model.require(member.alive, "已在部署界面");
                    death(player, null);
                    state(player, member.map, true, "重新部署会扣除一次阵亡兵力");
                }
                case "deploy" -> {
                    deploy(player, str(request, "kit", ""), str(request, "spawn", ""), str(request, "vehicle", ""),
                            str(request,"primary",null),str(request,"secondary",null),request);
                    state(player, member(player).map, false, "已部署");
                }
                default -> throw new IllegalArgumentException("未知操作");
            }
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Battlefield request rejected", ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            state(player, selected, false, "操作失败：" + message);
        }
    }

    /** Cached armament description for the whole catalogue; cleared whenever the catalogue is rescanned. */
    private Map<String, Object> armamentCache;

    /**
     * A level to instantiate probe entities in.
     *
     * <p>Vehicle data is read from an entity instance, so reading a vehicle's weapon table needs a level
     * even though nothing is ever spawned into the world. Uses the overworld like the catalogue scanner,
     * and falls back to any loaded level so a server without one still answers instead of silently
     * reporting no data.
     */
    private ServerLevel probeLevel() {
        ServerLevel overworld = server.overworld();
        if (overworld != null) return overworld;
        var levels = server.getAllLevels().iterator();
        return levels.hasNext() ? levels.next() : null;
    }

    /**
     * Everything the editor needs to configure a vehicle's ammunition: slot count, stations, and the stack
     * size of every ammunition type.
     *
     * <p>Computed once for the whole catalogue and reused. Probing instantiates an entity per vehicle, and
     * this payload is rebuilt on every page change and every search keystroke, so doing it per request
     * would multiply an already expensive step for no benefit.
     */
    private Map<String, Object> armamentTable() {
        if (armamentCache != null) return armamentCache;
        Map<String, Object> out = new LinkedHashMap<>();
        for (String id : catalog.vehicles) {
            Map<String, Object> info = armamentInfo(id);
            if (info != null) out.put(id, info);
        }
        armamentCache = out;
        return out;
    }

    /**
     * One vehicle's stations, ammunition and rack size, or {@code null} when its data cannot be read.
     *
     * <p>Returning {@code null} rather than an empty object is deliberate: the editor distinguishes
     * "resource library not scanned" from "this vehicle has no readable weapons", and an empty object
     * would blur the two into the same useless message.
     */
    private Map<String, Object> armamentInfo(String id) {
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            if (type == null) return null;
            // The overworld, exactly as the catalogue scanner does. Passing a null arena here dereferenced
            // map.dimension inside level(), threw, and was swallowed — so every vehicle reported "no
            // weapon-station data" no matter how healthy its data was.
            ServerLevel level = probeLevel();
            if (level == null) return null;
            Entity probe = type.create(level);
            if (probe == null) return null;
            try {
                Map<String, Object> stations = new LinkedHashMap<>();
                for (var entry : VehicleEquip.stationStacks(probe).entrySet()) {
                    List<Map<String, Object>> ammo = new ArrayList<>();
                    for (ItemStack stack : entry.getValue()) {
                        Map<String, Object> one = new LinkedHashMap<>();
                        one.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        one.put("stack", Math.max(1, stack.getMaxStackSize()));
                        ammo.add(one);
                    }
                    if (!ammo.isEmpty()) stations.put(entry.getKey(), ammo);
                }
                if (stations.isEmpty()) return null;
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("slots", VehicleEquip.containerSize(probe));
                info.put("stations", stations);
                return info;
            } finally {
                probe.discard();
            }
        } catch (Exception ex) {
            // Warn, not debug: a vehicle whose data cannot be read shows up in the editor as "no weapon
            // stations", and without a log line there is nothing to correlate that message with.
            BattlefieldOps.LOG.warn("Cannot read weapon stations for " + id, ex);
            return null;
        }
    }

    private void catalog(ServerPlayer player, JsonObject request) {
        int page = Math.max(0, num(request, "page", 0));
        String query = str(request, "query", "").toLowerCase(Locale.ROOT);
        Model.require(query.length() < 100, "检索词过长");
        List<Catalog.Entry> all = catalog.entries.stream()
                .filter(entry -> LibrarySearch.matches(query,entry.id(),entry.name(),LibrarySource.searchable(entry.id(),entry.kind())))
                .toList();
        int from = Math.min(all.size(), page * 64);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "catalog");
        out.put("page", page);
        out.put("total", all.size());
        out.put("entries", all.subList(from, Math.min(all.size(), from + 64)));
        out.put("diagnostics", catalog.diagnostics);
        // Classification travels with the catalogue so the designer can label a vehicle as ground or
        // air while authorising it, instead of finding out later that no point can launch it.
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        for (String id : catalog.vehicles) {
            String type = catalog.vehicleTypes.get(id);
            if (type != null) types.put(id, Catalog.vehicleTypeLabel(type) + " / " + type);
            classes.put(id, String.valueOf(catalog.vehicleClass(id)));
        }
        out.put("vehicleTypes", types);
        out.put("vehicleClasses", classes);
        // Ammunition each whitelisted-on-the-catalogue vehicle accepts, so the armament editor can offer
        // real choices instead of asking the operator to type item ids from memory. Bounded by the
        // catalogue size, and per-vehicle lists are short (a handful of shell types at most).
        // Per station, not per vehicle: the editor must only offer ammunition a given station can
        // actually fire, otherwise an operator can assign a shell to a machine-gun slot and the
        // allocation silently drops it.
        // Per station: what it accepts and how large a stack of each is, plus the rack's slot count. The
        // editor needs all three to show a round count and to say whether it physically fits — a round
        // count alone cannot be checked, because a slot holds a different number of rounds per type.
        out.put("vehicleArmament", armamentTable());
        Network.send(player, out);
    }


    private void admin(ServerPlayer player) {
        Model.require(player.hasPermissions(2), "需要管理员权限（OP 2）");
    }

    private Member member(ServerPlayer player) {
        Member member = members.get(player.getUUID());
        Model.require(member != null, "尚未加入对局");
        return member;
    }

    private void save(Model.Arena map) throws IOException {
        MapFiles.save(folder.resolve("maps"),map.id,BattlefieldOps.GSON.toJson(map));
    }

    // ------------------------------------------------------------------ lifecycle

    private void createRoom(String id) {
        Model.Arena original = maps.get(id);
        Model.require(original != null, "先保存地图");
        Model.require(!matches.containsKey(id), "该地图已经有一个房间");
        original.validate();
        catalog.scan(server);
        catalog.check(original);
        for (Match active : matches.values()) {
            Model.Arena other = active.rules.map;
            if (other.dimension.equals(original.dimension)) {
                Model.require(!boxesOverlap(original, other), "与正在运行的地图范围重叠");
            }
        }
        Model.Arena copy = BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(original), Model.Arena.class);
        Match match = new Match(copy);
        matches.put(id, match);
        results.remove(id);
        sidebar(match);
    }

    private boolean boxesOverlap(Model.Arena a, Model.Arena b) {
        double ax = a.boundary.stream().mapToDouble(Model.Vec::x).min().orElse(0);
        double aX = a.boundary.stream().mapToDouble(Model.Vec::x).max().orElse(0);
        double az = a.boundary.stream().mapToDouble(Model.Vec::z).min().orElse(0);
        double aZ = a.boundary.stream().mapToDouble(Model.Vec::z).max().orElse(0);
        double bx = b.boundary.stream().mapToDouble(Model.Vec::x).min().orElse(0);
        double bX = b.boundary.stream().mapToDouble(Model.Vec::x).max().orElse(0);
        double bz = b.boundary.stream().mapToDouble(Model.Vec::z).min().orElse(0);
        double bZ = b.boundary.stream().mapToDouble(Model.Vec::z).max().orElse(0);
        return ax <= bX && aX >= bx && az <= bZ && aZ >= bz;
    }

    private void join(ServerPlayer player, String id) throws IOException {
        Model.require(!members.containsKey(player.getUUID()), "请先退出当前房间");
        Match room = matches.get(id);
        Model.require(room != null, "请先创建房间");
        Model.require(player.isAlive() && !player.isPassenger(), "请存活且离开载具后加入");
        recover(player);
        player.closeContainer();
        backup(player);
        members.put(player.getUUID(), new Member(id, player.getScoreboardName(), -1));
        player.getInventory().clearContent();
        player.setGameMode(GameType.SPECTATOR);
    }

    private void selectTeam(ServerPlayer player, int team) {
        Member member = member(player);
        Match room = matches.get(member.map);
        Model.require(room != null, "房间已关闭");
        Model.require(team == 0 || team == 1, "阵营无效");
        Model.require(!member.alive, "请在部署阶段选择阵营");
        Model.require(!room.running || member.team < 0 || member.team == team, "对局开始后不能更换阵营");
        long own = members.values().stream().filter(m -> m != member && m.map.equals(member.map) && m.team == team).count();
        long other = members.values().stream().filter(m -> m != member && m.map.equals(member.map) && m.team == 1-team).count();
        Model.require(own <= other || member.team == team, "该阵营人数较多，请加入另一方");
        Model.Spawn spawn = Tactics.safestSpawn(deploySpawns(room.rules, team), enemiesOf(room.rules, team));
        Model.require(spawn != null, "该阵营缺少出生点");
        member.team = team;
        board.apply(player, member.map, team, room.rules.map.friendlyFire, room.rules.map.hideEnemyNametags);
        // Put the deploy camera over the team's activity area instead of over their feet: the whole
        // point of the deploy screen is choosing where to go, and that needs a view of the area, not a
        // view of the spawn point. The player is a spectator at this stage, so this is only a viewpoint.
        DeployView.Placement view = deployView(room.rules.map, team);
        player.teleportTo(level(room.rules.map), view.x(), view.y(), view.z(), view.yaw(), view.pitch());
        String warning = DeployView.warning(room.rules.map.extent(room.rules.map.viewArea(team))[2], viewDistance());
        if (warning != null) announce(player, "部署视角", warning);
    }

    /** The overhead viewpoint for a team's deploy stage. */
    private DeployView.Placement deployView(Model.Arena map, int team) {
        double[] extent = map.extent(map.viewArea(team));
        double centreX = extent[0], centreZ = extent[1], span = extent[2];
        // Ground height at the centre, falling back to the middle of the allowed height band when the
        // level is not loaded there — the camera still has to be placed somewhere sane.
        double groundY = (map.minY + map.maxY) / 2.0;
        ServerLevel level = level(map);
        if (level != null && level.hasChunkAt(BlockPos.containing(centreX, map.maxY, centreZ))) {
            groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(centreX), (int) Math.floor(centreZ));
        }
        return DeployView.place(centreX, centreZ, span, groundY, viewDistance());
    }

    /**
     * The horizontal view distance the camera may use.
     *
     * <p>Read from the server's own setting rather than assumed: a server running a short render
     * distance cannot show a wide area from above, and the warning message needs the real number.
     */
    private int viewDistance() {
        try {
            return server.getPlayerList().getViewDistance() * 16;
        } catch (RuntimeException ex) {
            return DeployView.DEFAULT_VIEW;
        }
    }

    /** Own-team spawns that are legal right now, i.e. inside the current breakthrough stage. */
    private List<Model.Spawn> legalSpawns(Rules rules, int team) {
        Model.Arena map = rules.map;
        // A spawn outside the team's own activity zone is not a legal spawn: choosing it would deposit
        // the player outside their area, and the very next tick would start the boundary countdown.
        return map.spawns.stream()
                .filter(s -> s.team == team && (!rules.isBreakthrough() || s.sector == rules.stage))
                .filter(s -> map.inZone(team, s.x, s.z))
                .toList();
    }

    /** Every deploy site the team may use right now: spawns plus the capture points it holds. */
    private List<Model.Spawn> deploySpawns(Rules rules, int team) {
        return Deploy.sites(rules.map, team, rules.stage, rules.isBreakthrough(), Deploy.owners(rules))
                .stream().map(site -> site.asSpawn(team)).toList();
    }

    /**
     * Services supply pads: ammunition, battery and hull health for vehicles, health for players.
     *
     * <p>Runs once a second with the rest of the match tick. Restocking continuously from a pad would
     * make damage pointless while a vehicle sits in one, which is why this restores to the deployed
     * loadout rather than inventing more — the pad is a place to re-arm, not a place to become
     * invulnerable. Health is the exception the operator chooses to enable, and it is off unless the
     * pad says so.
     *
     * <p>Players are serviced whether or not they are in a vehicle, because a dismounted crew that has
     * to walk back to base for a bandage is not what "supply point" was asked for.
     */
    /**
     * Ages out expired manual marks.
     *
     * <p>There is deliberately no automatic team-wide visibility here. A contact one soldier can see does not
     * appear on a teammate's map by itself — the teammate has to be told, and the mark key is how they tell.
     * Without that, sharing costs nothing and the key would be pointless.
     */
    private void refreshMarks(Match match) {
        if (match == null) return;
        long now = System.currentTimeMillis();
        match.marks.removeIf(mark -> mark == null || !mark.active(now));
        while (match.marks.size() > Spotting.MAX_MARKS * 2) match.marks.removeFirst();
    }

    /**
     * The map id a match belongs to.
     *
     * <p>Read from the arena the match holds rather than stored twice: a match and its map cannot then
     * disagree about which map is being played.
     */
    private static String whereIs(Match match) {
        return match == null || match.rules == null || match.rules.map == null ? "" : match.rules.map.id;
    }

    /**
     * The map's armament limits for a live vehicle entity.
     *
     * <p>Resolved through the entity's registry id rather than remembered at spawn time: a hull can
     * outlive the configuration that created it (the operator saves a new draft mid-match), and reading
     * the id back keeps deployment and resupply consulting the same table.
     */
    private static VehicleArmament armamentFor(Model.Arena map, Entity vehicle) {
        if (map == null || vehicle == null) return new VehicleArmament();
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(vehicle.getType()).toString();
        return map.armament(id);
    }

    private void updateSupply(Model.Arena map, List<ServerPlayer> players) {        if (map.supplyPoints().isEmpty()) return;
        for (ServerPlayer player : players) {
            Member member = members.get(player.getUUID());
            if (member == null || member.team < 0 || !member.alive || !player.isAlive()) continue;
            VehicleSupply.Supply pad = map.supplyAt(member.team, player.getX(), player.getY(), player.getZ());
            if (pad == null) continue;
            Entity riding = player.getVehicle();
            if (riding != null && vehicles.contains(riding.getUUID())) {
                // While riding, the hull is what needs servicing; the driver takes no damage anyway.
                // The map's own armament limits apply here too: a pad must not be able to load
                // ammunition the operator restricted away, or it becomes a way around the restriction.
                if (VehicleEquip.resupply(riding, pad, armamentFor(map, riding))) {
                    player.displayClientMessage(Component.literal("§b补给点 §f已补充 " + pad.id()), true);
                }
            } else if (VehicleEquip.resupplyPlayer(player, pad)) {
                player.displayClientMessage(Component.literal("§b补给点 §f已恢复生命 " + pad.id()), true);
            }
        }
    }

    /**
     * Removes hulls that nobody is using and that no living player of that team could return to.
     *
     * <p>The per-match vehicle cap counts every hull in the world, so a vehicle whose crew died and
     * respawned at base would keep its slot for the rest of the match. On a map with a limit of six that
     * is the entire allowance gone after six deaths, and the symptom is "vehicles stopped working"
     * rather than "you left a tank in a field".
     *
     * <p>Deliberately conservative: an empty hull is only removed when it also has no living member of
     * its own team within {@link #VEHICLE_ORPHAN_RANGE} blocks. A tank parked next to a firefight is a
     * usable asset and stays; one abandoned on the far side of the map after its driver respawned is
     * just a used-up slot. Crew members who dismount and walk away keep their vehicle as long as they
     * stay nearby, which is the normal case of parking and coming back.
     */
    private void reapEmptyVehicles(Match match) {
        if (match.vehicles.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(match.vehicles)) {
            Entity vehicle = findEntity(uuid);
            if (vehicle == null || vehicle.isRemoved()) {
                match.vehicles.remove(uuid);
                match.vehicleTeams.remove(uuid);
                vehicles.remove(uuid);
                continue;
            }
            if (!vehicle.getPassengers().isEmpty()) continue;
            int owner = match.vehicleTeams.getOrDefault(uuid, -1);
            if (owner < 0) continue;
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Member member = members.get(player.getUUID());
                if (member == null || member.team != owner || !member.alive) continue;
                nearest = Math.min(nearest, Math.sqrt(player.distanceToSqr(vehicle)));
            }
            if (!VehicleDeploy.orphaned(vehicle.getPassengers().size(), nearest, VEHICLE_ORPHAN_RANGE)) {
                continue;
            }
            vehicle.discard();
            match.vehicles.remove(uuid);
            match.vehicleTeams.remove(uuid);
            vehicles.remove(uuid);
        }
    }

    /** Living enemy positions for the given team, used for spawn selection and glow checks. */
    private List<Tactics.At> enemiesOf(Rules rules, int team) {
        List<Tactics.At> positions = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            Member mate = members.get(other.getUUID());
            if (mate == null || mate.team == team || !mate.alive) continue;
            if (!mate.map.equals(rules.map.id)) continue;
            positions.add(new Tactics.At(other.getX(), other.getY(), other.getZ()));
        }
        return positions;
    }

    private void backup(ServerPlayer player) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", player.getInventory().save(new ListTag()));
        // Accessories live outside the vanilla inventory, so they need saving separately. Without this the
        // kit's accessories stayed equipped after leaving a match: the player kept them for free, and the
        // backup no longer described what they actually owned.
        ListTag curios = CuriosSupport.save(player);
        if (curios != null) tag.put("curios", curios);
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        tag.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
        tag.putFloat("health", player.getHealth());
        tag.putInt("food", player.getFoodData().getFoodLevel());
        tag.putFloat("saturation", player.getFoodData().getSaturationLevel());
        Path file = folder.resolve("players").resolve(player.getUUID() + ".nbt");
        Model.require(!Files.exists(file), "尚有未恢复的背包备份，请先联系管理员处理");
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, tmp);
        Files.move(tmp, file);
    }

    /** Restores a backed-up inventory, or does nothing when the player has no pending backup. */
    public void recover(ServerPlayer player) {
        if (members.containsKey(player.getUUID())) return;
        Path file = folder.resolve("players").resolve(player.getUUID() + ".nbt");
        if (!Files.exists(file)) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))));
            Model.require(level != null, "原维度不存在，备份已保留等待恢复");
            player.closeContainer();
            player.stopRiding();
            player.getInventory().clearContent();
            player.getInventory().load(tag.getList("inventory", Tag.TAG_COMPOUND));
            // Clear first, then restore: whatever is equipped now was issued by the match, and an old
            // backup written before accessories were supported must not leave them behind either.
            CuriosSupport.clear(player);
            if (tag.contains("curios")) {
                CuriosSupport.load(player, tag.getList("curios", Tag.TAG_COMPOUND));
            }
            player.setGameMode(GameType.byId(tag.getInt("mode")));
            player.teleportTo(level, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getFloat("yaw"), tag.getFloat("pitch"));
            player.setHealth(Math.max(1, Math.min(player.getMaxHealth(), tag.getFloat("health"))));
            player.getFoodData().setFoodLevel(tag.getInt("food"));
            player.getFoodData().setSaturation(tag.getFloat("saturation"));
            player.inventoryMenu.broadcastChanges();
            Files.delete(file);
        } catch (Exception ex) {
            BattlefieldOps.LOG.error("Cannot restore player {}", player.getUUID(), ex);
            player.sendSystemMessage(Component.literal("战地背包恢复失败，备份已保留：" + ex.getMessage()));
        }
    }

    public void leave(ServerPlayer player) {
        Member member = members.remove(player.getUUID());
        if (member != null) {
            player.stopRiding();
            board.clearPlayer(player);
            clearGlow(player, member);
            recover(player);
            Match match = matches.get(member.map);
            if (match != null) sidebar(match);
        }
        requests.remove(player.getUUID());
        polls.remove(player.getUUID());
    }

    private void exitAtCurrentPosition(ServerPlayer player) {
        ServerLevel current = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();
        leave(player);
        player.teleportTo(current, x, y, z, yaw, pitch);
        player.fallDistance = 0;
    }

    private void teleport(ServerPlayer player, ServerLevel level, Model.Spawn spawn) {
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    // ------------------------------------------------------------------ deployment

    static Map<String,String> selections(JsonObject request,String key){
        Map<String,String> out=new LinkedHashMap<>();
        if(!request.has(key))return out;
        for(var entry:request.getAsJsonObject(key).entrySet()){
            Model.require(out.size()<32&&entry.getKey().length()<128,"改装参数过多或过长");
            String value=entry.getValue().getAsString();Model.require(value.length()<160,"配件 ID 过长");out.put(entry.getKey(),value);
        }
        return out;
    }
    private void fittings(ServerPlayer player,JsonObject request)throws Exception{
        Member member=member(player);Model.Arena map=maps.get(member.map);
        Model.require(map!=null,"请先进入房间");
        String id=str(request,"id",""),kind=str(request,"kind","");
        List<Fittings.Group> groups;
        if(kind.equals("gun")){
            Model.require(map.kits.stream().anyMatch(k->k.options(true).contains(id)||k.options(false).contains(id)),"武器未获本图授权");
            groups=Fittings.gunOptions(catalog.gun(player,id,0).getFirst());
        }else{
            Model.require(kind.equals("vehicle")&&map.vehicles.contains(id),"载具未获本图授权");
            EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            Entity sample=type.create(level(map));Model.require(sample!=null&&catalog.isVehicleEntity(sample),"载具不可用");
            groups=Fittings.vehicleOptions(sample);
        }
        Network.send(player,Map.of("type","fittings","id",id,"kind",kind,"groups",groups));
    }

    private void deploy(ServerPlayer player, String kitId, String spawnId, String vehicle, String primary, String secondary, JsonObject deployment) throws Exception {
        Member member = member(player);
        Match match = matches.get(member.map);
        Model.require(match != null && match.running && member.team >= 0, "请等待开局并选择阵营");
        Model.Arena map = match.rules.map;
        Model.require(!member.alive, "只能在部署阶段切换配装");
        Model.require(!member.downed, "倒地状态下无法部署，等待队友救援或出血阵亡");
        Model.require(ticks >= member.readyAt, "重生倒计时尚未结束");

        Model.Kit kit = map.kits.stream()
                .filter(k -> k.id.equals(kitId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("配装未获地图授权：" + kitId));

        // Deploy sites are spawn points plus capture points this team holds. Computed once, from the
        // same rule the client draws with, so the two cannot disagree about what is legal.
        List<Deploy.Site> sites = Deploy.sites(map, member.team, match.rules.stage,
                match.rules.isBreakthrough(), Deploy.owners(match.rules));
        Model.require(!sites.isEmpty(), "本方当前没有可用的部署点");
        Deploy.Site requested = sites.stream()
                .filter(s -> s.id().equals(spawnId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("部署点未获授权或阶段不符：" + spawnId));
        Model.Spawn requestedSpawn = requested.asSpawn(member.team);

        // Validate the vehicle *before* choosing a site, because the site fallback below must be
        // restricted to sites that can actually launch it. Everything here is a pure check: nothing is
        // written, so a rejected request leaves the player exactly as they were.
        char vehicleClass = 0;
        EntityType<?> vehicleType = null;
        if (!vehicle.isEmpty()) {
            Model.require(map.vehicles.contains(vehicle) && catalog.isVehicle(vehicle), "载具未获地图授权：" + vehicle);
            Model.require(catalog.deployableVehicle(vehicle), "该载具不能用于部署：" + vehicle);
            // Vehicles are rationed by cooldown and by the per-match cap, not by personal score. Score
            // is now purely a record of what the player did, so spending it on a hull would also make
            // the scoreboard read as a wallet — which is exactly the confusion this removes.
            Model.require(ticks >= member.vehicleAt,
                    "载具冷却尚未结束（" + (member.vehicleAt - ticks + 19) / 20 + " 秒）");
            match.vehicles.removeIf(uuid -> findEntity(uuid) == null);
            Model.require(teamVehicles(match,member.team) < map.vehicleLimitFor(member.team), "本方载具名额已满");
            vehicleClass = catalog.vehicleClass(vehicle);
            vehicleType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(vehicle));
            Model.require(vehicleType != null, "载具实体未注册：" + vehicle);
            // The requested site must be able to launch this class. Checked up front so the player gets
            // a precise message instead of being silently moved somewhere that cannot launch it.
            Model.require(requested.allows(vehicleClass), vehicleClass == 'A'
                    ? requested.id() + " 不是飞机点位，无法放飞航空器"
                    : requested.id() + " 不提供载具刷新");
        }

        // A site camped by the enemy is worse than useless, so fall back to the safest legal one.
        // The choice is still made here: the client cannot ask for an unauthorised site.
        //
        // The candidate list must be restricted to sites that can launch the requested vehicle. Without
        // that restriction, Tactics.chooseSpawn looks at *every* site when the requested one is camped,
        // and relocates the player to one that launches nothing but infantry. The vehicle check then
        // rejected the whole deployment — so an enemy standing 16 blocks from the airfield turned
        // "bring a jet" into "you cannot deploy at all". Infantry keep the full list.
        List<Model.Spawn> candidateSpawns = sites.stream().map(s -> s.asSpawn(member.team)).toList();
        if (!vehicle.isEmpty()) {
            List<Model.Spawn> launchable = Deploy.sitesFor(sites, vehicleClass).stream()
                    .map(s -> s.asSpawn(member.team)).toList();
            if (!launchable.isEmpty()) candidateSpawns = launchable;
        }
        List<Tactics.At> enemies = enemiesOf(match.rules, member.team);
        Model.Spawn chosen = Tactics.chooseSpawn(requestedSpawn, candidateSpawns, enemies);
        Deploy.Site spawn = sites.stream().filter(s -> s.id().equals(chosen.id)).findFirst().orElse(requested);
        if (!spawn.id().equals(requested.id())) {
            announce(player, "部署点被敌人压制", "已改到 " + spawn.id());
        }
        // A safety net rather than a possibility: candidateSpawns only contains launchable sites, so
        // this can fire only if the map changed between the two checks. Re-checking is cheap and turns
        // a silent "deployed without the vehicle" into a clear refusal. Routed through VehicleDeploy so
        // the rule has one home and is covered by VehicleDeploySim.
        if (vehicleType != null) {
            VehicleDeploy.Gate gate = VehicleDeploy.Gate.of(
                    spawn.allows(vehicleClass), teamVehicles(match,member.team), map.vehicleLimitFor(member.team), true);
            Model.require(gate.success(), gate.blocker());
        }

        List<ItemStack> mainItems=catalog.gun(player,kit.select(true,primary),kit.ammoStacks);
        List<ItemStack> sideItems=catalog.gun(player,kit.select(false,secondary),kit.ammoStacks);
        Map<String,String> mainFittings=selections(deployment,"primaryFittings"),sideFittings=selections(deployment,"secondaryFittings"),vehicleFittings=selections(deployment,"vehicleFittings");
        if(!mainItems.isEmpty())Fittings.applyGun(mainItems.getFirst(),mainFittings,player.registryAccess());
        else Model.require(mainFittings.isEmpty(),"没有主武器可改装");
        if(!sideItems.isEmpty())Fittings.applyGun(sideItems.getFirst(),sideFittings,player.registryAccess());
        else Model.require(sideFittings.isEmpty(),"没有副武器可改装");
        if(vehicleType!=null){
            Entity sample=vehicleType.create(level(map));Model.require(sample!=null,"载具数据不可用");
            List<Fittings.Group> groups=Fittings.vehicleOptions(sample);
            Fittings.validate(groups,vehicleFittings);
            // The operator's allow-list is enforced here, not only where the hull is filled in. Filling
            // a tank in around a restricted round would still let a client send that station index, and
            // the vehicle would switch to ammunition the map forbade. Rejecting the request is the only
            // point at which the restriction is actually authoritative.
            VehicleArmament armament=armamentFor(map,sample);
            Fittings.restrict(groups,vehicleFittings,armament);
        }
        else Model.require(vehicleFittings.isEmpty(),"未选择载具");
        List<ItemStack> items=new ArrayList<>(mainItems);items.addAll(sideItems);
        // Kit gear is validated and appended before the capacity check so that a kit overflowing the
        // inventory is refused outright rather than partially delivered.
        List<ItemStack> gear=KitItems.build(kit, player);
        items.addAll(gear);
        Model.require(items.size() <= 35, "配装超过背包容量");

        ServerLevel level = level(map);
        level.getChunkAt(BlockPos.containing(spawn.x(), spawn.y(), spawn.z()));
        Model.Spawn target = spawn.asSpawn(member.team);
        Model.require(level.noCollision(player, player.getBoundingBox()
                        .move(target.x - player.getX(), target.y - player.getY(), target.z - player.getZ())),
                "部署点空间被方块占用，请调整位置");

        player.closeContainer();
        player.getInventory().clearContent();
        for (ItemStack stack : items) player.getInventory().add(stack);
        // Armour and accessories are worn, not handed over: a chestplate left in the inventory means the
        // player deploys unarmoured, which defeats configuring armour at all. Done after the inventory is
        // cleared so a previous deployment's armour cannot survive into this one. Accessories live outside
        // the inventory and therefore need clearing explicitly.
        CuriosSupport.clear(player);
        KitItems.wear(kit, player);
        player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 16));
        teleport(player, level, target);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        member.alive = true;
        member.outside = 0;
        member.warnedSecond = -1;
        member.killCredited = false;
        member.reviving = null;
        clearGlow(player, member);
        player.inventoryMenu.broadcastChanges();
        player.setGlowingTag(false);

        if (vehicleType != null) {
            String result = spawnVehicle(player, level, map, match, member, vehicleType, spawn,vehicleFittings);
            if (result != null) {
                // The player has already been teleported, re-equipped and marked alive at this point, so
                // the deployment cannot simply be undone. Returning them to the deploy screen is the
                // honest outcome: nothing was deducted, so re-deploying is free apart from the wait they
                // would have had anyway. This used to leave them standing at the site with the message
                // "未扣分", which reads as "you have your vehicle" to someone who does not read closely.
                member.alive = false;
                member.readyAt = ticks;
                player.stopRiding();
                state(player, member.map, true, result);
                player.sendSystemMessage(Component.literal("§e" + result + " —— 已返回部署界面，未扣任何费用"));
                throw new IllegalArgumentException(result);
            }
        }
        announce(player, "已部署 · " + spawn.id(), Board.label(member.team) + " · " + kit.id);
    }

    /**
     * Creates the vehicle <em>at the spawn point, with the player already inside</em>.
     *
     * <p>This replaces the previous behaviour, which put the player on the spawn and the hull 8 blocks
     * to the east: on a runway that offset put the aircraft on its side, and on uneven ground it put
     * the hull inside terrain. Seating the driver first also means the vehicle cannot be left ownerless
     * if the player disconnects during the same tick.
     *
     * @return a warning to show the player, or {@code null} on success
     */
    private String spawnVehicle(ServerPlayer player, ServerLevel level, Model.Arena map, Match match,
                                Member member, EntityType<?> vehicleType, Deploy.Site site,Map<String,String> fittings) {
        Entity vehicle;
        try {
            vehicle = vehicleType.create(level);
        } catch (Exception ex) {
            return "载具生成失败，未扣分";
        }
        if (vehicle == null || !catalog.isVehicleEntity(vehicle)) return "载具生成失败，未扣分";

        // Place the hull exactly on the site, facing its yaw, then verify it fits.
        vehicle.moveTo(site.x(), site.y(), site.z(), site.yaw(), 0);
        if (!Model.contains(map.boundary, vehicle.getX(), vehicle.getZ())) {
            return "载具刷新点在地图边界外";
        }
        // Block collision only. Checking *entity* collisions here would always fail: the deploying
        // player is standing on this exact spot, having been teleported there a few lines earlier, so
        // the hull would collide with its own driver and every single deployment would be refused with
        // "空间不足". Whether the seat accepts them is decided by the seat itself, below.
        if (!level.noBlockCollision(vehicle, vehicle.getBoundingBox())) {
            return "刷新点空间不足以放下载具";
        }

        try{Fittings.applyVehicle(vehicle,fittings);}catch(ReflectiveOperationException|IllegalArgumentException ex){vehicle.discard();return "载具弹种应用失败："+ex.getMessage();}
        // Operator-configured attachments, applied after the ammunition choice so a station's allowed
        // ammunition list has already been honoured. A failure here is logged and ignored rather than
        // costing the player their vehicle: an attachment index that this weapon does not define is a
        // configuration mistake, not a reason to refuse the deployment.
        VehicleArmament armament = armamentFor(map, vehicle);
        try {
            Fittings.applyVehicleAttachments(vehicle, armament);
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            BattlefieldOps.LOG.warn("Cannot apply configured vehicle attachments", ex);
        }
        vehicle.addTag("bfops_vehicle");
        UUID uuid = vehicle.getUUID();
        vehicles.add(uuid);
        match.vehicles.add(uuid);
        match.vehicleTeams.put(uuid, member.team);
        if (!level.addFreshEntity(vehicle)) {
            vehicles.remove(uuid);
            match.vehicles.remove(uuid);
            match.vehicleTeams.remove(uuid);
            return "载具生成失败，未扣分";
        }
        // Fill the hull in *before* seating: the seat's own validation (some vehicles refuse a driver
        // until they are powered) is allowed to look at a working vehicle, not an empty shell.
        // A hull from EntityType#create has no rounds and no charge by default. The vehicle type is read
        // by VehicleEquip from the addon's own table, so the loadout depends on one source rather than
        // on the catalogue's copy of the same field.
        String equipSummary=VehicleEquip.equip(vehicle, VehicleEquip.kindOf(vehicle), armament);
        player.sendSystemMessage(Component.literal("载具配装："+equipSummary));

        // Seat the driver. If the seat refuses them the vehicle is discarded rather than left behind:
        // an unoccupied hull would still count against the per-match cap, so a player who hit this
        // repeatedly would quietly ratchet the team's allowance down to zero while being told that
        // nothing had happened. Nothing was deducted, so discarding the hull costs them only the wait,
        // which is also not charged in this branch.
        if (!player.startRiding(vehicle, true)) {
            vehicle.discard();
            vehicles.remove(uuid);
            match.vehicles.remove(uuid);
            match.vehicleTeams.remove(uuid);
            return "未能坐进载具，载具已回收，未扣冷却";
        }
        // Only the cooldown is charged, and only on success. Nothing is deducted from the player.
        member.vehicleAt = ticks + map.vehicleCooldown * 20L;
        return null;
    }

    private int teamVehicles(Match match,int team){
        return (int)match.vehicles.stream().filter(id->match.vehicleTeams.getOrDefault(id,-1)==team).filter(id->{Entity e=findEntity(id);return e!=null&&e.isAlive();}).count();
    }

    private Entity findEntity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    // ------------------------------------------------------------------ combat

    /** Blocks friendly fire, cross-match fire and hits on players who are not deployed yet. */
    public boolean blockDamage(ServerPlayer victim, Entity attacker) {
        Member target = members.get(victim.getUUID());
        if (target != null && !target.alive) return true;
        // A downed player is out of the fight; letting them be shot again would just be a slower bleed-out.
        if (target != null && target.downed) return true;
        if (attacker instanceof ServerPlayer shooter) {
            Member source = members.get(shooter.getUUID());
            if ((target == null) != (source == null)) return true;
            if (target != null && source != null) {
                if (!target.map.equals(source.map) || !source.alive) return true;
                Match match = matches.get(target.map);
                return match == null || (target.team == source.team && !match.rules.map.friendlyFire);
            }
        }
        return false;
    }

    /**
     * Advances every downed player: bleed-out, or a revive being held by a nearby teammate.
     *
     * <p>Runs every tick rather than once a second, because the revive bar is a sub-second interaction and a
     * one-second granularity would make a four-second revive feel arbitrary.
     */
    private void tickDowned() {
        for (ServerPlayer patient : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Member member = members.get(patient.getUUID());
            if (member == null || !member.downed) continue;
            Match match = matches.get(member.map);
            if (match == null) continue;
            int required = Medic.reviveTicks(match.rules.map.reviveSeconds);

            ServerPlayer helper = null;
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other == patient) continue;
                Member otherMember = members.get(other.getUUID());
                if (otherMember == null || !otherMember.alive || otherMember.downed) continue;
                if (!otherMember.map.equals(member.map) || otherMember.team != member.team) continue;
                // Must have actually started this revive and still be standing next to the patient.
                if (!patient.getUUID().equals(otherMember.reviving)) continue;
                if (other.distanceToSqr(patient) > 3.5 * 3.5) continue;
                helper = other;
                break;
            }
            // Re-applied every tick: vanilla derives the pose from movement and would stand them up again.
            downPose(patient, true);
            member.reviveProgress = Medic.progress(member.reviveProgress, helper != null, required);
            if (member.reviveProgress >= required) {
                revive(patient, member, helper);
                continue;
            }
            if (Medic.expired(member.bleedOutAt, ticks)) {
                ServerPlayer killer = member.downedBy == null ? null
                        : server.getPlayerList().getPlayer(member.downedBy);
                member.downed = false;
                member.reviveProgress = 0;
                clearReviver(patient.getUUID());
                downPose(patient, false);
                death(patient, killer);
            }
        }
    }

    /**
     * Turns a lethal hit into a downed state, if this map uses one.
     *
     * @return true when the hit was converted, meaning the caller must cancel the damage
     */
    public boolean tryDown(ServerPlayer victim, ServerPlayer attacker, float damage) {
        Member member = members.get(victim.getUUID());
        if (member == null || !member.alive || member.downed) return false;
        Match match = matches.get(member.map);
        if (match == null || !match.rules.map.downEnabled) return false;
        if (!victim.isAlive() || victim.getHealth() - damage > 0) return false;
        // Being downed inside a moving vehicle makes no sense and would strand them: leave those deaths alone.
        if (victim.getVehicle() != null) return false;

        Member shooter = attacker == null ? null : members.get(attacker.getUUID());
        member.downed = true;
        member.bleedOutAt = ticks + Medic.bleedOutTicks(match.rules.map.downSeconds);
        member.reviveProgress = 0;
        member.reviving = null;
        member.downedByName = shooter == null ? "" : shooter.name;
        member.downedBy = shooter == null ? null : attacker.getUUID();
        victim.setHealth(1);
        // Downed means prone and crawling, and nothing else. No blindness, no weakness: the player keeps their
        // view and their gun, and is limited only by being on the floor and slow. Slowness I is the crawl
        // itself rather than an extra penalty, and the swimming pose is what visibly puts them on the ground.
        downPose(victim, true);
        // A knockdown is a kill: the score, streak, headshot, assist and revenge all settle now. If the
        // victim is later revived, that only decides whether the body gets back up — the kill stays credited.
        member.killCredited = true;
        creditKill(victim, attacker);
        victim.displayClientMessage(Component.literal("§c你倒下了！§f等待队友救援，"
                + match.rules.map.downSeconds + " 秒后出血阵亡"), true);
        if (attacker != null) {
            attacker.displayClientMessage(Component.literal("§e" + member.name
                    + " 被你击倒（可被救起）"), true);
        }
        for (ServerPlayer mate : server.getPlayerList().getPlayers()) {
            Member other = members.get(mate.getUUID());
            if (other == null || other.team != member.team || other == member) continue;
            if (!other.map.equals(member.map) || !other.alive) continue;
            mate.displayClientMessage(Component.literal("§e队友 §f" + member.name
                    + " §e倒地，空手右键救援"), false);
        }
        state(victim, member.map, false, "");
        return true;
    }

    /**
     * Applies or removes the prone crawl.
     *
     * <p>The pose has to be re-applied every tick: vanilla recomputes the pose from movement each tick and will
     * otherwise stand the player back up, which makes a downed player look untouched.
     */
    private static void downPose(ServerPlayer player, boolean downed) {
        if (player == null) return;
        if (downed) {
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, false));
        } else {
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    /**
     * Starts a revive: an empty-handed teammate right-clicks a downed player.
     *
     * <p>The click only begins the channel — completing it still requires the helper to stay within reach
     * for the map's configured seconds. Validated server-side because the client only signals intent, and
     * a client-supplied target would otherwise let anyone "revive" an enemy or a player across the map.
     *
     * @return true when the interaction was consumed as a revive
     */
    public boolean reviveStart(ServerPlayer helper, ServerPlayer target) {
        Member helperMember = members.get(helper.getUUID());
        Member targetMember = members.get(target.getUUID());
        if (helperMember == null || targetMember == null) return false;
        if (!helperMember.alive || helperMember.downed) return false;
        if (!targetMember.downed) return false;
        if (!helperMember.map.equals(targetMember.map) || helperMember.team != targetMember.team) return false;
        // Empty hand only: the player must put their weapon away to help, so reviving is a deliberate choice.
        if (!helper.getMainHandItem().isEmpty()) return false;
        if (helper.distanceToSqr(target) > 3.5 * 3.5) return false;
        helperMember.reviving = target.getUUID();
        helper.displayClientMessage(Component.literal("§a正在救援 §f" + targetMember.name
                + "§a，保持靠近…"), true);
        return true;
    }

    /** Clears anyone currently reviving the given downed player. */
    private void clearReviver(java.util.UUID patientId) {
        for (Member member : members.values()) {
            if (patientId.equals(member.reviving)) member.reviving = null;
        }
    }

    /** Brings a downed player back, and tells both sides. */
    private void revive(ServerPlayer patient, Member member, ServerPlayer helper) {
        Match match = matches.get(member.map);
        member.downed = false;
        member.reviveProgress = 0;
        member.bleedOutAt = 0;
        member.killCredited = false;
        clearReviver(patient.getUUID());
        downPose(patient, false);
        float restored = Medic.revivedHealth(
                match == null ? 0 : match.rules.map.reviveHealth, patient.getMaxHealth());
        patient.setHealth(restored);
        patient.displayClientMessage(Component.literal("§a你被救起了"), true);
        if (helper != null) {
            Member helperMember = members.get(helper.getUUID());
            if (helperMember != null && match != null) {
                helperMember.score += match.rules.map.assistScore;
                helperMember.credits += match.rules.map.assistScore;
            }
            helper.displayClientMessage(Component.literal("§a已救起 §f" + member.name), true);
            award(helper, "救援", match == null ? 0 : match.rules.map.assistScore,
                    member.name, "assist");
        }
        state(patient, member.map, false, "");
    }

    public void death(ServerPlayer player, ServerPlayer killer) {
        Member member = members.get(player.getUUID());
        if (member == null) return;
        Match match = matches.get(member.map);
        if (match == null) return;
        player.setHealth(player.getMaxHealth());
        if (!member.alive) return;
        member.alive = false;
        member.deaths++;
        member.readyAt = ticks + match.rules.map.respawnSeconds * 20L;
        member.outside = 0;
        player.stopRiding();
        player.setGameMode(GameType.SPECTATOR);
        player.clearFire();
        player.getInventory().clearContent();
        match.rules.death(member.team);

        // A knockdown already paid the kill; a direct death pays it now. The victim's "you died" line is
        // separate because a downed player was told "you are down" instead.
        Member credited = member.killCredited ? null : creditKill(player, killer);
        if (credited != null) {
            player.sendSystemMessage(Component.literal("§c被 §f" + credited.name + " §c击杀"));
        } else {
            player.sendSystemMessage(Component.literal("§7你阵亡了"));
        }
        clearGlow(player, member);
        player.sendSystemMessage(Component.literal("§7重生倒计时 §f"
                + match.rules.map.respawnSeconds + "§7 秒，按部署键重新部署"));
        state(player, member.map, true, "已阵亡，等待重新部署");
    }

    /**
     * Credits a kill to the killer: score, streak, headshot, revenge, assists and the kill feed.
     *
     * <p>Called once per elimination — at the knockdown when the map uses a revive system (a knockdown is a
     * kill, and the revive only decides whether the body gets back up), and at death otherwise. The victim's
     * own message is left to the caller, because a downed player is told "you are down" rather than "you died".
     *
     * @return the credited killer, or null when the death has no credited killer (fall, environment, etc.)
     */
    private Member creditKill(ServerPlayer victim, ServerPlayer killer) {
        Member member = members.get(victim.getUUID());
        if (member == null) return null;
        Match match = matches.get(member.map);
        if (match == null) return null;
        Member credited = killer == null ? null : members.get(killer.getUUID());
        if (credited != null && credited != member && credited.alive
                && credited.map.equals(member.map) && credited.team != member.team) {
            credited.kills++;
            credited.score += match.rules.map.killScore;
            credited.credits += match.rules.map.killScore;
            killer.sendSystemMessage(Component.literal("§a击杀 §f" + member.name
                    + " §7(+" + match.rules.map.killScore + ")"));
            long now = System.currentTimeMillis();
            Model.Arena map = match.rules.map;
            long comboWindow = map.comboSeconds * 1000L;
            long assistWindow = map.assistSeconds * 1000L;

            // Streak first: the label replaces the plain "击杀" so the feed reads as one award, not two.
            int count = Awards.streak(match.streak.getOrDefault(killer.getUUID(), 0),
                    match.lastKillAt.getOrDefault(killer.getUUID(), 0L), now, comboWindow);
            match.streak.put(killer.getUUID(), count);
            match.lastKillAt.put(killer.getUUID(), now);
            int killPoints = map.killScore + (count > 1 ? map.comboBonus : 0);
            credited.score += count > 1 ? map.comboBonus : 0;
            credited.credits += count > 1 ? map.comboBonus : 0;
            award(killer, Awards.streakLabel(count), killPoints, member.name, "kill");

            // Headshot: judged from the height of the blow that landed, not from where the attacker stood.
            boolean headshot = Awards.headshot(Awards.lastHitY(match.hits.get(victim.getUUID())),
                    victim.getY(), victim.getY() + victim.getBbHeight());
            if (headshot) {
                credited.score += map.headshotBonus;
                credited.credits += map.headshotBonus;
                award(killer, "爆头", map.headshotBonus, member.name, "headshot");
            }

            // Revenge: this player's last death was caused by the player they just killed.
            if (Awards.revenge(match.lastKiller.get(killer.getUUID()), victim.getUUID())) {
                credited.score += map.revengeBonus;
                credited.credits += map.revengeBonus;
                award(killer, "复仇", map.revengeBonus, member.name, "revenge");
            }

            // Assists: everyone else who put damage in recently. Paid even if they have since died, because
            // the damage they dealt is what made the kill possible.
            List<UUID> helpers = Awards.assists(match.hits.get(victim.getUUID()), killer.getUUID(),
                    now, assistWindow);
            for (UUID helper : helpers) {
                Member helperMember = members.get(helper);
                if (helperMember == null || helperMember.team == member.team) continue;
                helperMember.score += map.assistScore;
                helperMember.credits += map.assistScore;
                ServerPlayer helperPlayer = server.getPlayerList().getPlayer(helper);
                if (helperPlayer != null) {
                    award(helperPlayer, "助攻", map.assistScore, member.name, "assist");
                }
            }
            // The victim's ledger is spent; a later death starts fresh.
            match.hits.remove(victim.getUUID());
            match.lastKiller.put(victim.getUUID(), killer.getUUID());
        }
        broadcastKill(credited, member, match);
        return credited;
    }

    /** Broadcasts a one-line kill feed to everyone in the same match. */
    private void broadcastKill(Member killer, Member victim, Match match) {
        String prefix = killer == null ? "§7" : killer.team == 0 ? "§9" : "§c";
        String text = (killer == null ? "§7阵亡" : prefix + killer.name + " §7→ "
                + (victim.team == 0 ? "§9" : "§c") + victim.name);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Member member = members.get(player.getUUID());
            if (member == null || member.team < 0 || !member.map.equals(match.rules.map.id)) continue;
            player.sendSystemMessage(Component.literal(text));
        }
    }

    // ------------------------------------------------------------------ tick

    /**
     * Advances every bake in flight by one tick's budget.
     *
     * <p>Called from {@link #tick()} before the once-a-second gate: a bake is chunk-bound work that is
     * already capped per call, and slowing it to once a second would make a large map take minutes to
     * appear.
     *
     * @return true when at least one bake completed this tick
     */
    private boolean stepTerrainBakes() {
        if (bakes.isEmpty()) return false;
        boolean any = false;
        for (var entry : new ArrayList<>(bakes.entrySet())) {
            String id = entry.getKey();
            TerrainBake bake = entry.getValue();
            Model.Arena map = maps.get(id);
            if (map == null) {
                bakes.remove(id);
                terrainWaiters.remove(id);
                continue;
            }
            // Resolved without Model.require: a missing dimension is a reason to abandon this bake, not to
            // throw out of the server tick.
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(map.dimension)));
            boolean done = level != null && bake.step(level);
            if (bake.abandoned()) {
                // Give up cleanly: the map is too large or the world is unavailable. Waiter is told so the
                // client stops showing "preparing" forever.
                bakes.remove(id);
                for (ServerPlayer player : takeWaiters(id)) {
                    Network.send(player, Map.of("type", "terrain", "map", id, "state", "FAILED"));
                }
                continue;
            }
            if (!done) continue;
            bakes.remove(id);
            TerrainMap result = bake.result();
            terrainMaps.put(id, result);
            TerrainBake.store(folder, result);
            any = true;
            for (ServerPlayer player : takeWaiters(id)) sendTerrain(player, id, result);
        }
        return any;
    }

    private Set<ServerPlayer> takeWaiters(String id) {
        Set<java.util.UUID> waiting = terrainWaiters.remove(id);
        if (waiting == null || waiting.isEmpty()) return Set.of();
        Set<ServerPlayer> out = new java.util.LinkedHashSet<>();
        for (java.util.UUID uuid : waiting) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) out.add(player);
        }
        return out;
    }

    private static void sendTerrain(ServerPlayer player, String id, TerrainMap map) {
        try {
            sendTerrainUnsafe(player, id, map);
        } catch (RuntimeException ex) {
            // An oversized or rejected payload for one player must not stop the others from receiving theirs.
            BattlefieldOps.LOG.warn("Cannot send terrain for " + id + " to " + player.getName().getString(), ex);
        }
    }

    private static void sendTerrainUnsafe(ServerPlayer player, String id, TerrainMap map) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "terrain");
        out.put("map", id);
        out.put("state", "READY");
        out.put("signature", map.signature);
        out.put("data", map.encode());
        Network.send(player, out);
    }

    /**
     * Tells a player's client that they earned something, for the floating award feed.
     *
     * <p>Sent from the server because the server is what awards the score: letting the client decide would put a
     * number on screen that the scoreboard might not agree with. The client only renders what it is told.
     */
    private static void award(ServerPlayer player, String label, int points) {
        award(player, label, points, "", "kill");
    }

    /**
     * @param detail what the award is about (a victim's name, usually); shown after the text
     * @param icon   glyph kind: {@code kill}, {@code headshot}, {@code assist}, {@code revenge},
     *               {@code mark} or {@code capture}
     */
    private static void award(ServerPlayer player, String label, int points, String detail, String icon) {
        if (player == null || (points <= 0 && (detail == null || detail.isEmpty()))) return;
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "bonus");
            out.put("label", label);
            out.put("points", points);
            out.put("detail", detail == null ? "" : detail);
            out.put("icon", icon == null ? "kill" : icon);
            Network.send(player, out);
        } catch (RuntimeException ex) {
            // A cosmetic feed must never be able to fail the scoring that triggered it.
            BattlefieldOps.LOG.debug("Cannot send bonus feed entry", ex);
        }
    }

    /**
     * Tells an attacker their shot connected, for the hit marker.
     *
     * <p>Only when the victim is an enemy in the same match: a hit marker on a teammate would be actively
     * misleading, and one on a mob would fire constantly in a mode about players.
     */
    public void hitNotice(ServerPlayer attacker, ServerPlayer victim, float damage, double hitY) {
        if (attacker == null || victim == null || damage <= 0) return;
        Member attackerMember = members.get(attacker.getUUID());
        Member victimMember = members.get(victim.getUUID());
        if (attackerMember == null || victimMember == null) return;
        if (!attackerMember.alive || !attackerMember.map.equals(victimMember.map)) return;
        if (attackerMember.team == victimMember.team) return;
        Match match = matches.get(attackerMember.map);
        if (match != null) {
            long now = System.currentTimeMillis();
            long window = match.rules.map.assistSeconds * 1000L;
            UUID victimId = victim.getUUID();
            match.hits.put(victimId, Awards.record(match.hits.get(victimId),
                    new Awards.Hit(attacker.getUUID(), damage, now, hitY)));
            // Drop stale records so the ledger cannot grow across a whole match.
            match.hits.replaceAll((id, list) -> Awards.prune(list, now, window));
        }
        try {
            Network.send(attacker, Map.of("type", "hit", "damage", Math.round(damage)));
        } catch (RuntimeException ex) {
            BattlefieldOps.LOG.debug("Cannot send hit marker", ex);
        }
    }

    /**
     * Marks the enemy the player is looking at, for their team.
     *
     * <p>Validated entirely server-side: the client sends only the intent to mark, never a position or a
     * target. Accepting a client position would let anyone place marks on enemies they cannot see, which is
     * the same wall-hack the line-of-sight rule exists to prevent.
     *
     * <p>The target must be alive, an enemy, within spotting range, inside a narrow cone around the
     * crosshair, and have an unobstructed line — so the key marks what the player is actually aiming at
     * rather than whatever is closest.
     */
    private void mark(ServerPlayer player, JsonObject request) throws Exception {
        Member member = member(player);
        Match match = matches.get(member.map);
        Model.require(match != null && match.running, "对局尚未开始");
        Model.require(member.alive && player.isAlive(), "阵亡时无法标记");
        Model.require(member.team >= 0, "请先选择阵营");

        double halfAngle = Math.toRadians(Spotting.MARK_CONE_DEGREES);
        double heading = Math.toRadians(player.getYRot());
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            Member target = members.get(other.getUUID());
            if (target == null || !target.alive || !other.isAlive()) continue;
            if (target.team == member.team || target.team < 0) continue;
            if (!target.map.equals(member.map)) continue;
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance > Tactics.ENEMY_GLOW_RANGE) continue;
            if (!Spotting.inCone(heading, halfAngle, dx, dz)) continue;
            if (!player.hasLineOfSight(other)) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        Model.require(best != null, "准星前方 " + Spotting.MARK_CONE_DEGREES
                + "° 内没有可见敌军（需要视线无遮挡且在 "
                + (int) Tactics.ENEMY_GLOW_RANGE + " 格内）");
        match.marks.add(new Spotting.Mark(member.team, best.getX(), best.getZ(),
                System.currentTimeMillis() + match.rules.map.markSeconds * 1000L));
        while (match.marks.size() > Spotting.MAX_MARKS * 2) match.marks.removeFirst();
        // A successful mark is worth score, and the feed says so: that is the whole feedback the player needs,
        // instead of a countdown they have to watch.
        if (match.rules.map.markScore > 0) {
            member.score += match.rules.map.markScore;
            member.credits += match.rules.map.markScore;
            award(player, "已标记", match.rules.map.markScore,
                    best.getName().getString(), "mark");
        } else {
            award(player, "已标记", 0, best.getName().getString(), "mark");
        }
        announce(player, "已标记", best.getName().getString() + " · " + (int) bestDistance + " 格");
        // Everyone who can see the mark needs it without waiting for their next poll.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            Member viewerMember = members.get(other.getUUID());
            if (viewerMember != null && viewerMember.team == member.team && viewerMember.map.equals(member.map)) {
                state(other, member.map, false, "");
            }
        }
    }

    /**
     * Delivers a map's baked terrain, baking it first if necessary.
     *
     * <p>Not admin-gated: every player looking at the deployment map benefits, and the data is static
     * terrain they can already walk around in. It is, however, rate-limited by construction — a client
     * that asks repeatedly for a map that is already baked gets a map lookup, not a bake.
     *
     * <p>The reply is always either READY with the data or BAKING with progress, so the client can show an
     * honest "preparing" line instead of a blank canvas.
     */
    private void terrain(ServerPlayer player, JsonObject request) {
        String id = str(request, "map", "");
        Model.Arena map = maps.get(id);
        // The request always names its map; the single-map fallback only covers a client that opened a
        // screen before the selection round-tripped. With several maps and no id there is no safe guess.
        if (map == null && maps.size() == 1) map = maps.values().iterator().next();
        Model.require(map != null, "地图不存在");

        double[] box = TerrainBake.bounds(map);
        int[] dims = TerrainMap.dimensions(Math.max(1, box[2] - box[0]), Math.max(1, box[3] - box[1]));
        String signature = TerrainMap.signature(map, dims[0], dims[1]);

        TerrainMap ready = terrainMaps.get(map.id);
        if (ready != null && ready.signature.equals(signature) && ready.valid()) {
            sendTerrain(player, map.id, ready);
            return;
        }
        if (ready != null) terrainMaps.remove(map.id);

        TerrainMap cached = TerrainBake.load(folder, map.id, signature);
        if (cached != null && cached.valid()) {
            terrainMaps.put(map.id, cached);
            sendTerrain(player, map.id, cached);
            return;
        }

        TerrainBake bake = bakes.get(map.id);
        if (bake == null || !bake.signature().equals(signature)) {
            bakes.put(map.id, new TerrainBake(map));
            terrainWaiters.remove(map.id);
        }
        terrainWaiters.computeIfAbsent(map.id, k -> new java.util.LinkedHashSet<>())
                .add(player.getUUID());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "terrain");
        out.put("map", map.id);
        out.put("state", "BAKING");
        out.put("progress", bakes.get(map.id).progress());
        Network.send(player, out);
    }

    public void tick() {
        ticks++;
        try {
            tickDowned();
        } catch (RuntimeException | LinkageError ex) {
            // Runs inside the server tick; a failure here must not take the loop down.
            BattlefieldOps.LOG.error("Downed-state tick failed", ex);
        }
        // Terrain bakes advance every tick, not once a second: they are chunk-bound and bounded per tick,
        // so running them on the slow path would stretch a few seconds of work into minutes.
        //
        // Guarded because this runs inside the server tick: an exception here does not fail a request, it
        // takes down the whole server loop. A map whose dimension is not loaded used to throw from level()
        // on every tick for as long as a bake was in flight, which crashed the game the moment a player held
        // the map key.
        try {
            stepTerrainBakes();
        } catch (RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.error("Terrain bake step failed", ex);
            bakes.clear();
        }
        if (ticks % 20 != 0) return;
        for (Map.Entry<String, Match> entry : new ArrayList<>(matches.entrySet())) {
            String id = entry.getKey();
            Match match = entry.getValue();
            Rules rules = match.rules;
            Model.Arena map = rules.map;
            try {
                refreshMarks(match);
            } catch (RuntimeException | LinkageError ex) {
                // Runs inside the server tick; a failure here must not take the loop down with it.
                BattlefieldOps.LOG.error("Cannot refresh enemy spotting for " + id, ex);
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                    .filter(p -> {
                        Member m = members.get(p.getUUID());
                        return m != null && m.map.equals(id);
                    })
                    .toList();
            if (!match.running) {
                for (ServerPlayer p : players) state(p, id, false, "");
                continue;
            }
            if (players.isEmpty()) continue;

            int[][] presence = new int[map.points.size()][2];
            Map<Integer, List<ServerPlayer>> occupancy = new HashMap<>();
            for (ServerPlayer player : players) {
                Member member = members.get(player.getUUID());
                if (!member.alive || !player.isAlive() || member.team < 0) continue;
                boolean sameDimension = player.level().dimension().location().toString().equals(map.dimension);
                boolean beyond = !sameDimension || RoomPolicy.beyond(map, player.getX(), player.getY(), player.getZ());
                if (beyond) {
                    exitAtCurrentPosition(player);
                    state(player, id, false, "已离开战区，退出房间并恢复背包（不计阵亡）");
                    continue;
                }
                boolean insideMap = Model.contains(map.boundary, player.getX(), player.getZ())
                        && player.getY() >= map.minY && player.getY() <= map.maxY;
                boolean insideStage = !rules.isBreakthrough()
                        || map.inSector(rules.stage, player.getX(), player.getZ());
                // The activity zone is a hard boundary on where a team may be, so it counts as
                // "outside the battle area" in exactly the same way the map edge does. It shares the
                // grace period rather than exiting instantly: a player who clips a zone corner while
                // running should get the same warning-and-return as one who clips the map edge, and
                // being killed off the match for it would be a harsher rule than the map edge itself.
                boolean insideZone = map.inZone(member.team, player.getX(), player.getZ());
                boolean inside = insideMap && insideStage && insideZone;
                if (!inside) {
                    member.outside++;
                    int remaining = Math.max(0, map.outsideSeconds - member.outside);
                    // Say *which* boundary was crossed. "离开战区" for a zone violation would send the
                    // player looking at the map edge, which is not where their problem is.
                    boundaryWarning(player, remaining,
                            insideMap && insideStage ? "已离开本方活动区域 " : "已离开战区 ");
                    if (member.outside >= map.outsideSeconds) {
                        exitAtCurrentPosition(player);
                        state(player, id, false, "越界宽限结束，已退出房间（不计阵亡）");
                    }
                    continue;
                }
                member.outside = 0;
                member.warnedSecond = -1;
                if (player.isSpectator()) continue;
                for (int i = 0; i < map.points.size(); i++) {
                    if (!rules.active(i)) continue;
                    Model.Point point = map.points.get(i);
                    if (ObjectiveZone.contains(point,player.getX(),player.getY(),player.getZ())) {
                        presence[i][member.team]++;
                        occupancy.computeIfAbsent(i, n -> new ArrayList<>()).add(player);
                    }
                }            }

            updateGlow(players);
            updateSupply(map, players);
            reapEmptyVehicles(match);
            int previousStage = rules.stage;
            for (Rules.Capture capture : rules.tick(presence)) {
                if (capture.team() < 0) continue; // neutralised: no personal score
                for (ServerPlayer player : occupancy.getOrDefault(capture.point(), List.of())) {
                    Member member = members.get(player.getUUID());
                    if (member.team != capture.team()) continue;
                    member.captures++;
                    member.score += map.captureScore;
                    member.credits += map.captureScore;
                    award(player, "占领", map.captureScore, "", "capture");
                }
            }

            if (rules.winner != -2) {
                finish(id, rules.result());
                continue;
            }

            if (previousStage != rules.stage) {
                for (ServerPlayer player : players) {
                    Member member = members.get(player.getUUID());
                    if (member == null || member.team < 0) continue;
                    member.alive = false;
                    member.readyAt = ticks + 40;
                    player.stopRiding();
                    player.getInventory().clearContent();
                    player.setGameMode(GameType.SPECTATOR);
                    int stage = rules.stage;
                    Model.Spawn spawn = map.spawns.stream()
                            .filter(s -> s.team == member.team && s.sector == stage)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("阶段 " + stage + " 缺少出生点"));
                    teleport(player, level(map), spawn);
                    state(player, id, true, "突破成功，进入阶段 " + rules.stage + "，请重新部署");
                }
            }

            ServerLevel level = level(map);
            for (int i = 0; i < map.points.size(); i++) {
                if (!rules.active(i)) continue;
                Model.Point point = map.points.get(i);
                Rules.Flag flag = rules.flags.get(i);
                Vector3f color = new Vector3f(flag.owner == 0 ? 0.2f : 1f, flag.owner < 0 ? 0.9f : 0.3f,
                        flag.owner == 0 ? 1f : 0.2f);
                DustParticleOptions particle = new DustParticleOptions(color, 1.3f);
                for (int n = 0; n < 12; n++) {
                    double angle = n * Math.PI / 6;
                    level.sendParticles(particle, point.x + Math.cos(angle) * point.radius, point.y + 0.2,
                            point.z + Math.sin(angle) * point.radius, 1, 0, 0, 0, 0);
                }
            }

            sidebar(match);
            for (ServerPlayer player : players) state(player, id, false, "");
        }
    }

    // ------------------------------------------------------------------ presentation

    /** Big centred title used for boundary warnings; colour shifts red as the timer runs out. */
    private void boundaryWarning(ServerPlayer player, int remaining) {
        boundaryWarning(player, remaining, "已离开战区 ");
    }

    /**
     * Boundary warning with a caller-chosen reason.
     *
     * @param reason what the player left, so the message points at the boundary they actually crossed
     */
    private void boundaryWarning(ServerPlayer player, int remaining, String reason) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 4));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("请回到战斗区域").withStyle(net.minecraft.ChatFormatting.RED)));
        String color = remaining <= 1 ? "§c" : remaining <= 3 ? "§6" : remaining <= 5 ? "§e" : "§b";
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                "你将在 " + color + remaining + "§r 秒后退出房间（不计阵亡）")));
        player.displayClientMessage(Component.literal("§c返回战区 §f" + remaining + "§c 秒 · " + reason.trim()), true);
    }

    /** Short feedback used for deployment and spawn reassignment. */
    private void announce(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 20, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        if (!subtitle.isEmpty()) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        }
    }

    /**
     * Teammates glow through walls so a squad can regroup; enemies glow only when close and in
     * line of sight, which keeps the highlight informational instead of a wall hack.
     *
     * <p>Vanilla glowing is a single shared entity flag — every client sees the same thing — so the
     * decision must be global per entity, not per viewer. A player therefore glows when *any*
     * teammate is near (squad marker), or when *any* enemy nearby has line of sight (spotted). This
     * is the only honest reading of the flag; a per-viewer outline would need a custom renderer and
     * a client-side mod.
     *
     * <p>Flags are only written on change, because a per-tick flag write is a per-tick entity data
     * packet for every player.
     */
    private void updateGlow(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Member member = members.get(player.getUUID());
            if (member != null && member.glowing) clearGlow(player, member);
        }
    }

    private void clearGlow(ServerPlayer player, Member member) {
        if (member != null && member.glowing) player.setGlowingTag(false);
        if (member != null) member.glowing = false;
    }

    private static Tactics.At at(ServerPlayer player) {
        return new Tactics.At(player.getX(), player.getY(), player.getZ());
    }

    /**
     * Side bar shared by every viewer: tickets, stage, objective ownership and the top fraggers.
     * Rows are pre-rendered once and then written with {@link Board#sidebar}, because a vanilla side
     * bar can only show one score per holder and all viewers see the same text.
     *
     * <p>Skipped when more than one match is running: the side bar is a single server-wide display
     * slot, so two matches would overwrite each other every second. In that case the per-player HUD
     * carries the information instead.
     */
    private void sidebar(Match match) {
        board.hideSidebar();
        sidebarShown = false;
    }

    private void finish(String id, String reason) {
        Match match = matches.remove(id);
        if (match == null) return;
        results.put(id, reason);
        boolean drawn = reason.contains("平局");
        int winner = drawn ? -1 : reason.contains("蓝方") ? 0 : reason.contains("红方") ? 1 : -1;

        // Snapshot the scoreboard before anyone leaves, so the result screen can list every player.
        List<Member> ranked = members.values().stream()
                .filter(m -> m.map.equals(id))
                .sorted(Comparator.comparingInt((Member m) -> m.score).reversed())
                .toList();

        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Member member = members.get(player.getUUID());
            if (member == null || !member.map.equals(id)) continue;
            if (!match.running || member.team < 0) {
                leave(player);
                state(player, id, false, reason);
                continue;
            }
            clearGlow(player, member);
            // Everyone gets the verdict as a title, then the exact same summary in chat so it can
            // still be read after the title fades.
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(resultTitle(reason))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(resultSubtitle(member, winner))));
            player.sendSystemMessage(Component.literal("§e=== " + reason + " ==="));
            player.sendSystemMessage(Component.literal("§7个人：击杀 §f" + member.kills + "§7 阵亡 §f"
                    + member.deaths + "§7 占点 §f" + member.captures + "§7 得分 §f" + member.score));
            for (int i = 0; i < ranked.size() && i < 10; i++) {
                Member entry = ranked.get(i);
                String prefix = entry.team == 0 ? "§9" : entry.team == 1 ? "§c" : "§7";
                player.sendSystemMessage(Component.literal("§7#" + (i + 1) + " " + prefix + entry.name
                        + " §7" + entry.kills + "/" + entry.deaths + " §f" + entry.score));
            }
            Map<String,Object> summary=new LinkedHashMap<>();
            summary.put("type","result");summary.put("mapName",match.rules.map.name);
            summary.put("reason",reason);summary.put("outcome",match.rules.winner==-2?"对局结束":winner<0?"平局":winner==member.team?"胜利":"失败");
            summary.put("kills",member.kills);summary.put("deaths",member.deaths);
            summary.put("captures",member.captures);summary.put("score",member.score);
            leave(player);
            state(player, id, false, reason);
            Network.send(player,summary);
        }
        board.clearMap(id);
        board.hideSidebar();
        for (UUID uuid : match.vehicles) {
            Entity entity = findEntity(uuid);
            if (entity != null) entity.discard();
            vehicles.remove(uuid);
        }
    }

    private static String resultTitle(String reason) {
        if (reason.contains("平局")) return "§7平局";
        if (reason.contains("蓝方")) return "§9蓝方胜利";
        if (reason.contains("红方")) return "§c红方胜利";
        return reason;
    }

    private static String resultSubtitle(Member member, int winner) {
        String outcome = winner < 0 ? "§7平局" : winner == member.team ? "§a胜利" : "§c失败";
        return outcome + " §7击杀 " + member.kills + " 阵亡 " + member.deaths
                + " 占点 " + member.captures + " §f得分 " + member.score;
    }

    public void shutdown() {
        for (String id : new ArrayList<>(matches.keySet())) finish(id, "服务器关闭，对局结束");
        board.clearAll();
    }

    // ------------------------------------------------------------------ state

    public void state(ServerPlayer player, String selected, boolean open, String message) {
        Member current = members.get(player.getUUID());
        if (current != null) selected = current.map;
        String key = maps.containsKey(selected) ? selected : maps.keySet().stream().findFirst().orElse("");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "state");
        out.put("open", open);
        out.put("message", message);
        out.put("admin", player.hasPermissions(2));
        out.put("maps", maps.values().stream()
                .map(a -> Map.of("id", a.id, "name", a.name, "running", matches.containsKey(a.id) && matches.get(a.id).running, "room", matches.containsKey(a.id)))
                .toList());
        out.put("selected", key);
        Match room = matches.get(key);
        boolean participating = RoomPolicy.canView(current == null ? null : current.map,
                current == null ? -1 : current.team, key, room != null && room.running);
        out.put("inMatch", participating);
        out.put("roomExists", room != null);
        out.put("roomRunning", room != null && room.running);
        out.put("roomPlayers", members.values().stream().filter(m -> m.map.equals(key)).count());
        out.put("position", List.of(player.getX(), player.getY(), player.getZ()));
        out.put("yaw", player.getYRot());
        out.put("dimension", player.level().dimension().location().toString());
        out.put("diagnostics", catalog.diagnostics);
        out.put("catalogCount", catalog.entries.size());
        out.put("catalog", Map.of("guns", catalog.guns.size(), "vehicles", catalog.vehicles.size()));

        // Which presentation surfaces this mod is allowed to use. The client cannot work this out
        // itself: TAB lives on the proxy and Xaero is a client mod, so only the server has enough
        // context to decide, and only the server knows whether a sidebar write would fight TAB.
        Map<String, Object> hud = new LinkedHashMap<>();
        hud.put("sidebar", sidebarShown);
        hud.put("teams", board.teamsEnabled());
        hud.put("minimap", compat.policy().mayUseMinimap(
                compat.settings().minimap, compat.settings().xaeroMinimap));
        hud.put("xaero", compat.settings().xaeroMinimap);
        // Which corner the HUD minimap takes. Sent as a name rather than coordinates: only the client knows
        // the GUI size, and a corner survives a window resize where pixels would not.
        hud.put("minimapCorner", Briefing.normaliseCorner(compat.settings().minimapCorner));
        // Whether the client should hide Xaero's minimap for the duration of a match. The client
        // enforces it because Xaero lives there, but the decision belongs to the server: it is a match
        // rule, and it must be the same for everyone in the room.
        hud.put("hideXaero", compat.settings().hideXaeroInMatch);
        out.put("hud", hud);
        // Exposed so an operator can see why a surface was given up, without reading the log.
        out.put("compat", compat.report(Catalog.hasTacz(), Catalog.hasSuperbWarfare(),
                server.getServerModName()));

        // Minimap blips. Teammates are always shown; enemies only appear once they are glowing,
        // which is exactly when the server decided they are close and in someone's line of sight.
        // Sending every enemy would hand the client a radar the server never authorised.
        Member viewer = members.get(player.getUUID());
        List<Map<String, Object>> mates = new ArrayList<>();
        if (participating) {
            mates.add(blip(player, viewer, true, false));
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                Member mate = members.get(other.getUUID());
                if (mate == null || mate == viewer || !mate.alive) continue;
                if (!mate.map.equals(viewer.map)) continue;
                boolean hostile = mate.team != viewer.team;
                if (!hostile && !room.rules.map.glowTeammates) continue;
                if (hostile) {
                    // Only what this player can see for themselves. A teammate's line of sight does not
                    // silently hand the team a contact: sharing is a deliberate act, and the mark key is that
                    // act. Automatic team-wide exposure turned the map into a radar nobody had to work for.
                    if (!room.rules.map.glowEnemies
                            || !Tactics.enemyVisible(at(player), at(other), player.hasLineOfSight(other))) continue;
                }
                mates.add(blip(other, mate, false, hostile));
            }
        }
        out.put("mates", mates);
        if (participating) {
            // Only this team's marks are sent: a mark is shared information within a side, never across it.
            List<Map<String, Object>> marks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Spotting.Mark mark : Spotting.visibleTo(
                    matches.get(key).marks, viewer.team, now)) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("x", mark.x());
                one.put("z", mark.z());
                // Seconds left, so the map can show a countdown instead of a marker that silently vanishes.
                one.put("left", Math.max(0, (mark.expiresAt() - now + 999) / 1000));
                marks.add(one);
            }
            out.put("marks", marks);
        }
        out.put("elapsed", participating ? matches.get(key).rules.elapsed : 0);
        out.put("vehicles", participating ? vehicleBlips(key, viewer) : List.of());
        if (maps.containsKey(key)) {
            Model.Arena arena = maps.get(key);
            out.put("arena", arena);
            out.put("names", names(arena));
        }
        Member me = members.get(player.getUUID());
        if (me != null) {
            out.put("me", me);
            out.put("respawnRemaining", Math.max(0, (me.readyAt - ticks + 19) / 20));
            // Bleed-out clock, top level rather than inside "me": it is derived from the tick counter, not a
            // field of the member, and putting a transient value on the serialised member would persist it.
            out.put("downLeft", me.downed ? Medic.secondsLeft(me.bleedOutAt, ticks) : 0);
            out.put("reviveProgress", me.downed ? me.reviveProgress : 0);
            out.put("vehicleRemaining", Math.max(0, (me.vehicleAt - ticks + 19) / 20));
            out.put("team", me.team);
            out.put("teamName", Board.label(me.team));
            // Whether this player could take a vehicle right now, and if not, why. Computed on the
            // server because the blockers (credits, cooldown, match cap) are server-side facts and
            // the client must not be able to talk itself into a vehicle it cannot have.
            out.put("vehicleState", vehicleState(key, me));

            // The exact deploy sites the server will accept, including capture points this team holds.
            // Sent rather than derived on the client so the list a player picks from is the list the
            // server validates against; any divergence would surface as "the point I chose was
            // rejected". Each entry carries its vehicle mask so the GUI can grey out an air point for
            // a tank without asking the server again on every click.
            if (room != null) {
                List<Map<String, Object>> sites = new ArrayList<>();
                for (Deploy.Site site : Deploy.sites(maps.get(key), me.team, room.rules.stage,
                        room.rules.isBreakthrough(), Deploy.owners(room.rules))) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", site.id());
                    entry.put("x", site.x());
                    entry.put("y", site.y());
                    entry.put("z", site.z());
                    entry.put("allows", site.allows());
                    entry.put("spawn", site.spawn());
                    // Headquarters / forward post / airfield. The client groups by this so an airfield
                    // is presented as its own choice rather than as an oddly-named base.
                    entry.put("kind", site.kind());
                    sites.add(entry);
                }
                out.put("deploySites", sites);
            }
        }
        // Vehicle classification, so the deploy screen can separate aircraft from ground vehicles
        // without shipping the whole superbwarfare metadata table to the client.
        Map<String, String> classes = new LinkedHashMap<>();
        if (maps.containsKey(key)) {
            for (String id : maps.get(key).vehicles) classes.put(id, String.valueOf(catalog.vehicleClass(id)));
        }
        out.put("vehicleClasses", classes);
        Match match = matches.get(key);
        if (participating) {
            out.put("tickets", match.rules.tickets);
            out.put("tracksTickets", List.of(match.rules.tracksTickets(0), match.rules.tracksTickets(1)));
            out.put("owned", match.rules.owned());
            out.put("stage", match.rules.stage);
            out.put("stages", match.rules.stageCount());
            out.put("flags", match.rules.flags);
            int objective=-1;
            Model.Arena activeMap=match.rules.map;
            if(me.alive&&player.isAlive()&&!player.isSpectator()&&me.outside==0
                    &&player.level().dimension().location().toString().equals(activeMap.dimension)){
                for(int i=0;i<activeMap.points.size();i++)if(match.rules.active(i)&&ObjectiveZone.contains(activeMap.points.get(i),player.getX(),player.getY(),player.getZ())){objective=i;break;}
            }
            out.put("objective",objective);
            if(objective<0&&me.alive&&player.isAlive()){
                for(int i=0;i<activeMap.points.size();i++){
                    Model.Point point=activeMap.points.get(i);
                    if(Math.hypot(player.getX()-point.x,player.getZ()-point.z)>point.radius)continue;
                    String hint=!match.rules.active(i)?point.id+" 尚未解锁（突破阶段 "+(point.sector+1)+"）":
                        !ObjectiveZone.contains(point,player.getX(),player.getY(),player.getZ())?point.id+" 高度不符：Y "+Math.round(point.y-point.height/2)+"～"+Math.round(point.y+point.height/2):"战区外不能占点";
                    out.put("objectiveHint",hint);break;
                }
            }
            out.put("scores", members.values().stream()
                    .filter(m -> m.map.equals(key) && m.team >= 0)
                    .sorted(Comparator.comparingInt((Member m) -> m.score).reversed())
                    .toList());
        }
        out.put("result", results.getOrDefault(key, ""));
        Network.send(player, out);
    }

    /**
     * Vehicle blips for the HUD, coloured by owner.
     *
     * <p>Every vehicle in the match is sent, including enemy ones. Unlike enemy players — which are
     * withheld until the server decides they are spotted — a vehicle is a large, loud, plainly
     * visible object, so hiding it from the HUD would be inconsistent rather than stealthy. Nothing
     * here leaks information the player could not already see.
     *
     * @param viewer the requesting member, or {@code null} for a non-participant
     */
    private List<Map<String, Object>> vehicleBlips(String mapId, Member viewer) {
        List<Map<String, Object>> out = new ArrayList<>();
        Match match = matches.get(mapId);
        if (match == null) return out;
        int viewerTeam = viewer == null ? -1 : viewer.team;
        for (UUID uuid : new ArrayList<>(match.vehicles)) {
            Entity entity = findEntity(uuid);
            if (entity == null || entity.isRemoved()) {
                // Drop hulls that no longer exist so the HUD does not show ghosts of destroyed
                // vehicles until the next cleanup pass.
                match.vehicles.remove(uuid);
                match.vehicleTeams.remove(uuid);
                continue;
            }
            int owner = match.vehicleTeams.getOrDefault(uuid, -1);
            VehicleHud.Blip blip = new VehicleHud.Blip(owner, entity.getX(), entity.getZ(),
                    entity.getPassengers().size(), entity.isRemoved());
            if (!VehicleHud.visible(blip)) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", Catalog.prettify(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
            entry.put("x", entity.getX());
            entry.put("z", entity.getZ());
            entry.put("team", owner);
            entry.put("friendly", VehicleHud.friendly(blip, viewerTeam));
            entry.put("occupants", blip.occupants());
            entry.put("occupancy", VehicleHud.occupancy(blip));
            entry.put("shareable", VehicleHud.shareable(blip, viewerTeam));
            out.add(entry);
        }
        return out;
    }

    /**
     * Whether this player could deploy a vehicle right now.
     *
     * <p>Reports the specific blocker rather than a bare boolean, because a player staring at a
     * disabled button needs the reason in order to change their plan.
     */
    private Map<String, Object> vehicleState(String mapId, Member member) {
        Model.Arena map = maps.get(mapId);
        Match match = matches.get(mapId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (map == null || match == null) {
            out.put("state", "NOT_ALLOWED");
            out.put("deployable", false);
            out.put("text", VehicleHud.describe(VehicleHud.State.NOT_ALLOWED, 0, 0, 0));
            return out;
        }
        match.vehicles.removeIf(uuid -> findEntity(uuid) == null);
        int cooldownTicks = (int) Math.max(0, member.vehicleAt - ticks);
        // Whether the team currently holds a spawn that can launch a vehicle at all. Reporting this
        // separately lets the HUD say "无可用刷新点" instead of the misleading "可部署" followed by a
        // rejection at deploy time.
        boolean siteAvailable = Deploy.sites(map, member.team, match.rules.stage,
                match.rules.isBreakthrough(), Deploy.owners(match.rules)).stream()
                .anyMatch(site -> !site.allows().isEmpty());
        VehicleHud.State state = VehicleHud.availability(
                !map.vehicles.isEmpty(), true, siteAvailable,
                cooldownTicks, teamVehicles(match,member.team), map.vehicleLimitFor(member.team));
        out.put("state", state.name());
        out.put("deployable", VehicleHud.deployable(state));
        out.put("text", VehicleHud.describe(state, (cooldownTicks + 19) / 20,
                teamVehicles(match,member.team), map.vehicleLimitFor(member.team)));
        out.put("cooldown", map.vehicleCooldown);
        out.put("remaining", (cooldownTicks + 19) / 20);
        out.put("limit", map.vehicleLimitFor(member.team));
        out.put("used", teamVehicles(match,member.team));
        return out;
    }

    /**
     * One minimap blip. Includes the vehicle type when the player is riding one, which is the
     * "show what vehicle someone is in" request — read from the entity itself so a vehicle addon
     * that is not in the whitelist still shows up correctly.
     */
    private Map<String, Object> blip(ServerPlayer player, Member member, boolean self, boolean hostile) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", member.name);
        out.put("x", player.getX());
        out.put("z", player.getZ());
        out.put("self", self);
        // Heading, so a teammate can be drawn as an arrow rather than a dot. Without it the map says where
        // somebody is but not which way they are facing, which is half of what a squad marker is for.
        out.put("yaw", player.getYRot());
        out.put("alive", member.alive);
        out.put("team", member.team);
        out.put("hostile", hostile);
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            // Name only: sending the full entity id list would leak the addon's internals and is
            // useless to the client, which cannot spawn anything from it.
            out.put("vehicle", Catalog.prettify(
                    BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString()));
        }
        return out;
    }

    /** Human-readable gun / vehicle names, so the GUI never shows raw registry ids only. */
    private Map<String, String> names(Model.Arena arena) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Model.Kit kit : arena.kits) {
            for(boolean main:new boolean[]{true,false})for(String id:kit.options(main))if(!id.isEmpty())out.put(id,catalog.name(id));
            if (!kit.primary.isEmpty()) out.put(kit.primary, catalog.name(kit.primary));
            if (!kit.secondary.isEmpty()) out.put(kit.secondary, catalog.name(kit.secondary));
        }
        for (String vehicle : arena.vehicles) out.put(vehicle, catalog.name(vehicle));
        return out;
    }
}
