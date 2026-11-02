package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.DigitalScrollEffect;
import cn.bfops.killicon.client.render.effect.SubtitleEntranceBackground;
import cn.bfops.killicon.client.render.effect.TextFadeEffect;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class ScoreSubtitleRenderer implements IHudRenderer {
   private static final long FADE_IN_DURATION = 250L;
   private static final long FADE_OUT_DURATION = 300L;
   private static final long SCALE_ANIMATION_PHASE_DURATION = 100L;
   private static final float SCALE_ANIMATION_MAX_MULTIPLIER = 1.2F;
   private static final float GLOW_OFFSET = 0.3F;
   private static final long BACKGROUND_TRIGGER_COOLDOWN_MS = 100L;
   private static ScoreSubtitleRenderer instance;
   private int configXOffset = 0;
   private int configYOffset = 80;
   private String configScreenAnchor = "bottom_center";
   private long displayDuration = 3500L;
   private String scoreFormat = "<score>";
   private int scoreThreshold = 1000;
   private String highScoreColor = "#D4B800";
   private String flashColor = "#D0D0D0";
   private float scale = 2.0F;
   private float animationDuration = 1.25F;
   private float animationRefreshRate = 0.01F;
   private boolean enableNumberSegmentation = false;
   private boolean enableFlash = true;
   private boolean alignLeft = false;
   private boolean alignRight = false;
   private boolean enableScoreScalingEffect = false;
   private boolean enableDigitalScroll = true;
   private boolean enableGlowEffect = false;
   private float glowIntensity = 0.5F;
   private float glowSize = 0.3F;
   private int glowColorRgb = 16777215;
   private float glowAlphaMultiplier = 1.0F;
   private int normalTextColor = -1;
   private boolean enableTextShadow = true;
   private boolean configBlinkFadeAnimation = false;
   private boolean configEntranceBackground = false;
   private long backgroundTriggerTime = -1L;
   private float configEntranceBgPeakTransparency = 0.2F;
   private long configEntranceBgFadeInMs = 66L;
   private long configEntranceBgSweepMs = 100L;
   private long configEntranceBgFadeOutMs = 133L;
   private int configEntranceBgColor = 16777215;
   private boolean visible = false;
   private float currentScore = 0.0F;
   private long lastScoreTime = -1L;
   private long startTime = -1L;
   private long textHideTime = -1L;
   private boolean isFadingOut = false;
   private DigitalScrollEffect scrollEffect;
   private long scaleAnimationStartTime = -1L;

   private ScoreSubtitleRenderer() {
   }

   public static synchronized ScoreSubtitleRenderer getInstance() {
      if (instance == null) {
         instance = new ScoreSubtitleRenderer();
      }

      return instance;
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      ScoreSubtitleRenderer.RenderState state = this.resolveRenderState();
      if (state != null) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         int centerX = ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
         int textY = ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
         this.renderInternal(guiGraphics, font, centerX, textY, state);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      ScoreSubtitleRenderer.RenderState state = this.resolveRenderState();
      if (state != null) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int resolvedCenterX = Math.round(centerX);
         int resolvedTextY = Math.round(centerY);
         this.renderInternal(guiGraphics, font, resolvedCenterX, resolvedTextY, state);
      }
   }

   @Override
   public void resetPreview() {
      this.resetState();
   }

   private ScoreSubtitleRenderer.RenderState resolveRenderState() {
      if (this.visible && this.startTime != -1L) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         if (!this.isFadingOut && currentTime > this.textHideTime) {
            this.isFadingOut = true;
         }

         float alpha = this.calculateAlpha(currentTime);
         if (!(alpha <= 0.05F) && (this.textHideTime <= 0L || currentTime < this.textHideTime + 300L)) {
            this.updateAnimatedScore(currentTime);
            float currentScale = this.scale;
            long elapsed = currentTime - this.startTime;
            if (elapsed < 250L) {
               float progress = (float)elapsed / 250.0F;
               float easedProgress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
               currentScale = Mth.lerp(easedProgress, 1.5F, this.scale);
            }

            if (this.enableScoreScalingEffect && this.scaleAnimationStartTime != -1L) {
               long scaleElapsed = currentTime - this.scaleAnimationStartTime;
               if (scaleElapsed < 200L) {
                  float animationScaleMultiplier;
                  if (scaleElapsed < 100L) {
                     float t = (float)scaleElapsed / 100.0F;
                     float progress = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
                     animationScaleMultiplier = 1.0F + 0.20000005F * progress;
                  } else {
                     float t = (float)(scaleElapsed - 100L) / 100.0F;
                     float progress = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
                     animationScaleMultiplier = 1.2F - 0.20000005F * progress;
                  }

                  currentScale *= animationScaleMultiplier;
               }
            }

            return new ScoreSubtitleRenderer.RenderState(currentTime, currentScale, alpha);
         } else {
            this.visible = false;
            this.resetState();
            return null;
         }
      } else {
         return null;
      }
   }

   private void renderInternal(GuiGraphics guiGraphics, Font font, int centerX, int textY, ScoreSubtitleRenderer.RenderState state) {
      float displayScore;
      if (this.enableDigitalScroll) {
         displayScore = this.scrollEffect != null ? this.scrollEffect.getCurrentValue() : 0.0F;
      } else {
         displayScore = this.currentScore;
      }

      String scoreStr = this.formatNumberWithSegmentation(displayScore, this.enableNumberSegmentation);
      String prefix = "";
      String suffix = "";
      boolean hasPlaceholder = this.scoreFormat.contains("<score>");
      if (hasPlaceholder) {
         int placeholderIndex = this.scoreFormat.indexOf("<score>");
         prefix = this.scoreFormat.substring(0, placeholderIndex);
         suffix = this.scoreFormat.substring(placeholderIndex + 7);
      } else {
         prefix = this.scoreFormat;
      }

      int prefixWidth = font.width(prefix);
      int scoreWidth = hasPlaceholder ? font.width(scoreStr) : 0;
      int suffixWidth = font.width(suffix);
      int totalWidth = prefixWidth + scoreWidth + suffixWidth;
      int defaultColorWithAlpha = this.normalTextColor & 16777215 | (int)(state.alpha * 255.0F) << 24;
      int scoreColorWithAlpha = this.calculateScoreColor(displayScore, hasPlaceholder, state.alpha, state.currentTime, defaultColorWithAlpha);
      if (this.enableFlash && scoreColorWithAlpha != defaultColorWithAlpha && this.isFlickering(state.currentTime)) {
         defaultColorWithAlpha = scoreColorWithAlpha;
      }

      int textX;
      if (this.alignLeft && !this.alignRight) {
         textX = centerX;
      } else if (this.alignRight && !this.alignLeft) {
         textX = centerX - totalWidth;
      } else {
         textX = centerX - totalWidth / 2;
      }

      int currentX = textX;
      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();
      float pivotY = (float)textY + 9.0F / 2.0F;
      float pivotX;
      if (this.alignLeft && !this.alignRight) {
         pivotX = (float)textX;
      } else if (this.alignRight && !this.alignLeft) {
         pivotX = (float)(textX + totalWidth);
      } else {
         pivotX = (float)textX + (float)totalWidth / 2.0F;
      }

      if (this.configEntranceBackground && this.backgroundTriggerTime >= 0L) {
         long elapsed = state.currentTime - this.backgroundTriggerTime;
         float scale = state.currentScale;
         float textLeftScreen = pivotX + ((float)textX - pivotX) * scale;
         float textRightScreen = pivotX + ((float)(textX + totalWidth) - pivotX) * scale;
         float textHeightScreen = 9.0F * scale;
         SubtitleEntranceBackground.draw(
            guiGraphics,
            elapsed,
            textLeftScreen,
            textRightScreen,
            pivotY,
            textHeightScreen,
            this.configEntranceBgFadeInMs,
            this.configEntranceBgSweepMs,
            this.configEntranceBgFadeOutMs,
            this.configEntranceBgPeakTransparency,
            this.configEntranceBgColor,
            false
         );
      }

      poseStack.translate(pivotX, pivotY, 0.0F);
      poseStack.scale(state.currentScale, state.currentScale, 1.0F);
      poseStack.translate(-pivotX, -pivotY, 0.0F);
      if (!prefix.isEmpty()) {
         this.drawTextWithGlow(guiGraphics, font, prefix, textX, textY, defaultColorWithAlpha, this.enableTextShadow);
         currentX = textX + prefixWidth;
      }

      if (hasPlaceholder) {
         this.drawTextWithGlow(guiGraphics, font, scoreStr, currentX, textY, scoreColorWithAlpha, this.enableTextShadow);
         currentX += scoreWidth;
      }

      if (!suffix.isEmpty()) {
         this.drawTextWithGlow(guiGraphics, font, suffix, currentX, textY, defaultColorWithAlpha, this.enableTextShadow);
      }

      poseStack.popPose();
   }

   private void drawTextWithGlow(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, boolean dropShadow) {
      if (this.enableGlowEffect) {
         int alpha = color >> 24 & 0xFF;
         int glowAlpha = (int)((float)alpha * this.glowIntensity * this.glowAlphaMultiplier);
         glowAlpha = Math.max(25, Math.min(255, glowAlpha));
         glowAlpha = Math.max(0, Math.min(255, glowAlpha));
         int glowColor = this.glowColorRgb & 16777215 | glowAlpha << 24;
         PoseStack poseStack = guiGraphics.pose();
         float[][] offsets = new float[][]{
            {-this.glowSize, 0.0F},
            {this.glowSize, 0.0F},
            {0.0F, -this.glowSize},
            {0.0F, this.glowSize},
            {-this.glowSize, -this.glowSize},
            {this.glowSize, -this.glowSize},
            {-this.glowSize, this.glowSize},
            {this.glowSize, this.glowSize}
         };

         for (float[] offset : offsets) {
            poseStack.pushPose();
            poseStack.translate(offset[0], offset[1], 0.0F);
            guiGraphics.drawString(font, text, x, y, glowColor, false);
            poseStack.popPose();
         }
      }

      guiGraphics.drawString(font, text, x, y, color, dropShadow);
   }

   public void addScore(float score) {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "score");
      if (config != null) {
         boolean configVisible = !config.has("visible") || config.get("visible").getAsBoolean();
         if (!configVisible) {
            this.visible = false;
         } else {
            this.loadConfig(config);
            if (this.displayDuration < 250L) {
               this.displayDuration = 250L;
            }

            long currentTime = PreviewRenderTimeContext.currentTimeMillis();
            boolean shouldReset = false;
            if (this.isFadingOut) {
               shouldReset = true;
            } else if (this.textHideTime > 0L && currentTime > this.textHideTime) {
               shouldReset = true;
            }

            if (shouldReset) {
               this.resetState();
               this.currentScore = score;
            } else {
               this.currentScore += score;
            }

            if (this.scrollEffect != null) {
               this.scrollEffect.startAnimation(this.scrollEffect.getCurrentValue(), this.currentScore);
            }

            this.lastScoreTime = currentTime;
            this.scaleAnimationStartTime = currentTime;
            if (this.backgroundTriggerTime < 0L || currentTime - this.backgroundTriggerTime >= 100L) {
               this.backgroundTriggerTime = currentTime;
            }

            if (this.startTime == -1L) {
               this.startTime = currentTime;
               this.visible = true;
               this.isFadingOut = false;
            }

            this.textHideTime = currentTime + this.displayDuration;
            SoundTriggerManager.playScoreSound();
         }
      }
   }

   private void loadConfig(JsonObject config) {
      try {
         this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 40;
         this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.displayDuration = config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 4000L;
         this.scale = config.has("scale") ? config.get("scale").getAsFloat() : 2.0F;
         this.scoreFormat = config.has("format_score") ? config.get("format_score").getAsString() : "<score>";
         this.scoreThreshold = config.has("score_threshold") ? config.get("score_threshold").getAsInt() : 1000;
         this.highScoreColor = config.has("color_high_score") ? config.get("color_high_score").getAsString() : "#D4B800";
         this.flashColor = config.has("color_flash") ? config.get("color_flash").getAsString() : "#D0D0D0";
         this.animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 1.25F;
         this.animationRefreshRate = config.has("animation_refresh_rate") ? config.get("animation_refresh_rate").getAsFloat() : 0.01F;
         this.enableNumberSegmentation = config.has("enable_number_segmentation") ? config.get("enable_number_segmentation").getAsBoolean() : false;
         this.enableFlash = config.has("enable_flash") ? config.get("enable_flash").getAsBoolean() : true;
         this.alignLeft = config.has("align_left") ? config.get("align_left").getAsBoolean() : false;
         this.alignRight = config.has("align_right") ? config.get("align_right").getAsBoolean() : false;
         this.enableScoreScalingEffect = config.has("enable_score_scaling_effect") ? config.get("enable_score_scaling_effect").getAsBoolean() : false;
         this.enableDigitalScroll = config.has("enable_digital_scroll") ? config.get("enable_digital_scroll").getAsBoolean() : true;
         this.enableGlowEffect = config.has("enable_glow_effect") && config.get("enable_glow_effect").getAsBoolean();
         this.glowIntensity = config.has("glow_intensity") ? config.get("glow_intensity").getAsFloat() : 0.5F;
         this.glowSize = config.has("glow_size") ? config.get("glow_size").getAsFloat() : 0.3F;
         this.glowColorRgb = this.hexColorToInt(config.has("glow_color") ? config.get("glow_color").getAsString() : "#FFFFFF");
         this.glowAlphaMultiplier = config.has("glow_alpha") ? Mth.clamp(config.get("glow_alpha").getAsFloat(), 0.0F, 1.0F) : 1.0F;
         this.enableTextShadow = !config.has("enable_text_shadow") || config.get("enable_text_shadow").getAsBoolean();
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.configEntranceBackground = config.has("entrance_background") && config.get("entrance_background").getAsBoolean();
         this.configEntranceBgPeakTransparency = config.has("entrance_background_alpha")
            ? Mth.clamp(config.get("entrance_background_alpha").getAsFloat(), 0.0F, 1.0F)
            : 0.2F;
         this.configEntranceBgFadeInMs = config.has("entrance_background_fade_in")
            ? Math.max(1L, (long)(config.get("entrance_background_fade_in").getAsFloat() * 1000.0F))
            : 66L;
         this.configEntranceBgSweepMs = config.has("entrance_background_sweep_duration")
            ? Math.max(1L, (long)(config.get("entrance_background_sweep_duration").getAsFloat() * 1000.0F))
            : 100L;
         this.configEntranceBgFadeOutMs = config.has("entrance_background_fade_out")
            ? Math.max(1L, (long)(config.get("entrance_background_fade_out").getAsFloat() * 1000.0F))
            : 133L;
         this.configEntranceBgColor = config.has("entrance_background_color")
            ? SubtitleEntranceBackground.parseColor(config.get("entrance_background_color").getAsString(), 16777215)
            : 16777215;
         this.normalTextColor = this.hexColorToInt(config.has("color_normal_text") ? config.get("color_normal_text").getAsString() : "#FFFFFF");
         if (this.scrollEffect == null) {
            this.scrollEffect = new DigitalScrollEffect(this.animationDuration, this.animationRefreshRate, DigitalScrollEffect.Easing.QUINTIC_OUT);
         } else {
            this.scrollEffect.setAnimationDuration(this.animationDuration);
            this.scrollEffect.setAnimationRefreshRate(this.animationRefreshRate);
         }
      } catch (Exception var3) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.subtitle.config_error");
         this.configXOffset = 0;
         this.configYOffset = 80;
         this.configScreenAnchor = "bottom_center";
         this.displayDuration = 4000L;
         this.scale = 2.0F;
         this.scoreFormat = "<score>";
         this.scoreThreshold = 1000;
         this.highScoreColor = "#D4B800";
         this.flashColor = "#D0D0D0";
         this.animationDuration = 1.25F;
         this.animationRefreshRate = 0.01F;
         this.enableNumberSegmentation = false;
         this.enableFlash = true;
         this.alignLeft = false;
         this.alignRight = false;
         this.enableScoreScalingEffect = false;
         this.normalTextColor = -1;
         if (this.scrollEffect == null) {
            this.scrollEffect = new DigitalScrollEffect(this.animationDuration, this.animationRefreshRate, DigitalScrollEffect.Easing.QUINTIC_OUT);
         } else {
            this.scrollEffect.setAnimationDuration(this.animationDuration);
            this.scrollEffect.setAnimationRefreshRate(this.animationRefreshRate);
         }
      }
   }

   private float calculateAlpha(long currentTime) {
      if (this.textHideTime > 0L) {
         if (currentTime < this.textHideTime) {
            return 1.0F;
         } else {
            long fadeElapsed = currentTime - this.textHideTime;
            float fadeProgress = (float)fadeElapsed / 300.0F;
            return TextFadeEffect.fadeAlpha(fadeProgress, this.configBlinkFadeAnimation);
         }
      } else {
         return 1.0F;
      }
   }

   private void resetState() {
      this.currentScore = 0.0F;
      this.startTime = -1L;
      this.lastScoreTime = -1L;
      this.textHideTime = -1L;
      this.isFadingOut = false;
      this.visible = false;
      this.scrollEffect = null;
      this.backgroundTriggerTime = -1L;
   }

   private int roundToInteger(float value) {
      return Math.round(value);
   }

   private void updateAnimatedScore(long currentTime) {
      if (this.scrollEffect != null) {
         this.scrollEffect.update(currentTime);
      }
   }

   private boolean isFlickering(long currentTime) {
      if (this.textHideTime > 0L) {
         long timeRemaining = this.textHideTime - currentTime;
         if (timeRemaining > 0L && timeRemaining <= 2000L) {
            return (timeRemaining - 1L) / 500L % 2L == 1L;
         }
      }

      return false;
   }

   private int calculateScoreColor(float roundedScore, boolean hasPlaceholder, float alpha, long currentTime, int defaultColorWithAlpha) {
      int scoreColorWithAlpha;
      if (roundedScore >= (float)this.scoreThreshold && hasPlaceholder) {
         int highScoreColorInt = this.hexColorToInt(this.highScoreColor);
         scoreColorWithAlpha = highScoreColorInt & 16777215 | (int)(alpha * 255.0F) << 24;
      } else {
         scoreColorWithAlpha = defaultColorWithAlpha;
      }

      if (this.enableFlash && this.isFlickering(currentTime)) {
         int flashColorInt = this.hexColorToInt(this.flashColor);
         int flashColorWithAlpha = flashColorInt & 16777215 | (int)(alpha * 255.0F) << 24;
         scoreColorWithAlpha = flashColorWithAlpha;
      }

      return scoreColorWithAlpha;
   }

   private int hexColorToInt(String hexColor) {
      if (hexColor != null && !hexColor.isEmpty()) {
         String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;

         try {
            if (hex.length() == 6) {
               return Integer.parseInt(hex, 16) | 0xFF000000;
            }

            if (hex.length() == 8) {
               return (int)Long.parseLong(hex, 16);
            }

            if (hex.length() == 3) {
               int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
               int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
               int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
               return r << 16 | g << 8 | b | 0xFF000000;
            }
         } catch (NumberFormatException var6) {
            ClientMessageLogger.chatWarn("battlefield_ops.client.score_subtitle.invalid_color", hexColor);
         }

         return -1;
      } else {
         return -1;
      }
   }

   private String formatNumberWithSegmentation(float number, boolean enabled) {
      boolean isNegative = number < 0.0F;
      float absNumber = Math.abs(number);
      String formattedNumber;
      if (absNumber < 1.0F) {
         formattedNumber = String.format("%.1f", absNumber);
      } else {
         int roundedNumber = Math.round(absNumber);
         formattedNumber = String.valueOf(roundedNumber);
      }

      if (isNegative) {
         formattedNumber = "-" + formattedNumber;
      }

      if (enabled) {
         String[] parts = formattedNumber.split("\\.");
         String integerPart = parts[0];
         String decimalPart = parts.length > 1 ? parts[1] : "";
         StringBuilder result = new StringBuilder();
         int length = integerPart.length();

         for (int i = 0; i < length; i++) {
            if (i > 0 && (length - i) % 3 == 0 && integerPart.charAt(i) != '-') {
               result.append(",");
            }

            result.append(integerPart.charAt(i));
         }

         if (!decimalPart.isEmpty()) {
            result.append(".").append(decimalPart);
         }

         return result.toString();
      } else {
         return formattedNumber;
      }
   }

   private static final class RenderState {
      private final long currentTime;
      private final float currentScale;
      private final float alpha;

      private RenderState(long currentTime, float currentScale, float alpha) {
         this.currentTime = currentTime;
         this.currentScale = currentScale;
         this.alpha = alpha;
      }
   }
}
