package cn.bfops.killicon.client.gui.elements;

import com.google.gson.JsonObject;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.render.ScreenAnchor;

public class ElementPreview {
   private final String elementId;
   private int x;
   private int y;
   private int width;
   private int height;
   private float hoverProgress = 0.0F;
   private float clickProgress = 0.0F;
   private boolean isPressed = false;
   private float rotateHandleProgress = 0.0F;
   private long lastTime;
   private boolean visible = true;
   private boolean dragging = false;
   private int dragOffsetX = 0;
   private int dragOffsetY = 0;
   private boolean externalHover = false;
   private float scale = 1.0F;
   private int xOffset = 0;
   private int yOffset = 0;
   private float rotationAngle = 0.0F;
   private String screenAnchor = "bottom_center";
   private float scaleWeapon = 1.0F;
   private float scaleVictim = 1.2F;
   private float scaleHealth = 1.5F;
   private int borderSize = 3;
   private int bf1MinX = 0;
   private int bf1MinY = 0;
   private boolean alignLeft = false;
   private boolean alignRight = false;
   private int zIndex = 0;
   private ElementPreview.ResizeHandle currentHandle = ElementPreview.ResizeHandle.NONE;
   private float dragStartScale;
   private double dragStartDist;
   private int dragCenterX;
   private int dragCenterY;
   private long lastClickTime = 0L;
   private static final int HANDLE_SIZE = 3;
   private static final int HANDLE_HIT_RADIUS = 5;
   private static final int HANDLE_VISIBILITY_RADIUS = 60;
   private static final int ROTATE_HANDLE_OFFSET = 10;
   private GDTextRenderer elementIdRenderer;
   private GDTextRenderer elementNameRenderer;
   private GDTextRenderer xCoordRenderer;
   private GDTextRenderer yCoordRenderer;
   private GDTextRenderer scaleRenderer;
   private GDTextRenderer rotationRenderer;

   public int getZIndex() {
      return this.zIndex;
   }

   private void updateZIndex() {
      if ("kill_icon/card_bar".equals(this.elementId)) {
         this.zIndex = 40;
      } else if (this.elementId.startsWith("subtitle/")) {
         this.zIndex = 20;
      } else {
         this.zIndex = 30;
      }
   }

   public ElementPreview(String elementId) {
      this.elementId = elementId;
      this.lastTime = System.currentTimeMillis();
   }

   public void updateConfig(JsonObject config) {
      if (config == null) {
         this.visible = false;
      } else {
         this.visible = config.has("visible") ? config.get("visible").getAsBoolean() : true;
         this.scale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
         this.xOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.yOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
         this.rotationAngle = config.has("rotation_angle") ? config.get("rotation_angle").getAsFloat() : 0.0F;
         this.screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         if ("kill_icon/battlefield1".equals(this.elementId)) {
            this.scaleWeapon = config.has("scale_weapon") ? config.get("scale_weapon").getAsFloat() : 1.0F;
            this.scaleVictim = config.has("scale_victim") ? config.get("scale_victim").getAsFloat() : 1.2F;
            this.scaleHealth = config.has("scale_health") ? config.get("scale_health").getAsFloat() : 1.5F;
            this.borderSize = config.has("border_size") ? config.get("border_size").getAsInt() : 3;
         } else if ("subtitle/score".equals(this.elementId)) {
            this.alignLeft = config.has("align_left") ? config.get("align_left").getAsBoolean() : false;
            this.alignRight = config.has("align_right") ? config.get("align_right").getAsBoolean() : false;
         } else if ("subtitle/bonus_list".equals(this.elementId)) {
            this.alignLeft = config.has("align_left") ? config.get("align_left").getAsBoolean() : false;
            this.alignRight = config.has("align_right") ? config.get("align_right").getAsBoolean() : false;
         }

         this.recalculateSize();
         this.updateZIndex();
      }
   }

