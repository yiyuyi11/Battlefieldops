package cn.bfops.killicon.client.render.effect;

import cn.bfops.Draw;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class IconRingEffect {
   public static final boolean DEFAULT_ENABLE_ICON_EFFECT = true;
   private static final long EFFECT_DELAY_MS = 100L;
   private static final long EFFECT_DURATION_MS = 300L;
   private static final long EXPLOSION_SECOND_RING_DELAY_MS = 100L;
   private static final float BASE_RADIUS_PX = 10.0F;
   private static final float RADIUS_GROWTH_PX = 32.0F;
   private static final float BASE_THICKNESS_PX = 3.0F;
   private float scale = 1.0F;
   private static final int SEGMENTS = 72;
   private static final float EXPLOSION_SECOND_RING_THICKNESS_MULTIPLIER = 1.8F;
   private static final float CRIT_RING_THICKNESS_MULTIPLIER = 0.6F;
   private int killType = 0;
   private long effectStartTimeMs = -1L;
   private int headshotRgb = 0;
   private int explosionRgb = 0;
   private int critRgb = 0;
   private float normalMaxRadius = 42.0F;
   private float normalThickness = 1.8000001F;
   private float headshotMaxRadius = 42.0F;
   private float headshotThickness = 3.0F;
   private float explosionMaxRadius = 42.0F;
   private float explosionThickness = 5.3999996F;

   public void setScale(float scale) {
      this.scale = scale;
   }

   public void setRingParams(
      float normalMaxRadius, float normalThickness, float headshotMaxRadius, float headshotThickness, float explosionMaxRadius, float explosionThickness
   ) {
      this.normalMaxRadius = normalMaxRadius;
      this.normalThickness = normalThickness;
      this.headshotMaxRadius = headshotMaxRadius;
      this.headshotThickness = headshotThickness;
      this.explosionMaxRadius = explosionMaxRadius;
      this.explosionThickness = explosionThickness;
   }

   public void trigger(long triggerTimeMs, boolean enabled, int killType, int headshotRgb, int explosionRgb, int critRgb) {
      this.killType = killType;
      this.headshotRgb = headshotRgb & 16777215;
      this.explosionRgb = explosionRgb & 16777215;
      this.critRgb = critRgb & 16777215;
      if (enabled && (killType == 1 || killType == 2 || killType == 3)) {
         this.effectStartTimeMs = triggerTimeMs + 100L;
      } else {
         this.effectStartTimeMs = -1L;
      }
   }

   public void render(GuiGraphics guiGraphics, float centerX, float centerY, long currentTimeMs) {
      if (this.effectStartTimeMs > 0L) {
         long effectElapsed = currentTimeMs - this.effectStartTimeMs;
         long maxEffectDuration = this.killType == 2 ? 400L : 300L;
         if (effectElapsed >= 0L && effectElapsed <= maxEffectDuration) {
            float t = Mth.clamp((float)effectElapsed / 300.0F, 0.0F, 1.0F);
            float eased = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
            float effectAlpha = 1.0F - t;
            effectAlpha *= effectAlpha;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (this.killType == 1) {
               float radius = this.resolveRadius(this.headshotMaxRadius, eased);
               float thickness = this.headshotThickness * (1.0F - t) * this.scale;
               drawRing(guiGraphics, centerX, centerY, radius, thickness, this.headshotRgb, effectAlpha);
            } else if (this.killType == 2) {
               float radius = this.resolveRadius(this.headshotMaxRadius, eased);
               float thickness1 = this.headshotThickness * (1.0F - t) * this.scale;
               drawRing(guiGraphics, centerX, centerY, radius, thickness1, this.headshotRgb, effectAlpha);
               long ring2Elapsed = effectElapsed - 100L;
               if (ring2Elapsed >= 0L && ring2Elapsed <= 300L) {
                  float t2 = (float)ring2Elapsed / 300.0F;
                  float eased2 = 1.0F - (float)Math.pow((double)(1.0F - t2), 3.0);
                  float radius2 = this.resolveRadius(this.explosionMaxRadius, eased2);
                  float alpha2 = 1.0F - t2;
                  alpha2 *= alpha2;
                  float thickness2 = this.explosionThickness * (1.0F - t2) * this.scale;
                  drawRing(guiGraphics, centerX, centerY, radius2, thickness2, this.explosionRgb, alpha2);
               }
            } else {
               if (this.killType == 3) {
                  float radius = this.resolveRadius(this.normalMaxRadius, eased);
                  float thickness = this.normalThickness * (1.0F - t) * this.scale;
                  drawRing(guiGraphics, centerX, centerY, radius, thickness, this.critRgb, effectAlpha);
               }
            }
         }
      }
   }

   private float resolveRadius(float maxRadius, float eased) {
      float baseRatio = 0.23809524F;
      float minRadius = maxRadius * baseRatio;
      return (minRadius + (maxRadius - minRadius) * eased) * this.scale;
   }

   private static void drawRing(GuiGraphics guiGraphics, float centerX, float centerY, float radius, float thickness, int rgb, float alpha) {
      if (!(thickness <= 0.0F) && !(alpha <= 0.0F) && !(radius <= 0.0F)) {
         float rOuter = radius + thickness * 0.5F;
         float rInner = Math.max(0.0F, radius - thickness * 0.5F);
         int a = Mth.clamp((int)(alpha * 255.0F), 0, 255);
         int red = rgb >> 16 & 0xFF;
         int green = rgb >> 8 & 0xFF;
         int blue = rgb & 0xFF;
         Matrix4f matrix = guiGraphics.pose().last().pose();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder;
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         builder = Draw.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

         for (int i = 0; i <= 72; i++) {
            float angle = (float)((Math.PI * 2) * (double)i / 72.0);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float xo = centerX + cos * rOuter;
            float yo = centerY + sin * rOuter;
            float xi = centerX + cos * rInner;
            float yi = centerY + sin * rInner;
            builder.vertex(matrix, xo, yo, 0.0F).color(red, green, blue, a);
            builder.vertex(matrix, xi, yi, 0.0F).color(red, green, blue, a);
         }

         Draw.end(builder);
      }
   }
}
