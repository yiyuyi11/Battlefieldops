package cn.bfops.killicon.client.gui.elements;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.killicon.client.config.ClientConfigManager;

public class GDTextRenderer {
   private final Minecraft minecraft;
   private String text;
   private int x1;
   private int y1;
   private int x2;
   private int y2;
   private int width;
   private int height;
   private float fontSize;
   private int color;
   private List<GDTextRenderer.ColoredText> coloredTexts;
   private boolean autoWrap;
   private long pauseStartTime = -1L;
   private float scrollOffset = 0.0F;
   private boolean scrollingForward = true;
   private long lastTime;
   private static final float SCROLL_SPEED = 30.0F;
   private static final long PAUSE_TIME_MS = 1000L;
   private boolean centered = false;
   private Integer overrideColor = null;
   private float scrollY = 0.0F;

   public void setOverrideColor(Integer color) {
      this.overrideColor = color;
   }

   public GDTextRenderer(String text, int x1, int y1, int x2, int y2, float fontSize, int color, boolean autoWrap) {
      this.minecraft = Minecraft.getInstance();
      this.text = text;
      this.coloredTexts = null;
      this.x1 = Math.min(x1, x2);
      this.y1 = Math.min(y1, y2);
      this.x2 = Math.max(x1, x2);
      this.y2 = Math.max(y1, y2);
      this.width = this.x2 - this.x1;
      this.height = this.y2 - this.y1;
      this.fontSize = fontSize;
      this.color = color;
      this.autoWrap = autoWrap;
      this.lastTime = System.currentTimeMillis();
   }

   public GDTextRenderer(List<GDTextRenderer.ColoredText> coloredTexts, int x1, int y1, int x2, int y2, float fontSize, boolean autoWrap) {
      this.minecraft = Minecraft.getInstance();
      this.coloredTexts = coloredTexts;
      this.text = "";

      for (GDTextRenderer.ColoredText ct : coloredTexts) {
         this.text = this.text + ct.text;
      }

      this.x1 = Math.min(x1, x2);
      this.y1 = Math.min(y1, y2);
      this.x2 = Math.max(x1, x2);
      this.y2 = Math.max(y1, y2);
      this.width = this.x2 - this.x1;
      this.height = this.y2 - this.y1;
      this.fontSize = fontSize;
      this.color = -1;
      this.autoWrap = autoWrap;
      this.lastTime = System.currentTimeMillis();
   }

   public void setCentered(boolean centered) {
      this.centered = centered;
   }

   public void render(GuiGraphics guiGraphics, float partialTick) {
      this.render(guiGraphics, partialTick, true);
   }

   public void render(GuiGraphics guiGraphics, float partialTick, boolean useScissor) {
      long now = System.currentTimeMillis();
      float dt = (float)(now - this.lastTime) / 1000.0F;
      this.lastTime = now;
      this.renderInternal(guiGraphics, partialTick, useScissor, dt);
   }

   public void renderInternal(GuiGraphics guiGraphics, float partialTick, boolean useOwnScissorAndScroll, float dt) {
      guiGraphics.pose().pushPose();
      if (useOwnScissorAndScroll) {
         guiGraphics.enableScissor(this.x1, this.y1, this.x2, this.y2);
      }

      float scale = this.fontSize;
      guiGraphics.pose().translate((float)this.x1, (float)this.y1, 0.0F);
      guiGraphics.pose().scale(scale, scale, 1.0F);
      float scaledWidth = (float)this.width / scale;
      if (this.autoWrap) {
         this.renderWrappedText(guiGraphics, scaledWidth);
      } else {
         this.renderScrollingText(guiGraphics, dt, scaledWidth);
      }

      guiGraphics.pose().popPose();
      if (useOwnScissorAndScroll) {
         guiGraphics.disableScissor();
      }
   }

