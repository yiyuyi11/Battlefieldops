package cn.bfops.killicon.client.gui.tabs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.gui.elements.ChoiceListDialog;
import cn.bfops.killicon.client.gui.elements.ColorPickerDialog;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.elements.TextInputDialog;
import cn.bfops.killicon.client.util.I18nCompat;

public abstract class ConfigTabContent {
   protected final Minecraft minecraft;
   protected final Component title;
   protected GDTextRenderer titleRenderer;
   protected GDTextRenderer subtitleRenderer;
   protected int area1Bottom;
   protected final List<GDRowRenderer> configRows = new ArrayList<>();
   protected double scrollY = 0.0;
   protected double targetScrollY = 0.0;
   protected static final double SCROLL_SMOOTHING = 15.0;
   protected boolean useDefaultScroll = true;
   protected int totalContentHeight = 0;
   private GDTextRenderer dynamicTitleRenderer;
   private GDTextRenderer dynamicSubtitleRenderer;
   protected GDButton resetButton;
   protected GDButton cancelButton;
   protected boolean isResetConfirming = false;
   protected long resetConfirmTime = 0L;
   protected static final long RESET_CONFIRM_TIMEOUT = 3000L;
   protected TextInputDialog textInputDialog;
   protected ColorPickerDialog colorPickerDialog;
   protected ChoiceListDialog choiceListDialog;
   protected PromptDialog promptDialog;
   protected boolean isDragging = false;
   protected double lastMouseY = 0.0;
   protected long lastFrameTime = 0L;

   public ConfigTabContent(Minecraft minecraft, String titleKey) {
      this.minecraft = minecraft;
      this.title = Component.translatable(titleKey);
      this.textInputDialog = new TextInputDialog(minecraft, null, null);
      this.colorPickerDialog = new ColorPickerDialog(minecraft);
      this.choiceListDialog = new ChoiceListDialog(minecraft);
      this.promptDialog = new PromptDialog(minecraft, null, null);
   }

   public ConfigTabContent(Minecraft minecraft, Component title) {
      this.minecraft = minecraft;
      this.title = title;
      this.textInputDialog = new TextInputDialog(minecraft, null, null);
      this.colorPickerDialog = new ColorPickerDialog(minecraft);
      this.choiceListDialog = new ChoiceListDialog(minecraft);
      this.promptDialog = new PromptDialog(minecraft, null, null);
   }

   public TextInputDialog getTextInputDialog() {
      return this.textInputDialog;
   }

   public ColorPickerDialog getColorPickerDialog() {
      return this.colorPickerDialog;
   }

   public ChoiceListDialog getChoiceListDialog() {
      return this.choiceListDialog;
   }

   public PromptDialog getPromptDialog() {
      return this.promptDialog;
   }

   public List<GDRowRenderer> getConfigRows() {
      return this.configRows;
   }

   public void sortConfigRows() {
      if (this.configRows != null && !this.configRows.isEmpty()) {
         this.configRows.sort((a, b) -> {
            String keyA = a.getSortKey();
            String keyB = b.getSortKey();
            return keyA.compareToIgnoreCase(keyB);
         });
      }
   }

