package cn.bfops.killicon.client.render.effect;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.textures.ExternalTextureManager;

public final class IconGlowProcessor {
   private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

   private IconGlowProcessor() {
   }

   public static void clearCache() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null) {
         CACHE.clear();
      } else {
         for (ResourceLocation texture : CACHE.values()) {
            minecraft.getTextureManager().release(texture);
         }

         CACHE.clear();
      }
   }

   public static ResourceLocation getOrCreateProcessedTexture(
      String presetId, String texturePath, boolean accentTintEnabled, int accentColor, float brightness, float contrast
   ) {
      String cacheKey = String.format(
         Locale.ROOT, "%s:%s:%b:%06X:%.3f:%.3f", presetId, texturePath, accentTintEnabled, accentColor & 16777215, brightness, contrast
      );
      ResourceLocation cached = CACHE.get(cacheKey);
      if (cached != null) {
         return cached;
      } else {
         try {
            byte[] bytes = ExternalTextureManager.readTextureBytes(presetId, texturePath);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
               return null;
            } else {
               BufferedImage processed = new BufferedImage(source.getWidth(), source.getHeight(), 2);

               for (int y = 0; y < source.getHeight(); y++) {
                  for (int x = 0; x < source.getWidth(); x++) {
                     int argb = source.getRGB(x, y);
                     processed.setRGB(x, y, processTexturePixel(argb, accentTintEnabled, accentColor, brightness, contrast));
                  }
               }

               ResourceLocation processedTexture = registerBufferedTexture("processed", cacheKey, processed);
               CACHE.put(cacheKey, processedTexture);
               return processedTexture;
            }
         } catch (IOException var14) {
            return null;
         }
      }
   }

   public static int applyBrightnessContrastToRgb(int rgb, float brightness, float contrast) {
      int red = applyBrightnessContrastToChannel(rgb >> 16 & 0xFF, brightness, contrast);
      int green = applyBrightnessContrastToChannel(rgb >> 8 & 0xFF, brightness, contrast);
      int blue = applyBrightnessContrastToChannel(rgb & 0xFF, brightness, contrast);
      return red << 16 | green << 8 | blue;
   }

   private static ResourceLocation registerBufferedTexture(String prefix, String cacheKey, BufferedImage image) throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(image, "png", output);
      DynamicTexture texture = new DynamicTexture(NativeImage.read(new ByteArrayInputStream(output.toByteArray())));
      String dynamicName = "battlefield_ops_" + prefix + "_" + Integer.toHexString(cacheKey.hashCode());
      return Minecraft.getInstance().getTextureManager().register(dynamicName, texture);
   }

   private static int processTexturePixel(int argb, boolean accentTintEnabled, int accentColor, float brightness, float contrast) {
      int alpha = argb >>> 24 & 0xFF;
      if (alpha <= 0) {
         return 0;
      } else {
         int red = argb >> 16 & 0xFF;
         int green = argb >> 8 & 0xFF;
         int blue = argb & 0xFF;
         if (accentTintEnabled && isAccentPixel(argb)) {
            float luminance = (float)Math.max(red, Math.max(green, blue)) / 255.0F;
            red = Math.round((float)(accentColor >> 16 & 0xFF) * luminance);
            green = Math.round((float)(accentColor >> 8 & 0xFF) * luminance);
            blue = Math.round((float)(accentColor & 0xFF) * luminance);
         }

         red = applyBrightnessContrastToChannel(red, brightness, contrast);
         green = applyBrightnessContrastToChannel(green, brightness, contrast);
         blue = applyBrightnessContrastToChannel(blue, brightness, contrast);
         return alpha << 24 | red << 16 | green << 8 | blue;
      }
   }

   private static boolean isAccentPixel(int argb) {
      int alpha = argb >>> 24 & 0xFF;
      if (alpha < 16) {
         return false;
      } else {
         int red = argb >> 16 & 0xFF;
         int green = argb >> 8 & 0xFF;
         int blue = argb & 0xFF;
         float[] hsb = Color.RGBtoHSB(red, green, blue, null);
         float saturation = hsb[1];
         float brightness = hsb[2];
         return brightness >= 0.18F && brightness <= 0.98F && saturation >= 0.18F;
      }
   }

   private static int applyBrightnessContrastToChannel(int channel, float brightness, float contrast) {
      float normalized = (float)channel / 255.0F;
      float contrasted = (normalized - 0.5F) * contrast + 0.5F;
      float adjusted = contrasted * brightness;
      return Mth.clamp(Math.round(adjusted * 255.0F), 0, 255);
   }
}
