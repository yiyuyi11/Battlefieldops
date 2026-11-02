package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.config.FormatDefaultsManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.BonusTextBox;
import cn.bfops.killicon.client.render.effect.DigitalScrollEffect;
import cn.bfops.killicon.client.render.effect.TextFadeEffect;
import cn.bfops.killicon.client.render.effect.TextScrambleEffect;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.bonus.BonusDefinition;
import cn.bfops.killicon.common.bonus.BonusRegistry;

public class BonusListRenderer implements IHudRenderer {
   private static final float COMBO_ANIMATION_DURATION_MULTIPLIER = 1.25F;
   private static final float STREAK_ANIMATION_DURATION_MULTIPLIER = 5.0F;
   private static final float ALPHA_THRESHOLD = 0.05F;
   private static final float GLOW_OFFSET = 0.3F;
   private static final long KILL_FEED_ENTRY_ANIMATION_DURATION = 350L;
   private static final float KILL_FEED_ENTRY_SCALE_START = 1.8F;
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<(\\w+)>");
   private static final BonusListRenderer INSTANCE = new BonusListRenderer();
   private final List<BonusListRenderer.BonusItem> items = new ArrayList<>();
   private final Deque<BonusListRenderer.BonusItem> pendingQueue = new ArrayDeque<>();
   private float animationDuration = 0.5F;
   private float animationRefreshRate = 0.01F;
   private boolean enableTextScrolling = false;
   private float textScrollingDurationMultiplier = 1.5F;
   private float textScrollingRefreshRate = 0.05F;
   private long enterAnimationDuration = 200L;
   private boolean enableTextSweepAnimation = false;
   private long mergeWindowDuration = 500L;
   private float animationSpeed = 10.0F;
   private float killBonusScale = 1.0F;
   private boolean enableKillFeed = false;
   private String killFeedFormat = "[<weapon>] <target> +<score>";
   private String killFeedVictimColor = "#FF0000";
   private boolean enableDigitalScroll = true;
   private boolean enableStackMultiplier = false;
   private boolean enableGlowEffect = false;
   private float glowIntensity = 0.5F;
   private float glowSize = 0.3F;
   private int glowColorRgb = 16777215;
   private float glowAlphaMultiplier = 1.0F;
   private boolean enableTextBox = false;
   private float textBoxBorderWidth = 1.0F;
   private int textBoxColorRgb = 16777215;
   private boolean cleanSubtitleContent = false;
   private long fadeOutDurationMs = 300L;
   private float fadeStartLineRatio = 0.0F;
   private boolean enableQueueLinkage = false;
   private float queueLinkageScrollSpeed = 0.1F;
   private float queueLinkageIconYOffset = 0.0F;
   private boolean linkageConfigLoaded = false;
   private float linkedYOffset = 0.0F;
   private float linkageFromOffset = 0.0F;
   private long linkageStartTime = 0L;
   private float linkageTargetOffset = 0.0F;
   private float linkageBaseY = 0.0F;
   private int normalTextColor = 16777215;
   private boolean enableTextShadow = true;
   private boolean configBlinkFadeAnimation = false;
   private long lastProcessTime = 0L;
   private long lastRenderTime = 0L;
   private long nextFadeTriggerTime = 0L;

   private BonusListRenderer() {
   }

   public static BonusListRenderer getInstance() {
      return INSTANCE;
   }

   public static boolean isBonusConfigEmpty(int type, JsonObject config) {
      BonusDefinition def = BonusRegistry.get(type);
      return def != null && config != null && config.has(def.formatConfigKey()) ? config.get(def.formatConfigKey()).getAsString().isEmpty() : false;
   }

   public static String getEffectiveFormat(int type, String extraData) {
      JsonObject config = ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/bonus_list");
      return getEffectiveFormat(type, extraData, config);
   }

