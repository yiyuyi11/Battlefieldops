package cn.bfops.killicon.client.render.impl;

import cn.bfops.Draw;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.FormatDefaultsManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.TextFadeEffect;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;

public class ComboSubtitleRenderer implements IHudRenderer {
   private static final long FADE_IN_DURATION = 200L;
   private static final long FADE_OUT_DURATION = 200L;
   private static final long EXIT_ANIMATION_DURATION = 500L;
   private static final long LIGHT_SCAN_DURATION = 400L;
   private static final float LIGHT_SCAN_DISTANCE = 20.0F;
   private static final long LIGHT_STRIP_FADE_DELAY = 200L;
   private static final long LIGHT_STRIP_FADE_OUT_DURATION = 200L;
   private static ComboSubtitleRenderer instance;
   private boolean visible = true;
   private float scale = 1.5F;
   private int xOffset = 0;
   private int yOffset = 150;
   private String screenAnchor = "bottom_center";
   private int colorKillCombo = 16711680;
   private int colorAssistCombo = 16766720;
   private String formatKillSingle = "";
   private String formatKillMulti = "";
   private String formatAssistSingle = "";
   private String formatAssistMulti = "";
   private boolean enableAnimation = true;
   private boolean enableLightEffect = true;
   private boolean enableBold = true;
   private boolean enableTextShadow = true;
   private boolean configBlinkFadeAnimation = false;
   private double lightHeight = 12.0;
   private double lightHoldDuration = 0.2;
   private boolean enableScaleAnimation = false;
   private long displayDuration = 5000L;
   private String resetKillCombo = "death";
   private String resetAssistCombo = "death";
   private float comboResetTimeout = 10.0F;
   private long startTime = -1L;
   private boolean isVisible = false;
   private int currentCombo = 0;
   private boolean isAssist = false;
   private final Deque<ComboSubtitleRenderer.ComboItem> pendingQueue = new ArrayDeque<>();
   private long lastDequeueTime = 0L;
   private int localKillComboCount = 0;
   private int localAssistComboCount = 0;
   private long lastKillTime = 0L;
   private long lastAssistTime = 0L;
   private float lastScanX = 0.0F;

   private ComboSubtitleRenderer() {
   }