   private void renderWrappedText(GuiGraphics guiGraphics, float maxWidth) {
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0F, -this.scrollY, 0.0F);
      this.drawWrappedInternal(guiGraphics, maxWidth);
      guiGraphics.pose().popPose();
   }

   public void setScrollY(float scrollY) {
      this.scrollY = scrollY;
   }

   public float getScrollY() {
      return this.scrollY;
   }

   public float getMaxScrollY() {
      if (!this.autoWrap) {
         return 0.0F;
      } else {
         int contentHeight = this.getFinalHeight();
         float containerHeight = (float)this.height / this.fontSize;
         return Math.max(0.0F, (float)contentHeight - containerHeight);
      }
   }

   private void drawWrappedInternal(GuiGraphics guiGraphics, float maxWidth) {
      if (this.coloredTexts != null) {
         this.renderMixedColorWrapped(guiGraphics, maxWidth);
      } else {
         List<String> lines = this.wrapText(this.text, maxWidth);

         for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float lineX = 0.0F;
            if (this.centered) {
               lineX = (maxWidth - (float)this.minecraft.font.width(line)) / 2.0F;
            }

            guiGraphics.drawString(this.minecraft.font, line, (int)lineX, i * 9, this.overrideColor != null ? this.overrideColor : this.color, true);
         }
      }
   }

   private void renderMixedColorWrapped(GuiGraphics guiGraphics, float maxWidth) {
      int yOffset = 0;
      float currentX = 0.0F;

      for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
         String remaining = ct.text;

         while (!remaining.isEmpty()) {
            int count = (float)this.minecraft.font.width(remaining) > maxWidth - currentX
               ? this.minecraft.font.plainSubstrByWidth(remaining, (int)(maxWidth - currentX)).length()
               : remaining.length();
            if (count == 0 && currentX > 0.0F) {
               currentX = 0.0F;
               yOffset += 9;
            } else {
               String part = remaining.substring(0, Math.max(1, count));
               guiGraphics.drawString(this.minecraft.font, part, (int)currentX, yOffset, this.overrideColor != null ? this.overrideColor : ct.color, true);
               currentX += (float)this.minecraft.font.width(part);
               remaining = remaining.substring(part.length());
               if (currentX >= maxWidth - 1.0F) {
                  currentX = 0.0F;
                  yOffset += 9;
               }
            }
         }
      }
   }

   private List<String> wrapText(String text, float maxWidth) {
      List<String> lines = new ArrayList<>();
      String processedText = text.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n");
      String[] paragraphs = processedText.split("\n", -1);

      for (String paragraph : paragraphs) {
         if (paragraph.isEmpty()) {
            lines.add("");
         } else {
            StringBuilder currentLine = new StringBuilder();

            for (int i = 0; i < paragraph.length(); i++) {
               char c = paragraph.charAt(i);
               String nextText = currentLine.toString() + c;
               if ((float)this.minecraft.font.width(nextText) > maxWidth && currentLine.length() > 0) {
                  lines.add(currentLine.toString());
                  currentLine = new StringBuilder();
               }

               currentLine.append(c);
            }

            if (currentLine.length() > 0) {
               lines.add(currentLine.toString());
            }
         }
      }

      return lines;
   }

   private void renderScrollingText(GuiGraphics guiGraphics, float dt, float maxWidth) {
      int textWidth = 0;
      if (this.coloredTexts == null) {
         textWidth = this.minecraft.font.width(this.text);
      } else {
         for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
            textWidth += this.minecraft.font.width(ct.text);
         }
      }

      if ((float)textWidth <= maxWidth + 1.0F) {
         this.drawInternal(guiGraphics, 0, 0, maxWidth);
         this.scrollOffset = 0.0F;
         this.pauseStartTime = -1L;
         this.scrollingForward = true;
      } else if ("ellipsis".equals(ClientConfigManager.getSingleLineSubtitleCompressionMode())) {
         this.drawEllipsizedInternal(guiGraphics, 0, 0, maxWidth);
         this.scrollOffset = 0.0F;
         this.pauseStartTime = -1L;
         this.scrollingForward = true;
      } else {
         float maxScroll = (float)textWidth - maxWidth;
         if (this.pauseStartTime != -1L) {
            long elapsed = System.currentTimeMillis() - this.pauseStartTime;
            if (elapsed > 1000L) {
               this.pauseStartTime = -1L;
            }
         } else if (this.scrollingForward) {
            this.scrollOffset += 30.0F * dt;
            if (this.scrollOffset >= maxScroll) {
               this.scrollOffset = maxScroll;
               this.scrollingForward = false;
               this.pauseStartTime = System.currentTimeMillis();
            }
         } else {
            this.scrollOffset -= 30.0F * dt;
            if (this.scrollOffset <= 0.0F) {
               this.scrollOffset = 0.0F;
               this.scrollingForward = true;
               this.pauseStartTime = System.currentTimeMillis();
            }
         }

         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(-this.scrollOffset, 0.0F, 0.0F);
         this.drawInternal(guiGraphics, 0, 0, (float)textWidth);
         guiGraphics.pose().popPose();
      }
   }

   private void drawInternal(GuiGraphics guiGraphics, int x, int y, float containerWidth) {
      if (this.coloredTexts == null) {
         float drawX = (float)x;
         if (this.centered && !this.isCurrentlyScrolling()) {
            drawX += (containerWidth - (float)this.minecraft.font.width(this.text)) / 2.0F;
         }

         guiGraphics.drawString(this.minecraft.font, this.text, (int)drawX, y, this.overrideColor != null ? this.overrideColor : this.color, true);
      } else {
         float currentX = (float)x;
         if (this.centered && !this.isCurrentlyScrolling()) {
            float totalWidth = 0.0F;

            for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
               totalWidth += (float)this.minecraft.font.width(ct.text);
            }

            currentX += (containerWidth - totalWidth) / 2.0F;
         }

         for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
            guiGraphics.drawString(this.minecraft.font, ct.text, (int)currentX, y, this.overrideColor != null ? this.overrideColor : ct.color, true);
            currentX += (float)this.minecraft.font.width(ct.text);
         }
      }
   }

   private void drawEllipsizedInternal(GuiGraphics guiGraphics, int x, int y, float maxWidth) {
      String ellipsis = "...";
      int ellipsisWidth = this.minecraft.font.width(ellipsis);
      int targetWidth = Math.max(0, (int)maxWidth - ellipsisWidth);
      if (this.coloredTexts == null) {
         String base = this.text == null ? "" : this.text;
         String clipped = this.minecraft.font.plainSubstrByWidth(base, targetWidth);
         guiGraphics.drawString(this.minecraft.font, clipped + ellipsis, x, y, this.overrideColor != null ? this.overrideColor : this.color, true);
      } else {
         List<GDTextRenderer.ColoredText> segments = this.buildEllipsizedColoredTexts(targetWidth);
         float currentX = (float)x;
         int lastColor = this.overrideColor != null ? this.overrideColor : (segments.isEmpty() ? this.color : segments.get(segments.size() - 1).color);

         for (GDTextRenderer.ColoredText segment : segments) {
            int segmentColor = this.overrideColor != null ? this.overrideColor : segment.color;
            guiGraphics.drawString(this.minecraft.font, segment.text, (int)currentX, y, segmentColor, true);
            currentX += (float)this.minecraft.font.width(segment.text);
            lastColor = segmentColor;
         }

         guiGraphics.drawString(this.minecraft.font, ellipsis, (int)currentX, y, lastColor, true);
      }
   }

   private List<GDTextRenderer.ColoredText> buildEllipsizedColoredTexts(int targetWidth) {
      List<GDTextRenderer.ColoredText> result = new ArrayList<>();
      if (targetWidth > 0 && this.coloredTexts != null) {
         int used = 0;

         for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
            if (ct != null && ct.text != null && !ct.text.isEmpty()) {
               int remaining = targetWidth - used;
               if (remaining <= 0) {
                  break;
               }

               String part = this.minecraft.font.plainSubstrByWidth(ct.text, remaining);
               if (part == null || part.isEmpty()) {
                  break;
               }

               result.add(new GDTextRenderer.ColoredText(part, ct.color));
               used += this.minecraft.font.width(part);
               if (part.length() < ct.text.length()) {
                  break;
               }
            }
         }

         return result;
      } else {
         return result;
      }
   }

   private boolean isCurrentlyScrolling() {
      int textWidth = 0;
      if (this.coloredTexts == null) {
         textWidth = this.minecraft.font.width(this.text);
      } else {
         for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
            textWidth += this.minecraft.font.width(ct.text);
         }
      }

      float scale = this.fontSize;
      float scaledWidth = (float)this.width / scale;
      return (float)textWidth > scaledWidth + 1.0F;
   }

   public int getFinalHeight() {
      if (!this.autoWrap) {
         return (int)(9.0F * this.fontSize);
      } else {
         float scale = this.fontSize;
         float scaledWidth = (float)this.width / scale;
         if (this.coloredTexts != null) {
            return (int)((float)this.getMixedColorWrappedHeight(scaledWidth) * scale);
         } else {
            List<String> lines = this.wrapText(this.text, scaledWidth);
            return (int)((float)(lines.size() * 9) * scale);
         }
      }
   }

   private int getMixedColorWrappedHeight(float maxWidth) {
      int lines = 1;
      float currentX = 0.0F;

      for (GDTextRenderer.ColoredText ct : this.coloredTexts) {
         String remaining = ct.text;

         while (!remaining.isEmpty()) {
            int count = (float)this.minecraft.font.width(remaining) > maxWidth - currentX
               ? this.minecraft.font.plainSubstrByWidth(remaining, (int)(maxWidth - currentX)).length()
               : remaining.length();
            if (count == 0 && currentX > 0.0F) {
               currentX = 0.0F;
               lines++;
            } else {
               String part = remaining.substring(0, Math.max(1, count));
               currentX += (float)this.minecraft.font.width(part);
               remaining = remaining.substring(part.length());
               if (currentX >= maxWidth - 1.0F && !remaining.isEmpty()) {
                  currentX = 0.0F;
                  lines++;
               }
            }
         }
      }

      return lines * 9;
   }

   public void setText(String text) {
      if (text != null && !text.equals(this.text)) {
         this.text = text;
         this.coloredTexts = null;
      }
   }

   public void setColor(int color) {
      this.color = color;
   }

   public void setColoredTexts(List<GDTextRenderer.ColoredText> coloredTexts) {
      if (coloredTexts != null) {
         this.coloredTexts = coloredTexts;
         StringBuilder sb = new StringBuilder();

         for (GDTextRenderer.ColoredText ct : coloredTexts) {
            sb.append(ct.text);
         }

         this.text = sb.toString();
      }
   }

   public void setX1(int x1) {
      this.x1 = x1;
      this.updateWidthHeight();
   }

   public void setY1(int y1) {
      this.y1 = y1;
      this.updateWidthHeight();
   }

   public void setX2(int x2) {
      this.x2 = x2;
      this.updateWidthHeight();
   }

   public void setY2(int y2) {
      this.y2 = y2;
      this.updateWidthHeight();
   }

   private void updateWidthHeight() {
      this.width = Math.abs(this.x2 - this.x1);
      this.height = Math.abs(this.y2 - this.y1);
   }

   public static class ColoredText {
      public String text;
      public int color;

      public ColoredText(String text, int color) {
         this.text = text;
         this.color = color;
      }
   }
}