   public static String getEffectiveFormat(int type, String extraData, JsonObject config) {
      if (type == 8 && config != null && config.has("enable_special_streak_subtitles") && config.get("enable_special_streak_subtitles").getAsBoolean()) {
         try {
            int combo = Integer.parseInt(extraData);
            BonusDefinition def = BonusRegistry.get(type);
            if (def != null) {
               String subtitle = def.streakSubtitle(combo);
               if (subtitle != null) {
                  return I18n.get(subtitle, new Object[0]);
               }
            }
         } catch (NumberFormatException var6) {
         }
      }

      String configValue = null;
      BonusDefinition def = BonusRegistry.get(type);
      if (def != null && config != null && config.has(def.formatConfigKey())) {
         configValue = config.get(def.formatConfigKey()).getAsString();
      }

      if (def == null) {
         return "";
      } else {
         return configValue != null && !configValue.isEmpty() ? configValue : FormatDefaultsManager.getDefault("subtitle/bonus_list", def.formatConfigKey());
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      JsonObject config = ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/bonus_list");
      if (config != null && config.get("visible").getAsBoolean()) {
         if (!this.linkageConfigLoaded) {
            this.loadConfig(config);
            this.linkageConfigLoaded = true;
         }

         int xOffset = config.get("x_offset").getAsInt();
         int yOffset = config.get("y_offset").getAsInt();
         String screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         float centerX = (float)ScreenAnchor.resolveCenterX(screenAnchor, xOffset, screenWidth);
         float startY = (float)ScreenAnchor.resolveCenterY(screenAnchor, yOffset, screenHeight);
         startY = this.applyQueueLinkage(startY);
         this.renderInternal(guiGraphics, config, centerX, startY);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      JsonObject config = ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/bonus_list");
      if (config != null && config.get("visible").getAsBoolean()) {
         this.renderInternal(guiGraphics, config, centerX, centerY);
      }
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      JsonObject config = ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/bonus_list");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.loadConfig(config);
         BonusListRenderer.ParsedData parsed = BonusListRenderer.ParsedData.parse(context.extraData());
         String extraData = parsed.extraData;
         if (!isBonusConfigEmpty(context.type(), config)) {
            String format = getEffectiveFormat(context.type(), extraData);
            int specialColor = this.parseSpecialColor(config);
            long now = PreviewRenderTimeContext.currentTimeMillis();
            String weaponName = "";
            String victimName = "";
            Minecraft mc = Minecraft.getInstance();
            if (parsed.victimName != null && !parsed.victimName.isEmpty()) {
               if (I18n.exists(parsed.victimName)) {
                  victimName = I18n.get(parsed.victimName, new Object[0]);
               } else {
                  victimName = parsed.victimName;
               }
            } else if (mc.level != null && context.entityId() != -1) {
               Entity entity = mc.level.getEntity(context.entityId());
               if (entity != null) {
                  victimName = entity.getDisplayName().getString();
               } else {
                  victimName = I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
               }
            } else {
               victimName = I18n.get("battlefield_ops.client.text.unknown", new Object[0]);
            }

            if (mc.player != null) {
               if (mc.player.getVehicle() != null) {
                  weaponName = mc.player.getVehicle().getDisplayName().getString();
               } else {
                  ItemStack held = mc.player.getMainHandItem();
                  if (held.isEmpty()) {
                     if (I18n.exists("battlefield_ops.client.text.bare_hand")) {
                        weaponName = I18n.get("battlefield_ops.client.text.bare_hand", new Object[0]);
                     } else {
                        weaponName = "Bare Hand";
                     }
                  } else {
                     weaponName = held.getHoverName().getString();
                  }
               }
            }

            synchronized (this.items) {
               boolean isComboFormat = format.contains("<combo>");

               for (BonusListRenderer.BonusItem item : this.items) {
                  if (this.canMerge(item, format, isComboFormat, extraData, now)) {
                     item.merge(parsed.score, extraData, isComboFormat, format);
                     this.nextFadeTriggerTime = now + (long)(config.get("display_duration").getAsFloat() * 1000.0F);
                     return;
                  }
               }

               BonusListRenderer.BonusItem newItem = new BonusListRenderer.BonusItem(
                  format, parsed.score, extraData, context.type(), specialColor, weaponName, victimName
               );
               this.pendingQueue.add(newItem);
            }
         }
      }
   }

   public void triggerPreview(int type, float score, String extraData, String weaponName, String victimName, JsonObject config) {
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.loadConfig(config);
         String resolvedExtraData = extraData != null ? extraData : "";
         if (!isBonusConfigEmpty(type, config)) {
            String format = getEffectiveFormat(type, resolvedExtraData, config);
            int specialColor = this.parseSpecialColor(config);
            long now = PreviewRenderTimeContext.currentTimeMillis();
            synchronized (this.items) {
               boolean isComboFormat = format.contains("<combo>");

               for (BonusListRenderer.BonusItem item : this.items) {
                  if (this.canMerge(item, format, isComboFormat, resolvedExtraData, now)) {
                     item.merge(score, resolvedExtraData, isComboFormat, format);
                     this.nextFadeTriggerTime = now + (long)(config.get("display_duration").getAsFloat() * 1000.0F);
                     return;
                  }
               }

               BonusListRenderer.BonusItem newItem = new BonusListRenderer.BonusItem(
                  format, score, resolvedExtraData, type, specialColor, weaponName, victimName
               );
               this.pendingQueue.add(newItem);
            }
         }
      }
   }

   @Override
   public void resetPreview() {
      synchronized (this.items) {
         this.items.clear();
         this.pendingQueue.clear();
      }

      this.lastProcessTime = 0L;
      this.lastRenderTime = 0L;
      this.nextFadeTriggerTime = 0L;
   }

   private void loadConfig(JsonObject config) {
      try {
         this.animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 0.5F;
         this.animationRefreshRate = config.has("animation_refresh_rate") ? config.get("animation_refresh_rate").getAsFloat() : 0.01F;
         this.enableTextScrolling = config.has("enable_text_scrolling") && config.get("enable_text_scrolling").getAsBoolean();
         this.textScrollingDurationMultiplier = config.has("text_scrolling_duration_multiplier")
            ? config.get("text_scrolling_duration_multiplier").getAsFloat()
            : 1.5F;
         this.textScrollingRefreshRate = config.has("text_scrolling_refresh_rate") ? config.get("text_scrolling_refresh_rate").getAsFloat() : 0.05F;
         this.enterAnimationDuration = config.has("enter_animation_duration") ? (long)(config.get("enter_animation_duration").getAsFloat() * 1000.0F) : 200L;
         this.enableTextSweepAnimation = config.has("enable_text_sweep_animation") && config.get("enable_text_sweep_animation").getAsBoolean();
         this.mergeWindowDuration = config.has("merge_window_duration") ? (long)(config.get("merge_window_duration").getAsFloat() * 1000.0F) : 500L;
         this.animationSpeed = config.has("animation_speed") ? config.get("animation_speed").getAsFloat() : 10.0F;
         this.killBonusScale = config.has("kill_bonus_scale") ? config.get("kill_bonus_scale").getAsFloat() : 1.0F;
         this.enableKillFeed = config.has("enable_kill_feed") && config.get("enable_kill_feed").getAsBoolean();
         this.killFeedFormat = config.has("kill_feed_format") ? config.get("kill_feed_format").getAsString() : "[<weapon>] <target> +<score>";
         this.killFeedVictimColor = config.has("kill_feed_victim_color") ? config.get("kill_feed_victim_color").getAsString() : "#FF0000";
         this.enableDigitalScroll = !config.has("enable_digital_scroll") || config.get("enable_digital_scroll").getAsBoolean();
         this.enableStackMultiplier = config.has("enable_stack_multiplier") && config.get("enable_stack_multiplier").getAsBoolean();
         this.enableGlowEffect = config.has("enable_glow_effect") && config.get("enable_glow_effect").getAsBoolean();
         this.glowIntensity = config.has("glow_intensity") ? config.get("glow_intensity").getAsFloat() : 0.5F;
         this.glowSize = config.has("glow_size") ? config.get("glow_size").getAsFloat() : 0.3F;
         this.glowColorRgb = this.parseHexColor(config, "glow_color", 16777215);
         this.glowAlphaMultiplier = config.has("glow_alpha") ? Mth.clamp(config.get("glow_alpha").getAsFloat(), 0.0F, 1.0F) : 1.0F;
         this.enableTextBox = config.has("text_box") && config.get("text_box").getAsBoolean();
         this.textBoxBorderWidth = config.has("text_box_border_width") ? Math.max(0.0F, config.get("text_box_border_width").getAsFloat()) : 1.0F;
         this.textBoxColorRgb = this.parseHexColor(config, "text_box_color", 16777215);
         this.cleanSubtitleContent = config.has("clean_subtitle_content") && config.get("clean_subtitle_content").getAsBoolean();
         this.fadeOutDurationMs = config.has("fade_out_duration") ? Math.max(1L, (long)(config.get("fade_out_duration").getAsFloat() * 1000.0F)) : 300L;
         this.fadeStartLineRatio = config.has("fade_start_line_ratio") ? Mth.clamp(config.get("fade_start_line_ratio").getAsFloat(), 0.0F, 1.0F) : 0.0F;
         this.enableQueueLinkage = config.has("enable_queue_linkage") && config.get("enable_queue_linkage").getAsBoolean();
         this.queueLinkageScrollSpeed = config.has("queue_linkage_scroll_speed")
            ? Math.max(0.01F, config.get("queue_linkage_scroll_speed").getAsFloat())
            : 0.1F;
         this.queueLinkageIconYOffset = config.has("queue_linkage_icon_y_offset") ? config.get("queue_linkage_icon_y_offset").getAsFloat() : 0.0F;
         this.enableTextShadow = !config.has("enable_text_shadow") || config.get("enable_text_shadow").getAsBoolean();
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.normalTextColor = this.parseHexColor(config, "color_normal_text", 16777215);
      } catch (Exception var3) {
         this.animationDuration = 0.5F;
         this.animationRefreshRate = 0.01F;
         this.enableTextScrolling = false;
         this.enterAnimationDuration = 200L;
         this.enableTextSweepAnimation = false;
         this.mergeWindowDuration = 500L;
         this.animationSpeed = 10.0F;
         this.killBonusScale = 1.0F;
         this.enableKillFeed = false;
         this.enableDigitalScroll = true;
         this.enableGlowEffect = false;
         this.glowIntensity = 0.5F;
         this.glowSize = 0.3F;
         this.glowColorRgb = 16777215;
         this.glowAlphaMultiplier = 1.0F;
         this.normalTextColor = 16777215;
      }
   }