   public static synchronized ComboSubtitleRenderer getInstance() {
      if (instance == null) {
         instance = new ComboSubtitleRenderer();
      }

      return instance;
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "combo");
      if (config != null) {
         this.loadConfig(config);
         if (!this.visible) {
            this.isVisible = false;
         } else {
            long now = PreviewRenderTimeContext.currentTimeMillis();
            if (context.type() != 5) {
               this.isAssist = context.type() == 4;
               this.checkResetTimeout(now);
               boolean isAssistEvent = this.isAssist;
               int newCombo;
               if (this.isAssist) {
                  if ("server".equals(this.resetAssistCombo)) {
                     newCombo = context.comboCount();
                     this.localAssistComboCount = newCombo;
                  } else if ("never".equals(this.resetAssistCombo)) {
                     newCombo = (int)ClientStatsManager.getTotalAssists() + 1;
                     this.localAssistComboCount = newCombo;
                  } else {
                     this.localAssistComboCount++;
                     newCombo = this.localAssistComboCount;
                     this.lastAssistTime = now;
                  }
               } else if ("server".equals(this.resetKillCombo)) {
                  newCombo = context.comboCount();
                  this.localKillComboCount = newCombo;
               } else if ("never".equals(this.resetKillCombo)) {
                  newCombo = (int)ClientStatsManager.getTotalKills();
                  this.localKillComboCount = newCombo;
               } else {
                  this.localKillComboCount++;
                  newCombo = this.localKillComboCount;
                  this.lastKillTime = now;
               }

               if (this.pendingQueue.size() < 10) {
                  this.pendingQueue.add(new ComboSubtitleRenderer.ComboItem(newCombo, isAssistEvent));
               }

               this.isVisible = true;
            }
         }
      }
   }

   public void resetKillCombo() {
      this.localKillComboCount = 0;
   }

   public void resetAssistCombo() {
      this.localAssistComboCount = 0;
   }

   public void onPlayerDeath() {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "combo");
      if (config != null) {
         this.loadConfig(config);
      }

      boolean resetKill = "death".equals(this.resetKillCombo);
      boolean resetAssist = "death".equals(this.resetAssistCombo);
      if (resetKill) {
         this.localKillComboCount = 0;
         this.lastKillTime = 0L;
      }

      if (resetAssist) {
         this.localAssistComboCount = 0;
         this.lastAssistTime = 0L;
      }

      if (resetKill || resetAssist) {
         this.pendingQueue.clear();
         this.currentCombo = 0;
         this.isVisible = false;
         this.startTime = -1L;
         this.lastDequeueTime = 0L;
         this.lastScanX = 0.0F;
      }
   }

   public void onPlayerLogout() {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "combo");
      if (config != null) {
         this.loadConfig(config);
      }

      if (!"never".equals(this.resetKillCombo)) {
         this.localKillComboCount = 0;
         this.lastKillTime = 0L;
      }

      if (!"never".equals(this.resetAssistCombo)) {
         this.localAssistComboCount = 0;
         this.lastAssistTime = 0L;
      }

      this.pendingQueue.clear();
      this.currentCombo = 0;
      this.isVisible = false;
      this.startTime = -1L;
      this.lastDequeueTime = 0L;
      this.lastScanX = 0.0F;
   }

   private void checkResetTimeout(long now) {
      long timeoutMs = (long)(this.comboResetTimeout * 1000.0F);
      if ("time".equals(this.resetKillCombo) && now - this.lastKillTime > timeoutMs) {
         this.localKillComboCount = 0;
      }

      if ("time".equals(this.resetAssistCombo) && now - this.lastAssistTime > timeoutMs) {
         this.localAssistComboCount = 0;
      }
   }

   public void triggerPreview(boolean assist, int combo) {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "combo");
      if (config != null) {
         this.loadConfig(config);
         if (!this.visible) {
            this.isVisible = false;
         } else {
            this.isAssist = assist;
            this.currentCombo = combo;
            this.startTime = PreviewRenderTimeContext.currentTimeMillis();
            this.isVisible = true;
            this.pendingQueue.clear();
            if (this.enableLightEffect) {
               this.lastScanX = 0.0F;
            }
         }
      }
   }

   @Override
   public void resetPreview() {
      this.pendingQueue.clear();
      this.isVisible = false;
      this.startTime = -1L;
      this.currentCombo = 0;
      this.isAssist = false;
      this.lastDequeueTime = 0L;
      this.lastScanX = 0.0F;
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      this.processQueue();
      ComboSubtitleRenderer.RenderState state = this.resolveRenderState();
      if (state != null) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         int centerX = ScreenAnchor.resolveCenterX(this.screenAnchor, this.xOffset, screenWidth);
         int centerY = ScreenAnchor.resolveCenterY(this.screenAnchor, this.yOffset, screenHeight);
         this.renderInternal(guiGraphics, partialTick, centerX, centerY, state);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float x, float y) {
      this.processQueue();
      ComboSubtitleRenderer.RenderState state = this.resolveRenderState();
      if (state != null) {
         this.renderInternal(guiGraphics, partialTick, (int)x, (int)y, state);
      }
   }

   private void processQueue() {
      long now = PreviewRenderTimeContext.currentTimeMillis();
      if (!this.pendingQueue.isEmpty()) {
         if (now - this.lastDequeueTime >= 200L) {
            ComboSubtitleRenderer.ComboItem item = this.pendingQueue.poll();
            if (item != null) {
               this.currentCombo = item.combo;
               this.isAssist = item.isAssist;
               this.startTime = now;
               this.lastDequeueTime = now;
               this.isVisible = true;
               if (this.enableLightEffect) {
                  this.lastScanX = 0.0F;
               }
            }
         }
      }
   }

   private ComboSubtitleRenderer.RenderState resolveRenderState() {
      if (this.isVisible && this.startTime != -1L) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         long elapsed = currentTime - this.startTime;
         if (elapsed > this.displayDuration + 500L) {
            this.isVisible = false;
            this.startTime = -1L;
            return null;
         } else {
            float alpha = 1.0F;
            if (this.enableAnimation) {
               if (elapsed < 200L) {
                  alpha = (float)elapsed / 200.0F;
               } else if (elapsed > this.displayDuration) {
                  alpha = TextFadeEffect.fadeAlpha((float)(elapsed - this.displayDuration) / 500.0F, this.configBlinkFadeAnimation);
               }
            }

            alpha = Mth.clamp(alpha, 0.0F, 1.0F);
            if (alpha <= 0.05F && elapsed > this.displayDuration) {
               this.isVisible = false;
               this.startTime = -1L;
               return null;
            } else {
               float currentScale = this.scale;
               if (this.enableAnimation && this.enableScaleAnimation && elapsed < 200L) {
                  float progress = (float)elapsed / 200.0F;
                  float easedProgress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
                  currentScale = Mth.lerp(easedProgress, this.scale * 1.5F, this.scale);
               }

               return new ComboSubtitleRenderer.RenderState(elapsed, alpha, currentScale);
            }
         }
      } else {
         return null;
      }
   }

   private void renderInternal(GuiGraphics guiGraphics, float partialTick, int centerX, int centerY, ComboSubtitleRenderer.RenderState state) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      String formatKey;
      if (this.isAssist) {
         formatKey = this.currentCombo > 1 ? this.formatAssistMulti : this.formatAssistSingle;
      } else {
         formatKey = this.currentCombo > 1 ? this.formatKillMulti : this.formatKillSingle;
      }

      String text = formatKey.replace("<combo>", String.valueOf(this.currentCombo));
      int color = this.isAssist ? this.colorAssistCombo : this.colorKillCombo;
      int textWidth = font.width(text);
      int textHeight = 9;
      float textHalfWidth = (float)textWidth / 2.0F;
      float textHalfHeight = (float)textHeight / 2.0F;
      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();
      poseStack.translate((float)centerX, (float)centerY, 0.0F);
      poseStack.scale(state.currentScale, state.currentScale, 1.0F);
      if (this.enableLightEffect) {
         this.renderLightEffect(guiGraphics, poseStack, state.elapsed, textWidth, textHeight);
      }

      int alphaInt = (int)(state.alpha * 255.0F);
      int finalColor = color & 16777215 | alphaInt << 24;
      if (this.enableBold) {
         Component comp = Component.literal(text).withStyle(style -> style.withBold(true));
         textWidth = font.width(comp);
         textHalfWidth = (float)textWidth / 2.0F;
         guiGraphics.drawString(font, comp, (int)(-textHalfWidth), (int)(-textHalfHeight), finalColor, this.enableTextShadow);
      } else {
         guiGraphics.drawString(font, text, (int)(-textHalfWidth), (int)(-textHalfHeight), finalColor, this.enableTextShadow);
      }

      poseStack.popPose();
   }

   private void renderLightEffect(GuiGraphics guiGraphics, PoseStack poseStack, long elapsed, int width, int height) {
      long holdDurationMs = (long)(this.lightHoldDuration * 1000.0);
      if (elapsed <= 400L + holdDurationMs + 200L) {
         float currentScanX = 20.0F;
         if (elapsed < 400L) {
            float progress = (float)elapsed / 400.0F;
            float ease = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
            currentScanX = ease * 20.0F;
         }

         float baseAlpha = 1.0F;
         if (elapsed > 400L + holdDurationMs) {
            long fadeOutElapsed = elapsed - (400L + holdDurationMs);
            if (fadeOutElapsed < 200L) {
               float fadeOutProgress = (float)fadeOutElapsed / 200.0F;
               baseAlpha = Mth.lerp(fadeOutProgress, 1.0F, 0.0F);
            } else {
               baseAlpha = 0.0F;
            }
         }

         if (!(baseAlpha <= 0.01F)) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer = Draw.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float halfHeight = (float)this.lightHeight / 2.0F;
            float yOffset = 1.0F;
            int r = 255;
            int g = 255;
            int b = 255;
            int aCenter = (int)(200.0F * baseAlpha);
            int aEdge = 0;
            buffer.vertex(poseStack.last().pose(), 0.0F, -halfHeight + yOffset, 0.0F).color(r, g, b, aCenter);
            buffer.vertex(poseStack.last().pose(), -currentScanX, -halfHeight + yOffset, 0.0F).color(r, g, b, aEdge);
            buffer.vertex(poseStack.last().pose(), -currentScanX, halfHeight + yOffset, 0.0F).color(r, g, b, aEdge);
            buffer.vertex(poseStack.last().pose(), 0.0F, halfHeight + yOffset, 0.0F).color(r, g, b, aCenter);
            buffer.vertex(poseStack.last().pose(), currentScanX, -halfHeight + yOffset, 0.0F).color(r, g, b, aEdge);
            buffer.vertex(poseStack.last().pose(), 0.0F, -halfHeight + yOffset, 0.0F).color(r, g, b, aCenter);
            buffer.vertex(poseStack.last().pose(), 0.0F, halfHeight + yOffset, 0.0F).color(r, g, b, aCenter);
            buffer.vertex(poseStack.last().pose(), currentScanX, halfHeight + yOffset, 0.0F).color(r, g, b, aEdge);
            Draw.end(buffer);
         }
      }
   }

   private void loadConfig(JsonObject config) {
      try {
         this.visible = config.has("visible") ? config.get("visible").getAsBoolean() : true;
         this.scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.5F;
         this.xOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.yOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 150;
         this.screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.colorKillCombo = this.parseColor(config, "color_kill_combo", 16711680);
         this.colorAssistCombo = this.parseColor(config, "color_assist_combo", 16766720);
         this.formatKillSingle = this.getFormat(config, "format_kill_single", FormatDefaultsManager.getDefault("subtitle/combo", "format_kill_single"));
         this.formatKillMulti = this.getFormat(config, "format_kill_multi", FormatDefaultsManager.getDefault("subtitle/combo", "format_kill_multi"));
         this.formatAssistSingle = this.getFormat(config, "format_assist_single", FormatDefaultsManager.getDefault("subtitle/combo", "format_assist_single"));
         this.formatAssistMulti = this.getFormat(config, "format_assist_multi", FormatDefaultsManager.getDefault("subtitle/combo", "format_assist_multi"));
         this.enableAnimation = config.has("enable_animation") ? config.get("enable_animation").getAsBoolean() : true;
         this.enableLightEffect = config.has("enable_light_effect") ? config.get("enable_light_effect").getAsBoolean() : true;
         this.enableBold = config.has("enable_bold") ? config.get("enable_bold").getAsBoolean() : true;
         this.enableTextShadow = !config.has("enable_text_shadow") || config.get("enable_text_shadow").getAsBoolean();
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.lightHeight = config.has("light_height") ? config.get("light_height").getAsDouble() : 12.0;
         this.lightHoldDuration = config.has("light_hold_duration") ? config.get("light_hold_duration").getAsDouble() : 0.2;
         this.enableScaleAnimation = config.has("enable_scale_animation") ? config.get("enable_scale_animation").getAsBoolean() : false;
         this.displayDuration = config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 5000L;
         this.resetKillCombo = config.has("reset_kill_combo") ? config.get("reset_kill_combo").getAsString() : "death";
         this.resetAssistCombo = config.has("reset_assist_combo") ? config.get("reset_assist_combo").getAsString() : "death";
         this.comboResetTimeout = config.has("combo_reset_timeout") ? config.get("combo_reset_timeout").getAsFloat() : 10.0F;
      } catch (Exception var3) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.config_error");
      }
   }

   private int parseColor(JsonObject config, String key, int def) {
      if (!config.has(key)) {
         return def;
      } else {
         String hex = config.get(key).getAsString();

         try {
            if (hex.startsWith("#")) {
               hex = hex.substring(1);
            }

            return Integer.parseInt(hex, 16);
         } catch (NumberFormatException var6) {
            return def;
         }
      }
   }

   private String getFormat(JsonObject config, String key, String def) {
      return !config.has(key) ? def : config.get(key).getAsString();
   }

   private static class ComboItem {
      int combo;
      boolean isAssist;

      ComboItem(int combo, boolean isAssist) {
         this.combo = combo;
         this.isAssist = isAssist;
      }
   }

   private static final class RenderState {
      private final long elapsed;
      private final float alpha;
      private final float currentScale;

      private RenderState(long elapsed, float alpha, float currentScale) {
         this.elapsed = elapsed;
         this.alpha = alpha;
         this.currentScale = currentScale;
      }
   }
}