   private void recalculateSize() {
      if ("kill_icon/battlefield1".equals(this.elementId)) {
         this.calculateBattlefield1Size();
      } else if ("subtitle/kill_feed".equals(this.elementId)) {
         this.width = (int)(this.scale * 120.0F);
         this.height = (int)(this.scale * 10.0F);
      } else if ("subtitle/score".equals(this.elementId)) {
         this.width = (int)(this.scale * 15.0F);
         this.height = (int)(this.scale * 6.0F) + 4;
      } else if ("subtitle/bonus_list".equals(this.elementId)) {
         this.width = (int)(this.scale * 40.0F);
         this.height = (int)(this.scale * 25.0F);
      } else if ("kill_icon/scrolling".equals(this.elementId)) {
         int size = (int)(this.scale * 70.0F);
         this.width = size;
         this.height = size;
      } else if ("kill_icon/honor".equals(this.elementId)) {
         this.width = (int)(this.scale * 35.0F);
         this.height = (int)(this.scale * 20.0F);
      } else if ("subtitle/hit_info".equals(this.elementId)) {
         int size = Math.max(4, (int)(this.scale * 70.0F / 3.0F));
         this.width = size;
         this.height = size;
      } else if ("subtitle/combo".equals(this.elementId)) {
         this.width = (int)(this.scale * 30.0F);
         this.height = (int)(this.scale * 10.0F);
      } else if ("kill_icon/combo".equals(this.elementId)) {
         int size = (int)(this.scale * 55.0F);
         this.width = size;
         this.height = size;
      } else if ("kill_icon/valorant".equals(this.elementId)) {
         int size = (int)(this.scale * 90.0F);
         this.width = size;
         this.height = size;
      } else if ("kill_icon/card_bar".equals(this.elementId)) {
         this.width = (int)(this.scale * 300.0F);
         this.height = (int)(this.scale * 40.0F);
      } else if ("kill_icon/card".equals(this.elementId)) {
         this.width = (int)(this.scale * 240.0F);
         this.height = (int)(this.scale * 333.0F);
      } else {
         int size = (int)(this.scale * 60.0F);
         this.width = size;
         this.height = size;
      }
   }

   private void calculateBattlefield1Size() {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      if (font != null) {
         String weaponName = "HK416A5 突击步枪";
         String victimName = "-六五六-";
         String healthText = "20";
         int weaponW = font.width(weaponName);
         int victimW = font.width(victimName);
         int healthW = font.width(healthText);
         int fontH = 9;
         float effWeaponW = (float)weaponW * this.scaleWeapon * this.scale;
         float effWeaponH = (float)fontH * this.scaleWeapon * this.scale;
         float effVictimW = (float)victimW * this.scaleVictim * this.scale;
         float effVictimH = (float)fontH * this.scaleVictim * this.scale;
         float effHealthW = (float)healthW * this.scaleHealth * this.scale;
         float effHealthH = (float)fontH * this.scaleHealth * this.scale;
         float weaponX = -effWeaponW / 2.0F;
         float weaponY = -effWeaponH / 2.0F;
         float weaponRight = weaponX + effWeaponW;
         float weaponBottom = weaponY + effWeaponH;
         float victimX = weaponRight - effVictimW;
         float victimBottom = weaponY - (float)this.borderSize;
         float victimY = victimBottom - effVictimH;
         float healthX = weaponRight + (float)this.borderSize;
         float midY = (victimY + weaponBottom) / 2.0F;
         float healthY = midY - effHealthH / 2.0F;
         float healthRight = healthX + effHealthW;
         float healthBottom = healthY + effHealthH;
         float subBoxRight = Math.max(weaponRight, Math.max(healthRight, weaponRight)) + (float)this.borderSize;
         float subBoxTop = Math.min(weaponY, Math.min(healthY, victimY)) - (float)this.borderSize;
         float subBoxLeft = Math.min(weaponX, Math.min(healthX, victimX)) - (float)this.borderSize;
         float subBoxBottom = Math.max(weaponBottom, Math.max(healthBottom, victimBottom)) + (float)this.borderSize;
         float subBoxH = subBoxBottom - subBoxTop;
         float iconBoxLeft = subBoxLeft - subBoxH;
         float iconBoxBottom = subBoxTop + subBoxH;
         this.width = (int)(subBoxRight - iconBoxLeft);
         this.height = (int)(subBoxBottom - subBoxTop);
         this.bf1MinX = (int)iconBoxLeft;
         this.bf1MinY = (int)subBoxTop;
      }
   }

