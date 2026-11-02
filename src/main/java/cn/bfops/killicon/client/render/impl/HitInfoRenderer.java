package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.TextFadeEffect;

public class HitInfoRenderer implements IHudRenderer {
   private static final String ELEMENT_ID = "subtitle/hit_info";
   private static final String DAMAGE_PLACEHOLDER = "<damage>";
   private static HitInfoRenderer instance;
   private boolean visible = true;
   private float scale = 1.0F;
   private int configXOffset = 0;
   private int configYOffset = 80;
   private String screenAnchor = "bottom_center";
   private long displayDurationMs = 3000L;
   private boolean alignLeft = false;
   private boolean alignRight = false;
   private String format = "<damage>";
   private int colorDamageDefault = -1;
   private int colorDamageKill = -2836480;
   private int colorNormalText = -1;
   private long fadeInDurationMs = 200L;
   private long fadeOutDurationMs = 300L;
   private boolean blinkFadeAnimation = false;
   private boolean enableDamageScroll = true;
   private float scrollDuration = 1.25F;
   private float scrollRefreshRate = 0.02F;
   private boolean enableTextShadow = true;
   private boolean enableGlowEffect = false;
   private float glowIntensity = 0.5F;
   private float glowSize = 0.3F;
   private int glowColorDefaultRgb = 16777215;
   private int glowColorKillRgb = 16777215;
   private float glowAlphaMultiplier = 1.0F;
   private boolean enableEntityLayers = false;
   private int maxLayers = 5;
   private float layerSpacing = 12.0F;
   private float layerMoveAnimDuration = 0.2F;
   private float layerCollapseAnimDuration = 0.2F;
   private boolean displaying = false;
   private float totalDamage = 0.0F;
   private boolean killed = false;
   private long startTime = -1L;
   private long textHideTime = -1L;
   private boolean isFadingOut = false;
   private float displayDamage = 0.0F;
   private float scrollFrom = 0.0F;
   private float scrollTarget = 0.0F;
   private long scrollStartTime = -1L;
   private boolean isScrolling = false;
   private final List<HitInfoRenderer.EntityLine> entityLines = new ArrayList<>();
   private long lastLayerRenderTime = 0L;
   private int previewStep = 0;

   public static synchronized HitInfoRenderer getInstance() {
      if (instance == null) {
         instance = new HitInfoRenderer();
      }

      return instance;
   }

   public void addDamage(int entityId, float amount) {
      JsonObject config = this.getConfig();
      if (config != null) {
         if (!config.has("visible") || config.get("visible").getAsBoolean()) {
            this.loadConfig(config);
            long now = PreviewRenderTimeContext.currentTimeMillis();
            if (this.enableEntityLayers) {
               this.addLayerDamage(entityId, amount, now);
            } else {
               if (!this.displaying) {
                  this.totalDamage = amount;
                  this.killed = false;
                  this.startTime = now;
                  this.textHideTime = now + this.displayDurationMs;
                  this.isFadingOut = false;
                  this.displaying = true;
                  this.beginScroll(0.0F, this.totalDamage, now);
               } else {
                  this.totalDamage += amount;
                  this.textHideTime = now + this.displayDurationMs;
                  this.beginScroll(this.displayDamage, this.totalDamage, now);
               }
            }
         }
      }
   }

   public void markKill(int entityId) {
      if (this.enableEntityLayers) {
         for (HitInfoRenderer.EntityLine line : this.entityLines) {
            if (line.entityId == entityId && !line.isFading) {
               line.killed = true;
               line.lastHitTime = PreviewRenderTimeContext.currentTimeMillis();
               return;
            }
         }
      } else {
         this.killed = true;
         if (this.displaying) {
            long now = PreviewRenderTimeContext.currentTimeMillis();
            this.textHideTime = now + this.displayDurationMs;
         }
      }
   }

