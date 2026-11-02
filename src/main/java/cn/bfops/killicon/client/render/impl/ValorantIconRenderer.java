package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.config.ValorantStyleCatalog;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconGlowProcessor;
import cn.bfops.killicon.client.textures.IconTextureAnimationManager;
import cn.bfops.killicon.client.textures.ModTextures;

public class ValorantIconRenderer implements IHudRenderer {
   @Override public void resetPreview(){visible=false;startTime=-1;}

   private static final long DEFAULT_DISPLAY_DURATION = 2600L;
   private static final long FIVE_PLUS_FADE_IN_DURATION_MS = 300L;
   private static final long PARTICLE_BASE_DURATION_MS = 620L;
   private static final long BAR_BOUNCE_START_MS = 20L;
   private static final long BAR_BOUNCE_PEAK_MS = 180L;
   private static final long BAR_BOUNCE_HOLD_END_MS = 270L;
   private static final long BAR_BOUNCE_END_MS = 620L;
   private static final long BAR_SPIN_START_MS = 750L;
   private static final long BAR_SPIN_DURATION_180_MS = 700L;
   private static final long BAR_SPIN_DURATION_360_MS = 1000L;
   private static final long FADE_OUT_DURATION_MS = 220L;
   private static final float DEFAULT_ICON_ENTRY_OFFSET_PX = -16.0F;
   private static final float DEFAULT_ICON_ENTRY_DURATION_SECONDS = 0.1F;
   private static final float BAR_BOUNCE_DISTANCE_PX = 9.0F;
   private static final float VISUAL_CENTER_Y_OFFSET_PX = 6.0F;
   private static final float GLOBAL_VISUAL_SCALE = 0.95F;
   private static final float ICON_BASE_SIZE = 116.0F;
   private static final float BAR_BASE_SIZE = 32.0F;
   private static final float HEADSHOT_SIZE_MULTIPLIER = 0.16F;
   private static final float PARTICLE_BASE_SIZE = 112.0F;
   private static final float PARTICLE_FRAME_ANCHOR_Y_RATIO = 0.8046875F;
   private static final float BAR_RING_RADIUS = 36.0F;
   private static final int HALO_SEGMENTS = 72;
   private static final float GAIA_DEFAULT_BRIGHTNESS = 1.3F;
   private static final float GAIA_DEFAULT_CONTRAST = 1.1F;
   private static final int DEFAULT_ICON_FLASH_COLOR = 16722486;
   private static final int MAX_ICON_FLASH_COUNT = 12;
   private static final int PRIME_PARTICLE_SMALL_COLOR = 16765240;
   private static final int GAIA_PARTICLE_SMALL_COLOR = 14831708;
   private static final int DEFAULT_ACCENT_COLOR = 14831708;
   private static final float DEFAULT_ICON_FLASH_HOLD_DURATION_SECONDS = 0.08F;
   private static final float ICON_FLASH_MAX_ALPHA = 0.9F;
   private static final float ICON_TEXTURE_SOURCE_SIZE = 1729.0F;
   private static final float DEFAULT_HERO_FLAME_SOURCE_SCALE = 8.0F;
   private static final float DEFAULT_LARGE_SPARKS_SOURCE_SCALE = 6.8F;
   private static final float DEFAULT_X_SPARKS_SOURCE_SCALE = 11.0F;
   private static final float HERO_FLAME_Y_OFFSET_PX = -30.0F;
   private static final float LARGE_SPARKS_Y_OFFSET_PX = 2.0F;
   private static final float X_SPARKS_Y_OFFSET_PX = -8.0F;
   private static final ValorantIconRenderer.ParticleOverlaySpec HERO_FLAME_PARTICLE = new ValorantIconRenderer.ParticleOverlaySpec(
      "hero_flame", 0.4934375F, 0.49079242F, 8.0F, 0.0F, -30.0F, false
   );
   private static final ValorantIconRenderer.ParticleOverlaySpec LARGE_SPARKS_PARTICLE = new ValorantIconRenderer.ParticleOverlaySpec(
      "large_sparks", 0.49052733F, 0.5185547F, 6.8F, 0.0F, 2.0F, true
   );
   private static final ValorantIconRenderer.ParticleOverlaySpec X_SPARKS_PARTICLE = new ValorantIconRenderer.ParticleOverlaySpec(
      "x_sparks", 0.48015872F, 0.56722003F, 11.0F, 0.0F, -8.0F, false
   );
   private static final float[][] BAR_LAYOUT_ANGLES = new float[][]{
      {0.0F},
      {-90.0F, 90.0F},
      {0.0F, -120.0F, 120.0F},
      {0.0F, -90.0F, 90.0F, 180.0F},
      {0.0F, -72.0F, 72.0F, -144.0F, 144.0F},
      {0.0F, -60.0F, 60.0F, -120.0F, 120.0F, 180.0F}
   };
   private static final float[][] GLOW_OFFSETS = new float[][]{
      {-1.0F, 0.0F},
      {1.0F, 0.0F},
      {0.0F, -1.0F},
      {0.0F, 1.0F},
      {-0.70710677F, -0.70710677F},
      {-0.70710677F, 0.70710677F},
      {0.70710677F, -0.70710677F},
      {0.70710677F, 0.70710677F}
   };
   private float configScale = 1.0F;
   private float configXOffset = 0.0F;
   private float configYOffset = 80.0F;
   private String configScreenAnchor = "bottom_center";
   private float configBarXOffset = 0.0F;
   private float configBarYOffset = 0.0F;
   private float configBarRadiusOffset = 0.0F;
   private float configIconScale = 1.0F;
   private float configHeadshotAnimInitialScale = 1.8F;
   private float configHeadshotAnimDuration = 0.25F;
   private float configBarEntryInitialScale = 1.6F;
   private float configBarEntryDuration = 0.18F;
   private float configBrightness = 1.0F;
   private boolean configIconGlowEnabled = false;
   private int configIconGlowColor = 16777215;
   private float configIconGlowIntensity = 0.45F;
   private float configIconGlowSize = 4.0F;
   private boolean configHaloRingEnabled = false;
   private float configHaloRingRadius = 30.0F;
   private float configHaloRingWidth = 1.5F;
   private int configHaloRingColor = 16777215;
   private boolean configBladeEffect = true;
   private boolean configBladeRotationEffect = true;
   private float configBladeDecelerationWindowSec = 1.5F;
   private float configMathParticleDensity = 1.0F;
   private float configMathParticleSpread = 1.0F;
   private float configMathParticleSize = 1.0F;
   private JsonObject currentConfig;
   private boolean configMathParticleEffect = false;
   private long startTime = -1L;
   private boolean visible = false;
   private int comboCount = 1;
   private boolean headshotTrigger = false;
   private float spinDirection = 1.0F;
   private ValorantIconRenderer.SmallParticle[] smallParticles = new ValorantIconRenderer.SmallParticle[0];