   private void renderInternal(GuiGraphics guiGraphics, JsonObject config, float baseCenterX, float baseBottomY) {
      this.loadConfig(config);
      float scale = config.get("scale").getAsFloat();
      int lineSpacing = config.get("line_spacing").getAsInt();
      int maxLines = config.get("max_lines").getAsInt();
      float displayDuration = config.get("display_duration").getAsFloat() * 1000.0F;
      float fadeOutInterval = config.get("fade_out_interval").getAsFloat() * 1000.0F;
      Minecraft mc = Minecraft.getInstance();
      int centerX = Math.round(baseCenterX);
      int startY = Math.round(baseBottomY);
      long now = PreviewRenderTimeContext.currentTimeMillis();
      if (this.lastRenderTime == 0L) {
         this.lastRenderTime = now;
      }

      float dt = (float)(now - this.lastRenderTime) / 1000.0F;
      this.lastRenderTime = now;
      this.processPendingQueue(now, displayDuration);
      this.processIdleFade(now, fadeOutInterval);
      this.renderItems(guiGraphics, mc, scale, centerX, startY, lineSpacing, maxLines, now, dt);
   }

   private int parseSpecialColor(JsonObject config) {
      if (config.has("color_special_placeholder")) {
         String colorStr = config.get("color_special_placeholder").getAsString();

         try {
            if (colorStr.startsWith("#")) {
               return Integer.parseInt(colorStr.substring(1), 16);
            }

            return Integer.parseInt(colorStr, 16);
         } catch (NumberFormatException var4) {
         }
      }

      return 13940736;
   }

   private int parseHexColor(JsonObject config, String key, int fallback) {
      if (config.has(key)) {
         String colorStr = config.get(key).getAsString();

         try {
            String hex = colorStr.startsWith("#") ? colorStr.substring(1) : colorStr;
            return Integer.parseInt(hex, 16);
         } catch (NumberFormatException var6) {
         }
      }

      return fallback;
   }

