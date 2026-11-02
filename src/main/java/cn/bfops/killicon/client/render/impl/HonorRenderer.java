package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.FormatDefaultsManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconTextureFilterEffect;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;

public class HonorRenderer implements IHudRenderer {
   private static final HonorRenderer INSTANCE = new HonorRenderer();
   private static final int TEXTURE_W = 110;
   private static final int TEXTURE_H = 61;
   private static final float HONOR_DISPLAY_SCALE = 0.4F;
   private boolean configVisible = true;
   private float configScale = 1.0F;
   private String configDirection = "right";
   private int configXOffset = 0;
   private int configYOffset = 100;
   private String configScreenAnchor = "bottom_center";
   private long configDisplayDelayMs = 200L;
   private long configEnterAnimationMs = 200L;
   private float configEnterStartScale = 0.0F;
   private long configDisplayDurationMs = 3000L;
   private long configFadeOutMs = 200L;
   private boolean configBlinkFadeAnimation = false;
   private boolean configHintBoxEnabled = true;
   private int configHintBoxXOffset = -30;
   private int configHintBoxHeight = 15;
   private long configHintBoxEnterMs = 600L;
   private int configHintBoxColor = 16777215;
   private float configHintBoxMaxAlpha = 0.0F;
   private int configHintBoxTextColor = 0;
   private int configHintBoxTextPadding = 3;
   private int configHintBoxTextGap = 5;
   private float configHintBoxTextScale = 0.8F;
   private boolean configHintBoxTextShadow = true;
   private float configMainIconMaxAlpha = 20.0F;
   private long configMinDisplayMs = 1000L;
   private boolean configShakeEnabled = false;
   private int configShakeCount = 2;
   private int configShakeRange = 2;
   private boolean configBestEnabled = true;
   private int configBestIconColor = 16500077;
   private int configBestHintBoxColor = 16500077;
   private int configBestSweepStartWidth = 5;
   private int configBestSweepEndWidth = 3;
   private float configBestSweepDuration = 0.15F;
   private float configBestSweepFadeDuration = 0.2F;
   private int configBestSweepTextGap = 1;
   private float configBestTextFadeInDuration = 0.1F;
   private HonorRenderer.Display currentDisplay;
   private final ArrayDeque<HonorRenderer.Display> pendingHonorIds = new ArrayDeque<>();
   private boolean displaySoundPlayed = false;
   private boolean lastAppliedFilter = false;
   private boolean hasAppliedFilter = false;
   private final Random shakeRandom = new Random();
   private float shakeOffsetX = 0.0F;
   private float shakeOffsetY = 0.0F;
   private int shakeSegment = -1;

   public static HonorRenderer getInstance() {
      return INSTANCE;
   }

   public boolean isDisplaying() {
      return this.currentDisplay != null;
   }

   @Override
   public void resetPreview() {
      this.currentDisplay = null;
      this.pendingHonorIds.clear();
      this.displaySoundPlayed = false;
   }