   public void updatePosition(int screenWidth, int screenHeight) {
      int centerX = ScreenAnchor.resolveCenterX(this.screenAnchor, this.xOffset, screenWidth);
      int centerY = ScreenAnchor.resolveCenterY(this.screenAnchor, this.yOffset, screenHeight);
      if (!"subtitle/bonus_list".equals(this.elementId) && !"subtitle/score".equals(this.elementId)) {
         if ("kill_icon/card".equals(this.elementId)) {
            this.x = centerX - this.width / 2;
            this.y = centerY - this.height;
         } else if ("subtitle/kill_feed".equals(this.elementId)) {
            this.x = centerX - this.width / 2;
            this.y = centerY;
         } else if ("kill_icon/battlefield1".equals(this.elementId)) {
            this.x = centerX + this.bf1MinX;
            this.y = centerY + this.bf1MinY;
         } else {
            this.x = centerX - this.width / 2;
            this.y = centerY - this.height / 2;
         }
      } else {
         int baseY = "subtitle/score".equals(this.elementId) ? centerY - 4 : centerY;
         if (this.alignLeft && !this.alignRight) {
            this.x = centerX;
            this.y = baseY;
         } else if (this.alignRight && !this.alignLeft) {
            this.x = centerX - this.width;
            this.y = baseY;
         } else {
            this.x = centerX - this.width / 2;
            this.y = baseY;
         }
      }
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      double[] local = this.toLocalPoint(mouseX, mouseY);
      double halfW = (double)this.width / 2.0;
      double halfH = (double)this.height / 2.0;
      return local[0] >= -halfW && local[0] <= halfW && local[1] >= -halfH && local[1] <= halfH;
   }

   public void setExternalHover(boolean hover) {
      this.externalHover = hover;
   }

   public void render(GuiGraphics guiGraphics, float partialTick, int screenWidth, boolean isHovered, double mouseX, double mouseY) {
      boolean hovered = isHovered || this.externalHover;
      long now = System.currentTimeMillis();
      float dt = (float)(now - this.lastTime) / 1000.0F;
      this.lastTime = now;
      if (hovered) {
         this.hoverProgress = Math.min(1.0F, this.hoverProgress + dt * 2.0F);
      } else {
         this.hoverProgress = Math.max(0.0F, this.hoverProgress - dt * 2.0F);
      }

      if (this.isPressed) {
         this.clickProgress = Math.min(1.0F, this.clickProgress + dt * 5.0F);
      } else {
         this.clickProgress = Math.max(0.0F, this.clickProgress - dt * 5.0F);
      }

      boolean nearRotateHandle = this.isMouseNearRotateHandle(mouseX, mouseY, 5.0);
      boolean rotateHandleShouldShow = hovered
         || this.rotateHandleProgress > 0.001F && nearRotateHandle
         || this.currentHandle == ElementPreview.ResizeHandle.ROTATE;
      if (rotateHandleShouldShow) {
         this.rotateHandleProgress = Math.min(1.0F, this.rotateHandleProgress + dt * 6.0F);
      } else {
         this.rotateHandleProgress = Math.max(0.0F, this.rotateHandleProgress - dt * 6.0F);
      }

      int baseAlpha = 77;
      int borderAlpha = 64;
      if (this.visible) {
         baseAlpha = Math.min(255, (int)((double)baseAlpha * 2.0));
         borderAlpha = Math.min(255, (int)((double)borderAlpha * 2.0));
      }

      int baseColor = 4473924 | baseAlpha << 24;
      int borderColor = GuiConstants.COLOR_BLACK & 16777215 | borderAlpha << 24;
      int trailColor = this.visible ? GuiConstants.COLOR_GOLD : -5592406;
      int targetClickColor = this.visible ? GuiConstants.COLOR_GOLD : -5592406;
      int targetColor = targetClickColor & 16777215 | -2147483648;
      int fillColor = this.interpolateColor(baseColor, targetColor, this.clickProgress);
      guiGraphics.pose().pushPose();
      float centerX = (float)this.x + (float)this.width / 2.0F;
      float centerY = (float)this.y + (float)this.height / 2.0F;
      guiGraphics.pose().translate(centerX, centerY, 0.0F);
      guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(this.rotationAngle));
      int localX = -this.width / 2;
      int localY = -this.height / 2;
      guiGraphics.fill(localX, localY, localX + this.width, localY + this.height, fillColor);
      if (this.hoverProgress < 1.0F) {
         guiGraphics.fill(localX, localY, localX + this.width, localY + 1, borderColor);
         guiGraphics.fill(localX, localY + this.height - 1, localX + this.width, localY + this.height, borderColor);
         guiGraphics.fill(localX, localY + 1, localX + 1, localY + this.height - 1, borderColor);
         guiGraphics.fill(localX + this.width - 1, localY + 1, localX + this.width, localY + this.height - 1, borderColor);
      }

      if (this.hoverProgress > 0.001F) {
         this.renderHoverTrail(guiGraphics, localX, localY, this.width, this.height, trailColor);
      }

