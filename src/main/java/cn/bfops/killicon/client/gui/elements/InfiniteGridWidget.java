package cn.bfops.killicon.client.gui.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.textures.ModTextures;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class InfiniteGridWidget {
   private int x;
   private int y;
   private int width;
   private int height;
   private double viewX = 0.0;
   private double viewY = 0.0;
   private int gridSize = 20;
   private boolean isDragging = false;
   private double lastMouseX;
   private double lastMouseY;
   private static final int GRID_COLOR = 1084926634;
   private static final int CROSS_COLOR = -1867863382;
   private static final int TEXT_COLOR = -5592406;
   private static final int BORDER_COLOR = -2136298838;
   private static final int ICON_SIZE = 64;

   public InfiniteGridWidget(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.viewX = (double)x + (double)width / 2.0;
      this.viewY = (double)y + (double)height / 2.0;
   }

   public void setBounds(int x, int y, int width, int height) {
      double dx = (double)(x - this.x);
      double dy = (double)(y - this.y);
      double dw = (double)(width - this.width);
      double dh = (double)(height - this.height);
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.viewX += dx + dw / 2.0;
      this.viewY += dy + dh / 2.0;
   }

   public float getOriginX() {
      return (float)this.viewX;
   }

   public float getOriginY() {
      return (float)this.viewY;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, List<InfiniteGridWidget.ScrollingIcon> icons) {
      guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + 1, -2136298838);
      guiGraphics.fill(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, -2136298838);
      guiGraphics.fill(this.x, this.y, this.x + 1, this.y + this.height, -2136298838);
      guiGraphics.fill(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, -2136298838);
      guiGraphics.enableScissor(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1);
      int startCol = (int)Math.floor(((double)this.x - this.viewX) / (double)this.gridSize);
      int endCol = (int)Math.ceil(((double)(this.x + this.width) - this.viewX) / (double)this.gridSize);
      int startRow = (int)Math.floor(((double)this.y - this.viewY) / (double)this.gridSize);
      int endRow = (int)Math.ceil(((double)(this.y + this.height) - this.viewY) / (double)this.gridSize);
      double baseViewX = Math.floor(this.viewX);
      double baseViewY = Math.floor(this.viewY);
      float subPixelX = (float)(this.viewX - baseViewX);
      float subPixelY = (float)(this.viewY - baseViewY);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(subPixelX, subPixelY, 0.0F);

      for (int i = startCol; i <= endCol; i++) {
         int lineX = (int)(baseViewX + (double)(i * this.gridSize));
         int lineColor = i == 0 ? -1867863382 : 1084926634;
         guiGraphics.fill(lineX, this.y, lineX + 1, this.y + this.height, lineColor);
      }

      for (int j = startRow; j <= endRow; j++) {
         int lineY = (int)(baseViewY + (double)(j * this.gridSize));
         int lineColor = j == 0 ? -1867863382 : 1084926634;
         guiGraphics.fill(this.x, lineY, this.x + this.width, lineY + 1, lineColor);
      }

      guiGraphics.pose().popPose();
      if (icons != null && !icons.isEmpty()) {
         for (InfiniteGridWidget.ScrollingIcon icon : icons) {
            float iconX = (float)(this.viewX + icon.gridX);
            float iconY = (float)(this.viewY + icon.gridY);
            String texturePath = getTexturePath(icon.killType);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(iconX, iconY, 0.0F);
            guiGraphics.pose().translate(-32.0F, -32.0F, 0.0F);
            guiGraphics.blit(ModTextures.get(texturePath), 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
            guiGraphics.pose().popPose();
         }

         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      guiGraphics.pose().pushPose();
      float scale = 0.5F;
      guiGraphics.pose().translate(subPixelX, subPixelY, 0.0F);
      guiGraphics.pose().scale(scale, scale, 1.0F);
      Minecraft mc = Minecraft.getInstance();

      for (int i = startCol; i <= endCol; i++) {
         for (int j = startRow; j <= endRow; j++) {
            int px = (int)(baseViewX + (double)(i * this.gridSize));
            int py = (int)(baseViewY + (double)(j * this.gridSize));
            if (px >= this.x && px <= this.x + this.width && py >= this.y && py <= this.y + this.height) {
               String coordText = i * this.gridSize + "," + j * this.gridSize;
               int textWidth = mc.font.width(coordText);
               float drawX = (float)(px + 2) / scale;
               float drawY = (float)(py + 2) / scale;
               guiGraphics.drawString(mc.font, coordText, (int)drawX, (int)drawY, -5592406, false);
            }
         }
      }

      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
   }

   private static String getTexturePath(int killType) {
      String presetId = ConfigManager.getCurrentPresetId();
      String textureKey = getTextureKey(killType);
      return ElementTextureDefinition.getSelectedTextureFileName(
         presetId, "kill_icon/scrolling", textureKey, ElementConfigManager.getElementConfig(presetId, "kill_icon/scrolling")
      );
   }

   private static String getTextureKey(int killType) {
      return KillTypeRegistry.get(killType).textureKey();
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
         this.isDragging = true;
         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         boolean wasDragging = this.isDragging;
         this.isDragging = false;
         return wasDragging;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isDragging && button == 0) {
         double dx = mouseX - this.lastMouseX;
         double dy = mouseY - this.lastMouseY;
         this.viewX += dx;
         this.viewY += dy;
         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
         return true;
      } else {
         return false;
      }
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
   }

   public static final class ScrollingIcon {
      private final int killType;
      private final double gridX;
      private final double gridY;

      public ScrollingIcon(int killType) {
         this(killType, 0.0, 0.0);
      }

      public ScrollingIcon(int killType, double gridX, double gridY) {
         this.killType = killType;
         this.gridX = gridX;
         this.gridY = gridY;
      }
   }
}
