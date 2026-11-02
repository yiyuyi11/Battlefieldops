package cn.bfops.killicon.client.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.loading.FMLPaths;

public class StatsPersistenceManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String STATS_FILE_NAME = "client_stats.json";

   private static Path getStatsFilePath() {
      Path gameDirectory = FMLPaths.GAMEDIR.get();
      Path dataDirectory = gameDirectory.resolve("data").resolve("battlefield_ops");

      try {
         Files.createDirectories(dataDirectory);
      } catch (Exception var3) {
      }

      return dataDirectory.resolve("client_stats.json");
   }

   public static void saveStats(StatsPersistenceManager.ClientStatsData data) {
      if (data != null) {
         Path statsFile = getStatsFilePath();

         try (Writer writer = Files.newBufferedWriter(statsFile, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
         } catch (Exception var7) {
         }
      }
   }

   public static StatsPersistenceManager.ClientStatsData loadStats() {
      Path statsFile = getStatsFilePath();
      if (!Files.exists(statsFile)) {
         return new StatsPersistenceManager.ClientStatsData();
      } else {
         try {
            StatsPersistenceManager.ClientStatsData var3;
            try (Reader reader = Files.newBufferedReader(statsFile, StandardCharsets.UTF_8)) {
               StatsPersistenceManager.ClientStatsData data = (StatsPersistenceManager.ClientStatsData)GSON.fromJson(
                  reader, StatsPersistenceManager.ClientStatsData.class
               );
               var3 = data != null ? data : new StatsPersistenceManager.ClientStatsData();
            }

            return var3;
         } catch (Exception var6) {
            return new StatsPersistenceManager.ClientStatsData();
         }
      }
   }

   public static class ClientStatsData {
      public long totalKills = 0L;
      public long totalDeaths = 0L;
      public long totalAssists = 0L;
      public long totalRevives = 0L;
      public double maxKillDistance = 0.0;
      public long maxKillStreak = 0L;
      public double totalDamageDealt = 0.0;
      public Map<String, Long> mobKillCounts = new ConcurrentHashMap<>();
      public Map<String, Long> playerKillCounts = new ConcurrentHashMap<>();
      public Map<String, Long> weaponUseCounts = new ConcurrentHashMap<>();
      public Map<String, Long> deathByPlayerCounts = new ConcurrentHashMap<>();
      public Map<String, Long> honorCounts = new ConcurrentHashMap<>();
   }
}
