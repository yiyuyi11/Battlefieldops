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

public final class IconEntranceBackground {
   private IconEntranceBackground() {
   }

   public static void drawRect(
      GuiGraphics guiGraphics, long elapsedMs, float centerX, float centerY, float size, int color, long fadeInMs, long fadeOutMs, float peakTransparency
   ) {
      if (elapsedMs >= 0L && fadeInMs > 0L && fadeOutMs > 0L && elapsedMs < fadeInMs + fadeOutMs) {
         float peakAlpha = Mth.clamp(1.0F - peakTransparency, 0.0F, 1.0F);
         float alpha;
         float scale;
         if (elapsedMs < fadeInMs) {
            alpha = peakAlpha * ((float)elapsedMs / (float)fadeInMs);
            scale = 1.0F;
         } else {
            float progress = (float)(elapsedMs - fadeInMs) / (float)fadeOutMs;
            alpha = peakAlpha * (1.0F - progress);
            scale = 1.0F - progress;
         }

         if (!(alpha <= 0.001F) && !(scale <= 0.001F)) {
            int argb = color & 16777215 | (int)(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY, 0.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.pose().translate(-size / 2.0F, -size / 2.0F, 0.0F);
            guiGraphics.fill(0, 0, Math.round(size), Math.round(size), argb);
            guiGraphics.pose().popPose();
         }
      }
   }

   public static void drawBorder(
      GuiGraphics guiGraphics,
      long elapsedMs,
      float centerX,
      float centerY,
      float size,
      int color,
      long fadeInMs,
      long fadeOutMs,
      float borderWidth,
      float peakTransparency
   ) {
      if (!(borderWidth <= 0.0F)) {
         if (elapsedMs >= 0L && fadeInMs > 0L && fadeOutMs > 0L && elapsedMs < fadeInMs + fadeOutMs) {
            float peakAlpha = Mth.clamp(1.0F - peakTransparency, 0.0F, 1.0F);
            float alpha;
            float scale;
            if (elapsedMs < fadeInMs) {
               alpha = peakAlpha * ((float)elapsedMs / (float)fadeInMs);
               scale = 1.0F;
            } else {
               float progress = (float)(elapsedMs - fadeInMs) / (float)fadeOutMs;
               alpha = peakAlpha * (1.0F - progress);
               scale = 1.0F - progress;
            }

            if (!(alpha <= 0.001F) && !(scale <= 0.001F)) {
               float half = size * scale / 2.0F;
               float maxDiag = half * 1.4142135F;
               float quarterX = (float)fadeInMs / 4.0F;
               float diagLen;
               if ((float)elapsedMs < quarterX) {
                  diagLen = maxDiag * (1.0F - (float)elapsedMs / quarterX);
               } else if (elapsedMs < fadeInMs) {
                  diagLen = maxDiag * (((float)elapsedMs - quarterX) / ((float)fadeInMs - quarterX));
               } else {
                  float twoThirdsZ = (float)fadeOutMs * 2.0F / 3.0F;
                  float outProgress = (float)(elapsedMs - fadeInMs);
                  diagLen = outProgress < twoThirdsZ ? maxDiag * (1.0F - outProgress / twoThirdsZ) : 0.0F;
               }

               drawBorderQuads(guiGraphics, centerX, centerY, half, color, borderWidth, alpha, diagLen);
            }
         }
      }
   }

   private static void drawBorderQuads(
      GuiGraphics guiGraphics, float centerX, float centerY, float half, int color, float borderWidth, float alpha, float diagLen
   ) {
      float x1 = centerX - half;
      float x2 = centerX + half;
      float y1 = centerY - half;
      float y2 = centerY + half;
      float halfThickness = borderWidth * 0.5F;
      int a = (int)(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      float leftOuter = x1 - halfThickness;
      float leftInner = x1 + halfThickness;
      float rightInner = x2 - halfThickness;
      float rightOuter = x2 + halfThickness;
      float topOuter = y1 - halfThickness;
      float topInner = y1 + halfThickness;
      float bottomInner = y2 - halfThickness;
      float bottomOuter = y2 + halfThickness;
      Matrix4f matrix = guiGraphics.pose().last().pose();
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder builder;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      builder = Draw.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      quad(builder, matrix, leftOuter, topOuter, rightOuter, topInner, r, g, b, a);
      quad(builder, matrix, leftOuter, bottomInner, rightOuter, bottomOuter, r, g, b, a);
      quad(builder, matrix, leftOuter, topInner, leftInner, bottomInner, r, g, b, a);
      quad(builder, matrix, rightInner, topInner, rightOuter, bottomInner, r, g, b, a);
      float rayProj = diagLen * 0.70710677F;
      lineQuad(builder, matrix, centerX, centerY, centerX - rayProj, centerY - rayProj, halfThickness, r, g, b, a);
      lineQuad(builder, matrix, centerX, centerY, centerX + rayProj, centerY - rayProj, halfThickness, r, g, b, a);
      lineQuad(builder, matrix, centerX, centerY, centerX + rayProj, centerY + rayProj, halfThickness, r, g, b, a);
      lineQuad(builder, matrix, centerX, centerY, centerX - rayProj, centerY + rayProj, halfThickness, r, g, b, a);
      Draw.end(builder);
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private static void lineQuad(BufferBuilder builder, Matrix4f matrix, float ax, float ay, float bx, float by, float halfThickness, int r, int g, int b, int a) {
      float dx = bx - ax;
      float dy = by - ay;
      float len = (float)Math.sqrt((double)(dx * dx + dy * dy));
      if (!(len < 1.0E-4F)) {
         float nx = -dy / len * halfThickness;
         float ny = dx / len * halfThickness;
         builder.vertex(matrix, ax + nx, ay + ny, 0.0F).color(r, g, b, a);
         builder.vertex(matrix, ax - nx, ay - ny, 0.0F).color(r, g, b, a);
         builder.vertex(matrix, bx - nx, by - ny, 0.0F).color(r, g, b, a);
         builder.vertex(matrix, bx + nx, by + ny, 0.0F).color(r, g, b, a);
      }
   }

   private static void quad(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, int r, int g, int b, int a) {
      builder.vertex(matrix, x1, y1, 0.0F).color(r, g, b, a);
      builder.vertex(matrix, x2, y1, 0.0F).color(r, g, b, a);
      builder.vertex(matrix, x2, y2, 0.0F).color(r, g, b, a);
      builder.vertex(matrix, x1, y2, 0.0F).color(r, g, b, a);
   }

   public static int parseColor(String hex, int fallback) {
      if (hex == null) {
         return fallback;
      } else {
         String value = hex.trim();
         if (value.startsWith("#")) {
            value = value.substring(1);
         }

         if (value.length() != 6) {
            return fallback;
         } else {
            try {
               return Integer.parseInt(value, 16) & 16777215;
            } catch (NumberFormatException var4) {
               return fallback;
            }
         }
      }
   }
}
