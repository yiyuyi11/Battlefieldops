package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconEntranceBackground;
import cn.bfops.killicon.client.render.effect.IconGlowRenderEffect;
import cn.bfops.killicon.client.render.effect.IconRingEffect;
import cn.bfops.killicon.client.textures.IconTextureAnimationManager;
import cn.bfops.killicon.client.textures.ModTextures;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.common.killtype.KillTypeDefinition;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class ScrollingIconRenderer implements IHudRenderer {
   @Override public void resetPreview(){activeIcons.clear();pendingIcons.clear();isVisible=false;lastIconDisplayTime=0;hasCustomCenter=false;}

   private static final ScrollingIconRenderer INSTANCE = new ScrollingIconRenderer();
   private boolean linkageConfigLoaded = false;
   private static final long DEFAULT_DISPLAY_DURATION = 3000L;
   private static final long DEFAULT_ANIMATION_DURATION = 300L;
   private static final long DEFAULT_FADE_OUT_DURATION_MS = 100L;
   private static final long DEFAULT_POSITION_ANIMATION_DURATION = 300L;
   private static final float DEFAULT_START_SCALE = 2.0F;
   private static final int BASE_ICON_SIZE = 64;
   private static final float DEFAULT_ICON_SPACING = 8.0F;
   private static final int DEFAULT_MAX_VISIBLE_ICONS = 7;
   private static final int DEFAULT_MAX_PENDING_ICONS = 30;
   private static final long DEFAULT_DISPLAY_INTERVAL_MS = 100L;
   private static final int DEFAULT_HEADSHOT_COLOR = 13940736;
   private static final int DEFAULT_EXPLOSION_COLOR = 16219904;
   private static final int DEFAULT_CRIT_COLOR = 10275941;
   private float configScale = 1.0F;
   private int configXOffset = 0;
   private int configYOffset = 0;
   private String configScreenAnchor = "bottom_center";
   private int configScrollDirection = 1;
   private boolean configPinNewestIcon = false;
   private boolean configBlinkFadeAnimation = false;
   private boolean configEntranceBackground = false;
   private long configIconDelayMs = 0L;
   private float configEntranceBgSize = 64.0F;
   private long configEntranceBgFadeInMs = 200L;
   private long configEntranceBgFadeOutMs = 200L;
   private int configEntranceBgColor = 16777215;
   private int configEntranceBgHeadshotColor = 16732160;
   private float configEntranceBgPeakTransparency = 0.2F;
   private float configEntranceBgBorder = 0.5F;
   private int configEntranceBgBorderColor = 16777215;
   private int configEntranceBgHeadshotBorderColor = 16728832;
   private float configEntranceBgBorderAlpha = 0.2F;
   private long displayDuration = 3000L;
   private final Map<String, Boolean> ringEnableFlags = new HashMap<>();
   private long animationDuration = 300L;
   private long fadeOutDurationMs = 100L;
   private long positionAnimationDuration = 300L;
   private float startScale = 2.0F;
   private float iconSpacing = 8.0F;
   private int maxVisibleIcons = 7;
   private long displayIntervalMs = 100L;
   private int maxPendingIcons = 30;
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
   private boolean isVisible = false;
   private final List<ScrollingIconRenderer.ScrollingIcon> activeIcons = new ArrayList<>();
   private final List<ScrollingIconRenderer.ScrollingIcon> pendingIcons = new ArrayList<>();
   private long lastIconDisplayTime = 0L;
   private boolean hasCustomCenter = false;
   private float lastCustomCenterX = 0.0F;
   private JsonObject currentConfig;

   public static ScrollingIconRenderer getInstance() {
      return INSTANCE;
   }

   private void ensureConfigLoaded() {
      if (!this.linkageConfigLoaded) {
         JsonObject config = ConfigManager.getElementConfig("kill_icon", "scrolling");
         if (config != null) {
            this.loadConfig(config);
         }

         this.linkageConfigLoaded = true;
      }
   }

   public boolean hasVisibleIcons() {
      return !this.activeIcons.isEmpty() || !this.pendingIcons.isEmpty();
   }

   public float getIconsAnchorY() {
      this.ensureConfigLoaded();
      Minecraft mc = Minecraft.getInstance();
      int screenHeight = mc.getWindow().getGuiScaledHeight();
      return (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "scrolling");
      if (config != null) {
         boolean visible = !config.has("visible") || config.get("visible").getAsBoolean();
         if (!visible) {
            this.isVisible = false;
            this.activeIcons.clear();
         } else {
            this.loadConfig(config);
            if (this.displayDuration < this.animationDuration) {
               this.displayDuration = this.animationDuration;
            }

            this.isVisible = true;
            ScrollingIconRenderer.ScrollingIcon icon = new ScrollingIconRenderer.ScrollingIcon(context.type(), 0L, this.displayDuration);
            this.pendingIcons.add(icon);
            if (this.pendingIcons.size() > this.maxPendingIcons) {
               this.pendingIcons.remove(0);
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.isVisible && (!this.activeIcons.isEmpty() || !this.pendingIcons.isEmpty())) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         float centerX = this.resolveCenterX();
         this.processPendingIcons(currentTime, centerX);
         if (!this.activeIcons.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int centerY = ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
            boolean removedAny = false;
            Iterator<ScrollingIconRenderer.ScrollingIcon> iterator = this.activeIcons.iterator();

            while (iterator.hasNext()) {
               ScrollingIconRenderer.ScrollingIcon icon = iterator.next();
               long elapsed = currentTime - icon.startTime;
               long animElapsed = Math.max(0L, elapsed - this.configIconDelayMs);
               this.updatePosition(icon, currentTime);
               float currentScale = this.resolveScale(animElapsed);
               float alpha = this.resolveAlpha(icon, currentTime, animElapsed);
               if (this.shouldRemoveIcon(icon, currentTime, animElapsed)) {
                  iterator.remove();
                  removedAny = true;
               } else {
                  String texturePath = this.getTexturePath(icon.killType);
                  String textureKey = this.getTextureKey(icon.killType);
                  IconTextureAnimationManager.TextureFrame frame = IconTextureAnimationManager.getTextureFrame(
                     ConfigManager.getCurrentPresetId(), "kill_icon/scrolling", textureKey, texturePath, icon.startTime, this.currentConfig
                  );
                  String prefix = "anim_" + textureKey + "_";
                  boolean animEnabled = this.currentConfig != null
                     && this.currentConfig.has(prefix + "enable_texture_animation")
                     && this.currentConfig.get(prefix + "enable_texture_animation").getAsBoolean();
                  float drawWidth;
                  float drawHeight;
                  if (animEnabled) {
                     float aspectRatio = (float)frame.height / (float)Math.max(1, frame.width);
                     drawWidth = 64.0F;
                     drawHeight = 64.0F * aspectRatio;
                  } else {
                     float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
                     float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
                     drawWidth = 64.0F * frameWidthRatio;
                     drawHeight = 64.0F * frameHeightRatio;
                  }

                  if (this.configEntranceBackground) {
                     boolean headshot = icon.killType == 1;
                     int rectColor = headshot ? this.configEntranceBgHeadshotColor : this.configEntranceBgColor;
                     int borderColor = headshot ? this.configEntranceBgHeadshotBorderColor : this.configEntranceBgBorderColor;
                     IconEntranceBackground.drawRect(
                        guiGraphics,
                        elapsed,
                        icon.currentX,
                        (float)centerY,
                        this.configEntranceBgSize,
                        rectColor,
                        this.configEntranceBgFadeInMs,
                        this.configEntranceBgFadeOutMs,
                        this.configEntranceBgPeakTransparency
                     );
                     IconEntranceBackground.drawBorder(
                        guiGraphics,
                        elapsed,
                        icon.currentX,
                        (float)centerY,
                        this.configEntranceBgSize,
                        borderColor,
                        this.configEntranceBgFadeInMs,
                        this.configEntranceBgFadeOutMs,
                        this.configEntranceBgBorder,
                        this.configEntranceBgBorderAlpha
                     );
                  }

                  float focusedAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/scrolling", textureKey);
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(icon.currentX, (float)centerY, 0.0F);
                  guiGraphics.pose().scale(currentScale, currentScale, 1.0F);
                  guiGraphics.pose().translate(-drawWidth / 2.0F, -drawHeight / 2.0F, 0.0F);
                  if (this.configIconGlowEnabled) {
                     float glowFactor = this.configBlinkFadeAnimation ? focusedAlpha * focusedAlpha : focusedAlpha;
                     IconGlowRenderEffect.drawGlowFrame(
                        guiGraphics,
                        ModTextures.get(texturePath),
                        0,
                        0,
                        (int)drawWidth,
                        (int)drawHeight,
                        frame.u,
                        frame.v,
                        frame.width,
                        frame.height,
                        frame.totalWidth,
                        frame.totalHeight,
                        glowFactor,
                        this.configIconGlowColor,
                        this.configIconGlowIntensity,
                        this.configIconGlowSize
                     );
                     RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
                  }

                  guiGraphics.blit(
                     ModTextures.get(texturePath),
                     0,
                     0,
                     (int)drawWidth,
                     (int)drawHeight,
                     (float)frame.u,
                     (float)frame.v,
                     frame.width,
                     frame.height,
                     frame.totalWidth,
                     frame.totalHeight
                  );
                  guiGraphics.pose().popPose();
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                  icon.ringEffect.render(guiGraphics, icon.currentX, (float)centerY, currentTime);
               }
            }

            if (removedAny) {
               this.updateAllIconTargetPositions(currentTime, centerX);
            }

            if (this.activeIcons.isEmpty() && this.pendingIcons.isEmpty()) {
               this.isVisible = false;
            }
         }
      } else {
         this.isVisible = false;
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float originX, float originY) {
      if (this.isVisible && (!this.activeIcons.isEmpty() || !this.pendingIcons.isEmpty())) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         this.syncCustomCenter(originX);
         this.processPendingIcons(currentTime, originX);
         if (!this.activeIcons.isEmpty()) {
            this.updateAllIconTargetPositions(currentTime, originX);
            boolean removedAny = false;
            Iterator<ScrollingIconRenderer.ScrollingIcon> iterator = this.activeIcons.iterator();

            while (iterator.hasNext()) {
               ScrollingIconRenderer.ScrollingIcon icon = iterator.next();
               long elapsed = currentTime - icon.startTime;
               long animElapsed = Math.max(0L, elapsed - this.configIconDelayMs);
               this.updatePosition(icon, currentTime);
               float currentScale = this.resolveScale(animElapsed);
               float alpha = this.resolveAlpha(icon, currentTime, animElapsed);
               if (this.shouldRemoveIcon(icon, currentTime, animElapsed)) {
                  iterator.remove();
                  removedAny = true;
               } else {
                  String texturePath = this.getTexturePath(icon.killType);
                  String textureKey = this.getTextureKey(icon.killType);
                  IconTextureAnimationManager.TextureFrame frame = IconTextureAnimationManager.getTextureFrame(
                     ConfigManager.getCurrentPresetId(), "kill_icon/scrolling", textureKey, texturePath, icon.startTime, this.currentConfig
                  );
                  String prefix = "anim_" + textureKey + "_";
                  boolean animEnabled = this.currentConfig != null
                     && this.currentConfig.has(prefix + "enable_texture_animation")
                     && this.currentConfig.get(prefix + "enable_texture_animation").getAsBoolean();
                  float drawWidth;
                  float drawHeight;
                  if (animEnabled) {
                     float aspectRatio = (float)frame.height / (float)Math.max(1, frame.width);
                     drawWidth = 64.0F;
                     drawHeight = 64.0F * aspectRatio;
                  } else {
                     float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
                     float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
                     drawWidth = 64.0F * frameWidthRatio;
                     drawHeight = 64.0F * frameHeightRatio;
                  }

                  if (this.configEntranceBackground) {
                     boolean headshot = icon.killType == 1;
                     int rectColor = headshot ? this.configEntranceBgHeadshotColor : this.configEntranceBgColor;
                     int borderColor = headshot ? this.configEntranceBgHeadshotBorderColor : this.configEntranceBgBorderColor;
                     IconEntranceBackground.drawRect(
                        guiGraphics,
                        elapsed,
                        icon.currentX,
                        originY,
                        this.configEntranceBgSize,
                        rectColor,
                        this.configEntranceBgFadeInMs,
                        this.configEntranceBgFadeOutMs,
                        this.configEntranceBgPeakTransparency
                     );
                     IconEntranceBackground.drawBorder(
                        guiGraphics,
                        elapsed,
                        icon.currentX,
                        originY,
                        this.configEntranceBgSize,
                        borderColor,
                        this.configEntranceBgFadeInMs,
                        this.configEntranceBgFadeOutMs,
                        this.configEntranceBgBorder,
                        this.configEntranceBgBorderAlpha
                     );
                  }

                  float focusedAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/scrolling", textureKey);
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(icon.currentX, originY, 0.0F);
                  guiGraphics.pose().scale(currentScale, currentScale, 1.0F);
                  guiGraphics.pose().translate(-drawWidth / 2.0F, -drawHeight / 2.0F, 0.0F);
                  if (this.configIconGlowEnabled) {
                     float glowFactor = this.configBlinkFadeAnimation ? focusedAlpha * focusedAlpha : focusedAlpha;
                     IconGlowRenderEffect.drawGlowFrame(
                        guiGraphics,
                        ModTextures.get(texturePath),
                        0,
                        0,
                        (int)drawWidth,
                        (int)drawHeight,
                        frame.u,
                        frame.v,
                        frame.width,
                        frame.height,
                        frame.totalWidth,
                        frame.totalHeight,
                        glowFactor,
                        this.configIconGlowColor,
                        this.configIconGlowIntensity,
                        this.configIconGlowSize
                     );
                     RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
                  }

                  guiGraphics.blit(
                     ModTextures.get(texturePath),
                     0,
                     0,
                     (int)drawWidth,
                     (int)drawHeight,
                     (float)frame.u,
                     (float)frame.v,
                     frame.width,
                     frame.height,
                     frame.totalWidth,
                     frame.totalHeight
                  );
                  guiGraphics.pose().popPose();
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                  icon.ringEffect.render(guiGraphics, icon.currentX, originY, currentTime);
               }
            }

            if (removedAny) {
               this.updateAllIconTargetPositions(currentTime, originX);
            }

            if (this.activeIcons.isEmpty() && this.pendingIcons.isEmpty()) {
               this.isVisible = false;
            }
         }
      } else {
         this.isVisible = false;
      }
   }

   private void loadConfig(JsonObject config) {
      try {
         this.currentConfig = config;
         this.configScale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
         this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         this.configScrollDirection = config.has("scroll_direction") && "right".equals(config.get("scroll_direction").getAsString()) ? -1 : 1;
         this.configPinNewestIcon = config.has("pin_newest_icon") && config.get("pin_newest_icon").getAsBoolean();
         this.configBlinkFadeAnimation = config.has("blink_fade_animation") && config.get("blink_fade_animation").getAsBoolean();
         this.configEntranceBackground = config.has("entrance_background") && config.get("entrance_background").getAsBoolean();
         this.configIconDelayMs = config.has("icon_delay") ? Math.max(0L, (long)(config.get("icon_delay").getAsFloat() * 1000.0F)) : 0L;
         this.configEntranceBgSize = config.has("entrance_background_size") ? config.get("entrance_background_size").getAsFloat() : 64.0F;
         this.configEntranceBgFadeInMs = config.has("entrance_background_fade_in")
            ? Math.max(1L, (long)(config.get("entrance_background_fade_in").getAsFloat() * 1000.0F))
            : 200L;
         this.configEntranceBgFadeOutMs = config.has("entrance_background_fade_out")
            ? Math.max(1L, (long)(config.get("entrance_background_fade_out").getAsFloat() * 1000.0F))
            : 200L;
         this.configEntranceBgColor = config.has("entrance_background_color")
            ? IconEntranceBackground.parseColor(config.get("entrance_background_color").getAsString(), 16777215)
            : 16777215;
         this.configEntranceBgPeakTransparency = config.has("entrance_background_alpha")
            ? Mth.clamp(config.get("entrance_background_alpha").getAsFloat(), 0.0F, 1.0F)
            : 0.2F;
         this.configEntranceBgBorder = config.has("entrance_background_border") ? Math.max(0.0F, config.get("entrance_background_border").getAsFloat()) : 0.5F;
         this.configEntranceBgBorderColor = config.has("entrance_background_border_color")
            ? IconEntranceBackground.parseColor(config.get("entrance_background_border_color").getAsString(), 16777215)
            : 16777215;
         this.configEntranceBgBorderAlpha = config.has("entrance_background_border_alpha")
            ? Mth.clamp(config.get("entrance_background_border_alpha").getAsFloat(), 0.0F, 1.0F)
            : 0.2F;
         this.configEntranceBgHeadshotColor = config.has("entrance_background_headshot_color")
            ? IconEntranceBackground.parseColor(config.get("entrance_background_headshot_color").getAsString(), 16732160)
            : 16732160;
         this.configEntranceBgHeadshotBorderColor = config.has("entrance_background_headshot_border_color")
            ? IconEntranceBackground.parseColor(config.get("entrance_background_headshot_border_color").getAsString(), 16728832)
            : 16728832;
         this.displayDuration = config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 3000L;
         boolean defaultRingEnable = !config.has("enable_icon_effect") || config.get("enable_icon_effect").getAsBoolean();
         this.ringEnableFlags
            .put("enable_ring_effect_crit", config.has("enable_ring_effect_crit") ? config.get("enable_ring_effect_crit").getAsBoolean() : defaultRingEnable);
         this.ringEnableFlags
            .put(
               "enable_ring_effect_headshot",
               config.has("enable_ring_effect_headshot") ? config.get("enable_ring_effect_headshot").getAsBoolean() : defaultRingEnable
            );
         this.ringEnableFlags
            .put(
               "enable_ring_effect_explosion",
               config.has("enable_ring_effect_explosion") ? config.get("enable_ring_effect_explosion").getAsBoolean() : defaultRingEnable
            );
         this.animationDuration = config.has("animation_duration") ? (long)(config.get("animation_duration").getAsFloat() * 1000.0F) : 300L;
         this.fadeOutDurationMs = config.has("fade_out_duration") ? Math.max(1L, (long)(config.get("fade_out_duration").getAsFloat() * 1000.0F)) : 100L;
         this.positionAnimationDuration = config.has("position_animation_duration")
            ? (long)(config.get("position_animation_duration").getAsFloat() * 1000.0F)
            : 300L;
         this.startScale = config.has("start_scale") ? config.get("start_scale").getAsFloat() : 2.0F;
         this.iconSpacing = config.has("icon_spacing") ? config.get("icon_spacing").getAsFloat() : 8.0F;
         this.maxVisibleIcons = config.has("max_visible_icons") ? config.get("max_visible_icons").getAsInt() : 7;
         this.displayIntervalMs = config.has("display_interval_ms") ? (long)config.get("display_interval_ms").getAsInt() : 100L;
         this.maxPendingIcons = config.has("max_pending_icons") ? config.get("max_pending_icons").getAsInt() : 30;
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
         ClientMessageLogger.chatWarn("battlefield_ops.client.scrolling.config_error");
         this.currentConfig = null;
         this.configScale = 1.0F;
         this.configXOffset = 0;
         this.configYOffset = 0;
         this.configScreenAnchor = "bottom_center";
         this.displayDuration = 3000L;
         this.ringEnableFlags.clear();
         this.ringEnableFlags.put("enable_ring_effect_crit", true);
         this.ringEnableFlags.put("enable_ring_effect_headshot", true);
         this.ringEnableFlags.put("enable_ring_effect_explosion", true);
         this.animationDuration = 300L;
         this.positionAnimationDuration = 300L;
         this.startScale = 2.0F;
         this.iconSpacing = 8.0F;
         this.maxVisibleIcons = 7;
         this.displayIntervalMs = 100L;
         this.maxPendingIcons = 30;
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

   private void processPendingIcons(long currentTime, float centerX) {
      while (!this.pendingIcons.isEmpty() && currentTime - this.lastIconDisplayTime >= this.displayIntervalMs) {
         ScrollingIconRenderer.ScrollingIcon nextIcon = this.pendingIcons.remove(0);
         nextIcon.startTime = currentTime;
         this.lastIconDisplayTime = currentTime;
         boolean ringEnabled = this.isRingEnabledForKillType(nextIcon.killType);
         if (ringEnabled) {
            nextIcon.ringEffect
               .setRingParams(
                  this.ringCritRadius,
                  this.ringCritThickness,
                  this.ringHeadshotRadius,
                  this.ringHeadshotThickness,
                  this.ringExplosionRadius,
                  this.ringExplosionThickness
               );
            nextIcon.ringEffect
               .trigger(currentTime, true, nextIcon.killType, this.resolveHeadshotEffectRgb(), this.resolveExplosionEffectRgb(), this.resolveCritEffectRgb());
         } else {
            nextIcon.ringEffect.trigger(currentTime, false, nextIcon.killType, 0, 0, 0);
         }

         this.addIcon(nextIcon, currentTime, centerX);
      }
   }

   private void addIcon(ScrollingIconRenderer.ScrollingIcon icon, long currentTime, float centerX) {
      this.activeIcons.add(icon);
      this.updateAllIconTargetPositions(currentTime, centerX);
      icon.prevX = icon.targetX;
      icon.currentX = icon.targetX;
      icon.positionAnimationStart = currentTime;
   }

   private void updateAllIconTargetPositions(long currentTime, float centerX) {
      if (!this.activeIcons.isEmpty()) {
         int direction = this.configScrollDirection;
         float spacing = this.resolveIconSpacing();
         int size = this.activeIcons.size();
         int visibleStart = Math.max(0, size - this.maxVisibleIcons);
         int visibleCount = size - visibleStart;
         if (this.configPinNewestIcon) {
            float newestFixedX = centerX;
            int newestIndex = size - 1;

            for (int i = 0; i < visibleStart; i++) {
               ScrollingIconRenderer.ScrollingIcon icon = this.activeIcons.get(i);
               float overflowX = newestFixedX + (float)(direction * (newestIndex - i)) * spacing;
               this.updateTarget(icon, overflowX, currentTime);
               if (icon.forcedFadeStartTime < 0L) {
                  icon.forcedFadeStartTime = currentTime;
               }
            }

            for (int ix = visibleStart; ix < size; ix++) {
               ScrollingIconRenderer.ScrollingIcon icon = this.activeIcons.get(ix);
               float newTargetX = newestFixedX + (float)(direction * (newestIndex - ix)) * spacing;
               this.updateTarget(icon, newTargetX, currentTime);
            }
         } else {
            float rightmostSlotX = centerX + (float)direction * ((float)(visibleCount - 1) / 2.0F) * spacing;

            for (int ix = 0; ix < visibleStart; ix++) {
               ScrollingIconRenderer.ScrollingIcon icon = this.activeIcons.get(ix);
               float overflowX = rightmostSlotX + (float)(direction * (visibleStart - ix)) * spacing;
               this.updateTarget(icon, overflowX, currentTime);
               if (icon.forcedFadeStartTime < 0L) {
                  icon.forcedFadeStartTime = currentTime;
               }
            }

            for (int ixx = visibleStart; ixx < size; ixx++) {
               ScrollingIconRenderer.ScrollingIcon icon = this.activeIcons.get(ixx);
               float position = (float)(ixx - visibleStart) - (float)(visibleCount - 1) / 2.0F;
               float newTargetX = centerX - (float)direction * position * spacing;
               this.updateTarget(icon, newTargetX, currentTime);
            }
         }
      }
   }

   private void updateTarget(ScrollingIconRenderer.ScrollingIcon icon, float newTargetX, long currentTime) {
      if (Math.abs(icon.targetX - newTargetX) > 0.1F) {
         icon.prevX = icon.currentX;
         icon.targetX = newTargetX;
         icon.positionAnimationStart = currentTime;
      }
   }

   private float resolveCenterX() {
      Minecraft mc = Minecraft.getInstance();
      int screenWidth = mc.getWindow().getGuiScaledWidth();
      return (float)ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
   }

   private float resolveIconSpacing() {
      float baseSize = 64.0F * this.configScale;
      return this.iconSpacing * baseSize;
   }

   private void updatePosition(ScrollingIconRenderer.ScrollingIcon icon, long currentTime) {
      if (!(Math.abs(icon.currentX - icon.targetX) <= 0.1F)) {
         long moveElapsed = currentTime - icon.positionAnimationStart;
         float progress = Math.min((float)moveElapsed / (float)this.positionAnimationDuration, 1.0F);
         float easedProgress = 1.0F - (1.0F - progress) * (1.0F - progress);
         icon.currentX = Mth.lerp(easedProgress, icon.prevX, icon.targetX);
      }
   }

   private float resolveScale(long elapsed) {
      float endScale = 1.0F * this.configScale;
      if (elapsed >= this.animationDuration) {
         return endScale;
      } else {
         float initialScale = this.startScale * this.configScale;
         float progress = (float)elapsed / (float)this.animationDuration;
         progress = 1.0F - (float)Math.pow((double)(1.0F - progress), 3.0);
         return Mth.lerp(progress, initialScale, endScale);
      }
   }

   private float resolveAlpha(ScrollingIconRenderer.ScrollingIcon icon, long currentTime, long elapsed) {
      long fadeInDuration = Math.max(1L, this.animationDuration);
      float fadeInProgress = Math.min((float)elapsed / (float)fadeInDuration, 1.0F);
      float easedIn = 1.0F - (float)Math.pow((double)(1.0F - fadeInProgress), 3.0);
      float baseAlpha = Mth.clamp(easedIn, 0.0F, 1.0F);
      if (icon.forcedFadeStartTime >= 0L) {
         long fadeElapsed = currentTime - icon.forcedFadeStartTime;
         float fadeProgress = (float)fadeElapsed / (float)this.fadeOutDurationMs;
         float alpha = this.resolveFadeAlpha(fadeProgress);
         return Mth.clamp(baseAlpha * alpha, 0.0F, 1.0F);
      } else if (elapsed <= icon.displayDuration) {
         return baseAlpha;
      } else {
         long fadeElapsed = elapsed - icon.displayDuration;
         float fadeProgress = (float)fadeElapsed / (float)this.fadeOutDurationMs;
         float alpha = this.resolveFadeAlpha(fadeProgress);
         return Mth.clamp(baseAlpha * alpha, 0.0F, 1.0F);
      }
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

   private boolean shouldRemoveIcon(ScrollingIconRenderer.ScrollingIcon icon, long currentTime, long elapsed) {
      if (icon.forcedFadeStartTime >= 0L) {
         long fadeElapsed = currentTime - icon.forcedFadeStartTime;
         return fadeElapsed >= this.fadeOutDurationMs;
      } else {
         return elapsed >= icon.displayDuration + this.fadeOutDurationMs;
      }
   }

   private String getTexturePath(int killType) {
      String textureKey = this.getTextureKey(killType);
      return ElementTextureDefinition.getSelectedTextureFileName(ConfigManager.getCurrentPresetId(), "kill_icon/scrolling", textureKey, this.currentConfig);
   }

   private String getTextureKey(int killType) {
      return KillTypeRegistry.get(killType).textureKey();
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

   private boolean isRingEnabledForKillType(int killType) {
      KillTypeDefinition def = KillTypeRegistry.get(killType);
      return def != null && def.ringEnableKey() != null ? this.ringEnableFlags.getOrDefault(def.ringEnableKey(), false) : false;
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

   private void syncCustomCenter(float centerX) {
      if (!this.hasCustomCenter) {
         this.hasCustomCenter = true;
         this.lastCustomCenterX = centerX;
      } else {
         float delta = centerX - this.lastCustomCenterX;
         if (!(Math.abs(delta) <= 0.1F)) {
            this.lastCustomCenterX = centerX;
            if (!this.activeIcons.isEmpty()) {
               for (ScrollingIconRenderer.ScrollingIcon icon : this.activeIcons) {
                  icon.prevX += delta;
                  icon.currentX += delta;
                  icon.targetX += delta;
               }
            }
         }
      }
   }

   private static final class ScrollingIcon {
      private final int killType;
      private long startTime;
      private final long displayDuration;
      private final IconRingEffect ringEffect = new IconRingEffect();
      private float prevX;
      private float currentX;
      private float targetX;
      private long positionAnimationStart;
      private long forcedFadeStartTime = -1L;

      private ScrollingIcon(int killType, long startTime, long displayDuration) {
         this.killType = killType;
         this.startTime = startTime;
         this.displayDuration = displayDuration;
      }
   }
}
