package cn.bfops.killicon.client.gui.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import cn.bfops.killicon.client.gui.GuiConstants;

public class ColorPickerDialog {
   private final Minecraft minecraft;
   private Consumer<String> onConfirm;
   private Runnable onCancel;
   private boolean visible = false;
   private String title = "选择颜色";
   private String initialHex = "#FFFFFF";
   private float h = 0.0F;
   private float s = 0.0F;
   private float v = 1.0F;
   private int currentRGB = -1;
   private static final int PANEL_WIDTH = 140;
   private static final int BUTTON_HEIGHT = 17;
   private static final int PREVIEW_HEIGHT = 17;
   private static final int HUE_BAR_WIDTH = 17;
   private static final int PAD = 8;
   private static final int SV_BOX_SIZE = 115;
   private static final int PANEL_HEIGHT = 165;
   private boolean isDraggingSV = false;
   private boolean isDraggingHue = false;
   private float svHoverProgress = 0.0F;
   private float hueHoverProgress = 0.0F;
   private long lastFrameTime;
   private GDButton confirmButton;
   private GDButton cancelButton;
   private GDTextRenderer previewTextRenderer;
   private GDTextRenderer titleRenderer;

   public ColorPickerDialog(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public void show(String initialHex, String title, Consumer<String> onConfirm, Runnable onCancel) {
      this.initialHex = initialHex;
      this.title = title;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
      this.visible = true;
      this.lastFrameTime = System.currentTimeMillis();
      this.svHoverProgress = 0.0F;
      this.hueHoverProgress = 0.0F;
      this.parseHex(initialHex);
      int w1 = 69;
      int w2 = 139 - w1;
      this.confirmButton = new GDButton(
         0, 0, w2, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.confirm", new Object[0])), btn -> this.confirm()
      );
      this.cancelButton = new GDButton(
         0, 0, w1, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.cancel", new Object[0])), btn -> this.cancel()
      );
      this.previewTextRenderer = new GDTextRenderer("§l" + this.formatHex(this.currentRGB), 0, 0, 0, 0, 1.0F, -1, false);
      this.previewTextRenderer.setCentered(true);
      if (this.titleRenderer == null) {
         this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0F, GuiConstants.COLOR_GOLD, false);
      } else {
         this.titleRenderer.setText(title);
         this.titleRenderer.setColor(GuiConstants.COLOR_GOLD);
      }
   }

   private void parseHex(String hex) {
      try {
         if (hex.startsWith("#")) {
            hex = hex.substring(1);
         }

         if (hex.length() == 3) {
            StringBuilder sb = new StringBuilder();

            for (char c : hex.toCharArray()) {
               sb.append(c).append(c);
            }

            hex = sb.toString();
         }

         int rgb = Integer.parseInt(hex, 16);
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         float[] hsv = Color.RGBtoHSB(r, g, b, null);
         this.h = hsv[0];
         this.s = hsv[1];
         this.v = hsv[2];
         this.updateCurrentRGB();
      } catch (Exception var7) {
         this.h = 0.0F;
         this.s = 0.0F;
         this.v = 1.0F;
         this.updateCurrentRGB();
      }
   }

   private void updateCurrentRGB() {
      this.currentRGB = Color.HSBtoRGB(this.h, this.s, this.v);
      if (this.previewTextRenderer != null) {
         this.previewTextRenderer.setText("§l" + this.formatHex(this.currentRGB));
         int textColor = this.v > 0.5F ? -16777216 : -1;
         this.previewTextRenderer.setColor(textColor);
      }
   }

   private String formatHex(int rgb) {
      return String.format("#%06X", 16777215 & rgb);
   }

   private void confirm() {
      if (this.onConfirm != null) {
         this.onConfirm.accept(this.formatHex(this.currentRGB));
      }

      this.visible = false;
   }