      this.renderHandles(guiGraphics, mouseX, mouseY);
      guiGraphics.pose().popPose();
      if (hovered) {
         this.renderSubtitle(guiGraphics, partialTick, screenWidth);
      }
   }

   private void renderSubtitle(GuiGraphics guiGraphics, float partialTick, int screenWidth) {
      Minecraft mc = Minecraft.getInstance();
      int spaceWidth = mc.font.width(" ");
      int[] bounds = this.getRotatedBounds();
      int textX = bounds[1] + spaceWidth;
      int textY = bounds[2];
      if (textX < screenWidth) {
         int idColor = this.visible ? GuiConstants.COLOR_GOLD : -5592406;
         int nameColor = this.visible ? -1 : -5592406;
         String idText = this.visible
            ? this.elementId
            : this.elementId + " [" + I18n.get("battlefield_ops.client.gui.preview.hidden_state", new Object[0]) + "]";
         if (this.elementIdRenderer == null) {
            this.elementIdRenderer = new GDTextRenderer(idText, textX, textY, screenWidth, textY + 9, 1.0F, idColor, false);
         } else {
            this.elementIdRenderer.setX1(textX);
            this.elementIdRenderer.setY1(textY);
            this.elementIdRenderer.setX2(screenWidth);
            this.elementIdRenderer.setY2(textY + 9);
            this.elementIdRenderer.setText(idText);
            this.elementIdRenderer.setColor(idColor);
         }

         this.elementIdRenderer.render(guiGraphics, partialTick);
         int nameY = textY + 10;
         String nameKey = "battlefield_ops.element.name." + this.elementId.replace("/", ".");
         String nameText = I18n.get(nameKey, new Object[0]);
         if (this.elementNameRenderer == null) {
            this.elementNameRenderer = new GDTextRenderer(nameText, textX, nameY, screenWidth, nameY + 9, 1.0F, nameColor, false);
         } else {
            this.elementNameRenderer.setX1(textX);
            this.elementNameRenderer.setY1(nameY);
            this.elementNameRenderer.setX2(screenWidth);
            this.elementNameRenderer.setY2(nameY + 9);
            this.elementNameRenderer.setText(nameText);
            this.elementNameRenderer.setColor(nameColor);
         }

         this.elementNameRenderer.render(guiGraphics, partialTick);
         String xText = "X: " + this.xOffset;
         int xLineY = nameY + 10;
         if (this.xCoordRenderer == null) {
            this.xCoordRenderer = new GDTextRenderer(xText, textX, xLineY, screenWidth, xLineY + 5, 0.5F, -5592406, false);
         } else {
            this.xCoordRenderer.setX1(textX);
            this.xCoordRenderer.setY1(xLineY);
            this.xCoordRenderer.setX2(screenWidth);
            this.xCoordRenderer.setY2(xLineY + 5);
            this.xCoordRenderer.setText(xText);
            this.xCoordRenderer.setColor(-5592406);
         }

         this.xCoordRenderer.render(guiGraphics, partialTick);
         String yText = "Y: " + this.yOffset;
         int yLineY = xLineY + 5;
         if (this.yCoordRenderer == null) {
            this.yCoordRenderer = new GDTextRenderer(yText, textX, yLineY, screenWidth, yLineY + 5, 0.5F, -5592406, false);
         } else {
            this.yCoordRenderer.setX1(textX);
            this.yCoordRenderer.setY1(yLineY);
            this.yCoordRenderer.setX2(screenWidth);
            this.yCoordRenderer.setY2(yLineY + 5);
            this.yCoordRenderer.setText(yText);
            this.yCoordRenderer.setColor(-5592406);
         }

         this.yCoordRenderer.render(guiGraphics, partialTick);
         if (!"kill_icon/battlefield1".equals(this.elementId)) {
            String sText = "S: " + String.format("%.2f", this.scale);
            int sLineY = yLineY + 5;
            if (this.scaleRenderer == null) {
               this.scaleRenderer = new GDTextRenderer(sText, textX, sLineY, screenWidth, sLineY + 5, 0.5F, -5592406, false);
            } else {
               this.scaleRenderer.setX1(textX);
               this.scaleRenderer.setY1(sLineY);
               this.scaleRenderer.setX2(screenWidth);
               this.scaleRenderer.setY2(sLineY + 5);
               this.scaleRenderer.setText(sText);
               this.scaleRenderer.setColor(-5592406);
            }

            this.scaleRenderer.render(guiGraphics, partialTick);
         }

         String rText = "R: " + String.format("%.1f°", this.rotationAngle);
         int rLineY = "kill_icon/battlefield1".equals(this.elementId) ? yLineY : yLineY + 10;
         if (this.rotationRenderer == null) {
            this.rotationRenderer = new GDTextRenderer(rText, textX, rLineY, screenWidth, rLineY + 5, 0.5F, -5592406, false);
         } else {
            this.rotationRenderer.setX1(textX);
            this.rotationRenderer.setY1(rLineY);
            this.rotationRenderer.setX2(screenWidth);
            this.rotationRenderer.setY2(rLineY + 5);
            this.rotationRenderer.setText(rText);
            this.rotationRenderer.setColor(-5592406);
         }

         this.rotationRenderer.render(guiGraphics, partialTick);
      }
   }

   private void renderHandles(GuiGraphics guiGraphics, double mouseX, double mouseY) {
      if (!"kill_icon/battlefield1".equals(this.elementId)) {
         int color = this.visible ? GuiConstants.COLOR_GOLD : -5592406;
         this.drawHandle(guiGraphics, -this.width / 2, -this.height / 2, mouseX, mouseY, color, ElementPreview.ResizeHandle.TL);
         this.drawHandle(guiGraphics, this.width / 2 - 1, -this.height / 2, mouseX, mouseY, color, ElementPreview.ResizeHandle.TR);
         this.drawHandle(guiGraphics, -this.width / 2, this.height / 2 - 1, mouseX, mouseY, color, ElementPreview.ResizeHandle.BL);
         this.drawHandle(guiGraphics, this.width / 2 - 1, this.height / 2 - 1, mouseX, mouseY, color, ElementPreview.ResizeHandle.BR);
         if (this.rotateHandleProgress > 0.001F) {
            float eased = this.easeOut(this.rotateHandleProgress);
            float armLen = 10.0F * eased;
            int rotateY = Math.round((float)(-this.height) / 2.0F - armLen);
            int lineEnd = Math.round((float)(-this.height) / 2.0F - Math.max(1.0F, armLen));
            guiGraphics.fill(0, -this.height / 2, 1, lineEnd + 1, color);
            this.drawHandle(guiGraphics, 0, rotateY, mouseX, mouseY, color, ElementPreview.ResizeHandle.ROTATE);
         }
      }
   }

   private void drawHandle(GuiGraphics guiGraphics, int hx, int hy, double mx, double my, int color, ElementPreview.ResizeHandle handleType) {
      double centerX = (double)this.x + (double)this.width / 2.0;
      double centerY = (double)this.y + (double)this.height / 2.0;
      double dx = mx - centerX;
      double dy = my - centerY;
      double rad = Math.toRadians((double)(-this.rotationAngle));
      double localMouseX = dx * Math.cos(rad) - dy * Math.sin(rad);
      double localMouseY = dx * Math.sin(rad) + dy * Math.cos(rad);
      double dist = Math.sqrt(Math.pow(localMouseX - (double)hx, 2.0) + Math.pow(localMouseY - (double)hy, 2.0));
      int alpha = 0;
      int finalColor;
      if (this.currentHandle == handleType) {
         alpha = 255;
         finalColor = this.interpolateColor(color, -1, this.clickProgress);
      } else {
         if (!(dist < 60.0)) {
            return;
         }

         float factor = 1.0F - (float)(dist / 60.0);
         alpha = (int)(factor * 255.0F);
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         finalColor = alpha << 24 | r << 16 | g << 8 | b;
      }

      if (alpha > 5) {
         int half = 1;
         int x1 = hx - half;
         int y1 = hy - half;
         int x2 = hx + half + 1;
         int y2 = hy + half + 1;
         guiGraphics.fill(x1, y1, x2, y2, finalColor);
      }
   }

   private ElementPreview.ResizeHandle getHandleAt(double mouseX, double mouseY) {
      if ("kill_icon/battlefield1".equals(this.elementId)) {
         return ElementPreview.ResizeHandle.NONE;
      } else {
         int[] tl = this.rotatedHandlePoint(-this.width / 2, -this.height / 2);
         int[] tr = this.rotatedHandlePoint(this.width / 2 - 1, -this.height / 2);
         int[] bl = this.rotatedHandlePoint(-this.width / 2, this.height / 2 - 1);
         int[] br = this.rotatedHandlePoint(this.width / 2 - 1, this.height / 2 - 1);
         int[] rotate = this.rotatedHandlePoint(0, this.getCurrentRotateHandleLocalY());
         if (this.isNear(mouseX, mouseY, tl[0], tl[1])) {
            return ElementPreview.ResizeHandle.TL;
         } else if (this.isNear(mouseX, mouseY, tr[0], tr[1])) {
            return ElementPreview.ResizeHandle.TR;
         } else if (this.isNear(mouseX, mouseY, bl[0], bl[1])) {
            return ElementPreview.ResizeHandle.BL;
         } else if (this.isNear(mouseX, mouseY, br[0], br[1])) {
            return ElementPreview.ResizeHandle.BR;
         } else {
            return this.rotateHandleProgress > 0.05F && this.isNear(mouseX, mouseY, rotate[0], rotate[1])
               ? ElementPreview.ResizeHandle.ROTATE
               : ElementPreview.ResizeHandle.NONE;
         }
      }
   }

   private int[] rotatedHandlePoint(int localX, int localY) {
      double rad = Math.toRadians((double)this.rotationAngle);
      double rx = (double)localX * Math.cos(rad) - (double)localY * Math.sin(rad);
      double ry = (double)localX * Math.sin(rad) + (double)localY * Math.cos(rad);
      int centerX = this.x + this.width / 2;
      int centerY = this.y + this.height / 2;
      return new int[]{centerX + (int)Math.round(rx), centerY + (int)Math.round(ry)};
   }

   private boolean isNear(double mx, double my, int hx, int hy) {
      return Math.sqrt(Math.pow(mx - (double)hx, 2.0) + Math.pow(my - (double)hy, 2.0)) <= 5.0;
   }

   private int getCurrentRotateHandleLocalY() {
      float eased = this.easeOut(this.rotateHandleProgress);
      float armLen = 10.0F * eased;
      return Math.round((float)(-this.height) / 2.0F - armLen);
   }

   private boolean isMouseNearRotateHandle(double mouseX, double mouseY, double radius) {
      int[] lineStart = this.rotatedHandlePoint(0, -this.height / 2);
      int[] lineEnd = this.rotatedHandlePoint(0, -this.height / 2 - 10);
      double distanceToLine = this.distancePointToSegment(mouseX, mouseY, (double)lineStart[0], (double)lineStart[1], (double)lineEnd[0], (double)lineEnd[1]);
      return distanceToLine <= radius ? true : Math.sqrt(Math.pow(mouseX - (double)lineEnd[0], 2.0) + Math.pow(mouseY - (double)lineEnd[1], 2.0)) <= radius;
   }

   private double distancePointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
      double vx = x2 - x1;
      double vy = y2 - y1;
      double wx = px - x1;
      double wy = py - y1;
      double len2 = vx * vx + vy * vy;
      if (len2 <= 1.0E-4) {
         return Math.sqrt(Math.pow(px - x1, 2.0) + Math.pow(py - y1, 2.0));
      } else {
         double t = (wx * vx + wy * vy) / len2;
         t = Math.max(0.0, Math.min(1.0, t));
         double projX = x1 + t * vx;
         double projY = y1 + t * vy;
         return Math.sqrt(Math.pow(px - projX, 2.0) + Math.pow(py - projY, 2.0));
      }
   }

   public void resetVisualState() {
      this.isPressed = false;
      this.clickProgress = 0.0F;
      this.dragging = false;
      this.currentHandle = ElementPreview.ResizeHandle.NONE;
   }

   public ElementPreview.PreviewInteractionResult mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 1) {
         return this.isMouseOver(mouseX, mouseY) ? ElementPreview.PreviewInteractionResult.RIGHT_CLICK : ElementPreview.PreviewInteractionResult.PASS;
      } else if (button != 0) {
         return ElementPreview.PreviewInteractionResult.PASS;
      } else {
         ElementPreview.ResizeHandle handle = this.getHandleAt(mouseX, mouseY);
         if (handle != ElementPreview.ResizeHandle.NONE) {
            this.currentHandle = handle;
            this.dragStartScale = this.scale;
            this.dragCenterX = this.x + this.width / 2;
            this.dragCenterY = this.y + this.height / 2;
            this.dragStartDist = Math.sqrt(Math.pow(mouseX - (double)this.dragCenterX, 2.0) + Math.pow(mouseY - (double)this.dragCenterY, 2.0));
            if (this.dragStartDist < 1.0) {
               this.dragStartDist = 1.0;
            }

            this.isPressed = true;
            return ElementPreview.PreviewInteractionResult.HANDLED;
         } else if (this.isMouseOver(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            if (now - this.lastClickTime < 250L) {
               this.lastClickTime = now;
               return ElementPreview.PreviewInteractionResult.DOUBLE_CLICK;
            } else {
               this.lastClickTime = now;
               this.isPressed = true;
               this.dragging = true;
               this.dragOffsetX = (int)mouseX - this.x;
               this.dragOffsetY = (int)mouseY - this.y;
               return ElementPreview.PreviewInteractionResult.HANDLED;
            }
         } else {
            return ElementPreview.PreviewInteractionResult.PASS;
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      } else {
         boolean wasInteracting = this.isPressed || this.dragging || this.currentHandle != ElementPreview.ResizeHandle.NONE;
         this.isPressed = false;
         this.dragging = false;
         this.currentHandle = ElementPreview.ResizeHandle.NONE;
         return wasInteracting;
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int screenWidth, int screenHeight) {
      if (button != 0) {
         return false;
      } else if (this.currentHandle != ElementPreview.ResizeHandle.NONE) {
         if (this.currentHandle == ElementPreview.ResizeHandle.ROTATE) {
            double angleWorld = Math.toDegrees(Math.atan2(mouseY - (double)this.dragCenterY, mouseX - (double)this.dragCenterX));
            float newRotation = this.normalizeDegrees((float)(angleWorld + 90.0));
            this.rotationAngle = newRotation;
            String presetId = ClientConfigManager.getCurrentPresetId();
            ElementConfigManager.updateConfigValue(presetId, this.elementId, "rotation_angle", String.valueOf(newRotation));
            return true;
         } else {
            float oldScale = this.scale;
            int oldWidth = this.width;
            int oldHeight = this.height;
            int oldX = this.x;
            int oldY = this.y;
            double currentDist = Math.sqrt(Math.pow(mouseX - (double)this.dragCenterX, 2.0) + Math.pow(mouseY - (double)this.dragCenterY, 2.0));
            float scaleFactor = (float)(currentDist / this.dragStartDist);
            float newScale = this.dragStartScale * scaleFactor;
            if (newScale < 0.1F) {
               newScale = 0.1F;
            }

            if (newScale > 5.0F) {
               newScale = 5.0F;
            }

            this.scale = newScale;
            this.recalculateSize();
            this.updatePosition(screenWidth, screenHeight);
            int padding = 8;
            int maxX = screenWidth - padding;
            int minY = 26 + padding;
            int maxY = screenHeight - padding;
            boolean outOfBounds = this.x < padding || this.x + this.width > maxX || this.y < minY || this.y + this.height > maxY;
            if (outOfBounds && newScale > oldScale) {
               this.scale = oldScale;
               this.width = oldWidth;
               this.height = oldHeight;
               this.x = oldX;
               this.y = oldY;
            } else {
               String presetId = ClientConfigManager.getCurrentPresetId();
               ElementConfigManager.updateConfigValue(presetId, this.elementId, "scale", String.valueOf(this.scale));
            }

            this.dragCenterX = this.x + this.width / 2;
            this.dragCenterY = this.y + this.height / 2;
            return true;
         }
      } else if (!this.dragging) {
         return false;
      } else {
         int newTopLeftX = (int)mouseX - this.dragOffsetX;
         int newTopLeftY = (int)mouseY - this.dragOffsetY;
         int padding = 8;
         int maxX = screenWidth - this.width - padding;
         int minY = 26 + padding;
         int maxY = screenHeight - this.height - padding;
         if (newTopLeftX < padding) {
            newTopLeftX = padding;
         }

         if (newTopLeftX > maxX) {
            newTopLeftX = maxX;
         }

         if (newTopLeftY < minY) {
            newTopLeftY = minY;
         }

         if (newTopLeftY > maxY) {
            newTopLeftY = maxY;
         }

         int anchorX;
         int anchorY;
         if (!"subtitle/bonus_list".equals(this.elementId) && !"subtitle/score".equals(this.elementId)) {
            if ("kill_icon/card".equals(this.elementId)) {
               anchorX = newTopLeftX + this.width / 2;
               anchorY = newTopLeftY + this.height;
            } else if ("subtitle/kill_feed".equals(this.elementId)) {
               anchorX = newTopLeftX + this.width / 2;
               anchorY = newTopLeftY;
            } else if ("kill_icon/battlefield1".equals(this.elementId)) {
               anchorX = newTopLeftX - this.bf1MinX;
               anchorY = newTopLeftY - this.bf1MinY;
            } else {
               anchorX = newTopLeftX + this.width / 2;
               anchorY = newTopLeftY + this.height / 2;
            }
         } else {
            if (this.alignLeft && !this.alignRight) {
               anchorX = newTopLeftX;
               anchorY = newTopLeftY;
            } else if (this.alignRight && !this.alignLeft) {
               anchorX = newTopLeftX + this.width;
               anchorY = newTopLeftY;
            } else {
               anchorX = newTopLeftX + this.width / 2;
               anchorY = newTopLeftY;
            }

            if ("subtitle/score".equals(this.elementId)) {
               anchorY += 4;
            }
         }

         int newXOffset = Math.round((float)anchorX - ScreenAnchor.resolveAnchorX(this.screenAnchor, screenWidth));
         int newYOffset = Math.round(ScreenAnchor.resolveAnchorY(this.screenAnchor, screenHeight) - (float)anchorY);
         String presetId = ClientConfigManager.getCurrentPresetId();
         ElementConfigManager.updateConfigValue(presetId, this.elementId, "x_offset", String.valueOf(newXOffset));
         ElementConfigManager.updateConfigValue(presetId, this.elementId, "y_offset", String.valueOf(newYOffset));
         this.xOffset = newXOffset;
         this.yOffset = newYOffset;
         return true;
      }
   }

   private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
      if (!(this.hoverProgress <= 0.001F)) {
         float totalLength = (float)(w + h + w + h);
         float currentLength = totalLength * this.easeOut(this.hoverProgress);
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

   private int interpolateColor(int color1, int color2, float t) {
      int a1 = color1 >> 24 & 0xFF;
      int r1 = color1 >> 16 & 0xFF;
      int g1 = color1 >> 8 & 0xFF;
      int b1 = color1 & 0xFF;
      int a2 = color2 >> 24 & 0xFF;
      int r2 = color2 >> 16 & 0xFF;
      int g2 = color2 >> 8 & 0xFF;
      int b2 = color2 & 0xFF;
      int a = (int)((float)a1 + (float)(a2 - a1) * t);
      int r = (int)((float)r1 + (float)(r2 - r1) * t);
      int g = (int)((float)g1 + (float)(g2 - g1) * t);
      int b = (int)((float)b1 + (float)(b2 - b1) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public String getElementId() {
      return this.elementId;
   }

   public boolean isPressed() {
      return this.isPressed;
   }

   public int getPreviewX() {
      return this.x;
   }

   public int getPreviewY() {
      return this.y;
   }

   public int getPreviewWidth() {
      return this.width;
   }

   public int getPreviewHeight() {
      return this.height;
   }

   private double[] toLocalPoint(double worldX, double worldY) {
      double centerX = (double)this.x + (double)this.width / 2.0;
      double centerY = (double)this.y + (double)this.height / 2.0;
      double dx = worldX - centerX;
      double dy = worldY - centerY;
      double rad = Math.toRadians((double)(-this.rotationAngle));
      double localX = dx * Math.cos(rad) - dy * Math.sin(rad);
      double localY = dx * Math.sin(rad) + dy * Math.cos(rad);
      return new double[]{localX, localY};
   }

   public int[] visualBounds(){return getRotatedBounds();}
   private int[] getRotatedBounds() {
      int[] tl = this.rotatedHandlePoint(-this.width / 2, -this.height / 2);
      int[] tr = this.rotatedHandlePoint(this.width / 2, -this.height / 2);
      int[] bl = this.rotatedHandlePoint(-this.width / 2, this.height / 2);
      int[] br = this.rotatedHandlePoint(this.width / 2, this.height / 2);
      int minX = Math.min(Math.min(tl[0], tr[0]), Math.min(bl[0], br[0]));
      int maxX = Math.max(Math.max(tl[0], tr[0]), Math.max(bl[0], br[0]));
      int minY = Math.min(Math.min(tl[1], tr[1]), Math.min(bl[1], br[1]));
      int maxY = Math.max(Math.max(tl[1], tr[1]), Math.max(bl[1], br[1]));
      return new int[]{minX, maxX, minY, maxY};
   }

   private float normalizeDegrees(float angle) {
      float result = angle % 360.0F;
      if (result > 180.0F) {
         result -= 360.0F;
      } else if (result < -180.0F) {
         result += 360.0F;
      }

      return result;
   }

   public static enum PreviewInteractionResult {
      PASS,
      HANDLED,
      DOUBLE_CLICK,
      RIGHT_CLICK;
   }

   private static enum ResizeHandle {
      NONE,
      TL,
      TR,
      BL,
      BR,
      ROTATE;
   }
}
