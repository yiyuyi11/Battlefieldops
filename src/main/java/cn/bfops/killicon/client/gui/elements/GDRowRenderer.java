package cn.bfops.killicon.client.gui.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import cn.bfops.killicon.client.gui.GuiConstants;

public class GDRowRenderer {
   private int x1;
   private int y1;
   private int x2;
   private int y2;
   private int bgColor;
   private float bgAlpha;
   private final boolean isHeader;
   private final List<GDRowRenderer.Column> columns = new ArrayList<>();
   private int currentColumnIndex = 0;
   private long lastTime = System.currentTimeMillis();
   private boolean separateFirstColumn = false;
   private String hoverTitle;
   private String hoverDescription;
   private Consumer<Boolean> onHover;
   private Integer clipX1;
   private Integer clipY1;
   private Integer clipX2;
   private Integer clipY2;
   private Supplier<Boolean> activeCondition = () -> true;

   public void setActiveCondition(Supplier<Boolean> activeCondition) {
      this.activeCondition = activeCondition;
   }

   public Supplier<Boolean> getActiveCondition() {
      return this.activeCondition;
   }

   public GDRowRenderer(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, boolean isHeader) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
      this.bgColor = bgColor;
      this.bgAlpha = bgAlpha;
      this.isHeader = isHeader;
   }

   public void setX1(int x1) {
      this.x1 = x1;
   }

   public void setY1(int y1) {
      this.y1 = y1;
   }

   public void setX2(int x2) {
      this.x2 = x2;
   }

   public void setY2(int y2) {
      this.y2 = y2;
   }

   public void setBackgroundColor(int bgColor) {
      this.bgColor = bgColor;
   }

   public void setBackgroundAlpha(float bgAlpha) {
      this.bgAlpha = bgAlpha;
   }

   public void addColumn(String text, int width, int color, boolean isDarker, boolean isCentered, Consumer<Integer> onClick) {
      GDRowRenderer.Column col = this.getOrCreateColumn(this.currentColumnIndex++);
      col.text = text;
      col.coloredTexts = null;
      col.icon = null;
      col.width = width;
      col.color = color;
      col.isDarker = isDarker;
      col.isCentered = isCentered;
      col.onClick = onClick;
      if (col.textRenderer != null) {
         col.textRenderer.setColor(color);
      }
   }

   public void addColumn(String text, int width, int color, boolean isDarker, boolean isCentered) {
      this.addColumn(text, width, color, isDarker, isCentered, null);
   }

   public void addNameColumn(String name, String id, int nameColor, int idColor, boolean isDarker, boolean isCentered) {
      List<GDTextRenderer.ColoredText> parts = new ArrayList<>();
      String resolvedName = name == null ? "" : name;
      parts.add(new GDTextRenderer.ColoredText(" " + resolvedName + (id != null && !id.isEmpty() ? " " : ""), nameColor));
      if (id != null && !id.isEmpty()) {
         parts.add(new GDTextRenderer.ColoredText(id, idColor));
      }

      this.addColoredColumn(parts, -1, isDarker, isCentered);
   }

   public void addColoredColumn(List<GDTextRenderer.ColoredText> coloredTexts, int width, boolean isDarker, boolean isCentered, Consumer<Integer> onClick) {
      GDRowRenderer.Column col = this.getOrCreateColumn(this.currentColumnIndex++);
      col.coloredTexts = coloredTexts;
      col.text = null;
      col.icon = null;
      col.width = width;
      col.isDarker = isDarker;
      col.isCentered = isCentered;
      col.onClick = onClick;
   }

   public String getSortKey() {
      if (!this.columns.isEmpty()) {
         String text = this.columns.get(0).text;
         if (text != null) {
            return text.trim();
         }

         List<GDTextRenderer.ColoredText> colored = this.columns.get(0).coloredTexts;
         if (colored != null && !colored.isEmpty()) {
            StringBuilder combined = new StringBuilder();

            for (GDTextRenderer.ColoredText part : colored) {
               if (part != null && part.text != null) {
                  combined.append(part.text);
               }
            }

            return combined.toString().trim();
         }
      }

      return "";
   }

   public void addColoredColumn(List<GDTextRenderer.ColoredText> coloredTexts, int width, boolean isDarker, boolean isCentered) {
      this.addColoredColumn(coloredTexts, width, isDarker, isCentered, null);
   }

   public void addIconColumn(ItemStack icon, int width, boolean isDarker, boolean isCentered, Consumer<Integer> onClick) {
      GDRowRenderer.Column col = this.getOrCreateColumn(this.currentColumnIndex++);
      col.icon = icon;
      col.text = null;
      col.coloredTexts = null;
      col.width = width;
      col.isDarker = isDarker;
      col.isCentered = isCentered;
      col.onClick = onClick;
   }

   public void addCustomColumn(int width, Consumer<Integer> onClick, GDRowRenderer.CellRenderer renderer) {
      GDRowRenderer.Column col = this.getOrCreateColumn(this.currentColumnIndex++);
      col.width = width;
      col.onClick = onClick;
      col.customRenderer = renderer;
      col.text = null;
      col.coloredTexts = null;
      col.icon = null;
   }

   private GDRowRenderer.Column getOrCreateColumn(int index) {
      while (this.columns.size() <= index) {
         this.columns.add(new GDRowRenderer.Column());
      }

      return this.columns.get(index);
   }

   public void resetColumnConfig() {
      this.currentColumnIndex = 0;
   }

   public void setHoverInfo(String title, String description) {
      this.hoverTitle = title;
      this.hoverDescription = description;
   }

   public String getHoverTitle() {
      return this.hoverTitle;
   }

   public String getHoverDescription() {
      return this.hoverDescription;
   }

   public void setOnHover(Consumer<Boolean> onHover) {
      this.onHover = onHover;
   }

   public void setSeparateFirstColumn(boolean separate) {
      this.separateFirstColumn = separate;
   }

   public void setBounds(int x1, int y1, int x2, int y2) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
   }

   public GDRowRenderer.Column getColumn(int index) {
      return index >= 0 && index < this.columns.size() ? this.columns.get(index) : null;
   }

   public void setColumnHoverReplacement(int index, List<GDRowRenderer.Column> replacementColumns) {
      GDRowRenderer.Column col = this.getColumn(index);
      if (col != null) {
         col.hoverReplacementColumns = replacementColumns;
      }
   }

   public void setClip(int x1, int y1, int x2, int y2) {
      this.clipX1 = x1;
      this.clipY1 = y1;
      this.clipX2 = x2;
      this.clipY2 = y2;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.onHover != null) {
         this.onHover.accept(this.isHovered((double)mouseX, (double)mouseY));
      }

      long now = System.currentTimeMillis();
      float dt = (float)(now - this.lastTime) / 1000.0F;
      this.lastTime = now;
      int rowHeight = this.y2 - this.y1;
      int totalWidth = this.x2 - this.x1;
      int fixedW = 0;
      int flexC = 0;

      for (int i = 0; i < this.currentColumnIndex; i++) {
         if (this.columns.get(i).width == -1) {
            flexC++;
         } else {
            fixedW += this.columns.get(i).width;
         }
      }

      int flexW = flexC > 0 ? Math.max(0, (totalWidth - fixedW) / flexC) : 0;
      int alphaBits = (int)(this.bgAlpha * 255.0F) << 24;
      int baseR = this.bgColor >> 16 & 0xFF;
      int baseG = this.bgColor >> 8 & 0xFF;
      int baseB = this.bgColor & 0xFF;
      boolean doGap = !this.isHeader && this.currentColumnIndex > 1 && this.columns.get(0).width == rowHeight || this.separateFirstColumn;
      if (doGap && this.currentColumnIndex > 0) {
         int col0W = this.columns.get(0).width == -1 ? flexW : this.columns.get(0).width;
         guiGraphics.fill(this.x1, this.y1, this.x1 + col0W, this.y2, alphaBits | baseR << 16 | baseG << 8 | baseB);
         guiGraphics.fill(this.x1 + col0W + 1, this.y1, this.x2, this.y2, alphaBits | baseR << 16 | baseG << 8 | baseB);
      } else {
         guiGraphics.fill(this.x1, this.y1, this.x2, this.y2, alphaBits | baseR << 16 | baseG << 8 | baseB);
      }

      int currentX = this.x1;

      for (int ix = 0; ix < this.currentColumnIndex; ix++) {
         GDRowRenderer.Column col = this.columns.get(ix);
         int colW = col.width == -1 ? flexW : col.width;
         if (colW > 0) {
            boolean isHovered = mouseX >= currentX && mouseX < currentX + colW && mouseY >= this.y1 && mouseY < this.y2;
            if (isHovered && col.hoverReplacementColumns != null && !col.hoverReplacementColumns.isEmpty()) {
               this.renderReplacementColumns(guiGraphics, col.hoverReplacementColumns, currentX, this.y1, colW, rowHeight, mouseX, mouseY, partialTick, dt);
               currentX += colW;
            } else {
               float animSpeed = 8.0F;
               if (isHovered) {
                  col.hoverProgress = Math.min(1.0F, col.hoverProgress + dt * animSpeed);
               } else {
                  col.hoverProgress = Math.max(0.0F, col.hoverProgress - dt * animSpeed);
               }

               float translateX = guiGraphics.pose().last().pose().m30();
               float translateY = guiGraphics.pose().last().pose().m31();
               int sX1 = Math.round((float)Math.max(this.x1, currentX) + translateX);
               int sX2 = Math.round((float)Math.min(this.x2, currentX + colW) + translateX);
               int sY1 = Math.round((float)this.y1 + translateY);
               int sY2 = Math.round((float)this.y2 + translateY);
               if (this.clipX1 != null) {
                  sX1 = Math.max(sX1, this.clipX1);
               }

               if (this.clipY1 != null) {
                  sY1 = Math.max(sY1, this.clipY1);
               }

               if (this.clipX2 != null) {
                  sX2 = Math.min(sX2, this.clipX2);
               }

               if (this.clipY2 != null) {
                  sY2 = Math.min(sY2, this.clipY2);
               }

               if (sX2 > sX1 && sY2 > sY1) {
                  guiGraphics.enableScissor(sX1, sY1, sX2, sY2);
                  int drawX = currentX;
                  int drawW = colW;
                  if (doGap && ix == 1) {
                     drawX = currentX + 1;
                     drawW = colW - 1;
                  }

                  if (col.isDarker || col.hoverProgress > 0.0F) {
                     float darken = 0.0F;
                     if (col.isDarker) {
                        darken += 0.25F;
                     }

                     if (col.hoverProgress > 0.0F && (this.isHeader || col.onClick != null)) {
                        darken += col.hoverProgress * 0.2F;
                     }

                     int overlayAlpha = (int)(255.0F * darken);
                     int overlayColor = overlayAlpha << 24 | 0;
                     guiGraphics.fill(drawX, this.y1, drawX + drawW, this.y2, overlayColor);
                  }

                  if ((this.isHeader || col.onClick != null) && col.hoverProgress > 0.001F) {
                     float t = col.hoverProgress;
                     float ease = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
                     float barWidth = (float)drawW * ease;
                     guiGraphics.pose().pushPose();
                     guiGraphics.pose().translate((float)drawX, (float)this.y2 - 1.0F, 0.0F);
                     guiGraphics.pose().scale(barWidth, 1.0F, 1.0F);
                     guiGraphics.fill(0, 0, 1, 1, GuiConstants.COLOR_GOLD);
                     guiGraphics.pose().popPose();
                  }

                  this.renderContent(guiGraphics, col, drawX, drawW, rowHeight, partialTick, dt, ix);
                  guiGraphics.disableScissor();
               }

               currentX += colW;
            }
         }
      }
   }

   private void renderReplacementColumns(
      GuiGraphics guiGraphics, List<GDRowRenderer.Column> subColumns, int x, int y, int w, int h, int mouseX, int mouseY, float pt, float dt
   ) {
      int count = subColumns.size();
      int subW = w / count;
      int currentX = x;

      for (int i = 0; i < count; i++) {
         GDRowRenderer.Column subCol = subColumns.get(i);
         int thisW = i == count - 1 ? x + w - currentX : subW;
         boolean subHovered = mouseX >= currentX && mouseX < currentX + thisW && mouseY >= y && mouseY < y + h;
         if (subHovered) {
            subCol.hoverProgress = Math.min(1.0F, subCol.hoverProgress + dt * 8.0F);
         } else {
            subCol.hoverProgress = Math.max(0.0F, subCol.hoverProgress - dt * 8.0F);
         }

         float translateX = guiGraphics.pose().last().pose().m30();
         float translateY = guiGraphics.pose().last().pose().m31();
         int sX1 = Math.round((float)Math.max(this.x1, currentX) + translateX);
         int sX2 = Math.round((float)Math.min(this.x2, currentX + thisW) + translateX);
         int sY1 = Math.round((float)this.y1 + translateY);
         int sY2 = Math.round((float)this.y2 + translateY);
         if (this.clipX1 != null) {
            sX1 = Math.max(sX1, this.clipX1);
         }

         if (this.clipY1 != null) {
            sY1 = Math.max(sY1, this.clipY1);
         }

         if (this.clipX2 != null) {
            sX2 = Math.min(sX2, this.clipX2);
         }

         if (this.clipY2 != null) {
            sY2 = Math.min(sY2, this.clipY2);
         }

         if (sX2 > sX1 && sY2 > sY1) {
            guiGraphics.enableScissor(sX1, sY1, sX2, sY2);
            int alphaBits = (int)(this.bgAlpha * 255.0F) << 24;
            int bgCol = this.bgColor & 16777215 | alphaBits;
            guiGraphics.fill(currentX, y, currentX + thisW, y + h, bgCol);
            if (subHovered || subCol.isDarker) {
               float darken = subCol.isDarker ? 0.25F : 0.0F;
               if (subHovered) {
                  darken += 0.2F;
               }

               int overlayAlpha = (int)(255.0F * darken);
               guiGraphics.fill(currentX, y, currentX + thisW, y + h, overlayAlpha << 24);
            }

            this.renderContent(guiGraphics, subCol, currentX, thisW, h, pt, dt, -1);
            if (subCol.onClick != null && subCol.hoverProgress > 0.001F) {
               float t = subCol.hoverProgress;
               float ease = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
               float barWidth = (float)thisW * ease;
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate((float)currentX, (float)(y + h) - 1.0F, 0.0F);
               guiGraphics.pose().scale(barWidth, 1.0F, 1.0F);
               guiGraphics.fill(0, 0, 1, 1, GuiConstants.COLOR_GOLD);
               guiGraphics.pose().popPose();
            }

            guiGraphics.disableScissor();
         }

         if (i < count - 1) {
            guiGraphics.fill(currentX + thisW - 1, y, currentX + thisW, y + h, 1073741824);
         }

         currentX += thisW;
      }
   }

   protected void renderContent(GuiGraphics guiGraphics, GDRowRenderer.Column col, int x, int w, int h, float pt, float dt, int colIndex) {
      if (col.customRenderer != null) {
         col.customRenderer.render(guiGraphics, x, this.y1, w, h);
      } else {
         if (col.icon != null) {
            float scale = (float)h / 16.0F * 0.8F;
            int size = (int)(16.0F * scale);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((double)x + (double)(w - size) / 2.0, (double)this.y1 + (double)(h - size) / 2.0, 0.0);
            guiGraphics.pose().scale(scale, scale, 1.0F);

            try {
               guiGraphics.renderItem(col.icon, 0, 0);
            } catch (Exception var13) {
               guiGraphics.renderItem(Items.BARRIER.getDefaultInstance(), 0, 0);
            }

            guiGraphics.pose().popPose();
         } else {
            int textY = this.y1 + (h - 9) / 2;
            int tx1 = col.isCentered ? x : x + 2;
            int tx2 = x + w;
            if (col.textRenderer == null) {
               if (col.coloredTexts != null) {
                  col.textRenderer = new GDTextRenderer(col.coloredTexts, tx1, textY, tx2, textY + 9, 1.0F, false);
               } else {
                  col.textRenderer = new GDTextRenderer(col.text, tx1, textY, tx2, textY + 9, 1.0F, col.color, false);
               }
            } else {
               col.textRenderer.setX1(tx1);
               col.textRenderer.setY1(textY);
               col.textRenderer.setX2(tx2);
               col.textRenderer.setY2(textY + 9);
               if (col.coloredTexts != null) {
                  col.textRenderer.setColoredTexts(col.coloredTexts);
               } else {
                  col.textRenderer.setText(col.text);
               }
            }

            col.textRenderer.setCentered(col.isCentered);
            boolean isActive = this.activeCondition.get();
            if (!isActive) {
               col.textRenderer.setOverrideColor(-5592406);
            } else {
               col.textRenderer.setOverrideColor(null);
            }

            col.textRenderer.renderInternal(guiGraphics, pt, false, dt);
         }
      }
   }

   public boolean isHovered(double mouseX, double mouseY) {
      return mouseX >= (double)this.x1 && mouseX <= (double)this.x2 && mouseY >= (double)this.y1 && mouseY <= (double)this.y2;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         return false;
      } else if (!this.activeCondition.get()) {
         return false;
      } else {
         int currentX = this.x1;
         int totalW = this.x2 - this.x1;
         int fixedW = 0;
         int flexC = 0;

         for (int i = 0; i < this.currentColumnIndex; i++) {
            if (this.columns.get(i).width == -1) {
               flexC++;
            } else {
               fixedW += this.columns.get(i).width;
            }
         }

         int flexW = flexC > 0 ? (totalW - fixedW) / flexC : 0;

         for (int ix = 0; ix < this.currentColumnIndex; ix++) {
            int colW = this.columns.get(ix).width == -1 ? flexW : this.columns.get(ix).width;
            if (mouseX >= (double)currentX && mouseX < (double)(currentX + colW)) {
               GDRowRenderer.Column col = this.columns.get(ix);
               if (col.hoverReplacementColumns != null && !col.hoverReplacementColumns.isEmpty()) {
                  int subCount = col.hoverReplacementColumns.size();
                  int subW = colW / subCount;
                  int subX = currentX;

                  for (int j = 0; j < subCount; j++) {
                     int thisW = j == subCount - 1 ? currentX + colW - subX : subW;
                     if (mouseX >= (double)subX && mouseX < (double)(subX + thisW)) {
                        if (col.hoverReplacementColumns.get(j).onClick != null) {
                           col.hoverReplacementColumns.get(j).onClick.accept(button);
                           return true;
                        }

                        return false;
                     }

                     subX += thisW;
                  }
               }

               if (col.onClick != null) {
                  col.onClick.accept(button);
                  return true;
               }

               return false;
            }

            currentX += colW;
         }

         return false;
      }
   }

   public interface CellRenderer {
      void render(GuiGraphics var1, int var2, int var3, int var4, int var5);
   }

   public static class Column {
      public String text;
      public List<GDTextRenderer.ColoredText> coloredTexts;
      public ItemStack icon;
      public int width;
      public int color;
      public boolean isDarker;
      public boolean isCentered;
      public GDTextRenderer textRenderer;
      public Consumer<Integer> onClick;
      public float hoverProgress = 0.0F;
      public List<GDRowRenderer.Column> hoverReplacementColumns;
      public GDRowRenderer.CellRenderer customRenderer;
   }
}
