package cn.bfops.killicon.client.render.impl;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.scores.Team;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconGlowRenderEffect;
import cn.bfops.killicon.client.render.effect.IconRingEffect;
import cn.bfops.killicon.client.textures.ExternalTextureManager;

public class CardBarRenderer implements IHudRenderer {
   @Override public void resetPreview(){flashStartTime=-1;waveSystem.waves.clear();waveSystem.pendingSpawns.clear();}

   private static final Map<ResourceLocation, Float> ASPECT_RATIO_CACHE = new ConcurrentHashMap<>();
   private static final float DEFAULT_ASPECT_RATIO = 1.0F;
   private static final int BASE_LOGICAL_HEIGHT = 40;
   private static final float CENTER_FLASH_SPEED_MULTIPLIER = 3.0F;
   private long flashStartTime = -1L;
   private final CardBarRenderer.WaveEffectSystem waveSystem = new CardBarRenderer.WaveEffectSystem();
   private final IconRingEffect ringEffect = new IconRingEffect();
   private JsonObject currentConfig;

   public CardBarRenderer() {
      this.ringEffect.setScale(1.0F);
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "card_bar");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.currentConfig = config;
         float scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         int xOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         int yOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
         String screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         String team = config.has("team") ? config.get("team").getAsString() : "ct";
         boolean dynamicCardStyle = config.has("dynamic_card_style") && config.get("dynamic_card_style").getAsBoolean();
         float animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 0.2F;
         Minecraft mc = Minecraft.getInstance();
         if (dynamicCardStyle && mc.player != null) {
            Team pt = mc.player.getTeam();
            if (pt != null) {
               ChatFormatting color = pt.getColor();
               if (color == ChatFormatting.BLUE || color == ChatFormatting.AQUA || color == ChatFormatting.DARK_AQUA || color == ChatFormatting.DARK_BLUE) {
                  team = "ct";
               } else if (color == ChatFormatting.YELLOW || color == ChatFormatting.GOLD || color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
                  team = "t";
               }
            }
         }

