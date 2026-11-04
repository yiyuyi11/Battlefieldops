package cn.bfops.killicon.client.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.stats.ClientStatsManager;

public class AceLagSimulator {
   private static final Random RANDOM = new Random();
   private static final Deque<Long> killTimestamps = new ArrayDeque<>();
   private static final int MAX_HISTORY = 10;
   private static long lastLagTime = 0L;
   private static final long MIN_LAG_INTERVAL = 2000L;
   private static long nextAmbientLagTime = 0L;
   private static long nextDiskLagTime = 0L;

   public static void onKillEvent() {
      if (ClientConfigManager.isEnableAceLag()) {
         long now = System.currentTimeMillis();
         killTimestamps.addLast(now);

         while (killTimestamps.size() > 10) {
            killTimestamps.remove(0);
         }

         while (!killTimestamps.isEmpty() && now - killTimestamps.peekFirst() > 10000L) {
            killTimestamps.remove(0);
         }

         int recentKills = killTimestamps.size();
         long streak = ClientStatsManager.getCurrentStreak();
         double streakScore = Math.min((double)streak * 0.05, 0.5);
         double densityScore = (double)recentKills * 0.1;
         double intensity = streakScore + densityScore;
         int configIntensity = ClientConfigManager.getAceLagIntensity();
         double intensityFactor = Math.min(1.0, (double)configIntensity / 100.0);
         double triggerChance = Math.min(intensity * (0.25 + intensityFactor * 0.75), 0.95);
         if (RANDOM.nextDouble() < triggerChance) {
            triggerLag(intensity, intensityFactor);
         }
      }
   }

   public static void onClientTick() {
      if (ClientConfigManager.isEnableAceLag()) {
         int configIntensity = ClientConfigManager.getAceLagIntensity();
         double intensityFactor = Math.min(1.0, (double)configIntensity / 100.0);
         long now = System.currentTimeMillis();
         long minInterval = (long)(300.0 + 1700.0 * (1.0 - intensityFactor));
         long maxInterval = (long)(900.0 + 2400.0 * (1.0 - intensityFactor));
         if (maxInterval < minInterval) {
            maxInterval = minInterval;
         }

         if (nextAmbientLagTime == 0L) {
            nextAmbientLagTime = now + minInterval + (long)RANDOM.nextInt((int)(maxInterval - minInterval + 1L));
         }

         if (now >= nextAmbientLagTime) {
            long base = 10L + (long)(120.0 * intensityFactor);
            long extra = (long)RANDOM.nextInt((int)(40.0 + 160.0 * intensityFactor));
            long sleepTime = base + extra;
            sleepTime = Math.min(sleepTime, (long)(400.0 + 2600.0 * intensityFactor));
            sleepMs(sleepTime);
            nextAmbientLagTime = now + minInterval + (long)RANDOM.nextInt((int)(maxInterval - minInterval + 1L));
         }

         if (configIntensity > 50) {
            long minDiskInterval = (long)(700.0 + 2200.0 * (1.0 - intensityFactor));
            long maxDiskInterval = (long)(1400.0 + 3500.0 * (1.0 - intensityFactor));
            if (maxDiskInterval < minDiskInterval) {
               maxDiskInterval = minDiskInterval;
            }

            if (nextDiskLagTime == 0L) {
               nextDiskLagTime = now + minDiskInterval + (long)RANDOM.nextInt((int)(maxDiskInterval - minDiskInterval + 1L));
            }

            if (now >= nextDiskLagTime) {
               long duration = (long)(80.0 + 400.0 * intensityFactor + (double)RANDOM.nextInt((int)(120.0 + 480.0 * intensityFactor)));
               simulateDiskScan(duration);
               nextDiskLagTime = now + minDiskInterval + (long)RANDOM.nextInt((int)(maxDiskInterval - minDiskInterval + 1L));
            }
         }
      }
   }

   private static void triggerLag(double intensity, double intensityFactor) {
      long now = System.currentTimeMillis();
      long currentCooldown = (long)(2000.0 / Math.max(0.2, 0.6 + intensityFactor * 2.5));
      if (now - lastLagTime >= currentCooldown) {
         double roll = RANDOM.nextDouble();
         long sleepTime = 0L;
         if (intensity > 0.8 && roll < 0.15 + intensityFactor * 0.6) {
            sleepTime = (long)(400 + RANDOM.nextInt(900));
         } else if (intensity > 0.4 && roll < 0.35 + intensityFactor * 0.5) {
            sleepTime = (long)(160 + RANDOM.nextInt(200));
         } else {
            sleepTime = (long)(40 + RANDOM.nextInt(80));
         }

         sleepTime = (long)((double)sleepTime * (0.6 + intensityFactor * 2.8));
         sleepTime = Math.min(sleepTime, 5000L);
         sleepMs(sleepTime);
         if (intensityFactor > 0.5) {
            long duration = (long)(120.0 + 500.0 * intensityFactor + (double)RANDOM.nextInt((int)(160.0 + 500.0 * intensityFactor)));
            simulateDiskScan(duration);
         }

         lastLagTime = System.currentTimeMillis();
      }
   }

   private static void sleepMs(long sleepTime) {
      if (sleepTime > 0L) {
         try {
            Thread.sleep(sleepTime);
         } catch (InterruptedException var3) {
         }
      }
   }

   private static void simulateDiskScan(long durationMs) {
      sleepMs(durationMs);
   }
}
