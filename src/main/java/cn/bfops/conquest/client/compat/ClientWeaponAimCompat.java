package cn.bfops.conquest.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class ClientWeaponAimCompat {
   private static final float AIM_FADE_SECONDS = 0.1F;
   private static float aimFadeAlpha = 1.0F;

   private ClientWeaponAimCompat() {
   }

   public static void tickFade(Minecraft minecraft, float deltaSeconds) {
      boolean aiming = isAnyWeaponAiming(minecraft);
      float target = aiming ? 0.0F : 1.0F;
      float step = Mth.clamp(deltaSeconds / 0.1F, 0.0F, 1.0F);
      aimFadeAlpha = Mth.lerp(step, aimFadeAlpha, target);
   }

   public static float fadeAlpha() {
      return aimFadeAlpha;
   }

   public static boolean isAnyWeaponAiming(Minecraft minecraft) {
      if (minecraft == null || minecraft.player == null) {
         return false;
      } else {
         return isTaczAiming(minecraft.player) ? true : isSuperbWarfareAiming();
      }
   }

   private static boolean isTaczAiming(LocalPlayer player) {
      try {
         Class<?> operatorClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
         Method fromLocalPlayer = operatorClass.getMethod("fromLocalPlayer", LocalPlayer.class);
         Object operator = fromLocalPlayer.invoke(null, player);
         if (operator == null) {
            return false;
         } else {
            Method isAim = operatorClass.getMethod("isAim");
            return Boolean.TRUE.equals(isAim.invoke(operator));
         }
      } catch (Throwable var5) {
         return false;
      }
   }

   private static boolean isSuperbWarfareAiming() {
      try {
         Class<?> handlerClass = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler");
         Field zoomField = handlerClass.getField("zoom");
         return Boolean.TRUE.equals(zoomField.get(null));
      } catch (Throwable var2) {
         return false;
      }
   }
}
