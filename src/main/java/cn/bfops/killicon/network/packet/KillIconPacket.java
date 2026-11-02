package cn.bfops.killicon.network.packet;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.impl.ComboIconRenderer;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.client.util.AceLagSimulator;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class KillIconPacket implements IPacket {
   private static final long VEHICLE_PRIORITY_DELAY_MS = 700L;
   private static final Deque<KillIconPacket.PendingTrigger> PENDING_TRIGGERS = new ArrayDeque<>();
   private static long lastVehicleDestroyTime = -1L;
   private final String category;
   private final String name;
   private final int killType;
   private final int comboCount;
   private final int victimId;
   private final double comboWindowSeconds;
   private final boolean hasHelmet;
   private final String customVictimName;
   private final boolean isVictimPlayer;
   private final boolean shouldRecordStats;
   private final float distance;
   private final float bonusMultiplier;
   private final float bonusScale;

   public KillIconPacket(String category, String name, int killType, int victimId) {
      this(category, name, killType, 0, victimId, -1.0, false, "", false, false);
   }

   public KillIconPacket(String category, String name, int killType, int comboCount, int victimId) {
      this(category, name, killType, comboCount, victimId, -1.0, false, "", false, false);
   }

   public KillIconPacket(String category, String name, int killType, int comboCount, int victimId, double comboWindowSeconds) {
      this(category, name, killType, comboCount, victimId, comboWindowSeconds, false, "", false, false, 0.0F);
   }

   public KillIconPacket(String category, String name, int killType, int comboCount, int victimId, double comboWindowSeconds, boolean hasHelmet) {
      this(category, name, killType, comboCount, victimId, comboWindowSeconds, hasHelmet, "", false, false, 0.0F);
   }

   public KillIconPacket(
      String category, String name, int killType, int comboCount, int victimId, double comboWindowSeconds, boolean hasHelmet, String customVictimName
   ) {
      this(category, name, killType, comboCount, victimId, comboWindowSeconds, hasHelmet, customVictimName, false, false, 0.0F);
   }

   public KillIconPacket(
      String category,
      String name,
      int killType,
      int comboCount,
      int victimId,
      double comboWindowSeconds,
      boolean hasHelmet,
      String customVictimName,
      boolean isVictimPlayer
   ) {
      this(category, name, killType, comboCount, victimId, comboWindowSeconds, hasHelmet, customVictimName, isVictimPlayer, false, 0.0F);
   }

   public KillIconPacket(
      String category,
      String name,
      int killType,
      int comboCount,
      int victimId,
      double comboWindowSeconds,
      boolean hasHelmet,
      String customVictimName,
      boolean isVictimPlayer,
      boolean shouldRecordStats
   ) {
      this(category, name, killType, comboCount, victimId, comboWindowSeconds, hasHelmet, customVictimName, isVictimPlayer, shouldRecordStats, 0.0F);
   }

   public KillIconPacket(
      String category,
      String name,
      int killType,
      int comboCount,
      int victimId,
      double comboWindowSeconds,
      boolean hasHelmet,
      String customVictimName,
      boolean isVictimPlayer,
      boolean shouldRecordStats,
      float distance
   ) {
      this(
         category,
         name,
         killType,
         comboCount,
         victimId,
         comboWindowSeconds,
         hasHelmet,
         customVictimName,
         isVictimPlayer,
         shouldRecordStats,
         distance,
         0.0F,
         0.0F
      );
   }

   public KillIconPacket(
      String category,
      String name,
      int killType,
      int comboCount,
      int victimId,
      double comboWindowSeconds,
      boolean hasHelmet,
      String customVictimName,
      boolean isVictimPlayer,
      boolean shouldRecordStats,
      float distance,
      float bonusMultiplier,
      float bonusScale
   ) {
      this.category = category;
      this.name = name;
      this.killType = killType;
      this.comboCount = comboCount;
      this.victimId = victimId;
      this.comboWindowSeconds = comboWindowSeconds;
      this.hasHelmet = hasHelmet;
      this.customVictimName = customVictimName == null ? "" : customVictimName;
      this.isVictimPlayer = isVictimPlayer;
      this.shouldRecordStats = shouldRecordStats;
      this.distance = distance;
      this.bonusMultiplier = bonusMultiplier;
      this.bonusScale = bonusScale;
   }

   public KillIconPacket(FriendlyByteBuf buffer) {
      this.category = buffer.readUtf();
      this.name = buffer.readUtf();
      this.killType = buffer.readInt();
      this.comboCount = buffer.readInt();
      this.victimId = buffer.readInt();
      this.comboWindowSeconds = buffer.readDouble();
      this.hasHelmet = buffer.readBoolean();
      this.customVictimName = buffer.readUtf();
      this.isVictimPlayer = buffer.readBoolean();
      this.shouldRecordStats = buffer.readBoolean();
      this.distance = buffer.readFloat();
      this.bonusMultiplier = buffer.readFloat();
      this.bonusScale = buffer.readFloat();
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.category);
      buffer.writeUtf(this.name);
      buffer.writeInt(this.killType);
      buffer.writeInt(this.comboCount);
      buffer.writeInt(this.victimId);
      buffer.writeDouble(this.comboWindowSeconds);
      buffer.writeBoolean(this.hasHelmet);
      buffer.writeUtf(this.customVictimName);
      buffer.writeBoolean(this.isVictimPlayer);
      buffer.writeBoolean(this.shouldRecordStats);
      buffer.writeFloat(this.distance);
      buffer.writeFloat(this.bonusMultiplier);
      buffer.writeFloat(this.bonusScale);
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(() -> {
         ComboIconRenderer.updateServerComboWindowSeconds(this.comboWindowSeconds);
         long now = System.currentTimeMillis();
         if (this.killType == 5) {
            lastVehicleDestroyTime = now;
            processTrigger(this, now);
         } else {
            long delayUntil = lastVehicleDestroyTime > 0L && now - lastVehicleDestroyTime < 700L ? lastVehicleDestroyTime + 700L : -1L;
            if (delayUntil > now) {
               PENDING_TRIGGERS.add(new KillIconPacket.PendingTrigger(this, delayUntil));
            } else {
               processTrigger(this, now);
            }
         }
      });
      context.setPacketHandled(true);
   }

   public static void processPendingTriggers() {
      if (!PENDING_TRIGGERS.isEmpty()) {
         long now = System.currentTimeMillis();

         while (!PENDING_TRIGGERS.isEmpty()) {
            KillIconPacket.PendingTrigger pending = PENDING_TRIGGERS.peekFirst();
            if (pending == null || pending.executeAt > now) {
               return;
            }

            PENDING_TRIGGERS.pollFirst();
            processTrigger(pending.packet, now);
         }
      }
   }

   public static void clearPending(){PENDING_TRIGGERS.clear();lastVehicleDestroyTime=-1L;}

   private static void processTrigger(KillIconPacket packet, long now) {
      boolean suppressValorantTrigger = "kill_icon".equals(packet.category) && "valorant".equals(packet.name) && (packet.killType == 4 || packet.killType == 5);
      if (!suppressValorantTrigger) {
         SoundTriggerManager.tryPlaySound(packet.category, packet.name, packet.killType, packet.comboCount, packet.hasHelmet, packet.isVictimPlayer);
      }

      String displayName = packet.customVictimName;
      if (packet.customVictimName != null && !packet.customVictimName.isEmpty() && !packet.isVictimPlayer) {
         try {
            displayName = I18n.get(packet.customVictimName, new Object[0]);
         } catch (Exception var6) {
         }
      }

      if (!suppressValorantTrigger) {
         HudElementManager.trigger(
            packet.category,
            packet.name,
            new IHudRenderer.TriggerContext(
               packet.killType, packet.victimId, packet.comboCount, displayName, packet.distance, packet.bonusMultiplier, packet.bonusScale
            )
         );
      }

      if (packet.shouldRecordStats && displayName != null && !displayName.isEmpty() && !isLocalPlayerVictim(packet)) {
         ClientStatsManager.recordGeneralKillStats(displayName, packet.isVictimPlayer);
      }

      AceLagSimulator.onKillEvent();
   }

   private static boolean isLocalPlayerVictim(KillIconPacket packet) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && player.getId() == packet.victimId;
   }

   private static final class PendingTrigger {
      private final KillIconPacket packet;
      private final long executeAt;

      private PendingTrigger(KillIconPacket packet, long executeAt) {
         this.packet = packet;
         this.executeAt = executeAt;
      }
   }
}
