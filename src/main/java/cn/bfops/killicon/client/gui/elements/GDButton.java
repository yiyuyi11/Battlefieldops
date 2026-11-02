package cn.bfops.killicon.client.gui.elements;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import cn.bfops.killicon.client.gui.GuiConstants;

public class GDButton {
   private int x;
   private int y;
   private int width;
   private int height;
   private Component message;
   private final Consumer<GDButton> onPress;
   private final Minecraft minecraft;
   private int textColor = -1;
   public boolean active = true;
   private float hoverProgress = 0.0F;
   private long lastTime;
   private GDTextRenderer textRenderer;

   public GDButton(int x, int y, int width, int height, Component message, Consumer<GDButton> onPress) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.message = message;
      this.onPress = onPress;
      this.minecraft = Minecraft.getInstance();
      this.lastTime = System.currentTimeMillis();
      int textY = y + (height - 9) / 2;
      this.textRenderer = new GDTextRenderer(message.getString(), x, textY, x + width, textY + 9, 1.0F, this.textColor, false);
      this.textRenderer.setCentered(true);
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      long now = System.currentTimeMillis();
      float dt = (float)(now - this.lastTime) / 200.0F;
      this.lastTime = now;
      boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
      if (hovered) {
         this.hoverProgress = Math.min(1.0F, this.hoverProgress + dt);
      } else {
         this.hoverProgress = Math.max(0.0F, this.hoverProgress - dt);
      }

      guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, 1296319556);
      this.textRenderer.render(guiGraphics, partialTick);
      if (this.hoverProgress > 0.001F) {
         float ease = 1.0F - (float)Math.pow((double)(1.0F - this.hoverProgress), 3.0);
         float floatBarWidth = (float)this.width * ease;
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate((float)this.x, (float)(this.y + this.height) - 1.0F, 0.0F);
         guiGraphics.pose().scale(floatBarWidth, 1.0F, 1.0F);
         guiGraphics.fill(0, 0, 1, 1, GuiConstants.COLOR_GOLD);
         guiGraphics.pose().popPose();
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.active
         && button == 0
         && mouseX >= (double)this.x
         && mouseX <= (double)(this.x + this.width)
         && mouseY >= (double)this.y
         && mouseY <= (double)(this.y + this.height)) {
         this.onPress.accept(this);
         return true;
      } else {
         return false;
      }
   }

   public void setX(int x) {
      this.x = x;
      this.updateTextRendererBounds();
   }

   public void setY(int y) {
      this.y = y;
      this.updateTextRendererBounds();
   }

   public void setWidth(int width) {
      this.width = width;
      this.updateTextRendererBounds();
   }

   public void setHeight(int height) {
      this.height = height;
      this.updateTextRendererBounds();
   }

   public void setMessage(Component message) {
      this.message = message;
      if (this.textRenderer != null) {
         this.textRenderer.setText(message.getString());
      }
   }

   public void setTextColor(int color) {
      this.textColor = color;
      if (this.textRenderer != null) {
         this.textRenderer.setColor(color);
      }
   }

   private void updateTextRendererBounds() {
      if (this.textRenderer != null) {
         int textY = this.y + (this.height - 9) / 2;
         this.textRenderer.setX1(this.x);
         this.textRenderer.setY1(textY);
         this.textRenderer.setX2(this.x + this.width);
         this.textRenderer.setY2(textY + 9);
      }
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

   public Component getMessage() {
      return this.message;
   }
}
