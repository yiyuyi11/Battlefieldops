package cn.bfops.conquest.client.marker;

import cn.bfops.Draw;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TacticalMarkerRenderer {
   private static final ResourceLocation TEX_ASSAULT_TEAM = new ResourceLocation("battlefield_ops:textures/gui/marker_class_assault_team.png");
   private static final ResourceLocation TEX_ASSAULT_SQUAD = new ResourceLocation("battlefield_ops:textures/gui/marker_class_assault_squad.png");
   private static final ResourceLocation TEX_ENGINEER_TEAM = new ResourceLocation("battlefield_ops:textures/gui/marker_class_engineer_team.png");
   private static final ResourceLocation TEX_ENGINEER_SQUAD = new ResourceLocation("battlefield_ops:textures/gui/marker_class_engineer_squad.png");
   private static final ResourceLocation TEX_RECON_TEAM = new ResourceLocation("battlefield_ops:textures/gui/marker_class_recon_team.png");
   private static final ResourceLocation TEX_RECON_SQUAD = new ResourceLocation("battlefield_ops:textures/gui/marker_class_recon_squad.png");
   private static final ResourceLocation TEX_SUPPORT_TEAM = new ResourceLocation("battlefield_ops:textures/gui/marker_class_support_team.png");
   private static final ResourceLocation TEX_SUPPORT_SQUAD = new ResourceLocation("battlefield_ops:textures/gui/marker_class_support_squad.png");
   private static final ResourceLocation TEX_NEED_AMMO = new ResourceLocation("battlefield_ops:textures/gui/marker_ammo_box.png");
   private static final ResourceLocation TEX_DOWNED_MEDICAL = new ResourceLocation("battlefield_ops:textures/gui/marker_med_box.png");
   private static final ResourceLocation TEX_HUD_AMMO_BOX = new ResourceLocation("battlefield_ops:textures/gui/hud_ammo_box.png");
   private static final ResourceLocation TEX_HUD_MEDICAL_BOX = new ResourceLocation("battlefield_ops:textures/gui/hud_medical_box.png");
   private static final ResourceLocation TEX_HUD_AMMO_BAG = new ResourceLocation("battlefield_ops:textures/gui/hud_ammo_bag.png");
   private static final ResourceLocation TEX_HUD_MEDIC_BAG = new ResourceLocation("battlefield_ops:textures/gui/hud_medic_bag.png");
   private static final ResourceLocation TEX_HUD_GRENADE = new ResourceLocation("battlefield_ops:textures/gui/hud_grenade.png");
   private static final ResourceLocation TEX_HUD_CLAYMORE_MINE = new ResourceLocation("battlefield_ops:textures/gui/hud_claymore_mine.png");
   private static final ResourceLocation TEX_HUD_BEACON = new ResourceLocation("battlefield_ops:textures/gui/hud_beacon.png");
   private static final float MAX_MARKER_VISUAL_OPACITY = 1.0F;
   private static final float TRIANGLE_BASE_Y = 4.0F;
   private static final float TRIANGLE_HALF_WIDTH = 3.0F;
   private static final float TRIANGLE_HEIGHT = 5.5F;
   private static final float DIAMOND_HALF_DIAGONAL = 3.8F;
   private static final float ALLY_DOT_RADIUS = 2.55F;
   private static final float DOWNED_RADIUS = 4.15F;
   private static final float ALLY_CYAN_RED = 0.16F;
   private static final float ALLY_CYAN_GREEN = 0.88F;
   private static final float ALLY_CYAN_BLUE = 1.0F;
   private static final float SQUAD_GREEN_RED = 0.38F;
   private static final float SQUAD_GREEN_GREEN = 1.0F;
   private static final float SQUAD_GREEN_BLUE = 0.58F;
   private static final float CLASS_MARKER_HALF_SIZE = 5.0F;
   private static final float SUPPLY_BOX_MARKER_HALF_SIZE = 15.6F;
   private static final float SUPPLY_BAG_MARKER_HALF_SIZE = 10.2F;
   private static final float GRENADE_MARKER_HALF_SIZE = 14.0F;
   private static final float CLAYMORE_MARKER_HALF_SIZE = 7.2F;
   private static final float BEACON_MARKER_HALF_SIZE = 7.8F;
   private static final float CENTER_OVERLAY_HALF_SIZE = 2.75F;

   private TacticalMarkerRenderer() {
   }

   public static void drawMarker(
      GuiGraphics gui,
      float centerX,
      float centerY,
      float alpha,
      float shapeOpacity,
      float pulse,
      boolean hovered,
      TacticalMarkerRenderer.MarkerVisualType visualType,
      float entryScale,
      float helpPulseProgress,
      String targetClass,
      boolean supportBoxAlert
   ) {
      float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha)) * 1.0F;
      float clampedShapeOpacity = Math.max(0.0F, Math.min(1.0F, shapeOpacity));
      if (!(clampedAlpha <= 0.01F) || !(clampedShapeOpacity <= 0.01F)) {
         float scale = Math.max(0.01F, entryScale) * 0.82F;
         TacticalMarkerRenderer.MarkerVisual visual = TacticalMarkerRenderer.MarkerVisual.resolve(
            visualType == null ? TacticalMarkerRenderer.MarkerVisualType.NORMAL : visualType
         );
         float red = visual.red();
         float green = visual.green();
         float blue = visual.blue();
         float finalAlpha = clampedShapeOpacity * visual.opacityMultiplier();
         if (visual.pulseEffect() && helpPulseProgress >= 0.0F && helpPulseProgress < 1.0F) {
            float emphasis = 1.0F - helpPulseProgress;
            finalAlpha = Math.min(1.0F, finalAlpha + emphasis * 0.22F);
         }

         gui.pose().pushPose();
         gui.pose().translate(centerX, centerY, 0.0F);
         Matrix4f matrix = gui.pose().last().pose();
         RenderSystem.disableDepthTest();
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableCull();
         if (finalAlpha > 0.01F) {
            drawShape(gui, matrix, visual, scale, red, green, blue, finalAlpha, targetClass, supportBoxAlert);
            if (visual.centerOverlayTexture() != null) {
               drawCenterOverlay(gui, matrix, visual.centerOverlayTexture(), scale, Math.min(1.0F, finalAlpha * 0.97F));
            }
         }

         if (visual.pulseEffect() && helpPulseProgress >= 0.0F && helpPulseProgress < 1.0F) {
            float pulseFade = 1.0F - helpPulseProgress;
            float pulseScale = 1.0F + helpPulseProgress * 2.55F;
            float pulseAlpha = pulseFade * 0.85F;
            drawShape(gui, matrix, visual, scale * pulseScale, red, green, blue, pulseAlpha, targetClass, supportBoxAlert);
            float outerPulseScale = 1.28F + helpPulseProgress * 3.15F;
            float outerPulseAlpha = pulseFade * pulseFade * 0.442F;
            drawShape(gui, matrix, visual, scale * outerPulseScale, red, green, blue, outerPulseAlpha, targetClass, supportBoxAlert);
         }

         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         gui.pose().popPose();
      }
   }

   private static void drawShape(
      GuiGraphics gui,
      Matrix4f matrix,
      TacticalMarkerRenderer.MarkerVisual visual,
      float scale,
      float red,
      float green,
      float blue,
      float alpha,
      String targetClass,
      boolean supportBoxAlert
   ) {
      if (visual.shape() == TacticalMarkerRenderer.MarkerShape.AMMO_BOX) {
         float r = 1.0F;
         float g = 1.0F;
         float b = 1.0F;
         if (supportBoxAlert) {
            long time = System.currentTimeMillis();
            float flash = (float)(Math.sin((double)time / 150.0) * 0.5 + 0.5);
            g = flash;
            b = flash;
         }

         drawTextureMarker(gui, matrix, TEX_HUD_AMMO_BOX, scale, r, g, b, alpha, 15.6F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.MEDICAL_BOX) {
         float r = 1.0F;
         float g = 1.0F;
         float b = 1.0F;
         if (supportBoxAlert) {
            long time = System.currentTimeMillis();
            float flash = (float)(Math.sin((double)time / 150.0) * 0.5 + 0.5);
            g = flash;
            b = flash;
         }

         drawTextureMarker(gui, matrix, TEX_HUD_MEDICAL_BOX, scale, r, g, b, alpha, 15.6F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.AMMO_BAG) {
         drawTextureMarker(gui, matrix, TEX_HUD_AMMO_BAG, scale, 1.0F, 1.0F, 1.0F, alpha, 10.2F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.MEDICAL_BAG) {
         drawTextureMarker(gui, matrix, TEX_HUD_MEDIC_BAG, scale, 1.0F, 1.0F, 1.0F, alpha, 10.2F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.GRENADE) {
         float r = 1.0F;
         float g = supportBoxAlert ? resolveFastAlertChannel() : 1.0F;
         float b = supportBoxAlert ? resolveFastAlertChannel() : 1.0F;
         drawTextureMarker(gui, matrix, TEX_HUD_GRENADE, scale, r, g, b, alpha, 14.0F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.CLAYMORE) {
         drawTextureMarker(gui, matrix, TEX_HUD_CLAYMORE_MINE, scale, 1.0F, 1.0F, 1.0F, alpha, 7.2F);
      } else if (visual.shape() == TacticalMarkerRenderer.MarkerShape.BEACON) {
         float r = 1.0F;
         float g = supportBoxAlert ? resolveAlertChannel() : 1.0F;
         float b = supportBoxAlert ? resolveAlertChannel() : 1.0F;
         drawTextureMarker(gui, matrix, TEX_HUD_BEACON, scale, r, g, b, alpha, 7.8F);
      } else {
         if (visual.shape() == TacticalMarkerRenderer.MarkerShape.CIRCLE && targetClass != null && !targetClass.isBlank()) {
            ResourceLocation tex = resolveClassTexture(targetClass, visual.isSquad());
            if (tex != null) {
               drawTextureMarker(gui, matrix, tex, scale, red, green, blue, alpha, 5.0F);
               return;
            }
         }

         switch (visual.shape()) {
            case TRIANGLE:
               float triangleCenterY = 4.0F * scale;
               drawInvertedTriangle(matrix, 0.0F, triangleCenterY, 3.0F * scale, 5.5F * scale, red, green, blue, alpha);
               break;
            case CIRCLE:
               drawFilledCircle(matrix, visual.radius() * scale, red, green, blue, alpha, 28);
               break;
            case DIAMOND:
               drawDiamond(matrix, 0.0F, 0.0F, 3.8F * scale, red, green, blue, alpha);
         }
      }
   }

   private static ResourceLocation resolveClassTexture(String targetClass, boolean isSquad) {
      String lowerClass = targetClass.toLowerCase(Locale.ROOT);

      return switch (lowerClass) {
         case "assault" -> new ResourceLocation("battlefield_ops","textures/gui/class_assault.png");
         case "engineer" -> new ResourceLocation("battlefield_ops","textures/gui/class_engineer.png");
         case "recon" -> new ResourceLocation("battlefield_ops","textures/gui/class_recon.png");
         case "support" -> new ResourceLocation("battlefield_ops","textures/gui/class_support.png");
         default -> null;
      };
   }

   private static float resolveAlertChannel() {
      long time = System.currentTimeMillis();
      return (float)(Math.sin((double)time / 150.0) * 0.5 + 0.5);
   }

   private static float resolveFastAlertChannel() {
      long time = System.currentTimeMillis();
      return (float)(Math.sin((double)time / 80.0) * 0.5 + 0.5);
   }

   private static void drawTextureMarker(
      GuiGraphics gui, Matrix4f matrix, ResourceLocation texture, float scale, float r, float g, float b, float alpha, float halfSizeBase
   ) {
      RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
      RenderSystem.setShaderTexture(0, texture);
      BufferBuilder buffer;
      buffer = Draw.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      float halfSize = halfSizeBase * scale;
      buffer.vertex(matrix, -halfSize, -halfSize, 0.0F).uv(0.0F, 0.0F).color(r, g, b, alpha);
      buffer.vertex(matrix, -halfSize, halfSize, 0.0F).uv(0.0F, 1.0F).color(r, g, b, alpha);
      buffer.vertex(matrix, halfSize, halfSize, 0.0F).uv(1.0F, 1.0F).color(r, g, b, alpha);
      buffer.vertex(matrix, halfSize, -halfSize, 0.0F).uv(1.0F, 0.0F).color(r, g, b, alpha);
      Draw.end(buffer);
   }

   private static void drawCenterOverlay(GuiGraphics gui, Matrix4f matrix, ResourceLocation texture, float scale, float alpha) {
      if (texture != null && !(alpha <= 0.01F)) {
         RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
         RenderSystem.setShaderTexture(0, texture);
         BufferBuilder buffer;
         buffer = Draw.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         float halfSize = 2.75F * scale;
         buffer.vertex(matrix, -halfSize, -halfSize, 0.0F).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha);
         buffer.vertex(matrix, -halfSize, halfSize, 0.0F).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha);
         buffer.vertex(matrix, halfSize, halfSize, 0.0F).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha);
         buffer.vertex(matrix, halfSize, -halfSize, 0.0F).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha);
         Draw.end(buffer);
      }
   }

   private static void drawFilledCircle(Matrix4f matrix, float radius, float red, float green, float blue, float alpha, int segments) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder buffer;
      buffer = Draw.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).color(red, green, blue, alpha);

      for (int i = 0; i <= segments; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)Math.max(segments, 3);
         float x = (float)(Math.cos(angle) * (double)radius);
         float y = (float)(Math.sin(angle) * (double)radius);
         buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha);
      }

      Draw.end(buffer);
   }

   private static void drawInvertedTriangle(
      Matrix4f matrix, float centerX, float centerY, float halfWidth, float height, float red, float green, float blue, float alpha
   ) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder buffer;
      buffer = Draw.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, centerX - halfWidth, centerY - height * 0.5F, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX + halfWidth, centerY - height * 0.5F, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX, centerY + height * 0.5F, 0.0F).color(red, green, blue, alpha);
      Draw.end(buffer);
   }

   private static void drawDiamond(Matrix4f matrix, float centerX, float centerY, float halfDiagonal, float red, float green, float blue, float alpha) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder buffer;
      buffer = Draw.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, centerX, centerY - halfDiagonal, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX + halfDiagonal, centerY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX, centerY + halfDiagonal, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX, centerY - halfDiagonal, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX, centerY + halfDiagonal, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, centerX - halfDiagonal, centerY, 0.0F).color(red, green, blue, alpha);
      Draw.end(buffer);
   }

   private static void drawQuad(Matrix4f matrix, float minX, float minY, float maxX, float maxY, float red, float green, float blue, float alpha) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder buffer;
      buffer = Draw.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      buffer.vertex(matrix, minX, minY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, maxX, minY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, maxX, maxY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, minX, minY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, maxX, maxY, 0.0F).color(red, green, blue, alpha);
      buffer.vertex(matrix, minX, maxY, 0.0F).color(red, green, blue, alpha);
      Draw.end(buffer);
   }

   private static enum MarkerShape {
      DIAMOND,
      TRIANGLE,
      CIRCLE,
      AMMO_BOX,
      MEDICAL_BOX,
      AMMO_BAG,
      MEDICAL_BAG,
      GRENADE,
      CLAYMORE,
      BEACON;
   }

   private static record MarkerVisual(
      TacticalMarkerRenderer.MarkerShape shape,
      float red,
      float green,
      float blue,
      float opacityMultiplier,
      float radius,
      ResourceLocation centerOverlayTexture,
      boolean downed,
      boolean pulseEffect,
      boolean isSquad
   ) {
      private static TacticalMarkerRenderer.MarkerVisual resolve(TacticalMarkerRenderer.MarkerVisualType type) {
         return switch (type) {
            case OBSERVE_GROUND -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.TRIANGLE, 0.96F, 0.54F, 0.12F, 0.9F, 3.0F, null, false, false, false
         );
            case REQUEST_STRIKE -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.DIAMOND, 0.96F, 0.54F, 0.12F, 0.95F, 3.8F, null, false, false, false
         );
            case ENEMY_GROUND -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.DIAMOND, 0.84F, 0.2F, 0.2F, 0.9F, 3.8F, null, false, false, false
         );
            case ENEMY_TRACKED -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.TRIANGLE, 0.69F, 0.1F, 0.1F, 0.9F, 3.0F, null, false, false, false
         );
            case AUTO_ENEMY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.TRIANGLE, 0.69F, 0.1F, 0.1F, 0.6F, 3.0F, null, false, false, false
         );
            case ALLY_PERSISTENT -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.40F, 0.72F, 0.98F, 0.5F, 2.55F, null, false, true, false
         );
            case SQUAD_PERSISTENT -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.16F, 0.88F, 1.0F, 0.5F, 2.55F, null, false, true, true
         );
            case DOWNED_ALLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.16F, 0.88F, 1.0F, 0.85F, 4.15F, TacticalMarkerRenderer.TEX_DOWNED_MEDICAL, true, true, false
         );
            case DOWNED_SQUAD -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.16F, 0.88F, 1.0F, 0.85F, 4.15F, TacticalMarkerRenderer.TEX_DOWNED_MEDICAL, true, true, true
         );
            case RESCUE_ALLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.DIAMOND, 0.16F, 0.88F, 1.0F, 0.9F, 3.8F, null, false, false, false
         );
            case RESCUE_SQUAD -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.DIAMOND, 0.16F, 0.88F, 1.0F, 0.9F, 3.8F, null, false, false, false
         );
            case NEED_AMMO_ALLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.16F, 0.88F, 1.0F, 0.85F, 4.15F, TacticalMarkerRenderer.TEX_NEED_AMMO, false, true, false
         );
            case NEED_AMMO_SQUAD -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CIRCLE, 0.16F, 0.88F, 1.0F, 0.85F, 4.15F, TacticalMarkerRenderer.TEX_NEED_AMMO, false, true, true
         );
            case AMMO_BOX -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.AMMO_BOX, 1.0F, 1.0F, 1.0F, 0.85F, 2.55F, null, false, false, false
         );
            case MEDICAL_BOX -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.MEDICAL_BOX, 1.0F, 1.0F, 1.0F, 0.85F, 2.55F, null, false, false, false
         );
            case AMMO_BAG -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.AMMO_BAG, 1.0F, 1.0F, 1.0F, 0.9F, 2.55F, null, false, false, false
         );
            case MEDICAL_BAG -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.MEDICAL_BAG, 1.0F, 1.0F, 1.0F, 0.9F, 2.55F, null, false, false, false
         );
            case GRENADE_SELF -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.GRENADE, 1.0F, 1.0F, 1.0F, 0.96F, 2.55F, null, false, false, false
         );
            case GRENADE_FRIENDLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.GRENADE, 1.0F, 1.0F, 1.0F, 0.92F, 2.55F, null, false, false, false
         );
            case GRENADE_HOSTILE -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.GRENADE, 1.0F, 1.0F, 1.0F, 0.96F, 2.55F, null, false, false, false
         );
            case CLAYMORE_FRIENDLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.CLAYMORE, 1.0F, 1.0F, 1.0F, 0.92F, 2.55F, null, false, false, false
         );
            case BEACON_FRIENDLY -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.BEACON, 1.0F, 1.0F, 1.0F, 0.92F, 2.55F, null, false, false, false
         );
            case BEACON_HOSTILE -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.BEACON, 1.0F, 1.0F, 1.0F, 0.96F, 2.55F, null, false, false, false
         );
            case NORMAL -> new TacticalMarkerRenderer.MarkerVisual(
            TacticalMarkerRenderer.MarkerShape.DIAMOND, 0.95F, 0.82F, 0.2F, 0.9F, 3.8F, null, false, false, false
         );
         };
      }
   }

   public static enum MarkerVisualType {
      NORMAL,
      OBSERVE_GROUND,
      REQUEST_STRIKE,
      ENEMY_GROUND,
      ENEMY_TRACKED,
      AUTO_ENEMY,
      ALLY_PERSISTENT,
      SQUAD_PERSISTENT,
      DOWNED_ALLY,
      DOWNED_SQUAD,
      RESCUE_ALLY,
      RESCUE_SQUAD,
      NEED_AMMO_ALLY,
      NEED_AMMO_SQUAD,
      AMMO_BOX,
      MEDICAL_BOX,
      AMMO_BAG,
      MEDICAL_BAG,
      GRENADE_SELF,
      GRENADE_FRIENDLY,
      GRENADE_HOSTILE,
      CLAYMORE_FRIENDLY,
      BEACON_FRIENDLY,
      BEACON_HOSTILE;
   }
}
