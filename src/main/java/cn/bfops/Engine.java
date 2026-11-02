package cn.bfops;

import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.FrontVoicePlayRequest;
import cn.bfops.voice.common.audio.FrontVoicePlaybackMode;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.network.ClientboundPlayAudioPayload;
import cn.bfops.voice.network.VoiceNetwork;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class Engine {
  /** Server tick of the last hit each player took; medical support stops while this is recent. */
  private final java.util.Map<java.util.UUID,Integer> damageTicks = new java.util.HashMap<>();
   private static final double VEHICLE_ORPHAN_RANGE = 64.0;
   private final MinecraftServer server;
   private final Path folder;
   private final Map<String, TerrainBake> bakes = new HashMap<>();
   private final Map<String, TerrainMap> terrainMaps = new HashMap<>();
   private final Map<String, Set<UUID>> terrainWaiters = new HashMap<>();
   private final Map<String, Model.Arena> maps = new TreeMap<>();
   private final Map<String, Engine.Match> matches = new HashMap<>();
   public final Map<UUID, Engine.Member> members = new HashMap<>();
   public final Set<UUID> vehicles = new HashSet<>();
   private final Map<UUID, Long> requests = new HashMap<>();
   private final Map<UUID, Long> polls = new HashMap<>();
   private final Map<String, String> results = new HashMap<>();
   private final Catalog catalog = new Catalog();
   final GadgetRefill refills;
   private final Board board;
   private final Compat compat;
   private boolean sidebarShown;
   private LobbySettings lobbySettings = new LobbySettings();
   private long ticks;
   final ConquestSupport support;
   final FinalHonours finalHonours;
   private final BattleAtmosphere atmosphere;
   private final RushObjectives rush;
   private final PlayerProgress progress;
   private final CareerStats career;
   private Map<String, Object> armamentCache;
   private final Map<String, Long> padReadyAt = new HashMap<>();
   private final Map<UUID, Engine.Beacon> beacons = new LinkedHashMap<>();
   private final Map<UUID, Integer> beaconUses = new HashMap<>();
   private final Map<UUID, Float> beaconHealth = new HashMap<>();
   private static final ResourceLocation CRAWL_SPEED = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "downed_crawl");
   private final Map<UUID, ServerPlayer> recentAttacker = new HashMap<>();
   private final Map<UUID, Long> recentAttackerAt = new HashMap<>();
   private static final long RECENT_ATTACKER_TICKS = 300L;
   final CosmeticKills cosmeticKills = new CosmeticKills();

   Engine(MinecraftServer var1) {
      this.server = var1;
      this.support = new ConquestSupport(this, var1);
      this.finalHonours = new FinalHonours(this, var1);
      this.atmosphere = new BattleAtmosphere(this);
      this.rush = new RushObjectives(this, var1);
      this.board = new Board(var1);
      this.refills = new GadgetRefill(this, var1);
      this.folder = var1.getWorldPath(LevelResource.ROOT).resolve("battlefield_ops");
      this.progress = new PlayerProgress(this.folder);
      this.career = new CareerStats(this.folder);
      this.compat = Compat.load(this.folder, BattlefieldOps.LOG);
      this.board.compat(this.compat);

      try {
         Files.createDirectories(this.folder.resolve("maps"));
         Files.createDirectories(this.folder.resolve("players"));

         try (Stream<Path> var2 = Files.list(this.folder.resolve("maps"))) {
            var2.filter(var0 -> var0.toString().endsWith(".json")).forEach(var1x -> {
               try {
                  Model.Arena var2x = (Model.Arena)BattlefieldOps.GSON.fromJson(Files.readString(var1x), Model.Arena.class);
                  boolean var3x = FixedClasses.migrateLegacy(var2x);
                  EliteRules.complete(var2x);
                  var2x.validate();
                  if (var3x) {
                     Path var4x = var1x.resolveSibling(var1x.getFileName() + ".classes-legacy.bak");
                     if (!Files.exists(var4x)) {
                        Files.copy(var1x, var4x);
                     }

                     BattlefieldOps.LOG.info("Converted legacy classes in {}; original retained at {}", var2x.id, var4x);
                  }

                  this.maps.put(var2x.id, var2x);
               } catch (Exception var5) {
                  BattlefieldOps.LOG.error("Invalid battlefield map {}", var1x, var5);
               }
            });
         }
      } catch (IOException var8) {
         throw new IllegalStateException("无法读取战地配置目录", var8);
      }

      Path var9 = this.folder.resolve("lobby.json");
      if (Files.exists(var9)) {
         try {
            LobbySettings var3 = (LobbySettings)BattlefieldOps.GSON.fromJson(Files.readString(var9), LobbySettings.class);
            Model.require(var3 != null, "大厅配置为空");
            var3.validate();
            this.lobbySettings = var3;
         } catch (Exception var6) {
            BattlefieldOps.LOG.error("Invalid lobby config; automatic transfer disabled", var6);
         }
      }

      this.board.clearAll();
      this.catalog.scan(var1);

      for (ServerPlayer var4 : var1.getPlayerList().getPlayers()) {
         this.recover(var4);
      }
   }

   ServerLevel level(Model.Arena var1) {
      Model.require(var1 != null, "内部错误：未指定地图，无法定位维度");
      ServerLevel var2 = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(var1.dimension)));
      Model.require(var2 != null, "地图维度未加载：" + var1.dimension);
      return var2;
   }

   private ServerLevel levelOrNull(Model.Arena var1) {
      return var1 == null ? null : this.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(var1.dimension)));
   }

   public boolean isBot(ServerPlayer var1) {
      Engine.Member var2 = var1 == null ? null : this.members.get(var1.getUUID());
      return var2 != null && var2.bot;
   }

   int playerLevel(ServerPlayer var1) throws IOException {
      return this.progress.level(var1.getUUID());
   }

   void setPlayerLevel(ServerPlayer var1, int var2) throws IOException {
      this.progress.setLevel(var1.getUUID(), var2);
   }

   static String str(JsonObject var0, String var1, String var2) {
      return var0.has(var1) ? var0.get(var1).getAsString() : var2;
   }

   static int num(JsonObject var0, String var1, int var2) {
      return var0.has(var1) ? var0.get(var1).getAsInt() : var2;
   }

   public void request(ServerPlayer var1, String var2) {
      if (Network.supported(var1)) {
         if (var2.length() <= 20000) {
            String var3 = "";
            String var4 = "";

            try {
               JsonObject var5 = JsonParser.parseString(var2).getAsJsonObject();
               var4 = str(var5, "op", "");
               var3 = str(var5, "map", "");
               boolean var22 = var4.equals("open") || var4.equals("lobby") && var5.has("open") && var5.get("open").getAsBoolean();
               boolean var7 = var4.equals("prone") || var4.equals("editorEnd");
               boolean var8 = var4.equals("catalogAll") || var4.equals("state") || var4.equals("lobby") && !var22;
               var var9 = var8 ? this.polls : this.requests;
               if (!var22 && !var7 && this.ticks - var9.getOrDefault(var1.getUUID(), -100L) < 2L) {
                  if (var8) {
                     return;
                  }

                  throw new IllegalArgumentException("操作过快，请稍后重试");
               }

               if (!var22 && !var7) {
                  var9.put(var1.getUUID(), this.ticks);
               }

               if (Set.of(
                     "selectTeam",
                     "deploy",
                     "redeploy",
                     "squad",
                     "placeBeacon",
                     "removeBeacon",
                     "mark",
                     "revive",
                     "rescueCall",
                     "leave",
                     "elitePickup",
                     "prone",
                     "rushInteract",
                     "giveUp",
                     "skipRespawn"
                  )
                  .contains(var4)) {
                  Engine.Member var10 = this.members.get(var1.getUUID());
                  Model.require(RoomPolicy.sameRoom(var10 == null ? null : var10.map, var3), "房间已变化，请刷新后重试");
               }

               switch (var4) {
                  case "career":
                     for (Entry var40 : this.members.entrySet()) {
                        ServerPlayer var43 = this.server.getPlayerList().getPlayer((UUID)var40.getKey());
                        if (var43 != null) {
                           this.career.poll(var43, (Engine.Member)var40.getValue());
                        }
                     }

                     this.career.send(var1, str(var5, "sort", "score"));
                     break;
                  case "open":
                     this.open(var1);
                     break;
                  case "state":
                     this.state(var1, var3, false, "");
                     break;
                  case "lobby":
                     boolean var34 = var5.has("open") && var5.get("open").getAsBoolean();
                     Model.require(!var34 || !this.members.containsKey(var1.getUUID()), "请先退出当前房间，再进入游戏大厅");
                     this.lobby(var1, var34, "");
                     break;
                  case "lobbyCreate":
                     this.createRoom(var3, var1);
                     this.lobby(var1, false, "房间已创建，玩家可进入后选队");
                     break;
                  case "lobbyStart":
                     this.requireRoomControl(var1, var3);
                     this.startRoom(var3);
                     this.lobby(var1, false, "该房间已开始，其他房间继续运行");
                     break;
                  case "lobbyClose":
                     this.requireRoomControl(var1, var3);
                     Model.require(this.matches.containsKey(var3), "房间已关闭");
                     this.finish(var3, "房主或管理员结束对局");
                     this.lobby(var1, false, "该房间已关闭");
                     break;
                  case "lobbyJoin":
                     this.join(var1, var3);
                     this.state(var1, var3, true, "已进入房间，请选择阵营");
                     break;
                  case "lobbyReturn":
                     Model.require(var1.isAlive(), "请先完成重生，再返回大厅");
                     this.leave(var1);
                     boolean var33 = !Files.exists(this.folder.resolve("players").resolve(var1.getUUID() + ".nbt"));
                     this.state(var1, "", false, var33 ? "已退出房间，背包已恢复" : "背包恢复失败，备份已保留");
                     Model.require(var33, "原背包尚未恢复，备份已保留，请联系管理员");
                     this.transferToLobby(var1);
                     this.lobby(var1, true, "已返回游戏大厅");
                     break;
                  case "lobbySet":
                     this.admin(var1);
                     this.setLobby(var1);
                     this.lobby(var1, false, "共享大厅已设为你当前的位置");
                     break;
                  case "lobbyDisable":
                     this.admin(var1);
                     LobbySettings var32 = (LobbySettings)BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(this.lobbySettings), LobbySettings.class);
                     var32.enabled = false;
                     MapFiles.save(this.folder, "lobby", BattlefieldOps.GSON.toJson(var32));
                     this.lobbySettings = var32;
                     this.lobby(var1, false, "共享大厅传送已关闭，离房恢复原位置");
                     break;
                  case "catalog":
                     this.catalog(var1, var5);
                     break;
                  case "terrain":
                     this.terrain(var1, var5);
                     break;
                  case "mark":
                     this.mark(var1, var5);
                     break;
                  case "rushInteract":
                     this.rush.interact(var1, this.ticks);
                     break;
                  case "elitePickup":
                     this.claimElite(var1);
                     this.state(var1, this.member(var1).map, false, "已领取精英装备");
                     break;
                  case "squad":
                     this.changeSquad(var1, num(var5, "squad", 0));
                     this.state(var1, this.member(var1).map, false, "小队已更新");
                     break;
                  case "placeBeacon":
                     this.placeBeacon(var1);
                     this.state(var1, this.member(var1).map, false, "信标已放置：有效 10 分钟");
                     break;
                  case "removeBeacon":
                     this.removeOwnedBeacon(var1.getUUID());
                     this.state(var1, this.member(var1).map, false, "已回收自己的信标");
                     break;
                  case "revive":
                     this.server
                        .getPlayerList()
                        .getPlayers()
                        .stream()
                        .filter(var2x -> var2x != var1 && this.canRescue(var1, var2x))
                        .sorted(Comparator.comparingDouble(var1::distanceToSqr))
                        .filter(var2x -> this.reviveStart(var1, var2x))
                        .findFirst();
                     break;
                  case "giveUp":
                     Engine.Member var31 = this.member(var1);
                     Engine.Match var39 = this.matches.get(var31.map);
                     if (var39 != null && var39.running && var31.alive && var31.downed && var39.rules.map.allowGiveUp) {
                        if (this.ticks - var31.giveUpHeldAt > 6L || var31.giveUpAt < 0L) {
                           var31.giveUpAt = this.ticks;
                        }

                        var31.giveUpHeldAt = this.ticks;
                     }
                     break;
                  case "skipRespawn":
                     Engine.Member var30 = this.member(var1);
                     Engine.Match var38 = this.matches.get(var30.map);
                     Model.require(var38 != null && var38.running && !var30.alive && var30.team >= 0 && var38.rules.map.allowRespawnSkip, "当前不能跳过部署等待");
                     var30.readyAt = this.ticks;
                     this.state(var1, var30.map, false, "已跳过等待，请选择部署点");
                     break;
                  case "prone":
                     Engine.Member var29 = this.member(var1);
                     Engine.Match var37 = this.matches.get(var29.map);
                     Model.require(
                        var37 != null && var37.running && var29.team >= 0 && var29.alive && !var29.downed && !var1.isSpectator() && !var1.isPassenger(),
                        "当前状态不能切换趴下"
                     );
                     boolean var42 = var5.has("value") && var5.get("value").getAsBoolean();
                     Model.require(!var42 || var1.onGround(), "落地后可以趴下");
                     if (!var42 || this.ticks - var29.proneAt >= 2L) {
                        if (var42) {
                           var29.proneAt = this.ticks;
                        }

                        TaczProneBridge.set(var1, var42);
                     }
                     break;
                  case "leaderboard":
                     Engine.Member var28 = this.members.get(var1.getUUID());
                     if (var28 != null && this.matches.containsKey(var28.map) && this.matches.get(var28.map).running) {
                        this.state(var1, var28.map, false, "");
                     } else {
                        this.career
                           .leaderboard(var1, num(var5, "offset", 0), num(var5, "limit", 64), var5.has("requestId") ? var5.get("requestId").getAsLong() : 0L);
                     }
                     break;
                  case "catalogAll":
                     JsonObject var27 = new JsonObject();
                     var27.addProperty("all", true);
                     this.catalog(var1, var27);
                     break;
                  case "editorStart":
                     this.admin(var1);
                     EditorFlight.start(var1, this.maps.get(var3));
                     break;
                  case "editorEnd":
                     EditorFlight.restore(var1);
                     Network.send(var1, Map.of("type", "editorFlight", "active", false));
                     break;
                  case "rescueCall":
                     this.callRescue(var1);
                     break;
                  case "fittings":
                     this.fittings(var1, var5);
                     break;
                  case "trainingLoadout":
                     this.rearm(var1, var5);
                     break;
                  case "attachmentCatalog":
                     this.admin(var1);
                     String var26 = str(var5, "id", "");
                     Model.require(this.catalog.guns.contains(var26), "未扫描到武器");
                     Network.send(var1, Map.of("type", "attachmentCatalog", "id", var26, "groups", this.catalog.gunFittings.getOrDefault(var26, List.of())));
                     break;
                  case "compatMode":
                     this.admin(var1);
                     String var25 = str(var5, "extra", "");
                     int var36 = var25.indexOf(61);
                     Model.require(var36 > 0, "兼容参数格式错误");
                     String var41 = var25.substring(0, var36);
                     String var15 = var25.substring(var36 + 1);
                     if (var41.equals("hideXaero")) {
                        String var17 = var15.toLowerCase(Locale.ROOT);

                        boolean var16 = switch (var17) {
                           case "true", "1", "on", "yes" -> true;
                           case "false", "0", "off", "no" -> false;
                           default -> throw new IllegalArgumentException("hideXaero 需要 true 或 false");
                        };
                        this.compat.settings().hideXaeroInMatch = var16;
                        this.compat.save(BattlefieldOps.LOG);
                        this.state(var1, var3, false, var16 ? "对局中将隐藏 Xaero 小地图" : "对局中保持 Xaero 小地图显示");
                     } else {
                        String var44 = var15.toUpperCase(Locale.ROOT);

                        CompatPolicy.Mode var45;
                        try {
                           var45 = CompatPolicy.Mode.valueOf(var44);
                        } catch (IllegalArgumentException var20) {
                           throw new IllegalArgumentException("未知的兼容模式：" + var44);
                        }

                        switch (var41) {
                           case "sidebar":
                              this.compat.settings().sidebar = var45;
                              break;
                           case "teams":
                              this.compat.settings().teams = var45;
                              break;
                           case "minimap":
                              this.compat.settings().minimap = var45;
                              break;
                           default:
                              throw new IllegalArgumentException("未知的兼容项：" + var41);
                        }

                        this.compat.reset();
                        this.compat.save(BattlefieldOps.LOG);
                        this.state(var1, var3, false, "兼容设置已更新：" + var41 + " = " + var44);
                     }
                     break;
                  case "compatCorner":
                     this.admin(var1);
                     String var24 = Briefing.normaliseCorner(str(var5, "value", ""));
                     this.compat.settings().minimapCorner = var24;
                     this.compat.save(BattlefieldOps.LOG);
                     this.state(var1, var3, false, "小地图位置：" + Briefing.minimapCornerName(var24));
                     break;
                  case "compatReset":
                     this.admin(var1);
                     this.compat.reset();
                     this.state(var1, var3, false, "已清除兼容自检结果，下次对局将重新检测");
                     break;
                  case "compatSave":
                     this.admin(var1);
                     this.compat.save(BattlefieldOps.LOG);
                     this.state(var1, var3, false, "兼容配置已写入 " + this.folder.resolve("compat.json"));
                     break;
                  case "scan":
                     this.admin(var1);
                     this.catalog.scan(this.server);
                     this.armamentCache = null;
                     this.state(
                        var1, var3, false, "扫描完成：" + this.catalog.guns.size() + " 把武器，" + this.catalog.vehicles.size() + " 种载具；" + this.catalog.diagnostics
                     );
                     break;
                  case "save":
                     this.admin(var1);
                     Model.Arena var23 = (Model.Arena)BattlefieldOps.GSON.fromJson(var5.get("arena"), Model.Arena.class);
                     var23.validate();
                     FixedClasses.complete(var23);
                     EliteRules.complete(var23);
                     if (var5.has("baseRevision")) {
                        String var13 = str(var5, "baseId", "");
                        Model.Arena var14 = this.maps.get(var13);
                        MapFiles.expect(var14 == null ? "" : BattlefieldOps.GSON.toJson(var14), str(var5, "baseRevision", ""));
                        Model.require(var23.id.equals(var13) || !this.maps.containsKey(var23.id), "地图 ID 已存在，请换一个 ID");
                     }

                     Model.require(!this.matches.containsKey(var23.id), "请先结束这张地图的对局");
                     Model.require(this.maps.containsKey(var23.id) || this.maps.size() < 64, "最多 64 张地图");
                     this.level(var23);
                     this.catalog.check(var23);
                     this.save(var23);
                     this.maps.put(var23.id, var23);
                     Network.send(var1, Map.of("type", "saved", "arena", var23));
                     this.state(var1, var23.id, false, "地图已保存");
                     break;
                  case "delete":
                     this.admin(var1);
                     Model.require(!this.matches.containsKey(var3), "请先结束对局");
                     Model.require(this.maps.containsKey(var3), "地图不存在");
                     Files.move(
                        this.folder.resolve("maps").resolve(var3 + ".json"),
                        this.folder.resolve("maps").resolve(var3 + ".json.deleted"),
                        StandardCopyOption.REPLACE_EXISTING
                     );
                     this.maps.remove(var3);
                     this.state(var1, "", false, "已移到 .deleted 备份");
                     break;
                  case "createRoom":
                     this.createRoom(var3, var1);
                     this.state(var1, var3, false, "房间已创建，请先进入房间再选队");
                     break;
                  case "selectTeam":
                     this.selectTeam(var1, num(var5, "team", -1));
                     this.state(var1, this.member(var1).map, false, "阵营已选择");
                     break;
                  case "start":
                     this.requireRoomControl(var1, var3);
                     Model.require(this.member(var1).map.equals(var3), "请先进入该房间");
                     this.startRoom(var3);
                     break;
                  case "stop":
                     this.requireRoomControl(var1, var3);
                     this.finish(var3, "房主或管理员结束对局");
                     this.state(var1, var3, false, "对局已结束");
                     break;
                  case "join":
                     this.join(var1, var3);
                     this.state(var1, var3, false, "已进入房间，请选择红机械轴心");
                     break;
                  case "leave":
                     this.leave(var1);
                     this.state(var1, var3, false, "已退出，原背包与位置已恢复");
                     break;
                  case "redeploy":
                     Engine.Member var12 = this.member(var1);
                     Model.require(var12.alive, "已在部署界面");
                     this.death(var1, null);
                     this.state(var1, var12.map, true, "重新部署会扣除一次阵亡兵力");
                     break;
                  case "deploy":
                     this.deploy(
                        var1,
                        str(var5, "kit", ""),
                        str(var5, "spawn", ""),
                        str(var5, "vehicle", ""),
                        str(var5, "primary", null),
                        str(var5, "secondary", null),
                        str(var5, "melee", null),
                        var5
                     );
                     this.state(var1, this.member(var1).map, false, "已部署");
                     break;
                  default:
                     throw new IllegalArgumentException("未知操作");
               }
            } catch (Exception var21) {
               BattlefieldOps.LOG.debug("Battlefield request rejected", var21);
               String var6 = var21.getMessage() == null ? var21.getClass().getSimpleName() : var21.getMessage();
               if (var4.startsWith("lobby")) {
                  this.lobby(var1, var4.equals("lobbyReturn"), "操作失败：" + var6);
               } else {
                  this.state(var1, var3, false, "操作失败：" + var6);
               }
            }
         }
      }
   }

   private ServerLevel probeLevel() {
      ServerLevel var1 = this.server.overworld();
      if (var1 != null) {
         return var1;
      } else {
         Iterator var2 = this.server.getAllLevels().iterator();
         return var2.hasNext() ? (ServerLevel)var2.next() : null;
      }
   }

   private Map<String, Object> armamentTable() {
      if (this.armamentCache != null) {
         return this.armamentCache;
      } else {
         LinkedHashMap var1 = new LinkedHashMap();

         for (String var3 : this.catalog.vehicles) {
            var var4 = this.armamentInfo(var3);
            if (var4 != null) {
               var1.put(var3, var4);
            }
         }

         this.armamentCache = var1;
         return var1;
      }
   }

   private Map<String, Object> armamentInfo(String var1) {
      try {
         EntityType var2 = (EntityType)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(var1));
         if (var2 == null) {
            return null;
         } else {
            ServerLevel var3 = this.probeLevel();
            if (var3 == null) {
               return null;
            } else {
               Entity var4 = var2.create(var3);
               if (var4 == null) {
                  return null;
               } else {
                  Map var17;
                  try {
                     LinkedHashMap var5 = new LinkedHashMap();

                     for (Entry var7 : VehicleEquip.stationStacks(var4).entrySet()) {
                        var var8 = new ArrayList();

                        for (ItemStack var10 : (java.lang.Iterable<ItemStack>)((List)var7.getValue())) {
                           LinkedHashMap var11 = new LinkedHashMap();
                           var11.put("id", BuiltInRegistries.ITEM.getKey(var10.getItem()).toString());
                           var11.put("stack", Math.max(1, var10.getMaxStackSize()));
                           var8.add(var11);
                        }

                        if (!var8.isEmpty()) {
                           var5.put((String)var7.getKey(), var8);
                        }
                     }

                     if (!var5.isEmpty()) {
                        var17 = new LinkedHashMap();
                        var17.put("slots", VehicleEquip.containerSize(var4));
                        var17.put("stations", var5);
                        return var17;
                     }

                     var17 = null;
                  } finally {
                     var4.discard();
                  }

                  return var17;
               }
            }
         }
      } catch (Exception var16) {
         BattlefieldOps.LOG.warn("Cannot read weapon stations for " + var1, var16);
         return null;
      }
   }

   private void catalog(ServerPlayer var1, JsonObject var2) {
      int var3 = var2.has("all") && var2.get("all").getAsBoolean() ? -1 : Math.max(0, num(var2, "page", 0));
      String var4 = str(var2, "query", "").toLowerCase(Locale.ROOT);
      Model.require(var4.length() < 100, "检索词过长");
      String var5 = str(var2, "category", "");
      List var6 = this.catalog
         .entries
         .stream()
         .filter(var2x -> var5.isEmpty() || var5.equals(this.catalog.category(var2x)))
         .filter(var1x -> LibrarySearch.matches(var4, var1x.id(), var1x.name(), LibrarySource.searchable(var1x.id(), var1x.kind())))
         .toList();
      int var7 = var3 < 0 ? 0 : Math.min(var6.size(), var3 * 64);
      LinkedHashMap var8 = new LinkedHashMap();
      var8.put("type", "catalog");
      var8.put("page", var3);
      var8.put("total", var6.size());
      var8.put("entries", var3 < 0 ? var6 : var6.subList(var7, Math.min(var6.size(), var7 + 64)));
      var8.put("complete", var3 < 0);
      var8.put("diagnostics", this.catalog.diagnostics);
      LinkedHashMap var9 = new LinkedHashMap();
      LinkedHashMap var10 = new LinkedHashMap();

      for (String var12 : this.catalog.vehicles) {
         String var13 = this.catalog.vehicleTypes.get(var12);
         if (var13 != null) {
            var9.put(var12, Catalog.vehicleTypeLabel(var13) + " / " + var13);
         }

         var10.put(var12, String.valueOf(this.catalog.vehicleClass(var12)));
      }

      var8.put("gunTypes", this.catalog.gunTypes);
      var8.put("vehicleTypes", var9);
      var8.put("vehicleClasses", var10);
      var8.put("knives", this.catalog.knives());
      var var14 = LrtEquipment.entries();
      var8.put("lrtEntries", var14);
      var8.put("meleeEntries", var14.stream().filter(var0 -> ((String)var0.get("category")).equals("melee")).toList());
      if (var3 < 0) {
         Network.send(var1, var8);
      } else {
         var8.put("vehicleArmament", this.armamentTable());
         Network.send(var1, var8);
      }
   }

   private void admin(ServerPlayer var1) {
      Model.require(var1.hasPermissions(2), "需要管理员权限（OP 2）");
   }

   private Engine.Member member(ServerPlayer var1) {
      Engine.Member var2 = this.members.get(var1.getUUID());
      Model.require(var2 != null, "尚未加入对局");
      return var2;
   }

   private void save(Model.Arena var1) throws IOException {
      MapFiles.save(this.folder.resolve("maps"), var1.id, BattlefieldOps.GSON.toJson(var1));
   }

   private void createRoom(String var1, ServerPlayer var2) {
      Model.Arena var3 = this.maps.get(var1);
      Model.require(var3 != null, "先保存地图");
      Model.require(!this.matches.containsKey(var1), "该地图已经有一个房间");
      Model.require(
         !this.lobbySettings.enabled || !RoomPolicy.hubConflict(var3, this.lobbySettings.dimension, this.lobbySettings.x, this.lobbySettings.z),
         "地图及退出缓冲区过于接近共享大厅，请调整地图边界或大厅位置"
      );
      var3.validate();
      this.catalog.check(var3);

      for (Engine.Match var5 : this.matches.values()) {
         Model.Arena var6 = var5.rules.map;
         if (var6.dimension.equals(var3.dimension)) {
            Model.require(!RoomPolicy.overlaps(var3, var6), "地图及退出缓冲区与其他房间占用相同区块");
         }
      }

      Model.Arena var7 = (Model.Arena)BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(var3), Model.Arena.class);
      Engine.Match var8 = new Engine.Match(var7);
      var8.owner = var2.getUUID();
      var8.ownerName = var2.getScoreboardName();
      this.matches.put(var1, var8);
      this.results.remove(var1);
      this.sidebar(var8);
   }

   private void releaseElite(UUID var1, Engine.Member var2) {
      Engine.Match var3 = this.matches.get(var2.map);
      if (var3 != null) {
         var3.elites.release(var1, this.ticks);
      }

      var2.eliteType = "";
   }

   private String eliteBlocker(Engine.Match var1, Engine.Member var2, ServerPlayer var3, EliteRules.Spawn var4) {
      EliteRules.Template var5 = EliteRules.template(var1.rules.map, var4.type);
      if (!EliteRules.configured(var5)) {
         return "未配置精英装备";
      } else if (var1.rules.isStaged() && var4.sector >= 0 && var4.sector != var1.rules.stage) {
         return "阶段未解锁";
      } else {
         long var6 = EliteRules.delayRemaining(var1.elites.startedAt, var4, this.ticks);
         return var6 > 0L ? "首次刷新 " + (var6 + 19L) / 20L + " 秒" : var1.elites.blocker(var3.getUUID(), var2.team, var4.type, this.ticks);
      }
   }

   private List<Map<String, Object>> eliteSnapshot(ServerPlayer var1, Engine.Member var2, Engine.Match var3) {
      var var4 = new ArrayList();

      for (EliteRules.Spawn var6 : var3.rules.map.eliteSpawns) {
         if (var6.enabled && (var6.team < 0 || var6.team == var2.team)) {
            LinkedHashMap var7 = new LinkedHashMap();
            var7.put("id", var6.id);
            var7.put("type", var6.type);
            var7.put("name", EliteRules.Kind.of(var6.type).name);
            var7.put("x", Double.valueOf(var6.x));
            var7.put("y", Double.valueOf(var6.y));
            var7.put("z", Double.valueOf(var6.z));
            var7.put("radius", Double.valueOf(var6.radius));
            var7.put("reason", this.eliteBlocker(var3, var2, var1, var6));
            var4.add(var7);
         }
      }

      return var4;
   }

   private void claimElite(ServerPlayer var1) throws Exception {
      Engine.Member var2 = this.member(var1);
      Engine.Match var3 = this.matches.get(var2.map);
      Model.require(var3 != null && var3.running && var2.team >= 0 && var2.alive && !var2.downed && var1.isAlive() && !var1.isSpectator(), "仅存活且已部署的玩家可领取精英装备");
      Model.require(var2.eliteType.isEmpty(), "当前已是精英兵");
      Model.require(!var1.isPassenger() && var1.containerMenu == var1.inventoryMenu, "请离开载具并关闭容器后领取");
      Model.Arena var4 = var3.rules.map;
      Model.require(var1.serverLevel() == this.level(var4), "不在当前战区");
      EliteRules.Spawn var5 = var4.eliteSpawns
         .stream()
         .filter(var3x -> EliteRules.reachable(var3x, var2.team, var3.rules.stage, var3.rules.isStaged(), var1.getX(), var1.getY(), var1.getZ()))
         .min(Comparator.<EliteRules.Spawn>comparingDouble(var1x -> var1.distanceToSqr(var1x.x, var1x.y, var1x.z)).thenComparing(var0 -> var0.id))
         .orElseThrow(() -> new IllegalArgumentException("附近没有可领取的精英刷新点"));
      String var6 = this.eliteBlocker(var3, var2, var1, var5);
      Model.require(var6.isEmpty(), var6);
      BlockHitResult var7 = var1.level().clip(new ClipContext(var1.getEyePosition(), new Vec3(var5.x, var5.y + 0.5, var5.z), Block.COLLIDER, Fluid.NONE, var1));
      Model.require(var7.getType() == Type.MISS, "刷新点被遮挡");
      Model.Kit var8 = EliteRules.template(var4, var5.type).kit;
      var var9 = new ArrayList();

      for (KitGear.Entry var11 : var8.gear().items) {
         var9.add(var11.id);
      }

      var9.addAll(var8.gear().armour.values());
      var9.addAll(var8.gear().accessories.values());

      for (String var19 : (java.lang.Iterable<String>)(var9)) {
         Model.require(!KitItems.resolve(var19, 1).isEmpty(), "精英装备物品未安装：" + var19);
      }

      Model.require(var8.gear().accessories.isEmpty() || CuriosSupport.available(), "未安装饰品支持，无法完整领取此套精英装备");
      var var18 = new ArrayList<>(this.catalog.gunRounds(var1, var8.options(true).getFirst(), ammoStacks(var8, var8.options(true).getFirst())));
      var18.addAll(this.catalog.gunRounds(var1, var8.options(false).getFirst(), ammoStacks(var8, var8.options(false).getFirst())));
      var18.addAll(KitItems.build(var8, var1));
      Model.require(!var18.isEmpty() || !var8.gear().armour.isEmpty(), "精英兵须配置有效武器、物品或护甲");
      Model.require(var18.size() <= 35, "精英装备超过背包容量");
      ListTag var20 = var1.getInventory().save(new ListTag());
      ListTag var12 = CuriosSupport.save(var1);
      var3.elites.reserve(var1.getUUID(), var2.team, var5.type, var5.respawnSeconds, this.ticks);

      try {
         var1.closeContainer();
         var1.getInventory().clearContent();
         CuriosSupport.clear(var1);

         for (ItemStack var14 : (java.lang.Iterable<ItemStack>)(var18)) {
            var1.getInventory().add(var14);
            Model.require(var14.isEmpty(), "精英装备无法完整放入背包");
         }

         KitItems.wear(var8, var1);
         var2.eliteType = var5.type;
         var1.inventoryMenu.broadcastChanges();
      } catch (LinkageError | Exception var16) {
         var3.elites.cancel(var1.getUUID());
         var2.eliteType = "";
         var1.getInventory().clearContent();
         var1.getInventory().load(var20);
         CuriosSupport.clear(var1);
         if (var12 != null) {
            CuriosSupport.load(var1, var12);
         }

         var1.inventoryMenu.broadcastChanges();
         throw var16;
      }

      this.refills.issued(var1);
      String var22 = var5.type;

      String var21 = switch (var22) {
         case "antivehicle" -> "voice_herokit_at";
         case "flametrooper" -> "voice_herokit_flame";
         case "runner" -> "voice_herokit_runner";
         case "sentry" -> "voice_herokit_sentry";
         case "trenchraider" -> "voice_herokit_trench";
         default -> "";
      };
      if (!var21.isEmpty()) {
         Network.send(var1, Map.of("type", "supportSound", "cue", var21));
      }
   }

   private boolean roomControl(ServerPlayer var1, Engine.Match var2) {
      return RoomPolicy.canManage(var1.hasPermissions(2), var1.getUUID(), var2 == null ? null : var2.owner);
   }

   private void requireRoomControl(ServerPlayer var1, String var2) {
      Model.require(this.matches.containsKey(var2) && this.roomControl(var1, this.matches.get(var2)), "仅房主或管理员可以开始、关闭该房间");
   }

   private void startRoom(String var1) {
      Engine.Match var2 = this.matches.get(var1);
      Model.require(var2 != null && !var2.running, "房间不存在或已经开始");
      this.compat.reset();
      var2.running = true;
      var2.elites.startedAt = this.ticks;

      for (ServerPlayer var4 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var5 = this.members.get(var4.getUUID());
         if (var5 != null && var5.map.equals(var1)) {
            if (var5.team >= 0) {
               this.board.apply(var4, var1, var5.team, var2.rules.map.friendlyFire, var2.rules.map.hideEnemyNametags);
            }

            this.state(var4, var1, false, "对局开始，可以部署");
         }
      }
   }

   private void join(ServerPlayer var1, String var2) throws IOException {
      Model.require(Network.supported(var1), "安装战地风云客户端模组后才能加入对局");
      Engine.Match var3 = this.matches.get(var2);
      long var4 = this.members.values().stream().filter(var1x -> var1x.map.equals(var2) && !var1x.bot).count();
      String var6 = RoomPolicy.joinBlocker(this.members.containsKey(var1.getUUID()), var3 != null, var4, var3 == null ? 0 : var3.rules.map.maxPlayers);
      Model.require(var6.isEmpty(), var6);
      Model.require(var1.isAlive() && !var1.isPassenger(), "请存活且离开载具后加入");
      Model.require(!EditorFlight.active(var1), "请先退出快速编辑模式，再加入房间");
      this.recover(var1);
      var1.closeContainer();
      this.backup(var1);
      this.members.put(var1.getUUID(), new Engine.Member(var2, var1.getScoreboardName(), -1));
      var1.getInventory().clearContent();
      var1.setGameMode(GameType.SPECTATOR);
      Engine.Member var7 = this.members.get(var1.getUUID());
      Model.Arena var8 = var3.rules.map;
      if (var8.entryEnabled) {
         var7.entryStarted = this.ticks;
         var7.entryUntil = this.ticks + (long)var8.entrySeconds * 20L + 20L;
         var7.entryToken = UUID.randomUUID().toString();
         var7.observerAt = this.ticks;
         this.positionDeployObservers();
      }
   }

   private int trainingTeam(ServerPlayer var1, int var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (var3 == null) {
         return var2;
      } else {
         Engine.Match var4 = this.matches.get(var3.map);
         return var4 != null && var4.rules.map.isTraining() ? 0 : var2;
      }
   }

   private void selectTeam(ServerPlayer var1, int var2) {
      int var3 = this.trainingTeam(var1, var2);
      Engine.Member var4 = this.member(var1);
      Engine.Match var5 = this.matches.get(var4.map);
      Model.require(var5 != null, "房间已关闭");
      Model.require(this.ticks >= var4.entryUntil, "请等待入局简报结束");
      Model.require(var3 == 0 || var3 == 1, "阵营无效");
      Model.require(!var4.alive, "请在部署阶段选择阵营");
      Model.require(!var5.running || var4.team < 0 || var4.team == var3, "对局开始后不能更换阵营");
      long var6 = this.members.values().stream().filter(var2x -> var2x != var4 && var2x.map.equals(var4.map) && var2x.team == var3).count();
      long var8 = this.members.values().stream().filter(var2x -> var2x != var4 && var2x.map.equals(var4.map) && var2x.team == 1 - var3).count();
      Model.require(var6 <= var8 || var4.team == var3, "该阵营人数较多，请加入另一方");
      Model.Spawn var10 = Tactics.safestSpawn(this.deploySpawns(var5.rules, var3), this.enemiesOf(var5.rules, var3));
      Model.require(var10 != null, "该阵营缺少出生点");
      if (var4.team != var3) {
         this.removeOwnedBeacon(var1.getUUID());
         var4.squad = 0;
      }

      var4.team = var3;
      var4.observerAt = this.ticks;
      if (var5.running) {
         this.board.apply(var1, var4.map, var3, var5.rules.map.friendlyFire, var5.rules.map.hideEnemyNametags);
      } else {
         this.board.clearPlayer(var1);
      }

      String var11 = DeployView.warning(var5.rules.map.extent(var5.rules.map.viewArea(var3))[2], this.viewDistance());
      if (var11 != null) {
         this.announce(var1, "部署视角", var11);
      }
   }

   private int viewDistance() {
      try {
         return this.server.getPlayerList().getViewDistance() * 16;
      } catch (RuntimeException var2) {
         return 100;
      }
   }

   private void positionDeployObservers() {
      for (Entry var2 : this.members.entrySet()) {
         Engine.Member var3 = (Engine.Member)var2.getValue();
         boolean var4 = var3.entryUntil > this.ticks;
         if (!var3.bot && !var3.alive && !var3.downed && (var4 || var3.team >= 0) && var3.observerAt >= 0L && this.ticks >= var3.observerAt) {
            Engine.Match var5 = this.matches.get(var3.map);
            ServerPlayer var6 = this.server.getPlayerList().getPlayer((UUID)var2.getKey());
            if (var5 != null && var6 != null) {
               Model.Arena var7 = var5.rules.map;
               var var8 = var4 ? var7.boundary : var7.viewArea(var3.team);
               if (var8.isEmpty()) {
                  var3.observerAt = -1L;
               } else {
                  double var9 = Double.POSITIVE_INFINITY;
                  double var11 = Double.NEGATIVE_INFINITY;
                  double var13 = var9;
                  double var15 = var11;

                  for (Model.Vec var18 : (java.lang.Iterable<Model.Vec>)(var8)) {
                     var9 = Math.min(var9, var18.x());
                     var11 = Math.max(var11, var18.x());
                     var13 = Math.min(var13, var18.z());
                     var15 = Math.max(var15, var18.z());
                  }

                  double var29 = var7.spawns.stream().filter(var2x -> var4 || var2x.team == var3.team).mapToDouble(var0 -> var0.y).max().orElse(64.0);
                  double var19 = var7.customDeployView ? var7.deployViewX : (var9 + var11) / 2.0;
                  double var21 = var7.customDeployView ? var7.deployViewZ : (var13 + var15) / 2.0;
                  double var23 = var7.customDeployView ? var7.deployViewY - 1.62 : var29 + 80.0;
                  float var25 = var7.customDeployView ? var7.deployViewYaw : 0.0F;
                  float var26 = var7.customDeployView ? var7.deployViewPitch : 90.0F;
                  if (var4 && var7.customEntryView) {
                     var19 = var7.entryX;
                     var23 = var7.entryY - 1.62;
                     var21 = var7.entryZ;
                     var25 = var7.entryYaw;
                     var26 = var7.entryPitch;
                  }

                  try {
                     var6.setCamera(var6);
                     var6.stopRiding();
                     var6.teleportTo(this.level(var7), var19, var23, var21, var25, var26);
                     var6.setDeltaMovement(Vec3.ZERO);
                     var6.fallDistance = 0.0F;
                     var3.observerAt = -1L;
                  } catch (RuntimeException var28) {
                     var3.observerAt = this.ticks + 100L;
                     BattlefieldOps.LOG.warn("Cannot position deployment observer for {}", var3.map, var28);
                  }
               }
            }
         }
      }
   }

   private List<Model.Spawn> legalSpawns(Rules var1, int var2) {
      Model.Arena var3 = var1.map;
      return var3.spawns
         .stream()
         .filter(var2x -> var2x.team == var2 && (!var1.isStaged() || var2x.sector == var1.stage))
         .filter(var2x -> var3.inZone(var2, var2x.x, var2x.z))
         .toList();
   }

   private List<ItemStack> tacticalStacks(ServerPlayer var1, Model.Kit var2, Model.Arena var3, JsonObject var4, String var5) throws Exception {
      String var6 = str(var4, var5, "");
      if (var6.isEmpty()) {
         return List.of();
      } else {
         boolean var7 = var3.isTraining() || var2.tacticalChoices().contains(var6);
         if (!var7) {
            return List.of();
         } else if (this.catalog.isGun(var6)) {
            var var11 = this.catalog.gunRounds(var1, var6, ammoStacks(var2, var6));
            applyAmmoType(var2, var6, var11);
            return var11;
         } else if (!SuperbAmmo.isGunId(var6)) {
            return countedStacks(var6, var2.countFor(var6));
         } else {
            String var8 = ammoChoice(var2, var6, var4);
            int var9 = var2.itemCounts != null && var2.itemCounts.containsKey(var8) ? var2.countFor(var8) : var2.countFor(var6);
            var var10 = SuperbAmmo.gunBundle(var6, var9, var8.isEmpty() ? null : var8);
            applyAmmoType(var2, var6, var10);
            return var10;
         }
      }
   }

   private static int ammoStacks(Model.Kit var0, String var1) {
      Integer var2 = var0.itemCounts == null ? null : var0.itemCounts.get(var1);
      int ammoStacksResolved = var2 != null ? Math.max(1, Math.min(64, var2)) : ammoStacks(var0);
      BattlefieldOps.LOG.info("[bfops] ammo for {}: configured={} -> {} rounds", var1, var2, ammoStacksResolved);
      return ammoStacksResolved;
   }

   private static void applyAmmoType(Model.Kit var0, String var1, List<ItemStack> var2) {
      if (var0 != null && var1 != null && !var2.isEmpty()) {
         ItemStack var3 = (ItemStack)var2.getFirst();
         if (SuperbAmmo.isGun(var3)) {
            SuperbAmmo.logOnce(var3, var1);
            int var4 = var0.countFor(var1);
            String var5 = var0.gunAmmoType == null ? null : var0.gunAmmoType.get(var1);
            String var6 = SuperbAmmo.chosen(var3, var5);
            if (!var6.isEmpty()) {
               String var7 = SuperbAmmo.slotFor(var3, var6);
               if (!var7.isEmpty()) {
                  SuperbAmmo.apply(var3, var7, var4);
               }
            }
         }
      }
   }

   private static List<ItemStack> countedStacks(String var0, int var1) {
      if (var0 != null && !var0.isEmpty() && var1 > 0) {
         ItemStack var2 = KitItems.resolve(var0, var1);
         if (var2.isEmpty()) {
            return List.of();
         } else {
            var var3 = new ArrayList();
            int var4 = Math.max(1, var2.getMaxStackSize());
            int var5 = var1;

            while (var5 > 0) {
               ItemStack var6 = var2.copy();
               int var7 = Math.min(var5, var4);
               var6.setCount(var7);
               var3.add(var6);
               var5 -= var7;
            }

            return var3;
         }
      } else {
         return List.of();
      }
   }

   private static ItemStack issueCounted(ServerPlayer var0, String var1, int var2) {
      if (var1 != null && !var1.isEmpty() && var2 > 0) {
         ItemStack var3 = KitItems.resolve(var1, var2);
         if (var3.isEmpty()) {
            return var3;
         } else {
            int var4 = var2 - var3.getCount();
            int var5 = Math.max(1, var3.getMaxStackSize());

            while (var4 > 0) {
               ItemStack var6 = var3.copy();
               int var7 = Math.min(var4, var5);
               var6.setCount(var7);
               giveToBackpack(var0, var6);
               var4 -= var7;
            }

            return var3;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static int topUp(ServerPlayer var0, Item var1, int var2) {
      return topUp(var0, var1, var2, -1);
   }

   private static int topUp(ServerPlayer var0, Item var1, int var2, int var3) {
      if (var2 > 0 && var1 != null) {
         Inventory var4 = var0.getInventory();
         int var5 = 0;

         for (int var6 = 0; var6 < var4.getContainerSize() && var2 > 0; var6++) {
            ItemStack var7 = var4.getItem(var6);
            if (!var7.isEmpty() && var7.is(var1)) {
               int var8 = var7.getMaxStackSize() - var7.getCount();
               if (var8 > 0) {
                  int var9 = Math.min(var8, var2);
                  var7.grow(var9);
                  var2 -= var9;
                  var5 += var9;
               }
            }
         }

         boolean var10 = true;

         while (var2 > 0) {
            int var11 = Math.max(1, var1.getDefaultMaxStackSize());
            int var12 = Math.min(var2, var11);
            ItemStack var13 = new ItemStack(var1, var12);
            if (var10) {
               giveToSlot(var0, var13, var3);
               var10 = false;
            } else {
               giveToBackpack(var0, var13);
            }

            var2 -= var12;
            var5 += var12;
         }

         return var5;
      } else {
         return 0;
      }
   }

   private static void giveToSlot(ServerPlayer var0, ItemStack var1, int var2) {
      if (!var1.isEmpty()) {
         Inventory var3 = var0.getInventory();
         if (var2 >= 0 && var2 < 36 && var3.getItem(var2).isEmpty()) {
            var3.setItem(var2, var1);
         } else if (var2 >= 0 && var2 < 9 && var3.getItem(var2).isEmpty()) {
            var3.setItem(var2, var1);
         } else {
            giveToBackpack(var0, var1);
         }
      }
   }

   private static void giveToBackpack(ServerPlayer var0, ItemStack var1) {
      if (!var1.isEmpty()) {
         Inventory var2 = var0.getInventory();

         for (int var3 = 9; var3 < 36; var3++) {
            if (var2.getItem(var3).isEmpty()) {
               var2.setItem(var3, var1);
               return;
            }
         }

         if (!var2.add(var1)) {
            var0.drop(var1, false);
         }
      }
   }

   private static String ammoChoice(Model.Kit var0, String var1, JsonObject var2) {
      var var3 = var0 == null ? List.of() : var0.ammoWhitelist(var1);
      if (var3.isEmpty()) {
         var var4 = SuperbAmmo.gunBundle(var1, 0);
         var3 = var4.isEmpty() ? List.of() : SuperbAmmo.ammoIds((ItemStack)var4.getFirst());
      }

      String var6 = str(var2, "ammo_" + var1, "");
      if (var6.isEmpty() || !var3.isEmpty() && !var3.contains(var6)) {
         String var5 = var0 != null && var0.gunAmmoType != null ? var0.gunAmmoType.getOrDefault(var1, "") : "";
         if (var5.isEmpty() || !var3.isEmpty() && !var3.contains(var5)) {
            return var3.isEmpty() ? "" : (String)var3.getFirst();
         } else {
            return var5;
         }
      } else {
         return var6;
      }
   }

   private static int ammoStacks(Model.Kit var0) {
      // The map's number is authoritative for deployment: resupply now tops up to whatever was issued, so a
      // large default no longer turns into a full backpack.
      return var0.ammoStacks > 0 ? var0.ammoStacks : 3;
   }

   private List<Model.Spawn> deploySpawns(Rules var1, int var2) {
      if (var1.map.isTraining()) {
         var var3 = Deploy.sites(var1.map, var2, var1.stage, var1.isStaged(), Deploy.owners(var1));
         if (var3.isEmpty()) {
            return List.of();
         } else {
            Deploy.Site var4 = (Deploy.Site)var3.get(0);
            return List.of(var4.asSpawn(var2));
         }
      } else {
         return Deploy.sites(var1.map, var2, var1.stage, var1.isStaged(), Deploy.owners(var1)).stream().map(var1x -> var1x.asSpawn(var2)).toList();
      }
   }

   private void refreshMarks(Engine.Match var1) {
      if (var1 != null) {
         long var2 = System.currentTimeMillis();
         var1.marks.removeIf(var2x -> var2x == null || !var2x.active(var2));

         while (var1.marks.size() > 32) {
            var1.marks.removeFirst();
         }
      }
   }

   private static String whereIs(Engine.Match var0) {
      return var0 != null && var0.rules != null && var0.rules.map != null ? var0.rules.map.id : "";
   }

   private static VehicleArmament armamentFor(Model.Arena var0, Entity var1) {
      if (var0 != null && var1 != null) {
         String var2 = BuiltInRegistries.ENTITY_TYPE.getKey(var1.getType()).toString();
         return var0.armament(var2);
      } else {
         return new VehicleArmament();
      }
   }

   private static String padKey(String var0, String var1) {
      return var0 + "|" + var1;
   }

   private long padSecondsLeft(String var1, String var2) {
      Long var3 = this.padReadyAt.get(padKey(var1, var2));
      if (var3 != null && var3 > 0L) {
         long var4 = var3 - this.ticks;
         return var4 <= 0L ? 0L : (var4 + 19L) / 20L;
      } else {
         return 0L;
      }
   }

   private List<Map<String, Object>> padBlips(String var1) {
      Engine.Match var2 = this.matches.get(var1);
      if (var2 == null) {
         return List.of();
      } else {
         var var3 = new ArrayList();

         for (VehicleSupply.Supply var5 : var2.rules.map.supplyPoints()) {
            LinkedHashMap var6 = new LinkedHashMap();
            var6.put("id", var5.id());
            var6.put("x", var5.x());
            var6.put("y", var5.y());
            var6.put("z", var5.z());
            var6.put("readyIn", this.padSecondsLeft(var1, var5.id()));
            var3.add(var6);
         }

         return var3;
      }
   }

   int refillCooldownTicks(String var1, SupportRules.Kind var2) {
      Engine.Match var3 = this.matches.get(var1);
      return var3 != null && var3.rules.map != null ? Math.max(0, var3.rules.map.refillSeconds(var2)) * 20 : var2.cooldown;
   }

   int refillPeriodTicks(String var1) {
      Engine.Match var2 = this.matches.get(var1);
      return var2 != null && var2.rules.map != null ? Math.max(0, var2.rules.map.ammoBoxRefillSeconds) * 20 : 0;
   }

   private boolean resupplyPlayer(ServerPlayer var1, VehicleSupply.Supply var2) {
      if (var1 != null && var2 != null) {
         boolean var3 = false;
         if (var2.health() && var1.getHealth() < var1.getMaxHealth()) {
            var1.setHealth(var1.getMaxHealth());
            var1.getFoodData().setFoodLevel(20);
            var3 = true;
         }

         if (var2.ammo()) {
            try {
               var3 |= this.supportAmmo(var1) > 0;
            } catch (ReflectiveOperationException var5) {
               BattlefieldOps.LOG.debug("Support ammunition bridge unavailable", var5);
            }
         }

         return var3;
      } else {
         return false;
      }
   }

   private void updateSupply(Model.Arena var1, List<ServerPlayer> var2) {
      if (!var1.supplyPoints().isEmpty()) {
         Engine.Match var3 = this.matches.get(var1.id);
         int var4 = var3 == null ? 0 : var3.rules.stage;
         boolean var5 = var3 != null && var3.rules.isStaged();

         for (ServerPlayer var7 : var2) {
            Engine.Member var8 = this.members.get(var7.getUUID());
            if (var8 != null && var8.team >= 0 && var8.alive && var7.isAlive()) {
               VehicleSupply.Supply var9 = var1.supplyAt(var8.team, var4, var5, var7.getX(), var7.getY(), var7.getZ());
               if (var9 != null) {
                  if (var1.supplyCooldownSeconds > 0) {
                     String var10 = padKey(var1.id, var9.id());
                     Long var11 = this.padReadyAt.get(var10);
                     if (var11 != null && this.ticks < var11) {
                        continue;
                     }
                  }

                  Entity var12 = var7.getVehicle();
                  if (var12 != null && this.vehicles.contains(var12.getUUID())) {
                     if (VehicleEquip.resupply(var12, var9, armamentFor(var1, var12))) {
                        var7.displayClientMessage(Component.literal("§b补给点 §f已补充 " + var9.id()), true);
                        if (var1.supplyCooldownSeconds > 0) {
                           this.padReadyAt.put(padKey(var1.id, var9.id()), this.ticks + (long)var1.supplyCooldownSeconds * 20L);
                        }
                     }
                  } else if (this.resupplyPlayer(var7, var9)) {
                     if (var1.supplyCooldownSeconds > 0) {
                        this.padReadyAt.put(padKey(var1.id, var9.id()), this.ticks + (long)var1.supplyCooldownSeconds * 20L);
                     }

                     var7.displayClientMessage(Component.literal("§b补给点 §f已恢复生命 " + var9.id()), true);
                  }
               }
            }
         }
      }
   }

   private void reapEmptyVehicles(Engine.Match var1) {
      if (!var1.vehicles.isEmpty()) {
         for (UUID var3 : new ArrayList<>(var1.vehicles)) {
            Entity var4 = this.findEntity(var3);
            if (var4 == null || var4.isRemoved()) {
               var1.vehicles.remove(var3);
               var1.vehicleTeams.remove(var3);
               this.vehicles.remove(var3);
            } else if (var4.getPassengers().isEmpty()) {
               int var5 = var1.vehicleTeams.getOrDefault(var3, -1);
               if (var5 >= 0) {
                  double var6 = Double.MAX_VALUE;

                  for (ServerPlayer var9 : this.server.getPlayerList().getPlayers()) {
                     Engine.Member var10 = this.members.get(var9.getUUID());
                     if (var10 != null && var10.team == var5 && var10.alive) {
                        var6 = Math.min(var6, Math.sqrt(var9.distanceToSqr(var4)));
                     }
                  }

                  if (VehicleDeploy.orphaned(var4.getPassengers().size(), var6, 64.0)) {
                     var4.discard();
                     var1.vehicles.remove(var3);
                     var1.vehicleTeams.remove(var3);
                     this.vehicles.remove(var3);
                  }
               }
            }
         }
      }
   }

   private List<Tactics.At> enemiesOf(Rules var1, int var2) {
      var var3 = new ArrayList();

      for (ServerPlayer var5 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var6 = this.members.get(var5.getUUID());
         if (var6 != null && var6.team != var2 && var6.alive && var6.map.equals(var1.map.id)) {
            var3.add(new Tactics.At(var5.getX(), var5.getY(), var5.getZ()));
         }
      }

      return var3;
   }

   private void backup(ServerPlayer var1) throws IOException {
      CompoundTag var2 = new CompoundTag();
      var2.put("inventory", var1.getInventory().save(new ListTag()));
      ListTag var3 = CuriosSupport.save(var1);
      if (var3 != null) {
         var2.put("curios", var3);
      }

      var2.putString("previousTeam", var1.getTeam() != null && !Board.owned(var1.getTeam().getName()) ? var1.getTeam().getName() : "");
      var2.putString("dimension", var1.level().dimension().location().toString());
      var2.putDouble("x", var1.getX());
      var2.putDouble("y", var1.getY());
      var2.putDouble("z", var1.getZ());
      var2.putFloat("yaw", var1.getYRot());
      var2.putFloat("pitch", var1.getXRot());
      var2.putInt("mode", var1.gameMode.getGameModeForPlayer().getId());
      var2.putFloat("health", var1.getHealth());
      var2.putInt("food", var1.getFoodData().getFoodLevel());
      var2.putFloat("saturation", var1.getFoodData().getSaturationLevel());
      var2.putFloat("foodExhaustion", var1.getFoodData().getExhaustionLevel());
      Path var4 = this.folder.resolve("players").resolve(var1.getUUID() + ".nbt");
      Model.require(!Files.exists(var4), "尚有未恢复的背包备份，请先联系管理员处理");
      Path var5 = var4.resolveSibling(var4.getFileName() + ".tmp");
      NbtIo.writeCompressed(var2, var5);
      Files.move(var5, var4);
   }

   public void recover(ServerPlayer var1) {
      if (!this.members.containsKey(var1.getUUID())) {
         this.board.clearPlayer(var1);
         Path var2 = this.folder.resolve("players").resolve(var1.getUUID() + ".nbt");
         if (Files.exists(var2)) {
            try {
               CompoundTag var3 = NbtIo.readCompressed(var2, NbtAccounter.unlimitedHeap());
               ServerLevel var4 = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(var3.getString("dimension"))));
               Model.require(var4 != null, "原维度不存在，备份已保留等待恢复");
               var1.closeContainer();
               var1.stopRiding();
               var1.getInventory().clearContent();
               var1.getInventory().load(var3.getList("inventory", 10));
               CuriosSupport.clear(var1);
               if (var3.contains("curios")) {
                  CuriosSupport.load(var1, var3.getList("curios", 10));
               }

               var1.setGameMode(GameType.byId(var3.getInt("mode")));
               var1.teleportTo(var4, var3.getDouble("x"), var3.getDouble("y"), var3.getDouble("z"), var3.getFloat("yaw"), var3.getFloat("pitch"));
               var1.setHealth(Math.max(1.0F, Math.min(var1.getMaxHealth(), var3.getFloat("health"))));
               var1.getFoodData().setFoodLevel(var3.getInt("food"));
               var1.getFoodData().setSaturation(var3.getFloat("saturation"));
               if (var3.contains("foodExhaustion")) {
                  var1.getFoodData().setExhaustion(var3.getFloat("foodExhaustion"));
               }

               var1.inventoryMenu.broadcastChanges();
               this.board.restoreTeam(var1, var3.getString("previousTeam"));
               Files.delete(var2);
            } catch (Exception var5) {
               BattlefieldOps.LOG.error("Cannot restore player {}", var1.getUUID(), var5);
               var1.sendSystemMessage(Component.literal("战地背包恢复失败，备份已保留：" + var5.getMessage()));
            }
         }
      }
   }

   public void leave(ServerPlayer var1) {
      this.refills.leave(var1.getUUID());
      Engine.Member var2 = this.members.remove(var1.getUUID());
      if (var2 != null) {
         this.career.poll(var1, var2);
         this.career.leave(var1.getUUID(), var2);
         TaczProneBridge.clear(var1);
      }

      if (var2 != null) {
         try {
            this.releaseElite(var1.getUUID(), var2);
         } catch (LinkageError | RuntimeException var9) {
            BattlefieldOps.LOG.error("Cannot release departing elite", var9);
         }
      }

      try {
         this.removeOwnedBeacon(var1.getUUID());
      } catch (LinkageError | RuntimeException var8) {
         BattlefieldOps.LOG.error("Cannot remove player's beacon", var8);
      }

      try {
         this.support.leave(var1.getUUID());
         this.finalHonours.leave(var1.getUUID());
         this.atmosphere.leave(var1.getUUID());
      } catch (LinkageError | RuntimeException var7) {
         BattlefieldOps.LOG.error("Cannot clear player's support devices", var7);
      }

      if (var2 != null) {
         try {
            downPose(var1, false);
            this.clearReviver(var1.getUUID());
            var1.stopRiding();
         } catch (LinkageError | RuntimeException var6) {
            BattlefieldOps.LOG.error("Cannot reset departing player pose", var6);
         }

         try {
            this.clearGlow(var1, var2);
         } catch (LinkageError | RuntimeException var5) {
            BattlefieldOps.LOG.error("Cannot clear departing player team", var5);
         }
      }

      try {
         this.board.clearPlayer(var1);
      } catch (LinkageError | RuntimeException var4) {
         BattlefieldOps.LOG.error("Cannot clear departing scoreboard team", var4);
      }

      this.recover(var1);
      if (var2 != null) {
         Engine.Match var3 = this.matches.get(var2.map);
         if (var3 != null) {
            this.sidebar(var3);
         }
      }

      this.requests.remove(var1.getUUID());
      this.polls.remove(var1.getUUID());
   }

   public void disconnected(ServerPlayer var1) {
      this.leave(var1);

      for (String var3 : new ArrayList<>(this.matches.keySet())) {
         Engine.Match var4 = this.matches.get(var3);
         if (var4 != null && var1.getUUID().equals(var4.owner)) {
            ServerPlayer var5 = this.server.getPlayerList().getPlayers().stream().filter(var1x -> !var1x.getUUID().equals(var1.getUUID())).filter(var2 -> {
               Engine.Member var3x = this.members.get(var2.getUUID());
               return var3x != null && !var3x.bot && var3x.map.equals(var3);
            }).findFirst().orElse(null);
            if (var5 == null) {
               this.finish(var3, "房主离线，空房间已关闭");
            } else {
               var4.owner = var5.getUUID();
               var4.ownerName = var5.getScoreboardName();
               this.state(var5, var3, false, "原房主离线，你已成为房主");
            }
         }
      }
   }

   private void exitAtCurrentPosition(ServerPlayer var1) {
      ServerLevel var2 = var1.serverLevel();
      double var3 = var1.getX();
      double var5 = var1.getY();
      double var7 = var1.getZ();
      float var9 = var1.getYRot();
      float var10 = var1.getXRot();
      this.leave(var1);
      var1.teleportTo(var2, var3, var5, var7, var9, var10);
      var1.fallDistance = 0.0F;
   }

   private void teleport(ServerPlayer var1, ServerLevel var2, Model.Spawn var3) {
      var1.teleportTo(var2, var3.x, var3.y, var3.z, var3.yaw, 0.0F);
      var1.setDeltaMovement(Vec3.ZERO);
      var1.fallDistance = 0.0F;
   }

   static Map<String, String> selections(JsonObject var0, String var1) {
      LinkedHashMap var2 = new LinkedHashMap();
      if (!var0.has(var1)) {
         return var2;
      } else {
         for (Entry var4 : var0.getAsJsonObject(var1).entrySet()) {
            Model.require(var2.size() < 32 && ((String)var4.getKey()).length() < 128, "改装参数过多或过长");
            String var5 = ((JsonElement)var4.getValue()).getAsString();
            Model.require(var5.length() < 160, "配件 ID 过长");
            var2.put((String)var4.getKey(), var5);
         }

         return var2;
      }
   }

   private void fittings(ServerPlayer var1, JsonObject var2) throws Exception {
      Engine.Member var3 = this.members.get(var1.getUUID());
      String var4 = str(var2, "id", "");
      String var5 = str(var2, "kind", "");
      LinkedHashMap var7 = new LinkedHashMap();
      int var8 = this.progress.level(var1.getUUID());
      if (var3 != null && !str(var2, "map", "").isBlank()) {
         Engine.Match var9 = this.matches.get(var3.map);
         Model.require(RoomPolicy.sameRoom(var3.map, str(var2, "map", "")), "房间已改变，请重新打开配装");
         Model.require(var9 != null, "请先进入房间");
         Model.Arena var10 = var9.rules.map;
         List var18;
         if (var5.equals("gun")) {
            String var11 = str(var2, "kit", var3.kit);
            Model.Kit var12 = var10.kits
               .stream()
               .filter(var1x -> var1x.id.equals(var11))
               .findFirst()
               .orElseThrow(() -> new IllegalArgumentException("请选择当前地图兵种"));
            Model.require(
               var10.isTraining() && this.catalog.isGun(var4) || var12.options(true).contains(var4) || var12.options(false).contains(var4), "武器未获本图此兵种授权"
            );
            var18 = this.catalog.gunFittings.getOrDefault(var4, List.of());

            for (Fittings.Group var14 : (java.lang.Iterable<Fittings.Group>)(var18)) {
               for (Fittings.Option var16 : var14.options()) {
                  String var17 = InfantryAttachments.blocker(var12, var4, var16.id(), var8);
                  if (!var17.isEmpty()) {
                     var7.put(var16.id(), var17);
                  }
               }
            }
         } else {
            Model.require(var5.equals("vehicle") && var10.vehicles.contains(var4), "载具未获本图授权");
            var18 = this.catalog.vehicleFittings.getOrDefault(var4, List.of());
         }

         Network.send(
            var1,
            Map.of(
               "type", "fittings", "id", var4, "kind", var5, "groups", var18, "locks", var7, "level", var8, "kit", str(var2, "kit", var3.kit), "map", var3.map
            )
         );
      } else {
         Model.require(var5.equals("gun") && this.catalog.isGun(var4) || var5.equals("vehicle") && this.catalog.isVehicle(var4), "装备未安装");
         var var6 = (var5.equals("gun") ? this.catalog.gunFittings : this.catalog.vehicleFittings).getOrDefault(var4, List.of());
         Network.send(var1, Map.of("type", "fittings", "id", var4, "kind", var5, "groups", var6, "locks", var7, "level", var8, "kit", "", "map", ""));
      }
   }

   private void rearm(ServerPlayer var1, JsonObject var2) throws Exception {
      Engine.Member var3 = this.members.get(var1.getUUID());
      Model.require(var3 != null, "请先部署");
      Engine.Match var4 = this.matches.get(var3.map);
      Model.require(var4 != null && var4.rules.map != null, "对局未开始");
      Model.Arena var5 = var4.rules.map;
      Model.require(var5.isTraining(), "只有靶场可以随时切换装备");
      Model.Kit var6 = var5.kits
         .stream()
         .filter(var2x -> var2x.id.equals(str(var2, "kit", var3.kit)))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("请选择当前地图兵种"));
      String var7 = str(var2, "primary", "");
      String var8 = str(var2, "secondary", "");
      String var9 = this.catalog.guns.contains(var7) ? var7 : var6.select(true, var7, var3.team);
      String var10 = this.catalog.guns.contains(var8) ? var8 : var6.select(false, var8, var3.team);
      int var9Rounds = ammoStacks(var6, var9);
      var var11 = this.catalog.gunRounds(var1, var9, var9Rounds);
      applyAmmoType(var6, var9, var11);
      int var10Rounds = ammoStacks(var6, var10);
      var var12 = this.catalog.gunRounds(var1, var10, var10Rounds);
      applyAmmoType(var6, var10, var12);
      var3.issuedAmmo.put(var9, var9Rounds);
      var3.issuedAmmo.put(var10, var10Rounds);
      ItemStack var13 = var11.isEmpty() ? ItemStack.EMPTY : (ItemStack)var11.getFirst();
      ItemStack var14 = var12.isEmpty() ? ItemStack.EMPTY : (ItemStack)var12.getFirst();
      String var15 = str(var2, "melee", "");
      ItemStack var16 = ItemStack.EMPTY;
      if (!var15.isEmpty()) {
         if (this.catalog.isGun(var15)) {
            var var17 = this.catalog.gun(var1, var15, 0);
            if (!var17.isEmpty()) {
               var16 = (ItemStack)var17.getFirst();
            }
         } else {
            var16 = KitItems.resolve(var15, 1);
         }
      }

      String var35 = str(var2, "throwable", "");
      ItemStack var18 = ItemStack.EMPTY;
      if (!var35.isEmpty()) {
         var18 = issueCounted(var1, var35, var6.countFor(var35));
      }

      var var19 = new ArrayList();
      var var20 = var11.size() <= 1 ? List.of() : var11.subList(1, var11.size());
      var var21 = var12.size() <= 1 ? List.of() : var12.subList(1, var12.size());
      var var22 = this.tacticalStacks(var1, var6, var5, var2, "tactical1");
      var var23 = this.tacticalStacks(var1, var6, var5, var2, "tactical2");
      ItemStack var24 = var22.isEmpty() ? ItemStack.EMPTY : (ItemStack)var22.getFirst();
      ItemStack var25 = var23.isEmpty() ? ItemStack.EMPTY : (ItemStack)var23.getFirst();
      if (var22.size() > 1) {
         var19.addAll(var22.subList(1, var22.size()));
      }

      if (var23.size() > 1) {
         var19.addAll(var23.subList(1, var23.size()));
      }

      LinkedHashMap var26 = new LinkedHashMap();

      for (KitGear.Entry var28 : var6.gear().deduplicatedItems()) {
         var26.put(var28.id, var28);
      }

      for (String var41 : var6.issueOrder()) {
         if (var41.equals("main_ammo")) {
            var19.addAll(var20);
         } else if (var41.equals("side_ammo")) {
            var19.addAll(var21);
         }
      }

      if (var2.has("trainingItems")) {
         for (JsonElement var42 : var2.getAsJsonArray("trainingItems")) {
            if (var19.size() >= 24) {
               break;
            }

            ItemStack var29 = KitItems.resolve(var42.getAsString(), 1);
            if (!var29.isEmpty()) {
               var19.add(var29);
            }
         }
      }

      var1.closeContainer();
      var1.getInventory().clearContent();
      var1.getInventory().setItem(0, var13);
      var1.getInventory().setItem(1, var14);
      var1.getInventory().setItem(2, var16);
      int var38 = 3;
      if (!var18.isEmpty()) {
         var3.issuedSlots.put(str(var2, "throwable", ""), var38);
         var1.getInventory().setItem(var38++, var18);
      }

      if (!var24.isEmpty()) {
         var3.issuedSlots.put(str(var2, "tactical1", ""), var38);
         var1.getInventory().setItem(var38++, var24);
      }

      if (!var25.isEmpty()) {
         var3.issuedSlots.put(str(var2, "tactical2", ""), var38);
         var1.getInventory().setItem(var38++, var25);
      }

      var1.getInventory().setItem(var38++, new ItemStack(Items.COOKED_BEEF, 16));
      if (var5.isTraining() && SupportItems.DEPLOY_TERMINAL.get() != null) {
         var1.getInventory().setItem(var38++, new ItemStack((ItemLike)SupportItems.DEPLOY_TERMINAL.get()));
      }

      for (ItemStack var45 : (java.lang.Iterable<ItemStack>)(var19)) {
         giveToBackpack(var1, var45);
      }

      CuriosSupport.clear(var1);
      KitItems.wear(var6, var1);
      var3.kit = var6.id;
      var3.lastKit = var6.id;
      var3.lastPrimary = var9;
      var3.lastMelee = str(var2, "melee", "");
      var3.lastSecondary = var10;
      var3.tactical1 = var24.isEmpty() ? "" : str(var2, "tactical1", "");
      var3.throwable = var18.isEmpty() ? "" : str(var2, "throwable", "");
      var3.issuedTemplates.clear();
      var var44 = var3.issuedTemplates;
      if (!var35.isEmpty() && !var18.isEmpty()) {
         var44.put(var35, var18.copyWithCount(1));
      }

      if (!var24.isEmpty()) {
         String var46 = str(var2, "tactical1", "");
         if (!var46.isEmpty()) {
            var44.put(var46, var24.copyWithCount(1));
         }
      }

      if (!var25.isEmpty()) {
         String var47 = str(var2, "tactical2", "");
         if (!var47.isEmpty()) {
            var44.put(var47, var25.copyWithCount(1));
         }
      }

      var3.ammoTemplates.clear();

      for (String var32 : new String[]{var9, var10, var3.tactical1, var3.tactical2}) {
         if (var32 != null && !var32.isEmpty() && this.catalog.isGun(var32)) {
            try {
               var var33 = this.catalog.gun(var1, var32, 1);
               if (var33.size() > 1) {
                  var3.ammoTemplates.put(var32, ((ItemStack)var33.get(1)).copyWithCount(1));
               }
            } catch (RuntimeException | ReflectiveOperationException var34) {
            }
         }
      }

      var3.gunAmmo.clear();

      for (String var52 : new String[]{var9, var10, var3.tactical1, var3.tactical2}) {
         if (var52 != null && !var52.isEmpty() && SuperbAmmo.isGunId(var52)) {
            String var53 = ammoChoice(var6, var52, var2);
            if (!var53.isEmpty()) {
               var3.gunAmmo.put(var52, var53);
            }
         }
      }

      var3.tactical2 = var25.isEmpty() ? "" : str(var2, "tactical2", "");
   }

   private void changeSquad(ServerPlayer var1, int var2) {
      Engine.Member var3 = this.member(var1);
      Model.require(var3.team >= 0, "请先选择阵营");
      Model.require(!var3.alive, "请在待部署时切换小队");
      if (var2 != var3.squad) {
         long var4 = this.members
            .values()
            .stream()
            .filter(var2x -> var2x != var3 && SquadRules.same(var3.map, var3.team, var2, var2x.map, var2x.team, var2x.squad))
            .count();
         Model.require(var2 == 0 || SquadRules.canJoin(var2, (int)var4), "小队已满或编号无效");
         this.removeOwnedBeacon(var1.getUUID());
         var3.squad = var2;
      }
   }

   private void removeOwnedBeacon(UUID var1) {
      for (Engine.Beacon var3 : new ArrayList<>(this.beacons.values())) {
         if (var3.owner.equals(var1)) {
            this.removeBeacon(var3);
         }
      }
   }

   private void removeBeacon(Engine.Beacon var1) {
      Entity var2 = this.findEntity(var1.entity);
      if (var2 != null) {
         var2.discard();
      }

      this.beacons.remove(var1.entity);
      this.beaconUses.remove(var1.entity);
      this.beaconHealth.remove(var1.entity);
   }

   void restoreBeacon(UUID var1) {
      Engine.Beacon var2 = this.beacons.get(var1);
      if (var2 != null) {
         Engine.Member var3 = this.members.get(var2.owner);
         Engine.Match var4 = this.matches.get(var2.map);
         if (this.supportActive(var3)
            && var4 != null
            && this.ticks < var2.expires
            && SquadRules.same(var2.map, var2.team, var2.squad, var3.map, var3.team, var3.squad)) {
            DeployBeacon var5 = new DeployBeacon(this.level(var4.rules.map), (double)var2.at.getX() + 0.5, (double)var2.at.getY(), (double)var2.at.getZ() + 0.5);
            var5.setUUID(var1);
            var5.setHealth(this.beaconHealth.getOrDefault(var1, 10.0F));
            var5.setNoGravity(true);
            var5.setInvisible(true);
            var5.setCustomNameVisible(false);
            var5.setCustomName(Component.literal(SquadRules.label(var2.squad) + " · 信标 " + this.beaconUses.getOrDefault(var1, 6) + " 次"));
            var5.setItemSlot(EquipmentSlot.HEAD, ConquestEquipment.stack(SupportRules.Kind.RADIO_BEACON.key(), 1));
            var5.addTag("bfops_beacon");
            this.level(var4.rules.map).addFreshEntity(var5);
         }
      }
   }

   Model.Arena supportMap(Engine.Member var1) {
      Engine.Match var2 = var1 == null ? null : this.matches.get(var1.map);
      return var2 == null ? null : var2.rules.map;
   }

   void supportRefresh(ServerPlayer var1) {
      Engine.Member var2 = this.members.get(var1.getUUID());
      if (var2 != null) {
         this.state(var1, var2.map, false, "");
      }
   }

   boolean supportActive(Engine.Member var1) {
      return var1 != null && var1.team >= 0 && this.matches.containsKey(var1.map) && this.matches.get(var1.map).running;
   }

   boolean supportPosition(Engine.Member var1, Vec3 var2) {
      return this.supportActive(var1) && this.legalDynamic(this.matches.get(var1.map), var1.team, var2.x, var2.y, var2.z);
   }

   void supportPlaceBeacon(ServerPlayer var1) {
      this.placeBeacon(var1);
   }

 
  /** TaCZ registers one ammunition item and distinguishes types by the AmmoId in its custom data. */
  private static String ammoIdOf(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return null;
    var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    if (data == null) return null;
    var tag = data.copyTag();
    return tag.contains("AmmoId") ? tag.getString("AmmoId") : null;
  }

  /** How many rounds of this exact ammunition the player is carrying. */
  private static int countAmmo(ServerPlayer player, ItemStack template) {
    int total = 0;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (sameAmmo(stack, template)) total += stack.getCount();
    }
    return total;
  }

  /** Same ammunition: same item and, when TaCZ tags it, the same AmmoId. Stack size etc. are ignored. */
  private static boolean sameAmmo(ItemStack a, ItemStack b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
    if (!a.is(b.getItem())) return false;
    String left = ammoIdOf(a), right = ammoIdOf(b);
    if (left == null || right == null) return ItemStack.isSameItemSameComponents(a, b);
    return left.equals(right);
  }

  /** Set while an ammunition bag tops up, so only gun ammunition is handed out. */
  private boolean bagGunsOnly;

  /** Ammunition bag variant: main and secondary weapons only, no throwables, no gadgets. */
  /** True when this gun is the player's primary or secondary, as opposed to a tactical-slot launcher. */
  private static boolean isMainOrSecondary(Engine.Member member, Model.Kit kit, String gunId) {
    if (gunId == null || gunId.isEmpty()) return false;
    if (gunId.equals(member.lastPrimary) || gunId.equals(member.lastSecondary)) return true;
    if (kit == null) return false;
    return gunId.equals(kit.primary) || gunId.equals(kit.secondary);
  }

  /**
   * One unit of ammunition: a single magazine-sized stack for the first gun that is short, otherwise a
   * single round of the first throwable or tactical item that is short. The box calls this once per
   * configured interval so "one item every N seconds" really means one item.
   */
  /** True when there is anything at all for a supply point to hand this player. */
  boolean supportNeeded(ServerPlayer player) throws ReflectiveOperationException {
    Engine.Member member = this.members.get(player.getUUID());
    if (member == null || !this.supportActive(member) || !member.alive || member.downed) return false;
    Model.Kit kit = this.matches.get(member.map).rules.map.kits.stream()
        .filter(k -> k.id.equals(member.kit)).findFirst().orElse(null);
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack gun = player.getInventory().getItem(slot);
      String gunId = gunIdOf(gun);
      if (gunId.isEmpty() || !this.catalog.isGun(gunId)) continue;
      List<ItemStack> bundle;
      try {
        bundle = this.catalog.gun(player, gunId, 1);
      } catch (RuntimeException missing) {
        continue;
      }
      if (bundle.size() < 2) continue;
      Integer recorded = member.issuedAmmo == null ? null : member.issuedAmmo.get(gunId);
      int want = Math.max(1, recorded != null ? recorded : ammoStacks(kit, gunId));
      if (countAmmo(player, bundle.get(1)) < want) return true;
    }
    for (String id : new String[]{member.throwable, member.tactical1, member.tactical2}) {
      if (id == null || id.isEmpty()) continue;
      if (this.catalog.isGun(id) || SuperbAmmo.isGunId(id)) continue;
      if (ConquestEquipment.kind(id) != null || SupportItems.isSupportId(id)) continue;
      int want = kit == null ? 1 : kit.countFor(id);
      int have = 0;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id)) have += stack.getCount();
      }
      if (have < want) return true;
    }
    return false;
  }

  int supportAmmoUnit(ServerPlayer player) throws ReflectiveOperationException {
    Engine.Member member = this.members.get(player.getUUID());
    if (!this.supportActive(member) || !member.alive || member.downed) return 0;
    Model.Kit kit = this.matches.get(member.map).rules.map.kits.stream()
        .filter(k -> k.id.equals(member.kit)).findFirst().orElse(null);
    if (kit == null) return 0;

    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack gun = player.getInventory().getItem(slot);
      if (gun.isEmpty() || !this.catalog.isGun(gunIdOf(gun))) continue;
      String gunId = gunIdOf(gun);
      List<ItemStack> bundle;
      try {
        bundle = this.catalog.gun(player, gunId, 1);
      } catch (RuntimeException missing) {
        continue;
      }
      if (bundle.size() < 2) continue;
      ItemStack template = bundle.get(1);
      Integer recorded = member.issuedAmmo == null ? null : member.issuedAmmo.get(gunId);
      int want = Math.max(1, recorded != null ? recorded : ammoStacks(kit, gunId));
      int have = countAmmo(player, template);
      if (have >= want) continue;
      int give = Math.min(want - have, Math.max(1, template.getMaxStackSize()));
      ItemStack stack = template.copyWithCount(give);
      giveToBackpack(player, stack);
      int added = give - stack.getCount();
      if (added > 0) {
        BattlefieldOps.LOG.info("[bfops] box unit: {} +{} (want={} have={})", gunId, added, want, have);
        return added;
      }
    }

    for (String id : new String[]{member.throwable, member.tactical1, member.tactical2}) {
      if (id == null || id.isEmpty() || this.catalog.isGun(id) || SuperbAmmo.isGunId(id)) continue;
      if (ConquestEquipment.kind(id) != null || SupportItems.isSupportId(id)) continue;
      int want = kit.countFor(id);
      int have = 0;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id)) have += stack.getCount();
      }
      if (have >= want) continue;
      ItemStack one = KitItems.resolve(id, 1);
      if (one.isEmpty()) continue;
      giveToBackpack(player, one);
      if (one.isEmpty()) {
        BattlefieldOps.LOG.info("[bfops] box unit: {} +1", id);
        return 1;
      }
    }
    return 0;
  }

  private static String gunIdOf(ItemStack stack) {
    try {
      Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
      if (!gun.isInstance(stack.getItem())) return "";
      return String.valueOf(gun.getMethod("getGunId", ItemStack.class).invoke(stack.getItem(), stack));
    } catch (ReflectiveOperationException | RuntimeException unreadable) {
      return "";
    }
  }

  int supportAmmoBag(ServerPlayer player) throws ReflectiveOperationException {
    this.bagGunsOnly = true;
    try {
      int added = supportAmmo(player);
      BattlefieldOps.LOG.info("[bfops] ammo bag for {} -> {} units", player.getScoreboardName(), added);
      return added;
    } finally {
      this.bagGunsOnly = false;
    }
  }

  /**
   * Ammunition belongs in the hotbar or the backpack, never in the three right-hand slots (6-8) that the
   * HUD uses for gadgets: anything that landed there is moved into the backpack.
   */
  private static void relocateColumnAmmo(ServerPlayer player) {
    var inventory = player.getInventory();
    for (int slot = 0; slot <= 8; slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (stack.isEmpty() || !looksLikeAmmo(stack)) continue;
      ItemStack moving = stack.copy();
      inventory.setItem(slot, ItemStack.EMPTY);
      // Only the backpack rows: add() would put it straight back into the hotbar.
      for (int target = 9; target < inventory.getContainerSize() && !moving.isEmpty(); target++) {
        ItemStack existing = inventory.getItem(target);
        if (!existing.isEmpty() && sameAmmo(existing, moving)) {
          int room = existing.getMaxStackSize() - existing.getCount();
          if (room > 0) {
            int moved = Math.min(room, moving.getCount());
            existing.grow(moved);
            moving.shrink(moved);
          }
        }
      }
      for (int target = 9; target < inventory.getContainerSize() && !moving.isEmpty(); target++) {
        if (inventory.getItem(target).isEmpty()) {
          inventory.setItem(target, moving.copy());
          moving.setCount(0);
        }
      }
      if (!moving.isEmpty()) player.drop(moving, false);
    }
  }

  /** Ammunition is tagged by TaCZ / Superb Warfare with an AmmoId in its custom data. */
  private static boolean looksLikeAmmo(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return false;
    String full = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    // Class gear is never ammunition, whatever its name says: ammo_bag and ammo_box used to be moved into
    // the backpack mid-use, which left the consumed stack untouched and duplicated the item.
    if (SupportItems.isSupportId(full) || ConquestEquipment.kind(full) != null) return false;
    if (ammoIdOf(stack) != null) return true;
    String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    if (path.endsWith("_bag") || path.endsWith("_box") || path.startsWith("ammo_")) return false;
    return path.contains("ammo") || path.contains("rocket") || path.contains("magazine");
  }

  /**
   * Hard cap: whatever the counters thought, the player never carries more than the configured amount of
   * one ammunition type. Excess is removed from the backpack first, then from the hotbar.
   */
  private static void capAmmo(ServerPlayer player, ItemStack template, int want) {
    var inventory = player.getInventory();
    int total = countAmmo(player, template);
    for (int slot = inventory.getContainerSize() - 1; slot >= 0 && total > want; slot--) {
      ItemStack stack = inventory.getItem(slot);
      if (!sameAmmo(stack, template)) continue;
      int excess = Math.min(stack.getCount(), total - want);
      stack.shrink(excess);
      total -= excess;
      if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
    }
  }

  /** The map's configured amount for this gun, or 0 when it is not configured at all. */
  private static int kitCount(Model.Kit kit, String gunId) {
    if (kit == null || kit.itemCounts == null) return 0;
    Integer configured = kit.itemCounts.get(gunId);
    return configured == null ? 0 : Math.max(0, configured);
  }


  int supportAmmo(ServerPlayer var1) throws ReflectiveOperationException {
    BattlefieldOps.LOG.info("[bfops] supportAmmo start for {}", var1.getScoreboardName());
      Engine.Member var2 = this.members.get(var1.getUUID());
    // Elite kits carry ordinary guns too, so they get ammunition as well; the old elite check made the
    // ammo bag silently do nothing on those classes.
    if (this.supportActive(var2) && var2.alive && !var2.downed) {
         Model.Kit var3 = this.matches.get(var2.map).rules.map.kits.stream().filter(var1x -> var1x.id.equals(var2.kit)).findFirst().orElse(null);
    BattlefieldOps.LOG.info("[bfops] supportAmmo kit={} throwable={} t1={} t2={}", var3 == null ? "null" : var3.id, var2.throwable, var2.tactical1, var2.tactical2);
    java.util.Set<String> countedGuns = new java.util.HashSet<>();
         int var4 = 0;
         LinkedHashMap var5 = new LinkedHashMap();
         LinkedHashMap var6 = new LinkedHashMap();

         Class var7;
         try {
            var7 = Class.forName("com.tacz.guns.api.item.IGun");
         } catch (ClassNotFoundException var22) {
            return 0;
         }

      for (int var8 = 0; var8 < var1.getInventory().getContainerSize(); var8++) {
        ItemStack var9 = var1.getInventory().getItem(var8);
        if (var9.isEmpty() || !var7.isInstance(var9.getItem())) continue;
        String gunId;
        try {
          gunId = var7.getMethod("getGunId", ItemStack.class).invoke(var9.getItem(), var9).toString();
        } catch (ReflectiveOperationException unreadable) {
          continue;
        }
        if (this.bagGunsOnly && !isMainOrSecondary(var2, var3, gunId)) {
          // The bag feeds the primary and secondary only; launchers in the tactical category are left alone.
          continue;
        }
        BattlefieldOps.LOG.info("[bfops] resupply sees gun {} (catalog={})", gunId, this.catalog.guns.contains(gunId));
        List<ItemStack> bundle;
        try {
          bundle = this.catalog.gun(var1, gunId, 1);
        } catch (RuntimeException notIndexed) {
          BattlefieldOps.LOG.info("[bfops] resupply cannot build {}: {}", gunId, notIndexed.getMessage());
          continue;
        }
        if (bundle.size() > 1) {
          countedGuns.add(gunId);
          String templateKey = bundle.get(1).getComponents().toString();
          var5.putIfAbsent(templateKey, bundle.get(1));
          // Target for an ammunition item that came straight from the inventory scan, not from the cached
          // deploy-time map: without this the loop below has a template but no wanted amount.
          // Shared ammunition: two guns drawing on one stock must not double the reserve, so the larger
          // requirement wins instead of the sum (which turned 180 into 360).
          // Top up to what this player actually deployed with, not to the map's default: a weapon issued with
          // 2 rounds must come back with 2, not with 64 or 180.
          Integer recorded = var2.issuedAmmo == null ? null : var2.issuedAmmo.get(gunId);
          // No record (deployed before this field existed): fall back to the same number deployment uses,
          // i.e. the map's configured carry amount. It must not drift with whatever the player happens to hold.
          int issued = recorded != null ? recorded : ammoStacks(var3, gunId);
var6.merge(templateKey, Math.max(1, issued), (a, b) -> Math.max((Integer)a, (Integer)b));
        } else {
          BattlefieldOps.LOG.info("[bfops] resupply {} built no ammunition", gunId);
        }
      }

    if (var3 != null && !this.bagGunsOnly) {
            LinkedHashSet var23 = new LinkedHashSet();
            if (!var2.throwable.isEmpty()) {
               var23.add(var2.throwable);
            }

            if (!var2.tactical1.isEmpty()) {
               var23.add(var2.tactical1);
            }

            if (!var2.tactical2.isEmpty()) {
               var23.add(var2.tactical2);
               // Everything else actually issued at deploy: a C4 in slot 5 or a med bag in slot 4 counts too.
               if (var2.issuedTemplates != null) {
                 for (String issuedId : var2.issuedTemplates.keySet()) {
                   if (issuedId == null || issuedId.isEmpty()) continue;
                   if (this.catalog.isGun(issuedId) || SuperbAmmo.isGunId(issuedId)) continue;
                   if (ConquestEquipment.kind(issuedId) != null || SupportItems.isSupportId(issuedId)) continue;
                   var23.add(issuedId);
                 }
               }
            }

            Iterator var27 = var23.iterator();

            label339:
            while (true) {
               String var31;
               ResourceLocation var35;
               do {
                  if (!var27.hasNext()) {
                     break label339;
                  }

                  var31 = (String)var27.next();
                  if (LrtEquipment.explicit(var31)) {
                     break;
                  }

                  var35 = ResourceLocation.tryParse(var31);
               } while (var35 == null || !BuiltInRegistries.ITEM.containsKey(var35));

               if (!var31.isEmpty() && !this.catalog.isGun(var31) && !SuperbAmmo.isGunId(var31) && ConquestEquipment.kind(var31) == null && !SupportItems.isSupportId(var31)) {
                  int var36 = var3.countFor(var31);
                  ItemStack var12 = var2.issuedTemplates == null ? ItemStack.EMPTY : var2.issuedTemplates.get(var31);
                  ItemStack var13 = !var12.isEmpty() ? var12 : KitItems.resolve(var31, 1);
                  int var14 = 0;

                  for (int var15 = 0; var15 < var1.getInventory().getContainerSize(); var15++) {
                     ItemStack var16 = var1.getInventory().getItem(var15);
                     if (!var16.isEmpty() && !var13.isEmpty() && ItemStack.isSameItemSameComponents(var16, var13)) {
                        var14 += var16.getCount();
                     }
                  }

                  int var47 = var36 - var14;
                  if (var47 > 0) {
                     if (!var12.isEmpty()) {
                        int var52 = var47;

                        for (int var56 = 0; var56 < var1.getInventory().getContainerSize() && var52 > 0; var56++) {
                           ItemStack var59 = var1.getInventory().getItem(var56);
                           if (!var59.isEmpty() && ItemStack.isSameItemSameComponents(var59, var12)) {
                              int var63 = var59.getMaxStackSize() - var59.getCount();
                              if (var63 > 0) {
                                 int var20 = Math.min(var63, var52);
                                 var59.grow(var20);
                                 var52 -= var20;
                                 var4 += var20;
                              }
                           }
                        }

                        int var57 = var2.issuedSlots == null ? -1 : var2.issuedSlots.getOrDefault(var31, -1);
                        boolean var60 = true;

                        while (var52 > 0) {
                           int var64 = Math.min(var52, Math.max(1, var12.getMaxStackSize()));
                           ItemStack var67 = var12.copyWithCount(var64);
                           if (var60) {
                              giveToSlot(var1, var67, var57);
                              var60 = false;
                           } else {
                              giveToBackpack(var1, var67);
                           }

                           var52 -= var64;
                           var4 += var64;
                        }
                     } else if (LrtEquipment.explicit(var31)) {
                        for (int var51 = 0; var51 < var1.getInventory().getContainerSize() && var47 > 0; var51++) {
                           ItemStack var55 = var1.getInventory().getItem(var51);
                           if (!var55.isEmpty() && var31.equals(LrtEquipment.idOf(var55))) {
                              int var18 = var55.getMaxStackSize() - var55.getCount();
                              if (var18 > 0) {
                                 int var19 = Math.min(var18, var47);
                                 var55.grow(var19);
                                 var47 -= var19;
                                 var4 += var19;
                              }
                           }
                        }
                     } else {
                        ItemStack var50 = KitItems.resolve(var31, 1);
                        int var17 = var2.issuedSlots == null ? -1 : var2.issuedSlots.getOrDefault(var31, -1);
                        if (!var50.isEmpty()) {
                           var4 += topUp(var1, var50.getItem(), var47, var17);
                        }
                     }
                  }
               }
            }
         }

         for (int var24 = 0; var24 < var1.getInventory().getContainerSize(); var24++) {
            ItemStack var28 = var1.getInventory().getItem(var24);
            if (SuperbAmmo.isGun(var28)) {
               String var32 = BuiltInRegistries.ITEM.getKey(var28.getItem()).toString();
               if (this.bagGunsOnly && !isMainOrSecondary(var2, var3, var32)) continue;   // bag: primary and secondary only
               String var37 = var2.gunAmmo.get(var32);
               boolean var39 = var3 != null && var3.itemCounts != null && var37 != null && var3.itemCounts.containsKey(var37);
               int var41 = var3 == null ? 1 : (var39 ? var3.countFor(var37) : var3.countFor(var32));
               if (var37 == null) {
                  var37 = var3 != null && var3.gunAmmoType != null ? var3.gunAmmoType.get(var32) : null;
               }

               String var44 = SuperbAmmo.chosen(var28, var37);
               if (!var44.isEmpty()) {
                  ResourceLocation var48 = ResourceLocation.tryParse(var44);
                  if (var48 != null && BuiltInRegistries.ITEM.containsKey(var48)) {
                     Item var53 = (Item)BuiltInRegistries.ITEM.get(var48);
                     int var58 = 0;

                     for (int var61 = 0; var61 < var1.getInventory().getContainerSize(); var61++) {
                        ItemStack var65 = var1.getInventory().getItem(var61);
                        if (!var65.isEmpty() && var65.is(var53)) {
                           var58 += var65.getCount();
                        }
                     }

                     int var62 = var41 - var58;
                     if (var62 > 0) {
                        int var66 = Math.max(1, var53.getDefaultMaxStackSize());

                        while (var62 > 0) {
                           int var68 = Math.min(var62, var66);
                           ItemStack var21 = new ItemStack(var53, var68);
                           giveToBackpack(var1, var21);
                           var62 -= var68;
                           var4 += var68;
                        }
                     }
                  }
               }
            }
         }

         if (var2.ammoTemplates != null) {
            for (Entry var29 : var2.ammoTemplates.entrySet()) {
              // A gun that was replaced by a later deploy must not keep feeding its old ammunition.
              if (var2.issuedAmmo != null && !var2.issuedAmmo.containsKey((String)var29.getKey())) continue;
               if (var29.getValue() != null && !((ItemStack)var29.getValue()).isEmpty()) {
                  String var33 = ((ItemStack)var29.getValue()).getComponents().toString();
                  var5.putIfAbsent(var33, (ItemStack)var29.getValue());
                  // Sum the wants of every gun that shares this ammunition item: counting by item is accurate, but
        // several guns (or a tactical launcher and a rifle) can draw on the same stock, and taking only
        // the first gun's number made the crate stop feeding the rest.
        Integer recordedCached = var2.issuedAmmo == null ? null : var2.issuedAmmo.get((String)var29.getKey());
        int issuedCached = recordedCached != null ? recordedCached
            : ammoStacks(var3, (String)var29.getKey());
        var6.merge(var33, Math.max(1, issuedCached), (a, b) -> Math.max((Integer)a, (Integer)b));
               }
            }
         }

    BattlefieldOps.LOG.info("[bfops] supportAmmo ammo templates: {} entries", var5.size());
         for (Entry var30 : (java.lang.Iterable<Entry>)(var5.entrySet())) {
            ItemStack var34 = (ItemStack)var30.getValue();
            int var38 = var3 == null ? 1 : Math.max(1, ((Integer)var6.getOrDefault(var30.getKey(), ammoStacks(var3))));
            int var40 = 0;

            for (int var42 = 0; var42 < var1.getInventory().getContainerSize(); var42++) {
               ItemStack var45 = var1.getInventory().getItem(var42);
        // Count by item *and* AmmoId: TaCZ uses a single ammunition item for every calibre, so comparing by
        // item alone made every type count as one and the crate thought the rockets were full.
        if (sameAmmo(var45, var34)) {
                  var40 += var45.getCount();
               }
            }

            // ammoStacks is a round count now, not a stack count: multiplying by the stack size refilled the whole
      // backpack (4 x 64 rockets) instead of the configured four rounds.
      int var43 = Math.max(0, var38 - var40);
      // Diagnostic: shows exactly what the crate wants, sees and hands over for each ammunition type.
      BattlefieldOps.LOG.info("[bfops] resupply {}: want={} have={} give={}", var30.getKey(), var38, var40, var43);
      // Safety: never hand out more than two stacks in one pass, whatever the numbers say.
      var43 = Math.min(var43, 2 * Math.max(1, var34.getMaxStackSize()));

            while (var43 > 0) {
               int var46 = Math.min(var43, var34.getMaxStackSize());
               ItemStack var49 = var34.copyWithCount(var46);
               var1.getInventory().add(var49);
               int var54 = var46 - var49.getCount();
               var4 += var54;
               var43 -= var54;
               if (var54 == 0) {
                  break;
               }
      capAmmo(var1, var34, var38);
            }
         }

         if (var4 > 0) {
      relocateColumnAmmo(var1);
            var1.inventoryMenu.broadcastChanges();
            var1.getInventory().setChanged();
         }

         return var4;
      } else {
         return 0;
      }

   }

   void supportCredit(ServerPlayer var1, ServerPlayer var2, String var3) {
      if (var1 != var2) {
         Engine.Member var4 = this.members.get(var1.getUUID());
         Engine.Member var5 = this.members.get(var2.getUUID());
         if (this.supportActive(var4) && var5 != null && var4.map.equals(var5.map)) {
            boolean var6 = var3.equals("地面侦测");
            if (var6 ? var4.team != var5.team : var4.team == var5.team) {
               if (var3.equals("信标部署")) {
                  this.career.count(var1, var4, "squadDeploy", 3, "pathfinder", true);
               } else if (!var6) {
                  this.career.count(var1, var4, "supplyRevive", 25, "quartermaster", true);
               }

               int var7 = this.matches.get(var4.map).rules.map.supportScore;
               var4.score += var7;
               var4.credits += var7;
               Network.send(
                  var1,
                  Map.of(
                     "type",
                     "bonus",
                     "label",
                     var3,
                     "points",
                     var7,
                     "detail",
                     var2.getScoreboardName(),
                     "icon",
                     SupportRules.feedbackIcon(var3),
                     "killFeed",
                     false
                  )
               );
            }
         }
      }
   }

   void supportContact(UUID var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1);
      Engine.Member var4 = this.members.get(var2.getUUID());
      if (this.supportActive(var3) && var4 != null && var3.map.equals(var4.map) && var3.team != var4.team) {
         Engine.Match var5 = this.matches.get(var3.map);
         String var6 = "传感器 · " + var2.getScoreboardName();
         var5.marks.removeIf(var2x -> var2x.team() == var3.team && var2x.name().equals(var6));
         var5.marks
            .add(new Spotting.Mark(var3.team, var2.getX(), var2.getZ(), System.currentTimeMillis() + 2000L, Spotting.Kind.ENEMY, var6, var2.getY() + 2.0));

         while (var5.marks.size() > 32) {
            var5.marks.removeFirst();
         }
      }
   }

   void supportGadgetDestroyed(ServerPlayer var1, String var2, int var3, Entity var4) {
      Engine.Member var5 = this.members.get(var1.getUUID());
      if (this.supportActive(var5) && var5.map.equals(var2) && var5.team != var3) {
         this.career.count(var1, var5, "tacticalGadget", 3, "destroyer", true);
         this.cosmeticKills.report(var4, var1, false, "摧毁设备");
      }
   }

   boolean knownBeacon(UUID var1) {
      return this.beacons.containsKey(var1);
   }

   boolean damageBeacon(Entity var1, DamageSource var2, float var3) {
      Engine.Beacon var4 = this.beacons.get(var1.getUUID());
      if (var4 != null && var1 instanceof DeployBeacon var5 && Float.isFinite(var3) && !(var3 <= 0.0F)) {
         ServerPlayer var6 = VehicleKillCompat.attacker(var2);
         if (var6 != null) {
            Engine.Member var7 = this.members.get(var6.getUUID());
            if (var7 == null || !var7.map.equals(var4.map) || !var7.alive || var7.downed) {
               return false;
            }

            if (var7.team == var4.team && !var6.getUUID().equals(var4.owner)) {
               return false;
            }
         }

         boolean var8 = PropVitals.damage(var5, var3);
         this.beaconHealth.put(var4.entity, var5.getHealth());
         if (var8) {
            if (var6 != null) {
               this.supportGadgetDestroyed(var6, var4.map, var4.team, var1);
            }

            this.removeBeacon(var4);
         }

         return true;
      }

      return false;
   }

   private void cleanBeacons() {
      for (Engine.Beacon var2 : new ArrayList<>(this.beacons.values())) {
         Engine.Member var3 = this.members.get(var2.owner);
         Engine.Match var4 = this.matches.get(var2.map);
         boolean var5 = this.chunkLoaded(var2);
         Entity var6 = var5 ? this.findEntity(var2.entity) : null;
         String var7 = SquadRules.beaconFate(
            var3 != null,
            var4 != null && var4.running,
            this.ticks >= var2.expires,
            var5,
            var6 != null,
            var6 != null && var6.isAlive(),
            var3 != null && SquadRules.same(var2.map, var2.team, var2.squad, var3.map, var3.team, var3.squad)
         );
         if (var7 != null) {
            this.removeBeacon(var2);
         }
      }
   }

   private boolean chunkLoaded(Engine.Beacon var1) {
      Engine.Match var2 = this.matches.get(var1.map);
      if (var2 != null && var2.rules.map.dimension != null) {
         ServerLevel var3 = this.level(var2.rules.map);
         if (var3 == null) {
            return false;
         } else {
            BlockPos var4 = var1.at();
            return var4 != null && var3.hasChunkAt(var4);
         }
      } else {
         return false;
      }
   }

   private boolean legalDynamic(Engine.Match var1, int var2, double var3, double var5, double var7) {
      return this.legalDynamic(var1, var2, "I", var3, var5, var7);
   }

   private boolean legalDynamic(Engine.Match var1, int var2, String var3, double var4, double var6, double var8) {
      Model.Arena var10 = var1.rules.map;
      return var6 >= (double)var10.minY && var6 <= (double)var10.maxY && var10.deploymentArea(var2, var3, var4, var8, var1.rules.stage, var1.rules.isStaged());
   }

   private boolean hot(Engine.Match var1, int var2, double var3, double var5, double var7) {
      return this.enemiesOf(var1.rules, var2)
         .stream()
         .anyMatch(var6 -> Math.pow(var6.x() - var3, 2.0) + Math.pow(var6.y() - var5, 2.0) + Math.pow(var6.z() - var7, 2.0) < 256.0);
   }

   private Deploy.Site safeSite(
      ServerPlayer var1, Engine.Match var2, int var3, String var4, String var5, String var6, double var7, double var9, double var11, float var13
   ) {
      ServerLevel var14 = this.level(var2.rules.map);

      for (int var18 : new int[]{0, 1, -1, 2, -2}) {
         for (double[] var22 : new double[][]{{2.0, 0.0}, {-2.0, 0.0}, {0.0, 2.0}, {0.0, -2.0}, {2.0, 2.0}, {-2.0, -2.0}}) {
            double var23 = Math.floor(var7 + var22[0]) + 0.5;
            double var25 = Math.floor(var9) + (double)var18;
            double var27 = Math.floor(var11 + var22[1]) + 0.5;
            BlockPos var29 = BlockPos.containing(var23, var25, var27);
            if (var14.hasChunkAt(var29)
               && this.legalDynamic(var2, var3, var23, var25, var27)
               && var14.getBlockState(var29.below()).isFaceSturdy(var14, var29.below(), Direction.UP)) {
               BlockState var30 = var14.getBlockState(var29.below());
               if (!var30.is(Blocks.MAGMA_BLOCK)
                  && !var30.is(Blocks.CAMPFIRE)
                  && !var30.is(Blocks.SOUL_CAMPFIRE)
                  && !var30.is(Blocks.CACTUS)
                  && !var14.getBlockState(var29).is(BlockTags.FIRE)
                  && var14.getFluidState(var29).isEmpty()
                  && var14.getFluidState(var29.above()).isEmpty()
                  && var14.noCollision(var1, new AABB(var23 - 0.3, var25, var27 - 0.3, var23 + 0.3, var25 + 1.8, var27 + 0.3))) {
                  return new Deploy.Site(var4, var5, var23, var25, var27, var13, "", false, var6);
               }
            }
         }
      }

      return null;
   }

   private void placeBeacon(ServerPlayer var1) {
      Engine.Member var2 = this.member(var1);
      Engine.Match var3 = this.matches.get(var2.map);
      ItemStack var4 = ItemStack.EMPTY;

      for (int var5 = 0; var5 < var1.getInventory().getContainerSize(); var5++) {
         if (ConquestEquipment.kind(var1.getInventory().getItem(var5)) == SupportRules.Kind.RADIO_BEACON) {
            var4 = var1.getInventory().getItem(var5);
            break;
         }
      }

      Model.require(var1.isCreative() || !var4.isEmpty(), "请先在配装中携带复活信标装备");
      Model.require(var3 != null && var3.running && var2.alive && !var2.downed && var2.squad > 0, "请加入小队并在存活时放置信标");
      Model.require(!var1.isPassenger() && var1.onGround() && var1.level() == this.level(var3.rules.map), "请下车并站在战区地面");
      Model.require(this.ticks >= var2.beaconAt, "信标冷却尚未结束");
      Model.require(!this.hot(var3, var2.team, var1.getX(), var1.getY(), var1.getZ()), "附近有敌军，不能放置信标");
      ServerLevel var20 = this.level(var3.rules.map);
      Vec3 var6 = var1.getEyePosition();
      BlockHitResult var7 = var20.clip(new ClipContext(var6, var6.add(var1.getLookAngle().scale(4.5)), Block.COLLIDER, Fluid.NONE, var1));
      Model.require(var7.getType() == Type.BLOCK && var7.getDirection() == Direction.UP, "请瞄准 4.5 格内地面的上表面放置信标");
      BlockPos var8 = var7.getBlockPos();
      Model.require(var20.hasChunkAt(var8) && var20.getBlockState(var8).isFaceSturdy(var20, var8, Direction.UP), "信标需要完整稳固的地面支撑");
      double var9 = (double)var8.getX() + 0.5;
      double var11 = var7.getLocation().y + 0.02;
      double var13 = (double)var8.getZ() + 0.5;
      Model.require(this.legalDynamic(var3, var2.team, var9, var11, var13) && !this.hot(var3, var2.team, var9, var11, var13), "落点不在可用战区内或附近有敌军");
      Model.require(var20.getFluidState(BlockPos.containing(var9, var11, var13)).isEmpty(), "信标不能放在水或其他流体中");
      Deploy.Site var15 = new Deploy.Site("", "", var9, var11, var13, var1.getYRot(), "", false, "BEACON");
      DeployBeacon var16 = new DeployBeacon(this.level(var3.rules.map), var15.x(), var15.y(), var15.z());
      var16.setNoGravity(true);
      var16.setItemSlot(EquipmentSlot.HEAD, ConquestEquipment.stack(SupportRules.Kind.RADIO_BEACON.key(), 1));
      Model.require(var20.noCollision(var16, var16.getBoundingBox()), "落点空间不足或被实体占用");
      var16.setCustomName(Component.literal(SquadRules.label(var2.squad) + " · 复活信标"));
      var16.setCustomNameVisible(false);
      var16.addTag("bfops_beacon");
      Engine.Beacon var17 = new Engine.Beacon(var16.getUUID(), var1.getUUID(), var2.map, var2.team, var2.squad, this.ticks + 12000L, var16.blockPosition());
      this.beacons.put(var17.entity, var17);
      if (!this.level(var3.rules.map).addFreshEntity(var16)) {
         this.beacons.remove(var17.entity);
         throw new IllegalArgumentException("信标放置失败");
      } else {
         for (Engine.Beacon var19 : new ArrayList<>(this.beacons.values())) {
            if (var19 != var17 && SquadRules.same(var19.map, var19.team, var19.squad, var2.map, var2.team, var2.squad)) {
               this.removeBeacon(var19);
            }
         }

         var2.beaconAt = this.ticks + 1200L;
         this.beaconUses.put(var17.entity, 6);
         if (!var1.isCreative()) {
            var4.shrink(1);
         }

         this.refills.used(var1, SupportRules.Kind.RADIO_BEACON, this.ticks);
         var1.getInventory().setChanged();
         var1.inventoryMenu.broadcastChanges();
      }
   }

   private List<Engine.DeployOption> deploymentOptions(ServerPlayer var1, Engine.Member var2, Engine.Match var3) {
      var var4 = new ArrayList();

      for (Deploy.Site var6 : Deploy.sites(var3.rules.map, var2.team, var3.rules.stage, var3.rules.isStaged(), Deploy.owners(var3.rules))) {
         var4.add(new Engine.DeployOption(var6, "", null));
      }

      for (ServerPlayer var17 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var7 = this.members.get(var17.getUUID());
         if (var17 != var1 && var7 != null && SquadRules.same(var2.map, var2.team, var2.squad, var7.map, var7.team, var7.squad)) {
            String var8 = "mate:" + var17.getUUID();
            String var9 = "队友 · " + var7.name;
            String var10 = SquadRules.teammateBlocker(var7.alive, var7.downed, var17.isAlive(), var17.isSpectator(), var17.isCreative(), var17.isPassenger());
            String var11 = !var10.isEmpty()
               ? var10
               : (
                  var17.level() != this.level(var3.rules.map)
                     ? "不在本战区"
                     : (
                        !this.legalDynamic(var3, var2.team, var17.getX(), var17.getY(), var17.getZ())
                           ? "超出活动区域"
                           : (
                              SquadRules.inCombat(var7.combatUntil, this.ticks)
                                 ? "队友刚刚交战，稍后可部署"
                                 : (this.hot(var3, var2.team, var17.getX(), var17.getY(), var17.getZ()) ? "附近有敌军" : "")
                           )
                     )
               );
            Deploy.Site var12 = var11.isEmpty()
               ? this.safeSite(var1, var3, var2.team, var8, var9, "TEAMMATE", var17.getX(), var17.getY(), var17.getZ(), var17.getYRot())
               : null;
            if (var12 == null) {
               if (var11.isEmpty()) {
                  var11 = "无安全落脚点";
               }

               var12 = new Deploy.Site(var8, var9, var17.getX(), var17.getY(), var17.getZ(), 0.0F, "", false, "TEAMMATE");
            }

            var4.add(new Engine.DeployOption(var12, var11, null));
         }
      }

      for (Engine.Beacon var18 : this.beacons.values()) {
         if (SquadRules.same(var2.map, var2.team, var2.squad, var18.map, var18.team, var18.squad)) {
            Entity var20 = this.findEntity(var18.entity);
            if (var20 != null && var20.isAlive() && this.ticks < var18.expires) {
               String var22 = "beacon:" + var18.entity;
               String var24 = "信标 · " + SquadRules.label(var2.squad) + " · 剩余 " + this.beaconUses.getOrDefault(var18.entity, 6) + " 次";
               String var26 = !this.legalDynamic(var3, var2.team, var20.getX(), var20.getY(), var20.getZ())
                  ? "不在当前活动区域"
                  : (this.hot(var3, var2.team, var20.getX(), var20.getY(), var20.getZ()) ? "附近有敌军" : "");
               Deploy.Site var28 = var26.isEmpty()
                  ? this.safeSite(var1, var3, var2.team, var22, var24, "BEACON", var20.getX(), var20.getY(), var20.getZ(), 0.0F)
                  : null;
               if (var28 == null) {
                  if (var26.isEmpty()) {
                     var26 = "无安全落脚点";
                  }

                  var28 = new Deploy.Site(var22, var24, var20.getX(), var20.getY(), var20.getZ(), 0.0F, "", false, "BEACON");
               }

               var4.add(new Engine.DeployOption(var28, var26, null));
            }
         }
      }

      for (UUID var19 : var3.vehicles) {
         if (var3.vehicleTeams.getOrDefault(var19, -1) == var2.team) {
            Entity var21 = this.findEntity(var19);
            if (var21 != null && var21.isAlive()) {
               int var23 = this.catalog.vehicleSeats.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(var21.getType()).toString(), 0);
               LivingEntity var25 = var21.getControllingPassenger();
               Engine.Member var27 = var25 instanceof ServerPlayer var29 ? this.members.get(var29.getUUID()) : null;
               String var30 = VehicleDeploy.boardingBlocker(
                  var2.map,
                  var2.team,
                  var2.squad,
                  var3.rules.map.teamWideVehicleDeploy,
                  var25 != null,
                  var27 == null ? null : var27.map,
                  var27 == null ? -1 : var27.team,
                  var27 == null ? 0 : var27.squad,
                  var27 != null && var27.alive && !var27.downed
               );
               String var31 = "登车 · " + var21.getName().getString() + " " + var21.getPassengers().size() + "/" + var23;
               String var13 = var23 <= var21.getPassengers().size()
                  ? "座位已满或座位数据不可用"
                  : (
                     var21.level() != this.level(var3.rules.map)
                        ? "不在本战区"
                        : (
                           !this.legalDynamic(
                                 var3,
                                 var2.team,
                                 String.valueOf(this.catalog.vehicleClass(BuiltInRegistries.ENTITY_TYPE.getKey(var21.getType()).toString())),
                                 var21.getX(),
                                 var21.getY(),
                                 var21.getZ()
                              )
                              ? "不在当前活动区域"
                              : (this.hot(var3, var2.team, var21.getX(), var21.getY(), var21.getZ()) ? "附近有敌军" : "")
                        )
                  );
               if (var13.isEmpty()) {
                  var13 = var30;
               }

               var4.add(
                  new Engine.DeployOption(
                     new Deploy.Site("vehicle:" + var19, var31, var21.getX(), var21.getY(), var21.getZ(), var21.getYRot(), "", false, "VEHICLE"), var13, var21
                  )
               );
            }
         }
      }

      return var4;
   }

   private void deploy(ServerPlayer var1, String var2, String var3, String var4, String var5, String var6, String var7, JsonObject var8) throws Exception {
      Engine.Member var9 = this.member(var1);
      Engine.Match var10 = this.matches.get(var9.map);
      Model.require(var10 != null && var10.running && var9.team >= 0, "请等待开局并选择阵营");
      Model.Arena var11 = var10.rules.map;
      Model.require(!var9.alive, "只能在部署阶段切换配装");
      Model.require(!var9.downed, "倒地状态下无法部署，等待队友救援或出血阵亡");
      Model.require(this.ticks >= var9.readyAt, "重生倒计时尚未结束");
      Model.require(this.ticks >= var9.entryUntil, "请等待入局简报结束");
      Model.require(FixedClasses.resolve(var2) != null, "兵种不属于参考端固定四类");
      Model.Kit var12 = var11.kits
         .stream()
         .filter(var1x -> var1x.id.equals(var2))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("配装未获地图授权：" + var2));
      var7 = var11.isTraining() && !var7.isEmpty() ? var7 : var12.selectMelee(var7, var9.team);
      String var13 = var11.isTraining() ? str(var8, "throwable", "") : var12.selectThrowable(str(var8, "throwable", ""), var9.team);
      var var14 = this.deploymentOptions(var1, var9, var10);
      Engine.DeployOption var15 = var14.stream()
         .filter(var1x -> var1x.site.id().equals(var3))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("部署目标已失效，请重新选择"));
      Model.require(var15.blocked.isEmpty(), var15.blocked);
      Entity var16 = var15.vehicle;
      String var17 = DeploymentRules.requestBlocker(var15.site, var4, var16 != null);
      Model.require(var17.isEmpty(), var17);
      var var18 = var14.stream().filter(var0 -> var0.blocked.isEmpty()).map(Engine.DeployOption::site).toList();
      Model.require(!var18.isEmpty(), "本方当前没有可用的部署点");
      Deploy.Site var19 = var18.stream()
         .filter(var1x -> var1x.id().equals(var3))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("此部署目标已不可用，请重新选择"));
      Model.Spawn var20 = var19.asSpawn(var9.team);
      Model.require(!SquadRules.dynamic(var19.kind()) || var4.isEmpty(), "队友、信标和登车部署不能同时刷新新载具");
      char var21 = 0;
      EntityType var22 = null;
      if (!var4.isEmpty()) {
         Model.require(var11.vehiclesFor(var9.team).contains(var4) && this.catalog.isVehicle(var4), "载具未获地图授权：" + var4);
         Model.require(this.catalog.deployableVehicle(var4), "该载具不能用于部署：" + var4);
         Model.require(this.ticks >= var9.vehicleAt, "载具冷却尚未结束（" + (var9.vehicleAt - this.ticks + 19L) / 20L + " 秒）");
         var10.vehicles.removeIf(var1x -> this.findEntity(var1x) == null);
         Model.require(this.teamVehicles(var10, var9.team) < var11.vehicleLimitFor(var9.team), "本方载具名额已满");
         var21 = this.catalog.vehicleClass(var4);
         var22 = (EntityType)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(var4));
         Model.require(var22 != null, "载具实体未注册：" + var4);
         Model.require(var19.allows(var21), var21 == 'A' ? var19.id() + " 不是飞机点位，无法放飞航空器" : var19.id() + " 不提供载具刷新");
      }

      var var23 = var18.stream().filter(var0 -> !SquadRules.dynamic(var0.kind())).map(var1x -> var1x.asSpawn(var9.team)).toList();
      if (!var4.isEmpty()) {
         var var24 = Deploy.sitesFor(var18, var21).stream().map(var1x -> var1x.asSpawn(var9.team)).toList();
         if (!var24.isEmpty()) {
            var23 = var24;
         }
      }

      String var63 = var16 != null
         ? String.valueOf(this.catalog.vehicleClass(BuiltInRegistries.ENTITY_TYPE.getKey(var16.getType()).toString()))
         : (var21 == 0 ? "I" : String.valueOf(var21));
      Model.require(var11.deploymentArea(var9.team, var63, var19.x(), var19.z(), var10.rules.stage, var10.rules.isStaged()), "部署目标不在当前装备的活动范围内");
      var23 = var23.stream().filter(var4x -> var11.deploymentArea(var9.team, var63, var4x.x, var4x.z, var10.rules.stage, var10.rules.isStaged())).toList();
      var var25 = this.enemiesOf(var10.rules, var9.team);
      Model.Spawn var26 = SquadRules.dynamic(var19.kind()) ? var20 : Tactics.chooseSpawn(var20, var23, var25);
      Deploy.Site var27 = var18.stream().filter(var1x -> var1x.id().equals(var26.id)).findFirst().orElse(var19);
      boolean var28 = !var27.id().equals(var19.id());
      if (var22 != null) {
         VehicleDeploy.Gate var29 = VehicleDeploy.Gate.of(var27.allows(var21), this.teamVehicles(var10, var9.team), var11.vehicleLimitFor(var9.team), true);
         Model.require(var29.success(), var29.blocker());
      }

      String var64 = var11.isTraining() && this.catalog.guns.contains(var5) ? var5 : var12.select(true, var5, var9.team);
      String var30 = var11.isTraining() && this.catalog.guns.contains(var6) ? var6 : var12.select(false, var6, var9.team);
      var9.issuedAmmo.clear();
      int var31Rounds = ammoStacks(var12, var64);
      var var31 = this.catalog.gunRounds(var1, var64, var31Rounds);
      applyAmmoType(var12, var64, var31);
      int var32Rounds = ammoStacks(var12, var30);
      var var32 = this.catalog.gunRounds(var1, var30, var32Rounds);
      applyAmmoType(var12, var30, var32);
      var9.issuedAmmo.put(var64, var31Rounds);
      var9.issuedAmmo.put(var30, var32Rounds);
      var var33 = selections(var8, "primaryFittings");
      var var34 = selections(var8, "secondaryFittings");
      var var35 = selections(var8, "vehicleFittings");
      int var36 = this.progress.level(var1.getUUID());
      var33.entrySet().removeIf(var3x -> !InfantryAttachments.blocker(var12, var64, (String)var3x.getValue(), var36).isEmpty());
      var34.entrySet().removeIf(var3x -> !InfantryAttachments.blocker(var12, var30, (String)var3x.getValue(), var36).isEmpty());
      InfantryAttachments.authorize(var12, var64, var33, var36, this.catalog.gunFittings.getOrDefault(var64, List.of()));
      InfantryAttachments.authorize(var12, var30, var34, var36, this.catalog.gunFittings.getOrDefault(var30, List.of()));
      if (!var31.isEmpty()) {
         Fittings.applyGun((ItemStack)var31.getFirst(), var33, var1.registryAccess());
      } else {
         Model.require(var33.isEmpty(), "没有主武器可改装");
      }

      if (!var32.isEmpty()) {
         Fittings.applyGun((ItemStack)var32.getFirst(), var34, var1.registryAccess());
      } else {
         Model.require(var34.isEmpty(), "没有副武器可改装");
      }

      if (var22 != null) {
         Entity var37 = var22.create(this.level(var11));
         Model.require(var37 != null, "载具数据不可用");
         var var38 = Fittings.vehicleOptions(var37);
         Fittings.validate(var38, var35);
         VehicleArmament var39 = armamentFor(var11, var37);
         Fittings.restrict(var38, var35, var39);
      } else {
         Model.require(var35.isEmpty(), "未选择载具");
      }

      ItemStack var65 = var31.isEmpty() ? ItemStack.EMPTY : (ItemStack)var31.getFirst();
      ItemStack var66 = var32.isEmpty() ? ItemStack.EMPTY : (ItemStack)var32.getFirst();
      var var67 = var31.size() <= 1 ? List.of() : var31.subList(1, var31.size());
      var var40 = var32.size() <= 1 ? List.of() : var32.subList(1, var32.size());
      ItemStack var41 = ItemStack.EMPTY;
      if (var7 != null && !var7.isEmpty()) {
         if (this.catalog.isGun(var7)) {
            var var42 = this.catalog.gun(var1, var7, 0);
            if (!var42.isEmpty()) {
               var41 = (ItemStack)var42.getFirst();
            }
         } else {
            var41 = KitItems.resolve(var7, 1);
         }

         Model.require(!var41.isEmpty(), "所选近身武器无法生成，请确认服务端已加载对应刀包：" + var7);
      }

      var var68 = new ArrayList();
      LinkedHashMap var43 = new LinkedHashMap();

      for (KitGear.Entry var45 : var12.gear().deduplicatedItems()) {
         var43.put(var45.id, var45);
      }

      for (String var72 : var12.issueOrder()) {
         if (var72.equals("main_ammo")) {
            var68.addAll(var67);
         } else if (var72.equals("side_ammo")) {
            var68.addAll(var40);
         }
      }

      if (var11.isTraining() && var8.has("trainingItems")) {
         for (JsonElement var73 : var8.getAsJsonArray("trainingItems")) {
            if (var68.size() >= 24) {
               break;
            }

            ItemStack var46 = KitItems.resolve(var73.getAsString(), 1);
            if (!var46.isEmpty()) {
               var68.add(var46);
            }
         }
      }

      ItemStack var71 = ItemStack.EMPTY;
      if (var13 != null && !var13.isEmpty()) {
         var71 = KitItems.resolve(var13, var12.countFor(var13));
      }

      var var74 = this.tacticalStacks(var1, var12, var11, var8, "tactical1");
      var var75 = this.tacticalStacks(var1, var12, var11, var8, "tactical2");
      ItemStack var47 = var74.isEmpty() ? ItemStack.EMPTY : (ItemStack)var74.getFirst();
      ItemStack var48 = var75.isEmpty() ? ItemStack.EMPTY : (ItemStack)var75.getFirst();
      if (var74.size() > 1) {
         var68.addAll(var74.subList(1, var74.size()));
      }

      if (var75.size() > 1) {
         var68.addAll(var75.subList(1, var75.size()));
      }

      int var49 = (var65.isEmpty() ? 0 : 1)
         + (var66.isEmpty() ? 0 : 1)
         + (var41.isEmpty() ? 0 : 1)
         + (var71.isEmpty() ? 0 : 1)
         + (var47.isEmpty() ? 0 : 1)
         + (var48.isEmpty() ? 0 : 1)
         + var68.size();
      Model.require(var49 <= 35, "配装超过背包容量");
      ServerLevel var50 = this.level(var11);
      var50.getChunkAt(BlockPos.containing(var27.x(), var27.y(), var27.z()));
      Model.Spawn var51 = var27.asSpawn(var9.team);
      if (var16 == null && !SquadRules.dynamic(var27.kind())) {
         String var52 = var22 == null ? "I" : String.valueOf(var21);
         var51 = AreaDeploy.choose(
            var50,
            var1,
            var27,
            var11,
            var9.team,
            var52,
            var22 == null ? 0.6 : Math.max(0.6, (double)var22.getWidth()),
            var22 == null ? 1.8 : Math.max(1.8, (double)var22.getHeight()),
            var4x -> this.legalDynamic(var10, var9.team, var52, var4x.x, var4x.y, var4x.z) && !this.hot(var10, var9.team, var4x.x, var4x.y, var4x.z)
         );
         var27 = new Deploy.Site(var27.id(), var27.label(), var51.x, var51.y, var51.z, var51.yaw, var27.allows(), var27.spawn(), var27.kind());
      }

      Model.require(
         var16 != null || var50.noCollision(var1, var1.getBoundingBox().move(var51.x - var1.getX(), var51.y - var1.getY(), var51.z - var1.getZ())),
         "部署点空间被方块占用，请调整位置"
      );
      if (var16 != null) {
         double var76 = var1.getX();
         double var54 = var1.getY();
         double var56 = var1.getZ();
         float var58 = var1.getYRot();
         float var59 = var1.getXRot();
         this.teleport(var1, var50, var51);
         if (!var1.startRiding(var16, true)) {
            var1.teleportTo(var50, var76, var54, var56, var58, var59);
            var1.setDeltaMovement(Vec3.ZERO);
            var1.fallDistance = 0.0F;
            throw new IllegalArgumentException("载具拒绝登车，请选择其他座位或部署点");
         }
      }

      this.releaseElite(var1.getUUID(), var9);
      var1.closeContainer();
      var1.getInventory().clearContent();
      var1.getInventory().setItem(0, var65);
      var1.getInventory().setItem(1, var66);
      var1.getInventory().setItem(2, var41);
      int var77 = 3;
      if (!var71.isEmpty()) {
         var9.issuedSlots.put(str(var8, "throwable", ""), var77);
         var1.getInventory().setItem(var77++, var71);
      }

      if (!var47.isEmpty()) {
         var9.issuedSlots.put(str(var8, "tactical1", ""), var77);
         var1.getInventory().setItem(var77++, var47);
      }

      if (!var48.isEmpty()) {
         var9.issuedSlots.put(str(var8, "tactical2", ""), var77);
         var1.getInventory().setItem(var77++, var48);
      }

      var1.getInventory().setItem(var77++, new ItemStack(Items.COOKED_BEEF, 16));
      if (var11.isTraining() && SupportItems.DEPLOY_TERMINAL.get() != null) {
         var1.getInventory().setItem(var77++, new ItemStack((ItemLike)SupportItems.DEPLOY_TERMINAL.get()));
      }

      for (ItemStack var81 : (java.lang.Iterable<ItemStack>)(var68)) {
         giveToBackpack(var1, var81);
      }

      CuriosSupport.clear(var1);
      KitItems.wear(var12, var1);
      if (var16 == null) {
         this.teleport(var1, var50, var51);
      }

      var1.setGameMode(GameType.ADVENTURE);
      var1.setHealth(var1.getMaxHealth());
      var1.clearFire();
      var1.getFoodData().setFoodLevel(20);
      var1.getFoodData().setSaturation(var11.defaultSaturation ? 20.0F : 5.0F);
      var9.alive = true;
      var9.downed = false;
      var9.kit = var12.id;
      var9.lastKit = var12.id;
      var9.lastPrimary = var64;
      var9.lastMelee = str(var8, "melee", "");
      var9.lastSecondary = var30;
      var9.tactical1 = var47.isEmpty() ? "" : str(var8, "tactical1", "");
      var9.throwable = var71.isEmpty() ? "" : str(var8, "throwable", "");
      var9.issuedTemplates.clear();
      var9.ammoTemplates.clear();
      var var80 = var9.issuedTemplates;
      String var82 = str(var8, "throwable", "");
      if (!var82.isEmpty() && !var71.isEmpty()) {
         var80.put(var82, var71.copyWithCount(1));
      }

      if (!var47.isEmpty()) {
         String var55 = str(var8, "tactical1", "");
         if (!var55.isEmpty()) {
            var80.put(var55, var47.copyWithCount(1));
         }
      }

      if (!var48.isEmpty()) {
         String var83 = str(var8, "tactical2", "");
         if (!var83.isEmpty()) {
            var80.put(var83, var48.copyWithCount(1));
         }
      }

      var9.ammoTemplates.clear();

      for (String var95 : new String[]{var64, var30, var9.tactical1, var9.tactical2}) {
         if (var95 != null && !var95.isEmpty() && this.catalog.isGun(var95)) {
            try {
               var var98 = this.catalog.gun(var1, var95, 1);
               if (var98.size() > 1) {
                  var9.ammoTemplates.put(var95, ((ItemStack)var98.get(1)).copyWithCount(1));
               }
            } catch (RuntimeException | ReflectiveOperationException var60) {
            }
         }
      }

      var9.gunAmmo.clear();

      for (String var96 : new String[]{var64, var30, var9.tactical1, var9.tactical2}) {
         if (var96 != null && !var96.isEmpty() && SuperbAmmo.isGunId(var96)) {
            String var99 = ammoChoice(var12, var96, var8);
            if (!var99.isEmpty()) {
               var9.gunAmmo.put(var96, var99);
            }
         }
      }

      var9.tactical2 = var48.isEmpty() ? "" : str(var8, "tactical2", "");
      var9.downedAt = -1L;
      var9.giveUpAt = -1L;
      var9.giveUpHeldAt = -100L;
      var9.readyAt = 0L;
      var9.hasDeployTarget = true;
      var9.deployX = var51.x;
      var9.deployY = var51.y + (double)var1.getEyeHeight();
      var9.deployZ = var51.z;
      var9.deployYaw = var51.yaw;
      var9.deployPitch = 0.0F;
      downPose(var1, false);
      var9.outside = 0;
      var9.warnedSecond = -1;
      var9.killCredited = false;
      var9.reviving = null;
      this.clearGlow(var1, var9);
      var1.inventoryMenu.broadcastChanges();
      var1.setGlowingTag(false);
      if (var22 != null) {
         String var86 = this.spawnVehicle(var1, var50, var11, var10, var9, var22, var27, var35);
         if (var86 != null) {
            var9.alive = false;
            var9.readyAt = this.ticks;
            var1.stopRiding();
            var1.setGameMode(GameType.SPECTATOR);
            var1.getInventory().clearContent();
            CuriosSupport.clear(var1);
            this.state(var1, var9.map, true, var86);
            var1.sendSystemMessage(Component.literal("§e" + var86 + " —— 已返回部署界面，未扣任何费用"));
            throw new IllegalArgumentException(var86);
         }
      }

      if (var27.id().startsWith("mate:")) {
         ServerPlayer var87 = this.server.getPlayerList().getPlayer(UUID.fromString(var27.id().substring(5)));
         if (var87 != null) {
            Engine.Member var91 = this.members.get(var87.getUUID());
            if (var91 != null) {
               this.career.count(var87, var91, "squadDeploy", 3, "pathfinder", true);
            }
         }
      }

      if (var27.id().startsWith("beacon:")) {
         UUID var88 = UUID.fromString(var27.id().substring(7));
         Engine.Beacon var92 = this.beacons.get(var88);
         if (var92 != null) {
            int var94 = SupportRules.consumeBeacon(this.beaconUses.getOrDefault(var88, 6), true);
            this.beaconUses.put(var88, var94);
            ServerPlayer var97 = this.server.getPlayerList().getPlayer(var92.owner);
            if (var97 != null) {
               this.supportCredit(var97, var1, "信标部署");
            }

            if (var94 <= 0) {
               this.removeBeacon(var92);
            } else {
               Entity var100 = this.findEntity(var92.entity);
               if (var100 != null) {
                  var100.setCustomName(Component.literal(SquadRules.label(var92.squad) + " · 信标 " + var94 + " 次"));
               }
            }
         }
      }

      var9.deploySequence++;
      this.refills.issued(var1);
      var9.deathVehicleClass = "";
      var1.displayClientMessage(
         Component.literal(
            "§b部署完成 §7· " + DeploymentText.label(var27.id(), var27.kind(), var27.label()) + " · " + var12.id + (var28 ? " · 原目标受压制，已转移安全位置" : "")
         ),
         true
      );
   }

   private String spawnVehicle(
      ServerPlayer var1,
      ServerLevel var2,
      Model.Arena var3,
      Engine.Match var4,
      Engine.Member var5,
      EntityType<?> var6,
      Deploy.Site var7,
      Map<String, String> var8
   ) {
      if (!DeploymentRules.canSpawnNew(var7)) {
         return "当前部署目标不能生成新载具";
      } else {
         Entity var9;
         try {
            var9 = var6.create(var2);
         } catch (Exception var15) {
            return "载具生成失败，未扣分";
         }

         if (var9 != null && this.catalog.isVehicleEntity(var9)) {
            var9.moveTo(var7.x(), var7.y(), var7.z(), var7.yaw(), 0.0F);
            if (!AreaDeploy.footprint(AreaDeploy.shape(var3, var7.id()), var9.getBoundingBox())) {
               return "载具超出部署范围，请扩大范围或选择其他部署点";
            } else if (!Model.contains(var3.boundary, var9.getX(), var9.getZ())) {
               return "载具刷新点在地图边界外";
            } else if (!var2.noBlockCollision(var9, var9.getBoundingBox())) {
               return "刷新点空间不足以放下载具";
            } else {
               try {
                  Fittings.applyVehicle(var9, var8);
               } catch (IllegalArgumentException | ReflectiveOperationException var14) {
                  var9.discard();
                  return "载具弹种应用失败：" + var14.getMessage();
               }

               VehicleArmament var10 = armamentFor(var3, var9);

               try {
                  Fittings.applyVehicleAttachments(var9, var10);
               } catch (IllegalArgumentException | ReflectiveOperationException var13) {
                  BattlefieldOps.LOG.warn("Cannot apply configured vehicle attachments", var13);
               }

               var9.addTag("bfops_vehicle");
               UUID var11 = var9.getUUID();
               this.vehicles.add(var11);
               var4.vehicles.add(var11);
               var4.vehicleTeams.put(var11, var5.team);
               if (!var2.addFreshEntity(var9)) {
                  this.vehicles.remove(var11);
                  var4.vehicles.remove(var11);
                  var4.vehicleTeams.remove(var11);
                  return "载具生成失败，未扣分";
               } else {
                  String var12 = VehicleEquip.equip(var9, VehicleEquip.kindOf(var9), var10);
                  var1.sendSystemMessage(Component.literal("载具配装：" + var12));
                  if (!var1.startRiding(var9, true)) {
                     var9.discard();
                     this.vehicles.remove(var11);
                     var4.vehicles.remove(var11);
                     var4.vehicleTeams.remove(var11);
                     return "未能坐进载具，载具已回收，未扣冷却";
                  } else {
                     var5.vehicleAt = this.ticks + (long)var3.vehicleCooldown * 20L;
                     return null;
                  }
               }
            }
         } else {
            return "载具生成失败，未扣分";
         }
      }
   }

   private int teamVehicles(Engine.Match var1, int var2) {
      return (int)var1.vehicles.stream().filter(var2x -> var1.vehicleTeams.getOrDefault(var2x, -1) == var2).filter(var1x -> {
         Entity var2x = this.findEntity(var1x);
         return var2x != null && var2x.isAlive();
      }).count();
   }

   private Entity findEntity(UUID var1) {
      for (ServerLevel var3 : this.server.getAllLevels()) {
         Entity var4 = var3.getEntity(var1);
         if (var4 != null) {
            return var4;
         }
      }

      return null;
   }

   public boolean foodHealing(Player var1) {
      Engine.Member var2 = this.members.get(var1.getUUID());
      if (var2 == null) {
         return true;
      } else {
         Engine.Match var3 = this.matches.get(var2.map);
         return !var2.downed && (var3 == null || !var3.running || !var2.alive || var3.rules.map.foodRegeneration);
      }
   }

   public boolean foodRule(Player var1, boolean var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (var3 == null) {
         return var2;
      } else {
         Engine.Match var4 = this.matches.get(var3.map);
         return var4 != null && var4.running && var3.alive ? !var3.downed && var4.rules.map.foodRegeneration : var2;
      }
   }

   private void tickNutrition() {
      for (Entry var2 : this.members.entrySet()) {
         Engine.Member var3 = (Engine.Member)var2.getValue();
         if (var3.alive && !var3.downed) {
            Engine.Match var4 = this.matches.get(var3.map);
            if (var4 != null && var4.running && var4.rules.map.defaultSaturation) {
               ServerPlayer var5 = this.server.getPlayerList().getPlayer((UUID)var2.getKey());
               if (var5 != null) {
                  var5.getFoodData().setFoodLevel(20);
                  var5.getFoodData().setSaturation(20.0F);
                  var5.getFoodData().setExhaustion(0.0F);
               }
            }
         }
      }
   }

   public boolean isDowned(Player var1) {
      Engine.Member var2 = this.members.get(var1.getUUID());
      return var2 != null && var2.downed;
   }

   public boolean blockDamage(ServerPlayer var1, Entity var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (var3 != null && !var3.alive) {
         return true;
      } else if (var3 != null && var3.downed) {
         return true;
      } else {
         if (var2 instanceof ServerPlayer var4) {
            Engine.Member var5 = this.members.get(var4.getUUID());
            if (var3 == null != (var5 == null)) {
               return true;
            }

            if (var3 != null && var5 != null) {
               if (var3.map.equals(var5.map) && var5.alive && !var5.downed) {
                  Engine.Match var6 = this.matches.get(var3.map);
                  if (var6 != null && var6.rules.map.isTraining()) {
                     return false;
                  }

                  return var6 == null || var3.team == var5.team && !var6.rules.map.friendlyFire;
               }

               return true;
            }
         }

         return false;
      }
   }

   private void tickDowned() {
      for (ServerPlayer var2 : (java.lang.Iterable<ServerPlayer>)(new ArrayList(this.server.getPlayerList().getPlayers()))) {
         Engine.Member var3 = this.members.get(var2.getUUID());
         if (var3 != null && var3.downed) {
            Engine.Match var4 = this.matches.get(var3.map);
            if (var4 != null) {
               int var5 = Medic.reviveTicks(var4.rules.map.reviveSeconds);
               ServerPlayer var6 = null;

               for (ServerPlayer var8 : this.server.getPlayerList().getPlayers()) {
                  if (var8 != var2) {
                     Engine.Member var9 = this.members.get(var8.getUUID());
                     if (var9 != null
                        && var9.alive
                        && !var9.downed
                        && var9.map.equals(var3.map)
                        && var9.team == var3.team
                        && var2.getUUID().equals(var9.reviving)) {
                        if (!Medic.continuingRescue(this.ticks, var9.reviveHeldAt, this.canRescue(var8, var2))) {
                           var9.reviving = null;
                        } else if (var6 == null || FixedClasses.resolve(var9.kit) == FixedClasses.Role.SUPPORT) {
                           var6 = var8;
                        }
                     }
                  }
               }

               downPose(var2, true);
               int var10 = var6 != null && FixedClasses.resolve(this.members.get(var6.getUUID()).kit) == FixedClasses.Role.SUPPORT
                  ? var4.rules.map.supportReviveSpeed
                  : 1;
               var3.reviveProgress = Math.max(0, Math.min(var5, var3.reviveProgress + (var6 == null ? -2 : var10)));
               if (this.ticks - var3.giveUpHeldAt > 6L) {
                  var3.giveUpAt = -1L;
               }

               if (this.ticks % 4L == 0L) {
                  Network.send(
                     var2,
                     Map.of(
                        "type",
                        "medic",
                        "downLeft",
                        Medic.secondsLeft(var3.bleedOutAt, this.ticks),
                        "reviveProgress",
                        var3.reviveProgress,
                        "giveUpProgress",
                        var3.giveUpAt < 0L ? 0L : Math.min(20L, this.ticks - var3.giveUpAt),
                        "respawnRemaining",
                        Math.max(0L, (var3.readyAt - this.ticks + 19L) / 20L)
                     )
                  );
               }

               if (var3.reviveProgress >= var5) {
                  this.revive(var2, var3, var6);
               } else if (Medic.expired(var3.bleedOutAt, this.ticks)
                  || var4.rules.map.allowGiveUp && var3.giveUpAt >= 0L && this.ticks - var3.giveUpAt >= 20L && this.ticks - var3.giveUpHeldAt <= 6L) {
                  ServerPlayer var11 = var3.downedBy == null ? null : this.server.getPlayerList().getPlayer(var3.downedBy);
                  var3.reviveProgress = 0;
                  this.clearReviver(var2.getUUID());
                  downPose(var2, false);
                  this.death(var2, var11);
               }
            }
         }
      }
   }

   public boolean tryDown(ServerPlayer var1, ServerPlayer var2, float var3) {
      Engine.Member var4 = this.members.get(var1.getUUID());
      if (var4 == null || !var4.alive || var4.downed) {
         return false;
      } else if (var4.bot) {
         return false;
      } else {
         Engine.Match var5 = this.matches.get(var4.map);
         if (var5 != null && var5.rules.map.downEnabled) {
            if (var1.isAlive() && Medic.lethal(var1.getHealth(), var3)) {
               if (var1.getVehicle() != null) {
                  return false;
               } else {
                  Engine.Member var6 = var2 == null ? null : this.members.get(var2.getUUID());
                  var4.downed = true;
                  var4.downedAt = this.ticks;
                  var4.readyAt = this.ticks + (long)var5.rules.map.respawnSeconds * 20L;
                  var4.giveUpAt = -1L;
                  var4.giveUpHeldAt = -100L;
                  var1.stopUsingItem();
                  var4.bleedOutAt = this.ticks + Medic.bleedOutTicks(var5.rules.map.downSeconds);
                  var4.reviveProgress = 0;
                  var4.reviving = null;
                  var4.downedByName = var6 == null ? "" : var6.name;
                  var4.downedBy = var6 == null ? null : var2.getUUID();
                  this.support.down(var1);
                  var1.setHealth(1.0F);
                  if (var4.claimKill()) {
                     this.creditKill(var1, var2);
                  }

                  downPose(var1, true);
                  var1.displayClientMessage(Component.literal("§c你倒下了！§f等待队友救援，" + var5.rules.map.downSeconds + " 秒后出血阵亡"), true);
                  if (var2 != null) {
                     var2.displayClientMessage(Component.literal("§e" + var4.name + " 被你击倒（可被救起）"), true);
                  }

                  for (ServerPlayer var8 : this.server.getPlayerList().getPlayers()) {
                     Engine.Member var9 = this.members.get(var8.getUUID());
                     if (var9 != null && var9.team == var4.team && var9 != var4 && var9.map.equals(var4.map) && var9.alive) {
                        var8.displayClientMessage(Component.literal("§e队友 §f" + var4.name + " §e倒地，空手右键救援"), false);
                     }
                  }

                  this.state(var1, var4.map, false, "");
                  return true;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static void downPose(ServerPlayer var0, boolean var1) {
      if (var0 != null) {
         AttributeInstance var2 = var0.getAttribute(Attributes.MOVEMENT_SPEED);
         if (var2 != null) {
            if (var1 && !var2.hasModifier(CRAWL_SPEED)) {
               var2.addTransientModifier(new AttributeModifier(CRAWL_SPEED, -0.75, Operation.ADD_MULTIPLIED_TOTAL));
            }

            if (!var1) {
               var2.removeModifier(CRAWL_SPEED);
            }
         }

         CrawlPose.apply(var0, var1);
      }
   }

   private static boolean rescueRole(Engine.Member var0, Engine.Member var1) {
      return var0 != null
         && var1 != null
         && var0.alive
         && !var0.downed
         && var0.team == var1.team
         && var0.map.equals(var1.map)
         && (FixedClasses.resolve(var0.kit) == FixedClasses.Role.SUPPORT || var0.squad > 0 && var0.squad == var1.squad);
   }

   public boolean callRescue(ServerPlayer var1) {
      Engine.Member var2 = this.members.get(var1.getUUID());
      if (var2 != null && var2.downed && var2.alive && this.supportActive(var2) && this.ticks - var2.rescueCallAt >= 160L) {
         var2.rescueCallAt = this.ticks;
         var2.rescueCallUntil = this.ticks + 160L;
         FrontVoicePlayRequest var3 = FrontVoicePlayRequest.cue(
            VoiceCue.DOWNED_HELP, CampType.CN, FrontVoicePlaybackMode.ENTITY, var1.getId(), var1.position(), 1.0F, 1.0F, 96.0F
         );

         for (ServerPlayer var5 : this.server.getPlayerList().getPlayers()) {
            if (var5 != var1 && rescueRole(this.members.get(var5.getUUID()), var2) && var5.level() == var1.level() && var5.distanceToSqr(var1) <= 9216.0) {
               VoiceNetwork.sendToPlayer(var5, new ClientboundPlayAudioPayload(var3));
               this.state(var5, var2.map, false, "");
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean canRescue(ServerPlayer var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      Engine.Member var4 = this.members.get(var2.getUUID());
      if (!rescueRole(var3, var4)) {
         return false;
      } else if (var3 != null
         && var4 != null
         && var3.alive
         && !var3.downed
         && var4.downed
         && var4.alive
         && var3.team >= 0
         && var3.team == var4.team
         && var3.map.equals(var4.map)
         && var1.getMainHandItem().isEmpty()
         && var1.level() == var2.level()) {
         Engine.Match var5 = this.matches.get(var3.map);
         if (var5 != null && var5.running) {
            double var6 = (double)var5.rules.map.reviveDistance;
            Vec3 var8 = var1.getEyePosition();
            Vec3 var9 = var8.add(var1.getLookAngle().scale(var6));
            Optional var10 = var2.getBoundingBox().inflate(0.65, 0.3, 0.65).clip(var8, var9);
            if (!var10.isEmpty() && !(var8.distanceToSqr(var2.getBoundingBox().getCenter()) > (var6 + 0.5) * (var6 + 0.5))) {
               BlockHitResult var11 = var1.level().clip(new ClipContext(var8, (Vec3)var10.get(), Block.COLLIDER, Fluid.NONE, var1));
               return var11.getType() == Type.MISS;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean reviveStart(ServerPlayer var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      Engine.Member var4 = this.members.get(var2.getUUID());
      if (var3 == null || var4 == null) {
         return false;
      } else if (!var3.alive || var3.downed) {
         return false;
      } else if (!var4.downed) {
         return false;
      } else if (!var3.map.equals(var4.map) || var3.team != var4.team) {
         return false;
      } else if (!this.canRescue(var1, var2)) {
         return false;
      } else {
         var3.reviving = var2.getUUID();
         var3.reviveHeldAt = this.ticks;
         var1.displayClientMessage(
            Component.literal(
               "§b救援 "
                  + var4.name
                  + " "
                  + (int)(100.0 * Medic.fraction(var4.reviveProgress, Medic.reviveTicks(this.matches.get(var4.map).rules.map.reviveSeconds)))
                  + "% · 保持空手并按住右键"
            ),
            true
         );
         return true;
      }
   }

   private void clearReviver(UUID var1) {
      for (Engine.Member var3 : this.members.values()) {
         if (var1.equals(var3.reviving)) {
            var3.reviving = null;
         }
      }
   }

   private void revive(ServerPlayer var1, Engine.Member var2, ServerPlayer var3) {
      Engine.Match var4 = this.matches.get(var2.map);
      var2.downed = false;
      var2.reviveProgress = 0;
      var2.bleedOutAt = 0L;
      var2.downedAt = -1L;
      var2.readyAt = 0L;
      var2.giveUpAt = -1L;
      var2.giveUpHeldAt = -100L;
      var2.killCredited = false;
      this.clearReviver(var1.getUUID());
      downPose(var1, false);
      float var5 = Medic.revivedHealth(var4 == null ? 0.0F : (float)var4.rules.map.reviveHealth, var1.getMaxHealth());
      var1.setHealth(var5);
      var1.displayClientMessage(Component.literal("§a你被救起了"), true);
      if (var3 != null) {
         Engine.Member var6 = this.members.get(var3.getUUID());
         if (var6 != null && var4 != null) {
            var6.revives++;
            this.career.revive(var3, var6);
            this.career.count(var3, var6, "fieldRevive", 5, "field_medic", true);
            this.career.count(var3, var6, "revive", 20, "combat_medic", false);
            this.career.count(var3, var6, "supplyRevive", 25, "quartermaster", true);
            var6.score = var6.score + var4.rules.map.assistScore;
            var6.credits = var6.credits + var4.rules.map.assistScore;
         }

         var3.displayClientMessage(Component.literal("§a已救起 §f" + var2.name), true);
         award(var3, "救援", var4 == null ? 0 : var4.rules.map.assistScore, var2.name, "assist");
      }

      this.state(var1, var2.map, false, "");
   }

   public void death(ServerPlayer var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (var3 != null) {
         Engine.Match var4 = this.matches.get(var3.map);
         if (var4 != null) {
            var1.setHealth(var1.getMaxHealth());
            if (var3.alive) {
               this.releaseElite(var1.getUUID(), var3);
               String var5 = var2 != null && !var2.getUUID().equals(var1.getUUID()) ? var2.getScoreboardName() : (var3.downed ? var3.downedByName : "");
               var3.alive = false;
               var3.observerAt = this.ticks + 80L;
               var3.downed = false;
               downPose(var1, false);
               this.clearReviver(var1.getUUID());
               var3.deaths++;
               Network.send(var1, Map.of("type", "deathStats", "killer", var5));
               var3.readyAt = var3.downedAt >= 0L
                  ? var3.downedAt + (long)var4.rules.map.respawnSeconds * 20L
                  : this.ticks + (long)var4.rules.map.respawnSeconds * 20L;
               var3.downedAt = -1L;
               var3.giveUpAt = -1L;
               var3.giveUpHeldAt = -100L;
               var3.outside = 0;
               Entity var6 = var1.getRootVehicle();
               var3.deathVehicleClass = var6 == var1
                  ? ""
                  : String.valueOf(this.catalog.vehicleClass(BuiltInRegistries.ENTITY_TYPE.getKey(var6.getType()).toString()));
               var1.stopRiding();
               var1.setGameMode(GameType.SPECTATOR);
               var1.clearFire();
               var1.getInventory().clearContent();
               var4.rules.death(var3.team);
               Engine.Member var7 = var3.claimKill() ? this.creditKill(var1, var2) : null;
               if (var7 != null) {
                  var1.sendSystemMessage(Component.literal("§c被 §f" + var7.name + " §c击杀"));
               } else {
                  var1.sendSystemMessage(Component.literal("§7你阵亡了"));
               }

               this.clearGlow(var1, var3);
               var1.sendSystemMessage(Component.literal("§7重生倒计时 §f" + Math.max(0L, (var3.readyAt - this.ticks + 19L) / 20L) + "§7 秒，请选择部署点"));
               this.state(var1, var3.map, true, "已阵亡，等待重新部署");
            }
         }
      }
   }

   private Honours.Evidence honourEvidence(ServerPlayer var1, ServerPlayer var2, Engine.Match var3, int var4, boolean var5) {
      Model.Arena var6 = var3.rules.map;
      double var7 = var2.getX() - var1.getX();
      double var9 = var2.getZ() - var1.getZ();
      double var11 = Math.sqrt(var7 * var7 + var9 * var9);
      double var13 = var2.getY() - var1.getY();
      double var15 = Math.sqrt(var2.getDeltaMovement().x * var2.getDeltaMovement().x + var2.getDeltaMovement().z * var2.getDeltaMovement().z);
      boolean var17 = var1.getVehicle() != null;
      boolean var18 = var2.getVehicle() == null;
      boolean var19 = false;

      try {
         var19 = var1.getLastDamageSource() != null && var1.getLastDamageSource().is(DamageTypeTags.IS_EXPLOSION);
      } catch (RuntimeException var31) {
      }

      boolean var20 = false;
      if (var11 > 0.1) {
         double var21 = Math.toRadians((double)var1.getYRot());
         double var23 = -Math.sin(var21);
         double var25 = Math.cos(var21);
         double var27 = var7 / var11;
         double var29 = var9 / var11;
         var20 = var23 * var27 + var25 * var29 < -0.5;
      }

      boolean var32 = false;
      long var22 = System.currentTimeMillis();

      for (Spotting.Mark var34 : var3.marks) {
         if (var34.expiresAt() > var22) {
            double var26 = var34.x() - var1.getX();
            double var28 = var34.z() - var1.getZ();
            if (var26 * var26 + var28 * var28 <= 36.0) {
               var32 = true;
               break;
            }
         }
      }

      boolean var33 = false;

      for (Spotting.Mark var36 : var3.marks) {
         if (var36.expiresAt() > var22) {
            double var37 = var36.x() - var2.getX();
            double var38 = var36.z() - var2.getZ();
            if (var37 * var37 + var38 * var38 <= 36.0) {
               var33 = true;
               break;
            }
         }
      }

      return new Honours.Evidence(
         var11,
         var13,
         var5,
         var19,
         var4,
         var3.streak.getOrDefault(var1.getUUID(), 0) >= 3,
         var32,
         var33,
         var20,
         var18,
         var17,
         var15,
         (double)(var1.getHealth() / Math.max(1.0F, var1.getMaxHealth())),
         (double)(var2.getHealth() / Math.max(1.0F, var2.getMaxHealth())),
         var2.hasEffect(MobEffects.BLINDNESS)
      );
   }

   private Engine.Member creditKill(ServerPlayer var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (var3 == null) {
         return null;
      } else {
         Engine.Match var4 = this.matches.get(var3.map);
         if (var4 == null) {
            return null;
         } else {
            Engine.Member var5 = var2 == null ? null : this.members.get(var2.getUUID());
            if (var5 != null
               && var5 != var3
               && var5.alive
               && var4.running
               && var5.team >= 0
               && var3.team >= 0
               && var5.map.equals(var3.map)
               && var5.team != var3.team) {
               if (!var4.rules.acceptsKillCredit()) {
                  return null;
               }

               var4.rules.kill(var5.team, var3.team);
               var5.kills++;
               var5.score = var5.score + var4.rules.map.killScore;
               var5.credits = var5.credits + var4.rules.map.killScore;
               var2.sendSystemMessage(Component.literal("§a击杀 §f" + var3.name + " §7(+" + var4.rules.map.killScore + ")"));
               long var6 = System.currentTimeMillis();
               Model.Arena var8 = var4.rules.map;
               long var9 = (long)var8.comboSeconds * 1000L;
               long var11 = (long)var8.assistSeconds * 1000L;
               int var13 = Awards.streak(var4.streak.getOrDefault(var2.getUUID(), 0), var4.lastKillAt.getOrDefault(var2.getUUID(), 0L), var6, var9);
               var4.streak.put(var2.getUUID(), var13);
               var4.lastKillAt.put(var2.getUUID(), var6);
               int var14 = var8.killScore;
               var5.score = var5.score + (var13 > 1 ? var8.comboBonus : 0);
               var5.credits = var5.credits + (var13 > 1 ? var8.comboBonus : 0);
               boolean var15 = this.cosmeticKills
                  .headshot(var1, var2, Awards.headshot(Awards.lastHitY(var4.hits.get(var1.getUUID())), var1.getY(), var1.getY() + (double)var1.getBbHeight()));
               if (var5.score >= 0
                  && this.members
                     .values()
                     .stream()
                     .filter(var1x -> var1x.map.equals(var3.map) && var1x.team == var3.team && var1x != var3)
                     .noneMatch(var1x -> var1x.score > var3.score)) {
                  this.career.repeated(var2, var5, "top");
               }

               if (Awards.revenge(var4.lastKiller.get(var2.getUUID()), var1.getUUID())) {
                  this.career.repeated(var2, var5, "avenge");
               }

               if (var1.getRootVehicle() != var1
                  ? this.catalog.vehicleClass(BuiltInRegistries.ENTITY_TYPE.getKey(var1.getRootVehicle().getType()).toString()) == 'A'
                  : "A".equals(var3.deathVehicleClass)) {
                  this.career.repeated(var2, var5, "flight_dispatcher");
               }

               for (int var16 = 0; var16 < var8.points.size(); var16++) {
                  Model.Point var17 = var8.points.get(var16);
                  if (var4.rules.active(var16) && ObjectiveZone.contains(var17, var2.getX(), var2.getY(), var2.getZ())) {
                     boolean var18 = ObjectiveZone.contains(var17, var1.getX(), var1.getY(), var1.getZ())
                        || this.server
                           .getPlayerList()
                           .getPlayers()
                           .stream()
                           .anyMatch(
                              var3x -> {
                                 Engine.Member var4x = this.members.get(var3x.getUUID());
                                 return var4x != null
                                    && var4x.alive
                                    && var4x.map.equals(var5.map)
                                    && var4x.team >= 0
                                    && var4x.team != var5.team
                                    && ObjectiveZone.contains(var17, var3x.getX(), var3x.getY(), var3x.getZ());
                              }
                           );
                     if (var18) {
                        this.career.count(var2, var5, "garrison", 3, "garrison", true);
                        break;
                     }
                  }
               }

               this.career.kill(var2, var5, this.honourEvidence(var1, var2, var4, var13, var15), var1);
               awardKill(var2, Awards.streakLabel(var13), var14, var3.name, var15 ? "headshot" : "kill", var13, var1.getId(), (double)var8.comboSeconds);
               if (var13 > 1 && var8.comboBonus > 0) {
                  Network.send(
                     var2,
                     Map.of(
                        "type",
                        "bonus",
                        "label",
                        "连杀加成",
                        "points",
                        var8.comboBonus,
                        "icon",
                        "streak",
                        "bonusId",
                        "KILL_COMBO",
                        "combo",
                        var13,
                        "victimId",
                        var1.getId(),
                        "detail",
                        var3.name
                     )
                  );
               }

               if (var15) {
                  var5.score = var5.score + var8.headshotBonus;
                  var5.credits = var5.credits + var8.headshotBonus;
                  award(var2, "爆头加成", var8.headshotBonus, var3.name, "headshot");
               }

               for (String var23 : Honours.judge(this.honourEvidence(var1, var2, var4, var13, var15))) {
                  if (!"headshot".equals(var23)) {
                     int var25 = Honours.points(var23);
                     if (var25 > 0) {
                        var5.score += var25;
                        var5.credits += var25;
                     }

                     award(var2, Honours.name(var23), var25, var3.name, Honours.icon(var23));
                  }
               }

               if (Awards.revenge(var4.lastKiller.get(var2.getUUID()), var1.getUUID())) {
                  var5.score = var5.score + var8.revengeBonus;
                  var5.credits = var5.credits + var8.revengeBonus;
                  award(var2, "复仇", var8.revengeBonus, var3.name, "revenge");
               }

               for (UUID var26 : Awards.assists(var4.hits.get(var1.getUUID()), var2.getUUID(), var6, var11)) {
                  Engine.Member var19 = this.members.get(var26);
                  if (var19 != null && var19.team != var3.team && var19.map.equals(var3.map)) {
                     var19.assists++;
                     var19.score = var19.score + var8.assistScore;
                     var19.credits = var19.credits + var8.assistScore;
                     ServerPlayer var20 = this.server.getPlayerList().getPlayer(var26);
                     if (var20 != null) {
                        award(var20, "助攻", var8.assistScore, var3.name, "assist");
                     }
                  }
               }

               var4.hits.remove(var1.getUUID());
               var4.lastKiller.put(var1.getUUID(), var2.getUUID());
            }

            this.broadcastKill(var5, var3, var4);
            return var5;
         }
      }
   }

   private void broadcastKill(Engine.Member var1, Engine.Member var2, Engine.Match var3) {
      String var4 = var1 == null ? "§7" : (var1.team == 0 ? "§9" : "§c");
      String var5 = var1 == null ? "§7阵亡" : var4 + var1.name + " §7→ " + (var2.team == 0 ? "§9" : "§c") + var2.name;

      for (ServerPlayer var7 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var8 = this.members.get(var7.getUUID());
         if (var8 != null && var8.team >= 0 && var8.map.equals(var3.rules.map.id)) {
            var7.sendSystemMessage(Component.literal(var5));
         }
      }
   }

   private void spawnBots(Engine.Match var1, ServerLevel var2) {
      Model.Arena var3 = var1.rules.map;

      for (int var4 = 0; var4 < 2; var4++) {
         var var5 = this.legalSpawns(var1.rules, var4);
         if (!var5.isEmpty()) {
            for (int var6 = 0; var6 < var3.botsPerTeam; var6++) {
               this.spawnBotPlayer(var1, var2, var4, (var4 == 0 ? "蓝" : "红") + "人机" + (var6 + 1), (Model.Spawn)var5.get(var6 % var5.size()));
            }
         }
      }
   }

   private void spawnBotPlayer(Engine.Match var1, ServerLevel var2, int var3, String var4, Model.Spawn var5) {
      ServerPlayer var6;
      try {
         var6 = new ServerPlayer(this.server, var2, new GameProfile(UUID.randomUUID(), var4), ClientInformation.createDefault());
      } catch (Exception var11) {
         BattlefieldOps.LOG.warn("Cannot create bot player", var11);
         return;
      }

      try {
         var6.connection = new ServerGamePacketListenerImpl(
            this.server, new Engine.BotConnection(), var6, CommonListenerCookie.createInitial(var6.getGameProfile(), false)
         );
         var6.moveTo(var5.x, var5.y, var5.z, var5.yaw, 0.0F);
         if (!var2.addFreshEntity(var6)) {
            BattlefieldOps.LOG.warn("Cannot add bot player to the map dimension");
            return;
         }

         Engine.Member var7 = new Engine.Member(var1.rules.map.id, var4, var3);
         var7.bot = true;
         this.members.put(var6.getUUID(), var7);
         var1.bots.add(var6.getUUID());
         this.deployBot(var6, var7, var1.rules, var2, var5);
         this.broadcastPlayerInfo(var6, true);
      } catch (Throwable var10) {
         BattlefieldOps.LOG.warn("Cannot spawn bot player " + var4, var10);

         try {
            var6.discard();
         } catch (Throwable var9) {
         }
      }
   }

   private void deployBot(ServerPlayer var1, Engine.Member var2, Rules var3, ServerLevel var4, Model.Spawn var5) {
      var2.alive = true;
      var2.downed = false;
      var2.killCredited = false;
      var2.reviving = null;
      var2.outside = 0;
      var1.stopRiding();
      var1.setGameMode(GameType.SURVIVAL);
      var1.getInventory().clearContent();
      var1.setHealth(var1.getMaxHealth());
      var1.getFoodData().setFoodLevel(20);
      var1.teleportTo(var4, var5.x, var5.y, var5.z, var5.yaw, 0.0F);
      var1.fallDistance = 0.0F;
      this.board.apply(var1, var2.map, var2.team, var3.map.friendlyFire, var3.map.hideEnemyNametags);
   }

   private void broadcastPlayerInfo(ServerPlayer var1, boolean var2) {
      try {
         if (var2) {
            ClientboundPlayerInfoUpdatePacket var3 = new ClientboundPlayerInfoUpdatePacket(Action.ADD_PLAYER, var1);

            for (ServerPlayer var5 : this.server.getPlayerList().getPlayers()) {
               var5.connection.send(var3);
            }
         } else {
            ClientboundPlayerInfoRemovePacket var7 = new ClientboundPlayerInfoRemovePacket(List.of(var1.getUUID()));

            for (ServerPlayer var9 : this.server.getPlayerList().getPlayers()) {
               var9.connection.send(var7);
            }
         }
      } catch (LinkageError | RuntimeException var6) {
         BattlefieldOps.LOG.debug("Cannot broadcast bot player info", var6);
      }
   }

   private void removeBots(Engine.Match var1) {
      for (UUID var3 : var1.bots) {
         Entity var4 = this.findEntity(var3);
         if (var4 instanceof ServerPlayer var5) {
            this.broadcastPlayerInfo(var5, false);
            var5.serverLevel().removePlayerImmediately(var5, RemovalReason.DISCARDED);
         } else if (var4 != null) {
            var4.discard();
         }

         this.members.remove(var3);
      }

      var1.bots.clear();
      var1.botsSpawned = false;
   }

   private void respawnBots(Engine.Match var1, ServerLevel var2) {
      if (!var1.bots.isEmpty()) {
         Model.Arena var3 = var1.rules.map;

         for (UUID var5 : new ArrayList<>(var1.bots)) {
            Engine.Member var6 = this.members.get(var5);
            if (var6 != null) {
               ServerPlayer var8 = this.findEntity(var5) instanceof ServerPlayer var9 ? var9 : null;
               if (var6.alive) {
                  if (var8 == null || !var8.isAlive()) {
                     var6.alive = false;
                     var6.readyAt = this.ticks + (long)Math.max(1, var3.respawnSeconds) * 20L;
                  }
               } else if (this.ticks >= var6.readyAt) {
                  if (var8 == null) {
                     var var10 = this.legalSpawns(var1.rules, var6.team);
                     if (!var10.isEmpty()) {
                        this.spawnBotPlayer(var1, var2, var6.team, var6.name, (Model.Spawn)var10.get(var6.deaths % var10.size()));
                     }
                  } else {
                     var var11 = this.legalSpawns(var1.rules, var6.team);
                     if (!var11.isEmpty()) {
                        this.deployBot(var8, var6, var1.rules, var2, (Model.Spawn)var11.get(var6.deaths % var11.size()));
                     }
                  }
               }
            }
         }
      }
   }

   private void tickBotsMovement() {
      for (Engine.Match var2 : this.matches.values()) {
         if (!var2.bots.isEmpty()) {
            ServerLevel var3 = this.levelOrNull(var2.rules.map);
            if (var3 != null) {
               for (UUID var5 : var2.bots) {
                  Engine.Member var6 = this.members.get(var5);
                  if (var6 != null && var6.alive && !var6.downed) {
                     Entity var7 = this.findEntity(var5);
                     if (var7 instanceof ServerPlayer) {
                        ServerPlayer var8 = (ServerPlayer)var7;
                        if (var8.isAlive()) {
                           Model.Point var9 = this.nearestActivePoint(var2, var8);
                           if (var9 != null) {
                              double var10 = var9.x - var8.getX();
                              double var12 = var9.z - var8.getZ();
                              double var14 = Math.hypot(var10, var12);
                              if (var14 > 1.5) {
                                 var8.setYRot((float)Math.toDegrees(Math.atan2(-var10, var12)));
                                 var8.setYHeadRot(var8.getYRot());
                                 double var16 = 0.2;
                                 var8.move(MoverType.SELF, new Vec3(var10 / var14 * var16, 0.0, var12 / var14 * var16));
                              }
                           }

                           if (this.ticks >= var6.botNextAttackAt) {
                              LivingEntity var18 = this.nearestEnemy(var2, var6, var8);
                              if (var18 != null && var8.distanceToSqr(var18) <= 12.25) {
                                 var18.hurt(var3.damageSources().playerAttack(var8), 5.0F);
                                 var6.botNextAttackAt = this.ticks + 20L;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private Model.Point nearestActivePoint(Engine.Match var1, LivingEntity var2) {
      if (var1.rules.map.isDeathmatch()) {
         Engine.Member var11 = this.members.get(var2.getUUID());
         LivingEntity var12 = var11 == null ? null : this.nearestEnemy(var1, var11, var2);
         if (var12 == null) {
            return null;
         } else {
            Model.Point var5 = new Model.Point();
            var5.x = var12.getX();
            var5.y = var12.getY();
            var5.z = var12.getZ();
            return var5;
         }
      } else {
         Model.Point var3 = null;
         double var4 = Double.MAX_VALUE;
         var var6 = var1.rules.map.points;

         for (int var7 = 0; var7 < var6.size(); var7++) {
            if (var1.rules.active(var7)) {
               Model.Point var8 = (Model.Point)var6.get(var7);
               double var9 = var2.distanceToSqr(var8.x, var8.y, var8.z);
               if (var9 < var4) {
                  var4 = var9;
                  var3 = var8;
               }
            }
         }

         return var3;
      }
   }

   private LivingEntity nearestEnemy(Engine.Match var1, Engine.Member var2, LivingEntity var3) {
      Object var4 = null;
      double var5 = Double.MAX_VALUE;

      for (ServerPlayer var8 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var9 = this.members.get(var8.getUUID());
         if (var9 != null && var9.map.equals(var2.map) && var9.team != var2.team && var9.alive && var8.isAlive()) {
            double var10 = var3.distanceToSqr(var8);
            if (var10 < var5) {
               var5 = var10;
               var4 = var8;
            }
         }
      }

      for (UUID var15 : var1.bots) {
         if (!var15.equals(var3.getUUID())) {
            Engine.Member var16 = this.members.get(var15);
            if (var16 != null && var16.team != var2.team) {
               Entity var17 = this.findEntity(var15);
               if (var17 instanceof LivingEntity) {
                  LivingEntity var11 = (LivingEntity)var17;
                  if (var11.isAlive()) {
                     double var12 = var3.distanceToSqr(var11);
                     if (var12 < var5) {
                        var5 = var12;
                        var4 = var11;
                     }
                  }
               }
            }
         }
      }

      return (LivingEntity)var4;
   }

   private boolean stepTerrainBakes() {
      if (this.bakes.isEmpty()) {
         return false;
      } else {
         boolean var1 = false;

         for (Entry var3 : new ArrayList<>(this.bakes.entrySet())) {
            String var4 = (String)var3.getKey();
            TerrainBake var5 = (TerrainBake)var3.getValue();
            Model.Arena var6 = this.maps.get(var4);
            if (var6 == null) {
               this.bakes.remove(var4);
               this.terrainWaiters.remove(var4);
            } else {
               ServerLevel var7 = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(var6.dimension)));
               boolean var8 = var7 != null && var5.step(var7);
               if (var5.abandoned()) {
                  this.bakes.remove(var4);

                  for (ServerPlayer var13 : this.takeWaiters(var4)) {
                     Network.send(var13, Map.of("type", "terrain", "map", var4, "state", "FAILED"));
                  }
               } else if (var8) {
                  this.bakes.remove(var4);
                  TerrainMap var9 = var5.result();
                  this.terrainMaps.put(var4, var9);
                  TerrainBake.store(this.folder, var9);
                  var1 = true;

                  for (ServerPlayer var11 : this.takeWaiters(var4)) {
                     sendTerrain(var11, var4, var9);
                  }
               }
            }
         }

         return var1;
      }
   }

   private Set<ServerPlayer> takeWaiters(String var1) {
      var var2 = this.terrainWaiters.remove(var1);
      if (var2 != null && !var2.isEmpty()) {
         LinkedHashSet var3 = new LinkedHashSet();

         for (UUID var5 : (java.lang.Iterable<UUID>)(var2)) {
            ServerPlayer var6 = this.server.getPlayerList().getPlayer(var5);
            if (var6 != null) {
               var3.add(var6);
            }
         }

         return var3;
      } else {
         return Set.of();
      }
   }

   private static void sendTerrain(ServerPlayer var0, String var1, TerrainMap var2) {
      try {
         sendTerrainUnsafe(var0, var1, var2);
      } catch (RuntimeException var4) {
         BattlefieldOps.LOG.warn("Cannot send terrain for " + var1 + " to " + var0.getName().getString(), var4);
      }
   }

   private static void sendTerrainUnsafe(ServerPlayer var0, String var1, TerrainMap var2) {
      LinkedHashMap var3 = new LinkedHashMap();
      var3.put("type", "terrain");
      var3.put("map", var1);
      var3.put("state", "READY");
      var3.put("signature", var2.signature);
      var3.put("data", var2.encode());
      Network.send(var0, var3);
   }

   private static void award(ServerPlayer var0, String var1, int var2) {
      award(var0, var1, var2, "", "kill");
   }

   private static void awardKill(ServerPlayer var0, String var1, int var2, String var3, String var4, int var5, int var6, double var7) {
      if (var0 != null) {
         try {
            LinkedHashMap var9 = new LinkedHashMap();
            var9.put("type", "bonus");
            var9.put("label", var1);
            var9.put("points", var2);
            var9.put("detail", var3 == null ? "" : var3);
            var9.put("icon", var4 == null ? "kill" : var4);
            var9.put("elimination", true);
            var9.put("combo", var5);
            var9.put("victimId", var6);
            var9.put("comboSeconds", var7);
            if (var0.level().getEntity(var6) != null) {
               var9.put("distance", var0.distanceTo(var0.level().getEntity(var6)));
            }

            var9.put("bonusId", FeedbackTypes.bonusId("", var4));
            Network.send(var0, var9);
            BattlefieldOps.LOG.debug("awardKill -> {}: +{} {} ({}) combo={}", new Object[]{var0.getScoreboardName(), var2, var1, var4, var5});
         } catch (RuntimeException var10) {
            BattlefieldOps.LOG.debug("Cannot send kill award", var10);
         }
      }
   }

   private static void award(ServerPlayer var0, String var1, int var2, String var3, String var4) {
      if (var0 != null && (var2 > 0 || var3 != null && !var3.isEmpty())) {
         try {
            LinkedHashMap var5 = new LinkedHashMap();
            var5.put("type", "bonus");
            var5.put("label", var1);
            var5.put("points", var2);
            var5.put("detail", var3 == null ? "" : var3);
            var5.put("icon", var4 == null ? "kill" : var4);
            if ("远距离击杀".equals(var1)) {
               ServerPlayer var6 = var0.getServer().getPlayerList().getPlayerByName(var3);
               if (var6 != null) {
                  var5.put("extra", Long.toString((long)Math.round(var0.distanceTo(var6))));
               }
            }

            Network.send(var0, var5);
            BattlefieldOps.LOG.debug("award -> {}: +{} {} ({})", new Object[]{var0.getScoreboardName(), var2, var1, var4});
         } catch (RuntimeException var7) {
            BattlefieldOps.LOG.debug("Cannot send bonus feed entry", var7);
         }
      }
   }

   void vehicleDestroyed(ServerPlayer var1, Entity var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (this.supportActive(var3) && var3.alive && !var3.downed) {
         Engine.Match var4 = this.matches.get(var3.map);
         Integer var5 = var4.vehicleTeams.get(var2.getUUID());
         if (var5 != null && var5 != var3.team && var4.vehicles.contains(var2.getUUID()) && var2.level() == var1.level()) {
            String var6 = BuiltInRegistries.ENTITY_TYPE.getKey(var2.getType()).toString();
            boolean var7 = this.catalog.isAircraft(var6);
            boolean var8 = "TANK".equals(this.catalog.vehicleTypes.get(var6));
            boolean var9 = var1.getRootVehicle() != var1
               && this.catalog.isAircraft(BuiltInRegistries.ENTITY_TYPE.getKey(var1.getRootVehicle().getType()).toString());
            this.career.destroyVehicle(var1, var3, var2, var7, var8, var9);
            if (this.finalHonours.mineKill(var1, var2)) {
               this.career.repeated(var1, var3, "minelayer");
            }
         }
      }
   }

   void vehicleMount(ServerPlayer var1, Entity var2, boolean var3) {
      Engine.Member var4 = this.members.get(var1.getUUID());
      if (this.supportActive(var4) && var4.alive && !var4.downed) {
         Engine.Match var5 = this.matches.get(var4.map);
         if (var5.vehicleTeams.getOrDefault(var2.getUUID(), -1) == var4.team) {
            if (this.catalog.isAircraft(BuiltInRegistries.ENTITY_TYPE.getKey(var2.getType()).toString())) {
               this.career.mount(var1, var4, var2, var3);
            }
         }
      }
   }

   Rules rushRules(String var1) {
      Engine.Match var2 = this.matches.get(var1);
      return var2 != null && var2.running && var2.rules.map.isRush() ? var2.rules : null;
   }

   void rushPlant(ServerPlayer var1, Engine.Member var2) {
      this.career.rushObjective(var2, true);
      int var3 = this.matches.get(var2.map).rules.map.supportScore;
      var2.score += var3;
      var2.credits += var3;
      Network.send(var1, Map.of("type", "bonus", "label", "炸点安放", "points", var3, "bonusId", "RUSH_BOMB_PLANTED", "icon", "capture"));
   }

   void rushAward(ServerPlayer var1, Engine.Member var2, String var3) {
      boolean var4 = var3.equals("bomb_technician");
      if (var4) {
         this.career.rushObjective(var2, false);
      } else {
         var2.captures++;
      }

      int var5 = this.matches.get(var2.map).rules.map.captureScore;
      var2.score += var5;
      var2.credits += var5;
      Network.send(
         var1,
         Map.of(
            "type",
            "bonus",
            "label",
            var4 ? "炸点拆除" : "目标摧毁",
            "points",
            var5,
            "bonusId",
            var4 ? "RUSH_BOMB_DEFUSED" : "RUSH_OBJECTIVE_DESTROYED",
            "icon",
            "capture"
         )
      );
      this.career.repeated(var1, var2, var3);
   }

   void atmosphereHit(Entity var1) {
      this.atmosphere.hit(var1);
   }

   void atmosphereTick(Entity var1) {
      this.atmosphere.tick(var1, this.ticks);
   }

   String gunCategory(String var1) {
      String var2 = this.catalog.gunTypes.getOrDefault(var1, "rifle");
      return Set.of("smg", "sniper", "rpg").contains(var2) ? var2 : "rifle";
   }

   void suppression(ServerPlayer var1, ServerPlayer var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (this.supportActive(var3)) {
         if (this.career.increment(var3, "life:nearMiss") % 35 == 0) {
            this.career.count(var1, var3, "fireSupport", 5, "fire_support", true);
            int var4 = this.matches.get(var3.map).rules.map.supportScore;
            var3.score += var4;
            var3.credits += var4;
            Network.send(
               var1,
               Map.of("type", "bonus", "label", "火力压制", "points", var4, "bonusId", "FIRE_SUPPRESSION", "icon", "support", "detail", var2.getScoreboardName())
            );
         }
      }
   }

   boolean friendlyHull(ServerPlayer var1, Entity var2) {
      Engine.Member var3 = this.members.get(var1.getUUID());
      if (this.supportActive(var3) && var3.alive && !var3.downed) {
         Engine.Match var4 = this.matches.get(var3.map);
         return var4.vehicleTeams.getOrDefault(var2.getUUID(), -1) == var3.team && var4.vehicles.contains(var2.getUUID()) && var2.level() == var1.level();
      } else {
         return false;
      }
   }

   boolean repairAward(ServerPlayer var1, Entity var2, String var3) {
      Engine.Member var4 = this.members.get(var1.getUUID());
      if (var4 != null && var4.map.equals(var3) && this.friendlyHull(var1, var2)) {
         this.career.count(var1, var4, "repair", 25, "mechanic", true);
         int var5 = this.matches.get(var3).rules.map.supportScore;
         var4.score += var5;
         var4.credits += var5;
         Network.send(
            var1,
            Map.of("type", "bonus", "label", "维修载具", "points", var5, "bonusId", "REPAIR_VEHICLE", "icon", "repair", "detail", var2.getDisplayName().getString())
         );
         return true;
      } else {
         return false;
      }
   }

   void vehicleDamage(ServerPlayer var1, Entity var2, float var3) {
      Engine.Member var4 = this.members.get(var1.getUUID());
      if (this.supportActive(var4) && var4.alive && !var4.downed && Float.isFinite(var3) && !(var3 <= 0.0F)) {
         Engine.Match var5 = this.matches.get(var4.map);
         Integer var6 = var5.vehicleTeams.get(var2.getUUID());
         if (var6 != null && var6 != var4.team && var2.level() == var1.level()) {
            this.career.vehicleDamage(var1, var4, var3);
         }
      }
   }

   ServerPlayer recentAttacker(ServerPlayer var1) {
      ServerPlayer var2 = this.recentAttacker.get(var1.getUUID());
      Long var3 = this.recentAttackerAt.get(var1.getUUID());
      if (var2 != null && var3 != null) {
         if (var1.level().getGameTime() - var3 > 300L) {
            return null;
         } else if (var2 != var1 && var2.isAlive()) {
            return this.members.containsKey(var2.getUUID()) ? var2 : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

  /** True when the player was hit within the given number of ticks. */
  boolean hurtRecently(java.util.UUID id, int window) {
    Integer at = this.damageTicks.get(id);
    if (at == null) return false;
    // The server keeps a jiffy counter that needs no per-tick bookkeeping.
    for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
      if (player.getUUID().equals(id)) return player.tickCount - at <= window;
    }
    return false;
  }

   void noteDamage(ServerPlayer var1, ServerPlayer var2, DamageSource var3) {
    if (var1 != null && var1.getServer() != null) this.damageTicks.put(var1.getUUID(), var1.tickCount);
      if (this.members.containsKey(var1.getUUID())) {
         this.career.damage(var1, var2, var3);
      }
   }

   void gunEvidence(ServerPlayer var1, Entity var2, String var3) {
      this.career.hit(var1, var2, var3, this.catalog.gunTypes.getOrDefault(var3, ""));
   }

   void gunReload(ServerPlayer var1) {
      this.career.reload(var1);
   }

   public void hitNotice(ServerPlayer var1, ServerPlayer var2, float var3, double var4, boolean var6) {
      if (var1 != null && var2 != null && !(var3 <= 0.0F)) {
         if (var1 != var2) {
            this.recentAttacker.put(var2.getUUID(), var1);
            this.recentAttackerAt.put(var2.getUUID(), var2.level().getGameTime());
         }

         Engine.Member var7 = this.members.get(var1.getUUID());
         Engine.Member var8 = this.members.get(var2.getUUID());
         if (var7 != null && var8 != null) {
            if (var7.alive && var7.map.equals(var8.map)) {
               if (var7.team != var8.team) {
                  var7.combatUntil = this.ticks + 140L;
                  var8.combatUntil = this.ticks + 140L;
                  Engine.Match var9 = this.matches.get(var7.map);
                  if (var9 != null) {
                     long var10 = System.currentTimeMillis();
                     long var12 = (long)var9.rules.map.assistSeconds * 1000L;
                     UUID var14 = var2.getUUID();
                     var9.hits.put(var14, Awards.record(var9.hits.get(var14), new Awards.Hit(var1.getUUID(), var3, var10, var4)));
                     var9.hits.replaceAll((var4x, var5) -> Awards.prune((List<Awards.Hit>)var5, var10, var12));
                  }

                  try {
                     Network.send(var1, Map.of("type", "hit", "damage", var3, "nativeMarker", var6, "victimId", var2.getId()));
                  } catch (RuntimeException var15) {
                     BattlefieldOps.LOG.debug("Cannot send hit marker", var15);
                  }
               }
            }
         }
      }
   }

   private void mark(ServerPlayer var1, JsonObject var2) throws Exception {
      Engine.Member var3 = this.member(var1);
      Engine.Match var4 = this.matches.get(var3.map);
      Model.require(var4 != null && var4.running && var3.alive && var1.isAlive() && var3.team >= 0, "当前无法标记");
      long var5 = System.currentTimeMillis();
      if (var5 - var3.lastMarkAt >= 450L) {
         var3.lastMarkAt = var5;
         Vec3 var7 = var1.getEyePosition();
         Vec3 var8 = var7.add(var1.getLookAngle().scale(150.0));
         BlockHitResult var9 = var1.level().clip(new ClipContext(var7, var8, Block.COLLIDER, Fluid.NONE, var1));
         double var10 = var7.distanceToSqr(var9.getLocation());
         Entity var12 = null;
         double var13 = var10;

         for (Entity var16 : var1.serverLevel()
            .getEntities(var1, var1.getBoundingBox().expandTowards(var1.getLookAngle().scale(150.0)).inflate(2.0), var0 -> var0.isAlive() && var0.isPickable())) {
            if (var16 != var1.getRootVehicle() && Model.contains(var4.rules.map.boundary, var16.getX(), var16.getZ())) {
               Engine.Member var17 = var16 instanceof ServerPlayer var18 ? this.members.get(var18.getUUID()) : null;
               if ((!(var16 instanceof ServerPlayer) || var17 != null && var17.alive && var17.map.equals(var3.map))
                  && (var16 instanceof ServerPlayer || this.catalog.vehicles.contains(BuiltInRegistries.ENTITY_TYPE.getKey(var16.getType()).toString()))) {
                  Optional var28 = var16.getBoundingBox().inflate(0.45).clip(var7, var8);
                  if (var28.isPresent() && var7.distanceToSqr((Vec3)var28.get()) <= var13) {
                     var13 = var7.distanceToSqr((Vec3)var28.get());
                     var12 = var16;
                  }
               }
            }
         }

         Spotting.Kind var25 = Spotting.Kind.POSITION;
         String var26 = "观察此处";
         String var27 = "";
         String var29 = "";
         Vec3 var19 = var9.getLocation();
         if (var12 != null) {
            var19 = var12.position().add(0.0, (double)var12.getBbHeight() + 0.35, 0.0);
            var29 = var12.getUUID().toString();
            if (var12 instanceof ServerPlayer var20) {
               Engine.Member var21 = this.members.get(var20.getUUID());
               var27 = var21.kit;
               var26 = var20.getName().getString();
               if (var21.team != var3.team) {
                  var25 = Spotting.Kind.ENEMY;
               } else if (FixedClasses.resolve(var21.kit) == FixedClasses.Role.SUPPORT) {
                  var25 = var1.isShiftKeyDown() ? Spotting.Kind.REQUEST_HEAL : Spotting.Kind.REQUEST_AMMO;
                  var26 = var25 == Spotting.Kind.REQUEST_HEAL ? "请求医疗" : "请求弹药";
                  var20.displayClientMessage(Component.literal(var1.getName().getString() + " · " + var26), true);
                  var19 = var1.position().add(0.0, 2.3, 0.0);
                  var29 = var1.getUUID().toString();
                  var27 = var3.kit;
               } else {
                  var25 = Spotting.Kind.MATE;
               }
            } else {
               var25 = Spotting.Kind.VEHICLE;
               var26 = "载具";

               for (Entity var22 : var12.getPassengers()) {
                  if (var22 instanceof ServerPlayer var23) {
                     Engine.Member var24 = this.members.get(var23.getUUID());
                     if (var24 != null && var24.map.equals(var3.map) && var24.team != var3.team) {
                        var25 = Spotting.Kind.ENEMY;
                        break;
                     }
                  }
               }
            }
         } else {
            for (Model.Point var34 : var4.rules.map.points) {
               if (ObjectiveZone.horizontal(var34, var19.x, var19.z)) {
                  var25 = Spotting.Kind.OBJECTIVE;
                  var26 = "据点 " + var34.id;
                  var19 = new Vec3(var34.x, var34.y + 2.0, var34.z);
                  break;
               }
            }
         }

         if (!var4.rules.map.boundary.isEmpty() && !Model.contains(var4.rules.map.boundary, var19.x, var19.z)) {
            Network.send(var1, Map.of("type", "supportSound", "cue", "mark_failed"));
            var1.displayClientMessage(Component.literal("该位置在战场边界外，无法标记"), true);
         } else {
            if (!var29.isEmpty()) {
               final int markTeam = var3.team;
               final var markTarget = var29;
               var4.marks.removeIf(var2x -> var2x.team() == markTeam && var2x.target().equals(markTarget));
            }

            var4.marks
               .add(
                  new Spotting.Mark(
                     var3.team, var19.x, var19.z, var5 + (long)var4.rules.map.markSeconds * 1000L, var25, var26, var19.y, var29, var27, var25.id()
                  )
               );

            while (var4.marks.size() > 32) {
               var4.marks.removeFirst();
            }

            if (var25 == Spotting.Kind.ENEMY && var5 - var3.rewardedMarks.getOrDefault(var29, 0L) >= 30000L) {
               var3.rewardedMarks.put(var29, Long.valueOf(var5));

               while (var3.rewardedMarks.size() > 64) {
                  var3.rewardedMarks.remove(var3.rewardedMarks.keySet().iterator().next());
               }

               this.career.count(var1, var3, "scout", 20, "scout", true);
               int var31 = var4.rules.map.markScore;
               if (var31 > 0) {
                  var3.score += var31;
                  var3.credits += var31;
                  award(var1, "标记", var31, var26, "spot_assist");
               }
            }

            Network.send(var1, Map.of("type", "supportSound", "cue", "mark_success"));

            for (ServerPlayer var35 : this.server.getPlayerList().getPlayers()) {
               Engine.Member var36 = this.members.get(var35.getUUID());
               if (var36 != null && var36.team == var3.team && var36.map.equals(var3.map)) {
                  this.state(var35, var3.map, false, "");
               }
            }
         }
      }
   }

   private void addMark(ServerPlayer var1, Engine.Member var2, Engine.Match var3, ServerPlayer var4, Spotting.Kind var5, double var6) throws Exception {
      var3.marks
         .add(
            new Spotting.Mark(
               var2.team,
               var4.getX(),
               var4.getZ(),
               System.currentTimeMillis() + (long)var3.rules.map.markSeconds * 1000L,
               var5,
               var4.getName().getString(),
               var4.getY() + 2.0
            )
         );

      while (var3.marks.size() > 32) {
         var3.marks.removeFirst();
      }

      boolean var8 = var5 == Spotting.Kind.ENEMY;
      String var9 = var8 ? "已标记" : "已呼叫";
      int var10 = var8 ? var3.rules.map.markScore : 0;
      if (var10 > 0) {
         var2.score += var10;
         var2.credits += var10;
         award(var1, var9, var10, var4.getName().getString(), var8 ? "spot_assist" : "assist");
      } else {
         award(var1, var9, 0, var4.getName().getString(), var8 ? "spot_assist" : "assist");
      }

      this.announce(var1, var9, var4.getName().getString() + " · " + (int)var6 + " 格");
      Network.send(var1, Map.of("type", "supportSound", "cue", var8 ? "mark_success" : "select"));

      for (ServerPlayer var12 : this.server.getPlayerList().getPlayers()) {
         Engine.Member var13 = this.members.get(var12.getUUID());
         if (var13 != null && var13.team == var2.team && var13.map.equals(var2.map)) {
            this.state(var12, var2.map, false, "");
         }
      }
   }

   private void terrain(ServerPlayer var1, JsonObject var2) {
      String var3 = str(var2, "map", "");
      Model.Arena var4 = this.maps.get(var3);
      if (var4 == null && this.maps.size() == 1) {
         var4 = this.maps.values().iterator().next();
      }

      Model.require(var4 != null, "地图不存在");
      double[] var5 = TerrainBake.bounds(var4);
      int[] var6 = TerrainMap.dimensions(Math.max(1.0, var5[2] - var5[0]), Math.max(1.0, var5[3] - var5[1]));
      String var7 = TerrainMap.signature(var4, var6[0], var6[1]);
      TerrainMap var8 = this.terrainMaps.get(var4.id);
      if (var8 != null && var8.signature.equals(var7) && var8.valid()) {
         sendTerrain(var1, var4.id, var8);
      } else {
         if (var8 != null) {
            this.terrainMaps.remove(var4.id);
         }

         TerrainMap var9 = TerrainBake.load(this.folder, var4.id, var7);
         if (var9 != null && var9.valid()) {
            this.terrainMaps.put(var4.id, var9);
            sendTerrain(var1, var4.id, var9);
         } else {
            TerrainBake var10 = this.bakes.get(var4.id);
            if (var10 == null || !var10.signature().equals(var7)) {
               this.bakes.put(var4.id, new TerrainBake(var4));
               this.terrainWaiters.remove(var4.id);
            }

            this.terrainWaiters.computeIfAbsent(var4.id, var0 -> new LinkedHashSet<>()).add(var1.getUUID());
            LinkedHashMap var11 = new LinkedHashMap();
            var11.put("type", "terrain");
            var11.put("map", var4.id);
            var11.put("state", "BAKING");
            var11.put("progress", this.bakes.get(var4.id).progress());
            Network.send(var1, var11);
         }
      }
   }

   public void tick() {
      this.cosmeticKills.tick();
      this.ticks++;
      if (this.ticks % 20L == 0L) {
         for (Entry var2 : this.members.entrySet()) {
            ServerPlayer var3 = this.server.getPlayerList().getPlayer((UUID)var2.getKey());
            if (var3 != null) {
               this.career.poll(var3, (Engine.Member)var2.getValue());
            }
         }
      }

      if (this.ticks % 1200L == 0L) {
         this.career.save();
      }

      this.support.tick(this.ticks);
      this.rush.tick(this.ticks);
      this.refills.tick(this.ticks);
      if (this.ticks % 20L == 0L) {
         this.tickNutrition();
      }

      try {
         this.tickDowned();
      } catch (LinkageError | RuntimeException var28) {
         BattlefieldOps.LOG.error("Downed-state tick failed", var28);
      }

      try {
         this.stepTerrainBakes();
      } catch (LinkageError | RuntimeException var27) {
         BattlefieldOps.LOG.error("Terrain bake step failed", var27);
         this.bakes.clear();
      }

      try {
         this.tickBotsMovement();
      } catch (LinkageError | RuntimeException var26) {
         BattlefieldOps.LOG.error("Bot movement tick failed", var26);
      }

      for (Entry var32 : this.members.entrySet()) {
         Engine.Member var35 = (Engine.Member)var32.getValue();
         if (var35.entryUntil >= 0L && (this.ticks >= var35.entryUntil || this.ticks % 20L == 0L)) {
            ServerPlayer var4 = this.server.getPlayerList().getPlayer((UUID)var32.getKey());
            if (var4 != null) {
               boolean var5 = this.ticks >= var35.entryUntil;
               if (var5) {
                  var35.entryUntil = -1L;
                  var35.observerAt = this.ticks;
               }

               this.state(var4, var35.map, var5, "");
            }
         }
      }

      if (this.ticks % 20L == 0L) {
         this.positionDeployObservers();
         this.cleanBeacons();

         for (Engine.Match var33 : this.matches.values()) {
            for (UUID var38 : new ArrayList<>(var33.elites.claims.keySet())) {
               Engine.Member var40 = this.members.get(var38);
               if (var40 == null || !var40.alive || !var40.map.equals(var33.rules.map.id) || var40.eliteType.isEmpty()) {
                  var33.elites.release(var38, this.ticks);
                  if (var40 != null && !var40.alive) {
                     var40.eliteType = "";
                  }
               }
            }
         }

         for (Entry var34 : new ArrayList<>(this.matches.entrySet())) {
            String var37 = (String)var34.getKey();
            Engine.Match var39 = (Engine.Match)var34.getValue();
            Rules var41 = var39.rules;
            Model.Arena var6 = var41.map;

            try {
               this.refreshMarks(var39);
            } catch (LinkageError | RuntimeException var25) {
               BattlefieldOps.LOG.error("Cannot refresh enemy spotting for " + var37, var25);
            }

            List var7 = this.server.getPlayerList().getPlayers().stream().filter(var2x -> {
               Engine.Member var3x = this.members.get(var2x.getUUID());
               return var3x != null && var3x.map.equals(var37);
            }).toList();
            if (!var39.running) {
               for (ServerPlayer var43 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                  this.state(var43, var37, false, "");
               }
            } else {
               ServerLevel var8 = this.levelOrNull(var6);
               if (var6.botMode) {
                  if (!var39.botsSpawned && var8 != null) {
                     var39.botsSpawned = true;
                     this.spawnBots(var39, var8);
                  }

                  if (var8 != null) {
                     this.respawnBots(var39, var8);
                  }
               }

               if (!var7.isEmpty() || !var39.bots.isEmpty()) {
                  int[][] var9 = new int[var6.points.size()][2];
                  HashMap var10 = new HashMap();

                  for (ServerPlayer var12 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                     Engine.Member var13 = this.members.get(var12.getUUID());
                     if (var13.alive && var12.isAlive() && var13.team >= 0) {
                        boolean var14 = var12.level().dimension().location().toString().equals(var6.dimension);
                        boolean var15 = !var14 || RoomPolicy.beyond(var6, var12.getX(), var12.getY(), var12.getZ());
                        if (var15) {
                           this.death(var12, null);
                           this.state(var12, var37, true, "离开战区过远，已阵亡，请重新部署");
                        } else {
                           boolean var16 = Model.contains(var6.boundary, var12.getX(), var12.getZ())
                              && var12.getY() >= (double)var6.minY
                              && var12.getY() <= (double)var6.maxY;
                           boolean var17 = !var41.isStaged() || var6.inSector(var41.stage, var12.getX(), var12.getZ());
                           Entity var18 = var12.getRootVehicle();
                           String var19 = var18 == var12
                              ? "I"
                              : String.valueOf(this.catalog.vehicleClass(BuiltInRegistries.ENTITY_TYPE.getKey(var18.getType()).toString()));
                           Model.Zone var20 = var6.zone(var13.team, var19);
                           if (!var19.equals("I") && var20 != null && var19.equals(var20.mobility)) {
                              var17 = true;
                           }

                           boolean var21 = var6.inZone(var13.team, var19, var12.getX(), var12.getZ());
                           boolean var22 = var16 && var17 && var21;
                           if (var22) {
                              var13.outside = 0;
                              var13.warnedSecond = -1;
                              if (!var12.isSpectator() && !var13.downed) {
                                 for (int var75 = 0; var75 < var6.points.size(); var75++) {
                                    if (var41.active(var75)) {
                                       Model.Point var24 = var6.points.get(var75);
                                       if (ObjectiveZone.contains(var24, var12.getX(), var12.getY(), var12.getZ())) {
                                          var9[var75][var13.team]++;
                                          ((List<ServerPlayer>)var10.computeIfAbsent(var75, var0 -> new ArrayList<ServerPlayer>())).add(var12);
                                       }
                                    }
                                 }
                              }
                           } else {
                              var13.outside++;
                              int var23 = Math.max(0, var6.outsideSeconds - var13.outside);
                              this.boundaryWarning(var12, var23, var16 && var17 ? "已离开本方活动区域 " : "已离开战区 ");
                              if (var13.outside >= var6.outsideSeconds) {
                                 this.death(var12, null);
                                 this.state(var12, var37, true, "越界倒计时结束，已阵亡，请重新部署");
                              }
                           }
                        }
                     }
                  }

                  for (UUID var46 : var39.bots) {
                     Engine.Member var53 = this.members.get(var46);
                     if (var53 != null && var53.alive && this.findEntity(var46) instanceof LivingEntity var66 && var66.isAlive()) {
                        for (int var70 = 0; var70 < var6.points.size(); var70++) {
                           if (var41.active(var70)) {
                              Model.Point var74 = var6.points.get(var70);
                              if (ObjectiveZone.contains(var74, var66.getX(), var66.getY(), var66.getZ())) {
                                 var9[var70][var53.team]++;
                                 break;
                              }
                           }
                        }
                     }
                  }

                  this.updateGlow(var7);
                  this.updateSupply(var6, var7);
                  this.reapEmptyVehicles(var39);
                  int var45 = var41.stage;

                  for (Rules.Capture var54 : var41.tick(var9)) {
                     if (var54.team() >= 0) {
                        for (ServerPlayer var67 : (java.lang.Iterable<ServerPlayer>)(var10.getOrDefault(var54.point(), List.of()))) {
                           Engine.Member var71 = this.members.get(var67.getUUID());
                           if (var71.team == var54.team()) {
                              var71.captures++;
                              this.career.capture(var71, var6.captureScore);
                              var71.score = var71.score + var6.captureScore;
                              var71.credits = var71.credits + var6.captureScore;
                              award(var67, "占领", var6.captureScore, "", "capture");
                           }
                        }
                     }
                  }

                  if (var41.winner != -2) {
                     if (var41.isStaged()) {
                        for (ServerPlayer var59 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                           Engine.Member var65 = this.members.get(var59.getUUID());
                           if (var65 != null) {
                              this.career.stage(var59, var65, var41.map.isRush());
                           }
                        }
                     }

                     this.finish(var37, var41.result());
                  } else {
                     for (Rules.Notice var55 : var41.drainNotices()) {
                        for (ServerPlayer var68 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                           Engine.Member var72 = this.members.get(var68.getUUID());
                           if (var72 != null && var72.team >= 0) {
                              var68.displayClientMessage(Component.literal((var72.team == var55.team() ? "§b我军" : "§c敌军") + "§r" + var55.text()), false);
                              var68.displayClientMessage(Component.literal((var72.team == var55.team() ? "§b我军" : "§c敌军") + "§r" + var55.text()), true);
                           }
                        }
                     }

                     if (var45 != var41.stage) {
                        for (ServerPlayer var56 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                           Engine.Member var63 = this.members.get(var56.getUUID());
                           if (var63 != null) {
                              this.career.stage(var56, var63, var41.map.isRush());
                           }
                        }

                        for (ServerPlayer var57 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                           Engine.Member var64 = this.members.get(var57.getUUID());
                           if (var64 != null && var64.team >= 0) {
                              var64.alive = false;
                              var64.downed = false;
                              var64.downedAt = -1L;
                              var64.giveUpAt = -1L;
                              var64.giveUpHeldAt = -100L;
                              var64.reviveProgress = 0;
                              var64.reviving = null;
                              this.clearReviver(var57.getUUID());
                              downPose(var57, false);
                              var64.readyAt = this.ticks + 40L;
                              var64.observerAt = this.ticks;
                              var57.stopRiding();
                              var57.getInventory().clearContent();
                              var57.setGameMode(GameType.SPECTATOR);
                              int var69 = var41.stage;
                              Model.Spawn var73 = var6.spawns
                                 .stream()
                                 .filter(var2x -> var2x.team == var64.team && var2x.sector == var69)
                                 .findFirst()
                                 .orElseThrow(() -> new IllegalStateException("阶段 " + var69 + " 缺少出生点"));
                              this.teleport(var57, this.level(var6), var73);
                              this.state(var57, var37, true, "突破成功，进入阶段 " + var41.stage + "，请重新部署");
                           }
                        }
                     }

                     this.sidebar(var39);

                     for (ServerPlayer var58 : (java.lang.Iterable<ServerPlayer>)(var7)) {
                        this.state(var58, var37, false, "");
                     }
                  }
               }
            }
         }
      }
   }

   private void boundaryWarning(ServerPlayer var1, int var2) {
      this.boundaryWarning(var1, var2, "已离开战区 ");
   }

   private void boundaryWarning(ServerPlayer var1, int var2, String var3) {
      var1.displayClientMessage(Component.literal("返回战区 · " + var2 + " 秒 · " + var3.trim()), true);
   }

   private void announce(ServerPlayer var1, String var2, String var3) {
      var1.displayClientMessage(Component.literal(var2 + (var3.isEmpty() ? "" : " · " + var3)), true);
   }

   private void updateGlow(List<ServerPlayer> var1) {
      for (ServerPlayer var3 : var1) {
         Engine.Member var4 = this.members.get(var3.getUUID());
         if (var4 != null && var4.glowing) {
            this.clearGlow(var3, var4);
         }
      }
   }

   private void clearGlow(ServerPlayer var1, Engine.Member var2) {
      if (var2 != null && var2.glowing) {
         var1.setGlowingTag(false);
      }

      if (var2 != null) {
         var2.glowing = false;
      }
   }

   private static Tactics.At at(ServerPlayer var0) {
      return new Tactics.At(var0.getX(), var0.getY(), var0.getZ());
   }

   private void sidebar(Engine.Match var1) {
      this.board.hideSidebar();
      this.sidebarShown = false;
   }

   private void finish(String var1, String var2) {
      this.rush.close(var1);
      Engine.Match var3 = this.matches.remove(var1);
      if (var3 != null) {
         this.results.put(var1, var2);
         boolean var4 = var2.contains("平局");
         int var5 = var4 ? -1 : (var2.contains("机械轴心") ? 0 : (var2.contains("格雷同盟") ? 1 : -1));
         List var6 = this.members
            .values()
            .stream()
            .filter(var1x -> var1x.map.equals(var1) && !var1x.bot)
            .sorted(Comparator.<Engine.Member>comparingInt(var0 -> var0.score).reversed())
            .toList();

         try {
            for (ServerPlayer var8 : (java.lang.Iterable<ServerPlayer>)(new ArrayList(this.server.getPlayerList().getPlayers()))) {
               Engine.Member var9 = this.members.get(var8.getUUID());
               if (var9 != null && var9.map.equals(var1)) {
                  try {
                     if (var3.running && var9.team >= 0) {
                        this.clearGlow(var8, var9);
                        LinkedHashMap var10 = new LinkedHashMap();
                        var10.put("type", "result");
                        var10.put("mapName", var3.rules.map.name);
                        var10.put("mode", var3.rules.map.mode);
                        var10.put("modeName", var3.rules.map.modeName());
                        var10.put("reason", var2);
                        var10.put("outcome", var3.rules.winner == -2 ? "对局结束" : (var5 < 0 ? "平局" : (var5 == var9.team ? "胜利" : "失败")));
                        var10.put("kills", var9.kills);
                        var10.put("deaths", var9.deaths);
                        var10.put("captures", var9.captures);
                        var10.put("score", var9.score);
                        var10.put("assists", var9.assists);
                        var10.put("revives", var9.revives);
                        var10.put("ranking", var6);
                        var10.put("team", var9.team);
                        var10.put("playerName", var9.name);
                        if (var3.rules.winner != -2) {
                           try {
                              var10.put("experience", this.progress.award(var8.getUUID(), var9.score, var5 == var9.team));
                              var10.put("level", this.progress.level(var8.getUUID()));
                           } catch (IOException var38) {
                              BattlefieldOps.LOG.error("Cannot persist player progression", var38);
                              var10.put("progressError", "等级结算写入失败，请联系管理员检查 progression.json；原文件已保留");
                           }
                        }

                        this.leave(var8);
                        this.transferToLobby(var8);
                        this.state(var8, var1, false, var2);
                        Network.send(var8, var10);
                     } else {
                        this.leave(var8);
                        this.transferToLobby(var8);
                        this.state(var8, var1, false, var2);
                     }
                  } catch (LinkageError | RuntimeException var41) {
                     BattlefieldOps.LOG.error("Cannot finish participant {} in {}", new Object[]{var8.getUUID(), var1, var41});

                     try {
                        this.leave(var8);
                        this.recover(var8);
                        this.transferToLobby(var8);
                        this.state(var8, var1, false, var2);
                     } catch (LinkageError | RuntimeException var37) {
                        BattlefieldOps.LOG.error("Participant cleanup failed; inventory backup retained", var37);
                     }
                  }
               }
            }
         } finally {
            try {
               this.career.finish(var1);
               this.board.clearMap(var1);
               this.board.hideSidebar();
            } catch (LinkageError | RuntimeException var36) {
               BattlefieldOps.LOG.error("Cannot clear room scoreboard " + var1, var36);
            }

            try {
               this.removeBots(var3);
            } catch (LinkageError | RuntimeException var35) {
               BattlefieldOps.LOG.error("Cannot remove room bots " + var1, var35);
            }
         }

         for (UUID var44 : var3.vehicles) {
            try {
               Entity var45 = this.findEntity(var44);
               if (var45 != null) {
                  var45.discard();
               }
            } catch (LinkageError | RuntimeException var39) {
               BattlefieldOps.LOG.error("Cannot remove room vehicle " + var44, var39);
            } finally {
               this.vehicles.remove(var44);
            }
         }
      }
   }

   private static String resultTitle(String var0) {
      if (var0.contains("平局")) {
         return "§7平局";
      } else if (var0.contains("机械轴心")) {
         return "§9机械轴心胜利";
      } else {
         return var0.contains("格雷同盟") ? "§c格雷同盟胜利" : var0;
      }
   }

   private static String resultSubtitle(Engine.Member var0, int var1) {
      String var2 = var1 < 0 ? "§7平局" : (var1 == var0.team ? "§a胜利" : "§c失败");
      return var2 + " §7击杀 " + var0.kills + " 阵亡 " + var0.deaths + " 占点 " + var0.captures + " §f得分 " + var0.score;
   }

   void deactivate() {
      this.shutdown();

      for (ServerPlayer var2 : this.server.getPlayerList().getPlayers()) {
         if (this.members.containsKey(var2.getUUID())) {
            this.leave(var2);
         } else {
            this.recover(var2);
         }

         LobbyAccess.remove(var2);
         Model.require(!Files.exists(this.folder.resolve("players").resolve(var2.getUUID() + ".nbt")), "玩家 " + var2.getScoreboardName() + " 的背包尚未恢复");
      }
   }

   public void shutdown() {
      this.career.save();
      this.support.close();
      this.refills.clear();

      for (Engine.Beacon var2 : new ArrayList<>(this.beacons.values())) {
         this.removeBeacon(var2);
      }

      for (String var4 : new ArrayList<>(this.matches.keySet())) {
         this.finish(var4, "服务器关闭，对局结束");
      }

      this.board.clearAll();
   }

   public void open(ServerPlayer var1) {
      if (Network.supported(var1) && !this.members.containsKey(var1.getUUID())) {
         this.lobby(var1, true, "");
      } else {
         this.state(var1, "", true, "");
      }
   }

   private void setLobby(ServerPlayer var1) throws IOException {
      Model.require(!this.members.containsKey(var1.getUUID()), "请先退出房间，再设置大厅位置");
      Model.require(var1.isAlive() && !var1.isPassenger(), "请存活并离开载具后设置大厅");
      LobbySettings var2 = new LobbySettings();
      var2.enabled = true;
      var2.dimension = var1.level().dimension().location().toString();
      var2.x = var1.getX();
      var2.y = var1.getY();
      var2.z = var1.getZ();
      var2.yaw = var1.getYRot();
      var2.pitch = var1.getXRot();
      var2.validate();

      for (Engine.Match var4 : this.matches.values()) {
         Model.require(!RoomPolicy.hubConflict(var4.rules.map, var2.dimension, var2.x, var2.z), "大厅应远离现有房间及退出缓冲区");
      }

      MapFiles.save(this.folder, "lobby", BattlefieldOps.GSON.toJson(var2));
      this.lobbySettings = var2;
   }

   private void transferToLobby(ServerPlayer var1) {
      if (this.lobbySettings.enabled
         && !this.members.containsKey(var1.getUUID())
         && !Files.exists(this.folder.resolve("players").resolve(var1.getUUID() + ".nbt"))) {
         ServerLevel var2 = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(this.lobbySettings.dimension)));
         if (var2 == null) {
            var1.sendSystemMessage(Component.literal("大厅维度不可用，已保留原位置"));
         } else {
            var1.stopRiding();
            var1.teleportTo(var2, this.lobbySettings.x, this.lobbySettings.y, this.lobbySettings.z, this.lobbySettings.yaw, this.lobbySettings.pitch);
            var1.setDeltaMovement(0.0, 0.0, 0.0);
            var1.fallDistance = 0.0F;
         }
      }
   }

   private void lobby(ServerPlayer var1, boolean var2, String var3) {
      Engine.Member var4 = this.members.get(var1.getUUID());
      var var5 = new ArrayList();

      for (Model.Arena var7 : this.maps.values()) {
         Engine.Match var8 = this.matches.get(var7.id);
         var var9 = this.members.values().stream().filter(var1x -> var1x.map.equals(var7.id) && !var1x.bot).toList();
         LinkedHashMap var10 = new LinkedHashMap();
         var10.put("id", var7.id);
         var10.put("name", var7.name);
         var10.put("mode", var7.mode);
         var10.put("modeName", var7.modeName());
         var10.put("room", var8 != null);
         var10.put("running", var8 != null && var8.running);
         var10.put("players", var9.size());
         var10.put("capacity", var8 == null ? var7.maxPlayers : var8.rules.map.maxPlayers);
         var10.put("blue", var9.stream().filter(var0 -> var0.team == 0).count());
         var10.put("red", var9.stream().filter(var0 -> var0.team == 1).count());
         var10.put("waiting", var9.stream().filter(var0 -> var0.team < 0).count());
         var10.put("dimension", var7.dimension);
         var10.put("canManage", var8 != null && this.roomControl(var1, var8));
         var10.put("host", var8 == null ? "" : var8.ownerName);
         var5.add(var10);
      }

      LinkedHashMap var11 = new LinkedHashMap();
      var11.put("type", "lobby");
      var11.put("open", var2);
      var11.put("message", var3);
      var11.put("admin", var1.hasPermissions(2));

      for (Map var13 : (java.lang.Iterable<Map>)(var5)) {
         String var14 = String.valueOf(var13.get("id"));
         Model.Arena var15 = this.maps.get(var14);
         if (var15 != null) {
            var13.put("description", var15.roomDescription);
         }
      }

      var11.put("rooms", var5);
      var11.put("membership", var4 == null ? "" : var4.map);
      var11.put("hubEnabled", this.lobbySettings.enabled);
      var11.put(
         "hub",
         this.lobbySettings.enabled
            ? this.lobbySettings.dimension
               + " · "
               + Math.round(this.lobbySettings.x)
               + ", "
               + Math.round(this.lobbySettings.y)
               + ", "
               + Math.round(this.lobbySettings.z)
            : "未设置：退出房间恢复原位置"
      );
      var11.put("online", this.server.getPlayerList().getPlayerCount());
      var11.put("activeRooms", this.matches.size());
      Network.send(var1, var11);
   }

   public void state(ServerPlayer var1, String var2, boolean var3, String var4) {
      if (!Network.supported(var1)) {
         if (var3) {
            var1.sendSystemMessage(Component.literal("你可以正常游玩服务器；安装战地风云客户端模组后才能加入地图对局。"));
         }
      } else {
         Engine.Member var5 = this.members.get(var1.getUUID());
         if (var5 != null) {
            var2 = var5.map;
         }

         String var6 = this.maps.containsKey(var2) ? var2 : this.maps.keySet().stream().findFirst().orElse("");
         LinkedHashMap var7 = new LinkedHashMap();
         var7.put("type", "state");
         var7.put("open", var3);
         var7.put("message", var4);
         var7.put("admin", var1.hasPermissions(2));
         var7.put(
            "maps",
            this.maps
               .values()
               .stream()
               .map(
                  var1x -> Map.of(
                        "id",
                        var1x.id,
                        "name",
                        var1x.name,
                        "modeName",
                        var1x.modeName(),
                        "running",
                        this.matches.containsKey(var1x.id) && this.matches.get(var1x.id).running,
                        "room",
                        this.matches.containsKey(var1x.id)
                     )
               )
               .toList()
         );
         var7.put("selected", var6);
         Engine.Match var8 = this.matches.get(var6);
         boolean var9 = RoomPolicy.canView(var5 == null ? null : var5.map, var5 == null ? -1 : var5.team, var6, var8 != null && var8.running);
         var7.put("inMatch", var9);
         var7.put("roomExists", var8 != null);
         var7.put("canManageRoom", var8 != null && this.roomControl(var1, var8));
         var7.put("roomRunning", var8 != null && var8.running);
         var7.put("roomPlayers", this.members.values().stream().filter(var1x -> var1x.map.equals(var6)).count());
         var var10 = new ArrayList();
         if (var5 != null && var5.team >= 0) {
            for (int var11 = 1; var11 <= 8; var11++) {
               int var12 = var11;
               List var13 = this.members
                  .values()
                  .stream()
                  .filter(var3x -> SquadRules.same(var6, var5.team, var12, var3x.map, var3x.team, var3x.squad))
                  .map(var0 -> var0.name)
                  .toList();
               var10.add(Map.of("id", var11, "count", var13.size(), "members", String.join("、", var13)));
            }
         }

         var7.put("squads", var10);
         var7.put("position", List.of(var1.getX(), var1.getY(), var1.getZ()));
         var7.put("yaw", var1.getYRot());
         var7.put("dimension", var1.level().dimension().location().toString());
         var7.put("diagnostics", this.catalog.diagnostics);
         var7.put("catalogCount", this.catalog.entries.size());
         var7.put("catalog", Map.of("guns", this.catalog.guns.size(), "vehicles", this.catalog.vehicles.size()));
         LinkedHashMap var24 = new LinkedHashMap();
         var24.put("battleMapEnabled", this.compat.settings().minimap != CompatPolicy.Mode.NEVER);
         var24.put("sidebar", this.sidebarShown);
         var24.put("teams", this.board.teamsEnabled());
         var24.put("minimap", this.compat.policy().mayUseMinimap(this.compat.settings().minimap, this.compat.settings().xaeroMinimap));
         var24.put("xaero", this.compat.settings().xaeroMinimap);
         var24.put("minimapCorner", Briefing.normaliseCorner(this.compat.settings().minimapCorner));
         var24.put("hideXaero", this.compat.settings().hideXaeroInMatch);
         var24.put("strictAids", this.compat.settings().strictAidsInMatch);
         var7.put("hud", var24);
         var7.put("compat", this.compat.report(Catalog.hasTacz(), Catalog.hasSuperbWarfare(), this.server.getServerModName()));
         Engine.Member var25 = this.members.get(var1.getUUID());
         var var26 = new ArrayList();
         if (var9) {
            var26.add(this.blip(var1, var25, true, false, var25));

            for (ServerPlayer var15 : this.server.getPlayerList().getPlayers()) {
               Engine.Member var16 = this.members.get(var15.getUUID());
               if (var16 != null && var16 != var25 && var16.alive && var16.map.equals(var25.map)) {
                  boolean var17 = var16.team != var25.team;
                  if (var17) {
                     boolean var18 = var8.marks
                        .stream()
                        .anyMatch(
                           var2x -> var2x.kind() == Spotting.Kind.ENEMY
                                 && var2x.team() == var25.team
                                 && var2x.active(System.currentTimeMillis())
                                 && var2x.target().equals(var15.getUUID().toString())
                        );
                     if (!var18 || !var25.alive || var1.level() != var15.level()) {
                        continue;
                     }
                  }

                  var26.add(this.blip(var15, var16, false, var17, var25));
               }
            }
         }

         var7.put("mates", var26);
         if (var25 != null && this.supportActive(var25)) {
            var var27 = new ArrayList();
            boolean var31 = false;

            for (Entry var43 : this.members.entrySet()) {
               Engine.Member var50 = (Engine.Member)var43.getValue();
               boolean var19 = ((UUID)var43.getKey()).equals(var1.getUUID());
               if (var19 || var25.squad > 0 && SquadRules.same(var25.map, var25.team, var25.squad, var50.map, var50.team, var50.squad)) {
                  boolean var20 = !var31 && !var19 && var25.squad > 0;
                  if (var20) {
                     var31 = true;
                  }

                  var27.add(Map.of("name", var50.name, "self", var19, "alive", var50.alive, "downed", var50.downed, "kit", var50.kit, "leader", var20));
               }
            }

            var7.put("squadRoster", var27);
         }

         var7.put("supportDevices", var9 ? this.support.snapshot(var25) : List.of());
         var7.put("gadgets", var9 ? this.refills.snapshot(var1, this.ticks) : List.of());
         var7.put("elitePoints", var9 ? this.eliteSnapshot(var1, var25, var8) : List.of());
         if (var9) {
            var var28 = new ArrayList();
            long var32 = System.currentTimeMillis();
            Iterator var44 = Spotting.visibleTo(this.matches.get(var6).marks, var25.team, var32).iterator();

            label357:
            while (true) {
               Spotting.Mark var51;
               LinkedHashMap var56;
               while (true) {
                  if (!var44.hasNext()) {
                     var7.put("marks", var28);
                     break label357;
                  }

                  var51 = (Spotting.Mark)var44.next();
                  var56 = new LinkedHashMap();
                  var56.put("team", var51.team());
                  var56.put("kind", var51.kind().id());
                  var56.put("name", var51.name());
                  var56.put("target", var51.target());
                  var56.put("kit", var51.kit());
                  var56.put("action", var51.action());
                  var56.put("x", var51.x());
                  var56.put("y", var51.y());
                  var56.put("z", var51.z());
                  if (!var51.target().isEmpty()) {
                     try {
                        Entity var61 = var1.serverLevel().getEntity(UUID.fromString(var51.target()));
                        if (var61 instanceof ServerPlayer var21) {
                           Engine.Member var22 = this.members.get(var21.getUUID());
                           if (var22 == null || !var22.alive || !var22.map.equals(var25.map)) {
                              continue;
                           }
                        }

                        if (var61 != null && var61.isAlive()) {
                           var56.put("entityId", var61.getId());
                           var56.put("x", var61.getX());
                           var56.put("y", var61.getY() + (double)var61.getBbHeight() + 0.35);
                           var56.put("z", var61.getZ());
                        }
                     } catch (IllegalArgumentException var23) {
                     }
                  }
                  break;
               }

               var56.put("left", Math.max(0L, (var51.expiresAt() - var32 + 999L) / 1000L));
               var56.put("leftMs", Math.max(0L, var51.expiresAt() - var32));
               var28.add(var56);
            }
         }

         var7.put("elapsed", var9 ? this.matches.get(var6).rules.elapsed : 0);
         if (var9) {
            var7.put("rush", this.rush.snapshot(var6, this.ticks, var25 == null ? -1 : var25.team));
            if (this.matches.get(var6).rules.map.isRush()) {
               var7.put("rushHint", "空手朝向炸点中心，按住右键安放／拆除");
            }
         }

         var7.put("vehicles", var9 ? this.vehicleBlips(var6, var25, var1) : List.of());
         var7.put("hazards", var9 ? DangerMarkers.snapshot(var1, this.matches.get(var6).rules.map, var25, this) : List.of());
         var7.put("pads", var9 ? this.padBlips(var6) : List.of());
         var7.put("refill", var9 ? this.support.refillProgress(var1) : 0.0);
      var7.put("refillWait", var9 ? this.support.refillWait(var1) : 0.0);
      try {
        var7.put("refillNeed", var9 && this.supportNeeded(var1));
      } catch (ReflectiveOperationException unreadable) {
        var7.put("refillNeed", false);
      }
         if (this.maps.containsKey(var6)) {
            Model.Arena var29 = this.maps.get(var6);
            var7.put("arena", var29);
            var7.put("names", this.names(var29));
            var7.put("gunStats", this.gunStats(var29));
            var7.put("vehicleFacts", this.vehicleFacts(var29));
            LinkedHashMap var33 = new LinkedHashMap();

            for (String var45 : var29.vehicles) {
               Integer var52 = this.catalog.vehicleSeats.get(var45);
               if (var52 != null && var52 > 0) {
                  var33.put(var45, var52);
               }
            }

            var7.put("vehicleSeats", var33);
            var7.put("fittingCounts", this.fittingCounts(var29));
         }

         Engine.Member var30 = this.members.get(var1.getUUID());
         if (var30 != null) {
            var7.put("me", var30);
            if (var30.entryUntil > this.ticks && this.matches.containsKey(var30.map)) {
               Model.Arena var34 = this.matches.get(var30.map).rules.map;
               LinkedHashMap var39 = new LinkedHashMap();
               var39.put("token", var30.entryToken);
               var39.put("remaining", Long.valueOf(var30.entryUntil - this.ticks));
               var39.put("duration", Integer.valueOf(var34.entrySeconds * 20));
               var39.put("room", var34.name);
               var39.put("map", var34.name);
               var39.put("dimension", var34.dimension);
               var39.put("description", var34.roomDescription);
               var39.put(
                  "tips",
                  List.of(
                     var34.entryTip1 == null ? "" : var34.entryTip1,
                     var34.entryTip2 == null ? "" : var34.entryTip2,
                     var34.entryTip3 == null ? "" : var34.entryTip3
                  )
               );
               Model.EntryLayout var46 = var34.entry == null ? new Model.EntryLayout() : var34.entry;
               var39.put(
                  "layout",
                  Map.of(
                     "textX",
                     var46.textX,
                     "textY",
                     var46.textY,
                     "titleScale",
                     Math.round(var46.titleScale * 10.0F),
                     "descWidth",
                     var46.descWidth,
                     "descHeight",
                     var46.descHeight,
                     "descBottom",
                     var46.descBottom,
                     "waitX",
                     var46.waitX
                  )
               );
               if (var34.customEntryView) {
                  var39.put("camera", List.of(var34.entryX, var34.entryY, var34.entryZ, var34.entryYaw, var34.entryPitch));
               } else if (var34.customDeployView) {
                  var39.put("camera", List.of(var34.deployViewX, var34.deployViewY, var34.deployViewZ, var34.deployViewYaw, var34.deployViewPitch));
               } else {
                  double[] var53 = var34.extent(var34.boundary);
                  double var57 = var34.spawns.stream().mapToDouble(var0 -> var0.y).max().orElse(64.0);
                  var39.put("camera", List.of(var53[0], var57 + 81.62, var53[1], 0, 90));
               }

               var7.put("entry", var39);
            }

            var7.put("respawnRemaining", Math.max(0L, (var30.readyAt - this.ticks + 19L) / 20L));
            var7.put(
               "giveUpProgress",
               var30.downed && var30.giveUpAt >= 0L && this.ticks - var30.giveUpHeldAt <= 6L ? Math.min(20L, this.ticks - var30.giveUpAt) : 0L
            );
            var7.put("downLeft", var30.downed ? Medic.secondsLeft(var30.bleedOutAt, this.ticks) : 0);
            var7.put("reviveProgress", var30.downed ? var30.reviveProgress : 0);
            var7.put("vehicleRemaining", Math.max(0L, (var30.vehicleAt - this.ticks + 19L) / 20L));
            var7.put("team", var30.team);
            var7.put("teamName", Board.label(var30.team));
            var7.put("vehicleState", this.vehicleState(var6, var30));
            if (var8 != null) {
               var var35 = new ArrayList();
               var var40 = new ArrayList();

               for (Engine.DeployOption var54 : this.deploymentOptions(var1, var30, var8)) {
                  Deploy.Site var58 = var54.site;
                  if (!var54.blocked.isEmpty()) {
                     var40.add(Map.of("id", var58.id(), "label", var58.label(), "reason", var54.blocked));
                  } else {
                     LinkedHashMap var62 = new LinkedHashMap();
                     var62.put("id", var58.id());
                     var62.put("label", var58.label());
                     var62.put("x", var58.x());
                     var62.put("y", var58.y());
                     var62.put("z", var58.z());
                     var62.put("allows", var58.allows());
                     var62.put("spawn", var58.spawn());
                     var62.put("kind", var58.kind());
                     var35.add(var62);
                  }
               }

               var7.put("deploySites", var35);
               var7.put("blockedDeploySites", var40);
            }
         }

         LinkedHashMap var36 = new LinkedHashMap();
         if (this.maps.containsKey(var6)) {
            for (String var48 : this.maps.get(var6).vehicles) {
               var36.put(var48, String.valueOf(this.catalog.vehicleClass(var48)));
            }
         }

         var7.put("vehicleClasses", var36);
         Engine.Match var42 = this.matches.get(var6);
         if (var9) {
            var7.put("tickets", var42.rules.tickets);
            var7.put("teamScores", var42.rules.teamScores);
            var7.put("scoreMode", var42.rules.map.isDeathmatch() ? "SCORE_TARGET" : "TICKETS");
            var7.put("scoreTarget", var42.rules.map.targetKills);
            var7.put("tracksTickets", List.of(var42.rules.tracksTickets(0), var42.rules.tracksTickets(1)));
            var7.put("owned", var42.rules.owned());
            var7.put("stage", var42.rules.stage);
            var7.put("stages", var42.rules.stageCount());
            var7.put("flags", var42.rules.flags);
            int var49 = -1;
            Model.Arena var55 = var42.rules.map;
            if (var30.alive
               && !var30.downed
               && var1.isAlive()
               && !var1.isSpectator()
               && var30.outside == 0
               && var1.level().dimension().location().toString().equals(var55.dimension)) {
               for (int var59 = 0; var59 < var55.points.size(); var59++) {
                  if (var42.rules.active(var59) && ObjectiveZone.contains(var55.points.get(var59), var1.getX(), var1.getY(), var1.getZ())) {
                     var49 = var59;
                     break;
                  }
               }
            }

            var7.put("objective", var49);
            if (!var55.isDeathmatch() && var49 < 0 && var30.alive && var1.isAlive()) {
               for (int var60 = 0; var60 < var55.points.size(); var60++) {
                  Model.Point var63 = var55.points.get(var60);
                  if (ObjectiveZone.horizontal(var63, var1.getX(), var1.getZ())) {
                     String var64 = !var42.rules.active(var60)
                        ? var63.id + " 尚未解锁（突破阶段 " + (var63.sector + 1) + "）"
                        : (
                           !ObjectiveZone.contains(var63, var1.getX(), var1.getY(), var1.getZ())
                              ? var63.id + " 高度不符：Y " + Math.round(var63.y - var63.height / 2.0) + "～" + Math.round(var63.y + var63.height / 2.0)
                              : "战区外不能占点"
                        );
                     var7.put("objectiveHint", var64);
                     break;
                  }
               }
            }

            var7.put(
               "scores",
               this.members
                  .values()
                  .stream()
                  .filter(var1x -> var1x.map.equals(var6) && var1x.team >= 0 && !var1x.bot)
                  .sorted(Comparator.<Engine.Member>comparingInt(var0 -> var0.score).reversed())
                  .toList()
            );
         }

         var7.put("result", this.results.getOrDefault(var6, ""));
         Network.send(var1, var7);
      }
   }

   private List<Map<String, Object>> vehicleBlips(String var1, Engine.Member var2, ServerPlayer var3) {
      var var4 = new ArrayList();
      Engine.Match var5 = this.matches.get(var1);
      if (var5 == null) {
         return var4;
      } else {
         int var6 = var2 == null ? -1 : var2.team;

         for (UUID var8 : new ArrayList<>(var5.vehicles)) {
            Entity var9 = this.findEntity(var8);
            if (var9 != null && !var9.isRemoved()) {
               int var10 = var5.vehicleTeams.getOrDefault(var8, -1);
               Engine.Member var12 = var9.getControllingPassenger() instanceof ServerPlayer var13 ? this.members.get(var13.getUUID()) : null;
               if (var12 != null && var12.map.equals(var1) && var12.team >= 0) {
                  var10 = var12.team;
               }

               if (var9.level() == var3.level()
                  && Tactics.contactVisible(
                     var10,
                     var6,
                     var2 != null && var2.alive,
                     var5.rules.map.glowEnemies,
                     at(var3),
                     new Tactics.At(var9.getX(), var9.getY(), var9.getZ()),
                     var3.hasLineOfSight(var9)
                  )) {
                  VehicleHud.Blip var18 = new VehicleHud.Blip(var10, var9.getX(), var9.getZ(), var9.getPassengers().size(), var9.isRemoved());
                  if (VehicleHud.visible(var18)) {
                     LinkedHashMap var14 = new LinkedHashMap();
                     String var15 = BuiltInRegistries.ENTITY_TYPE.getKey(var9.getType()).toString();
                     var14.put("name", Catalog.prettify(var15));
                     var14.put("type", var15);
                     var14.put("x", var9.getX());
                     var14.put("y", var9.getY());
                     var14.put("z", var9.getZ());
                     var14.put("team", var10);
                     var14.put("friendly", VehicleHud.friendly(var18, var6));
                     var14.put("occupants", var18.occupants());
                     var14.put("occupancy", VehicleHud.occupancy(var18));
                     var14.put("shareable", VehicleHud.shareable(var18, var6));
                     float var16 = this.catalog.vehicleHealthFraction(var9);
                     if (var16 >= 0.0F) {
                        var14.put("health", var16);
                     }

                     Integer var17 = this.catalog.vehicleSeats.get(var15);
                     if (var17 != null && var17 > 0) {
                        var14.put("seats", var17);
                     }

                     var4.add(var14);
                  }
               }
            } else {
               var5.vehicles.remove(var8);
               var5.vehicleTeams.remove(var8);
            }
         }

         return var4;
      }
   }

   private Map<String, Object> vehicleState(String var1, Engine.Member var2) {
      Model.Arena var3 = this.maps.get(var1);
      Engine.Match var4 = this.matches.get(var1);
      LinkedHashMap var5 = new LinkedHashMap();
      if (var3 != null && var4 != null) {
         var4.vehicles.removeIf(var1x -> this.findEntity(var1x) == null);
         int var6 = (int)Math.max(0L, var2.vehicleAt - this.ticks);
         boolean var7 = Deploy.sites(var3, var2.team, var4.rules.stage, var4.rules.isStaged(), Deploy.owners(var4.rules))
            .stream()
            .anyMatch(var0 -> !var0.allows().isEmpty());
         VehicleHud.State var8 = VehicleHud.availability(
            !var3.vehicles.isEmpty(), true, var7, var6, this.teamVehicles(var4, var2.team), var3.vehicleLimitFor(var2.team)
         );
         var5.put("state", var8.name());
         var5.put("deployable", VehicleHud.deployable(var8));
         var5.put("text", VehicleHud.describe(var8, (var6 + 19) / 20, this.teamVehicles(var4, var2.team), var3.vehicleLimitFor(var2.team)));
         var5.put("cooldown", var3.vehicleCooldown);
         var5.put("remaining", (var6 + 19) / 20);
         var5.put("limit", var3.vehicleLimitFor(var2.team));
         var5.put("used", this.teamVehicles(var4, var2.team));
         return var5;
      } else {
         var5.put("state", "NOT_ALLOWED");
         var5.put("deployable", false);
         var5.put("text", VehicleHud.describe(VehicleHud.State.NOT_ALLOWED, 0, 0, 0));
         return var5;
      }
   }

   private Map<String, Object> blip(ServerPlayer var1, Engine.Member var2, boolean var3, boolean var4, Engine.Member var5) {
      LinkedHashMap var6 = new LinkedHashMap();
      var6.put("name", var2.name);
      var6.put("uuid", var1.getUUID().toString());
      var6.put("y", var1.getY());
      var6.put("x", var1.getX());
      var6.put("z", var1.getZ());
      var6.put("self", var3);
      var6.put("yaw", var1.getYRot());
      var6.put("alive", var2.alive);
      var6.put("downed", var2.downed);
      var6.put("rescuable", !var4 && rescueRole(var5, var2));
      var6.put("rescueCall", var2.downed && var2.rescueCallUntil > this.ticks);
      var6.put("team", var2.team);
      var6.put("hostile", var4);
      var6.put("kit", var2.kit == null ? "" : var2.kit);
      var6.put("elite", var2.eliteType);
      var6.put("squad", var5 != null && SquadRules.same(var2.map, var2.team, var2.squad, var5.map, var5.team, var5.squad));
      Entity var7 = var1.getVehicle();
      if (var7 != null) {
         var6.put("vehicle", Catalog.prettify(BuiltInRegistries.ENTITY_TYPE.getKey(var7.getType()).toString()));
      }

      return var6;
   }

   private Map<String, String> names(Model.Arena var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for (Model.Kit var4 : var1.kits) {
         for (boolean var8 : new boolean[]{true, false}) {
            for (String var10 : var4.options(var8)) {
               if (!var10.isEmpty()) {
                  var2.put(var10, this.catalog.name(var10));
               }
            }
         }

         if (!var4.primary.isEmpty()) {
            var2.put(var4.primary, this.catalog.name(var4.primary));
         }

         if (!var4.secondary.isEmpty()) {
            var2.put(var4.secondary, this.catalog.name(var4.secondary));
         }
      }

      for (String var12 : var1.vehicles) {
         var2.put(var12, this.catalog.name(var12));
      }

      return var2;
   }

   private Map<String, String> gunStats(Model.Arena var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for (Model.Kit var4 : var1.kits) {
         for (boolean var8 : new boolean[]{true, false}) {
            for (String var10 : var4.options(var8)) {
               if (!var10.isEmpty() && this.catalog.gunStats.containsKey(var10)) {
                  var2.put(var10, this.catalog.gunStats.get(var10));
               }
            }
         }

         if (!var4.primary.isEmpty() && this.catalog.gunStats.containsKey(var4.primary)) {
            var2.put(var4.primary, this.catalog.gunStats.get(var4.primary));
         }

         if (!var4.secondary.isEmpty() && this.catalog.gunStats.containsKey(var4.secondary)) {
            var2.put(var4.secondary, this.catalog.gunStats.get(var4.secondary));
         }
      }

      return var2;
   }

   private Map<String, Map<String, Integer>> fittingCounts(Model.Arena var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for (Model.Kit var4 : var1.kits) {
         for (boolean var8 : new boolean[]{true, false}) {
            for (String var10 : var4.options(var8)) {
               if (!var10.isEmpty() && !var2.containsKey(var10)) {
                  var var11 = this.catalog.gunFittings.getOrDefault(var10, List.of());
                  if (!var11.isEmpty()) {
                     LinkedHashMap var12 = new LinkedHashMap();

                     for (Fittings.Group var14 : (java.lang.Iterable<Fittings.Group>)(var11)) {
                        var12.put(var14.id(), var14.options().size());
                     }

                     var2.put(var10, var12);
                  }
               }
            }
         }
      }

      return var2;
   }

   private Map<String, String> vehicleFacts(Model.Arena var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for (String var4 : var1.vehicles) {
         StringBuilder var5 = new StringBuilder();
         Integer var6 = this.catalog.vehicleSeats.get(var4);
         Float var7 = this.catalog.vehicleHealth.get(var4);
         if (var6 != null && var6 > 0) {
            var5.append(var6).append(" 座");
         }

         if (var7 != null && var7 > 0.0F) {
            if (var5.length() > 0) {
               var5.append(" · ");
            }

            var5.append("血量 ").append(Math.round(var7));
         }

         int var8 = this.catalog.vehicleFittings.getOrDefault(var4, List.of()).size();
         if (var8 > 0) {
            if (var5.length() > 0) {
               var5.append(" · ");
            }

            var5.append(var8).append(" 武器站");
         }

         if (var5.length() > 0) {
            var2.put(var4, var5.toString());
         }
      }

      return var2;
   }

   private static record Beacon(UUID entity, UUID owner, String map, int team, int squad, long expires, BlockPos at) {
   }

   private static final class BotConnection extends Connection {
      BotConnection() {
         super(PacketFlow.SERVERBOUND);

         try {
            Field var1 = Connection.class.getDeclaredField("channel");
            var1.trySetAccessible();
            var1.set(this, new EmbeddedChannel());
         } catch (Throwable var2) {
         }
      }

      public void send(Packet<?> var1) {
      }

      public void send(Packet<?> var1, PacketSendListener var2) {
      }

      public void send(Packet<?> var1, PacketSendListener var2, boolean var3) {
      }

      public void disconnect(Component var1) {
      }

      public void handleDisconnection() {
      }
   }

   private static record DeployOption(Deploy.Site site, String blocked, Entity vehicle) {
   }

   static final class Match {
      final Rules rules;
      boolean running;
      final EliteRuntime elites = new EliteRuntime();
      UUID owner;
      String ownerName = "";
      final Set<UUID> vehicles = new HashSet<>();
      final Map<UUID, Integer> vehicleTeams = new HashMap<>();
      final List<Spotting.Mark> marks = new ArrayList<>();
      final Map<UUID, List<Awards.Hit>> hits = new HashMap<>();
      final Map<UUID, UUID> lastKiller = new HashMap<>();
      final Map<UUID, Long> lastKillAt = new HashMap<>();
      final Map<UUID, Integer> streak = new HashMap<>();
      final List<UUID> bots = new ArrayList<>();
      boolean botsSpawned;

      Match(Model.Arena var1) {
         this.rules = new Rules(var1);
      }
   }

   public static final class Member {
      long observerAt = -1L;
      long entryStarted;
      long entryUntil = -1L;
      String entryToken = "";
      public String map;
      public String name;
      public transient String deathVehicleClass = "";
      public int team;
      public int score;
      public int kills;
      public int deaths;
      public int captures;
      public int assists;
      public int revives;
      public int outside;
      public int squad;
      public long beaconAt;
      public long combatUntil;
      public long proneAt = -100L;
      public int credits;
      public boolean alive;
      public boolean downed;
      public long bleedOutAt;
      public long rescueCallAt = -1000L;
      public long rescueCallUntil;
      public long downedAt = -1L;
      public long giveUpAt = -1L;
      public long giveUpHeldAt = -100L;
      public String lastPrimary = "";
      public String lastMelee = "";
      /** Rounds actually issued per gun at deploy; resupply tops up to this, not to the map default. */
      public java.util.Map<String,Integer> issuedAmmo = new java.util.LinkedHashMap<>();
      public String lastSecondary = "";
      public String lastKit = "";
      public String tactical1 = "";
      public String tactical2 = "";
      public Map<String, String> gunAmmo = new LinkedHashMap<>();
      public transient Map<String, ItemStack> issuedTemplates = new LinkedHashMap<>();
      public Map<String, Integer> issuedSlots = new LinkedHashMap<>();
      public transient Map<String, ItemStack> ammoTemplates = new LinkedHashMap<>();
      public String throwable = "";
      public String downedByName = "";
      public UUID downedBy;
      public int reviveProgress;
      public UUID reviving;
      public boolean killCredited;
      public boolean hasDeployTarget;
      public double deployX;
      public double deployY;
      public double deployZ;
      public float deployYaw;
      public float deployPitch;
      public long reviveHeldAt = -100L;
      public long readyAt;
      public long vehicleAt;
      public long deploySequence;
      public int warnedSecond = -1;
      public boolean glowing;
      public boolean bot;
      public long botNextPathAt;
      public String kit = "";
      public long lastMarkAt;
      public final LinkedHashMap<String, Long> rewardedMarks = new LinkedHashMap<>();
      public String eliteType = "";
      public long botNextAttackAt;

      boolean claimKill() {
         if (this.killCredited) {
            return false;
         } else {
            this.killCredited = true;
            return true;
         }
      }

      public Member(String var1, String var2, int var3) {
         this.map = var1;
         this.name = var2;
         this.team = var3;
      }
   }
}
