package cn.bfops.killicon.client.render.effect;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class IconGlowRenderEffect {
   private static final float[][] OFFSETS = new float[][]{
      {-1.0F, -1.0F}, {0.0F, -1.0F}, {1.0F, -1.0F}, {-1.0F, 0.0F}, {1.0F, 0.0F}, {-1.0F, 1.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}
   };

   private IconGlowRenderEffect() {
   }

   public static boolean isEnabled(JsonObject config) {
      return config != null && config.has("enable_icon_glow") && config.get("enable_icon_glow").getAsBoolean();
   }

   public static int resolveColor(JsonObject config) {
      return parseColor(config, "color_icon_glow", 16777215);
   }

   public static float resolveIntensity(JsonObject config) {
      return config != null && config.has("icon_glow_intensity") ? Mth.clamp(config.get("icon_glow_intensity").getAsFloat(), 0.0F, 1.0F) : 0.45F;
   }

   public static float resolveSize(JsonObject config) {
      return config != null && config.has("icon_glow_size") ? Math.max(0.0F, config.get("icon_glow_size").getAsFloat()) : 4.0F;
   }

   public static void drawGlowFrame(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      int x,
      int y,
      int width,
      int height,
      int u,
      int v,
      int frameWidth,
      int frameHeight,
      int totalWidth,
      int totalHeight,
      float alpha,
      int rgb,
      float intensity,
      float size
   ) {
      if (texture != null && !(alpha <= 0.001F) && !(intensity <= 0.001F) && !(size <= 0.01F) && width > 0 && height > 0) {
         float glowAlpha = Mth.clamp(alpha * intensity, 0.0F, 1.0F);
         float outerSpread = Math.max(0.5F, size);
         float innerSpread = outerSpread * 0.55F;
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         float r = (float)(rgb >> 16 & 0xFF) / 255.0F;
         float g = (float)(rgb >> 8 & 0xFF) / 255.0F;
         float b = (float)(rgb & 0xFF) / 255.0F;

         for (float[] offset : OFFSETS) {
            RenderSystem.setShaderColor(r, g, b, glowAlpha * 0.16F);
            guiGraphics.blit(
               texture,
               Math.round((float)x + offset[0] * outerSpread),
               Math.round((float)y + offset[1] * outerSpread),
               width,
               height,
               (float)u,
               (float)v,
               frameWidth,
               frameHeight,
               totalWidth,
               totalHeight
            );
         }

         for (float[] offset : OFFSETS) {
            RenderSystem.setShaderColor(r, g, b, glowAlpha * 0.11F);
            guiGraphics.blit(
               texture,
               Math.round((float)x + offset[0] * innerSpread),
               Math.round((float)y + offset[1] * innerSpread),
               width,
               height,
               (float)u,
               (float)v,
               frameWidth,
               frameHeight,
               totalWidth,
               totalHeight
            );
         }

         RenderSystem.setShaderColor(r, g, b, glowAlpha * 0.09F);
         guiGraphics.blit(texture, x, y, width, height, (float)u, (float)v, frameWidth, frameHeight, totalWidth, totalHeight);
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   private static int parseColor(JsonObject config, String key, int fallback) {
      if (config != null && config.has(key)) {
         String hex = config.get(key).getAsString();
         if (hex != null && !hex.isEmpty()) {
            try {
               return Integer.parseInt(hex.replace("#", ""), 16) & 16777215;
            } catch (NumberFormatException var5) {
               return fallback;
            }
         } else {
            return fallback;
         }
      } else {
         return fallback;
      }
   }
}