   private void cancel() {
      if (this.onCancel != null) {
         this.onCancel.run();
      }

      this.visible = false;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.visible) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int dimColor = -2008791996;
         guiGraphics.fill(0, 0, screenWidth, screenHeight, dimColor);
         int centerX = (screenWidth - 140) / 2;
         int centerY = (screenHeight - 165) / 2 + 20;
         int fontHeight = 9;
         int titleY = centerY - fontHeight - 8 + 6;
         int buttonsY = centerY + 165 - 17;
         int containerTop = titleY - 8;
         int containerLeft = centerX - 8;
         int containerRight = centerX + 140 + 8;
         int containerBottom = buttonsY + 17 + 8;
         guiGraphics.fill(containerLeft, containerTop, containerRight, containerBottom, 1296319556);
         if (this.titleRenderer != null) {
            this.titleRenderer.setX1(centerX);
            this.titleRenderer.setY1(titleY);
            this.titleRenderer.setX2(centerX + 140);
            this.titleRenderer.setY2(titleY + fontHeight);
            this.titleRenderer.render(guiGraphics, partialTick);
         }

         int btnRightX = centerX + 69 + 1;
         int previewY = buttonsY - 8 - 17;
         int previewW = 140;
         int hueX = centerX + 140 - 17;
         int hueY = previewY - 8 - 115;
         int hueH = 115;
         int svW = 115;
         int svH = 115;
         long currentTime = System.currentTimeMillis();
         float dt = (float)(currentTime - this.lastFrameTime) / 1000.0F;
         this.lastFrameTime = currentTime;
         boolean isHoveringSV = mouseX >= centerX && mouseX <= centerX + svW && mouseY >= hueY && mouseY <= hueY + svH;
         boolean isHoveringHue = mouseX >= hueX && mouseX <= hueX + 17 && mouseY >= hueY && mouseY <= hueY + hueH;
         if (isHoveringSV) {
            this.svHoverProgress = Math.min(1.0F, this.svHoverProgress + dt * 2.0F);
         } else {
            this.svHoverProgress = Math.max(0.0F, this.svHoverProgress - dt * 2.0F);
         }

         if (isHoveringHue) {
            this.hueHoverProgress = Math.min(1.0F, this.hueHoverProgress + dt * 2.0F);
         } else {
            this.hueHoverProgress = Math.max(0.0F, this.hueHoverProgress - dt * 2.0F);
         }

