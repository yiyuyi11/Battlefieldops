package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconGlowRenderEffect;
import cn.bfops.killicon.client.render.effect.IconRingEffect;
import cn.bfops.killicon.client.textures.ModTextures;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class ComboIconRenderer implements IHudRenderer {
   @Override public void resetPreview(){isVisible=false;startTime=-1;}

   private static final long DEFAULT_DISPLAY_DURATION = 1500L;
   private static final long ANIMATION_DURATION = 300L;
   private static final float START_SCALE = 2.0F;
   private static final int DEFAULT_HEADSHOT_COLOR = 13940736;
   private static final int DEFAULT_EXPLOSION_COLOR = 16219904;
   private static final int DEFAULT_CRIT_COLOR = 10275941;
   private static volatile long serverComboWindowMs = -1L;
   private float configScale = 1.0F;
   private int configXOffset = 0;
   private int configYOffset = 0;
   private String configScreenAnchor = "bottom_center";
   private long displayDuration = 1500L;
   private boolean enableCritRing = true;
   private boolean enableHeadshotRing = true;
   private boolean enableExplosionRing = true;
   private float ringCritRadius = 42.0F;
   private float ringCritThickness = 1.8F;
   private float ringHeadshotRadius = 42.0F;
   private float ringHeadshotThickness = 3.0F;
   private float ringExplosionRadius = 42.0F;
   private float ringExplosionThickness = 5.4F;
   private boolean configIconGlowEnabled = false;
   private int configIconGlowColor = 16777215;
   private float configIconGlowIntensity = 0.45F;
   private float configIconGlowSize = 4.0F;
   private JsonObject currentConfig;
   private long startTime = -1L;
   private boolean isVisible = false;
   private long effectiveDisplayDuration = 1500L;
   private int comboCount = 0;
   private int currentKillType = 0;
   private final IconRingEffect ringEffect = new IconRingEffect();

   public static void updateServerComboWindowSeconds(double seconds) {
      if (!(seconds <= 0.0)) {
         serverComboWindowMs = Math.max(100L, (long)(seconds * 1000.0));
      }
   }

   public static long getServerComboWindowMs() {
      return serverComboWindowMs;
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      this.currentKillType = context.type();
      this.comboCount = context.comboCount();
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "combo");
      if (config != null) {
         boolean visible = !config.has("visible") || config.get("visible").getAsBoolean();
         if (!visible) {
            this.isVisible = false;
         } else {
            this.loadConfig(config);
            if (this.displayDuration < 300L) {
               this.displayDuration = 300L;
            }

            this.effectiveDisplayDuration = this.displayDuration + 300L;
            this.startTime = PreviewRenderTimeContext.currentTimeMillis();
            this.isVisible = true;
            this.triggerRingEffect();
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.isVisible && this.startTime != -1L) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         float centerX = (float)ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
         float centerY = (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
         this.renderAt(guiGraphics, partialTick, centerX, centerY);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      if (this.isVisible && this.startTime != -1L) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         long elapsed = currentTime - this.startTime;
         if (elapsed > this.effectiveDisplayDuration) {
            this.isVisible = false;
            this.startTime = -1L;
         } else {
            float endScale = 1.0F * this.configScale;
            float initialScale = 2.0F * this.configScale;
            float currentScale = endScale;
            float alpha = 1.0F;
            if (elapsed < 300L) {
               float progress = (float)elapsed / 300.0F;
               progress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
               currentScale = Mth.lerp(progress, initialScale, endScale);
               alpha = Mth.clamp(progress, 0.0F, 1.0F);
            }

            if (elapsed > this.displayDuration) {
               long fadeElapsed = elapsed - this.displayDuration;
               float fadeProgress = (float)fadeElapsed / 300.0F;
               float fadeAlpha = 1.0F - fadeProgress;
               alpha = Mth.clamp(alpha * fadeAlpha, 0.0F, 1.0F);
            }

            int displayCombo = Mth.clamp(this.comboCount, 1, 6);
            String textureKey = "combo_" + displayCombo;
            String texturePath = ElementTextureDefinition.getSelectedTextureFileName(
               ConfigManager.getCurrentPresetId(), "kill_icon/combo", textureKey, this.currentConfig
            );
            float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
            float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
            float drawWidth = 64.0F * frameWidthRatio;
            float drawHeight = 64.0F * frameHeightRatio;
            float focusedAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/combo", textureKey);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);

            try {
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(centerX, centerY, 0.0F);
               guiGraphics.pose().scale(currentScale, currentScale, 1.0F);
               guiGraphics.pose().translate(-drawWidth / 2.0F, -drawHeight / 2.0F, 0.0F);
               if (this.configIconGlowEnabled) {
                  IconGlowRenderEffect.drawGlowFrame(
                     guiGraphics,
                     ModTextures.get(texturePath),
                     0,
                     0,
                     (int)drawWidth,
                     (int)drawHeight,
                     0,
                     0,
                     (int)drawWidth,
                     (int)drawHeight,
                     (int)drawWidth,
                     (int)drawHeight,
                     focusedAlpha,
                     this.configIconGlowColor,
                     this.configIconGlowIntensity,
                     this.configIconGlowSize
                  );
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
               }

               guiGraphics.blit(ModTextures.get(texturePath), 0, 0, 0.0F, 0.0F, (int)drawWidth, (int)drawHeight, (int)drawWidth, (int)drawHeight);
               guiGraphics.pose().popPose();
               this.ringEffect.render(guiGraphics, centerX, centerY, currentTime);
            } finally {
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   private void loadConfig(JsonObject config) {
      try {
         this.currentConfig = config;
         this.configScale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
         this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.displayDuration = this.resolveDisplayDuration(config);
         boolean defaultRingEnable = !config.has("enable_icon_effect") || config.get("enable_icon_effect").getAsBoolean();
         this.enableCritRing = config.has("enable_ring_effect_crit") ? config.get("enable_ring_effect_crit").getAsBoolean() : defaultRingEnable;
         this.enableHeadshotRing = config.has("enable_ring_effect_headshot") ? config.get("enable_ring_effect_headshot").getAsBoolean() : defaultRingEnable;
         this.enableExplosionRing = config.has("enable_ring_effect_explosion") ? config.get("enable_ring_effect_explosion").getAsBoolean() : defaultRingEnable;
         this.ringCritRadius = this.resolveRingFloat(config, "ring_effect_crit_radius", "ring_effect_normal_radius", 42.0F);
         this.ringCritThickness = this.resolveRingFloat(config, "ring_effect_crit_thickness", "ring_effect_normal_thickness", 1.8F);
         this.ringHeadshotRadius = config.has("ring_effect_headshot_radius") ? config.get("ring_effect_headshot_radius").getAsFloat() : 42.0F;
         this.ringHeadshotThickness = config.has("ring_effect_headshot_thickness") ? config.get("ring_effect_headshot_thickness").getAsFloat() : 3.0F;
         this.ringExplosionRadius = config.has("ring_effect_explosion_radius") ? config.get("ring_effect_explosion_radius").getAsFloat() : 42.0F;
         this.ringExplosionThickness = config.has("ring_effect_explosion_thickness") ? config.get("ring_effect_explosion_thickness").getAsFloat() : 5.4F;
         this.configIconGlowEnabled = IconGlowRenderEffect.isEnabled(config);
         this.configIconGlowColor = IconGlowRenderEffect.resolveColor(config);
         this.configIconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
         this.configIconGlowSize = IconGlowRenderEffect.resolveSize(config);
      } catch (Exception var3) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.combo.config_error");
         this.currentConfig = null;
         this.configScale = 1.0F;
         this.configXOffset = 0;
         this.configYOffset = 0;
         this.configScreenAnchor = "bottom_center";
         this.displayDuration = this.resolveDisplayDuration(null);
         this.enableCritRing = true;
         this.enableHeadshotRing = true;
         this.enableExplosionRing = true;
         this.ringCritRadius = 42.0F;
         this.ringCritThickness = 1.8F;
         this.ringHeadshotRadius = 42.0F;
         this.ringHeadshotThickness = 3.0F;
         this.ringExplosionRadius = 42.0F;
         this.ringExplosionThickness = 5.4F;
         this.configIconGlowEnabled = false;
         this.configIconGlowColor = 16777215;
         this.configIconGlowIntensity = 0.45F;
         this.configIconGlowSize = 4.0F;
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

   private long resolveDisplayDuration(JsonObject config) {
      long serverDuration = serverComboWindowMs;
      if (serverDuration > 0L) {
         return serverDuration;
      } else {
         return config != null && config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 1500L;
      }
   }

   private void triggerRingEffect() {
      if (this.isRingEnabledForKillType(this.currentKillType)) {
         this.ringEffect
            .setRingParams(
               this.ringCritRadius,
               this.ringCritThickness,
               this.ringHeadshotRadius,
               this.ringHeadshotThickness,
               this.ringExplosionRadius,
               this.ringExplosionThickness
            );
         this.ringEffect
            .trigger(this.startTime, true, this.currentKillType, this.resolveHeadshotEffectRgb(), this.resolveExplosionEffectRgb(), this.resolveCritEffectRgb());
      } else {
         this.ringEffect.trigger(this.startTime, false, this.currentKillType, 0, 0, 0);
      }
   }

   private int resolveHeadshotEffectRgb() {
      return this.resolveEffectRgb("ring_effect_headshot_color", 13940736);
   }

   private int resolveExplosionEffectRgb() {
      return this.resolveEffectRgb("ring_effect_explosion_color", 16219904);
   }

   private int resolveCritEffectRgb() {
      if (this.currentConfig != null) {
         if (this.currentConfig.has("ring_effect_crit_color")) {
            return this.resolveEffectRgb("ring_effect_crit_color", 10275941);
         }

         if (this.currentConfig.has("ring_effect_normal_color")) {
            return this.resolveEffectRgb("ring_effect_normal_color", 10275941);
         }
      }

      return 10275941;
   }

   private int resolveEffectRgb(String key, int defaultValue) {
      if (this.currentConfig == null) {
         return defaultValue;
      } else {
         String hex = this.currentConfig.has(key) ? this.currentConfig.get(key).getAsString() : null;
         return parseRgbHexOrDefault(hex, defaultValue);
      }
   }

   private float resolveRingFloat(JsonObject config, String key, String legacyKey, float defaultValue) {
      if (config == null) {
         return defaultValue;
      } else if (config.has(key)) {
         return config.get(key).getAsFloat();
      } else {
         return legacyKey != null && config.has(legacyKey) ? config.get(legacyKey).getAsFloat() : defaultValue;
      }
   }

   private boolean isRingEnabledForKillType(int type) {
      return switch (type) {
         case 1 -> this.enableHeadshotRing;
         case 2 -> this.enableExplosionRing;
         case 3 -> this.enableCritRing;
         default -> false;
      };
   }

   private static int parseRgbHexOrDefault(String hex, int fallbackRgb) {
      if (hex != null && !hex.isEmpty()) {
         try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            return rgb & 16777215;
         } catch (NumberFormatException var3) {
            return fallbackRgb;
         }
      } else {
         return fallbackRgb;
      }
   }
}
