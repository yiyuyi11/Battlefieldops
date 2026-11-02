package cn.bfops.killicon.client.render.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class BonusTextBox {
   private static final float PAD_X = 2.0F;
   private static final float PAD_Y = 1.0F;

   private BonusTextBox() {
   }

   public static void draw(GuiGraphics guiGraphics, float textWidth, float lineHeight, float borderWidth, int rgb, int alpha) {
      if (!(borderWidth <= 0.0F) && alpha > 0) {
         float x0 = -2.0F;
         float x1 = textWidth + 2.0F;
         float y0 = -1.0F;
         float y1 = lineHeight + 1.0F;
         float halfThickness = borderWidth * 0.5F;
         int a = Math.max(0, Math.min(255, alpha));
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         Matrix4f matrix = guiGraphics.pose().last().pose();
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableDepthTest();
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         float leftOuter = x0 - halfThickness;
         float leftInner = x0 + halfThickness;
         float rightInner = x1 - halfThickness;
         float rightOuter = x1 + halfThickness;
         float topOuter = y0 - halfThickness;
         float topInner = y0 + halfThickness;
         float bottomInner = y1 - halfThickness;
         float bottomOuter = y1 + halfThickness;
         quad(builder, matrix, leftOuter, topOuter, rightOuter, topInner, r, g, b, a);
         quad(builder, matrix, leftOuter, bottomInner, rightOuter, bottomOuter, r, g, b, a);
         quad(builder, matrix, leftOuter, topInner, leftInner, bottomInner, r, g, b, a);
         quad(builder, matrix, rightInner, topInner, rightOuter, bottomInner, r, g, b, a);
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
}
