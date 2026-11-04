package cn.bfops.killicon.client.render.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.render.IHudRenderer;

public class AceLogoRenderer implements IHudRenderer {
   @Override public void resetPreview(){isVisible=false;startTime=-1;}

   private static final ResourceLocation TEXTURE = new ResourceLocation("battlefield_ops", "icon/ace_logo.png");
   private static final long DISPLAY_DURATION_MS = 10000L;
   private static final long FADE_OUT_DURATION_MS = 600L;
   private static final int LOGO_WIDTH = 74;
   private static final int LOGO_HEIGHT = 25;
   private long startTime = -1L;
   private boolean isVisible = false;

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      if (this.isVisible && this.startTime >= 0L) {
         long now = System.currentTimeMillis();
         long elapsed = now - this.startTime;
         if (elapsed >= 10600L) {
            this.isVisible = false;
            this.startTime = -1L;
         } else {
            float alpha = 1.0F;
            if (elapsed > 10000L) {
               float t = (float)(elapsed - 10000L) / 600.0F;
               t = Mth.clamp(t, 0.0F, 1.0F);
               float fade = 1.0F - t;
               alpha = fade * fade;
            }

            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int drawX = screenWidth - 74;
            int drawY = screenHeight - 25;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            guiGraphics.blit(TEXTURE, drawX, drawY, 0.0F, 0.0F, 74, 25, 74, 25);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
         }
      }
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      if (ClientConfigManager.isEnableAceLag()) {
         this.startTime = System.currentTimeMillis();
         this.isVisible = true;
      }
   }
}