   public void loadConfig(JsonObject config) {
      if (config != null) {
         this.configVisible = !config.has("visible") || config.get("visible").getAsBoolean();
         this.configScale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         this.configDirection = config.has("direction") ? config.get("direction").getAsString() : "right";
         this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 100;
         this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.configDisplayDelayMs = (long)(config.has("display_delay") ? config.get("display_delay").getAsFloat() * 1000.0F : 200.0F);
         this.configEnterAnimationMs = Math.max(
            1L, (long)(config.has("enter_animation_duration") ? config.get("enter_animation_duration").getAsFloat() * 1000.0F : 200.0F)
         );
         this.configEnterStartScale = config.has("enter_start_scale") ? config.get("enter_start_scale").getAsFloat() : 0.0F;
         this.configDisplayDurationMs = (long)(config.has("display_duration") ? config.get("display_duration").getAsFloat() * 1000.0F : 3000.0F);
         this.configFadeOutMs = Math.max(1L, (long)(config.has("fade_out_duration") ? config.get("fade_out_duration").getAsFloat() * 1000.0F : 200.0F));
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.configHintBoxEnabled = !config.has("hint_box_enabled") || config.get("hint_box_enabled").getAsBoolean();
         this.configHintBoxXOffset = config.has("hint_box_x_offset") ? config.get("hint_box_x_offset").getAsInt() : -30;
         this.configHintBoxHeight = config.has("hint_box_height") ? config.get("hint_box_height").getAsInt() : 15;
         this.configHintBoxEnterMs = Math.max(
            1L, (long)(config.has("hint_box_enter_duration") ? config.get("hint_box_enter_duration").getAsFloat() * 1000.0F : 600.0F)
         );
         this.configHintBoxColor = parseColor(config, "hint_box_color", 16777215);
         this.configHintBoxMaxAlpha = config.has("hint_box_max_alpha") ? config.get("hint_box_max_alpha").getAsFloat() : 0.0F;
         this.configHintBoxTextColor = parseColor(config, "hint_box_text_color", 0);
         this.configHintBoxTextPadding = config.has("hint_box_text_padding") ? config.get("hint_box_text_padding").getAsInt() : 3;
         this.configHintBoxTextGap = config.has("hint_box_text_gap") ? config.get("hint_box_text_gap").getAsInt() : 5;
         this.configHintBoxTextScale = config.has("hint_box_text_scale") ? config.get("hint_box_text_scale").getAsFloat() : 0.8F;
         this.configHintBoxTextShadow = !config.has("hint_box_text_shadow") || config.get("hint_box_text_shadow").getAsBoolean();
         this.configMainIconMaxAlpha = config.has("main_icon_max_alpha") ? config.get("main_icon_max_alpha").getAsFloat() : 20.0F;
         this.configMinDisplayMs = Math.max(
            1L, (long)(config.has("min_display_duration") ? config.get("min_display_duration").getAsFloat() * 1000.0F : 1000.0F)
         );
         this.configShakeEnabled = config.has("shake_enabled") && config.get("shake_enabled").getAsBoolean();
         this.configShakeCount = config.has("shake_count") ? config.get("shake_count").getAsInt() : 2;
         this.configShakeRange = config.has("shake_range") ? config.get("shake_range").getAsInt() : 2;
         this.configBestEnabled = !config.has("best_enabled") || config.get("best_enabled").getAsBoolean();
         this.configBestIconColor = parseColor(config, "best_icon_color", 16500077);
         this.configBestHintBoxColor = parseColor(config, "best_hint_box_color", 16500077);
         this.configBestSweepStartWidth = config.has("best_sweep_start_width") ? config.get("best_sweep_start_width").getAsInt() : 5;
         this.configBestSweepEndWidth = config.has("best_sweep_end_width") ? config.get("best_sweep_end_width").getAsInt() : 3;
         this.configBestSweepDuration = config.has("best_sweep_duration") ? config.get("best_sweep_duration").getAsFloat() : 0.15F;
         this.configBestSweepFadeDuration = config.has("best_sweep_fade_duration") ? config.get("best_sweep_fade_duration").getAsFloat() : 0.2F;
         this.configBestSweepTextGap = config.has("best_sweep_text_gap") ? config.get("best_sweep_text_gap").getAsInt() : 1;
         this.configBestTextFadeInDuration = config.has("best_text_fade_in_duration") ? config.get("best_text_fade_in_duration").getAsFloat() : 0.1F;
      }
   }

