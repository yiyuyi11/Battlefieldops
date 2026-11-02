package cn.bfops.killicon.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.tabs.ConfigTabContent;
import cn.bfops.killicon.client.gui.tabs.ElementConfigContent;
import cn.bfops.killicon.client.gui.tabs.GlobalConfigTab;
import cn.bfops.killicon.client.gui.tabs.HelpTab;
import cn.bfops.killicon.client.gui.tabs.HomeTab;
import cn.bfops.killicon.client.gui.tabs.PresetConfigTab;
import cn.bfops.killicon.client.gui.tabs.ScoreboardTab;

public class ConfigScreenHeader {
   private final Minecraft minecraft;
   private final Component title;
   private final Component subtitle;
   private long openTime;
   private long lastRenderTime;
   private int splitPoint;
   private double scrollX;
   private double targetScrollX;
   private double maxScroll;
   private boolean isDragging;
   private boolean isPressed;
   private double lastMouseX;
   private final List<ConfigScreenHeader.Tab> tabs = new ArrayList<>();
   private ConfigScreenHeader.Tab selectedTab;
   private GDButton saveExitButton;
   private ConfigTabContent overrideContent;

   public ConfigScreenHeader() {
      this.minecraft = Minecraft.getInstance();
      this.title = Component.translatable("battlefield_ops.client.gui.config.title");
      this.subtitle = Component.translatable("battlefield_ops.client.gui.config.subtitle");
      this.openTime = System.currentTimeMillis();
      this.lastRenderTime = System.currentTimeMillis();
      this.initTabs();
   }

