package cn.bfops.killicon.client.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import cn.bfops.killicon.network.packet.ScoreboardSyncPacket;

public final class LocalLeaderboardCache {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String CACHE_DIR_NAME = "leaderboard_cache";
   private static String currentServerKey;
   private static final Map<UUID, ScoreboardSyncPacket.Entry> entryMap = new LinkedHashMap<>();

   private LocalLeaderboardCache() {
   }

   private static Path getCacheDir() {
      Path dir = FMLPaths.GAMEDIR.get().resolve("data").resolve("battlefield_ops").resolve("leaderboard_cache");

      try {
         Files.createDirectories(dir);
      } catch (Exception var2) {
      }

      return dir;
   }

   public static String resolveServerKey() {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) {
         return "unknown";
      } else if (mc.getCurrentServer() != null) {
         String ip = mc.getCurrentServer().ip;
         return sanitizeKey(ip != null && !ip.isEmpty() ? ip : "multiplayer");
      } else if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
         String saveName = mc.getSingleplayerServer().getWorldData().getLevelName();
         return sanitizeKey(saveName != null && !saveName.isEmpty() ? saveName : "singleplayer");
      } else {
         return "unknown";
      }
   }

   private static String sanitizeKey(String raw) {
      return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
   }

   private static Path getCacheFile(String serverKey) {
      return getCacheDir().resolve(serverKey + ".json");
   }

   public static List<ScoreboardSyncPacket.Entry> load(String serverKey) {
      currentServerKey = serverKey;
      entryMap.clear();
      Path file = getCacheFile(serverKey);
      if (!Files.exists(file)) {
         return new ArrayList<>();
      } else {
         try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LocalLeaderboardCache.CacheData data = (LocalLeaderboardCache.CacheData)GSON.fromJson(reader, LocalLeaderboardCache.CacheData.class);
            if (data != null && data.entries != null) {
               for (LocalLeaderboardCache.CachedEntry ce : data.entries) {
                  ScoreboardSyncPacket.Entry entry = ce == null ? null : ce.toEntry();
                  // A corrupt or hand-edited file must not put a null in the map: the next save would
                  // then serialise a hole, and the tab would draw a row it cannot name.
                  if (entry != null && entry.uuid != null) {
                     entryMap.put(entry.uuid, entry);
                  }
               }
            }
         } catch (Exception var9) {
         }

         return new ArrayList<>(entryMap.values());
      }
   }

   /**
    * Writes the given rows into this server's record.
    *
    * <p>Two fixes here. The server key used to be whatever the last {@link #load} left behind, so a
    * caller that saved before ever loading — the room-close archive path, which is the one that matters —
    * hit {@code currentServerKey == null} and wrote nothing at all, silently. It now resolves the key
    * itself when it has none. And a {@link CachedEntry} with an unparseable UUID deserialised to
    * {@code null}, which then went straight into the map as a keyless null value and threw later, far
    * from the cause.
    */
   public static void mergeAndSave(List<ScoreboardSyncPacket.Entry> newEntries) {
      if (newEntries == null || newEntries.isEmpty()) {
         return;
      }
      if (currentServerKey == null) {
         currentServerKey = resolveServerKey();
      }
      for (ScoreboardSyncPacket.Entry entry : newEntries) {
         if (entry != null && entry.uuid != null) {
            entryMap.put(entry.uuid, entry);
         }
      }

      save(currentServerKey);
   }

   private static void save(String serverKey) {
      Path file = getCacheFile(serverKey);
      LocalLeaderboardCache.CacheData data = new LocalLeaderboardCache.CacheData();
      data.entries = new ArrayList<>();

      for (ScoreboardSyncPacket.Entry entry : entryMap.values()) {
         data.entries.add(LocalLeaderboardCache.CachedEntry.fromEntry(entry));
      }

      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
         GSON.toJson(data, writer);
      } catch (Exception var8) {
      }
   }

   public static void clear() {
      currentServerKey = null;
      entryMap.clear();
   }

   private static class CacheData {
      @SerializedName("entries")
      List<LocalLeaderboardCache.CachedEntry> entries;
   }

   private static class CachedEntry {
      @SerializedName("uuid")
      String uuid;
      @SerializedName("name")
      String name;
      @SerializedName("lastLoginName")
      String lastLoginName;
      @SerializedName("teamName")
      String teamName;
      @SerializedName("squadLabel")
      String squadLabel;
      @SerializedName("score")
      int score;
      @SerializedName("kill")
      int kill;
      @SerializedName("death")
      int death;
      @SerializedName("assist")
      int assist;
      @SerializedName("revive")
      int revive;
      @SerializedName("ping")
      int ping;
      @SerializedName("online")
      boolean online;
      @SerializedName("spectator")
      boolean spectator;

      static LocalLeaderboardCache.CachedEntry fromEntry(ScoreboardSyncPacket.Entry entry) {
         LocalLeaderboardCache.CachedEntry ce = new LocalLeaderboardCache.CachedEntry();
         ce.uuid = entry.uuid.toString();
         ce.name = entry.name;
         ce.lastLoginName = entry.lastLoginName;
         ce.teamName = entry.teamName;
         ce.squadLabel = entry.squadLabel;
         ce.score = entry.score;
         ce.kill = entry.kill;
         ce.death = entry.death;
         ce.assist = entry.assist;
         ce.revive = entry.revive;
         ce.ping = entry.ping;
         ce.online = entry.online;
         ce.spectator = entry.spectator;
         return ce;
      }

      ScoreboardSyncPacket.Entry toEntry() {
         UUID id;
         try {
            id = UUID.fromString(this.uuid);
         } catch (Exception var3) {
            return null;
         }

         // `online` is deliberately false: nobody in a closed room is verifiably online, and claiming
         // otherwise would put a live-presence dot on a player who may not have logged in for days.
         // The offline *filter* is what has to know better, and it does — {@code ScoreboardTab} skips it
         // for a restored record, because filtering a historical table by presence is meaningless and
         // would empty it.
         return new ScoreboardSyncPacket.Entry(
            id, this.name, this.lastLoginName, this.teamName, this.squadLabel, this.score, this.kill,
            this.death, this.assist, this.revive, -1, false, false
         );
      }
   }
}