   private void addLayerDamage(int entityId, float amount, long now) {
      for (HitInfoRenderer.EntityLine line : this.entityLines) {
         if (line.entityId == entityId && !line.isFading) {
            line.totalDamage += amount;
            line.lastHitTime = now;
            line.beginScroll(line.displayDamage, line.totalDamage, now);
            return;
         }
      }

      HitInfoRenderer.EntityLine linex = new HitInfoRenderer.EntityLine(entityId, amount, now);
      this.entityLines.add(0, linex);
      this.trimToMaxLayers();
   }

   private void trimToMaxLayers() {
      while (this.entityLines.size() > this.maxLayers * 2) {
         boolean removed = false;

         for (int i = this.entityLines.size() - 1; i >= 0; i--) {
            if (this.entityLines.get(i).isFading) {
               this.entityLines.remove(i);
               removed = true;
               break;
            }
         }

         if (!removed) {
            break;
         }
      }
   }

   public void reset() {
      this.displaying = false;
      this.totalDamage = 0.0F;
      this.killed = false;
      this.startTime = -1L;
      this.textHideTime = -1L;
      this.isFadingOut = false;
      this.displayDamage = 0.0F;
      this.isScrolling = false;
      this.entityLines.clear();
      this.lastLayerRenderTime = 0L;
   }

   @Override
   public void resetPreview() {
      this.reset();
   }

   private void beginScroll(float from, float to, long now) {
      this.scrollFrom = from;
      this.scrollTarget = to;
      this.scrollStartTime = now;
      this.isScrolling = true;
      this.displayDamage = from;
   }

   private float updateScroll(float damage, long now) {
      if (!this.enableDamageScroll) {
         this.displayDamage = this.totalDamage;
         return this.totalDamage;
      } else {
         if (this.isScrolling) {
            float elapsed = (float)(now - this.scrollStartTime);
            if (elapsed <= 0.0F) {
               return this.displayDamage;
            }

            float progress = Math.min(elapsed / (this.scrollDuration * 1000.0F), 1.0F);
            float eased = 1.0F - (float)Math.pow((double)(1.0F - progress), 5.0);
            this.displayDamage = this.scrollFrom + (this.scrollTarget - this.scrollFrom) * eased;
            if (progress >= 1.0F) {
               this.displayDamage = this.scrollTarget;
               this.isScrolling = false;
            }
         }

         return this.displayDamage;
      }
   }

   private void loadConfig(JsonObject config) {
      this.scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
      this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
      this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 80;
      this.screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
      this.displayDurationMs = config.has("display_duration") ? Math.max(1L, (long)(config.get("display_duration").getAsFloat() * 1000.0F)) : 3000L;
      this.alignLeft = config.has("align_left") && config.get("align_left").getAsBoolean();
      this.alignRight = config.has("align_right") && config.get("align_right").getAsBoolean();
      this.format = config.has("format_damage") ? config.get("format_damage").getAsString() : "<damage>";
      this.colorDamageDefault = parseColorHex(config, "color_damage_default", -1);
      this.colorDamageKill = parseColorHex(config, "color_damage_kill", -2836480);
      this.colorNormalText = parseColorHex(config, "color_normal_text", -1);
      this.fadeInDurationMs = config.has("fade_in_duration") ? Math.max(1L, (long)(config.get("fade_in_duration").getAsFloat() * 1000.0F)) : 200L;
      this.fadeOutDurationMs = config.has("fade_out_duration") ? Math.max(1L, (long)(config.get("fade_out_duration").getAsFloat() * 1000.0F)) : 300L;
      this.blinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
      this.enableDamageScroll = config.has("enable_damage_scroll") && config.get("enable_damage_scroll").getAsBoolean();
      this.scrollDuration = config.has("damage_scroll_duration") ? Math.max(0.05F, config.get("damage_scroll_duration").getAsFloat()) : 1.25F;
      this.scrollRefreshRate = config.has("damage_scroll_refresh_rate") ? config.get("damage_scroll_refresh_rate").getAsFloat() : 0.02F;
      this.enableTextShadow = !config.has("enable_text_shadow") || config.get("enable_text_shadow").getAsBoolean();
      this.enableGlowEffect = config.has("enable_glow_effect") && config.get("enable_glow_effect").getAsBoolean();
      this.glowIntensity = config.has("glow_intensity") ? config.get("glow_intensity").getAsFloat() : 0.5F;
      this.glowSize = config.has("glow_size") ? config.get("glow_size").getAsFloat() : 0.3F;
      this.glowColorDefaultRgb = parseColorHex(config, "glow_color_damage_default", 16777215) & 16777215;
      this.glowColorKillRgb = parseColorHex(config, "glow_color_damage_kill", 16777215) & 16777215;
      this.glowAlphaMultiplier = config.has("glow_alpha") ? Mth.clamp(config.get("glow_alpha").getAsFloat(), 0.0F, 1.0F) : 1.0F;
      this.enableEntityLayers = config.has("enable_entity_layers") && config.get("enable_entity_layers").getAsBoolean();
      this.maxLayers = config.has("max_layers") ? Math.max(1, config.get("max_layers").getAsInt()) : 5;
      this.layerSpacing = config.has("layer_spacing") ? Math.max(0.0F, config.get("layer_spacing").getAsFloat()) : 12.0F;
      this.layerMoveAnimDuration = config.has("layer_move_animation_duration")
         ? Math.max(0.05F, config.get("layer_move_animation_duration").getAsFloat())
         : 0.2F;
      this.layerCollapseAnimDuration = config.has("layer_collapse_animation_duration")
         ? Math.max(0.05F, config.get("layer_collapse_animation_duration").getAsFloat())
         : 0.2F;
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
   }