   private static int parseColor(JsonObject config, String key, int fallback) {
      if (config != null && config.has(key)) {
         String raw = config.get(key).getAsString();

         try {
            if (raw.startsWith("#")) {
               return (int)Long.parseLong(raw.substring(1), 16);
            } else {
               return !raw.startsWith("0x") && !raw.startsWith("0X") ? (int)Long.parseLong(raw, 16) : (int)Long.parseLong(raw.substring(2), 16);
            }
         } catch (NumberFormatException var5) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private boolean isBest() {
      return this.configBestEnabled && this.currentDisplay != null && !this.currentDisplay.style.isEmpty();
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      if (ConfigManager.getElementConfig("kill_icon", "honor") != null) {
         String extra = context.extraData();
         if (extra != null && !extra.isEmpty()) {
            String honorId = extra;
            String style = "";
            int colon = extra.indexOf(58);
            if (colon > 0) {
               honorId = extra.substring(0, colon);
               String suffix = extra.substring(colon + 1);
               if (suffix.startsWith("style=")) {
                  style = suffix.substring("style=".length());
               }
            }

            if (!honorId.isEmpty()) {
               long now = System.currentTimeMillis();
               if (this.currentDisplay == null) {
                  this.currentDisplay = new HonorRenderer.Display(honorId, style, now);
                  this.displaySoundPlayed = false;
               } else {
                  this.pendingHonorIds.addLast(new HonorRenderer.Display(honorId, style, now));
               }
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "honor");
      if (config != null) {
         this.loadConfig(config);
         if (this.configVisible && this.currentDisplay != null) {
            long now = System.currentTimeMillis();
            long[] timeline = this.advanceTimeline(now, true);
            if (timeline != null) {
               long elapsed = timeline[0];
               long fadeStartMs = timeline[1];
               float scale = this.configScale;
               float peakAlpha = 1.0F - Mth.clamp(this.configMainIconMaxAlpha, 0.0F, 100.0F) / 100.0F;
               float alpha = peakAlpha;
               float fadeProgress = 0.0F;
               if (elapsed < this.configEnterAnimationMs) {
                  float progress = Math.min(1.0F, (float)elapsed / (float)this.configEnterAnimationMs);
                  progress = easeOutCubic(progress);
                  scale = Mth.lerp(progress, this.configEnterStartScale, this.configScale);
                  alpha = peakAlpha * progress;
               }

               if (elapsed >= fadeStartMs) {
                  fadeProgress = Math.min(1.0F, (float)(elapsed - fadeStartMs) / (float)this.configFadeOutMs);
                  alpha = peakAlpha * this.resolveFadeAlpha(fadeProgress);
               }

               float shakeX = 0.0F;
               float shakeY = 0.0F;
               if (elapsed < this.configEnterAnimationMs && this.configShakeEnabled) {
                  float[] shake = this.computeShake(elapsed);
                  shakeX = shake[0];
                  shakeY = shake[1];
               }

               Minecraft mc = Minecraft.getInstance();
               int screenWidth = mc.getWindow().getGuiScaledWidth();
               int screenHeight = mc.getWindow().getGuiScaledHeight();
               int centerX = ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
               int centerY = ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
               ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "icons/honor/honor_" + this.currentDisplay.honorId + ".png");
               float iconScale = scale * 0.4F;
               this.applyTextureFilter(texture);
               drawIcon(
                  guiGraphics, texture, (float)centerX + shakeX, (float)centerY + shakeY, iconScale, alpha, this.isBest() ? this.configBestIconColor : 16777215
               );
               float hintFadeAlpha = elapsed >= fadeStartMs ? this.resolveFadeAlpha(fadeProgress) : 1.0F;
               this.drawHintBox(guiGraphics, (float)centerX + shakeX, (float)centerY + shakeY, 110.0F * iconScale, elapsed, hintFadeAlpha);
            }
         }
      }
   }

   private float[] computeShake(long elapsed) {
      if (this.configShakeCount > 0 && this.configShakeRange > 0) {
         long segMs = Math.max(1L, this.configEnterAnimationMs / (long)this.configShakeCount);
         int seg = (int)Math.min((long)(this.configShakeCount - 1), elapsed / segMs);
         if (seg != this.shakeSegment) {
            this.shakeSegment = seg;
            this.shakeOffsetX = (this.shakeRandom.nextFloat() * 2.0F - 1.0F) * (float)this.configShakeRange;
            this.shakeOffsetY = (this.shakeRandom.nextFloat() * 2.0F - 1.0F) * (float)this.configShakeRange;
         }

         return new float[]{this.shakeOffsetX, this.shakeOffsetY};
      } else {
         return new float[]{0.0F, 0.0F};
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float originX, float originY, float baseW, float baseH) {
      this.loadConfig(ConfigManager.getElementConfig("kill_icon", "honor"));
      if (this.currentDisplay != null) {
         long now = System.currentTimeMillis();
         long[] timeline = this.advanceTimeline(now, false);
         if (timeline != null) {
            long elapsed = timeline[0];
            long fadeStartMs = timeline[1];
            float scaleFactor = 1.0F;
            float peakAlpha = 1.0F - Mth.clamp(this.configMainIconMaxAlpha, 0.0F, 100.0F) / 100.0F;
            float alpha = peakAlpha;
            if (elapsed < this.configEnterAnimationMs) {
               float progress = Math.min(1.0F, (float)elapsed / (float)this.configEnterAnimationMs);
               progress = easeOutCubic(progress);
               scaleFactor = Mth.lerp(progress, this.configEnterStartScale, 1.0F);
               alpha = peakAlpha * progress;
            }

            float fadeProgress = 0.0F;
            if (elapsed >= fadeStartMs) {
               fadeProgress = Math.min(1.0F, (float)(elapsed - fadeStartMs) / (float)this.configFadeOutMs);
               alpha = peakAlpha * this.resolveFadeAlpha(fadeProgress);
            }

            float shakeX = 0.0F;
            float shakeY = 0.0F;
            if (elapsed < this.configEnterAnimationMs && this.configShakeEnabled) {
               float[] shake = this.computeShake(elapsed);
               shakeX = shake[0];
               shakeY = shake[1];
            }

            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "icons/honor/honor_" + this.currentDisplay.honorId + ".png");
            float iconScale = scaleFactor * 0.4F;
            this.applyTextureFilter(texture);
            drawIcon(guiGraphics, texture, originX + shakeX, originY + shakeY, iconScale, alpha, this.isBest() ? this.configBestIconColor : 16777215);
            float hintFadeAlpha = elapsed >= fadeStartMs ? this.resolveFadeAlpha(fadeProgress) : 1.0F;
            this.drawHintBox(guiGraphics, originX + shakeX, originY + shakeY, 110.0F * iconScale, elapsed, hintFadeAlpha);
         }
      }
   }

   private void drawHintBox(GuiGraphics guiGraphics, float centerX, float centerY, float iconW, long elapsed, float fadeAlpha) {
      if (this.configHintBoxEnabled && this.currentDisplay != null) {
         Minecraft mc = Minecraft.getInstance();
         String subtitle = this.resolveSubtitle();
         if (subtitle != null) {
            float textW = (float)mc.font.width(subtitle) * this.configHintBoxTextScale;
            float iconLeft = centerX - iconW / 2.0F;
            float textRight = iconLeft - (float)this.configHintBoxTextGap;
            float textLeft = textRight - textW;
            float boxStart = centerX + (float)this.configHintBoxXOffset;
            float boxLeftFinal = textLeft - (float)this.configHintBoxTextPadding;
            float boxRightFinal = textRight + (float)this.configHintBoxTextPadding;
            float p = Math.min(1.0F, (float)elapsed / (float)this.configHintBoxEnterMs);
            float eased = easeOutCubic(p);
            float boxLeft = Mth.lerp(eased, boxStart, boxLeftFinal);
            float boxRight = Mth.lerp(eased, boxStart, boxRightFinal);
            float maxAlpha = 1.0F - Mth.clamp(this.configHintBoxMaxAlpha, 0.0F, 100.0F) / 100.0F;
            float alpha = Math.min(maxAlpha * eased, fadeAlpha);
            if (!(alpha <= 0.01F)) {
               if (this.isBest()) {
                  this.drawBestSweep(guiGraphics, textRight, boxLeftFinal, centerY, elapsed);
               }

               int boxColorRgb = this.isBest() ? this.configBestHintBoxColor : this.configHintBoxColor;
               int boxColor = (int)(alpha * 255.0F) << 24 | boxColorRgb & 16777215;
               float boxTop = centerY - (float)this.configHintBoxHeight / 2.0F;
               fillRectF(guiGraphics, boxLeft, boxTop, boxRight, boxTop + (float)this.configHintBoxHeight, boxColor);
               if (elapsed >= this.configHintBoxEnterMs / 2L) {
                  float tp = Math.min(
                     1.0F, ((float)elapsed - (float)this.configHintBoxEnterMs / 2.0F) / Math.max(1.0F, (float)this.configHintBoxEnterMs / 2.0F)
                  );
                  float textAlpha = Math.min(maxAlpha * easeOutCubic(tp), fadeAlpha);
                  if (textAlpha > 0.01F) {
                     int textColor = (int)(textAlpha * 255.0F) << 24 | this.configHintBoxTextColor & 16777215;
                     guiGraphics.pose().pushPose();
                     guiGraphics.pose().translate(textLeft, centerY, 0.0F);
                     guiGraphics.pose().scale(this.configHintBoxTextScale, this.configHintBoxTextScale, 1.0F);
                     guiGraphics.pose().translate(0,-4.5f,0);
                     guiGraphics.drawString(mc.font, subtitle, 0, 0, textColor, this.configHintBoxTextShadow);
                     guiGraphics.pose().popPose();
                  }
               }

               if (this.isBest()) {
                  this.drawBestText(guiGraphics, boxLeftFinal, boxTop, elapsed, fadeAlpha);
               }
            }
         }
      }
   }

   private void drawBestText(GuiGraphics guiGraphics, float boxLeft, float hintBoxTop, long elapsed, float fadeAlpha) {
      if (this.currentDisplay != null) {
         String text = this.resolveBestSubtitle();
         if (text != null && !text.isEmpty()) {
            if (elapsed >= this.configHintBoxEnterMs) {
               Minecraft mc = Minecraft.getInstance();
               float textW = (float)mc.font.width(text) * this.configHintBoxTextScale;
               float textH = 9.0F * this.configHintBoxTextScale;
               float top = hintBoxTop - textH;
               float textCenterY = hintBoxTop - textH / 2.0F;
               long fadeInMs = Math.max(1L, (long)(this.configBestTextFadeInDuration * 1000.0F));
               float p = Math.min(1.0F, (float)(elapsed - this.configHintBoxEnterMs) / (float)fadeInMs);
               float maxAlpha = 1.0F - Mth.clamp(this.configHintBoxMaxAlpha, 0.0F, 100.0F) / 100.0F;
               float alpha = Math.min(maxAlpha * easeOutCubic(p), fadeAlpha);
               if (!(alpha <= 0.01F)) {
                  int boxRgb = this.configBestHintBoxColor;
                  int boxColor = (int)(alpha * 255.0F) << 24 | boxRgb & 16777215;
                  fillRectF(guiGraphics, boxLeft, top, boxLeft + textW, hintBoxTop, boxColor);
                  int textColor = (int)(alpha * 255.0F) << 24 | this.configHintBoxTextColor & 16777215;
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(boxLeft, textCenterY, 0.0F);
                  guiGraphics.pose().scale(this.configHintBoxTextScale, this.configHintBoxTextScale, 1.0F);
                  guiGraphics.pose().translate(0,-4.5f,0);
                     guiGraphics.drawString(mc.font, text, 0, 0, textColor, this.configHintBoxTextShadow);
                  guiGraphics.pose().popPose();
               }
            }
         }
      }
   }

   private void drawBestSweep(GuiGraphics guiGraphics, float textRight, float boxLeft, float centerY, long elapsed) {
      long sweepMs = Math.max(1L, (long)(this.configBestSweepDuration * 1000.0F));
      long fadeMs = Math.max(1L, (long)(this.configBestSweepFadeDuration * 1000.0F));
      float endRight = boxLeft - (float)this.configBestSweepTextGap;
      float endLeft = endRight - (float)this.configBestSweepEndWidth;
      float startLeft = textRight - (float)this.configBestSweepStartWidth;
      float pos = Math.min(1.0F, (float)elapsed / (float)sweepMs);
      float eased = easeOutCubic(pos);
      float right = Mth.lerp(eased, textRight, endRight);
      float left = Mth.lerp(eased, startLeft, endLeft);
      float totalMs = (float)(sweepMs + fadeMs);
      float opacity = Math.min(1.0F, (float)elapsed / totalMs);
      float alpha = 1.0F - opacity;
      if (!(alpha <= 0.01F)) {
         int color = (int)(alpha * 255.0F) << 24 | this.configBestHintBoxColor & 16777215;
         float top = centerY - (float)this.configHintBoxHeight / 2.0F;
         fillRectF(guiGraphics, left, top, right, top + (float)this.configHintBoxHeight, color);
      }
   }

   private static void drawIcon(GuiGraphics guiGraphics, ResourceLocation texture, float centerX, float centerY, float iconScale, float alpha, int tintRgb) {
      if (!(alpha <= 0.001F) && !(iconScale <= 0.001F)) {
         int a = (int)(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
         int r = tintRgb >> 16 & 0xFF;
         int g = tintRgb >> 8 & 0xFF;
         int b = tintRgb & 0xFF;
         float w = 110.0F * iconScale;
         float h = 61.0F * iconScale;
         float x1 = centerX - w / 2.0F;
         float y1 = centerY - h / 2.0F;
         float x2 = centerX + w / 2.0F;
         float y2 = centerY + h / 2.0F;
         Matrix4f matrix = guiGraphics.pose().last().pose();
         RenderSystem.setShaderTexture(0, texture);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableDepthTest();
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder;
         builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         builder.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a).setUv(0.0F, 0.0F);
         builder.addVertex(matrix, x2, y1, 0.0F).setColor(r, g, b, a).setUv(1.0F, 0.0F);
         builder.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a).setUv(1.0F, 1.0F);
         builder.addVertex(matrix, x1, y2, 0.0F).setColor(r, g, b, a).setUv(0.0F, 1.0F);
         BufferUploader.drawWithShader(builder.buildOrThrow());
         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         RenderSystem.disableBlend();
      }
   }

   private void applyTextureFilter(ResourceLocation texture) {
      boolean filter = ClientConfigManager.isEnableIconAntialiasing();
      if (!this.hasAppliedFilter || filter != this.lastAppliedFilter) {
         IconTextureFilterEffect.apply(texture);
         this.lastAppliedFilter = filter;
         this.hasAppliedFilter = true;
      }
   }

   private String resolveSubtitle() {
      String formatKey = "format_" + this.currentDisplay.honorId;
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "honor");
      if (config != null && config.has(formatKey)) {
         String custom = config.get(formatKey).getAsString();
         return custom.isEmpty() ? null : custom;
      } else {
         return FormatDefaultsManager.getDefault("kill_icon/honor", formatKey);
      }
   }

   private String resolveBestSubtitle() {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "honor");
      if (config != null && config.has("best_text_format")) {
         String custom = config.get("best_text_format").getAsString();
         if (custom != null && !custom.isEmpty()) {
            return custom;
         }
      }

      String langKey = "battlefield_ops.honor.best.format";
      if (I18n.exists(langKey)) {
         String resolved = I18n.get(langKey, new Object[0]);
         if (resolved != null && !resolved.isEmpty() && !resolved.equals(langKey)) {
            return resolved;
         }
      }

      return "新最佳";
   }