   public void onFilesDrop(List<Path> paths) {
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      this.updateResetButtonState();
      boolean isDialogVisible = this.textInputDialog.isVisible()
         || this.colorPickerDialog.isVisible()
         || this.choiceListDialog.isVisible()
         || this.promptDialog.isVisible();
      int effectiveMouseX = isDialogVisible ? -1 : mouseX;
      int effectiveMouseY = isDialogVisible ? -1 : mouseY;
      GDRowRenderer hoveredRow = null;

      for (GDRowRenderer row : this.configRows) {
         if (row.isHovered((double)effectiveMouseX, (double)effectiveMouseY)) {
            hoveredRow = row;
            break;
         }
      }

      if (this.titleRenderer != null) {
         this.titleRenderer.render(guiGraphics, partialTick);
      }

      if (this.subtitleRenderer != null) {
         this.subtitleRenderer.render(guiGraphics, partialTick);
      }

      this.renderContent(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick, screenWidth, screenHeight, headerHeight);
      this.renderSideButtons(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick, screenWidth, screenHeight);
      if (this instanceof GlobalConfigTab && hoveredRow != null && hoveredRow.getHoverTitle() != null) {
         this.renderDynamicDescription(guiGraphics, hoveredRow, partialTick);
      }

      if (this.textInputDialog.isVisible()) {
         this.textInputDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      if (this.colorPickerDialog.isVisible()) {
         this.colorPickerDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      if (this.choiceListDialog.isVisible()) {
         this.choiceListDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      if (this.promptDialog.isVisible()) {
         this.promptDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   public void openTextInput(String title, String initialValue, Consumer<String> onConfirm) {
      this.textInputDialog.show(title, initialValue, onConfirm);
   }

   protected void updateResetButtonState() {
      if (this.isResetConfirming) {
         long elapsed = System.currentTimeMillis() - this.resetConfirmTime;
         if (elapsed > 6000L) {
            this.isResetConfirming = false;
            if (this.resetButton != null) {
               this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.button.reset"));
               this.resetButton.setTextColor(-1);
            }
         } else if (elapsed >= 3000L) {
            if (this.resetButton != null) {
               this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.confirm_reset"));
               this.resetButton.setTextColor(-65536);
            }
         } else if (this.resetButton != null) {
            long remaining = 3L - elapsed / 1000L;
            this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.confirm_reset_time", new Object[]{remaining}));
            this.resetButton.setTextColor(-7667712);
         }
      }
   }

   protected void renderSideButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int buttonY = screenHeight - 8 - 17 - 1 - 17;
      int totalWidth = area1Right - 8;
      int buttonWidth = (totalWidth - 1) / 2;
      int buttonHeight = 17;
      int x1 = 8 + (int)this.getSidebarOffset();
      if (this.resetButton == null) {
         this.resetButton = new GDButton(x1, buttonY, buttonWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.button.reset"), btn -> {
            if (this.isResetConfirming) {
               long elapsed = System.currentTimeMillis() - this.resetConfirmTime;
               if (elapsed >= 3000L) {
                  ConfigManager.resetFull();
                  ConfigManager.discardChanges();
                  if (this.minecraft.screen != null) {
                     this.minecraft.screen.onClose();
                  }

                  this.isResetConfirming = false;
                  btn.setMessage(Component.translatable("battlefield_ops.client.gui.button.reset"));
                  btn.setTextColor(-1);
               }
            } else {
               this.isResetConfirming = true;
               this.resetConfirmTime = System.currentTimeMillis();
               btn.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.confirm_reset_time", new Object[]{3}));
               btn.setTextColor(-7667712);
            }
         });
      }

      this.resetButton.setX(x1);
      this.resetButton.setY(buttonY);
      this.resetButton.setWidth(buttonWidth);
      this.resetButton.setHeight(buttonHeight);
      this.resetButton.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.cancelButton == null) {
         this.cancelButton = new GDButton(
            x1 + buttonWidth + 1, buttonY, buttonWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.button.cancel"), btn -> {
               if (this.minecraft.screen != null) {
                  this.minecraft.screen.onClose();
               }
            }
         );
      }

      this.cancelButton.setX(x1 + buttonWidth + 1);
      this.cancelButton.setY(buttonY);
      this.cancelButton.setWidth(buttonWidth);
      this.cancelButton.setHeight(buttonHeight);
      this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   protected void renderDynamicDescription(GuiGraphics guiGraphics, GDRowRenderer row, float partialTick) {
      int area1Right = (this.minecraft.getWindow().getGuiScaledWidth() - 16) / 3 + 8;
      int x1 = 8;
      int y1 = this.area1Bottom + 8;
      int titleHeight = 18;
      if (this.dynamicTitleRenderer == null) {
         this.dynamicTitleRenderer = new GDTextRenderer(row.getHoverTitle(), x1, y1, area1Right, y1 + titleHeight, 2.0F, -1, false);
      } else {
         this.dynamicTitleRenderer.setX1(x1);
         this.dynamicTitleRenderer.setY1(y1);
         this.dynamicTitleRenderer.setX2(area1Right);
         this.dynamicTitleRenderer.setY2(y1 + titleHeight);
         this.dynamicTitleRenderer.setText(row.getHoverTitle());
      }

      this.dynamicTitleRenderer.render(guiGraphics, partialTick);
      int subY1 = y1 + titleHeight + 4;
      int buttonY = this.minecraft.getWindow().getGuiScaledHeight() - 8 - 35 - 1 - 35;
      int subY2 = buttonY - 8;
      if (this.dynamicSubtitleRenderer == null) {
         this.dynamicSubtitleRenderer = new GDTextRenderer(row.getHoverDescription(), x1, subY1, area1Right, subY2, 1.0F, -1, true);
      } else {
         this.dynamicSubtitleRenderer.setX1(x1);
         this.dynamicSubtitleRenderer.setY1(subY1);
         this.dynamicSubtitleRenderer.setX2(area1Right);
         this.dynamicSubtitleRenderer.setY2(subY2);
         this.dynamicSubtitleRenderer.setText(row.getHoverDescription());
      }

      this.dynamicSubtitleRenderer.render(guiGraphics, partialTick);
   }

   protected void updateScroll(float dt, int screenHeight) {
      if (this.useDefaultScroll) {
         int contentY = 34;
         int viewHeight = screenHeight - contentY - 8;
         double maxScroll = (double)Math.max(0, this.totalContentHeight - viewHeight);
         this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
         double diff = this.targetScrollY - this.scrollY;
         if (Math.abs(diff) < 0.01) {
            this.scrollY = this.targetScrollY;
         } else {
            this.scrollY += diff * 15.0 * (double)dt;
         }
      }
   }

   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      if (this.configRows.isEmpty()) {
         Component noContent = Component.translatable("battlefield_ops.client.gui.config.no_content");
         int textWidth = this.minecraft.font.width(noContent);
         int area1Right = (screenWidth - 16) / 3 + 8;
         int contentX = area1Right + 8 + (screenWidth - area1Right - 16 - textWidth) / 2;
         int contentY = 34 + (screenHeight - 34 - 9) / 2;
         guiGraphics.drawString(this.minecraft.font, noContent, contentX, contentY, -5592406, true);
      } else {
         if (this.useDefaultScroll) {
            long now = System.nanoTime();
            if (this.lastFrameTime == 0L) {
               this.lastFrameTime = now;
            }

            float dt = (float)(now - this.lastFrameTime) / 1.0E9F;
            this.lastFrameTime = now;
            if (dt > 0.1F) {
               dt = 0.1F;
            }

            if (this.isDragging) {
               double diff = (double)mouseY - this.lastMouseY;
               this.targetScrollY -= diff;
               this.lastMouseY = (double)mouseY;
            }

            this.updateScroll(dt, screenHeight);
            int area1Right = (screenWidth - 16) / 3 + 8;
            int contentX = area1Right + 8;
            int contentY = 34;
            int contentWidth = screenWidth - contentX - 8;
            int contentHeight = screenHeight - contentY - 8;
            guiGraphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, -this.scrollY, 0.0);

            for (GDRowRenderer row : this.configRows) {
               row.render(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY), partialTick);
            }

            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
         } else {
            for (GDRowRenderer row : this.configRows) {
               row.render(guiGraphics, mouseX, mouseY, partialTick);
            }
         }
      }
   }

   public Component getTitle() {
      return this.title;
   }

   public void onTabOpen() {
   }

   public float getSidebarOffset() {
      return 0.0F;
   }

   public boolean isSidebarFloating() {
      return false;
   }

   public void updateLayout(int screenWidth, int screenHeight) {
      int goldBarBottom = 26;
      int area1Right = (screenWidth - 16) / 3 + 8;
      int x1 = 8;
      int y1 = goldBarBottom + 8;
      int titleHeight = 27;
      int y2 = y1 + titleHeight;
      if (this.titleRenderer == null) {
         this.onTabOpen();
         this.titleRenderer = new GDTextRenderer(this.title.getString(), x1, y1, area1Right, y2, 3.0F, -1, false);
      } else {
         this.titleRenderer.setX1(x1);
         this.titleRenderer.setY1(y1);
         this.titleRenderer.setX2(area1Right);
         this.titleRenderer.setY2(y2);
      }

      int subY1 = y2 + 4;
      int subX1 = 8;
      this.updateSubtitle(subX1, subY1, area1Right);
      if (this.subtitleRenderer != null) {
         this.area1Bottom = subY1 + this.subtitleRenderer.getFinalHeight();
      } else {
         this.area1Bottom = y2;
      }

      this.updateConfigRowsLayout(screenWidth, screenHeight);
   }

   protected void updateConfigRowsLayout(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int contentX = area1Right + 8;
      int contentY = 34;
      int contentWidth = screenWidth - contentX - 8;
      int currentY = contentY;

      for (int i = 0; i < this.configRows.size(); i++) {
         GDRowRenderer row = this.configRows.get(i);
         int rowHeight = 17;
         row.setBounds(contentX, currentY, contentX + contentWidth, currentY + rowHeight);
         if (i % 2 != 0) {
            row.setBackgroundAlpha(0.15F);
         } else {
            row.setBackgroundAlpha(0.3F);
         }

         currentY += rowHeight + 1;
      }

      this.totalContentHeight = currentY - contentY;
   }

   protected void renderTabHeader(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float partialTick) {
      this.updateLayout(screenWidth, screenHeight);
   }

   protected void updateSubtitle(int x1, int y1, int x2) {
      String translationKey = "";
      if (this.title.getContents() instanceof TranslatableContents tc) {
         translationKey = tc.getKey();
      }

      String subKey = translationKey + ".subtitle";
      String subtitleText = I18nCompat.exists(subKey) ? I18n.get(subKey, new Object[0]) : "";
      if (this.subtitleRenderer == null) {
         this.subtitleRenderer = new GDTextRenderer(subtitleText, x1, y1, x2, y1 + 100, 1.0F, -5592406, true);
      } else {
         this.subtitleRenderer.setX1(x1);
         this.subtitleRenderer.setY1(y1);
         this.subtitleRenderer.setX2(x2);
         this.subtitleRenderer.setText(subtitleText);
      }
   }

   public int getArea1Bottom() {
      return this.area1Bottom;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.choiceListDialog.isVisible()) {
         return this.choiceListDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.resetButton != null && this.resetButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         if (this.useDefaultScroll) {
            int area1Right = (this.minecraft.getWindow().getGuiScaledWidth() - 16) / 3 + 8;
            int contentX = area1Right + 8;
            if (mouseX > (double)contentX) {
               double adjustedMouseY = mouseY + this.scrollY;

               for (GDRowRenderer row : this.configRows) {
                  if (row.mouseClicked(mouseX, adjustedMouseY, button)) {
                     return true;
                  }
               }

               this.isDragging = true;
               this.lastMouseY = mouseY;
               return true;
            }
         } else {
            for (GDRowRenderer rowx : this.configRows) {
               if (rowx.mouseClicked(mouseX, mouseY, button)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDragging = false;
      if (this.promptDialog.isVisible()) {
         return true;
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseReleased(mouseX, mouseY, button);
      } else {
         return this.choiceListDialog.isVisible() ? this.choiceListDialog.mouseReleased(mouseX, mouseY, button) : false;
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      return false;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.choiceListDialog.isVisible()) {
         return this.choiceListDialog.mouseScrolled(mouseX, mouseY, delta);
      } else {
         if (this.useDefaultScroll) {
            int area1Right = (this.minecraft.getWindow().getGuiScaledWidth() - 16) / 3 + 8;
            int contentX = area1Right + 8;
            if (mouseX > (double)contentX) {
               this.targetScrollY -= delta * 40.0;
               return true;
            }
         }

         return false;
      }
   }

   public boolean charTyped(char codePoint, int modifiers) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.charTyped(codePoint, modifiers);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.charTyped(codePoint, modifiers);
      } else {
         return this.choiceListDialog.isVisible() ? this.choiceListDialog.charTyped(codePoint, modifiers) : false;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.keyPressed(keyCode, scanCode, modifiers);
      } else {
         return this.choiceListDialog.isVisible() ? this.choiceListDialog.keyPressed(keyCode, scanCode, modifiers) : false;
      }
   }
}