   public void triggerPreview() {
      this.previewStep++;
      if (this.previewStep % 5 == 0) {
         this.markKill(1000 + this.previewStep / 5 % 3);
      } else {
         int entityId = 1000 + this.previewStep % 3;
         this.addDamage(entityId, 1.0F + (float)ThreadLocalRandom.current().nextInt(20));
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.enableEntityLayers) {
         if (this.entityLines.isEmpty()) {
            return;
         }
      } else if (!this.displaying || this.startTime < 0L) {
         return;
      }

      JsonObject config = this.getConfig();
      if (config != null) {
         if (config.has("visible") && !config.get("visible").getAsBoolean()) {
            return;
         }

         this.loadConfig(config);
      }

      Minecraft mc = Minecraft.getInstance();
      int centerX = ScreenAnchor.resolveCenterX(this.screenAnchor, this.configXOffset, mc.getWindow().getGuiScaledWidth());
      int centerY = ScreenAnchor.resolveCenterY(this.screenAnchor, this.configYOffset, mc.getWindow().getGuiScaledHeight());
      this.renderAt(guiGraphics, partialTick, (float)centerX, (float)centerY);
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      if (this.enableEntityLayers) {
         this.renderLayered(guiGraphics, partialTick, centerX, centerY);
      } else if (this.displaying && this.startTime >= 0L) {
         JsonObject liveConfig = this.getConfig();
         if (liveConfig != null) {
            if (liveConfig.has("visible") && !liveConfig.get("visible").getAsBoolean()) {
               return;
            }

            this.loadConfig(liveConfig);
         }

         long now = PreviewRenderTimeContext.currentTimeMillis();
         float displayDamage = this.updateScroll(this.totalDamage, now);
         if (!this.isFadingOut && now > this.textHideTime) {
            this.isFadingOut = true;
         }

         float alpha = 1.0F;
         if (this.isFadingOut) {
            float fadeProgress = (float)(now - this.textHideTime) / (float)this.fadeOutDurationMs;
            alpha = TextFadeEffect.fadeAlpha(Mth.clamp(fadeProgress, 0.0F, 1.0F), this.blinkFadeAnimation);
         }

         if (now >= this.textHideTime + this.fadeOutDurationMs) {
            this.reset();
         } else {
            long elapsed = now - this.startTime;
            if (elapsed < this.fadeInDurationMs) {
               float t = (float)elapsed / (float)this.fadeInDurationMs;
               float eased = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
               alpha *= eased;
            }

            if (!(alpha <= 0.05F)) {
               this.renderDamageLine(guiGraphics, centerX, centerY, displayDamage, this.killed, alpha);
            }
         }
      }
   }

