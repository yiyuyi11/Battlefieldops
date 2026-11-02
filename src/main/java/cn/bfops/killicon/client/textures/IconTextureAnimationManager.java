package cn.bfops.killicon.client.textures;

import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;

public class IconTextureAnimationManager {
   private static final RandomSource RANDOM = RandomSource.create();

   public static String resolveTexturePath(String path) {
      return path;
   }

   public static IconTextureAnimationManager.TextureFrame getTextureFrame(
      String presetId, String elementId, String textureKey, String textureFileName, long startTime, JsonObject config
   ) {
      ExternalTextureManager.TextureDimensions dims = ExternalTextureManager.getTextureDimensions(presetId, textureFileName);
      int totalW = dims.width > 0 ? dims.width : 64;
      int totalH = dims.height > 0 ? dims.height : 64;
      String prefix = "anim_" + textureKey + "_";
      boolean enabled = getBoolean(config, prefix + "enable_texture_animation", false);
      if (!enabled) {
         return new IconTextureAnimationManager.TextureFrame(0, 0, totalW, totalH, totalW, totalH);
      } else {
         int totalFrames = Math.max(1, getInt(config, prefix + "texture_animation_total_frames", 1));
         String orientation = getString(config, prefix + "texture_animation_orientation", "vertical");
         int interval = Math.max(1, getInt(config, prefix + "texture_animation_interval_ms", 100));
         boolean loop = getBoolean(config, prefix + "texture_animation_loop", false);
         String style = getString(config, prefix + "texture_animation_play_style", "sequential");
         int frameW;
         int frameH;
         if ("horizontal".equalsIgnoreCase(orientation)) {
            frameW = totalW / totalFrames;
            frameH = totalH;
         } else {
            frameW = totalW;
            frameH = totalH / totalFrames;
         }

         long elapsed = PreviewRenderTimeContext.currentTimeMillis() - startTime;
         if (elapsed < 0L) {
            elapsed = 0L;
         }

         int frameIndex = 0;
         long currentTick = elapsed / (long)interval;
         if ("random".equalsIgnoreCase(style)) {
            long seed = startTime ^ currentTick * -7046029254386353131L;
            seed = seed * 31L + (long)(textureKey != null ? textureKey.hashCode() : 0);
            seed = seed * 31L + (long)(elementId != null ? elementId.hashCode() : 0);
            RandomSource random = RandomSource.create(seed);
            frameIndex = random.nextInt(totalFrames);
         } else if ("reverse".equalsIgnoreCase(style)) {
            if (loop) {
               frameIndex = totalFrames - 1 - (int)(currentTick % (long)totalFrames);
            } else {
               frameIndex = totalFrames - 1 - (int)Math.min(currentTick, (long)(totalFrames - 1));
            }
         } else if (!"pingpong".equalsIgnoreCase(style)
            && !"ping-pong".equalsIgnoreCase(style)
            && !"ping_pong".equalsIgnoreCase(style)
            && !"round_trip".equalsIgnoreCase(style)) {
            if (loop) {
               frameIndex = (int)(currentTick % (long)totalFrames);
            } else {
               frameIndex = (int)Math.min(currentTick, (long)(totalFrames - 1));
            }
         } else if (totalFrames > 1) {
            int cycleLen = (totalFrames - 1) * 2;
            if (loop) {
               int cyclePos = (int)(currentTick % (long)cycleLen);
               if (cyclePos < totalFrames) {
                  frameIndex = cyclePos;
               } else {
                  frameIndex = cycleLen - cyclePos;
               }
            } else {
               long effectiveTick = Math.min(currentTick, (long)cycleLen);
               int cyclePos = (int)effectiveTick;
               if (cyclePos < totalFrames) {
                  frameIndex = cyclePos;
               } else {
                  frameIndex = cycleLen - cyclePos;
               }
            }
         } else {
            frameIndex = 0;
         }

         frameIndex = Mth.clamp(frameIndex, 0, totalFrames - 1);
         int u = 0;
         int v = 0;
         if ("horizontal".equalsIgnoreCase(orientation)) {
            u = frameIndex * frameW;
         } else {
            v = frameIndex * frameH;
         }

         return new IconTextureAnimationManager.TextureFrame(u, v, frameW, frameH, totalW, totalH);
      }
   }

   private static boolean getBoolean(JsonObject config, String key, boolean def) {
      return config != null && config.has(key) ? config.get(key).getAsBoolean() : def;
   }

   private static int getInt(JsonObject config, String key, int def) {
      return config != null && config.has(key) ? config.get(key).getAsInt() : def;
   }

   private static String getString(JsonObject config, String key, String def) {
      return config != null && config.has(key) ? config.get(key).getAsString() : def;
   }

   public static class TextureFrame {
      public final int u;
      public final int v;
      public final int width;
      public final int height;
      public final int totalWidth;
      public final int totalHeight;

      public TextureFrame(int u, int v, int width, int height, int totalWidth, int totalHeight) {
         this.u = u;
         this.v = v;
         this.width = width;
         this.height = height;
         this.totalWidth = totalWidth;
         this.totalHeight = totalHeight;
      }
   }
}
