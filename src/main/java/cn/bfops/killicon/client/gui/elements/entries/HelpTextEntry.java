package cn.bfops.killicon.client.gui.elements.entries;

import java.util.List;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;

public class HelpTextEntry extends GDRowRenderer {
   private final String title;
   private final String description;
   private final int descriptionColor;
   private GDTextRenderer descriptionRenderer;

   public HelpTextEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String title, String description) {
      this(x1, y1, x2, y2, bgColor, bgAlpha, title, description, -1);
   }

   public HelpTextEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String title, String description, int descriptionColor) {
      super(x1, y1, x2, y2, bgColor, bgAlpha, false);
      this.title = title;
      this.description = description;
      this.descriptionColor = descriptionColor;
      this.addColumn(title, 120, GuiConstants.COLOR_GOLD, false, false, null);
      this.addCustomColumn(-1, null, (guiGraphics, x, y, width, height) -> {
         if (this.descriptionRenderer == null) {
            this.descriptionRenderer = new GDTextRenderer(description, x + 4, y + 4, x + width - 4, y + height - 4, 1.0F, descriptionColor, true);
         } else {
            this.descriptionRenderer.setX1(x + 4);
            this.descriptionRenderer.setY1(y + 4);
            this.descriptionRenderer.setX2(x + width - 4);
            this.descriptionRenderer.setY2(y + height - 4);
         }

         this.descriptionRenderer.render(guiGraphics, 0.0F, false);
      });
   }

   public HelpTextEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, List<GDTextRenderer.ColoredText> titleParts, String description) {
      this(x1, y1, x2, y2, bgColor, bgAlpha, titleParts, description, -1);
   }

   public HelpTextEntry(
      int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, List<GDTextRenderer.ColoredText> titleParts, String description, int descriptionColor
   ) {
      super(x1, y1, x2, y2, bgColor, bgAlpha, false);
      this.title = titleParts == null ? "" : titleParts.stream().map(part -> part.text).reduce("", String::concat);
      this.description = description;
      this.descriptionColor = descriptionColor;
      this.addColoredColumn(titleParts, 120, false, false);
      this.addCustomColumn(-1, null, (guiGraphics, x, y, width, height) -> {
         if (this.descriptionRenderer == null) {
            this.descriptionRenderer = new GDTextRenderer(description, x + 4, y + 4, x + width - 4, y + height - 4, 1.0F, descriptionColor, true);
         } else {
            this.descriptionRenderer.setX1(x + 4);
            this.descriptionRenderer.setY1(y + 4);
            this.descriptionRenderer.setX2(x + width - 4);
            this.descriptionRenderer.setY2(y + height - 4);
         }

         this.descriptionRenderer.render(guiGraphics, 0.0F, false);
      });
   }

   public int getRequiredHeight(int width) {
      int descWidth = width - 120 - 8;
      if (descWidth <= 0) {
         return 17;
      } else {
         if (this.descriptionRenderer == null) {
            this.descriptionRenderer = new GDTextRenderer(this.description, 0, 0, descWidth, 100, 1.0F, this.descriptionColor, true);
         } else {
            this.descriptionRenderer.setX1(0);
            this.descriptionRenderer.setX2(descWidth);
         }

         int textHeight = this.descriptionRenderer.getFinalHeight();
         return Math.max(17, textHeight + 8);
      }
   }
}