   private void renderLayered(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      JsonObject liveConfig = this.getConfig();
      if (liveConfig != null) {
         if (liveConfig.has("visible") && !liveConfig.get("visible").getAsBoolean()) {
            return;
         }

         this.loadConfig(liveConfig);
      }

      if (!this.entityLines.isEmpty()) {
         long now = PreviewRenderTimeContext.currentTimeMillis();
         float dt = this.lastLayerRenderTime == 0L ? 0.016F : (float)(now - this.lastLayerRenderTime) / 1000.0F;
         this.lastLayerRenderTime = now;
         if (dt > 0.1F) {
            dt = 0.1F;
         }

         boolean removedAny = false;
         Iterator<HitInfoRenderer.EntityLine> it = this.entityLines.iterator();
         int index = 0;

         while (it.hasNext()) {
            HitInfoRenderer.EntityLine line = it.next();
            if (!line.isFading && now > line.lastHitTime + this.displayDurationMs) {
               line.startFade(now);
            }

            if (line.isFading && now - line.fadeStartTime >= this.fadeOutDurationMs) {
               it.remove();
               removedAny = true;
            } else {
               float targetY = (float)index * this.layerSpacing;
               float animDur = removedAny ? this.layerCollapseAnimDuration : this.layerMoveAnimDuration;
               float smooth = 1.0F - (float)Math.exp((double)(-dt / Math.max(0.05F, animDur)));
               line.currentY = line.currentY + (targetY - line.currentY) * smooth;
               if (Math.abs(line.currentY - targetY) < 0.05F) {
                  line.currentY = targetY;
               }

               if (!line.isFading && index >= this.maxLayers) {
                  line.startFade(now);
               }

               float alpha = 1.0F;
               if (line.isFading) {
                  float fadeProgress = (float)(now - line.fadeStartTime) / (float)this.fadeOutDurationMs;
                  alpha = TextFadeEffect.fadeAlpha(Mth.clamp(fadeProgress, 0.0F, 1.0F), this.blinkFadeAnimation);
               }

               if (alpha <= 0.05F) {
                  index++;
               } else {
                  float display = line.updateScroll(now, this.enableDamageScroll, this.scrollDuration);
                  this.renderDamageLine(guiGraphics, centerX, centerY + line.currentY, display, line.killed, alpha);
                  index++;
               }
            }
         }
      }
   }