         this.renderSVBox(guiGraphics, centerX, hueY, svW, svH);
         this.renderHoverTrail(guiGraphics, centerX, hueY, svW, svH, this.svHoverProgress);
         this.renderHueBar(guiGraphics, hueX, hueY, 17, hueH);
         this.renderHoverTrail(guiGraphics, hueX, hueY, 17, hueH, this.hueHoverProgress);
         int selX = centerX + (int)(this.s * (float)svW);
         int selY = hueY + (int)((1.0F - this.v) * (float)svH);
         selX = Math.max(centerX, Math.min(centerX + svW, selX));
         selY = Math.max(hueY, Math.min(hueY + svH, selY));
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, 0.0F, 1.0F);
         guiGraphics.fill(selX - 2, selY - 2, selX + 2, selY + 2, -1);
         guiGraphics.fill(selX - 1, selY - 1, selX + 1, selY + 1, -16777216);
         guiGraphics.pose().popPose();
         int hueSelY = hueY + (int)(this.h * (float)hueH);
         hueSelY = Math.max(hueY, Math.min(hueY + hueH, hueSelY));
         guiGraphics.fill(hueX - 1, hueSelY - 1, hueX + 17 + 1, hueSelY + 1, -1);
         guiGraphics.fill(hueX, hueSelY, hueX + 17, hueSelY, -16777216);
         guiGraphics.fill(centerX, previewY, centerX + previewW, previewY + 17, 0xFF000000 | this.currentRGB);
         if (this.previewTextRenderer != null) {
            this.previewTextRenderer.setX1(centerX);
            this.previewTextRenderer.setY1(previewY + 4 + 1);
            this.previewTextRenderer.setX2(centerX + previewW);
            this.previewTextRenderer.setY2(previewY + 17);
            this.previewTextRenderer.render(guiGraphics, partialTick);
         }

         if (this.cancelButton != null) {
            this.cancelButton.setX(centerX);
            this.cancelButton.setY(buttonsY);
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.confirmButton != null) {
            this.confirmButton.setX(btnRightX);
            this.confirmButton.setY(buttonsY);
            this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.isDraggingSV) {
            float newS = (float)(mouseX - centerX) / (float)svW;
            float newV = 1.0F - (float)(mouseY - hueY) / (float)svH;
            this.s = Math.max(0.0F, Math.min(1.0F, newS));
            this.v = Math.max(0.0F, Math.min(1.0F, newV));
            this.updateCurrentRGB();
         }

         if (this.isDraggingHue) {
            float newH = (float)(mouseY - hueY) / (float)hueH;
            this.h = Math.max(0.0F, Math.min(1.0F, newH));
            this.updateCurrentRGB();
         }

         guiGraphics.pose().popPose();
      }
   }

   private void renderSVBox(GuiGraphics guiGraphics, int x, int y, int w, int h) {
      int hueColor = Color.HSBtoRGB(this.h, 1.0F, 1.0F);
      int r = hueColor >> 16 & 0xFF;
      int g = hueColor >> 8 & 0xFF;
      int b = hueColor & 0xFF;
      Matrix4f matrix = guiGraphics.pose().last().pose();
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder bufferbuilder;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      bufferbuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      bufferbuilder.addVertex(matrix, (float)x, (float)(y + h), 0.0F).setColor(0, 0, 0, 255);
      bufferbuilder.addVertex(matrix, (float)(x + w), (float)(y + h), 0.0F).setColor(0, 0, 0, 255);
      bufferbuilder.addVertex(matrix, (float)(x + w), (float)y, 0.0F).setColor(r, g, b, 255);
      bufferbuilder.addVertex(matrix, (float)x, (float)y, 0.0F).setColor(255, 255, 255, 255);
      BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
      RenderSystem.disableBlend();
   }

   private void renderHueBar(GuiGraphics guiGraphics, int x, int y, int w, int h) {
      int step = 1;

      for (int i = 0; i < h; i += step) {
         float hue = (float)i / (float)h;
         int color = Color.HSBtoRGB(hue, 1.0F, 1.0F);
         guiGraphics.fill(x, y + i, x + w, y + i + step, 0xFF000000 | color);
      }
   }

   private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h, float progress) {
      if (!(progress <= 0.001F)) {
         int color = GuiConstants.COLOR_GOLD;
         float totalLength = (float)(w + h + w + h);
         float currentLength = totalLength * this.easeOut(progress);
         float drawn = 0.0F;
         if (currentLength > 0.0F) {
            float segLen = Math.min((float)w, currentLength);
            guiGraphics.fill(x, y + h - 1, x + (int)segLen, y + h, color);
            drawn += (float)w;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)h, rem);
            guiGraphics.fill(x + w - 1, y + h - (int)segLen, x + w, y + h, color);
            drawn += (float)h;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)w, rem);
            guiGraphics.fill(x + w - (int)segLen, y, x + w, y + 1, color);
            drawn += (float)w;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)h, rem);
            guiGraphics.fill(x, y, x + 1, y + (int)segLen, color);
         }
      }
   }

   private float easeOut(float t) {
      return 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (this.confirmButton != null && this.confirmButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int centerX = (screenWidth - 140) / 2;
         int centerY = (screenHeight - 165) / 2 + 20;
         int buttonsY = centerY + 165 - 17;
         int previewY = buttonsY - 8 - 17;
         int hueY = previewY - 8 - 115;
         int hueX = centerX + 140 - 17;
         if (mouseX >= (double)centerX && mouseX <= (double)(centerX + 115) && mouseY >= (double)hueY && mouseY <= (double)(hueY + 115)) {
            this.isDraggingSV = true;
            return true;
         } else if (mouseX >= (double)hueX && mouseX <= (double)(hueX + 17) && mouseY >= (double)hueY && mouseY <= (double)(hueY + 115)) {
            this.isDraggingHue = true;
            return true;
         } else {
            return true;
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDraggingSV = false;
      this.isDraggingHue = false;
      return false;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      return this.visible;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         this.cancel();
         return true;
      } else if (keyCode != 257 && keyCode != 335) {
         return true;
      } else {
         this.confirm();
         return true;
      }
   }
}
