package cn.bfops.killicon.client.gui.tabs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.entries.HelpTextEntry;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.BonusType;
import cn.bfops.killicon.common.bonus.BonusRegistry;
import cn.bfops.killicon.common.honor.HonorDefinition;
import cn.bfops.killicon.common.honor.HonorRegistry;

public class HelpTab extends ConfigTabContent {
   private int area3X1;
   private int area3Y1;
   private int area3X2;
   private int area3Y2;
   private double scrollY3 = 0.0;
   private double targetScrollY3 = 0.0;
   private GDTextRenderer area3TextRenderer;
   private boolean isDragging3 = false;
   private double lastMouseY3 = 0.0;
   private int area2X1;
   private int area2Y1;
   private int area2X2;
   private int area2Y2;
   private final List<GDRowRenderer> contentRenderers = new ArrayList<>();
   private int[] categoryStartIndices = new int[4];
   private boolean isCommandExpanded = false;
   private boolean isBonusExpanded = false;
   private boolean isHonorExpanded = false;
   private boolean isPresetExpanded = false;
   private boolean isCommandBonusExpanded = false;
   private boolean isCommandResetExpanded = false;
   private boolean isCommandConfigExpanded = false;
   private boolean isCommandStatisticsExpanded = false;
   private boolean isCommandDebugExpanded = false;
   private final List<Integer> rowHeights = new ArrayList<>();
   private boolean isDragging = false;
   private double lastMouseY = 0.0;
   private int lastContentWidth = 0;
   private long lastFrameTime = 0L;

   public HelpTab(Minecraft minecraft) {
      super(minecraft, "battlefield_ops.client.gui.config.tab.help");
   }

   @Override
   public void onTabOpen() {
      super.onTabOpen();
      this.scrollY = 0.0;
      this.targetScrollY = 0.0;
      this.scrollY3 = 0.0;
      this.targetScrollY3 = 0.0;
      this.rebuildContent();
   }

   private void rebuildContent() {
      this.contentRenderers.clear();
      this.rowHeights.clear();
      int area1Right = (this.minecraft.getWindow().getGuiScaledWidth() - 16) / 3 + 8;
      int x1 = area1Right + 8;
      int x2 = this.minecraft.getWindow().getGuiScaledWidth() - 8;
      int contentWidth = x2 - x1;
      this.addCategoryHeader(0, "battlefield_ops.client.gui.help.category.commands", this.isCommandExpanded, btn -> {
         this.isCommandExpanded = !this.isCommandExpanded;
         this.rebuildContent();
      });
      this.categoryStartIndices[0] = 0;
      if (this.isCommandExpanded) {
         this.addSubCategoryHeader("battlefield_ops.client.gui.help.command.category.bonus", this.isCommandBonusExpanded, btn -> {
            this.isCommandBonusExpanded = !this.isCommandBonusExpanded;
            this.rebuildContent();
         });
         if (this.isCommandBonusExpanded) {
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.bonus.turnon.title", "battlefield_ops.client.gui.help.command.bonus.turnon.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.bonus.turnoff.title", "battlefield_ops.client.gui.help.command.bonus.turnoff.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.bonus.edit.title", "battlefield_ops.client.gui.help.command.bonus.edit.desc", contentWidth
            );
         }

         this.addSubCategoryHeader("battlefield_ops.client.gui.help.command.category.reset", this.isCommandResetExpanded, btn -> {
            this.isCommandResetExpanded = !this.isCommandResetExpanded;
            this.rebuildContent();
         });
         if (this.isCommandResetExpanded) {
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.reset.config.title", "battlefield_ops.client.gui.help.command.reset.config.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.reset.bonus.title", "battlefield_ops.client.gui.help.command.reset.bonus.desc", contentWidth
            );
         }

