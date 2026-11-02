package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.FormatDefaultsManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.SubtitleEntranceBackground;
import cn.bfops.killicon.client.render.effect.TextFadeEffect;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.killtype.KillTypeDefinition;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class SubtitleRenderer implements IHudRenderer {
   @Override public void resetPreview(){stackedItems.clear();pendingQueue.clear();isVisible=false;startTime=-1;textHideTime=-1;}

   private static final SubtitleRenderer INSTANCE = new SubtitleRenderer();
   private static final long FADE_IN_DURATION = 200L;
   private static final long FADE_OUT_DURATION = 300L;
   private static final int DEFAULT_PLACEHOLDER_COLOR = -16741493;
   private static final int DEFAULT_EMPHASIS_COLOR = -1;
   private static final int PREVIEW_SCORE_VICTIM_ID = -9999;
   private static final String RUSH_BOMB_PLANTED_CAPTURE_FORMAT = "format_rush_bomb_planted_capture";
   private static final String RUSH_BOMB_DEFUSED_CAPTURE_FORMAT = "format_rush_bomb_defused_capture";
   private static final String RUSH_OBJECTIVE_DESTROYED_CAPTURE_FORMAT = "format_rush_objective_destroyed_capture";
   private int configXOffset = 0;
   private int configYOffset = 20;
   private String configScreenAnchor = "bottom_center";
   private long displayDuration = 3000L;
   private String format = "";
   private int placeholderColor = -16741493;
   private boolean enablePlaceholderBold = false;
   private float scale = 1.0F;
   private int emphasisColor = -1;
   private final Map<String, Boolean> killTypeEnableFlags = new HashMap<>();
   private boolean enableStacking = false;
   private boolean enableTextShadow = true;
   private boolean configBlinkFadeAnimation = false;
   private boolean enableGlowEffect = false;
   private float glowIntensity = 0.5F;
   private float glowSize = 0.3F;
   private int glowColorRgb = 16777215;
   private int glowColorRescueRgb = 6541384;
   private float glowAlphaMultiplier = 1.0F;
   private boolean alignLeft = false;
   private boolean alignRight = false;
   private boolean configEnableFlashIn = true;
   private long fadeInDurationMs = 0L;
   private boolean enableQueueLinkage = false;
   private float queueLinkageScrollSpeed = 0.1F;
   private float queueLinkageIconYOffset = 0.0F;
   private boolean linkageConfigLoaded = false;
   private float linkedYOffset = 0.0F;
   private float linkageFromOffset = 0.0F;
   private long linkageStartTime = 0L;
   private float linkageTargetOffset = 0.0F;
   private float linkageBaseY = 0.0F;
   private boolean configEntranceBackground = false;
   private float configEntranceBgPeakTransparency = 0.2F;
   private long configEntranceBgFadeInMs = 66L;
   private long configEntranceBgSweepMs = 100L;
   private long configEntranceBgFadeOutMs = 133L;
   private int configEntranceBgColor = 16777215;
   private int maxLines = 5;
   private int lineSpacing = 12;
   private int normalTextColor = -1;
   private long startTime = -1L;
   private boolean isVisible = false;
   private boolean isPreview = false;
   private long textHideTime = -1L;
   private int currentKillType = 0;
   private int victimId = -1;
   private int currentVictimId = -1;
   private String currentScoreOverride = null;
   private String victimName = "";
   private ItemStack heldItem = ItemStack.EMPTY;
   private String currentWeaponName = "";
   private String rawFormat = "";
   private float currentDistance = 0.0F;
   private final List<SubtitleRenderer.SubtitleItem> stackedItems = new ArrayList<>();
   private final Deque<SubtitleRenderer.SubtitleItem> pendingQueue = new ArrayDeque<>();
   private long lastDequeueTime = 0L;
   private boolean enableScaleAnimation = false;

   public static SubtitleRenderer getInstance() {
      return INSTANCE;
   }

   public boolean hasVisibleSubtitle() {
      return this.isVisible;
   }

   public float getBaseTextY() {
      if (!this.linkageConfigLoaded) {
         JsonObject cfg = ConfigManager.getElementConfig("subtitle", "kill_feed");
         if (cfg != null) {
            this.loadConfig(cfg);
         }

         this.linkageConfigLoaded = true;
      }

      Minecraft mc = Minecraft.getInstance();
      int screenHeight = mc.getWindow().getGuiScaledHeight();
      return (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      JsonObject config = ConfigManager.getElementConfig("subtitle", "kill_feed");
      if (config != null) {
         this.isPreview = false;
         this.loadConfig(config);
         if (!config.has("visible") || !config.get("visible").getAsBoolean()) {
            this.isVisible = false;
         } else if (this.isKillTypeEnabled(context.type())) {
            int type = context.type();
            int entityId = context.entityId();
            Minecraft mc = Minecraft.getInstance();
            String rawExtra = context.extraData() == null ? "" : context.extraData();
            String captureWeaponToken = "";
            String captureFormatOverride = null;
            String captureScoreOverride = null;
            String bonusScoreStr = context.bonusScale() > 0.0F ? formatScore(context.bonusScale() * context.bonusMultiplier()) : null;
            String vName;
            if (!rawExtra.isEmpty()) {
               if (type == 6) {
                  String[] parts = rawExtra.split("\\|", 3);
                  captureWeaponToken = parts.length > 0 ? parts[0].trim() : "";
                  String captureTarget = parts.length > 1 ? parts[1].trim() : "";
                  captureFormatOverride = resolveRushCaptureFormat(captureWeaponToken);
                  if (captureFormatOverride != null) {
                     captureScoreOverride = isNumericToken(captureTarget) ? captureTarget : null;
                     captureWeaponToken = "";
                  }

                  String captureDisplayTarget = parts.length > 2 ? parts[2].trim() : captureTarget;
                  if (!captureDisplayTarget.isEmpty() && !isNumericToken(captureDisplayTarget)) {
                     vName = captureDisplayTarget;
                  } else if (!captureTarget.isEmpty() && !isNumericToken(captureTarget)) {
                     vName = captureTarget;
                  } else if (!captureWeaponToken.isEmpty()) {
                     vName = captureWeaponToken;
                  } else {
                     vName = I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
                  }
               } else if (type == 5) {
                  if (rawExtra.contains("|")) {
                     String[] partsx = rawExtra.split("\\|", 2);
                     vName = partsx[0];
                  } else {
                     vName = rawExtra;
                  }
               } else {
                  vName = rawExtra;
               }
            } else if (mc.level != null && entityId != -1) {
               Entity entity = mc.level.getEntity(entityId);
               if (entity != null) {
                  vName = entity.getDisplayName().getString();
               } else {
                  vName = I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
               }
            } else {
               vName = I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
            }

            ItemStack itemStack = ItemStack.EMPTY;
            String wName;
            if (type == 6) {
               itemStack = ItemStack.EMPTY;
               wName = captureWeaponToken != null && !captureWeaponToken.isEmpty()
                  ? (I18nCompat.exists(captureWeaponToken) ? I18n.get(captureWeaponToken, new Object[0]) : captureWeaponToken)
                  : I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
            } else if (mc.player != null) {
               if (mc.player.getVehicle() != null) {
                  itemStack = ItemStack.EMPTY;
                  wName = mc.player.getVehicle().getDisplayName().getString();
               } else {
                  itemStack = mc.player.getMainHandItem();
                  wName = itemStack.isEmpty() ? I18n.get("battlefield_ops.client.text.bare_hand", new Object[0]) : itemStack.getHoverName().getString();
               }
            } else {
               itemStack = ItemStack.EMPTY;
               wName = "Unknown";
            }

            String formatKey = formatKeyForType(type);
            if (!isKillFeedFormatEmpty(config, formatKey)) {
               String colorKey = placeholderColorKeyForType(type);
               String emphasisColorKey = emphasisColorKeyForType(type);
               String normalFormat = config.has("format_normal")
                  ? config.get("format_normal").getAsString()
                  : FormatDefaultsManager.getDefault("subtitle/kill_feed", KillTypeRegistry.get(type).formatKey());
               String resolvedFormat = config.has(formatKey) ? config.get(formatKey).getAsString() : normalFormat;
               if (captureFormatOverride != null && config.has(captureFormatOverride)) {
                  resolvedFormat = config.get(captureFormatOverride).getAsString();
               }

               String normalColorHex = config.has("color_normal_placeholder") ? config.get("color_normal_placeholder").getAsString() : "#008B8B";
               String chosenColorHex = config.has(colorKey) ? config.get(colorKey).getAsString() : normalColorHex;
               int pColor = parseColorHexOrDefault(chosenColorHex, -16741493);
               String emphasisHex = config.has(emphasisColorKey) ? config.get(emphasisColorKey).getAsString() : "#FFFFFF";
               int eColor = parseColorHexOrDefault(emphasisHex, -1);
               float dist = this.isNormalKillType(type) ? context.distance() : 0.0F;
               if (this.enableStacking) {
                  this.addItemToStack(
                     resolvedFormat,
                     pColor,
                     eColor,
                     wName,
                     vName,
                     this.displayDuration,
                     dist,
                     entityId,
                     type == 10 ? bonusScoreStr : (captureScoreOverride != null ? captureScoreOverride : bonusScoreStr),
                     type
                  );
               } else {
                  this.currentKillType = type;
                  this.victimId = entityId;
                  this.currentVictimId = entityId;
                  this.currentScoreOverride = type == 10 ? bonusScoreStr : (captureScoreOverride != null ? captureScoreOverride : bonusScoreStr);
                  this.victimName = vName;
                  this.heldItem = itemStack;
                  this.currentWeaponName = wName;
                  this.format = resolvedFormat;
                  this.placeholderColor = pColor;
                  this.emphasisColor = eColor;
                  this.currentDistance = dist;
                  if (this.displayDuration < 200L) {
                     this.displayDuration = 200L;
                  }

                  this.startTime = PreviewRenderTimeContext.currentTimeMillis();
                  this.textHideTime = this.startTime + this.displayDuration;
                  this.isVisible = true;
               }
            }
         }
      }
   }

   public void triggerPreview(int killType, String weaponName, String victimName) {
      this.currentKillType = killType;
      this.victimId = -9999;
      this.currentVictimId = -9999;
      this.isPreview = true;
      this.victimName = victimName != null ? victimName : "";
      this.heldItem = ItemStack.EMPTY;
      this.currentWeaponName = weaponName != null ? weaponName : "Unknown";
      JsonObject config = ConfigManager.getElementConfig("subtitle", "kill_feed");
      if (config != null) {
         this.loadConfig(config);
         if (!config.has("visible") || !config.get("visible").getAsBoolean()) {
            this.isVisible = false;
         } else if (this.isKillTypeEnabled(killType)) {
            String formatKey = formatKeyForType(killType);
            if (!isKillFeedFormatEmpty(config, formatKey)) {
               String colorKey = placeholderColorKeyForType(killType);
               String emphasisColorKey = emphasisColorKeyForType(killType);
               String normalFormat = config.has("format_normal")
                  ? config.get("format_normal").getAsString()
                  : FormatDefaultsManager.getDefault("subtitle/kill_feed", KillTypeRegistry.get(killType).formatKey());
               String resolvedFormat = config.has(formatKey) ? config.get(formatKey).getAsString() : normalFormat;
               String normalColorHex = config.has("color_normal_placeholder") ? config.get("color_normal_placeholder").getAsString() : "#008B8B";
               String chosenColorHex = config.has(colorKey) ? config.get(colorKey).getAsString() : normalColorHex;
               int pColor = parseColorHexOrDefault(chosenColorHex, -16741493);
               String emphasisHex = config.has(emphasisColorKey) ? config.get(emphasisColorKey).getAsString() : "#FFFFFF";
               int eColor = parseColorHexOrDefault(emphasisHex, -1);
               float dist = this.isNormalKillType(killType) ? 50.0F : 0.0F;
               if (this.enableStacking) {
                  this.addItemToStack(
                     resolvedFormat, pColor, eColor, this.currentWeaponName, this.victimName, this.displayDuration, dist, -9999, "20", killType
                  );
               } else {
                  this.format = resolvedFormat;
                  this.placeholderColor = pColor;
                  this.emphasisColor = eColor;
                  this.currentDistance = dist;
                  if (this.displayDuration < 200L) {
                     this.displayDuration = 200L;
                  }

                  this.startTime = PreviewRenderTimeContext.currentTimeMillis();
                  this.textHideTime = this.startTime + this.displayDuration;
                  this.isVisible = true;
               }
            }
         }
      }
   }

   private void addItemToStack(
      String format, int pColor, int eColor, String wName, String vName, long duration, float distance, int victimId, String scoreOverride, int killType
   ) {
      SubtitleRenderer.SubtitleItem newItem = new SubtitleRenderer.SubtitleItem(
         format, pColor, eColor, wName, vName, 0L, duration, distance, victimId, scoreOverride, killType
      );
      if (this.pendingQueue.size() < 10) {
         this.pendingQueue.add(newItem);
         this.isVisible = true;
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.isVisible) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         int centerX = ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
         float baseTextY = (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
         float textY = baseTextY;
         if (!this.linkageConfigLoaded) {
            JsonObject cfg = ConfigManager.getElementConfig("subtitle", "kill_feed");
            if (cfg != null) {
               this.loadConfig(cfg);
            }

            this.linkageConfigLoaded = true;
         }

         if (this.enableQueueLinkage) {
            ScrollingIconRenderer iconRenderer = ScrollingIconRenderer.getInstance();
            boolean iconsVisible = iconRenderer.hasVisibleIcons();
            float iconY = iconRenderer.getIconsAnchorY();
            float targetY = iconsVisible ? baseTextY : iconY + this.queueLinkageIconYOffset;
            textY = this.smoothLinkedY(baseTextY, targetY);
         } else {
            this.linkageStartTime = 0L;
            this.linkedYOffset = 0.0F;
         }

         if (this.enableStacking) {
            this.renderStacked(guiGraphics, font, centerX, textY);
         } else {
            SubtitleRenderer.RenderState state = this.resolveRenderState();
            if (state == null) {
               return;
            }

            this.renderInternal(
               guiGraphics,
               font,
               centerX,
               textY,
               state,
               this.format,
               this.placeholderColor,
               this.emphasisColor,
               this.currentWeaponName,
               this.victimName,
               this.currentDistance,
               this.currentVictimId,
               this.currentScoreOverride,
               this.currentKillType
            );
         }
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      if (this.isVisible) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int resolvedCenterX = Math.round(centerX);
         int resolvedTextY = Math.round(centerY);
         if (this.enableStacking) {
            this.renderStacked(guiGraphics, font, resolvedCenterX, (float)resolvedTextY);
         } else {
            SubtitleRenderer.RenderState state = this.resolveRenderState();
            if (state == null) {
               return;
            }

            this.renderInternal(
               guiGraphics,
               font,
               resolvedCenterX,
               (float)resolvedTextY,
               state,
               this.format,
               this.placeholderColor,
               this.emphasisColor,
               this.currentWeaponName,
               this.victimName,
               this.currentDistance,
               this.currentVictimId,
               this.currentScoreOverride,
               this.currentKillType
            );
         }
      }
   }

   private float smoothLinkedY(float baseY, float targetY) {
      float targetOffset = targetY - baseY;
      long now = System.currentTimeMillis();
      long durationMs = Math.max(1L, (long)(this.queueLinkageScrollSpeed * 1000.0F));
      if (this.linkageStartTime != 0L && this.linkageTargetOffset == targetOffset && this.linkageBaseY == baseY) {
         float progress = (float)(now - this.linkageStartTime) / (float)durationMs;
         progress = Math.min(1.0F, Math.max(0.0F, progress));
         float eased = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
         this.linkedYOffset = this.linkageFromOffset + (targetOffset - this.linkageFromOffset) * eased;
         if (progress >= 1.0F) {
            this.linkageFromOffset = targetOffset;
         }

         return baseY + this.linkedYOffset;
      } else {
         this.linkageFromOffset = this.linkedYOffset;
         this.linkageStartTime = now;
         this.linkageTargetOffset = targetOffset;
         this.linkageBaseY = baseY;
         if (Math.abs(targetOffset - this.linkedYOffset) < 0.001F) {
            this.linkageFromOffset = targetOffset;
            this.linkedYOffset = targetOffset;
         }

         return baseY + this.linkedYOffset;
      }
   }

   private void renderStacked(GuiGraphics guiGraphics, Font font, int centerX, float startY) {
      long now = PreviewRenderTimeContext.currentTimeMillis();
      if (!this.pendingQueue.isEmpty() && now - this.lastDequeueTime >= 200L) {
         SubtitleRenderer.SubtitleItem newItem = this.pendingQueue.poll();
         if (newItem != null) {
            newItem.spawnTime = now;
            this.stackedItems.add(newItem);

            while (this.stackedItems.size() > this.maxLines) {
               this.stackedItems.remove(0);
            }

            this.lastDequeueTime = now;
         }
      }

      if (this.stackedItems.isEmpty()) {
         this.isVisible = false;
      } else {
         boolean hasVisibleItems = false;
         Iterator<SubtitleRenderer.SubtitleItem> iterator = this.stackedItems.iterator();

         while (iterator.hasNext()) {
            SubtitleRenderer.SubtitleItem item = iterator.next();
            long hideTime = item.spawnTime + item.duration;
            if (now >= hideTime + 300L) {
               iterator.remove();
            } else {
               hasVisibleItems = true;
            }
         }

         if (!hasVisibleItems && this.stackedItems.isEmpty()) {
            this.isVisible = false;
         } else {
            this.renderStackItems(guiGraphics, font, centerX, startY);
         }
      }
   }

   private void renderStackItems(GuiGraphics guiGraphics, Font font, int centerX, float startY) {
      long now = PreviewRenderTimeContext.currentTimeMillis();

      for (int i = 0; i < this.stackedItems.size(); i++) {
         SubtitleRenderer.SubtitleItem item = this.stackedItems.get(i);
         int posFromBottom = this.stackedItems.size() - 1 - i;
         float targetRelY = (float)(-(posFromBottom * this.lineSpacing));
         float smooth = 0.2F;
         item.currentRelY = Mth.lerp(smooth, item.currentRelY, targetRelY);
         if (Math.abs(item.currentRelY - targetRelY) < 0.5F) {
            item.currentRelY = targetRelY;
         }

         float itemAlpha = 1.0F;
         long hideTime = item.spawnTime + item.duration;
         if (now >= hideTime) {
            long fadeElapsed = now - hideTime;
            itemAlpha = Math.max(0.0F, 1.0F - (float)fadeElapsed / 300.0F);
         }

         if (i == this.stackedItems.size() - 1) {
            long elapsed = now - item.spawnTime;
            if (elapsed < 200L) {
               float fadeIn = (float)elapsed / 200.0F;
               itemAlpha = Math.min(itemAlpha, fadeIn);
            }
         }

         if (this.maxLines > 1) {
            float posAlpha = Math.max(0.0F, 1.0F - (float)posFromBottom / (float)(this.maxLines - 1));
            itemAlpha *= posAlpha;
         }

         if (!(itemAlpha <= 0.05F)) {
            float drawY = startY + item.currentRelY;
            SubtitleRenderer.RenderState state = new SubtitleRenderer.RenderState(now - item.spawnTime, itemAlpha, this.scale);
            this.renderInternal(
               guiGraphics,
               font,
               centerX,
               drawY,
               state,
               item.format,
               item.pColor,
               item.eColor,
               item.wName,
               item.vName,
               item.distance,
               item.victimId,
               item.scoreOverride,
               item.killType
            );
         }
      }
   }

   private SubtitleRenderer.RenderState resolveRenderState() {
      if (this.isVisible && this.startTime != -1L) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         long elapsed = currentTime - this.startTime;
         float alpha = this.calculateAlpha(currentTime);
         boolean fadingIn = this.startTime >= 0L && this.fadeInDurationMs > 0L && currentTime - this.startTime < this.fadeInDurationMs;
         if ((!(alpha <= 0.05F) || fadingIn) && (this.textHideTime <= 0L || currentTime < this.textHideTime + 300L)) {
            float currentScale = this.scale;
            return new SubtitleRenderer.RenderState(elapsed, alpha, currentScale);
         } else {
            this.isVisible = false;
            this.startTime = -1L;
            return null;
         }
      } else {
         return null;
      }
   }

   private void renderInternal(
      GuiGraphics guiGraphics,
      Font font,
      int centerX,
      float textY,
      SubtitleRenderer.RenderState state,
      String fmt,
      int pColor,
      int eColor,
      String wName,
      String vName,
      float distance,
      int victimId,
      String scoreOverride,
      int killType
   ) {
      float textYFrac = textY - (float)Math.floor((double)textY);
      int textYInt = (int)Math.floor((double)textY);
      float colorProgress = this.configEnableFlashIn ? this.getColorProgress(state.elapsed) : 1.0F;
      String scoreStr = scoreOverride != null && !scoreOverride.isBlank() ? scoreOverride : "";
      Component fullText = this.buildFullText(fmt, pColor, eColor, wName, vName, scoreStr, colorProgress, distance);
      int textWidth = font.width(fullText);
      int textX;
      if (this.alignLeft && !this.alignRight) {
         textX = centerX;
      } else if (this.alignRight && !this.alignLeft) {
         textX = centerX - textWidth;
      } else {
         textX = centerX - textWidth / 2;
      }

      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();
      float pivotX;
      if (this.alignLeft && !this.alignRight) {
         pivotX = (float)textX;
      } else if (this.alignRight && !this.alignLeft) {
         pivotX = (float)(textX + textWidth);
      } else {
         pivotX = (float)textX + (float)textWidth / 2.0F;
      }

      float pivotY = textY + 9.0F / 2.0F;
      if (this.configEntranceBackground) {
         float scale = state.currentScale;
         float textLeftScreen = pivotX + ((float)textX - pivotX) * scale;
         float textRightScreen = pivotX + ((float)(textX + textWidth) - pivotX) * scale;
         float textHeightScreen = 9.0F * scale;
         boolean mirror = this.alignRight && !this.alignLeft;
         SubtitleEntranceBackground.draw(
            guiGraphics,
            state.elapsed,
            textLeftScreen,
            textRightScreen,
            pivotY,
            textHeightScreen,
            this.configEntranceBgFadeInMs,
            this.configEntranceBgSweepMs,
            this.configEntranceBgFadeOutMs,
            this.configEntranceBgPeakTransparency,
            this.configEntranceBgColor,
            mirror
         );
      }

      poseStack.translate(pivotX, pivotY, 0.0F);
      float s = state.currentScale;
      if (this.enableScaleAnimation && state.elapsed < 200L) {
         float progress = (float)state.elapsed / 200.0F;
         float easedProgress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
         s = Mth.lerp(easedProgress, this.scale * 1.5F, this.scale);
      } else {
         s = this.scale;
      }

      poseStack.scale(s, s, 1.0F);
      poseStack.translate(-pivotX, -pivotY, 0.0F);
      if (textYFrac > 0.001F) {
         poseStack.translate(0.0F, textYFrac, 0.0F);
      }

      int alphaInt = (int)(state.alpha * 255.0F) << 24;
      int colorWithAlpha = this.normalTextColor & 16777215 | alphaInt;
      if (this.enableGlowEffect) {
         int glowAlpha = (int)(state.alpha * this.glowIntensity * this.glowAlphaMultiplier * 255.0F);
         glowAlpha = Math.max(25, Math.min(255, glowAlpha));
         glowAlpha = Math.max(0, Math.min(255, glowAlpha));
         int glowRgb = killType == 10 ? this.glowColorRescueRgb : this.glowColorRgb;
         int glowColor = glowRgb & 16777215 | glowAlpha << 24;
         Component glowComponent = stripColor(fullText);
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
            guiGraphics.drawString(font, glowComponent, textX, textYInt, glowColor, false);
            poseStack.popPose();
         }
      }

      guiGraphics.drawString(font, fullText, textX, textYInt, colorWithAlpha, this.enableTextShadow);
      poseStack.popPose();
   }

   private static String formatScore(float score) {
      return score < 1.0F && score > 0.0F ? String.format("%.1f", score) : String.valueOf(Math.round(score));
   }

   private void loadConfig(JsonObject config) {
      try {
         this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 100;
         this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.displayDuration = config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 3000L;
         this.scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         this.enableScaleAnimation = !config.has("enable_scale_animation") || config.get("enable_scale_animation").getAsBoolean();
         this.killTypeEnableFlags.clear();
         this.killTypeEnableFlags.put("enable_normal_kill", !config.has("enable_normal_kill") || config.get("enable_normal_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_headshot_kill", !config.has("enable_headshot_kill") || config.get("enable_headshot_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_explosion_kill", !config.has("enable_explosion_kill") || config.get("enable_explosion_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_crit_kill", !config.has("enable_crit_kill") || config.get("enable_crit_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_assist_kill", !config.has("enable_assist_kill") || config.get("enable_assist_kill").getAsBoolean());
         this.killTypeEnableFlags
            .put("enable_destroy_vehicle_kill", !config.has("enable_destroy_vehicle_kill") || config.get("enable_destroy_vehicle_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_capture_kill", !config.has("enable_capture_kill") || config.get("enable_capture_kill").getAsBoolean());
         this.killTypeEnableFlags
            .put("enable_spot_assist_kill", !config.has("enable_spot_assist_kill") || config.get("enable_spot_assist_kill").getAsBoolean());
         this.killTypeEnableFlags.put("enable_rescue_kill", !config.has("enable_rescue_kill") || config.get("enable_rescue_kill").getAsBoolean());
         this.enableStacking = config.has("enable_stacking") && config.get("enable_stacking").getAsBoolean();
         this.maxLines = config.has("max_lines") ? config.get("max_lines").getAsInt() : 5;
         this.lineSpacing = config.has("line_spacing") ? config.get("line_spacing").getAsInt() : 12;
         String normalFormat = config.has("format_normal")
            ? config.get("format_normal").getAsString()
            : FormatDefaultsManager.getDefault("subtitle/kill_feed", KillTypeRegistry.get(0).formatKey());
         String normalColorHex = config.has("color_normal_placeholder") ? config.get("color_normal_placeholder").getAsString() : "#008B8B";
         this.format = normalFormat;
         this.placeholderColor = parseColorHexOrDefault(normalColorHex, -16741493);
         this.enablePlaceholderBold = config.has("enable_placeholder_bold") && config.get("enable_placeholder_bold").getAsBoolean();
         this.enableTextShadow = !config.has("enable_text_shadow") || config.get("enable_text_shadow").getAsBoolean();
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.enableGlowEffect = config.has("enable_glow_effect") && config.get("enable_glow_effect").getAsBoolean();
         this.glowIntensity = config.has("glow_intensity") ? config.get("glow_intensity").getAsFloat() : 0.5F;
         this.glowSize = config.has("glow_size") ? config.get("glow_size").getAsFloat() : 0.3F;
         this.glowColorRgb = parseColorHexOrDefault(config.has("glow_color") ? config.get("glow_color").getAsString() : "#FFFFFF", 16777215) & 16777215;
         this.glowColorRescueRgb = parseColorHexOrDefault(config.has("glow_color_rescue") ? config.get("glow_color_rescue").getAsString() : "#63D048", 6541384)
            & 16777215;
         this.glowAlphaMultiplier = config.has("glow_alpha") ? Mth.clamp(config.get("glow_alpha").getAsFloat(), 0.0F, 1.0F) : 1.0F;
         this.alignLeft = config.has("align_left") ? config.get("align_left").getAsBoolean() : false;
         this.alignRight = config.has("align_right") ? config.get("align_right").getAsBoolean() : false;
         this.configEnableFlashIn = !config.has("enable_flash_in") || config.get("enable_flash_in").getAsBoolean();
         this.fadeInDurationMs = config.has("fade_in_duration") ? Math.max(0L, (long)(config.get("fade_in_duration").getAsFloat() * 1000.0F)) : 0L;
         this.enableQueueLinkage = config.has("enable_queue_linkage") && config.get("enable_queue_linkage").getAsBoolean();
         this.queueLinkageScrollSpeed = config.has("queue_linkage_scroll_speed")
            ? Math.max(0.01F, config.get("queue_linkage_scroll_speed").getAsFloat())
            : 0.1F;
         this.queueLinkageIconYOffset = config.has("queue_linkage_icon_y_offset") ? config.get("queue_linkage_icon_y_offset").getAsFloat() : 0.0F;
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
         this.normalTextColor = parseColorHexOrDefault(config.has("color_normal_text") ? config.get("color_normal_text").getAsString() : "#FFFFFF", -1);
      } catch (Exception var4) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.subtitle.config_error");
         this.configXOffset = 0;
         this.configYOffset = 100;
         this.configScreenAnchor = "bottom_center";
         this.displayDuration = 3000L;
         this.scale = 1.0F;
         this.format = FormatDefaultsManager.getDefault("subtitle/kill_feed", KillTypeRegistry.get(0).formatKey());
         this.placeholderColor = -16741493;
         this.enablePlaceholderBold = false;
         this.enableScaleAnimation = true;
         this.killTypeEnableFlags.clear();
         this.killTypeEnableFlags.put("enable_normal_kill", true);
         this.killTypeEnableFlags.put("enable_capture_kill", true);
         this.enableStacking = false;
         this.normalTextColor = -1;
      }
   }

   private boolean isKillTypeEnabled(int type) {
      KillTypeDefinition def = KillTypeRegistry.get(type);
      return def != null && def.enableKey() != null ? this.killTypeEnableFlags.getOrDefault(def.enableKey(), true) : true;
   }

   private float calculateAlpha(long currentTime) {
      if (this.startTime >= 0L && this.fadeInDurationMs > 0L) {
         long elapsed = currentTime - this.startTime;
         if (elapsed < this.fadeInDurationMs) {
            return Math.max(0.0F, (float)elapsed / (float)this.fadeInDurationMs);
         }
      }

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

   private Component buildFullText(String fmt, int pColor, int eColor, String wName, String vName, String scoreStr, float colorProgress, float distance) {
      Component fullText = Component.empty();
      String tempFormat = fmt;

      while (!tempFormat.isEmpty()) {
         int weaponIdx = tempFormat.indexOf("<weapon>");
         int targetIdx = tempFormat.indexOf("<target>");
         int distanceIdx = tempFormat.indexOf("<distance>");
         int scoreIdx = tempFormat.indexOf("<score>");
         int emphasisStart = tempFormat.indexOf("/");
         int emphasisEnd = -1;
         if (emphasisStart != -1) {
            emphasisEnd = tempFormat.indexOf("\\", emphasisStart + 1);
            if (emphasisEnd == -1) {
               emphasisStart = -1;
            }
         }

         int firstIdx = -1;
         String type = "";
         if (weaponIdx != -1) {
            firstIdx = weaponIdx;
            type = "weapon";
         }

         if (targetIdx != -1 && (firstIdx == -1 || targetIdx < firstIdx)) {
            firstIdx = targetIdx;
            type = "target";
         }

         if (distanceIdx != -1 && (firstIdx == -1 || distanceIdx < firstIdx)) {
            firstIdx = distanceIdx;
            type = "distance";
         }

         if (scoreIdx != -1 && (firstIdx == -1 || scoreIdx < firstIdx)) {
            firstIdx = scoreIdx;
            type = "score";
         }

         if (emphasisStart != -1 && (firstIdx == -1 || emphasisStart < firstIdx)) {
            firstIdx = emphasisStart;
            type = "emphasis";
         }

         if (firstIdx == -1) {
            int targetColor = this.normalTextColor;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            fullText.getSiblings().add(Component.literal(tempFormat).withStyle(style -> style.withColor(interpolatedColor & 16777215)));
            break;
         }

         if (firstIdx > 0) {
            String prefix = tempFormat.substring(0, firstIdx);
            int targetColor = this.normalTextColor;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            fullText.getSiblings().add(Component.literal(prefix).withStyle(style -> style.withColor(interpolatedColor & 16777215)));
         }

         if (type.equals("weapon")) {
            int targetColor = pColor & 16777215;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            fullText.getSiblings()
               .add(Component.literal(wName).withStyle(style -> style.withColor(interpolatedColor & 16777215).withBold(this.enablePlaceholderBold)));
            tempFormat = tempFormat.substring(firstIdx + "<weapon>".length());
         } else if (type.equals("target")) {
            int targetColor = pColor & 16777215;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            String translatedVName = I18nCompat.exists(vName) ? I18n.get(vName, new Object[0]) : vName;
            fullText.getSiblings()
               .add(
                  Component.literal(translatedVName).withStyle(style -> style.withColor(interpolatedColor & 16777215).withBold(this.enablePlaceholderBold))
               );
            tempFormat = tempFormat.substring(firstIdx + "<target>".length());
         } else if (type.equals("distance")) {
            if (distance >= 20.0F) {
               String meterText = I18n.get("battlefield_ops.client.text.meter", new Object[0]);
               String content = String.format("(%d%s)", (int)distance, meterText);
               int targetColor = pColor & 16777215;
               int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
               fullText.getSiblings()
                  .add(Component.literal(content).withStyle(style -> style.withColor(interpolatedColor & 16777215).withBold(this.enablePlaceholderBold)));
            }

            tempFormat = tempFormat.substring(firstIdx + "<distance>".length());
         } else if (type.equals("score")) {
            int targetColor = pColor & 16777215;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            fullText.getSiblings()
               .add(Component.literal(scoreStr).withStyle(style -> style.withColor(interpolatedColor & 16777215).withBold(this.enablePlaceholderBold)));
            tempFormat = tempFormat.substring(firstIdx + "<score>".length());
         } else if (type.equals("emphasis")) {
            String content = tempFormat.substring(emphasisStart + 1, emphasisEnd);
            int targetColor = eColor & 16777215;
            int interpolatedColor = interpolateFromWhite(targetColor, colorProgress);
            fullText.getSiblings().add(Component.literal(content).withStyle(style -> style.withColor(interpolatedColor & 16777215)));
            tempFormat = tempFormat.substring(emphasisEnd + 1);
         }
      }

      return fullText;
   }

   private static boolean isKillFeedFormatEmpty(JsonObject config, String formatKey) {
      return formatKey != null && config != null && config.has(formatKey) && config.get(formatKey).getAsString().isEmpty();
   }

   private static String formatKeyForType(int killType) {
      return KillTypeRegistry.get(killType).formatKey();
   }

   private static String resolveRushCaptureFormat(String token) {
      if (token != null && !token.isEmpty()) {
         return switch (token) {
            case "rush_bomb_planted" -> "format_rush_bomb_planted_capture";
            case "rush_bomb_defused" -> "format_rush_bomb_defused_capture";
            case "rush_objective_destroyed" -> "format_rush_objective_destroyed_capture";
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static boolean isNumericToken(String token) {
      if (token != null && !token.isBlank()) {
         try {
            Double.parseDouble(token);
            return true;
         } catch (NumberFormatException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String placeholderColorKeyForType(int killType) {
      return KillTypeRegistry.get(killType).placeholderColorKey();
   }

   private static String emphasisColorKeyForType(int killType) {
      return KillTypeRegistry.get(killType).emphasisColorKey();
   }

   private boolean isNormalKillType(int type) {
      return type == 0 || type == 1 || type == 2 || type == 3;
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

   private static int parseColorHexOrDefault(String hex, int fallbackArgb) {
      if (hex != null && !hex.isEmpty()) {
         try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            return rgb & 16777215 | 0xFF000000;
         } catch (NumberFormatException var3) {
            return fallbackArgb;
         }
      } else {
         return fallbackArgb;
      }
   }

   private float getColorProgress(long elapsed) {
      return elapsed < 200L ? (float)elapsed / 200.0F : 1.0F;
   }

   private static int interpolateFromWhite(int targetColor, float progress) {
      if (progress >= 1.0F) {
         return targetColor;
      } else {
         int white = 16777215;
         int targetRGB = targetColor & 16777215;
         int alpha = targetColor & 0xFF000000;
         int r1 = white >> 16 & 0xFF;
         int g1 = white >> 8 & 0xFF;
         int b1 = white & 0xFF;
         int r2 = targetRGB >> 16 & 0xFF;
         int g2 = targetRGB >> 8 & 0xFF;
         int b2 = targetRGB & 0xFF;
         int r = (int)((float)r1 + (float)(r2 - r1) * progress);
         int g = (int)((float)g1 + (float)(g2 - g1) * progress);
         int b = (int)((float)b1 + (float)(b2 - b1) * progress);
         return alpha | r << 16 | g << 8 | b;
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

   private static class SubtitleItem {
      String format;
      int pColor;
      int eColor;
      String wName;
      String vName;
      long spawnTime;
      long duration;
      float currentRelY;
      float distance;
      int victimId;
      String scoreOverride;
      int killType;

      public SubtitleItem(
         String format,
         int pColor,
         int eColor,
         String wName,
         String vName,
         long spawnTime,
         long duration,
         float distance,
         int victimId,
         String scoreOverride,
         int killType
      ) {
         this.format = format;
         this.pColor = pColor;
         this.eColor = eColor;
         this.wName = wName;
         this.vName = vName;
         this.spawnTime = spawnTime;
         this.duration = duration;
         this.currentRelY = 0.0F;
         this.distance = distance;
         this.victimId = victimId;
         this.scoreOverride = scoreOverride;
         this.killType = killType;
      }
   }
}