   private void renderDamageLine(GuiGraphics guiGraphics, float centerX, float centerY, float displayDamage, boolean lineKilled, float alpha) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      String damageStr = String.valueOf(Math.round(displayDamage));
      int alphaInt = (int)(alpha * 255.0F) << 24;
      int damageColor = (lineKilled ? this.colorDamageKill : this.colorDamageDefault) & 16777215 | alphaInt;
      int normalColor = this.colorNormalText & 16777215 | alphaInt;
      Component text = this.buildTextComponent(font, damageStr, damageColor, normalColor);
      int textWidth = font.width(text);
      int textX;
      if (this.alignLeft && !this.alignRight) {
         textX = Math.round(centerX);
      } else if (this.alignRight && !this.alignLeft) {
         textX = Math.round(centerX - (float)textWidth * this.scale);
      } else {
         textX = Math.round(centerX - (float)textWidth * this.scale / 2.0F);
      }

      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate((float)textX, centerY, 0.0F);
      guiGraphics.pose().scale(this.scale, this.scale, 1.0F);
      this.drawTextWithGlow(guiGraphics, font, text, 0, -9 / 2, normalColor, this.enableTextShadow, lineKilled);
      guiGraphics.pose().popPose();
   }

   private void drawTextWithGlow(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int color, boolean dropShadow, boolean lineKilled) {
      if (this.enableGlowEffect) {
         int alpha = color >> 24 & 0xFF;
         int glowAlpha = (int)((float)alpha * this.glowIntensity * this.glowAlphaMultiplier);
         glowAlpha = Math.max(25, Math.min(255, glowAlpha));
         int glowRgb = lineKilled ? this.glowColorKillRgb : this.glowColorDefaultRgb;
         int glowColor = glowRgb & 16777215 | glowAlpha << 24;
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
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(offset[0], offset[1], 0.0F);
            guiGraphics.drawString(font, stripColor(text), x, y, glowColor, false);
            guiGraphics.pose().popPose();
         }
      }

      guiGraphics.drawString(font, text, x, y, color, dropShadow);
   }

   private Component buildTextComponent(Font font, String damageStr, int damageColor, int normalColor) {
      Component text;
      if (this.format.contains("<damage>")) {
         int idx = this.format.indexOf("<damage>");
         String prefix = this.format.substring(0, idx);
         String suffix = this.format.substring(idx + "<damage>".length());
         text = Component.literal(prefix)
            .withStyle(style -> style.withColor(normalColor & 16777215))
            .append(Component.literal(damageStr).withStyle(style -> style.withColor(damageColor & 16777215)))
            .append(Component.literal(suffix).withStyle(style -> style.withColor(normalColor & 16777215)));
      } else {
         text = Component.literal(this.format).withStyle(style -> style.withColor(normalColor & 16777215));
      }

      return text;
   }

   private JsonObject getConfig() {
      return ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/hit_info");
   }

   private static Component stripColor(Component component) {
      Style style = component.getStyle();
      Style stripped = (style == null ? Style.EMPTY : style).withColor((TextColor)null);
      MutableComponent result = component.copy();
      result.setStyle(stripped);

      for (int i = 0; i < result.getSiblings().size(); i++) {
         result.getSiblings().set(i, stripColor((Component)result.getSiblings().get(i)));
      }

      return result;
   }

   private static int parseColorHex(JsonObject config, String key, int fallback) {
      if (config != null && config.has(key)) {
         try {
            String hex = config.get(key).getAsString();
            return (int)Long.parseLong(hex.startsWith("#") ? hex.substring(1) : hex, 16) | 0xFF000000;
         } catch (Exception var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static class EntityLine {
      final int entityId;
      float totalDamage;
      boolean killed;
      long lastHitTime;
      boolean isFading;
      long fadeStartTime;
      float currentY;
      float displayDamage;
      float scrollFrom;
      float scrollTarget;
      long scrollStartTime;
      boolean isScrolling;

      EntityLine(int entityId, float amount, long now) {
         this.entityId = entityId;
         this.totalDamage = amount;
         this.lastHitTime = now;
         this.beginScroll(0.0F, amount, now);
      }

      void startFade(long now) {
         if (!this.isFading) {
            this.isFading = true;
            this.fadeStartTime = now;
         }
      }

      void beginScroll(float from, float to, long now) {
         this.scrollFrom = from;
         this.scrollTarget = to;
         this.scrollStartTime = now;
         this.isScrolling = true;
         this.displayDamage = from;
      }

      float updateScroll(long now, boolean enabled, float duration) {
         if (!enabled) {
            this.displayDamage = this.totalDamage;
            return this.totalDamage;
         } else {
            if (this.isScrolling) {
               float elapsed = (float)(now - this.scrollStartTime);
               if (elapsed <= 0.0F) {
                  return this.displayDamage;
               }

               float progress = Math.min(elapsed / (duration * 1000.0F), 1.0F);
               float eased = 1.0F - (float)Math.pow((double)(1.0F - progress), 5.0);
               this.displayDamage = this.scrollFrom + (this.scrollTarget - this.scrollFrom) * eased;
               if (progress >= 1.0F) {
                  this.displayDamage = this.scrollTarget;
                  this.isScrolling = false;
               }
            }

            return this.displayDamage;
         }
      }
   }
}