   private static String cleanSubtitleText(String text) {
      StringBuilder sb = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c != ' ' && c != '+') {
            sb.append(c);
         }
      }

      return sb.toString();
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

   private boolean canMerge(BonusListRenderer.BonusItem item, String format, boolean isComboFormat, String extraData, long now) {
      if (item.isFading || !item.formatString.equals(format) || !item.hasPlaceholder) {
         return false;
      } else if (isComboFormat) {
         return true;
      } else {
         boolean extraDataMatches = (item.extraData == null || item.extraData.isEmpty()) && (extraData == null || extraData.isEmpty())
            || item.extraData != null && item.extraData.equals(extraData);
         return !extraDataMatches ? false : now - item.spawnTime <= this.mergeWindowDuration;
      }
   }

   private void processPendingQueue(long now, float displayDuration) {
      if (!this.pendingQueue.isEmpty() && now - this.lastProcessTime >= 100L) {
         synchronized (this.items) {
            BonusListRenderer.BonusItem newItem = this.pendingQueue.poll();
            if (newItem != null) {
               newItem.spawnTime = now;
               this.items.add(0, newItem);
            }
         }

         this.lastProcessTime = now;
         this.nextFadeTriggerTime = now + (long)displayDuration;
      }
   }

   private void processIdleFade(long now, float fadeOutInterval) {
      if (this.pendingQueue.isEmpty() && now > this.nextFadeTriggerTime && !this.items.isEmpty()) {
         synchronized (this.items) {
            for (int i = this.items.size() - 1; i >= 0; i--) {
               BonusListRenderer.BonusItem item = this.items.get(i);
               if (!item.isFading) {
                  item.isFading = true;
                  item.fadeStartTime = now;
                  this.nextFadeTriggerTime += (long)fadeOutInterval;
                  return;
               }
            }

            this.nextFadeTriggerTime = now + (long)fadeOutInterval;
         }
      }
   }

   private void renderItems(GuiGraphics guiGraphics, Minecraft mc, float scale, int centerX, int startY, int lineSpacing, int maxLines, long now, float dt) {
      JsonObject config = ElementConfigManager.getElementConfig(ConfigManager.getCurrentPresetId(), "subtitle/bonus_list");
      boolean alignLeft = config != null && config.has("align_left") && config.get("align_left").getAsBoolean();
      boolean alignRight = config != null && config.has("align_right") && config.get("align_right").getAsBoolean();
      boolean effectiveAlignLeft = alignLeft && !alignRight;
      boolean effectiveAlignRight = !alignLeft && alignRight;
      boolean horizontal = config != null
         && config.has("enable_horizontal_layout")
         && config.get("enable_horizontal_layout").getAsBoolean()
         && (effectiveAlignLeft || effectiveAlignRight);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      guiGraphics.pose().pushPose();
      guiGraphics.pose().scale(scale, scale, 1.0F);
      float scaledCenterX = (float)centerX / scale;
      float scaledStartY = (float)startY / scale;
      synchronized (this.items) {
         Iterator<BonusListRenderer.BonusItem> iterator = this.items.iterator();
         int index = 0;
         float accumulatedX = 0.0F;

         while (iterator.hasNext()) {
            BonusListRenderer.BonusItem item = iterator.next();
            if (horizontal) {
               float itemWidth = (float)mc.font.width(item.getDisplayComponent());
               float dir = effectiveAlignRight ? -1.0F : 1.0F;
               item.updateX(now, dt, dir * accumulatedX);
               accumulatedX += itemWidth + (float)lineSpacing;
            } else {
               item.update(now, dt, (float)(index * lineSpacing));
            }

            float alpha = this.calculateAlpha(item, now, maxLines, lineSpacing, horizontal, index);
            if (alpha > 0.05F) {
               item.render(
                  guiGraphics,
                  mc,
                  scaledCenterX,
                  scaledStartY,
                  alpha,
                  effectiveAlignLeft,
                  effectiveAlignRight,
                  (float)mc.getWindow().getGuiScaledWidth() / scale,
                  scale,
                  horizontal
               );
            }

            if (this.shouldRemove(item, alpha, now, maxLines, lineSpacing, horizontal, index)) {
               iterator.remove();
            } else {
               index++;
            }
         }
      }

      guiGraphics.pose().popPose();
      RenderSystem.disableBlend();
   }

   private float calculateAlpha(BonusListRenderer.BonusItem item, long now, int maxLines, int lineSpacing, boolean horizontal, int index) {
      float alpha = 1.0F;
      if (!this.enableTextSweepAnimation) {
         long timeSinceSpawn = now - item.spawnTime;
         float fadeInProgress = (float)timeSinceSpawn / (float)this.enterAnimationDuration;
         alpha *= Math.min(1.0F, fadeInProgress);
      }

      float lineIndex = horizontal ? (float)index : item.currentY / (float)lineSpacing;
      float fadeRange = Math.max(1.0F, (float)maxLines - 1.0F);
      float fadeStartLine = this.fadeStartLineRatio * fadeRange;
      float fadeSpan = fadeRange - fadeStartLine;
      float posFadeProgress = fadeSpan <= 0.0F ? 0.0F : (lineIndex - fadeStartLine) / fadeSpan;
      float posAlpha = Math.max(0.0F, 1.0F - Math.max(0.0F, posFadeProgress));
      alpha *= posAlpha;
      if (posAlpha <= 0.0F) {
         return 0.0F;
      } else {
         if (item.isFading) {
            long fadeElapsed = now - item.fadeStartTime;
            float fadeDuration = (float)this.fadeOutDurationMs;
            float fadeProgress = (float)fadeElapsed / fadeDuration;
            alpha *= TextFadeEffect.fadeAlpha(fadeProgress, this.configBlinkFadeAnimation);
            alpha = Math.max(0.1F, alpha);
         }

         return alpha;
      }
   }

   private boolean shouldRemove(BonusListRenderer.BonusItem item, float alpha, long now, int maxLines, int lineSpacing, boolean horizontal, int index) {
      float lineIndex = horizontal ? (float)index : item.currentY / (float)lineSpacing;
      boolean positionHidden = lineIndex >= (float)maxLines;
      boolean fadeHidden = item.isFading && now - item.fadeStartTime >= this.fadeOutDurationMs;
      return positionHidden || fadeHidden;
   }

   private float applyQueueLinkage(float baseY) {
      if (!this.enableQueueLinkage) {
         this.linkageStartTime = 0L;
         this.linkedYOffset = 0.0F;
         return baseY;
      } else {
         SubtitleRenderer subtitleRenderer = SubtitleRenderer.getInstance();
         ScrollingIconRenderer iconRenderer = ScrollingIconRenderer.getInstance();
         boolean kfVisible = subtitleRenderer.hasVisibleSubtitle();
         boolean iconsVisible = iconRenderer.hasVisibleIcons();
         float kfBaseY = subtitleRenderer.getBaseTextY();
         float iconY = iconRenderer.getIconsAnchorY();
         float targetY;
         if (iconsVisible && kfVisible) {
            targetY = baseY;
         } else if (kfVisible) {
            targetY = kfBaseY;
         } else if (iconsVisible) {
            targetY = kfBaseY;
         } else {
            targetY = iconY + this.queueLinkageIconYOffset;
         }

         if (targetY >= baseY) {
            targetY = baseY;
         }

         return this.smoothLinkedY(baseY, targetY);
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

   private void drawComponentWithGlow(GuiGraphics guiGraphics, Font font, Component component, int x, int y, int alphaInt) {
      if (this.enableGlowEffect) {
         int glowAlpha = (int)((float)alphaInt * this.glowIntensity * this.glowAlphaMultiplier);
         glowAlpha = Math.max(25, Math.min(255, glowAlpha));
         glowAlpha = Math.max(0, Math.min(255, glowAlpha));
         int glowColor = glowAlpha << 24 | this.glowColorRgb & 16777215;
         Component glowComponent = stripColor(component);
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
            guiGraphics.drawString(font, glowComponent, x, y, glowColor, false);
            poseStack.popPose();
         }
      }

      int color = alphaInt << 24 | this.normalTextColor & 16777215;
      guiGraphics.drawString(font, component, x, y, color, this.enableTextShadow);
   }

   public static String getLocalizedNumber(int n) {
      String lang = Minecraft.getInstance().getLanguageManager().getSelected();
      boolean isChinese = lang != null && lang.startsWith("zh_");
      if (isChinese) {
         return switch (n) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(n);
         };
      } else {
         return String.valueOf(n);
      }
   }

   private int interpolateColor(int c1, int c2, float t) {
      int r1 = c1 >> 16 & 0xFF;
      int g1 = c1 >> 8 & 0xFF;
      int b1 = c1 & 0xFF;
      int r2 = c2 >> 16 & 0xFF;
      int g2 = c2 >> 8 & 0xFF;
      int b2 = c2 & 0xFF;
      int r = (int)((float)r1 + (float)(r2 - r1) * t);
      int g = (int)((float)g1 + (float)(g2 - g1) * t);
      int b = (int)((float)b1 + (float)(b2 - b1) * t);
      return r << 16 | g << 8 | b;
   }

   private static class AnimatedStat {
      final DigitalScrollEffect effect;
      float targetValue;
      float flashAlpha;
      float lastDisplayed;

      AnimatedStat(float initial, float duration, float refreshRate) {
         this.effect = new DigitalScrollEffect(duration, refreshRate, DigitalScrollEffect.Easing.CUBIC_OUT);
         this.targetValue = initial;
         this.effect.startAnimation(0.0F, initial);
         this.lastDisplayed = 0.0F;
         this.flashAlpha = 0.0F;
      }

      void add(float amount) {
         this.targetValue += amount;
         this.effect.startAnimation(this.effect.getCurrentValue(), this.targetValue);
      }

      void updateMax(float newVal) {
         if (newVal > this.targetValue) {
            this.targetValue = newVal;
            this.effect.startAnimation(this.effect.getCurrentValue(), this.targetValue);
         }
      }

      void update(long now, float dt) {
         this.effect.update(now);
         float current = this.effect.getCurrentValue();
         if (Math.abs(current - this.lastDisplayed) >= 0.1F) {
            this.flashAlpha = 1.0F;
            this.lastDisplayed = current;
         }

         if (this.flashAlpha > 0.0F) {
            this.flashAlpha = Math.max(0.0F, this.flashAlpha - dt * 5.0F);
         }
      }

      float getValue(boolean enableScroll) {
         return enableScroll ? this.effect.getCurrentValue() : this.targetValue;
      }
   }

   private class BonusItem {
      final String formatString;
      final boolean hasPlaceholder;
      String extraData;
      int stackCount = 1;
      float currentY;
      float currentXOffset;
      boolean isFading;
      long fadeStartTime;
      long spawnTime;
      final BonusListRenderer.AnimatedStat scoreStat;
      BonusListRenderer.AnimatedStat comboStat;
      BonusListRenderer.AnimatedStat mkStat;
      BonusListRenderer.AnimatedStat distanceStat;
      BonusListRenderer.AnimatedStat streakStat;
      int specialColor;
      final boolean isKillBonus;
      final float itemScale;
      final boolean showKillFeed;
      final String weaponName;
      final String victimName;
      final String killFeedFormatStr;
      final int killFeedVictimColorVal;
      private final List<TextScrambleEffect> scrambleEffects = new ArrayList<>();

      public BonusItem(String format, float initialScore, String extraData, int type, int specialColor, String weaponName, String victimName) {
         this.formatString = format;
         this.extraData = extraData != null ? extraData : "";
         this.currentY = 0.0F;
         this.currentXOffset = 0.0F;
         this.isFading = false;
         this.spawnTime = PreviewRenderTimeContext.currentTimeMillis();
         this.specialColor = specialColor;
         this.isKillBonus = type == 1 || type == 6 || type == 7 || type == 5;
         this.itemScale = this.isKillBonus ? BonusListRenderer.this.killBonusScale : 1.0F;
         this.showKillFeed = this.isKillBonus && BonusListRenderer.this.enableKillFeed;
         this.weaponName = weaponName;
         this.victimName = victimName;
         this.killFeedFormatStr = BonusListRenderer.this.killFeedFormat;
         int vColor = 16711680;

         try {
            String c = BonusListRenderer.this.killFeedVictimColor;
            if (c.startsWith("#")) {
               c = c.substring(1);
            }

            vColor = Integer.parseInt(c, 16);
         } catch (Exception var11) {
         }

         this.killFeedVictimColorVal = vColor;
         this.hasPlaceholder = format.contains("<score>");
         this.scoreStat = this.hasPlaceholder
            ? new BonusListRenderer.AnimatedStat(initialScore, BonusListRenderer.this.animationDuration, BonusListRenderer.this.animationRefreshRate)
            : null;
         this.initStats(format, extraData);
         this.initScrambleEffects(format);
      }

      private void initScrambleEffects(String format) {
         if (BonusListRenderer.this.enableTextScrolling) {
            Matcher matcher = BonusListRenderer.PLACEHOLDER_PATTERN.matcher(format);
            int lastEnd = 0;
            long duration = (long)(BonusListRenderer.this.animationDuration * BonusListRenderer.this.textScrollingDurationMultiplier * 1000.0F);

            long refresh;
            for (refresh = (long)(BonusListRenderer.this.textScrollingRefreshRate * 1000.0F); matcher.find(); lastEnd = matcher.end()) {
               String staticPart = format.substring(lastEnd, matcher.start());
               this.scrambleEffects.add(new TextScrambleEffect(staticPart, duration, refresh, true));
            }

            this.scrambleEffects.add(new TextScrambleEffect(format.substring(lastEnd), duration, refresh, true));
         }
      }

      private void initStats(String format, String extraData) {
         if (extraData != null && !extraData.isEmpty()) {
            if (format.contains("<combo>")) {
               this.comboStat = this.createStat(extraData, 1.25F);
            }

            if (format.contains("<multi_kill>")) {
               this.mkStat = this.createStat(extraData, 5.0F);
            }

            if (format.contains("<distance>")) {
               this.distanceStat = this.createStat(extraData, 5.0F);
            }

            if (format.contains("<streak>")) {
               this.streakStat = this.createStat(extraData, 5.0F);
            }
         }
      }

      private BonusListRenderer.AnimatedStat createStat(String data, float durationMult) {
         try {
            int val = Integer.parseInt(data);
            return new BonusListRenderer.AnimatedStat(
               (float)val, BonusListRenderer.this.animationDuration * durationMult, BonusListRenderer.this.animationRefreshRate
            );
         } catch (NumberFormatException var4) {
            return null;
         }
      }

      public void merge(float score, String newExtraData, boolean isComboFormat, String format) {
         this.stackCount++;
         if (this.scoreStat != null && !BonusListRenderer.this.enableStackMultiplier) {
            this.scoreStat.add(score);
         }

         if (newExtraData != null) {
            if (isComboFormat && this.comboStat != null) {
               try {
                  this.comboStat.updateMax((float)Integer.parseInt(newExtraData));
                  this.extraData = String.valueOf((int)this.comboStat.targetValue);
               } catch (NumberFormatException var9) {
               }
            }

            if (format.contains("<multi_kill>") && this.mkStat != null) {
               try {
                  this.mkStat.updateMax((float)Integer.parseInt(newExtraData));
                  this.extraData = String.valueOf((int)this.mkStat.targetValue);
               } catch (NumberFormatException var8) {
               }
            }

            if (format.contains("<distance>") && this.distanceStat != null) {
               try {
                  int dist = Integer.parseInt(newExtraData);
                  if ((float)dist != this.distanceStat.targetValue) {
                     this.distanceStat.targetValue = (float)dist;
                     this.distanceStat.effect.startAnimation(this.distanceStat.effect.getCurrentValue(), (float)dist);
                     this.extraData = String.valueOf(dist);
                  }
               } catch (NumberFormatException var7) {
               }
            }

            if (format.contains("<streak>") && this.streakStat != null) {
               try {
                  this.streakStat.updateMax((float)Integer.parseInt(newExtraData));
                  this.extraData = String.valueOf((int)this.streakStat.targetValue);
               } catch (NumberFormatException var6) {
               }
            }
         }
      }

      public void update(long now, float dt, float targetY) {
         if (this.scoreStat != null) {
            this.scoreStat.update(now, dt);
         }

         if (this.comboStat != null) {
            this.comboStat.update(now, dt);
         }

         if (this.mkStat != null) {
            this.mkStat.update(now, dt);
         }

         if (this.distanceStat != null) {
            this.distanceStat.update(now, dt);
         }

         if (this.streakStat != null) {
            this.streakStat.update(now, dt);
         }

         float smoothFactor = 1.0F - (float)Math.exp((double)(-BonusListRenderer.this.animationSpeed * dt));
         this.currentY = this.currentY + (targetY - this.currentY) * smoothFactor;
      }

      public void updateX(long now, float dt, float targetX) {
         if (this.scoreStat != null) {
            this.scoreStat.update(now, dt);
         }

         if (this.comboStat != null) {
            this.comboStat.update(now, dt);
         }

         if (this.mkStat != null) {
            this.mkStat.update(now, dt);
         }

         if (this.distanceStat != null) {
            this.distanceStat.update(now, dt);
         }

         if (this.streakStat != null) {
            this.streakStat.update(now, dt);
         }

         float smoothFactor = 1.0F - (float)Math.exp((double)(-BonusListRenderer.this.animationSpeed * dt));
         this.currentXOffset = this.currentXOffset + (targetX - this.currentXOffset) * smoothFactor;
      }

      public void render(
         GuiGraphics guiGraphics,
         Minecraft mc,
         float x,
         float y,
         float alpha,
         boolean alignLeft,
         boolean alignRight,
         float screenWidth,
         float globalScale,
         boolean horizontal
      ) {
         Component component = this.getDisplayComponent();
         Component killFeedComponent = null;
         if (this.showKillFeed) {
            killFeedComponent = this.buildKillFeedComponent();
         }

         int alphaInt = (int)(alpha * 255.0F);
         alphaInt = Math.max(0, Math.min(255, alphaInt));
         int textColor = alphaInt << 24 | 16777215;
         int baseTextWidth = mc.font.width(component);
         int feedTextWidth = killFeedComponent != null ? mc.font.width(killFeedComponent) : 0;
         long now = PreviewRenderTimeContext.currentTimeMillis();
         long elapsed = now - this.spawnTime;
         long enterDuration = BonusListRenderer.this.enterAnimationDuration;
         boolean sweepEnabled = BonusListRenderer.this.enableTextSweepAnimation;
         guiGraphics.pose().pushPose();
         float scaleOriginY = y + this.currentY + 9.0F / 2.0F;
         float currentScale = this.itemScale;
         if (this.showKillFeed && elapsed < 350L) {
            float progress = (float)elapsed / 350.0F;
            float easedProgress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
            float animationScaleMultiplier = Mth.lerp(easedProgress, 1.8F, 1.0F);
            currentScale *= animationScaleMultiplier;
         }

         guiGraphics.pose().translate(x, scaleOriginY, 0.0F);
         guiGraphics.pose().scale(currentScale, currentScale, 1.0F);
         guiGraphics.pose().translate(-x, -scaleOriginY, 0.0F);
         boolean renderOriginal = true;
         boolean renderFeed = false;
         float feedProgress = 0.0F;
         if (this.showKillFeed) {
            long feedStart = enterDuration * 4L;
            if (elapsed > feedStart) {
               feedProgress = (float)(elapsed - feedStart) / (float)enterDuration;
               feedProgress = Math.max(0.0F, Math.min(1.0F, feedProgress));
               renderFeed = true;
               if (feedProgress >= 1.0F) {
                  renderOriginal = false;
               }
            }
         }

         float totalScale = globalScale * this.itemScale;
         float screenAnchorX = x * globalScale;
         float pivotYScreen = scaleOriginY * globalScale;
         if (renderOriginal) {
            float drawX = this.calculateDrawX(x, baseTextWidth, alignLeft, alignRight);
            if (horizontal) {
               drawX += this.currentXOffset;
            }

            float drawY = y + this.currentY;
            if (sweepEnabled) {
               float entryProgress = (float)elapsed / (float)enterDuration;
               entryProgress = Math.max(0.0F, Math.min(1.0F, entryProgress));
               float screenWidthPx = (float)baseTextWidth * totalScale;
               float screenLeft = this.calculateScreenLeft(screenAnchorX, screenWidthPx, alignLeft, alignRight);
               float screenRight = screenLeft + screenWidthPx;
               float entryLeft = screenRight - screenWidthPx * entryProgress;
               float exitLeft = screenLeft + screenWidthPx * feedProgress;
               int scLeft = (int)Math.max(entryLeft, exitLeft);
               int scRight = (int)screenRight;
               int scY = (int)(pivotYScreen - 9.0F / 2.0F * totalScale);
               int scH = (int)(9.0F * totalScale);
               if (scRight > scLeft) {
                  guiGraphics.enableScissor(Math.max(0, scLeft), Math.max(0, scY), scRight, scY + scH + 2);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(drawX, drawY, 0.0F);
                  if (BonusListRenderer.this.enableTextBox) {
                     BonusTextBox.draw(
                        guiGraphics, (float)baseTextWidth, 9.0F, BonusListRenderer.this.textBoxBorderWidth, BonusListRenderer.this.textBoxColorRgb, alphaInt
                     );
                  }

                  BonusListRenderer.this.drawComponentWithGlow(guiGraphics, mc.font, component, 0, 0, alphaInt);
                  guiGraphics.pose().popPose();
                  guiGraphics.disableScissor();
               }
            } else {
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(drawX, drawY, 0.0F);
               if (BonusListRenderer.this.enableTextBox) {
                  BonusTextBox.draw(
                     guiGraphics, (float)baseTextWidth, 9.0F, BonusListRenderer.this.textBoxBorderWidth, BonusListRenderer.this.textBoxColorRgb, alphaInt
                  );
               }

               BonusListRenderer.this.drawComponentWithGlow(guiGraphics, mc.font, component, 0, 0, alphaInt);
               guiGraphics.pose().popPose();
            }
         }

         if (renderFeed && killFeedComponent != null) {
            float drawXx = this.calculateDrawX(x, feedTextWidth, alignLeft, alignRight);
            if (horizontal) {
               drawXx += this.currentXOffset;
            }

            float drawY = y + this.currentY;
            if (sweepEnabled) {
               float screenWidthPx = (float)feedTextWidth * totalScale;
               float screenLeft = this.calculateScreenLeft(screenAnchorX, screenWidthPx, alignLeft, alignRight);
               float revealRight = screenLeft + screenWidthPx * feedProgress;
               int scLeft = (int)screenLeft;
               int scRight = (int)revealRight;
               int scY = (int)(pivotYScreen - 9.0F / 2.0F * totalScale);
               int scH = (int)(9.0F * totalScale);
               if (scRight > scLeft) {
                  guiGraphics.enableScissor(Math.max(0, scLeft), Math.max(0, scY), scRight, scY + scH + 2);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(drawXx, drawY, 0.0F);
                  if (BonusListRenderer.this.enableTextBox) {
                     BonusTextBox.draw(
                        guiGraphics, (float)feedTextWidth, 9.0F, BonusListRenderer.this.textBoxBorderWidth, BonusListRenderer.this.textBoxColorRgb, alphaInt
                     );
                  }

                  BonusListRenderer.this.drawComponentWithGlow(guiGraphics, mc.font, killFeedComponent, 0, 0, alphaInt);
                  guiGraphics.pose().popPose();
                  guiGraphics.disableScissor();
               }
            } else {
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(drawXx, drawY, 0.0F);
               if (BonusListRenderer.this.enableTextBox) {
                  BonusTextBox.draw(
                     guiGraphics, (float)feedTextWidth, 9.0F, BonusListRenderer.this.textBoxBorderWidth, BonusListRenderer.this.textBoxColorRgb, alphaInt
                  );
               }

               BonusListRenderer.this.drawComponentWithGlow(guiGraphics, mc.font, killFeedComponent, 0, 0, alphaInt);
               guiGraphics.pose().popPose();
            }
         }

         guiGraphics.pose().popPose();
      }

      private float calculateDrawX(float x, int width, boolean alignLeft, boolean alignRight) {
         if (alignLeft) {
            return x;
         } else {
            return alignRight ? x - (float)width : x - (float)width / 2.0F;
         }
      }

      private float calculateScreenLeft(float anchorX, float widthPx, boolean alignLeft, boolean alignRight) {
         if (alignLeft) {
            return anchorX;
         } else {
            return alignRight ? anchorX - widthPx : anchorX - widthPx / 2.0F;
         }
      }

      private Component buildKillFeedComponent() {
         MutableComponent root = Component.empty();
         String fmt = this.killFeedFormatStr;
         float score = this.scoreStat != null ? this.scoreStat.getValue(BonusListRenderer.this.enableDigitalScroll) : 0.0F;
         Matcher m = Pattern.compile("(<weapon>|<target>|<score>)").matcher(fmt);
         int lastEnd = 0;

         while (m.find()) {
            String staticPart = fmt.substring(lastEnd, m.start());
            if (BonusListRenderer.this.cleanSubtitleContent) {
               staticPart = BonusListRenderer.cleanSubtitleText(staticPart);
            }

            if (!staticPart.isEmpty()) {
               root.append(Component.literal(staticPart).withStyle(Style.EMPTY.withColor(BonusListRenderer.this.normalTextColor)));
            }

            String tag = m.group(1);
            if (BonusListRenderer.this.cleanSubtitleContent && "<score>".equals(tag)) {
               lastEnd = m.end();
            } else {
               if ("<weapon>".equals(tag)) {
                  root.append(Component.literal(this.weaponName).withStyle(Style.EMPTY.withColor(BonusListRenderer.this.normalTextColor)));
               } else if ("<target>".equals(tag)) {
                  String displayVName = this.victimName;
                  if (I18nCompat.exists(displayVName)) {
                     displayVName = I18n.get(displayVName, new Object[0]);
                  }

                  root.append(Component.literal(displayVName).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(this.killFeedVictimColorVal))));
               } else if ("<score>".equals(tag)) {
                  String scoreStr;
                  if (Math.abs(score) < 1.0F && Math.abs(score) > 0.001F) {
                     scoreStr = String.format("%.1f", score);
                  } else {
                     scoreStr = String.valueOf(Math.round(score));
                  }

                  root.append(Component.literal(scoreStr).withStyle(Style.EMPTY.withColor(BonusListRenderer.this.normalTextColor)));
               }

               lastEnd = m.end();
            }
         }

         String tail = fmt.substring(lastEnd);
         if (BonusListRenderer.this.cleanSubtitleContent) {
            tail = BonusListRenderer.cleanSubtitleText(tail);
         }

         if (!tail.isEmpty()) {
            root.append(Component.literal(tail).withStyle(Style.EMPTY.withColor(BonusListRenderer.this.normalTextColor)));
         }

         return root;
      }

      private Component getDisplayComponent() {
         MutableComponent root = Component.empty();
         Matcher matcher = BonusListRenderer.PLACEHOLDER_PATTERN.matcher(this.formatString);
         int lastEnd = 0;
         int scrambleIdx = 0;

         while (matcher.find()) {
            String staticPart = this.formatString.substring(lastEnd, matcher.start());
            if (BonusListRenderer.this.cleanSubtitleContent) {
               staticPart = BonusListRenderer.cleanSubtitleText(staticPart);
            }

            if (BonusListRenderer.this.enableTextScrolling && scrambleIdx < this.scrambleEffects.size()) {
               TextScrambleEffect effect = this.scrambleEffects.get(scrambleIdx);
               root.append(effect != null ? effect.getCurrentText() : staticPart);
            } else {
               root.append(staticPart);
            }

            scrambleIdx++;
            String type = matcher.group(1);
            if (BonusListRenderer.this.cleanSubtitleContent && "score".equals(type)) {
               lastEnd = matcher.end();
            } else {
               switch (type) {
                  case "score":
                     float score = this.scoreStat != null ? this.scoreStat.getValue(BonusListRenderer.this.enableDigitalScroll) : 0.0F;
                     if (Math.abs(score) < 1.0F && Math.abs(score) > 0.001F) {
                        root.append(String.format("%.1f", score));
                     } else {
                        root.append(String.valueOf(Math.round(score)));
                     }
                     break;
                  case "combo": {
                     int val = this.comboStat != null
                        ? (int)this.comboStat.getValue(BonusListRenderer.this.enableDigitalScroll)
                        : this.tryParse(this.extraData);
                     root.append(this.createStyledComponent(String.valueOf(val), this.comboStat));
                     break;
                  }
                  case "multi_kill": {
                     int val = this.mkStat != null ? (int)this.mkStat.getValue(BonusListRenderer.this.enableDigitalScroll) : this.tryParse(this.extraData);
                     String text = BonusListRenderer.getLocalizedNumber(val);
                     root.append(this.createStyledComponent(text, this.mkStat));
                     break;
                  }
                  case "distance": {
                     int val = this.distanceStat != null
                        ? (int)this.distanceStat.getValue(BonusListRenderer.this.enableDigitalScroll)
                        : this.tryParse(this.extraData);
                     MutableComponent c = this.createStyledComponent(String.valueOf(val), this.distanceStat);
                     root.append(c);
                     MutableComponent unit = Component.literal("m");
                     if (c.getStyle().getColor() != null) {
                        unit.setStyle(c.getStyle());
                     }

                     root.append(unit);
                     break;
                  }
                  case "streak": {
                     int val = this.streakStat != null
                        ? (int)this.streakStat.getValue(BonusListRenderer.this.enableDigitalScroll)
                        : this.tryParse(this.extraData);
                     root.append(this.createStyledComponent(String.valueOf(val), this.streakStat));
                     break;
                  }
                  case "extra":
                     root.append(this.extraData);
               }

               lastEnd = matcher.end();
            }
         }

         String lastPart = this.formatString.substring(lastEnd);
         if (BonusListRenderer.this.cleanSubtitleContent) {
            lastPart = BonusListRenderer.cleanSubtitleText(lastPart);
         }

         if (BonusListRenderer.this.enableTextScrolling && scrambleIdx < this.scrambleEffects.size()) {
            TextScrambleEffect effect = this.scrambleEffects.get(scrambleIdx);
            root.append(effect != null ? effect.getCurrentText() : lastPart);
         } else {
            root.append(lastPart);
         }

         if (BonusListRenderer.this.enableStackMultiplier && this.stackCount > 1) {
            root.append(Component.literal(" ×" + this.stackCount).withStyle(Style.EMPTY.withColor(BonusListRenderer.this.normalTextColor)));
         }

         return root;
      }

      private MutableComponent createStyledComponent(String text, BonusListRenderer.AnimatedStat stat) {
         MutableComponent comp = Component.literal(text);
         int baseColor = this.specialColor != 0 ? this.specialColor : BonusListRenderer.this.normalTextColor;
         float flash = stat != null ? stat.flashAlpha : 0.0F;
         if (flash > 0.0F) {
            int color = BonusListRenderer.this.interpolateColor(baseColor, 16777215, flash);
            comp.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
         } else if (baseColor != 16777215) {
            comp.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(baseColor)));
         }

         return comp;
      }

      private int tryParse(String s) {
         try {
            return Integer.parseInt(s);
         } catch (Exception var3) {
            return 0;
         }
      }
   }

   private static record ParsedData(float score, String extraData, String victimName) {
      static BonusListRenderer.ParsedData parse(String data) {
         float score = 0.0F;
         String extraData = "";
         String victimName = null;
         if (data != null && !data.isEmpty()) {
            String[] parts = data.split("\\|", -1);
            if (parts.length > 0) {
               try {
                  score = Float.parseFloat(parts[0]);
               } catch (NumberFormatException var6) {
                  score = parseScore(parts[0]);
               }
            }

            if (parts.length > 1) {
               extraData = parts[1];
            }

            if (parts.length > 2) {
               victimName = parts[2];
            }
         }

         return new BonusListRenderer.ParsedData(score, extraData, victimName);
      }

      private static float parseScore(String data) {
         try {
            Matcher matcher = Pattern.compile("\\+([\\d.]+)").matcher(data);
            if (matcher.find()) {
               return Float.parseFloat(matcher.group(1));
            }

            matcher = Pattern.compile("([\\d.]+)").matcher(data);
            String lastMatch = null;

            while (matcher.find()) {
               lastMatch = matcher.group(1);
            }

            if (lastMatch != null) {
               return Float.parseFloat(lastMatch);
            }
         } catch (NumberFormatException var3) {
         }

         return 0.0F;
      }
   }
}