         this.addSubCategoryHeader("battlefield_ops.client.gui.help.command.category.config", this.isCommandConfigExpanded, btn -> {
            this.isCommandConfigExpanded = !this.isCommandConfigExpanded;
            this.rebuildContent();
         });
         if (this.isCommandConfigExpanded) {
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.config.combowindow.title", "battlefield_ops.client.gui.help.command.config.combowindow.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.config.scoremaxlimit.title",
               "battlefield_ops.client.gui.help.command.config.scoremaxlimit.desc",
               contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.config.displayname.title", "battlefield_ops.client.gui.help.command.config.displayname.desc", contentWidth
            );
         }

         this.addSubCategoryHeader("battlefield_ops.client.gui.help.command.category.statistics", this.isCommandStatisticsExpanded, btn -> {
            this.isCommandStatisticsExpanded = !this.isCommandStatisticsExpanded;
            this.rebuildContent();
         });
         if (this.isCommandStatisticsExpanded) {
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.statistics.get.title", "battlefield_ops.client.gui.help.command.statistics.get.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.statistics.list.title", "battlefield_ops.client.gui.help.command.statistics.list.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.statistics.modify.title", "battlefield_ops.client.gui.help.command.statistics.modify.desc", contentWidth
            );
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.statistics.reset.title", "battlefield_ops.client.gui.help.command.statistics.reset.desc", contentWidth
            );
         }

         this.addSubCategoryHeader("battlefield_ops.client.gui.help.command.category.debug", this.isCommandDebugExpanded, btn -> {
            this.isCommandDebugExpanded = !this.isCommandDebugExpanded;
            this.rebuildContent();
         });
         if (this.isCommandDebugExpanded) {
            this.addIndentedHelpEntry(
               "battlefield_ops.client.gui.help.command.debug.scoreboard.title", "battlefield_ops.client.gui.help.command.debug.scoreboard.desc", contentWidth
            );
         }
      }

      int bonusIndex = this.contentRenderers.size();
      this.addCategoryHeader(1, "battlefield_ops.client.gui.help.category.bonus", this.isBonusExpanded, btn -> {
         this.isBonusExpanded = !this.isBonusExpanded;
         this.rebuildContent();
      });
      this.categoryStartIndices[1] = bonusIndex;
      if (this.isBonusExpanded) {
         List<String> bonusNames = new ArrayList<>(BonusType.getAllNames());
         bonusNames.sort(Comparator.comparingInt(BonusType::getTypeByName));

         for (String bonusName : bonusNames) {
            int bonusType = BonusType.getTypeByName(bonusName);
            String displayName = I18n.get(BonusRegistry.nameKey(bonusType), new Object[0]);
            String description = I18n.get(BonusRegistry.descKey(bonusType), new Object[0]);
            this.addPrefixedBonusHelpEntry(bonusType, bonusName, displayName, description, contentWidth);
         }
      }

      int honorIndex = this.contentRenderers.size();
      this.addCategoryHeader(2, "battlefield_ops.client.gui.help.category.honor", this.isHonorExpanded, btn -> {
         this.isHonorExpanded = !this.isHonorExpanded;
         this.rebuildContent();
      });
      this.categoryStartIndices[2] = honorIndex;
      if (this.isHonorExpanded) {
         List<HonorDefinition> sortedHonors = new ArrayList<>(HonorRegistry.getAll());
         sortedHonors.sort(Comparator.comparing(HonorDefinition::id));

         for (HonorDefinition def : sortedHonors) {
            this.addPrefixedHonorHelpEntry(def.id(), def.displayNameKey(), def.descriptionKey(), contentWidth);
         }
      }

      int presetIndex = this.contentRenderers.size();
      this.addCategoryHeader(3, "battlefield_ops.client.gui.help.category.preset", this.isPresetExpanded, btn -> {
         this.isPresetExpanded = !this.isPresetExpanded;
         this.rebuildContent();
      });
      this.categoryStartIndices[3] = presetIndex;
      if (this.isPresetExpanded) {
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.intro", "battlefield_ops.client.gui.help.preset.intro.desc", contentWidth);
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.select", "battlefield_ops.client.gui.help.preset.select.desc", contentWidth);
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.create", "battlefield_ops.client.gui.help.preset.create.desc", contentWidth);
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.edit", "battlefield_ops.client.gui.help.preset.edit.desc", contentWidth);
         this.addHelpEntry(
            "battlefield_ops.client.gui.help.preset.texture_select_mode", "battlefield_ops.client.gui.help.preset.texture_select_mode.desc", contentWidth
         );
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.export", "battlefield_ops.client.gui.help.preset.export.desc", contentWidth);
         this.addHelpEntry("battlefield_ops.client.gui.help.preset.structure", "battlefield_ops.client.gui.help.preset.structure.desc", contentWidth);
      }

      String area3Text = I18n.get("battlefield_ops.client.gui.help.area3.desc", new Object[0]);
      if (this.area3TextRenderer == null) {
         this.area3TextRenderer = new GDTextRenderer(area3Text, this.area3X1, this.area3Y1, this.area3X2, this.area3Y2, 1.0F, -1, true);
      } else {
         this.area3TextRenderer.setText(area3Text);
      }

      this.totalContentHeight = 0;

      for (int h : this.rowHeights) {
         this.totalContentHeight += h + 1;
      }
   }

   private void addCategoryHeader(int index, String titleKey, boolean expanded, Consumer<Integer> onClick) {
      GDRowRenderer header = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_GOLD, 0.75F, true);
      header.addColumn(I18n.get(titleKey, new Object[0]), -1, -1, true, false, onClick);
      header.addColumn(expanded ? "▼" : "▶", 20, -1, true, true, onClick);
      this.contentRenderers.add(header);
      this.rowHeights.add(17);
   }

   private void addSubCategoryHeader(String titleKey, boolean expanded, Consumer<Integer> onClick) {
      GDRowRenderer header = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, true);
      String title = "  " + I18n.get(titleKey, new Object[0]);
      header.addColumn(title, -1, -1, true, false, onClick);
      header.addColumn(expanded ? "▼" : "▶", 20, -5592406, true, true, onClick);
      this.contentRenderers.add(header);
      this.rowHeights.add(17);
   }

   private void addIndentedHelpEntry(String titleKey, String descKey, int contentWidth) {
      String title = I18nCompat.exists(titleKey) ? I18n.get(titleKey, new Object[0]) : titleKey;
      String desc = I18nCompat.exists(descKey) ? I18n.get(descKey, new Object[0]) : descKey;
      title = "    " + title;
      HelpTextEntry entry = new HelpTextEntry(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, title, desc);
      int height = entry.getRequiredHeight(contentWidth);
      this.contentRenderers.add(entry);
      this.rowHeights.add(height);
   }

   private void addHelpEntry(String titleKey, String descKey, int contentWidth) {
      String title = I18nCompat.exists(titleKey) ? I18n.get(titleKey, new Object[0]) : titleKey;
      String desc = I18nCompat.exists(descKey) ? I18n.get(descKey, new Object[0]) : descKey;
      HelpTextEntry entry = new HelpTextEntry(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, title, desc);
      int height = entry.getRequiredHeight(contentWidth);
      this.contentRenderers.add(entry);
      this.rowHeights.add(height);
   }

   private void addPrefixedBonusHelpEntry(int bonusType, String bonusName, String titleKey, String descKey, int contentWidth) {
      String title = I18nCompat.exists(titleKey) ? I18n.get(titleKey, new Object[0]) : titleKey;
      String desc = I18nCompat.exists(descKey) ? I18n.get(descKey, new Object[0]) : descKey;
      List<GDTextRenderer.ColoredText> titleParts = new ArrayList<>();
      String prefix = "[" + bonusType + "] ";
      titleParts.add(new GDTextRenderer.ColoredText(prefix, -5592406));
      titleParts.add(new GDTextRenderer.ColoredText(bonusName, -10066330));
      titleParts.add(new GDTextRenderer.ColoredText(" " + title, GuiConstants.COLOR_GOLD));
      HelpTextEntry entry = new HelpTextEntry(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, titleParts, desc);
      int height = entry.getRequiredHeight(contentWidth);
      this.contentRenderers.add(entry);
      this.rowHeights.add(height);
   }

   private void addPrefixedHonorHelpEntry(String honorId, String titleKey, String descKey, int contentWidth) {
      String title = I18nCompat.exists(titleKey) ? I18n.get(titleKey, new Object[0]) : titleKey;
      String desc = I18nCompat.exists(descKey) ? I18n.get(descKey, new Object[0]) : descKey;
      HonorDefinition def = HonorRegistry.get(honorId);
      long count = ClientStatsManager.getHonorCount(honorId);
      int required = def != null ? def.unlockRequired() : 1;
      boolean unlocked = required > 0 && (double)count / (double)required >= 1.0;
      int dimColor = -10066330;
      List<GDTextRenderer.ColoredText> titleParts = new ArrayList<>();
      titleParts.add(new GDTextRenderer.ColoredText("[" + count + "/" + required + "] ", unlocked ? -5592406 : dimColor));
      titleParts.add(new GDTextRenderer.ColoredText(honorId.toUpperCase() + " ", -5592406));
      titleParts.add(new GDTextRenderer.ColoredText(title, unlocked ? GuiConstants.COLOR_GOLD : dimColor));
      HelpTextEntry entry = new HelpTextEntry(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, titleParts, desc, unlocked ? -1 : -5592406);
      int height = entry.getRequiredHeight(contentWidth);
      this.contentRenderers.add(entry);
      this.rowHeights.add(height);
   }

   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      this.updateAreaCoordinates(screenWidth, screenHeight);
      this.renderArea3(guiGraphics, mouseX, mouseY, partialTick);
      this.renderArea2(guiGraphics, mouseX, mouseY, partialTick, screenHeight);
      long now = System.nanoTime();
      if (this.lastFrameTime == 0L) {
         this.lastFrameTime = now;
      }

      float dt = (float)(now - this.lastFrameTime) / 1.0E9F;
      this.lastFrameTime = now;
      if (dt > 0.1F) {
         dt = 0.1F;
      }

      int currentContentWidth = this.area2X2 - this.area2X1;
      if (currentContentWidth != this.lastContentWidth && currentContentWidth > 0) {
         this.lastContentWidth = currentContentWidth;
         this.recalculateHeights(currentContentWidth);
      }

      if (this.isDragging) {
         double diff = (double)mouseY - this.lastMouseY;
         this.targetScrollY -= diff;
         this.lastMouseY = (double)mouseY;
      }

      if (this.isDragging3) {
         double diff = (double)mouseY - this.lastMouseY3;
         this.targetScrollY3 -= diff;
         this.lastMouseY3 = (double)mouseY;
      }

      this.updateScroll(dt, screenHeight);
   }

   private void updateAreaCoordinates(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      this.area2X1 = area1Right + 8;
      this.area2Y1 = 34;
      this.area2X2 = screenWidth - 8;
      this.area2Y2 = screenHeight - 8;
      this.area3X1 = 8;
      this.area3Y1 = this.area1Bottom + 8;
      this.area3X2 = area1Right;
      this.area3Y2 = screenHeight - 35 - 16;
      if (this.area3TextRenderer != null) {
         this.area3TextRenderer.setX1(this.area3X1);
         this.area3TextRenderer.setY1(this.area3Y1);
         this.area3TextRenderer.setX2(this.area3X2);
         this.area3TextRenderer.setY2(this.area3Y2);
      }
   }

   private void renderArea3(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.area3TextRenderer != null) {
         this.area3TextRenderer.render(guiGraphics, partialTick);
      }
   }

   private void recalculateHeights(int contentWidth) {
      this.rowHeights.clear();
      this.totalContentHeight = 0;

      for (GDRowRenderer renderer : this.contentRenderers) {
         int height;
         if (renderer instanceof HelpTextEntry) {
            height = ((HelpTextEntry)renderer).getRequiredHeight(contentWidth);
         } else {
            height = 17;
         }

         this.rowHeights.add(height);
         this.totalContentHeight += height + 1;
      }
   }

   private void renderArea2(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenHeight) {
      int x1 = this.area2X1;
      int x2 = this.area2X2;
      guiGraphics.enableScissor(this.area2X1, this.area2Y1, this.area2X2, this.area2Y2);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0, -this.scrollY, 0.0);
      int currentY = this.area2Y1;
      if (this.contentRenderers.isEmpty()) {
         this.rebuildContent();
      }

      for (int i = 0; i < this.contentRenderers.size(); i++) {
         GDRowRenderer renderer = this.contentRenderers.get(i);
         int height = this.rowHeights.get(i);
         renderer.setBounds(x1, currentY, x2, currentY + height);
         float actualTop = (float)currentY - (float)this.scrollY;
         float actualBottom = (float)(currentY + height) - (float)this.scrollY;
         if (actualBottom > (float)this.area2Y1 && actualTop < (float)this.area2Y2) {
            renderer.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY), partialTick);
         }

         currentY += height + 1;
      }

      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
   }

   private void expandAndScrollToCategory(int index) {
      if (index == 0) {
         this.isCommandExpanded = true;
      } else if (index == 1) {
         this.isBonusExpanded = true;
      } else if (index == 2) {
         this.isHonorExpanded = true;
      } else if (index == 3) {
         this.isPresetExpanded = true;
      }

      this.rebuildContent();
      this.scrollToCategory(index);
   }

   private void scrollToCategory(int index) {
      if (index >= 0 && index < this.categoryStartIndices.length) {
         int targetIndex = this.categoryStartIndices[index];
         if (targetIndex < this.rowHeights.size()) {
            double targetY = 0.0;

            for (int i = 0; i < targetIndex; i++) {
               targetY += (double)(this.rowHeights.get(i) + 1);
            }

            this.targetScrollY = targetY;
            double maxScroll = (double)Math.max(0, this.totalContentHeight - (this.area2Y2 - this.area2Y1));
            this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
         }
      }
   }

   @Override
   protected void updateScroll(float dt, int screenHeight) {
      this.totalContentHeight = 0;

      for (int h : this.rowHeights) {
         this.totalContentHeight += h + 1;
      }

      int viewHeight = this.area2Y2 - this.area2Y1;
      double maxScroll = (double)Math.max(0, this.totalContentHeight - viewHeight);
      this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
      double diff = this.targetScrollY - this.scrollY;
      if (Math.abs(diff) < 0.1) {
         this.scrollY = this.targetScrollY;
      } else {
         this.scrollY += diff * 15.0 * (double)dt;
      }

      if (this.area3TextRenderer != null) {
         float maxScroll3 = this.area3TextRenderer.getMaxScrollY();
         this.targetScrollY3 = Math.max(0.0, Math.min((double)maxScroll3, this.targetScrollY3));
         double diff3 = this.targetScrollY3 - this.scrollY3;
         if (Math.abs(diff3) < 0.1) {
            this.scrollY3 = this.targetScrollY3;
         } else {
            this.scrollY3 += diff3 * 15.0 * (double)dt;
         }

         this.area3TextRenderer.setScrollY((float)this.scrollY3);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (mouseX >= (double)this.area2X1 && mouseX <= (double)this.area2X2 && mouseY >= (double)this.area2Y1 && mouseY <= (double)this.area2Y2) {
         this.targetScrollY -= delta * 40.0;
         return true;
      } else if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
         this.targetScrollY3 -= delta * 40.0;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (mouseX >= (double)this.area3X1 && mouseX <= (double)this.area3X2 && mouseY >= (double)this.area3Y1 && mouseY <= (double)this.area3Y2) {
         this.isDragging3 = true;
         this.lastMouseY3 = mouseY;
         return true;
      } else if (mouseX >= (double)this.area2X1 && mouseX <= (double)this.area2X2 && mouseY >= (double)this.area2Y1 && mouseY <= (double)this.area2Y2) {
         double adjustedY = mouseY + this.scrollY;

         for (GDRowRenderer renderer : this.contentRenderers) {
            if (renderer.mouseClicked(mouseX, adjustedY, button)) {
               return true;
            }
         }

         this.isDragging = true;
         this.lastMouseY = mouseY;
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDragging = false;
      this.isDragging3 = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }
}
