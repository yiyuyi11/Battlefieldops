package cn.bfops.conquest.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class CaptureDiamondRenderer {
   private static final float LABEL_SCALE = 0.72F;
   private static final float LABEL_X_OFFSET = 0.3F;
   private static final float LABEL_Y_OFFSET = -2.8F;
   private static final int LABEL_PACKED_LIGHT = 15728880;

   private CaptureDiamondRenderer() {
   }

   public static void drawCaptureDiamond(Matrix4f matrix, float radius, float progress, float alpha, String ownerToken, String drivingToken) {
      float iconAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
      RenderSystem.disableCull();
      float[] baseColor = resolveBaseColor(progress, ownerToken);
      float baseBlend = 0.45F;
      baseColor[0] = Mth.lerp(baseBlend, baseColor[0], 0.22F);
      baseColor[1] = Mth.lerp(baseBlend, baseColor[1], 0.22F);
      baseColor[2] = Mth.lerp(baseBlend, baseColor[2], 0.22F);
      drawDiamond(matrix, radius, baseColor[0], baseColor[1], baseColor[2], iconAlpha);
      float sectorProgress = Mth.clamp(Math.abs(progress - 50.0F) / 50.0F, 0.0F, 1.0F);
      String sectorToken = resolveSectorPerspectiveToken(progress, ownerToken, drivingToken);
      if (sectorProgress > 0.0F && "ALLY".equalsIgnoreCase(sectorToken)) {
         drawDiamondSector(matrix, radius, sectorProgress, true, 0.14F, 0.52F, 1.0F, Math.min(1.0F, iconAlpha * 1.25F));
      } else if (sectorProgress > 0.0F && "ENEMY".equalsIgnoreCase(sectorToken)) {
         drawDiamondSector(matrix, radius, sectorProgress, false, 1.0F, 0.18F, 0.18F, Math.min(1.0F, iconAlpha * 1.25F));
      }

      drawDiamondOutline(matrix, radius, 1.0F, 1.0F, 1.0F, iconAlpha);
      drawDiamondOutline(matrix, radius + 0.35F, 1.0F, 1.0F, 1.0F, iconAlpha);
      drawDiamondOutline(matrix, Math.max(1.0F, radius - 0.35F), 1.0F, 1.0F, 1.0F, iconAlpha);
      RenderSystem.enableCull();
   }

   public static void drawSpawnDiamond(Matrix4f matrix, float radius, float alpha, String ownerToken) {
      float iconAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
      RenderSystem.disableCull();
      float[] baseColor = resolveSpawnBaseColor(ownerToken);
      drawDiamond(matrix, radius, baseColor[0], baseColor[1], baseColor[2], iconAlpha);
      drawDiamondOutline(matrix, radius, 1.0F, 1.0F, 1.0F, iconAlpha);
      drawDiamondOutline(matrix, radius + 0.35F, 1.0F, 1.0F, 1.0F, iconAlpha);
      drawDiamondOutline(matrix, Math.max(1.0F, radius - 0.35F), 1.0F, 1.0F, 1.0F, iconAlpha);
      RenderSystem.enableCull();
   }

   public static void drawCaptureIconInHud(
      GuiGraphics gui,
      Font font,
      String text,
      boolean spawnPoint,
      float centerX,
      float centerY,
      float radius,
      float progress,
      float alpha,
      String ownerToken,
      String drivingToken,
      float labelScale,
      float labelXOffset,
      boolean selected,
      boolean hovered
   ) {
      BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
      drawCaptureIconInHud(
         gui,
         font,
         text,
         spawnPoint,
         centerX,
         centerY,
         radius,
         progress,
         alpha,
         ownerToken,
         drivingToken,
         labelScale,
         labelXOffset,
         selected,
         hovered,
         bufferSource
      );
      bufferSource.endBatch();
   }

   public static void drawCaptureIconInHud(
      GuiGraphics gui,
      Font font,
      String text,
      boolean spawnPoint,
      float centerX,
      float centerY,
      float radius,
      float progress,
      float alpha,
      String ownerToken,
      String drivingToken,
      float labelScale,
      float labelXOffset,
      boolean selected,
      boolean hovered,
      BufferSource bufferSource
   ) {
      gui.pose().pushPose();
      gui.pose().translate(centerX, centerY, 0.0F);
      Matrix4f matrix = gui.pose().last().pose();
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      if (spawnPoint) {
         drawSpawnDiamond(matrix, radius, alpha, ownerToken);
         if ("ALLY_STAGE_PROXY".equalsIgnoreCase(ownerToken)) {
            drawDiamondOutline(matrix, radius, 0.14F, 0.36F, 0.82F, alpha);
            drawDiamondOutline(matrix, radius + 0.35F, 0.14F, 0.36F, 0.82F, alpha);
            drawDiamondOutline(matrix, Math.max(1.0F, radius - 0.35F), 0.14F, 0.36F, 0.82F, alpha);
         } else if ("ENEMY".equalsIgnoreCase(drivingToken)) {
            drawDiamondOutline(matrix, radius, 1.0F, 0.24F, 0.24F, alpha);
            drawDiamondOutline(matrix, radius + 0.35F, 1.0F, 0.24F, 0.24F, alpha);
            drawDiamondOutline(matrix, Math.max(1.0F, radius - 0.35F), 1.0F, 0.24F, 0.24F, alpha);
         }
      } else {
         drawCaptureDiamond(matrix, radius, progress, alpha, ownerToken, drivingToken);
      }

      if (selected) {
         drawDiamondOutline(matrix, radius + 1.0F, 1.0F, 0.9F, 0.2F, alpha);
         drawDiamondOutline(matrix, radius + 1.45F, 1.0F, 0.9F, 0.2F, alpha);
      } else if (hovered) {
         drawDiamondOutline(matrix, radius + 1.0F, 1.0F, 1.0F, 1.0F, alpha);
         drawDiamondOutline(matrix, radius + 1.45F, 1.0F, 1.0F, 1.0F, alpha);
      }

      drawIconGlyphInHud(gui, font, text, alpha, labelScale, bufferSource);
      RenderSystem.enableDepthTest();
      gui.pose().popPose();
   }

   public static void drawVehicleIconInHud(
      GuiGraphics gui,
      Font font,
      String text,
      float centerX,
      float centerY,
      float radius,
      float alpha,
      int fillRgb,
      int outlineRgb,
      int glyphRgb,
      float labelScale,
      boolean selected,
      boolean hovered
   ) {
      BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
      drawVehicleIconInHud(gui, font, text, centerX, centerY, radius, alpha, fillRgb, outlineRgb, glyphRgb, labelScale, selected, hovered, bufferSource);
      bufferSource.endBatch();
   }

   public static void drawVehicleIconInHud(
      GuiGraphics gui,
      Font font,
      String text,
      float centerX,
      float centerY,
      float radius,
      float alpha,
      int fillRgb,
      int outlineRgb,
      int glyphRgb,
      float labelScale,
      boolean selected,
      boolean hovered,
      BufferSource bufferSource
   ) {
      gui.pose().pushPose();
      gui.pose().translate(centerX, centerY, 0.0F);
      Matrix4f matrix = gui.pose().last().pose();
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      float[] fill = rgb(fillRgb);
      float[] outline = rgb(outlineRgb);
      drawDiamond(matrix, radius, fill[0], fill[1], fill[2], Mth.clamp(alpha, 0.0F, 1.0F));
      drawDiamondOutline(matrix, radius, outline[0], outline[1], outline[2], alpha);
      drawDiamondOutline(matrix, radius + 0.35F, outline[0], outline[1], outline[2], alpha);
      drawDiamondOutline(matrix, Math.max(1.0F, radius - 0.35F), outline[0], outline[1], outline[2], alpha);
      if (selected) {
         drawDiamondOutline(matrix, radius + 1.0F, 1.0F, 0.9F, 0.2F, alpha);
         drawDiamondOutline(matrix, radius + 1.45F, 1.0F, 0.9F, 0.2F, alpha);
      } else if (hovered) {
         drawDiamondOutline(matrix, radius + 1.0F, 1.0F, 1.0F, 1.0F, alpha);
         drawDiamondOutline(matrix, radius + 1.45F, 1.0F, 1.0F, 1.0F, alpha);
      }

      drawIconGlyphInHud(gui, font, text, alpha, labelScale, glyphRgb, bufferSource);
      RenderSystem.enableDepthTest();
      gui.pose().popPose();
   }

   public static void drawDiamondOutline(Matrix4f matrix, float radius, float red, float green, float blue, float alpha) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.lineWidth(1.0F);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer;
      buffer = tesselator.begin(Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
      buffer.addVertex(matrix, 0.0F, radius, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, -radius, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, -radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, radius, 0.0F).setColor(red, green, blue, alpha);
      BufferUploader.drawWithShader(buffer.buildOrThrow());
   }

   public static void drawIconLabelInWorld(Font font, PoseStack poseStack, BufferSource bufferSource, String text, float alpha) {
      float textAlpha = alpha <= 0.01F ? 0.0F : Math.max(0.11F, alpha);
      int alphaByte = (int)(Mth.clamp(textAlpha, 0.0F, 1.0F) * 255.0F) << 24;
      int color = alphaByte | 16777215;
      float textWidth = (float)font.width(text);
      poseStack.pushPose();
      poseStack.scale(0.72F, 0.72F, 1.0F);
      Matrix4f textMatrix = poseStack.last().pose();
      font.drawInBatch(text, (-textWidth * 0.5F + 0.3F) / 0.72F, -3.8888886F, color, true, textMatrix, bufferSource, DisplayMode.SEE_THROUGH, 0, 15728880);
      poseStack.popPose();
   }

   public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, int centerX, int centerY, float alpha) {
      drawIconLabelInHud(gui, font, text, centerX, centerY, alpha, 0.72F);
   }

   public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, int centerX, int centerY, float alpha, float labelScale) {
      drawIconLabelInHud(gui, font, text, (float)centerX, (float)centerY, alpha, labelScale, 0.3F);
   }

   public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, float centerX, float centerY, float alpha, float labelScale) {
      drawIconLabelInHud(gui, font, text, centerX, centerY, alpha, labelScale, 0.3F);
   }

   public static void drawIconLabelInHud(
      GuiGraphics gui, Font font, String text, float centerX, float centerY, float alpha, float labelScale, float labelXOffset
   ) {
      BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
      float textAlpha = alpha <= 0.01F ? 0.0F : Math.max(0.11F, alpha);
      int alphaByte = (int)(Mth.clamp(textAlpha, 0.0F, 1.0F) * 255.0F) << 24;
      int color = alphaByte | 16777215;
      float textWidth = (float)font.width(text);
      float usingScale = Math.max(0.1F, labelScale);
      gui.pose().pushPose();
      gui.pose().translate(centerX, centerY, 0.0F);
      gui.pose().scale(usingScale, usingScale, 1.0F);
      Matrix4f textMatrix = gui.pose().last().pose();
      font.drawInBatch(text, -textWidth * 0.5F + labelXOffset, -2.8F, color, true, textMatrix, bufferSource, DisplayMode.NORMAL, 0, 15728880);
      bufferSource.endBatch();
      gui.pose().popPose();
   }

   public static void drawCenteredHudText(GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow) {
      drawCenteredHudText(gui, font, text, centerX, topY, rgb, alpha, shadow, 1.0F);
   }

   public static void drawCenteredHudText(GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow, float scale) {
      BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
      drawCenteredHudText(gui, font, text, centerX, topY, rgb, alpha, shadow, scale, bufferSource);
      bufferSource.endBatch();
   }

   public static void drawCenteredHudText(
      GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow, float scale, BufferSource bufferSource
   ) {
      float textAlpha = alpha <= 0.01F ? 0.0F : Math.max(0.11F, alpha);
      int alphaByte = (int)(Mth.clamp(textAlpha, 0.0F, 1.0F) * 255.0F) << 24;
      int color = alphaByte | rgb & 16777215;
      float usingScale = Math.max(0.1F, scale);
      gui.pose().pushPose();
      gui.pose().translate(centerX, topY, 0.0F);
      gui.pose().scale(usingScale, usingScale, 1.0F);
      Matrix4f matrix = gui.pose().last().pose();
      font.drawInBatch(text, (float)(-font.width(text)) * 0.5F, 0.0F, color, shadow, matrix, bufferSource, DisplayMode.NORMAL, 0, 15728880);
      gui.pose().popPose();
   }

   public static void drawScreenCrosshair(
      GuiGraphics gui,
      float centerX,
      float centerY,
      int screenWidth,
      int screenHeight,
      float horizontalGap,
      float verticalGap,
      float horizontalFeather,
      float verticalFeather,
      float thickness,
      int rgb,
      float alpha
   ) {
      float clampedAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
      if (!(clampedAlpha <= 0.01F) && screenWidth > 0 && screenHeight > 0) {
         float safeCenterX = Mth.clamp(centerX, 0.0F, (float)screenWidth);
         float safeCenterY = Mth.clamp(centerY, 0.0F, (float)screenHeight);
         int anchorX = Math.round(safeCenterX);
         int anchorY = Math.round(safeCenterY);
         float offsetX = safeCenterX - (float)anchorX;
         float offsetY = safeCenterY - (float)anchorY;
         int pixelThickness = Math.max(1, Math.round(Math.max(1.0F, thickness)));
         int horizontalStartY = Math.round((float)(-pixelThickness) * 0.5F);
         int verticalStartX = Math.round((float)(-pixelThickness) * 0.5F);
         gui.pose().pushPose();
         gui.pose().translate(offsetX, offsetY, 0.0F);
         Matrix4f matrix = gui.pose().last().pose();
         float gapLeft = (float)anchorX - Math.max(0.0F, horizontalGap);
         float gapRight = (float)anchorX + Math.max(0.0F, horizontalGap);
         float gapTop = (float)anchorY - Math.max(0.0F, verticalGap);
         float gapBottom = (float)anchorY + Math.max(0.0F, verticalGap);
         float barTop = (float)(anchorY + horizontalStartY);
         float barBottom = barTop + (float)pixelThickness;
         float barLeft = (float)(anchorX + verticalStartX);
         float barRight = barLeft + (float)pixelThickness;
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder buffer;
         buffer = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         addHorizontalCrosshairArm(buffer, matrix, 0.0F, gapLeft, barTop, barBottom, false, horizontalFeather, rgb, clampedAlpha);
         addHorizontalCrosshairArm(buffer, matrix, gapRight, (float)screenWidth, barTop, barBottom, true, horizontalFeather, rgb, clampedAlpha);
         addVerticalCrosshairArm(buffer, matrix, 0.0F, gapTop, barLeft, barRight, false, verticalFeather, rgb, clampedAlpha);
         addVerticalCrosshairArm(buffer, matrix, gapBottom, (float)screenHeight, barLeft, barRight, true, verticalFeather, rgb, clampedAlpha);
         BufferUploader.drawWithShader(buffer.buildOrThrow());
         gui.pose().popPose();
      }
   }

   private static void drawIconGlyphInHud(GuiGraphics gui, Font font, String text, float alpha, float labelScale, BufferSource bufferSource) {
      drawIconGlyphInHud(gui, font, text, alpha, labelScale, 16777215, bufferSource);
   }

   private static void drawIconGlyphInHud(GuiGraphics gui, Font font, String text, float alpha, float labelScale, int rgb, BufferSource bufferSource) {
      float textAlpha = alpha <= 0.01F ? 0.0F : Math.max(0.11F, alpha);
      int alphaByte = (int)(Mth.clamp(textAlpha, 0.0F, 1.0F) * 255.0F) << 24;
      int color = alphaByte | rgb & 16777215;
      float usingScale = Math.max(0.1F, labelScale);
      gui.pose().pushPose();
      gui.pose().scale(usingScale, usingScale, 1.0F);
      Matrix4f textMatrix = gui.pose().last().pose();
      float drawX = (float)(-font.width(text)) * 0.5F;
      float drawY = (float)(-9) * 0.5F + 0.5F;
      font.drawInBatch(text, drawX, drawY, color, true, textMatrix, bufferSource, DisplayMode.NORMAL, 0, 15728880);
      gui.pose().popPose();
   }

   private static void addHorizontalCrosshairArm(
      BufferBuilder buffer, Matrix4f matrix, float startX, float endX, float top, float bottom, boolean extendsRight, float feather, int rgb, float alpha
   ) {
      if (!(endX <= startX) && !(alpha <= 0.0F)) {
         float clampedFeather = Math.max(0.0F, feather);
         if (clampedFeather <= 0.001F) {
            addSolidQuad(buffer, matrix, startX, top, endX, bottom, rgb, alpha);
         } else if (extendsRight) {
            float fadeEnd = Math.min(endX, startX + clampedFeather);
            addHorizontalGradientQuad(buffer, matrix, startX, top, fadeEnd, bottom, rgb, 0.0F, alpha);
            if (endX > fadeEnd) {
               addSolidQuad(buffer, matrix, fadeEnd, top, endX, bottom, rgb, alpha);
            }
         } else {
            float fadeStart = Math.max(startX, endX - clampedFeather);
            if (fadeStart > startX) {
               addSolidQuad(buffer, matrix, startX, top, fadeStart, bottom, rgb, alpha);
            }

            addHorizontalGradientQuad(buffer, matrix, fadeStart, top, endX, bottom, rgb, alpha, 0.0F);
         }
      }
   }

   private static void addVerticalCrosshairArm(
      BufferBuilder buffer, Matrix4f matrix, float startY, float endY, float left, float right, boolean extendsDown, float feather, int rgb, float alpha
   ) {
      if (!(endY <= startY) && !(alpha <= 0.0F)) {
         float clampedFeather = Math.max(0.0F, feather);
         if (clampedFeather <= 0.001F) {
            addSolidQuad(buffer, matrix, left, startY, right, endY, rgb, alpha);
         } else if (extendsDown) {
            float fadeEnd = Math.min(endY, startY + clampedFeather);
            addVerticalGradientQuad(buffer, matrix, left, startY, right, fadeEnd, rgb, 0.0F, alpha);
            if (endY > fadeEnd) {
               addSolidQuad(buffer, matrix, left, fadeEnd, right, endY, rgb, alpha);
            }
         } else {
            float fadeStart = Math.max(startY, endY - clampedFeather);
            if (fadeStart > startY) {
               addSolidQuad(buffer, matrix, left, startY, right, fadeStart, rgb, alpha);
            }

            addVerticalGradientQuad(buffer, matrix, left, fadeStart, right, endY, rgb, alpha, 0.0F);
         }
      }
   }

   private static void addSolidQuad(BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float alpha) {
      addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, alpha, alpha, alpha, alpha);
   }

   private static void addHorizontalGradientQuad(
      BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float leftAlpha, float rightAlpha
   ) {
      addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, leftAlpha, rightAlpha, rightAlpha, leftAlpha);
   }

   private static void addVerticalGradientQuad(
      BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float topAlpha, float bottomAlpha
   ) {
      addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, topAlpha, topAlpha, bottomAlpha, bottomAlpha);
   }

   private static void addGradientQuad(
      BufferBuilder buffer,
      Matrix4f matrix,
      float left,
      float top,
      float right,
      float bottom,
      int rgb,
      float topLeftAlpha,
      float topRightAlpha,
      float bottomRightAlpha,
      float bottomLeftAlpha
   ) {
      if (!(right <= left) && !(bottom <= top)) {
         float red = (float)(rgb >>> 16 & 0xFF) / 255.0F;
         float green = (float)(rgb >>> 8 & 0xFF) / 255.0F;
         float blue = (float)(rgb & 0xFF) / 255.0F;
         buffer.addVertex(matrix, left, bottom, 0.0F).setColor(red, green, blue, Mth.clamp(bottomLeftAlpha, 0.0F, 1.0F));
         buffer.addVertex(matrix, right, bottom, 0.0F).setColor(red, green, blue, Mth.clamp(bottomRightAlpha, 0.0F, 1.0F));
         buffer.addVertex(matrix, right, top, 0.0F).setColor(red, green, blue, Mth.clamp(topRightAlpha, 0.0F, 1.0F));
         buffer.addVertex(matrix, left, top, 0.0F).setColor(red, green, blue, Mth.clamp(topLeftAlpha, 0.0F, 1.0F));
      }
   }

   private static float[] rgb(int rgb) {
      return new float[]{(float)(rgb >>> 16 & 0xFF) / 255.0F, (float)(rgb >>> 8 & 0xFF) / 255.0F, (float)(rgb & 0xFF) / 255.0F};
   }

   private static float[] resolveBaseColor(float progress, String ownerToken) {
      float intensity = Mth.clamp(Math.abs(progress - 50.0F) / 50.0F, 0.0F, 1.0F);
      if ("ALLY".equalsIgnoreCase(ownerToken)) {
         return new float[]{Mth.lerp(intensity, 0.45F, 0.2F), Mth.lerp(intensity, 0.45F, 0.33F), Mth.lerp(intensity, 0.45F, 0.7F)};
      } else {
         return "ENEMY".equalsIgnoreCase(ownerToken)
            ? new float[]{Mth.lerp(intensity, 0.45F, 0.72F), Mth.lerp(intensity, 0.45F, 0.18F), Mth.lerp(intensity, 0.45F, 0.18F)}
            : new float[]{0.45F, 0.45F, 0.45F};
      }
   }

   private static float[] resolveSpawnBaseColor(String ownerToken) {
      if ("DBG_CAMP_A_SPAWN".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.12F, 0.24F, 0.58F};
      } else if ("DBG_CAMP_B_SPAWN".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.58F, 0.14F, 0.14F};
      } else if ("DBG_QUEUE".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.72F, 0.74F, 0.78F};
      } else if ("DBG_DEPLOY".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.2F, 0.62F, 0.32F};
      } else if ("DBG_END".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.8F, 0.68F, 0.16F};
      } else if ("DBG_OUTER_CAMP_A".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.56F, 0.7F, 0.96F};
      } else if ("DBG_OUTER_CAMP_B".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.95F, 0.56F, 0.56F};
      } else if ("DBG_INNER_CAMP_A".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.43F, 0.6F, 0.96F};
      } else if ("DBG_INNER_CAMP_B".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.96F, 0.48F, 0.48F};
      } else if ("DBG_INNER_NEUTRAL".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.7F, 0.73F, 0.78F};
      } else if ("DBG_OUTER_NEUTRAL".equalsIgnoreCase(ownerToken) || "DBG_NEUTRAL".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.78F, 0.8F, 0.84F};
      } else if ("ALLY".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.2F, 0.33F, 0.7F};
      } else if ("ALLY_STAGE_PROXY".equalsIgnoreCase(ownerToken)) {
         return new float[]{0.2F, 0.33F, 0.7F};
      } else {
         return "ENEMY".equalsIgnoreCase(ownerToken) ? new float[]{0.72F, 0.18F, 0.18F} : new float[]{0.45F, 0.45F, 0.45F};
      }
   }

   private static String resolveSectorPerspectiveToken(float progress, String ownerToken, String drivingToken) {
      if ("ALLY".equalsIgnoreCase(ownerToken) || "ENEMY".equalsIgnoreCase(ownerToken)) {
         return ownerToken;
      } else if (progress < 50.0F) {
         return "ALLY";
      } else {
         return progress > 50.0F ? "ENEMY" : "NEUTRAL";
      }
   }

   private static void drawDiamond(Matrix4f matrix, float radius, float red, float green, float blue, float alpha) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer;
      buffer = tesselator.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, radius, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, -radius, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, -radius, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, -radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, -radius, 0.0F, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, 0.0F, radius, 0.0F).setColor(red, green, blue, alpha);
      BufferUploader.drawWithShader(buffer.buildOrThrow());
   }

   private static void drawDiamondSector(Matrix4f matrix, float radius, float progress, boolean clockwise, float red, float green, float blue, float alpha) {
      if (!(progress <= 0.0F)) {
         List<float[]> perimeter = buildSectorPerimeter(radius, progress, clockwise);
         if (perimeter.size() >= 2) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer;
            buffer = tesselator.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (int i = 0; i < perimeter.size() - 1; i++) {
               float[] p0 = perimeter.get(i);
               float[] p1 = perimeter.get(i + 1);
               buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
               buffer.addVertex(matrix, p0[0], p0[1], 0.0F).setColor(red, green, blue, alpha);
               buffer.addVertex(matrix, p1[0], p1[1], 0.0F).setColor(red, green, blue, alpha);
               buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
               buffer.addVertex(matrix, p1[0], p1[1], 0.0F).setColor(red, green, blue, alpha);
               buffer.addVertex(matrix, p0[0], p0[1], 0.0F).setColor(red, green, blue, alpha);
            }

            BufferUploader.drawWithShader(buffer.buildOrThrow());
         }
      }
   }

   private static List<float[]> buildSectorPerimeter(float radius, float progress, boolean clockwise) {
      double len = (double)Mth.clamp(progress, 0.0F, 1.0F) * 4.0;
      List<float[]> points = new ArrayList<>();
      points.add(flipSectorY(new float[]{0.0F, radius}));
      int fullSegments = (int)Math.floor(len);

      for (int segment = 1; segment <= fullSegments; segment++) {
         points.add(flipSectorY(pointOnDiamondPerimeter(radius, (double)segment, clockwise)));
      }

      double remain = len - (double)fullSegments;
      if (remain > 1.0E-6 && fullSegments < 4) {
         points.add(flipSectorY(pointOnDiamondPerimeter(radius, (double)fullSegments + remain, clockwise)));
      }

      if (len >= 3.999) {
         points.add(flipSectorY(new float[]{0.0F, radius}));
      }

      return points;
   }

   private static float[] flipSectorY(float[] point) {
      return new float[]{point[0], -point[1]};
   }

   private static float[] pointOnDiamondPerimeter(float radius, double s, boolean clockwise) {
      double clamped = Mth.clamp(s, 0.0, 4.0);
      if (clockwise) {
         if (clamped <= 1.0) {
            return new float[]{(float)((double)radius * clamped), (float)((double)radius * (1.0 - clamped))};
         } else if (clamped <= 2.0) {
            double t = clamped - 1.0;
            return new float[]{(float)((double)radius * (1.0 - t)), (float)((double)(-radius) * t)};
         } else if (clamped <= 3.0) {
            double t = clamped - 2.0;
            return new float[]{(float)((double)(-radius) * t), (float)((double)(-radius) * (1.0 - t))};
         } else {
            double t = clamped - 3.0;
            return new float[]{(float)((double)(-radius) * (1.0 - t)), (float)((double)radius * t)};
         }
      } else if (clamped <= 1.0) {
         return new float[]{(float)((double)(-radius) * clamped), (float)((double)radius * (1.0 - clamped))};
      } else if (clamped <= 2.0) {
         double t = clamped - 1.0;
         return new float[]{(float)((double)(-radius) * (1.0 - t)), (float)((double)(-radius) * t)};
      } else if (clamped <= 3.0) {
         double t = clamped - 2.0;
         return new float[]{(float)((double)radius * t), (float)((double)(-radius) * (1.0 - t))};
      } else {
         double t = clamped - 3.0;
         return new float[]{(float)((double)radius * (1.0 - t)), (float)((double)radius * t)};
      }
   }
}
