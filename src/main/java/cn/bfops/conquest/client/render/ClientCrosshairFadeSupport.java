package cn.bfops.conquest.client.render;

import net.minecraft.util.Mth;

public final class ClientCrosshairFadeSupport {
   private ClientCrosshairFadeSupport() {
   }

   public static float resolveWeightedAlpha(
      int width, int height, double screenX, double screenY, float nearAlpha, float farAlpha, float radiusPixels, float curve
   ) {
      float crosshairX = (float)width * 0.5F;
      float crosshairY = (float)height * 0.5F;
      float distance = Mth.sqrt((float)(Math.pow(screenX - (double)crosshairX, 2.0) + Math.pow(screenY - (double)crosshairY, 2.0)));
      float normalizedDistance = Mth.clamp(distance / radiusPixels, 0.0F, 1.0F);
      float proximity = 1.0F - normalizedDistance;
      float factor = (float)Math.pow((double)proximity, (double)curve);
      return Mth.lerp(factor, farAlpha, nearAlpha);
   }
}
