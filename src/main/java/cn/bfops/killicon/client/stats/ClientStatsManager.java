package cn.bfops.killicon.client.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ClientStatsManager {
   private static StatsPersistenceManager.ClientStatsData statsData;
   private static long currentStreak;

   public static void init() {
      ensureLoaded();
   }

   public static void saveStats() {
      ensureLoaded();
      StatsPersistenceManager.saveStats(statsData);
   }

   public static void recordKill(LivingEntity victim) {
      ensureLoaded();
      String victimName = null;
      boolean isPlayer = false;
      if (victim instanceof Player) {
         victimName = victim.getName().getString();
         isPlayer = true;
      } else if (victim != null) {
         victimName = victim.getType().getDescription().getString();
      }

      recordGeneralKillStats(victimName, isPlayer);
   }

   public static void recordGeneralKillStats(String victimName, boolean isPlayer) {
      ensureLoaded();
      statsData.totalKills++;
      currentStreak++;
      statsData.maxKillStreak = Math.max(statsData.maxKillStreak, currentStreak);
      recordWeaponKill();
      recordVictimKill(victimName, isPlayer);
      saveStats();
   }

   public static void recordMobKill(String mobName) {
      ensureLoaded();
      if (!isBlank(mobName)) {
         statsData.mobKillCounts.merge(mobName, 1L, Long::sum);
         saveStats();
      }
   }

   public static void recordPlayerKill(String playerName) {
      ensureLoaded();
      if (!isBlank(playerName)) {
         statsData.playerKillCounts.merge(playerName, 1L, Long::sum);
         saveStats();
      }
   }

   public static void recordDeath() {
      ensureLoaded();
      statsData.totalDeaths++;
      currentStreak = 0L;
      saveStats();
   }

   public static void recordAssist() {
      ensureLoaded();
      statsData.totalAssists++;
      saveStats();
   }

   public static void recordRevive() {
      ensureLoaded();
      statsData.totalRevives++;
      saveStats();
   }

   public static void recordHonor(String honorId) {
      ensureLoaded();
      if (honorId != null && !honorId.isBlank()) {
         statsData.honorCounts.merge(honorId, 1L, Long::sum);
         saveStats();
      }
   }

   public static long getHonorCount(String honorId) {
      ensureLoaded();
      return honorId == null ? 0L : statsData.honorCounts.getOrDefault(honorId, 0L);
   }

   public static void recordDamage(double damage) {
      ensureLoaded();
      statsData.totalDamageDealt += damage;
      saveStats();
   }

   public static void recordKillDistance(double distance) {
      ensureLoaded();
      statsData.maxKillDistance = Math.max(statsData.maxKillDistance, distance);
      saveStats();
   }

   public static void recordDeathByPlayer(String playerName) {
      ensureLoaded();
      if (!isBlank(playerName)) {
         statsData.deathByPlayerCounts.merge(playerName, 1L, Long::sum);
         saveStats();
      }
   }

   public static long getTotalKills() {
      ensureLoaded();
      return statsData.totalKills;
   }

   public static long getTotalDeaths() {
      ensureLoaded();
      return statsData.totalDeaths;
   }

   public static long getTotalAssists() {
      ensureLoaded();
      return statsData.totalAssists;
   }

   public static long getTotalRevives() {
      ensureLoaded();
      return statsData.totalRevives;
   }

   public static long getCurrentStreak() {
      ensureLoaded();
      return currentStreak;
   }

   public static double getMaxKillDistance() {
      ensureLoaded();
      return statsData.maxKillDistance;
   }

   public static long getMaxKillStreak() {
      ensureLoaded();
      return statsData.maxKillStreak;
   }

   public static double getTotalDamageDealt() {
      ensureLoaded();
      return statsData.totalDamageDealt;
   }

   public static String getMostUsedWeapon() {
      ensureLoaded();
      return getMostFrequentKeys(statsData.weaponUseCounts);
   }

   public static List<ClientStatsManager.PlayerStat> getTopNemesisPlayers(int count) {
      ensureLoaded();
      return count <= 0
         ? Collections.emptyList()
         : statsData.deathByPlayerCounts
            .entrySet()
            .stream()
            .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
            .limit((long)count)
            .map(entry -> new ClientStatsManager.PlayerStat(entry.getKey(), entry.getValue()))
            .toList();
   }

   public static String getMostKilledMob() {
      ensureLoaded();
      return statsData.mobKillCounts.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse("无");
   }

   public static String getMostKilledPlayer() {
      ensureLoaded();
      return getMostFrequentKeys(statsData.playerKillCounts);
   }

   public static String getNemesis() {
      ensureLoaded();
      return getMostFrequentKeys(statsData.deathByPlayerCounts);
   }

   public static List<ClientStatsManager.MobStat> getTopKilledMobs(int count) {
      ensureLoaded();
      return count <= 0
         ? Collections.emptyList()
         : statsData.mobKillCounts
            .entrySet()
            .stream()
            .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
            .limit((long)count)
            .map(entry -> new ClientStatsManager.MobStat(entry.getKey(), entry.getValue()))
            .toList();
   }

   public static List<ClientStatsManager.PlayerStat> getTopKilledPlayers(int count) {
      ensureLoaded();
      return count <= 0
         ? Collections.emptyList()
         : statsData.playerKillCounts
            .entrySet()
            .stream()
            .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
            .limit((long)count)
            .map(entry -> new ClientStatsManager.PlayerStat(entry.getKey(), entry.getValue()))
            .toList();
   }

   public static List<ClientStatsManager.WeaponStat> getTopUsedWeapons(int count) {
      ensureLoaded();
      return count <= 0
         ? Collections.emptyList()
         : statsData.weaponUseCounts
            .entrySet()
            .stream()
            .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
            .limit((long)count)
            .map(entry -> new ClientStatsManager.WeaponStat(entry.getKey(), entry.getValue()))
            .toList();
   }

   private static void ensureLoaded() {
      if (statsData == null) {
         statsData = StatsPersistenceManager.loadStats();
         currentStreak = 0L;
      }
   }

   private static void recordWeaponKill() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String weaponName;
         if (mc.player.getVehicle() != null) {
            weaponName = mc.player.getVehicle().getDisplayName().getString();
         } else {
            ItemStack heldItem = mc.player.getMainHandItem();
            weaponName = heldItem.isEmpty() ? I18n.get("battlefield_ops.client.text.bare_hand", new Object[0]) : heldItem.getHoverName().getString();
         }

         if (!isBlank(weaponName)) {
            statsData.weaponUseCounts.merge(weaponName, 1L, Long::sum);
         }
      }
   }

   private static void recordVictimKill(String victimName, boolean isPlayer) {
      if (!isBlank(victimName)) {
         if (isPlayer) {
            statsData.playerKillCounts.merge(victimName, 1L, Long::sum);
         } else {
            statsData.mobKillCounts.merge(victimName, 1L, Long::sum);
         }
      }
   }

   private static boolean isBlank(String value) {
      return value == null || value.trim().isEmpty();
   }

   private static String getMostFrequentKeys(Map<String, Long> counts) {
      if (counts != null && !counts.isEmpty()) {
         long maxCount = counts.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
         if (maxCount <= 0L) {
            return "无";
         } else {
            List<String> keys = new ArrayList<>();

            for (Entry<String, Long> entry : counts.entrySet()) {
               if (entry != null && Objects.equals(entry.getValue(), maxCount) && !isBlank(entry.getKey())) {
                  keys.add(entry.getKey());
               }
            }

            return keys.isEmpty() ? "无" : String.join(" , ", keys);
         }
      } else {
         return "无";
      }
   }

   public static final class MobStat {
      public final String name;
      public final long count;

      public MobStat(String name, long count) {
         this.name = name;
         this.count = count;
      }
   }

   public static final class PlayerStat {
      public final String name;
      public final long count;

      public PlayerStat(String name, long count) {
         this.name = name;
         this.count = count;
      }
   }

   public static final class WeaponStat {
      public final String name;
      public final long count;

      public WeaponStat(String name, long count) {
         this.name = name;
         this.count = count;
      }
   }
}