         boolean showLight = config.has("show_light") && config.get("show_light").getAsBoolean();
         float lightWidth = config.has("light_width") ? config.get("light_width").getAsFloat() : 300.0F;
         float lightHeight = config.has("light_height") ? config.get("light_height").getAsFloat() : 20.0F;
         String lightColorCt = config.has("color_light_ct") ? config.get("color_light_ct").getAsString() : "9cc1eb";
         String lightColorT = config.has("color_light_t") ? config.get("color_light_t").getAsString() : "d9ac5b";
         boolean enableIconGlow = IconGlowRenderEffect.isEnabled(config);
         int iconGlowColor = IconGlowRenderEffect.resolveColor(config);
         float iconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
         float iconGlowSize = IconGlowRenderEffect.resolveSize(config);
         boolean isT = "t".equalsIgnoreCase(team);
         String textureKey = isT ? "bar_t" : "bar_ct";
         float focusMultiplier = PreviewTextureFocusContext.alphaMultiplier("kill_icon/card_bar", textureKey);
         String textureName = ElementTextureDefinition.getSelectedTextureFileName(ConfigManager.getCurrentPresetId(), "kill_icon/card_bar", textureKey, config);
         ResourceLocation texture = ExternalTextureManager.getTexture(textureName);
         if (texture != null) {
            float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
            float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
            int drawHeight = Math.round(40.0F * frameHeightRatio);
            int drawWidth = Math.round(40.0F * frameWidthRatio);
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            float centerX = (float)ScreenAnchor.resolveCenterX(screenAnchor, xOffset, screenWidth);
            float centerY = (float)ScreenAnchor.resolveCenterY(screenAnchor, yOffset, screenHeight);
            this.renderInternal(
               guiGraphics,
               partialTick,
               centerX,
               centerY,
               scale,
               isT,
               texture,
               drawWidth,
               drawHeight,
               showLight,
               lightWidth,
               lightHeight,
               lightColorCt,
               lightColorT,
               animationDuration,
               enableIconGlow,
               iconGlowColor,
               iconGlowIntensity,
               iconGlowSize,
               focusMultiplier
            );
         }
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "card_bar");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.currentConfig = config;
         float scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         String team = config.has("team") ? config.get("team").getAsString() : "ct";
         boolean dynamicCardStyle = config.has("dynamic_card_style") && config.get("dynamic_card_style").getAsBoolean();
         float animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 0.2F;
         Minecraft mc = Minecraft.getInstance();
         if (dynamicCardStyle && mc.player != null) {
            Team pt = mc.player.getTeam();
            if (pt != null) {
               ChatFormatting color = pt.getColor();
               if (color == ChatFormatting.BLUE || color == ChatFormatting.AQUA || color == ChatFormatting.DARK_AQUA || color == ChatFormatting.DARK_BLUE) {
                  team = "ct";
               } else if (color == ChatFormatting.YELLOW || color == ChatFormatting.GOLD || color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
                  team = "t";
               }
            }
         }

         boolean showLight = config.has("show_light") && config.get("show_light").getAsBoolean();
         float lightWidth = config.has("light_width") ? config.get("light_width").getAsFloat() : 300.0F;
         float lightHeight = config.has("light_height") ? config.get("light_height").getAsFloat() : 20.0F;
         String lightColorCt = config.has("color_light_ct") ? config.get("color_light_ct").getAsString() : "9cc1eb";
         String lightColorT = config.has("color_light_t") ? config.get("color_light_t").getAsString() : "d9ac5b";
         boolean enableIconGlow = IconGlowRenderEffect.isEnabled(config);
         int iconGlowColor = IconGlowRenderEffect.resolveColor(config);
         float iconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
         float iconGlowSize = IconGlowRenderEffect.resolveSize(config);
         String textureName = "killicon_card_bar_ct.png";
         boolean isT = "t".equalsIgnoreCase(team);
         if (isT) {
            textureName = "killicon_card_bar_t.png";
         }

         ResourceLocation texture = ExternalTextureManager.getTexture(textureName);
         if (texture != null) {
            String textureKey = isT ? "bar_t" : "bar_ct";
            float focusMultiplier = PreviewTextureFocusContext.alphaMultiplier("kill_icon/card_bar", textureKey);
            float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
            float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
            int drawHeight = Math.round(40.0F * frameHeightRatio);
            int drawWidth = Math.round(40.0F * frameWidthRatio);
            this.renderInternal(
               guiGraphics,
               partialTick,
               centerX,
               centerY,
               scale,
               isT,
               texture,
               drawWidth,
               drawHeight,
               showLight,
               lightWidth,
               lightHeight,
               lightColorCt,
               lightColorT,
               animationDuration,
               enableIconGlow,
               iconGlowColor,
               iconGlowIntensity,
               iconGlowSize,
               focusMultiplier
            );
         }
      }
   }

   public void renderPreviewAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY, JsonObject config) {
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.currentConfig = config;
         float scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         String team = config.has("team") ? config.get("team").getAsString() : "ct";
         boolean dynamicCardStyle = config.has("dynamic_card_style") && config.get("dynamic_card_style").getAsBoolean();
         float animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 0.2F;
         Minecraft mc = Minecraft.getInstance();
         if (dynamicCardStyle && mc.player != null) {
            Team pt = mc.player.getTeam();
            if (pt != null) {
               ChatFormatting color = pt.getColor();
               if (color == ChatFormatting.BLUE || color == ChatFormatting.AQUA || color == ChatFormatting.DARK_AQUA || color == ChatFormatting.DARK_BLUE) {
                  team = "ct";
               } else if (color == ChatFormatting.YELLOW || color == ChatFormatting.GOLD || color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
                  team = "t";
               }
            }
         }

         boolean showLight = config.has("show_light") && config.get("show_light").getAsBoolean();
         float lightWidth = config.has("light_width") ? config.get("light_width").getAsFloat() : 300.0F;
         float lightHeight = config.has("light_height") ? config.get("light_height").getAsFloat() : 20.0F;
         String lightColorCt = config.has("color_light_ct") ? config.get("color_light_ct").getAsString() : "9cc1eb";
         String lightColorT = config.has("color_light_t") ? config.get("color_light_t").getAsString() : "d9ac5b";
         boolean enableIconGlow = IconGlowRenderEffect.isEnabled(config);
         int iconGlowColor = IconGlowRenderEffect.resolveColor(config);
         float iconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
         float iconGlowSize = IconGlowRenderEffect.resolveSize(config);
         String textureName = "killicon_card_bar_ct.png";
         boolean isT = "t".equalsIgnoreCase(team);
         if (isT) {
            textureName = "killicon_card_bar_t.png";
         }

         ResourceLocation texture = ExternalTextureManager.getTexture(textureName);
         if (texture != null) {
            String textureKey = isT ? "bar_t" : "bar_ct";
            float focusMultiplier = PreviewTextureFocusContext.alphaMultiplier("kill_icon/card_bar", textureKey);
            float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
            float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
            int drawHeight = Math.round(40.0F * frameHeightRatio);
            int drawWidth = Math.round(40.0F * frameWidthRatio);
            this.renderInternal(
               guiGraphics,
               partialTick,
               centerX,
               centerY,
               scale,
               isT,
               texture,
               drawWidth,
               drawHeight,
               showLight,
               lightWidth,
               lightHeight,
               lightColorCt,
               lightColorT,
               animationDuration,
               enableIconGlow,
               iconGlowColor,
               iconGlowIntensity,
               iconGlowSize,
               focusMultiplier
            );
         }
      }
   }

   private void renderInternal(
      GuiGraphics guiGraphics,
      float partialTick,
      float centerX,
      float centerY,
      float scale,
      boolean isT,
      ResourceLocation texture,
      int drawWidth,
      int drawHeight,
      boolean showLight,
      float lightWidth,
      float lightHeight,
      String lightColorCt,
      String lightColorT,
      float animationDuration,
      boolean enableIconGlow,
      int iconGlowColor,
      float iconGlowIntensity,
      float iconGlowSize,
      float focusMultiplier
   ) {
      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();
      poseStack.translate(centerX, centerY, 10.0F);
      poseStack.scale(scale, scale, 1.0F);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      float flashAlpha = 0.0F;
      long animDurMs = (long)(animationDuration * 1000.0F);
      long flashAnimDurMs = Math.max(1L, (long)((float)animDurMs / 3.0F));
      if (this.flashStartTime != -1L) {
         long elapsed = PreviewRenderTimeContext.currentTimeMillis() - this.flashStartTime;
         long flashHold = flashAnimDurMs / 2L;
         long flashFade = flashAnimDurMs * 4L;
         if (elapsed < flashHold) {
            flashAlpha = 1.0F;
         } else {
            long flashElapsed = elapsed - flashHold;
            if (flashElapsed < flashFade) {
               flashAlpha = 1.0F - (float)flashElapsed / (float)flashFade;
            } else {
               this.flashStartTime = -1L;
            }
         }
      }

      if (showLight) {
         int baseColorInt = this.parseColor(isT ? lightColorT : lightColorCt);
         int br = baseColorInt >> 16 & 0xFF;
         int bg = baseColorInt >> 8 & 0xFF;
         int bb = baseColorInt & 0xFF;
         int mr = (int)((float)br + (float)(255 - br) * flashAlpha);
         int mg = (int)((float)bg + (float)(255 - bg) * flashAlpha);
         int mb = (int)((float)bb + (float)(255 - bb) * flashAlpha);
         String mixedColorHex = String.format("%02X%02X%02X", mr, mg, mb);
         this.renderLightEffect(guiGraphics, lightWidth, lightHeight, mixedColorHex, 1.0F);
         this.ringEffect.render(guiGraphics, 0.0F, 0.0F, PreviewRenderTimeContext.currentTimeMillis());
      }

      if (showLight) {
         this.waveSystem.updateAndRender(guiGraphics, lightWidth, isT ? lightColorT : lightColorCt, animationDuration, flashAlpha);
      }

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (enableIconGlow) {
         IconGlowRenderEffect.drawGlowFrame(
            guiGraphics,
            texture,
            -drawWidth / 2,
            -drawHeight / 2,
            drawWidth,
            drawHeight,
            0,
            0,
            drawWidth,
            drawHeight,
            drawWidth,
            drawHeight,
            focusMultiplier,
            iconGlowColor,
            iconGlowIntensity,
            iconGlowSize
         );
      }

      guiGraphics.blit(texture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight);
      if (flashAlpha > 0.01F) {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flashAlpha * focusMultiplier);
         guiGraphics.blit(texture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight);
         if (flashAlpha > 0.5F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (flashAlpha - 0.5F) * 2.0F * focusMultiplier);
            guiGraphics.blit(texture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight);
         }

         RenderSystem.defaultBlendFunc();
      }

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      poseStack.popPose();
   }

   private float getTextureAspectRatio(ResourceLocation texture) {
      if (ASPECT_RATIO_CACHE.containsKey(texture)) {
         return ASPECT_RATIO_CACHE.get(texture);
      } else {
         try {
            Resource resource = (Resource)Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
            if (resource != null) {
               try (InputStream inputStream = resource.open()) {
                  BufferedImage image = ImageIO.read(inputStream);
                  if (image != null && image.getHeight() > 0) {
                     float ratio = (float)image.getWidth() / (float)image.getHeight();
                     ASPECT_RATIO_CACHE.put(texture, ratio);
                     return ratio;
                  }
               }
            }
         } catch (Exception var9) {
         }

         ASPECT_RATIO_CACHE.put(texture, 1.0F);
         return 1.0F;
      }
   }

   private void renderLightEffect(GuiGraphics guiGraphics, float width, float height, String colorHex, float alphaMultiplier) {
      int color = this.parseColor(colorHex);
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      float halfWidth = width / 2.0F;
      float halfHeight = height / 2.0F;
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer;
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      buffer = tesselator.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
      int segments = 50;
      float coreRatio = 0.5F;

      for (int i = 0; i <= segments; i++) {
         float t = (float)i / (float)segments;
         float x = (t - 0.5F) * 2.0F * halfWidth;
         float dist = Math.abs(x) / halfWidth;
         float alphaVal;
         if (dist <= coreRatio) {
            alphaVal = 1.0F;
         } else {
            float decayProgress = (dist - coreRatio) / (1.0F - coreRatio);
            alphaVal = (float)Math.pow((double)(1.0F - decayProgress), 2.0);
         }

         int a = (int)(alphaVal * 255.0F * alphaMultiplier);
         buffer.addVertex(guiGraphics.pose().last().pose(), x, -halfHeight, 0.0F).setColor(r, g, b, a);
         buffer.addVertex(guiGraphics.pose().last().pose(), x, halfHeight, 0.0F).setColor(r, g, b, a);
      }

      BufferUploader.drawWithShader(buffer.buildOrThrow());
   }

   private int parseColor(String hex) {
      try {
         return Integer.parseInt(hex.replace("#", ""), 16);
      } catch (NumberFormatException var3) {
         return 16777215;
      }
   }

   private float resolveFrameRatio(String textureKey, String suffixKey) {
      if (this.currentConfig != null && textureKey != null) {
         String key = "anim_" + textureKey + "_" + suffixKey;
         if (!this.currentConfig.has(key)) {
            return 1.0F;
         } else {
            float value = this.currentConfig.get(key).getAsFloat();
            return value > 0.0F ? value : 1.0F;
         }
      } else {
         return 1.0F;
      }
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "card_bar");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         if (context.type() != 4) {
            this.flashStartTime = PreviewRenderTimeContext.currentTimeMillis();
            if (config != null) {
               String lightColorCt = config.has("color_light_ct") ? config.get("color_light_ct").getAsString() : "9cc1eb";
               String lightColorT = config.has("color_light_t") ? config.get("color_light_t").getAsString() : "d9ac5b";
               Minecraft mc = Minecraft.getInstance();
               String team = config.has("team") ? config.get("team").getAsString() : "ct";
               boolean dynamicCardStyle = config.has("dynamic_card_style") && config.get("dynamic_card_style").getAsBoolean();
               if (dynamicCardStyle && mc.player != null) {
                  Team pt = mc.player.getTeam();
                  if (pt != null) {
                     ChatFormatting color = pt.getColor();
                     if (color == ChatFormatting.BLUE || color == ChatFormatting.AQUA || color == ChatFormatting.DARK_AQUA || color == ChatFormatting.DARK_BLUE
                        )
                      {
                        team = "ct";
                     } else if (color == ChatFormatting.YELLOW || color == ChatFormatting.GOLD || color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
                        team = "t";
                     }
                  }
               }

               boolean isT = "t".equalsIgnoreCase(team);
               int color = this.parseColor(isT ? lightColorT : lightColorCt);
               int r = color >> 16 & 0xFF;
               int g = color >> 8 & 0xFF;
               int b = color & 0xFF;
               int r2 = (r + 255) / 2;
               int g2 = (g + 255) / 2;
               int b2 = (b + 255) / 2;
               int explosionColor2 = r2 << 16 | g2 << 8 | b2;
               this.ringEffect.trigger(PreviewRenderTimeContext.currentTimeMillis(), true, context.type(), color, explosionColor2, color);
            }

            int combo = context.comboCount();
            int waveCount = Math.min(combo, 5);
            if (combo >= 6) {
               waveCount = 1;
            }

            if (waveCount > 0) {
               this.waveSystem.trigger(waveCount);
            }
         }
      }
   }

   private static class WaveEffectSystem {
      private final List<CardBarRenderer.WaveEffectSystem.Wave> waves = new ArrayList<>();
      private final Queue<Long> pendingSpawns = new LinkedList<>();
      private long lastSpawnTime = 0L;
      private static final float WAVE_RADIUS_RATIO = 0.15F;
      private static final float MAX_STRETCH_PIXELS = 25.0F;

      public void trigger(int pairCount) {
         long now = PreviewRenderTimeContext.currentTimeMillis();

         for (int i = 0; i < pairCount; i++) {
            this.pendingSpawns.offer(now);
         }
      }

      public void updateAndRender(GuiGraphics guiGraphics, float width, String colorHex, float animationDuration, float flashAlpha) {
         long now = PreviewRenderTimeContext.currentTimeMillis();
         long interval = (long)(animationDuration * 1000.0F / 2.0F);
         if (!this.pendingSpawns.isEmpty()) {
            if (now - this.lastSpawnTime >= interval) {
               this.pendingSpawns.poll();
               this.waves.add(new CardBarRenderer.WaveEffectSystem.Wave(now, -1));
               this.waves.add(new CardBarRenderer.WaveEffectSystem.Wave(now, 1));
               this.lastSpawnTime = now;
            }
         } else if (now - this.lastSpawnTime > interval * 2L) {
            this.lastSpawnTime = now - interval;
         }

         long waveLifeTime = (long)(animationDuration * 1000.0F * 4.0F);
         Iterator<CardBarRenderer.WaveEffectSystem.Wave> it = this.waves.iterator();

         while (it.hasNext()) {
            if (now - it.next().startTime > waveLifeTime) {
               it.remove();
            }
         }

         if (!this.waves.isEmpty()) {
            this.renderWaves(guiGraphics, width, colorHex, animationDuration, flashAlpha, now, waveLifeTime);
         }
      }

      private void renderWaves(GuiGraphics guiGraphics, float width, String colorHex, float animationDuration, float flashAlpha, long now, long lifeTime) {
         int color = this.parseColor(colorHex);
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         int fr = 255;
         int fg = 255;
         int fb = 255;
         float halfWidth = width / 2.0F;
         float waveRadius = width * 0.15F;
         float speed = halfWidth / (float)lifeTime;
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder buffer;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.disableDepthTest();
         buffer = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

         for (float x = -halfWidth; x <= halfWidth; x++) {
            float distFromCenter = Math.abs(x);
            float maxDistRatio = distFromCenter / halfWidth;
            float totalInfluence = 0.0F;

            for (CardBarRenderer.WaveEffectSystem.Wave w : this.waves) {
               long elapsed = now - w.startTime;
               float wavePos = (float)w.direction * speed * (float)elapsed;
               float distToWave = Math.abs(x - wavePos);
               if (distToWave < waveRadius) {
                  float influence = 1.0F - distToWave / waveRadius;
                  influence = (float)Math.pow((double)influence, 2.0);
                  totalInfluence += influence;
               }
            }

            if (!(totalInfluence <= 0.001F)) {
               float fadeMask = 1.0F - maxDistRatio * 0.99F;
               boolean isSpecial = Math.abs(x) % 20.0F < 1.0F;
               float alphaMultiplier = isSpecial ? 1.5F : 1.0F;
               float lengthMultiplier = isSpecial ? 1.5F : 1.0F;
               float finalAlpha = totalInfluence * fadeMask * alphaMultiplier;
               finalAlpha = Math.min(finalAlpha, 1.0F);
               float randomLen = (float)(Math.sin((double)(x * 123.456F)) * 2.5 + 2.5);
               float stretch = totalInfluence * 25.0F * fadeMask * lengthMultiplier + randomLen;
               if (!(finalAlpha < 0.01F)) {
                  int aBottom = (int)(finalAlpha * 255.0F);
                  int aTop = 0;
                  float yBottom = 0.5F;
                  float yTop = -0.5F - stretch;
                  float xRight = x + 1.0F;
                  buffer.addVertex(guiGraphics.pose().last().pose(), xRight, yBottom, 0.0F).setColor(r, g, b, aBottom);
                  buffer.addVertex(guiGraphics.pose().last().pose(), xRight, yTop, 0.0F).setColor(r, g, b, aTop);
                  buffer.addVertex(guiGraphics.pose().last().pose(), x, yTop, 0.0F).setColor(r, g, b, aTop);
                  buffer.addVertex(guiGraphics.pose().last().pose(), x, yBottom, 0.0F).setColor(r, g, b, aBottom);
                  if (flashAlpha > 0.01F) {
                     int faBottom = (int)(finalAlpha * flashAlpha * 255.0F);
                     int faTop = 0;
                     if (faBottom > 0) {
                        buffer.addVertex(guiGraphics.pose().last().pose(), xRight, yBottom, 0.0F).setColor(fr, fg, fb, faBottom);
                        buffer.addVertex(guiGraphics.pose().last().pose(), xRight, yTop, 0.0F).setColor(fr, fg, fb, faTop);
                        buffer.addVertex(guiGraphics.pose().last().pose(), x, yTop, 0.0F).setColor(fr, fg, fb, faTop);
                        buffer.addVertex(guiGraphics.pose().last().pose(), x, yBottom, 0.0F).setColor(fr, fg, fb, faBottom);
                     }
                  }
               }
            }
         }

         BufferUploader.drawWithShader(buffer.buildOrThrow());
         RenderSystem.enableDepthTest();
      }

      private int parseColor(String hex) {
         try {
            return Integer.parseInt(hex.replace("#", ""), 16);
         } catch (NumberFormatException var3) {
            return 16777215;
         }
      }

      private static class Wave {
         long startTime;
         int direction;

         Wave(long startTime, int direction) {
            this.startTime = startTime;
            this.direction = direction;
         }
      }
   }
}