   private void initTabs() {
      this.tabs.add(new ConfigScreenHeader.Tab(new HomeTab(this.minecraft)));
      this.tabs.add(new ConfigScreenHeader.Tab(new GlobalConfigTab(this.minecraft)));
      PresetConfigTab presetTab = new PresetConfigTab(this.minecraft);
      presetTab.setHeader(this);
      this.tabs.add(new ConfigScreenHeader.Tab(presetTab));
      this.tabs.add(new ConfigScreenHeader.Tab(new ScoreboardTab(this.minecraft)));
      this.tabs.add(new ConfigScreenHeader.Tab(new HelpTab(this.minecraft)));
      if (!this.tabs.isEmpty()) {
         this.selectedTab = this.tabs.get(0);
         this.selectedTab.isSelected = true;
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.saveExitButton != null && this.overrideContent == null && this.saveExitButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (button == 0 && mouseX > (double)this.splitPoint && mouseY <= 30.0) {
         double localMouseX = mouseX - (double)this.splitPoint + this.scrollX;

         for (ConfigScreenHeader.Tab tab : this.tabs) {
            if (localMouseX >= (double)tab.x
               && localMouseX <= (double)(tab.x + tab.width)
               && mouseY >= (double)tab.finalY
               && mouseY <= (double)(tab.finalY + tab.height)) {
               if (this.selectedTab != tab) {
                  this.selectedTab.isSelected = false;
                  this.selectedTab = tab;
                  this.selectedTab.isSelected = true;
                  this.selectedTab.content.onTabOpen();
                  this.overrideContent = null;
               }

               this.isDragging = false;
               this.isPressed = true;
               this.lastMouseX = mouseX;
               return true;
            }
         }

         this.isDragging = false;
         this.isPressed = true;
         this.lastMouseX = mouseX;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDragging = false;
      this.isPressed = false;
      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isDragging) {
         this.targetScrollX -= dragX;
         this.scrollX = this.targetScrollX;
         this.clampScroll();
         return true;
      } else if (this.isPressed && mouseY <= 45.0 && mouseX > (double)this.splitPoint) {
         this.isDragging = true;
         this.targetScrollX -= dragX;
         this.scrollX = this.targetScrollX;
         this.clampScroll();
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (mouseX > (double)this.splitPoint && mouseY <= 45.0) {
         this.targetScrollX -= delta * 40.0;
         this.clampScroll();
         return true;
      } else {
         return false;
      }
   }

   private void clampScroll() {
      this.targetScrollX = Mth.clamp(this.targetScrollX, 0.0, this.maxScroll);
      if (this.isDragging) {
         this.scrollX = this.targetScrollX;
      }
   }

   public void render(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, float partialTick) {
      long now = System.currentTimeMillis();
      float dt = (float)(now - this.lastRenderTime) / 1000.0F;
      this.lastRenderTime = now;
      ConfigTabContent activeTab = this.getSelectedTabContent();
      int screenHeight = guiGraphics.guiHeight();
      if (activeTab != null) {
         activeTab.updateLayout(screenWidth, screenHeight);
      }

      this.calculateLayout(screenWidth);
      this.updateScroll(dt);
      this.updateFooterButtons(screenWidth, screenHeight);
      this.renderPart2(guiGraphics, screenWidth, mouseX, mouseY, now, dt);
      this.renderPart1(guiGraphics, now);
      this.renderIntroSlice(guiGraphics, screenWidth, now);
      if (this.saveExitButton != null && this.overrideContent == null) {
         this.saveExitButton.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      this.renderRegionBorders(guiGraphics, screenWidth, screenHeight);
   }

   private void updateFooterButtons(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int x = 8;
      int y = screenHeight - 8 - 17;
      int width = area1Right - 8;
      int height = 17;
      ConfigTabContent activeTab = this.getSelectedTabContent();
      if (activeTab != null) {
         float offset = activeTab.getSidebarOffset();
         x += (int)offset;
      }

      if (this.saveExitButton == null) {
         this.saveExitButton = new GDButton(x, y, width, height, Component.translatable("battlefield_ops.client.gui.config.button.save_and_exit"), btn -> {
            ConfigManager.saveChanges();
            if (this.minecraft.screen != null) {
               this.minecraft.screen.onClose();
            }
         });
      }

      this.saveExitButton.setX(x);
      this.saveExitButton.setY(y);
      this.saveExitButton.setWidth(width);
      this.saveExitButton.setHeight(height);
   }

   private void renderRegionBorders(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
      int goldBarBottom = 26;
      int area1Right = (screenWidth - 16) / 3 + 8;
      float sidebarOffset = 0.0F;
      boolean isSidebarFloating = false;
      ConfigTabContent activeTab = this.getSelectedTabContent();
      int area1Bottom;
      if (activeTab != null) {
         area1Bottom = activeTab.getArea1Bottom();
         sidebarOffset = activeTab.getSidebarOffset();
         isSidebarFloating = activeTab.isSidebarFloating();
      } else {
         area1Bottom = goldBarBottom + 8 + 40;
      }

      int area4Top;
      int translucentGray;
      int sideX1;
      int sideX2;
      label21: {
         area4Top = screenHeight - 8 - 35;
         translucentGray = -2136298838;
         sideX1 = 8 + (int)sidebarOffset;
         sideX2 = area1Right + (int)sidebarOffset;
         this.drawBorderRect(guiGraphics, sideX1, goldBarBottom + 8, sideX2, area1Bottom, translucentGray);
         int area2X1 = isSidebarFloating ? 8 : area1Right + 8;
         int area2Top = goldBarBottom + 8;
         if (activeTab instanceof ElementConfigContent elementContent && elementContent.isKillIconElement()) {
            area2Top += elementContent.getSecondaryTabHeight();
            this.drawBorderRectNoTop(guiGraphics, area2X1, area2Top, screenWidth - 8, screenHeight - 8, translucentGray);
            break label21;
         }

         this.drawBorderRect(guiGraphics, area2X1, area2Top, screenWidth - 8, screenHeight - 8, translucentGray);
      }

      this.drawBorderRect(guiGraphics, sideX1, area1Bottom + 8, sideX2, area4Top - 8, translucentGray);
      this.drawBorderRect(guiGraphics, sideX1, area4Top, sideX2, screenHeight - 8, translucentGray);
   }

   private void drawBorderRect(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
      guiGraphics.fill(x1, y1, x2, y1 + 1, color);
      guiGraphics.fill(x1, y2 - 1, x2, y2, color);
      guiGraphics.fill(x1, y1, x1 + 1, y2, color);
      guiGraphics.fill(x2 - 1, y1, x2, y2, color);
   }

   private void drawBorderRectNoTop(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
      guiGraphics.fill(x1, y2 - 1, x2, y2, color);
      guiGraphics.fill(x1, y1, x1 + 1, y2, color);
      guiGraphics.fill(x2 - 1, y1, x2, y2, color);
   }

   private void updateScroll(float dt) {
      if (!this.isDragging) {
         double diff = this.targetScrollX - this.scrollX;
         if (Math.abs(diff) < 0.1) {
            this.scrollX = this.targetScrollX;
         } else {
            this.scrollX += diff * 15.0 * (double)dt;
         }
      }
   }

   private void calculateLayout(int screenWidth) {
      int titleWidth = this.minecraft.font.width(this.title);
      int subtitleWidth = this.minecraft.font.width(this.subtitle);
      this.splitPoint = titleWidth + (int)((float)subtitleWidth * 0.5F) + 8 + 25;
      float currentX = 0.0F;

      for (ConfigScreenHeader.Tab tab : this.tabs) {
         tab.width = this.minecraft.font.width(tab.content.getTitle()) + 10;
         tab.x = (int)currentX;
         tab.finalY = 2;
         tab.height = 30;
         currentX += (float)(tab.width + 2);
      }

      float visibleWidth = (float)(screenWidth - this.splitPoint);
      this.maxScroll = (double)Math.max(0.0F, currentX - visibleWidth);
      if (this.targetScrollX > this.maxScroll) {
         this.targetScrollX = this.maxScroll;
      }

      if (this.scrollX > this.maxScroll) {
         this.scrollX = this.maxScroll;
      }
   }

   private void renderPart1(GuiGraphics guiGraphics, long now) {
      guiGraphics.fill(0, 0, this.splitPoint, 25, 1296319556);
      guiGraphics.fill(0, 25, this.splitPoint, 26, GuiConstants.COLOR_GOLD);
      guiGraphics.fillGradient(0, 20, this.splitPoint, 25, 0, -1728053248 | GuiConstants.COLOR_GOLD & 16777215);
      long elapsed = now - this.openTime;
      float ease = this.getEaseOutCubic(Math.min(1.0F, (float)elapsed / 600.0F));
      float animOffsetX = -100.0F * (1.0F - ease);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(animOffsetX + 8.0F, 8.0F, 0.0F);
      guiGraphics.drawString(this.minecraft.font, this.title, 0, 0, GuiConstants.COLOR_GOLD, true);
      guiGraphics.pose().popPose();
      float subtitleX = (float)(8 + this.minecraft.font.width(this.title) + 5);
      float subtitleY = 11.5F;
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(animOffsetX + subtitleX, subtitleY, 0.0F);
      guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
      guiGraphics.drawString(this.minecraft.font, this.subtitle, 0, 0, -5592406, true);
      guiGraphics.pose().popPose();
   }

   private void renderPart2(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, long now, float dt) {
      guiGraphics.enableScissor(this.splitPoint, 0, screenWidth, 35);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate((float)((double)this.splitPoint - this.scrollX), 0.0F, 0.0F);
      double localMouseX = (double)(mouseX - this.splitPoint) + this.scrollX;
      this.renderPart2Background(guiGraphics, screenWidth);
      this.renderTabs(guiGraphics, localMouseX, mouseY, now, dt);
      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
   }

   private void renderPart2Background(GuiGraphics guiGraphics, int screenWidth) {
      float drawEnd = Math.max((float)screenWidth + (float)this.maxScroll, 5000.0F);
      if (this.selectedTab == null) {
         guiGraphics.fill(0, 0, (int)drawEnd, 25, 1296319556);
         guiGraphics.fill(0, 25, (int)drawEnd, 26, GuiConstants.COLOR_GOLD);
         this.renderPart2Glow(guiGraphics, 0, (int)drawEnd);
      } else {
         if (this.selectedTab.x > 0) {
            guiGraphics.fill(0, 0, this.selectedTab.x, 25, 1296319556);
            guiGraphics.fill(0, 25, this.selectedTab.x, 26, GuiConstants.COLOR_GOLD);
            this.renderPart2Glow(guiGraphics, 0, this.selectedTab.x);
         }

         int tabRight = this.selectedTab.x + this.selectedTab.width;
         if ((float)tabRight < drawEnd) {
            guiGraphics.fill(tabRight, 0, (int)drawEnd, 25, 1296319556);
            guiGraphics.fill(tabRight, 25, (int)drawEnd, 26, GuiConstants.COLOR_GOLD);
            this.renderPart2Glow(guiGraphics, tabRight, (int)drawEnd);
         }
      }
   }

   private void renderPart2Glow(GuiGraphics guiGraphics, int startX, int endX) {
      int gradientStartY = 25;
      int gradientEndY = 20;
      guiGraphics.fillGradient(startX, gradientEndY, endX, gradientStartY, 0, -1728053248 | GuiConstants.COLOR_GOLD & 16777215);
   }

   private void renderTabs(GuiGraphics guiGraphics, double localMouseX, int mouseY, long now, float dt) {
      int selectedIndex = this.tabs.indexOf(this.selectedTab);

      for (int i = 0; i < this.tabs.size(); i++) {
         ConfigScreenHeader.Tab tab = this.tabs.get(i);
         boolean isSelected = tab == this.selectedTab;
         boolean hovered = localMouseX >= (double)tab.x
            && localMouseX <= (double)(tab.x + tab.width)
            && mouseY >= tab.finalY
            && mouseY <= tab.finalY + tab.height
            && mouseY <= 30;
         if (hovered) {
            tab.hoverProgress = Math.min(1.0F, tab.hoverProgress + 10.0F * dt);
         } else {
            tab.hoverProgress = Math.max(0.0F, tab.hoverProgress - 10.0F * dt);
         }

         if (isSelected) {
            tab.selectionProgress = Math.min(1.0F, tab.selectionProgress + 10.0F * dt);
         } else {
            tab.selectionProgress = Math.max(0.0F, tab.selectionProgress - 10.0F * dt);
         }

         int distance = Math.abs(i - selectedIndex);
         long delay = (long)distance * 100L;
         long tabIntroElapsed = now - (this.openTime + delay);
         float introEase = this.getEaseOutCubic(tabIntroElapsed > 0L ? Math.min(1.0F, (float)tabIntroElapsed / 600.0F) : 0.0F);
         float textCurrentY = 25.0F + (float)(tab.finalY - 25) * introEase;
         int alpha = Math.max(5, (int)(255.0F * introEase));
         int baseColor = isSelected ? -1 : (tab.hoverProgress > 0.0F ? -10066330 : -5592406);
         int colorWithAlpha = baseColor & 16777215 | alpha << 24;
         int textX = tab.x + (tab.width - this.minecraft.font.width(tab.content.getTitle())) / 2;
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate((float)textX, textCurrentY + 8.0F, 0.0F);
         guiGraphics.drawString(this.minecraft.font, tab.content.getTitle(), 0, 0, colorWithAlpha, true);
         guiGraphics.pose().popPose();
         if (tab.selectionProgress > 0.01F) {
            float borderEase = this.getEaseOutCubic(tab.selectionProgress);
            int borderCurrentY = (int)(25.0F + (float)(tab.finalY - 25) * borderEase);
            guiGraphics.fill(tab.x, borderCurrentY, tab.x + tab.width, borderCurrentY + 1, GuiConstants.COLOR_GOLD);
            guiGraphics.fill(tab.x, borderCurrentY, tab.x + 1, 26, GuiConstants.COLOR_GOLD);
            guiGraphics.fill(tab.x + tab.width - 1, borderCurrentY, tab.x + tab.width, 26, GuiConstants.COLOR_GOLD);
         }

         if (tab.hoverProgress > 0.01F) {
            float hoverEase = this.getEaseOutCubic(tab.hoverProgress);
            int hoverCurrentY = (int)(25.0F + (float)(tab.finalY - 25) * hoverEase);
            RenderSystem.enableBlend();
            guiGraphics.fill(tab.x, hoverCurrentY, tab.x + tab.width, hoverCurrentY + 1, 1082163328);
            guiGraphics.fill(tab.x, hoverCurrentY, tab.x + 1, 25, 1082163328);
            guiGraphics.fill(tab.x + tab.width - 1, hoverCurrentY, tab.x + tab.width, 25, 1082163328);
            RenderSystem.disableBlend();
         }
      }
   }

   private void renderIntroSlice(GuiGraphics guiGraphics, int screenWidth, long now) {
      long elapsed = now - this.openTime;
      if (elapsed <= 800L) {
         float t = (float)elapsed / 800.0F;
         float ease = this.getEaseOutCubic(t);
         float sliceWidth = 8.333333F;
         float currentX = (float)screenWidth * ease;
         float alpha = 1.0F - Math.min(1.0F, t * 3.0F);
         if (!(alpha <= 0.01F)) {
            int color = (int)(alpha * 255.0F) << 24 | 11184810;
            RenderSystem.enableBlend();
            guiGraphics.fill((int)currentX, 0, (int)(currentX + sliceWidth), 25, color);
            RenderSystem.disableBlend();
         }
      }
   }

   private float getEaseOutCubic(float t) {
      return 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
   }

   public void setSelectedTab(int index) {
      if (index >= 0 && index < this.tabs.size()) {
         ConfigScreenHeader.Tab tab = this.tabs.get(index);
         if (this.selectedTab != tab) {
            if (this.selectedTab != null) {
               this.selectedTab.isSelected = false;
            }

            this.selectedTab = tab;
            this.selectedTab.isSelected = true;
            this.selectedTab.content.onTabOpen();
         }
      }
   }

   public int getTabCount() {
      return this.tabs.size();
   }

   public ConfigTabContent getTabContent(int index) {
      return index >= 0 && index < this.tabs.size() ? this.tabs.get(index).content : null;
   }

   public ConfigTabContent getSelectedTabContent() {
      if (this.overrideContent != null) {
         return this.overrideContent;
      } else {
         return this.selectedTab != null ? this.selectedTab.content : null;
      }
   }

   public void setOverrideContent(ConfigTabContent content) {
      this.overrideContent = content;
      if (content != null) {
         content.onTabOpen();
      }
   }

   public void resetAnimation() {
      this.openTime = System.currentTimeMillis();
      this.lastRenderTime = System.currentTimeMillis();

      for (ConfigScreenHeader.Tab tab : this.tabs) {
         tab.selectionProgress = 0.0F;
         tab.hoverProgress = 0.0F;
      }
   }

   private static class Tab {
      ConfigTabContent content;
      int x;
      int width;
      int height;
      int finalY;
      boolean isSelected = false;
      float selectionProgress = 0.0F;
      float hoverProgress = 0.0F;

      public Tab(ConfigTabContent content) {
         this.content = content;
      }
   }
}
