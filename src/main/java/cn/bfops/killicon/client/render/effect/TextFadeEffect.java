package cn.bfops.killicon.client.render.effect;

import net.minecraft.util.Mth;

public final class TextFadeEffect {
   public static final float MIN_ALPHA = 0.1F;

   private TextFadeEffect() {
   }

   public static float fadeAlpha(float fadeProgress, boolean blink) {
      float alpha;
      if (blink) {
         if (fadeProgress < 0.33333334F) {
            alpha = 1.0F - 2.4F * fadeProgress;
         } else if (fadeProgress < 0.6666667F) {
            alpha = 2.4F * fadeProgress - 0.6F;
         } else {
            alpha = 3.0F * (1.0F - fadeProgress);
         }
      } else {
         alpha = 1.0F - fadeProgress;
      }

      return Math.max(0.1F, Mth.clamp(alpha, 0.0F, 1.0F));
   }
}
