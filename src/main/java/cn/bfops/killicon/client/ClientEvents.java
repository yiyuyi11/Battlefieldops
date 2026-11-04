package cn.bfops.killicon.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.gui.MainConfigScreen;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.impl.ComboSubtitleRenderer;
import cn.bfops.killicon.client.util.AceLagSimulator;
import cn.bfops.killicon.network.packet.KillIconPacket;

public class ClientEvents {
   private static boolean wasInGame = false;
   private static Class<?> taczGunSoundClass;
   private static boolean taczGunSoundResolved = false;
   private static final ResourceLocation TACZ_KILL_SOUND = new ResourceLocation("tacz", "kill");

   public static void onClientTickEnd() {
      while (KeyBindings.OPEN_CONFIG.consumeClick()) {
         Minecraft.getInstance().setScreen(new MainConfigScreen(Minecraft.getInstance().screen));
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         wasInGame = false;
      } else if (!wasInGame) {
         if (ClientConfigManager.isEnableAceLag()) {
            HudElementManager.trigger("global", "ace_logo", IHudRenderer.TriggerContext.of(0, -1));
         }

         wasInGame = true;
      }

      KillIconPacket.processPendingTriggers();
      AceLagSimulator.onClientTick();
   }

   public static void onClientPlayerLogout() {
      ComboSubtitleRenderer.getInstance().onPlayerLogout();
   }

   public static void onLivingDeath(Entity deadEntity) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && deadEntity == mc.player) {
         ComboSubtitleRenderer.getInstance().onPlayerDeath();
      }
   }

   public static boolean shouldMutePlaySound(Object sound) {
      if (!ClientConfigManager.isDisableTaczKillSound()) {
         return false;
      } else if (!ClientBridge.loader().isModLoaded("tacz")) {
         return false;
      } else {
         if (!taczGunSoundResolved) {
            taczGunSoundResolved = true;

            try {
               taczGunSoundClass = Class.forName("com.tacz.guns.client.sound.GunSoundInstance");
            } catch (Exception var5) {
            }
         }

         if (taczGunSoundClass == null) {
            return false;
         } else if (sound != null && taczGunSoundClass.isInstance(sound)) {
            try {
               Method method = taczGunSoundClass.getMethod("getRegistryName");
               if (method.invoke(sound) instanceof ResourceLocation location && TACZ_KILL_SOUND.equals(location)) {
                  return true;
               }
            } catch (Exception var4) {
            }

            return false;
         } else {
            return false;
         }
      }
   }
}
