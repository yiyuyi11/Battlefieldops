package cn.bfops.killicon.client.render.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class SubtitleEntranceBackground {
   private static final float INITIAL_WIDTH = 2.0F;
   private static final float EDGE_GAP = 2.0F;

   private SubtitleEntranceBackground() {
   }

   public static int parseColor(String hex, int fallback) {
      if (hex == null) {
         return fallback;
      } else {
         String value = hex.trim();
         if (value.startsWith("#")) {
            value = value.substring(1);
         }

         if (value.length() != 6) {
            return fallback;
         } else {
            try {
               return Integer.parseInt(value, 16) & 16777215;
            } catch (NumberFormatException var4) {
               return fallback;
            }
         }
      }
   }

   public static void draw(
      GuiGraphics guiGraphics,
      long elapsedMs,
      float textLeft,
      float textRight,
      float midY,
      float textHeight,
      long fadeInMs,
      long sweepMs,
      long fadeOutMs,
      float peakTransparency,
      int rgb,
      boolean mirror
   ) {
      if (elapsedMs >= 0L && fadeInMs > 0L && sweepMs > 0L && fadeOutMs > 0L && elapsedMs < fadeInMs + sweepMs + fadeOutMs) {
         float maxWidth = textRight - textLeft + 4.0F;
         float peakAlpha = Mth.clamp(1.0F - peakTransparency, 0.0F, 1.0F);
         float alpha;
         float width;
         if (elapsedMs < fadeInMs) {
            alpha = peakAlpha * ((float)elapsedMs / (float)fadeInMs);
            width = 2.0F;
         } else if (elapsedMs < fadeInMs + sweepMs) {
            float t = (float)(elapsedMs - fadeInMs) / (float)sweepMs;
            float eased = 1.0F - (float)Math.pow((double)(1.0F - Mth.clamp(t, 0.0F, 1.0F)), 3.0);
            alpha = peakAlpha;
            width = Mth.lerp(eased, 2.0F, maxWidth);
         } else {
            float p = (float)(elapsedMs - fadeInMs - sweepMs) / (float)fadeOutMs;
            alpha = peakAlpha * (1.0F - Mth.clamp(p, 0.0F, 1.0F));
            width = maxWidth;
         }

         if (!(alpha <= 0.001F) && !(width <= 0.001F)) {
            float left;
            float right;
            if (mirror) {
               right = textRight + 2.0F;
               left = right - width;
            } else {
               left = textLeft - 2.0F;
               right = left + width;
            }

            float top = midY - (textHeight + 2.0F) / 2.0F;
            float bottom = midY + (textHeight + 2.0F) / 2.0F;
            int argb = rgb & 16777215 | (int)(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24;
            int l = Math.round(left);
            int r = Math.round(right);
            int t = Math.round(top);
            int b = Math.round(bottom);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.fill(l, t, r, b, argb);
            RenderSystem.disableBlend();
         }
      }
   }
}