   public static void clearProcessedTextureCache() {
      IconGlowProcessor.clearCache();
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      if (context.comboCount() > 0) {
         if (context.type() != 4 && context.type() != 5) {
            JsonObject config = ConfigManager.getElementConfig("kill_icon", "valorant");
            if (config != null) {
               boolean enabled = !config.has("visible") || config.get("visible").getAsBoolean();
               if (!enabled) {
                  this.visible = false;
               } else {
                  this.loadConfig(config);
                  this.comboCount = Mth.clamp(context.comboCount(), 1, 6);
                  this.headshotTrigger = context.type() == 1;
                  this.spinDirection = ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F;
                  if (this.configMathParticleEffect) {
                     this.generateParticleState();
                  } else {
                     this.smallParticles = new ValorantIconRenderer.SmallParticle[0];
                  }

                  this.startTime = PreviewRenderTimeContext.currentTimeMillis();
                  this.visible = true;
               }
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.visible && this.startTime >= 0L) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         float centerX = (float)ScreenAnchor.resolveCenterX(this.configScreenAnchor, Math.round(this.configXOffset), screenWidth);
         float centerY = (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, Math.round(this.configYOffset), screenHeight) + 6.0F;
         this.renderAt(guiGraphics, partialTick, centerX, centerY);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      if (this.visible && this.startTime >= 0L) {
         JsonObject liveConfig = ConfigManager.getElementConfig("kill_icon", "valorant");
         if (liveConfig != null) {
            this.loadConfig(liveConfig);
         }

         long elapsed = PreviewRenderTimeContext.currentTimeMillis() - this.startTime;
         long displayDuration = this.resolveDisplayDuration();
         if (elapsed > displayDuration) {
            this.visible = false;
            this.startTime = -1L;
         } else {
            float alpha = this.resolveAlpha(elapsed, displayDuration);
            float iconOffsetY = this.resolveIconOffsetY(elapsed);
            float barTravel = this.resolveBarTravel(elapsed);
            float barRotation = this.resolveBarRotation(elapsed);
            ValorantIconRenderer.FlashState flash = this.resolveFlashState(elapsed);
            String presetId = ConfigManager.getCurrentPresetId();
            String emblemTexturePath = this.resolveSelectedTexture(presetId, "emblem");
            String frameTexturePath = this.resolveSelectedTexture(presetId, "frame");
            String bladeTexturePath = this.resolveSelectedTexture(presetId, "blade");
            String barTexturePath = this.resolveSelectedTexture(presetId, "bar");
            String headshotTexturePath = this.resolveSelectedTexture(presetId, "headshot");
            String baseParticleTexturePath = this.resolveSelectedTexture(presetId, "base_particle");
            String heroFlameTexturePath = this.resolveSelectedTexture(presetId, "hero_flame");
            String largeSparksTexturePath = this.resolveSelectedTexture(presetId, "large_sparks");
            String xSparksTexturePath = this.resolveSelectedTexture(presetId, "x_sparks");
            String styleId = this.resolveStyleId();
            boolean gaiaSkin = this.isGaiaSkin(styleId, emblemTexturePath, barTexturePath);
            boolean bladeEnabled = this.configBladeEffect;
            String activeBladeTexturePath = bladeEnabled ? this.resolveBladeTexturePath(styleId, bladeTexturePath) : null;
            float emblemWidth = 116.0F
               * this.configIconScale
               * this.resolveTextureScale("emblem")
               * this.resolveFrameRatio("emblem", "texture_frame_width_ratio");
            float emblemHeight = 116.0F
               * this.configIconScale
               * this.resolveTextureScale("emblem")
               * this.resolveFrameRatio("emblem", "texture_frame_height_ratio");
            float frameWidth = 116.0F * this.resolveTextureScale("frame") * this.resolveFrameRatio("frame", "texture_frame_width_ratio");
            float frameHeight = 116.0F * this.resolveTextureScale("frame") * this.resolveFrameRatio("frame", "texture_frame_height_ratio");
            float bladeWidth = 116.0F * this.resolveTextureScale("blade") * this.resolveFrameRatio("blade", "texture_frame_width_ratio");
            float bladeHeight = 116.0F * this.resolveTextureScale("blade") * this.resolveFrameRatio("blade", "texture_frame_height_ratio");
            float headshotWidth = 18.56F * this.resolveTextureScale("headshot") * this.resolveFrameRatio("headshot", "texture_frame_width_ratio");
            float headshotHeight = 18.56F * this.resolveTextureScale("headshot") * this.resolveFrameRatio("headshot", "texture_frame_height_ratio");
            float barBaseSize = 32.0F;
            float barWidth = barBaseSize * this.resolveTextureScale("bar") * this.resolveFrameRatio("bar", "texture_frame_width_ratio");
            float barHeight = barBaseSize * this.resolveTextureScale("bar") * this.resolveFrameRatio("bar", "texture_frame_height_ratio");
            float effectiveBrightness = this.resolveEffectiveBrightness(gaiaSkin);
            float effectiveContrast = this.resolveEffectiveContrast(gaiaSkin);
            boolean accentTintEnabled = this.isAccentTintEnabled();
            int accentColor = this.resolveAccentColor();
            int defaultParticleColor = this.resolveDefaultParticleColor(accentTintEnabled, accentColor, gaiaSkin);
            int xSparksDefaultColor = this.resolveConfiguredColor("color_icon_flash", 16722486);
            int headshotOverlayColor = this.resolveConfiguredColor("color_headshot_overlay", 16722486);
            boolean frameEntryLocked = this.isEntryMotionFrameLocked(styleId);
            float rootMotionOffsetY = frameEntryLocked ? 0.0F : iconOffsetY;
            float emblemMotionOffsetY = frameEntryLocked ? iconOffsetY : 0.0F;
            float resolvedHeadshotXOffset = (float)ValorantStyleCatalog.getHeadshotOffsetX(styleId) + this.resolveTextureOffset("headshot", "texture_x_offset");
            float resolvedHeadshotYOffset = (float)ValorantStyleCatalog.getHeadshotOffsetY(styleId) + this.resolveTextureOffset("headshot", "texture_y_offset");
            float barRadiusOffset = this.configBarRadiusOffset;
            float barCenterXOffset = this.configBarXOffset + this.resolveTextureOffset("bar", "texture_x_offset");
            float barCenterYOffset = this.configBarYOffset + this.resolveTextureOffset("bar", "texture_y_offset");
            float[] barLayoutAngles = BAR_LAYOUT_ANGLES[Mth.clamp(this.comboCount, 1, 6) - 1];

            try {
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(centerX, centerY + rootMotionOffsetY, 0.0F);
               float renderScale = this.configScale * 0.95F;
               guiGraphics.pose().scale(renderScale, renderScale, 1.0F);
               if (this.configMathParticleEffect) {
                  int mathParticleColor = this.resolveLayerParticleColor("base_particle", defaultParticleColor);
                  this.renderSmallParticles(guiGraphics, elapsed, alpha, mathParticleColor);
               } else {
                  this.renderBaseParticles(guiGraphics, baseParticleTexturePath, elapsed, alpha, effectiveBrightness, effectiveContrast, defaultParticleColor);
               }

               this.renderGradientHaloRing(guiGraphics, elapsed, alpha);
               this.renderBarRing(
                  guiGraphics,
                  barTexturePath,
                  barWidth,
                  barHeight,
                  barTravel,
                  alpha,
                  barRotation,
                  barRadiusOffset,
                  barCenterXOffset,
                  barCenterYOffset,
                  barLayoutAngles,
                  accentTintEnabled,
                  accentColor,
                  effectiveBrightness,
                  effectiveContrast,
                  elapsed
               );
               RenderSystem.defaultBlendFunc();
               guiGraphics.pose().pushPose();
               this.drawColorizableTexture(
                  guiGraphics,
                  "frame",
                  frameTexturePath,
                  frameWidth,
                  frameHeight,
                  alpha,
                  16777215,
                  false,
                  accentTintEnabled,
                  accentColor,
                  effectiveBrightness,
                  effectiveContrast
               );
               if (activeBladeTexturePath != null && !activeBladeTexturePath.isEmpty()) {
                  guiGraphics.pose().pushPose();
                  float bladeRotation = this.resolveFrameBladeRotation(styleId, elapsed);
                  if (Math.abs(bladeRotation) > 0.01F) {
                     guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(bladeRotation));
                  }

                  this.drawColorizableTexture(
                     guiGraphics,
                     "blade",
                     activeBladeTexturePath,
                     bladeWidth,
                     bladeHeight,
                     alpha,
                     16777215,
                     false,
                     accentTintEnabled,
                     accentColor,
                     effectiveBrightness,
                     effectiveContrast
                  );
                  guiGraphics.pose().popPose();
               }

               guiGraphics.pose().popPose();
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(0.0F, emblemMotionOffsetY, 0.0F);
               this.drawColorizableTexture(
                  guiGraphics,
                  "emblem",
                  emblemTexturePath,
                  emblemWidth,
                  emblemHeight,
                  alpha,
                  16777215,
                  false,
                  accentTintEnabled,
                  accentColor,
                  effectiveBrightness,
                  effectiveContrast
               );
               if (flash.alpha() > 0.0F) {
                  this.drawColorizableTexture(
                     guiGraphics,
                     "emblem",
                     emblemTexturePath,
                     emblemWidth,
                     emblemHeight,
                     alpha * flash.alpha(),
                     flash.color(),
                     false,
                     accentTintEnabled,
                     accentColor,
                     effectiveBrightness,
                     effectiveContrast
                  );
               }

               if (this.headshotTrigger) {
                  ValorantIconRenderer.HeadshotAnimState hsAnim = this.resolveHeadshotAnimState(elapsed, headshotOverlayColor);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(resolvedHeadshotXOffset, resolvedHeadshotYOffset, 0.0F);
                  if (Math.abs(hsAnim.scale() - 1.0F) > 0.001F) {
                     guiGraphics.pose().scale(hsAnim.scale(), hsAnim.scale(), 1.0F);
                  }

                  this.drawColorizableTexture(
                     guiGraphics, "headshot", headshotTexturePath, headshotWidth, headshotHeight, alpha, hsAnim.color(), false, false, 16777215, 1.0F, 1.0F
                  );
                  guiGraphics.pose().popPose();
               }

               guiGraphics.pose().popPose();
               RenderSystem.defaultBlendFunc();
               if (!this.configMathParticleEffect) {
                  this.renderOverlayParticles(
                     guiGraphics,
                     HERO_FLAME_PARTICLE,
                     heroFlameTexturePath,
                     elapsed,
                     alpha,
                     emblemWidth,
                     emblemHeight,
                     effectiveBrightness,
                     effectiveContrast,
                     defaultParticleColor,
                     emblemMotionOffsetY
                  );
                  if (this.comboCount >= 5) {
                     this.renderOverlayParticles(
                        guiGraphics,
                        LARGE_SPARKS_PARTICLE,
                        largeSparksTexturePath,
                        elapsed,
                        alpha,
                        emblemWidth,
                        emblemHeight,
                        effectiveBrightness,
                        effectiveContrast,
                        defaultParticleColor,
                        emblemMotionOffsetY
                     );
                  }

                  if (this.headshotTrigger) {
                     this.renderOverlayParticles(
                        guiGraphics,
                        X_SPARKS_PARTICLE,
                        xSparksTexturePath,
                        elapsed,
                        alpha,
                        emblemWidth,
                        emblemHeight,
                        effectiveBrightness,
                        effectiveContrast,
                        xSparksDefaultColor,
                        emblemMotionOffsetY
                     );
                  }
               }

               guiGraphics.pose().popPose();
            } finally {
               RenderSystem.defaultBlendFunc();
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   private void loadConfig(JsonObject config) {
      this.currentConfig = config;
      this.configScale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
      this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsFloat() : 0.0F;
      this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsFloat() : 80.0F;
      this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
      this.configBarXOffset = config.has("bar_x_offset") ? config.get("bar_x_offset").getAsFloat() : 0.0F;
      this.configBarYOffset = config.has("bar_y_offset") ? config.get("bar_y_offset").getAsFloat() : 0.0F;
      this.configBarRadiusOffset = config.has("bar_radius_offset") ? config.get("bar_radius_offset").getAsFloat() : 0.0F;
      this.configIconScale = config.has("icon_scale") ? config.get("icon_scale").getAsFloat() : 1.0F;
      this.configHeadshotAnimInitialScale = Mth.clamp(
         config.has("headshot_anim_initial_scale") ? config.get("headshot_anim_initial_scale").getAsFloat() : 1.8F, 1.0F, 5.0F
      );
      this.configHeadshotAnimDuration = Math.max(0.0F, config.has("headshot_anim_duration") ? config.get("headshot_anim_duration").getAsFloat() : 0.25F);
      this.configBarEntryInitialScale = Mth.clamp(
         config.has("bar_entry_initial_scale") ? config.get("bar_entry_initial_scale").getAsFloat() : 1.6F, 1.0F, 5.0F
      );
      this.configBarEntryDuration = Math.max(0.0F, config.has("bar_entry_duration") ? config.get("bar_entry_duration").getAsFloat() : 0.18F);
      this.configBrightness = config.has("brightness") ? config.get("brightness").getAsFloat() : 1.0F;
      this.configIconGlowEnabled = config.has("enable_icon_glow") && config.get("enable_icon_glow").getAsBoolean();
      this.configIconGlowColor = this.resolveConfiguredColor("color_icon_glow", 16777215);
      this.configIconGlowIntensity = Mth.clamp(config.has("icon_glow_intensity") ? config.get("icon_glow_intensity").getAsFloat() : 0.45F, 0.0F, 1.0F);
      this.configIconGlowSize = Math.max(0.0F, config.has("icon_glow_size") ? config.get("icon_glow_size").getAsFloat() : 4.0F);
      this.configHaloRingEnabled = config.has("enable_halo_ring") && config.get("enable_halo_ring").getAsBoolean();
      this.configHaloRingRadius = Math.max(4.0F, config.has("halo_ring_radius") ? config.get("halo_ring_radius").getAsFloat() : 30.0F);
      this.configHaloRingWidth = Math.max(0.1F, config.has("halo_ring_width") ? config.get("halo_ring_width").getAsFloat() : 1.5F);
      this.configHaloRingColor = this.resolveConfiguredColor("color_halo_ring", 16777215);
      this.configBladeEffect = !config.has("enable_blade_effect") || config.get("enable_blade_effect").getAsBoolean();
      this.configBladeRotationEffect = !config.has("enable_blade_rotation_effect") || config.get("enable_blade_rotation_effect").getAsBoolean();
      this.configBladeDecelerationWindowSec = Mth.clamp(
         config.has("blade_deceleration_window") ? config.get("blade_deceleration_window").getAsFloat() : 1.5F, 0.2F, 5.0F
      );
      this.configMathParticleEffect = config.has("enable_math_particle_effect") && config.get("enable_math_particle_effect").getAsBoolean();
      this.configMathParticleDensity = Mth.clamp(config.has("math_particle_density") ? config.get("math_particle_density").getAsFloat() : 1.0F, 0.2F, 4.0F);
      this.configMathParticleSpread = Mth.clamp(config.has("math_particle_spread") ? config.get("math_particle_spread").getAsFloat() : 1.0F, 0.25F, 3.0F);
      this.configMathParticleSize = Mth.clamp(config.has("math_particle_size") ? config.get("math_particle_size").getAsFloat() : 1.0F, 0.25F, 3.0F);
   }

   private long resolveDisplayDuration() {
      long serverDuration = ComboIconRenderer.getServerComboWindowMs();
      return serverDuration > 0L ? serverDuration : 2600L;
   }

   private long resolveDurationMillis(String key, float fallbackSeconds) {
      float seconds = this.getFloatConfig(key, fallbackSeconds);
      return Math.max(0L, (long)Math.round(seconds * 1000.0F));
   }

   private float resolveAlpha(long elapsed, long displayDuration) {
      float alpha = 1.0F;
      if (this.comboCount >= 5 && elapsed < 300L) {
         float progress = (float)elapsed / 300.0F;
         alpha *= easeOutQuad(progress);
      }

      if (displayDuration > 220L && elapsed > displayDuration - 220L) {
         float progress = (float)(elapsed - (displayDuration - 220L)) / 220.0F;
         return alpha * (1.0F - Mth.clamp(progress, 0.0F, 1.0F));
      } else {
         return alpha;
      }
   }

   private String resolveSelectedTexture(String presetId, String textureKey) {
      return ElementTextureDefinition.getSelectedTextureFileName(presetId, "kill_icon/valorant", textureKey, this.currentConfig);
   }

   private float resolveIconOffsetY(long elapsed) {
      float startOffset = this.getFloatConfig("icon_entry_offset_y", -16.0F);
      long durationMs = this.resolveDurationMillis("icon_entry_duration", 0.1F);
      if (durationMs > 0L && !(Math.abs(startOffset) < 0.01F) && elapsed < durationMs) {
         float normalizedProgress = Mth.clamp((float)elapsed / (float)durationMs, 0.0F, 1.0F);
         return Mth.lerp(easeOutCubic(normalizedProgress), startOffset, 0.0F);
      } else {
         return 0.0F;
      }
   }

   private float resolveBarTravel(long elapsed) {
      if (elapsed < 20L || elapsed >= 620L) {
         return 0.0F;
      } else if (elapsed <= 180L) {
         float progress = (float)(elapsed - 20L) / 160.0F;
         return Mth.lerp(easeOutSoft(progress), 0.0F, 9.0F);
      } else if (elapsed <= 270L) {
         return 9.0F;
      } else {
         float progress = (float)(elapsed - 270L) / 350.0F;
         return Mth.lerp(easeInQuart(progress), 9.0F, 0.0F);
      }
   }

   private float resolveBarRotation(long elapsed) {
      if (this.comboCount > 1 && elapsed > 750L) {
         long rotationDuration = this.comboCount >= 5 ? 1000L : 700L;
         float targetRotation = this.comboCount >= 5 ? 360.0F : 180.0F;
         float progress = (float)(elapsed - 750L) / (float)rotationDuration;
         progress = Mth.clamp(progress, 0.0F, 1.0F);
         return targetRotation * easeOutCubic(progress) * this.spinDirection;
      } else {
         return 0.0F;
      }
   }

   private ValorantIconRenderer.FlashState resolveFlashState(long elapsed) {
      int flashCount = Mth.clamp(this.getIntConfig("icon_flash_count", 4), 0, 12);
      if (flashCount <= 0) {
         return ValorantIconRenderer.FlashState.none();
      } else {
         long holdDurationMs = this.resolveDurationMillis("icon_flash_hold_duration", 0.08F);
         if (holdDurationMs <= 0L) {
            return ValorantIconRenderer.FlashState.none();
         } else {
            long gapDurationMs = Math.max(20L, (long)Math.round((float)holdDurationMs * 0.75F));
            long cycleDurationMs = holdDurationMs + gapDurationMs;
            long totalDurationMs = holdDurationMs + cycleDurationMs * ((long)flashCount - 1L);
            if (elapsed >= totalDurationMs) {
               return ValorantIconRenderer.FlashState.none();
            } else {
               long cycleElapsed = elapsed % cycleDurationMs;
               if (cycleElapsed >= holdDurationMs) {
                  return ValorantIconRenderer.FlashState.none();
               } else {
                  float holdMs = (float)holdDurationMs;
                  float localElapsed = (float)cycleElapsed;
                  float edgeDurationMs = Math.min(35.0F, holdMs * 0.25F);
                  float pulseAlpha = 1.0F;
                  if (edgeDurationMs > 1.0F) {
                     if (localElapsed < edgeDurationMs) {
                        pulseAlpha = localElapsed / edgeDurationMs;
                     } else if (holdMs - localElapsed < edgeDurationMs) {
                        pulseAlpha = (holdMs - localElapsed) / edgeDurationMs;
                     }
                  }

                  int color = this.resolveConfiguredColor("color_icon_flash", 16722486);
                  return new ValorantIconRenderer.FlashState(color, Mth.clamp(pulseAlpha, 0.0F, 1.0F) * 0.9F);
               }
            }
         }
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

   private float resolveTextureScale(String textureKey) {
      return this.resolveTextureFloat(textureKey, "texture_scale", 1.0F, 0.01F);
   }

   private float resolveTextureFinalOpacity(String textureKey) {
      return Mth.clamp(this.resolveTextureFloat(textureKey, "texture_final_opacity", 1.0F, 0.0F), 0.0F, 1.0F);
   }

   private float resolveTextureOffset(String textureKey, String suffixKey) {
      return this.resolveTextureFloat(textureKey, suffixKey, 0.0F, -Float.MAX_VALUE);
   }

   private float resolveTextureFloat(String textureKey, String suffixKey, float fallback, float minimum) {
      if (this.currentConfig != null && textureKey != null) {
         String key = "anim_" + textureKey + "_" + suffixKey;
         if (!this.currentConfig.has(key)) {
            return fallback;
         } else {
            float value = this.currentConfig.get(key).getAsFloat();
            return value < minimum ? fallback : value;
         }
      } else {
         return fallback;
      }
   }

   private void generateParticleState() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      this.smallParticles = this.createSmallParticles(random);
   }

   private ValorantIconRenderer.SmallParticle[] createSmallParticles(ThreadLocalRandom random) {
      int baseParticleCount = this.comboCount <= 1 ? 28 : 22 + this.comboCount * 3;
      int particleCount = Math.max(1, Math.round((float)baseParticleCount * this.configMathParticleDensity));
      float visibilityScale = 0.95F;
      float directionOffset = 0.0F;
      float spreadScale = this.configMathParticleSpread;
      float sizeScale = this.configMathParticleSize;
      ValorantIconRenderer.SmallParticle[] particles = new ValorantIconRenderer.SmallParticle[particleCount];

      for (int index = 0; index < particleCount; index++) {
         boolean rightSide = (index & 1) == 0;
         float angleBase = rightSide ? 90.0F : -90.0F;
         float sideSpread = (this.comboCount <= 1 ? 44.0F : 56.0F) * spreadScale;
         float angle = angleBase + directionOffset + random.nextFloat(-sideSpread, sideSpread);
         if (index % 4 == 0) {
            angle += rightSide ? random.nextFloat(-24.0F * spreadScale, -8.0F * spreadScale) : random.nextFloat(8.0F * spreadScale, 24.0F * spreadScale);
         }

         float startRadius = random.nextFloat(8.0F, 16.0F) * spreadScale;
         float endRadius = (this.comboCount <= 1 ? random.nextFloat(54.0F, 82.0F) : random.nextFloat(62.0F, 92.0F)) * spreadScale;
         float width = random.nextFloat(7.0F, 13.5F) * visibilityScale * sizeScale;
         float height = random.nextFloat(1.8F, 4.0F) * visibilityScale * sizeScale;
         float tilt = angle + random.nextFloat(-28.0F, 28.0F);
         float alphaScale = random.nextFloat(0.95F, 1.45F) * visibilityScale;
         float jagScale = random.nextFloat(0.0F, 1.0F);
         float bendScale = random.nextFloat(0.0F, 1.0F);
         float lateralBoost = random.nextFloat(1.35F, 1.75F);
         long delayMs = random.nextLong(0L, 30L);
         float gravityPx = random.nextFloat(50.0F, 82.0F);
         particles[index] = new ValorantIconRenderer.SmallParticle(
            angle, tilt, startRadius, endRadius, width, height, alphaScale, jagScale, bendScale, lateralBoost, gravityPx, delayMs
         );
      }

      return particles;
   }

   private void renderSmallParticles(GuiGraphics guiGraphics, long elapsed, float alpha, int particleRgb) {
      if (!(alpha <= 0.0F) && elapsed < 620L) {
         for (ValorantIconRenderer.SmallParticle particle : this.smallParticles) {
            long particleElapsed = elapsed - particle.delayMs();
            if (particleElapsed >= 0L && particleElapsed <= 620L) {
               float progress = Mth.clamp((float)particleElapsed / 620.0F, 0.0F, 1.0F);
               float move = easeOutCubic(progress);
               float distance = Mth.lerp(move, particle.startRadius(), particle.endRadius());
               float angleRad = (float)Math.toRadians((double)particle.angleDeg());
               float horizontalFactor = Mth.sin(angleRad) * distance * particle.lateralBoost();
               float verticalFactor = -Mth.cos(angleRad) * distance * (2.15F - particle.lateralBoost());
               float y = verticalFactor + particle.gravityPx() * easeInQuad(progress);
               float particleAlpha = alpha * particle.alphaScale() * (1.0F - easeInQuart(progress));
               int color = applyAlpha(particleRgb, particleAlpha);
               if (color >>> 24 > 0) {
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(horizontalFactor, y, 0.0F);
                  guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(particle.tiltDeg()));
                  this.drawSmallShard(guiGraphics, particle.width(), particle.height(), particle.jagScale(), particle.bendScale(), color);
                  guiGraphics.pose().popPose();
               }
            }
         }
      }
   }

   private void drawSmallShard(GuiGraphics guiGraphics, float length, float thickness, float jagScale, float bendScale, int color) {
      float halfLength = Math.max(1.5F, length * 0.5F);
      float halfThickness = Math.max(0.8F, thickness * 0.5F);
      float topFlare = halfThickness * (1.25F + jagScale * 0.45F);
      float bottomFlare = halfThickness * (1.05F + (1.0F - jagScale) * 0.35F);
      float tailShoulderX = -halfLength * (0.22F - bendScale * 0.08F);
      float upperKinkX = halfLength * (0.34F + jagScale * 0.12F);
      float lowerKinkX = halfLength * (0.18F + bendScale * 0.08F);
      float tailLowerX = -halfLength * (0.58F - bendScale * 0.1F);
      float skewY = (bendScale - 0.5F) * thickness * 0.55F;
      float[] xs = new float[]{-halfLength, tailShoulderX, upperKinkX, halfLength, lowerKinkX, tailLowerX};
      float[] ys = new float[]{
         halfThickness * 0.38F + skewY * 0.18F,
         topFlare + skewY,
         halfThickness * 0.22F + skewY * 0.3F,
         0.0F,
         -bottomFlare + skewY * 0.08F,
         -halfThickness * 0.62F + skewY * 0.45F
      };
      this.drawConvexPolygon(guiGraphics, xs, ys, color & 16777215, (float)(color >>> 24 & 0xFF) / 255.0F);
   }

   private void drawConvexPolygon(GuiGraphics guiGraphics, float[] xs, float[] ys, int rgb, float alpha) {
      if (xs.length >= 3 && ys.length >= 3 && !(alpha <= 0.0F)) {
         int a = Mth.clamp((int)(alpha * 255.0F), 0, 255);
         if (a > 0) {
            int red = rgb >> 16 & 0xFF;
            int green = rgb >> 8 & 0xFF;
            int blue = rgb & 0xFF;
            Matrix4f matrix = guiGraphics.pose().last().pose();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder = tesselator.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (int index = 1; index < xs.length - 1; index++) {
               builder.addVertex(matrix, xs[0], ys[0], 0.0F).setColor(red, green, blue, a);
               builder.addVertex(matrix, xs[index], ys[index], 0.0F).setColor(red, green, blue, a);
               builder.addVertex(matrix, xs[index + 1], ys[index + 1], 0.0F).setColor(red, green, blue, a);
            }

            BufferUploader.drawWithShader(builder.buildOrThrow());
         }
      }
   }

   private int resolveAccentColor() {
      if (this.currentConfig != null) {
         if (this.currentConfig.has("color_accent")) {
            return this.resolveConfiguredColor("color_accent", 14831708);
         }

         if (this.currentConfig.has("color_gaia_accent")) {
            return this.resolveConfiguredColor("color_gaia_accent", 14831708);
         }
      }

      return 14831708;
   }

   private int resolveDefaultParticleColor(boolean accentTintEnabled, int accentColor, boolean gaiaSkin) {
      return accentTintEnabled ? accentColor : (gaiaSkin ? 14831708 : 16765240);
   }

   private boolean isAccentTintEnabled() {
      if (this.currentConfig != null && this.currentConfig.has("enable_accent_tint")) {
         return this.currentConfig.get("enable_accent_tint").getAsBoolean();
      } else if (this.currentConfig != null && this.currentConfig.has("color_gaia_accent")) {
         String legacyColor = this.currentConfig.get("color_gaia_accent").getAsString();
         return legacyColor != null && !legacyColor.equalsIgnoreCase(String.format("#%06X", 14831708));
      } else {
         return false;
      }
   }

   private int resolveConfiguredColor(String key, int fallback) {
      if (this.currentConfig != null && key != null && this.currentConfig.has(key)) {
         String raw = this.currentConfig.get(key).getAsString();
         if (raw != null && raw.matches("^#[0-9A-Fa-f]{6}$")) {
            try {
               return Integer.parseInt(raw.substring(1), 16);
            } catch (NumberFormatException var5) {
            }
         }

         return fallback;
      } else {
         return fallback;
      }
   }

   private void renderGradientHaloRing(GuiGraphics guiGraphics, long elapsed, float alpha) {
      if (this.configHaloRingEnabled && !(alpha <= 0.001F) && !(this.configHaloRingWidth <= 0.0F) && !(this.configHaloRingRadius <= 0.0F)) {
         float focusAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/valorant", "frame");
         if (!(focusAlpha <= 0.001F)) {
            float centerX = this.resolveTextureOffset("frame", "texture_x_offset");
            float centerY = this.resolveTextureOffset("frame", "texture_y_offset");
            float outerRadius = this.configHaloRingRadius + this.configHaloRingWidth * 0.5F;
            float innerRadius = Math.max(0.0F, this.configHaloRingRadius - this.configHaloRingWidth * 0.5F);
            float minY = centerY - outerRadius;
            float maxY = centerY + outerRadius;
            float yRange = Math.max(0.001F, maxY - minY);
            int red = this.configHaloRingColor >> 16 & 0xFF;
            int green = this.configHaloRingColor >> 8 & 0xFF;
            int blue = this.configHaloRingColor & 0xFF;
            float angleOffset = (float)(elapsed % 2200L) / 2200.0F * (float) (Math.PI * 2);
            Matrix4f matrix = guiGraphics.pose().last().pose();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            builder = tesselator.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int i = 0; i <= 72; i++) {
               float angle = (float) (Math.PI * 2) * (float)i / 72.0F + angleOffset;
               float cos = Mth.cos(angle);
               float sin = Mth.sin(angle);
               float xo = centerX + cos * outerRadius;
               float yo = centerY + sin * outerRadius;
               float xi = centerX + cos * innerRadius;
               float yi = centerY + sin * innerRadius;
               float outerAlphaFactor = Mth.clamp((maxY - yo) / yRange, 0.0F, 1.0F);
               float innerAlphaFactor = Mth.clamp((maxY - yi) / yRange, 0.0F, 1.0F);
               int ao = Mth.clamp((int)(focusAlpha * outerAlphaFactor * 255.0F), 0, 255);
               int ai = Mth.clamp((int)(focusAlpha * innerAlphaFactor * 255.0F), 0, 255);
               builder.addVertex(matrix, xo, yo, 0.0F).setColor(red, green, blue, ao);
               builder.addVertex(matrix, xi, yi, 0.0F).setColor(red, green, blue, ai);
            }

            BufferUploader.drawWithShader(builder.buildOrThrow());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }
   }

   private boolean getBooleanConfig(String key, boolean fallback) {
      return this.currentConfig != null && key != null && this.currentConfig.has(key) ? this.currentConfig.get(key).getAsBoolean() : fallback;
   }

   private float getFloatConfig(String key, float fallback) {
      return this.currentConfig != null && key != null && this.currentConfig.has(key) ? this.currentConfig.get(key).getAsFloat() : fallback;
   }

   private int getIntConfig(String key, int fallback) {
      return this.currentConfig != null && key != null && this.currentConfig.has(key) ? this.currentConfig.get(key).getAsInt() : fallback;
   }

   private int resolveLayerParticleColor(String keyPrefix, int fallbackColor) {
      return this.resolveConfiguredColor("color_" + keyPrefix, fallbackColor);
   }

   private void renderBaseParticles(GuiGraphics guiGraphics, String texturePath, long elapsed, float alpha, float brightness, float contrast, int defaultColor) {
      if (this.getBooleanConfig("enable_base_particle", true)) {
         if (!(alpha <= 0.0F) && texturePath != null && !texturePath.isEmpty()) {
            IconTextureAnimationManager.TextureFrame frame = IconTextureAnimationManager.getTextureFrame(
               ConfigManager.getCurrentPresetId(), "kill_icon/valorant", "base_particle", texturePath, this.startTime, this.currentConfig
            );
            float particleSize = 112.0F * this.resolveTextureScale("base_particle");
            float layerAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/valorant", "base_particle");
            if (!(layerAlpha <= 0.001F)) {
               int particleColor = this.resolveLayerParticleColor("base_particle", defaultColor);
               ResourceLocation texture = this.resolveParticleTexture(texturePath, brightness, contrast);
               this.renderMirroredParticleBurst(
                  guiGraphics,
                  texture,
                  frame,
                  particleSize,
                  particleSize,
                  this.resolveTextureOffset("base_particle", "texture_x_offset"),
                  0.0F,
                  this.resolveTextureOffset("base_particle", "texture_y_offset"),
                  layerAlpha,
                  particleColor,
                  false,
                  0.0F,
                  0.8046875F
               );
            }
         }
      }
   }

   private void renderOverlayParticles(
      GuiGraphics guiGraphics,
      ValorantIconRenderer.ParticleOverlaySpec spec,
      String texturePath,
      long elapsed,
      float alpha,
      float iconWidth,
      float iconHeight,
      float brightness,
      float contrast,
      int defaultColor,
      float extraYOffset
   ) {
      String keyPrefix = spec.configKeyPrefix();
      if (this.getBooleanConfig("enable_" + keyPrefix, true)) {
         if (!(alpha <= 0.0F) && texturePath != null && !texturePath.isEmpty()) {
            float sourceScale = spec.defaultSourceScale() * this.resolveTextureScale(keyPrefix);
            IconTextureAnimationManager.TextureFrame frame = IconTextureAnimationManager.getTextureFrame(
               ConfigManager.getCurrentPresetId(), "kill_icon/valorant", keyPrefix, texturePath, this.startTime, this.currentConfig
            );
            float drawWidth = iconWidth * sourceScale * ((float)frame.width / 1729.0F);
            float drawHeight = iconHeight * sourceScale * ((float)frame.height / 1729.0F);
            if (!(drawWidth <= 0.0F) && !(drawHeight <= 0.0F)) {
               ResourceLocation texture = this.resolveParticleTexture(texturePath, brightness, contrast);
               float layerAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/valorant", keyPrefix);
               if (!(layerAlpha <= 0.001F)) {
                  int particleColor = this.resolveLayerParticleColor(keyPrefix, defaultColor);
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose()
                     .translate(
                        spec.offsetXPx() + this.resolveTextureOffset(keyPrefix, "texture_x_offset"),
                        spec.offsetYPx() + this.resolveTextureOffset(keyPrefix, "texture_y_offset") + extraYOffset,
                        0.0F
                     );
                  this.drawAnchoredResourceTextureFrame(
                     guiGraphics,
                     texture,
                     frame,
                     drawWidth,
                     drawHeight,
                     spec.anchorXRatio(),
                     spec.anchorYRatio(),
                     layerAlpha,
                     particleColor,
                     spec.additive(),
                     false
                  );
                  guiGraphics.pose().popPose();
               }
            }
         }
      }
   }

   private void renderMirroredParticleBurst(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      IconTextureAnimationManager.TextureFrame frame,
      float drawWidth,
      float drawHeight,
      float centerOffsetX,
      float mirrorOffsetX,
      float centerOffsetY,
      float alpha,
      int rgb,
      boolean additive,
      float anchorXRatio,
      float anchorYRatio
   ) {
      if (!(alpha <= 0.0F) && texture != null) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(centerOffsetX + mirrorOffsetX, centerOffsetY, 0.0F);
         this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, anchorXRatio, anchorYRatio, alpha, rgb, additive, false);
         guiGraphics.pose().popPose();
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(centerOffsetX - mirrorOffsetX, centerOffsetY, 0.0F);
         this.drawAnchoredResourceTextureFrame(
            guiGraphics, texture, frame, drawWidth, drawHeight, 1.0F - anchorXRatio, anchorYRatio, alpha, rgb, additive, true
         );
         guiGraphics.pose().popPose();
      }
   }

   private ResourceLocation resolveParticleTexture(String texturePath, float brightness, float contrast) {
      if (!this.needsTextureProcessing(texturePath, false, brightness, contrast)) {
         return ModTextures.get(texturePath);
      } else {
         ResourceLocation processedTexture = IconGlowProcessor.getOrCreateProcessedTexture(
            ConfigManager.getCurrentPresetId(), texturePath, false, 16777215, brightness, contrast
         );
         return processedTexture != null ? processedTexture : ModTextures.get(texturePath);
      }
   }

   private void drawCenteredTexture(
      GuiGraphics guiGraphics, String textureKey, String texturePath, float drawWidth, float drawHeight, float alpha, int rgb, boolean additive
   ) {
      if (texturePath != null) {
         ResourceLocation texture = ModTextures.get(texturePath);
         if (texture != null) {
            IconTextureAnimationManager.TextureFrame frame = this.resolveAnimatedFrame(textureKey, texturePath);
            this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, 0.5F, 0.5F, alpha, rgb, additive, false);
         }
      }
   }

   private void drawColorizableTexture(
      GuiGraphics guiGraphics,
      String textureKey,
      String texturePath,
      float drawWidth,
      float drawHeight,
      float alpha,
      int rgb,
      boolean additive,
      boolean accentTintEnabled,
      int accentColor,
      float brightness,
      float contrast
   ) {
      alpha *= PreviewTextureFocusContext.alphaMultiplier("kill_icon/valorant", textureKey);
      if (!(alpha <= 0.001F)) {
         float finalOpacity = this.resolveTextureFinalOpacity(textureKey);
         if (!(finalOpacity <= 0.001F)) {
            alpha *= finalOpacity;
            if (!(alpha <= 0.001F)) {
               float offsetX = this.resolveTextureOffset(textureKey, "texture_x_offset");
               float offsetY = this.resolveTextureOffset(textureKey, "texture_y_offset");
               boolean translated = Math.abs(offsetX) > 0.001F || Math.abs(offsetY) > 0.001F;
               if (translated) {
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate(offsetX, offsetY, 0.0F);
               }

               ResourceLocation processedTexture = null;
               boolean useProcessedTexture = this.needsTextureProcessing(texturePath, accentTintEnabled, brightness, contrast);
               if (useProcessedTexture) {
                  processedTexture = IconGlowProcessor.getOrCreateProcessedTexture(
                     ConfigManager.getCurrentPresetId(), texturePath, accentTintEnabled, accentColor, brightness, contrast
                  );
               }

               if (this.configIconGlowEnabled) {
                  if (processedTexture != null) {
                     this.drawGlowResourceTexture(guiGraphics, processedTexture, textureKey, texturePath, drawWidth, drawHeight, alpha);
                  } else {
                     this.drawGlowTexture(
                        guiGraphics, textureKey, texturePath, drawWidth, drawHeight, alpha, accentTintEnabled, accentColor, brightness, contrast
                     );
                  }
               }

               if (useProcessedTexture && processedTexture != null) {
                  this.drawCenteredResourceTexture(guiGraphics, processedTexture, textureKey, texturePath, drawWidth, drawHeight, alpha, rgb, additive);
                  if (translated) {
                     guiGraphics.pose().popPose();
                  }
               } else {
                  this.drawCenteredTexture(guiGraphics, textureKey, texturePath, drawWidth, drawHeight, alpha, rgb, additive);
                  if (translated) {
                     guiGraphics.pose().popPose();
                  }
               }
            }
         }
      }
   }

   private void drawCenteredResourceTexture(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      String textureKey,
      String texturePath,
      float drawWidth,
      float drawHeight,
      float alpha,
      int rgb,
      boolean additive
   ) {
      if (texture != null) {
         IconTextureAnimationManager.TextureFrame frame = this.resolveAnimatedFrame(textureKey, texturePath);
         this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, 0.5F, 0.5F, alpha, rgb, additive, false);
      }
   }

   private void drawGlowTexture(
      GuiGraphics guiGraphics,
      String textureKey,
      String texturePath,
      float drawWidth,
      float drawHeight,
      float alpha,
      boolean accentTintEnabled,
      int accentColor,
      float brightness,
      float contrast
   ) {
      if (texturePath != null && !texturePath.isEmpty()) {
         boolean useProcessedTexture = this.needsTextureProcessing(texturePath, accentTintEnabled, brightness, contrast);
         if (useProcessedTexture) {
            ResourceLocation processedTexture = IconGlowProcessor.getOrCreateProcessedTexture(
               ConfigManager.getCurrentPresetId(), texturePath, accentTintEnabled, accentColor, brightness, contrast
            );
            if (processedTexture != null) {
               this.drawGlowResourceTexture(guiGraphics, processedTexture, textureKey, texturePath, drawWidth, drawHeight, alpha);
               return;
            }
         }

         ResourceLocation texture = ModTextures.get(texturePath);
         if (texture != null) {
            this.drawGlowResourceTexture(guiGraphics, texture, textureKey, texturePath, drawWidth, drawHeight, alpha);
         }
      }
   }

   private void drawGlowResourceTexture(
      GuiGraphics guiGraphics, ResourceLocation texture, String textureKey, String texturePath, float drawWidth, float drawHeight, float alpha
   ) {
      if (texture != null
         && this.configIconGlowEnabled
         && !(this.configIconGlowSize <= 0.01F)
         && !(this.configIconGlowIntensity <= 0.001F)
         && !(alpha <= 0.001F)) {
         float outerSpread = Math.max(0.5F, this.configIconGlowSize);
         float innerSpread = outerSpread * 0.55F;
         float glowAlpha = Mth.clamp(alpha * this.configIconGlowIntensity, 0.0F, 1.0F);
         int color = this.configIconGlowColor;
         IconTextureAnimationManager.TextureFrame frame = this.resolveAnimatedFrame(textureKey, texturePath);

         for (float[] offset : GLOW_OFFSETS) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(offset[0] * outerSpread, offset[1] * outerSpread, 0.0F);
            this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, 0.5F, 0.5F, glowAlpha * 0.16F, color, true, false);
            guiGraphics.pose().popPose();
         }

         for (float[] offset : GLOW_OFFSETS) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(offset[0] * innerSpread, offset[1] * innerSpread, 0.0F);
            this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, 0.5F, 0.5F, glowAlpha * 0.11F, color, true, false);
            guiGraphics.pose().popPose();
         }

         this.drawAnchoredResourceTextureFrame(guiGraphics, texture, frame, drawWidth, drawHeight, 0.5F, 0.5F, glowAlpha * 0.09F, color, true, false);
      }
   }

   private IconTextureAnimationManager.TextureFrame resolveAnimatedFrame(String textureKey, String texturePath) {
      return texturePath != null && !texturePath.isEmpty() && textureKey != null && !textureKey.isEmpty()
         ? IconTextureAnimationManager.getTextureFrame(
            ConfigManager.getCurrentPresetId(), "kill_icon/valorant", textureKey, texturePath, this.startTime, this.currentConfig
         )
         : new IconTextureAnimationManager.TextureFrame(0, 0, 1, 1, 1, 1);
   }

   private void drawAnchoredResourceTextureFrame(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      IconTextureAnimationManager.TextureFrame frame,
      float drawWidth,
      float drawHeight,
      float anchorXRatio,
      float anchorYRatio,
      float alpha,
      int rgb,
      boolean additive,
      boolean flipX
   ) {
      if (texture != null && frame != null) {
         if (additive) {
            RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         } else {
            RenderSystem.defaultBlendFunc();
         }

         float red = (float)(rgb >> 16 & 0xFF) / 255.0F;
         float green = (float)(rgb >> 8 & 0xFF) / 255.0F;
         float blue = (float)(rgb & 0xFF) / 255.0F;
         float x0 = -drawWidth * anchorXRatio;
         float y0 = -drawHeight * anchorYRatio;
         float x1 = x0 + drawWidth;
         float y1 = y0 + drawHeight;
         float u0 = (float)frame.u / (float)frame.totalWidth;
         float u1 = (float)(frame.u + frame.width) / (float)frame.totalWidth;
         float v0 = (float)frame.v / (float)frame.totalHeight;
         float v1 = (float)(frame.v + frame.height) / (float)frame.totalHeight;
         if (flipX) {
            float swapped = u0;
            u0 = u1;
            u1 = swapped;
         }

         Matrix4f matrix = guiGraphics.pose().last().pose();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder;
         RenderSystem.enableBlend();
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.setShaderTexture(0, texture);
         RenderSystem.setShaderColor(red, green, blue, alpha);
         builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(matrix, x0, y1, 0.0F).setUv(u0, v1);
         builder.addVertex(matrix, x1, y1, 0.0F).setUv(u1, v1);
         builder.addVertex(matrix, x1, y0, 0.0F).setUv(u1, v0);
         builder.addVertex(matrix, x0, y0, 0.0F).setUv(u0, v0);
         BufferUploader.drawWithShader(builder.buildOrThrow());
      }
   }

   private void renderBarRing(
      GuiGraphics guiGraphics,
      String barTexturePath,
      float barWidth,
      float barHeight,
      float barTravel,
      float alpha,
      float barRotation,
      float barRadiusOffset,
      float barCenterXOffset,
      float barCenterYOffset,
      float[] layoutAngles,
      boolean accentTintEnabled,
      int accentColor,
      float brightness,
      float contrast,
      long elapsed
   ) {
      float distance = 36.0F + barRadiusOffset + barTravel;
      float entryScale = this.resolveBarEntryScale(elapsed);

      for (float layoutAngle : layoutAngles) {
         guiGraphics.pose().pushPose();
         if (barCenterXOffset != 0.0F || barCenterYOffset != 0.0F) {
            guiGraphics.pose().translate(barCenterXOffset, barCenterYOffset, 0.0F);
         }

         guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(layoutAngle + barRotation));
         guiGraphics.pose().translate(0.0F, -distance, 0.0F);
         if (Math.abs(entryScale - 1.0F) > 0.001F) {
            guiGraphics.pose().scale(entryScale, entryScale, 1.0F);
         }

         this.drawColorizableTexture(
            guiGraphics, "bar", barTexturePath, barWidth, barHeight, alpha, 16777215, false, accentTintEnabled, accentColor, brightness, contrast
         );
         RenderSystem.defaultBlendFunc();
         guiGraphics.pose().popPose();
      }
   }

   private float resolveBarEntryScale(long elapsed) {
      long durationMs = (long)Math.round(this.configBarEntryDuration * 1000.0F);
      if (durationMs <= 0L || this.configBarEntryInitialScale <= 1.0F) {
         return 1.0F;
      } else if (elapsed >= durationMs) {
         return 1.0F;
      } else {
         float t = Mth.clamp((float)elapsed / (float)durationMs, 0.0F, 1.0F);
         return Mth.lerp(easeOutCubic(t), this.configBarEntryInitialScale, 1.0F);
      }
   }

   private boolean isAccentTintableTexture(String texturePath) {
      return texturePath != null && !texturePath.isEmpty();
   }

   private boolean needsTextureProcessing(String texturePath, boolean accentTintEnabled, float brightness, float contrast) {
      if (texturePath == null || texturePath.isEmpty()) {
         return false;
      } else {
         return accentTintEnabled && this.isAccentTintableTexture(texturePath)
            ? true
            : Math.abs(brightness - 1.0F) > 0.001F || Math.abs(contrast - 1.0F) > 0.001F;
      }
   }

   private static float easeOutCubic(float value) {
      float clamped = Mth.clamp(value, 0.0F, 1.0F);
      float inverted = 1.0F - clamped;
      return 1.0F - inverted * inverted * inverted;
   }

   private static float easeInQuart(float value) {
      float clamped = Mth.clamp(value, 0.0F, 1.0F);
      return clamped * clamped * clamped * clamped;
   }

   private static float easeInQuad(float value) {
      float clamped = Mth.clamp(value, 0.0F, 1.0F);
      return clamped * clamped;
   }

   private static float easeOutQuad(float value) {
      float clamped = Mth.clamp(value, 0.0F, 1.0F);
      float inverted = 1.0F - clamped;
      return 1.0F - inverted * inverted;
   }

   private static float easeOutSoft(float value) {
      float quad = easeOutQuad(value);
      float cubic = easeOutCubic(value);
      return Mth.lerp(0.2F, quad, cubic);
   }

   private static int applyAlpha(int rgb, float alpha) {
      int a = Mth.clamp((int)(alpha * 255.0F), 0, 255);
      return a << 24 | rgb;
   }

   private float resolveFrameBladeRotation(String styleId, long elapsed) {
      if (!this.configBladeRotationEffect) {
         return 0.0F;
      } else {
         float baseDegPerSecond = 1350.0F;
         if (styleId != null && styleId.contains("gaia")) {
            baseDegPerSecond = 1100.0F;
         }

         float windowSec = Math.max(0.2F, this.configBladeDecelerationWindowSec);
         float t = Mth.clamp((float)elapsed / (windowSec * 1000.0F), 0.0F, 1.0F);
         float totalDegrees = baseDegPerSecond * windowSec * (1.0F - (float)Math.pow((double)(1.0F - t), 4.0)) * 0.25F;
         return -(totalDegrees * this.spinDirection);
      }
   }

   private float resolveEffectiveBrightness(boolean gaiaSkin) {
      return gaiaSkin ? this.configBrightness * 1.3F : this.configBrightness;
   }

   private float resolveEffectiveContrast(boolean gaiaSkin) {
      return gaiaSkin ? 1.1F : 1.0F;
   }

   private String resolveStyleId() {
      return ValorantStyleCatalog.resolveStyleId(ConfigManager.getCurrentPresetId(), this.currentConfig);
   }

   private String resolveBladeTexturePath(String styleId, String selectedBladeTexturePath) {
      if (selectedBladeTexturePath != null && !selectedBladeTexturePath.isBlank()) {
         return selectedBladeTexturePath;
      } else {
         String styleBlade = ValorantStyleCatalog.getFrameBladeTextureFileName(styleId);
         return styleBlade != null && !styleBlade.isBlank()
            ? styleBlade
            : ValorantStyleCatalog.getOfficialTextureFileNameForStyle("bubblegum_deathwish", "blade");
      }
   }

   private boolean isGaiaSkin(String styleId, String iconTexturePath, String barTexturePath) {
      return styleId != null && styleId.contains("gaia")
         ? true
         : iconTexturePath != null && iconTexturePath.contains("gaia") || barTexturePath != null && barTexturePath.contains("gaia");
   }

   private boolean isEntryMotionFrameLocked(String styleId) {
      return ValorantStyleCatalog.useEmblemOnlyEntryMotion(styleId);
   }

   private ValorantIconRenderer.HeadshotAnimState resolveHeadshotAnimState(long elapsed, int restColor) {
      long durationMs = (long)Math.round(this.configHeadshotAnimDuration * 1000.0F);
      if (durationMs <= 0L || this.configHeadshotAnimInitialScale <= 1.0F) {
         return ValorantIconRenderer.HeadshotAnimState.idle(restColor);
      } else if (elapsed >= durationMs) {
         return ValorantIconRenderer.HeadshotAnimState.idle(restColor);
      } else {
         float t = Mth.clamp((float)elapsed / (float)durationMs, 0.0F, 1.0F);
         float scale = Mth.lerp(easeOutCubic(t), this.configHeadshotAnimInitialScale, 1.0F);
         float flickerFreq = 18.0F;
         int color;
         if (flickerFreq <= 0.0F) {
            color = restColor;
         } else {
            float phase = (float)Math.sin((double)((float)elapsed * flickerFreq) * Math.PI * 2.0 / 1000.0);
            boolean flashWhite = phase > 0.0F;
            float envelope = (1.0F - t) * (1.0F - t);
            color = flashWhite ? lerpColor(restColor, 16777215, envelope) : restColor;
         }

         return new ValorantIconRenderer.HeadshotAnimState(scale, color);
      }
   }

   private static int lerpColor(int colorA, int colorB, float t) {
      int rA = colorA >> 16 & 0xFF;
      int gA = colorA >> 8 & 0xFF;
      int bA = colorA & 0xFF;
      int rB = colorB >> 16 & 0xFF;
      int gB = colorB >> 8 & 0xFF;
      int bB = colorB & 0xFF;
      int r = Math.round(Mth.lerp(t, (float)rA, (float)rB));
      int g = Math.round(Mth.lerp(t, (float)gA, (float)gB));
      int b = Math.round(Mth.lerp(t, (float)bA, (float)bB));
      return r << 16 | g << 8 | b;
   }

   private static record FlashState(int color, float alpha) {
      private static ValorantIconRenderer.FlashState none() {
         return new ValorantIconRenderer.FlashState(16777215, 0.0F);
      }
   }

   private static record HeadshotAnimState(float scale, int color) {
      private static ValorantIconRenderer.HeadshotAnimState idle(int restColor) {
         return new ValorantIconRenderer.HeadshotAnimState(1.0F, restColor);
      }
   }

   private static record ParticleOverlaySpec(
      String configKeyPrefix, float anchorXRatio, float anchorYRatio, float defaultSourceScale, float offsetXPx, float offsetYPx, boolean additive
   ) {
   }

   private static record SmallParticle(
      float angleDeg,
      float tiltDeg,
      float startRadius,
      float endRadius,
      float width,
      float height,
      float alphaScale,
      float jagScale,
      float bendScale,
      float lateralBoost,
      float gravityPx,
      long delayMs
   ) {
   }
}