   private static void fillRectF(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2, int argb) {
      if (!(x2 <= x1) && !(y2 <= y1)) {
         int a = argb >>> 24 & 0xFF;
         int r = argb >> 16 & 0xFF;
         int g = argb >> 8 & 0xFF;
         int b = argb & 0xFF;
         Matrix4f matrix = guiGraphics.pose().last().pose();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableDepthTest();
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         quad(builder, matrix, x1, y1, x2, y2, r, g, b, a);
         BufferUploader.drawWithShader(builder.buildOrThrow());
         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         RenderSystem.disableBlend();
      }
   }

   private static void quad(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, int r, int g, int b, int a) {
      builder.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
      builder.addVertex(matrix, x2, y1, 0.0F).setColor(r, g, b, a);
      builder.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);
      builder.addVertex(matrix, x1, y2, 0.0F).setColor(r, g, b, a);
   }

   private long[] advanceTimeline(long now, boolean playSound) {
      if (this.currentDisplay == null) {
         return null;
      } else {
         long t0 = this.currentDisplay.triggerTime + this.configDisplayDelayMs;
         long elapsed = now - t0;
         if (elapsed < 0L) {
            return null;
         } else {
            if (playSound && !this.displaySoundPlayed) {
               this.playDisplaySound();
               this.displaySoundPlayed = true;
            }

            boolean hasNext = !this.pendingHonorIds.isEmpty();
            long fadeStartMs = hasNext ? this.configMinDisplayMs : this.configDisplayDurationMs;
            long total = fadeStartMs + this.configFadeOutMs;
            if (elapsed >= total) {
               if (hasNext) {
                  HonorRenderer.Display next = this.pendingHonorIds.pollFirst();
                  this.currentDisplay = new HonorRenderer.Display(next.honorId, next.style, now);
                  this.displaySoundPlayed = false;
               } else {
                  this.currentDisplay = null;
                  this.displaySoundPlayed = false;
               }

               return null;
            } else {
               return new long[]{elapsed, fadeStartMs};
            }
         }
      }
   }

   private void playDisplaySound() {
      try {
         String slot = this.isBest() ? "honor_high" : "honor_normal";
         ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), slot);
      } catch (Exception var2) {
      }
   }

   private static float easeOutCubic(float t) {
      return 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
   }

   private float resolveFadeAlpha(float fadeProgress) {
      if (this.configBlinkFadeAnimation) {
         if (fadeProgress < 0.33333334F) {
            return 1.0F - 2.4F * fadeProgress;
         } else {
            return fadeProgress < 0.6666667F ? 2.4F * fadeProgress - 0.6F : 3.0F * (1.0F - fadeProgress);
         }
      } else {
         return 1.0F - fadeProgress;
      }
   }

   private static final class Display {
      final String honorId;
      final String style;
      final long triggerTime;

      Display(String honorId, String style, long triggerTime) {
         this.honorId = honorId;
         this.style = style == null ? "" : style;
         this.triggerTime = triggerTime;
      }
   }
}
