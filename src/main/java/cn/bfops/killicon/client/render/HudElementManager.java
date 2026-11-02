package cn.bfops.killicon.client.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;


public class HudElementManager {
   private static final Map<String, Map<String, IHudRenderer>> renderers = new HashMap<>();

   public static void register(String category, String name, IHudRenderer renderer) {
      renderers.computeIfAbsent(category, k -> new HashMap<>()).put(name, renderer);
   }

   public static void trigger(String category, String name, IHudRenderer.TriggerContext context) {
      Map<String, IHudRenderer> categoryMap = renderers.get(category);
      if (categoryMap != null) {
         IHudRenderer renderer = categoryMap.get(name);
         if (renderer != null) {
            renderer.trigger(context);
         }
      }
   }

   public static void clearAllPreviews() {
      for (Map<String, IHudRenderer> categoryMap : renderers.values()) {
         for (IHudRenderer renderer : categoryMap.values()) {
            renderer.resetPreview();
         }
      }
   }

   public static void init() {
      // Registered once through Battlefield Ops client lifecycle.
   }

   public static void onRenderGuiOverlay(GuiGraphics guiGraphics, float partialTick, boolean isMainOverlayPass) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen == null || mc.screen instanceof ChatScreen) {
         if (isMainOverlayPass) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            try {
               int screenWidth = mc.getWindow().getGuiScaledWidth();
               int screenHeight = mc.getWindow().getGuiScaledHeight();
               String presetId = ConfigManager.getCurrentPresetId();

               for (Entry<String, Map<String, IHudRenderer>> categoryEntry : renderers.entrySet()) {
                  String category = categoryEntry.getKey();

                  for (Entry<String, IHudRenderer> rendererEntry : categoryEntry.getValue().entrySet()) {
                     String elementId = category + "/" + rendererEntry.getKey();
                     IHudRenderer renderer = rendererEntry.getValue();
                     JsonObject config = ElementConfigManager.getElementConfig(presetId, elementId);
                     if(config==null||(config.has("visible")&&!config.get("visible").getAsBoolean()))continue;
                     float rotationAngle = config != null && config.has("rotation_angle") ? config.get("rotation_angle").getAsFloat() : 0.0F;
                     if (Math.abs(rotationAngle) <= 0.001F) {
                        renderer.render(guiGraphics, partialTick);
                     } else {
                        String screenAnchor = config != null && config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
                        int xOffset = config != null && config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
                        int yOffset = config != null && config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
                        float pivotX = (float)ScreenAnchor.resolveCenterX(screenAnchor, xOffset, screenWidth);
                        float pivotY = (float)ScreenAnchor.resolveCenterY(screenAnchor, yOffset, screenHeight);
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(pivotX, pivotY, 0.0F);
                        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationAngle));
                        guiGraphics.pose().translate(-pivotX, -pivotY, 0.0F);
                        renderer.render(guiGraphics, partialTick);
                        guiGraphics.pose().popPose();
                     }
                  }
               }
            } finally {
               guiGraphics.flush();
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
               RenderSystem.disableBlend();
            }
         }
      }
   }
}
