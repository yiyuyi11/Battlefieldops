package cn.bfops.killicon.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.tabs.ConfigTabContent;
import cn.bfops.killicon.client.gui.tabs.ElementConfigContent;
import cn.bfops.killicon.client.gui.tabs.ScoreboardTab;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;

public class MainConfigScreen extends Screen {
   private final Screen parent;
   private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("minecraft", "textures/block/gilded_blackstone.png");
   private final ConfigScreenHeader header;
   private boolean quickScoreboardMode = false;
   private GDButton btnExitNoSave;
   private GDButton btnCancel;
   private GDButton btnSaveExit;
   private boolean showExitConfirmation = false;
   private Runnable pendingExitAction;

   public MainConfigScreen(Screen parent) {
      super(Component.translatable("battlefield_ops.client.gui.config.title"));
      this.parent = parent;
      this.header = new ConfigScreenHeader();
      ConfigManager.startEditing();
   }

   public MainConfigScreen(Screen parent, int initialTabIndex) {
      this(parent);
      this.header.setSelectedTab(initialTabIndex);
   }

   public MainConfigScreen(Screen parent, int initialTabIndex, boolean quickScoreboardMode) {
      this(parent);
      this.quickScoreboardMode = quickScoreboardMode;
      this.header.setSelectedTab(initialTabIndex);
   }

   public Screen getParentScreen() {
      return this.parent;
   }

   protected void init() {
      super.init();
      int btnWidth = 100;
      int btnHeight = 17;
      int spacing = 1;
      int totalWidth = btnWidth * 3 + spacing * 2;
      int startX = (this.width - totalWidth) / 2;
      int textHeight = 9;
      int gap = 5;
      int groupHeight = textHeight + gap + btnHeight;
      int groupY = (this.height - groupHeight) / 2;
      int btnY = groupY + textHeight + gap;
      this.btnExitNoSave = new GDButton(
         startX,
         btnY,
         btnWidth,
         btnHeight,
         Component.translatable("battlefield_ops.client.gui.config.exit_dialog.exit_no_save"),
         btn -> this.pendingExitAction = () -> {
               ConfigManager.discardChanges();
               this.minecraft.setScreen(this.parent);
            }
      );
      this.btnCancel = new GDButton(
         startX + btnWidth + spacing,
         btnY,
         btnWidth,
         btnHeight,
         Component.translatable("battlefield_ops.client.gui.config.exit_dialog.return"),
         btn -> this.showExitConfirmation = false
      );
      this.btnSaveExit = new GDButton(
         startX + (btnWidth + spacing) * 2,
         btnY,
         btnWidth,
         btnHeight,
         Component.translatable("battlefield_ops.client.gui.config.exit_dialog.save_exit"),
         btn -> this.pendingExitAction = () -> {
               boolean showAceLogo = ClientConfigManager.isEnableAceLag() && ClientConfigManager.isAceLagConfigChangedInEdit();
               ConfigManager.saveChanges();
               this.minecraft.setScreen(this.parent);
               if (showAceLogo) {
                  HudElementManager.trigger("global", "ace_logo", IHudRenderer.TriggerContext.of(0, -1));
               }
            }
      );
      String languageCode = this.minecraft.options.languageCode;
      boolean languageChanged = ClientConfigManager.checkLanguageChangedAndUpdate(languageCode);
      ConfigTabContent activeTab = this.header.getSelectedTabContent();
      if (activeTab != null) {
         PromptDialog dialog = activeTab.getPromptDialog();
         Runnable showVersionPrompt = null;

         Runnable showVersionPromptFinal = showVersionPrompt;
         if (ClientConfigManager.shouldShowConfigIntro()) {
            ClientConfigManager.markConfigIntroShown();
            Runnable showThird = () -> dialog.show(
                  I18n.get("battlefield_ops.client.gui.prompt.config_intro_3", new Object[0]), PromptDialog.PromptType.INFO, showVersionPromptFinal
               );
            Runnable showSecond = () -> dialog.show(
                  I18n.get("battlefield_ops.client.gui.prompt.config_intro_2", new Object[0]), PromptDialog.PromptType.INFO, showThird
               );
            Runnable showFirst = () -> dialog.show(
                  I18n.get("battlefield_ops.client.gui.prompt.config_intro_1", new Object[0]), PromptDialog.PromptType.INFO, showSecond
               );
            if (languageChanged) {
               dialog.show(I18n.get("battlefield_ops.client.gui.prompt.language_changed", new Object[0]), PromptDialog.PromptType.INFO, showFirst);
            } else {
               showFirst.run();
            }
         } else if (languageChanged) {
            dialog.show(I18n.get("battlefield_ops.client.gui.prompt.language_changed", new Object[0]), PromptDialog.PromptType.INFO, showVersionPromptFinal);
         } else if (showVersionPromptFinal != null) {
            showVersionPromptFinal.run();
         }
      }
   }

   public void onClose() {
      HudElementManager.clearAllPreviews();
      if (ConfigManager.hasUnsavedChanges()) {
         this.showExitConfirmation = true;
      } else {
         ConfigManager.discardChanges();
         this.minecraft.setScreen(this.parent);
      }
   }

   public boolean isPauseScreen() {
      return !this.quickScoreboardMode;
   }

   public boolean isQuickScoreboardMode() {
      return this.quickScoreboardMode;
   }

   public void tick() {
      super.tick();
   }

   public boolean shouldCloseQuickScoreboardOnRelease() {
      if (this.quickScoreboardMode && !this.showExitConfirmation) {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         if (activeTab == null) {
            return true;
         } else if (!(activeTab instanceof ScoreboardTab)) {
            return false;
         } else if (activeTab.getChoiceListDialog().isVisible()) {
            return false;
         } else if (activeTab.getTextInputDialog().isVisible()) {
            return false;
         } else {
            return activeTab.getColorPickerDialog().isVisible() ? false : !activeTab.getPromptDialog().isVisible();
         }
      } else {
         return false;
      }
   }

   public boolean shouldCloseOnEsc() {
      return this.showExitConfirmation ? false : !this.quickScoreboardMode;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.showExitConfirmation) {
         if (this.minecraft.level == null) {
            this.renderGildedBlackstoneBackground(guiGraphics);
         } else {
            // The reference calls Minecraft's "transparent background" (m_280273_), which only lays a dark
            // gradient over the world. This must NOT be renderBackground: on 1.21.1 that also requests a
            // blurred screenshot and then pastes the menu background texture over the whole screen, and
            // because super.render() calls renderBackground again the screen ends up double-backgrounded
            // and the panels underneath are hidden behind an opaque blurred wash.
            this.renderBackground(guiGraphics);
         }

         int textHeight = 9;
         int gap = 5;
         int groupHeight = textHeight + gap + 17;
         int groupY = (this.height - groupHeight) / 2;
         guiGraphics.drawCenteredString(this.font, Component.translatable("battlefield_ops.client.gui.config.exit_dialog.title"), this.width / 2, groupY, -1);
         if (this.btnExitNoSave != null) {
            this.btnExitNoSave.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.btnCancel != null) {
            this.btnCancel.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.btnSaveExit != null) {
            this.btnSaveExit.render(guiGraphics, mouseX, mouseY, partialTick);
         }
      } else {
         if (this.minecraft.level == null) {
            this.renderGildedBlackstoneBackground(guiGraphics);
         } else {
            // See the note above: transparent backdrop, never the blurred menu background.
            this.renderBackground(guiGraphics);
         }

         this.header.render(guiGraphics, this.width, mouseX, mouseY, partialTick);
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         if (activeTab != null) {
            activeTab.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, 25);
         }

         super.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   /**
    * Forces the transparent backdrop for this screen.
    *
    * <p>{@link Screen#render} calls {@code renderBackground} itself, and on 1.21.1 that means a blurred
    * screenshot plus the menu background texture over the whole framebuffer. The config screen draws its own
    * chrome — header, tab strip, panels — and a blurred wash behind it reads as "the UI is half transparent
    * and I cannot see anything". Routing this to {@code renderTransparentBackground} keeps a single dim
    * gradient, which is what the reference gets by calling {@code m_280273_} explicitly on 1.20.1.
    *
    * <p>Kept as an override rather than only changing the explicit call above, because the {@code super.render}
    * inside this class would otherwise reintroduce the blurred background after the panels were drawn.
    */
   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.minecraft != null && this.minecraft.level == null) {
         this.renderGildedBlackstoneBackground(guiGraphics);
      } else {
         this.renderBackground(guiGraphics);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.showExitConfirmation) {
         if (this.btnExitNoSave != null && this.btnExitNoSave.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else if (this.btnCancel != null && this.btnCancel.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else {
            return this.btnSaveExit != null && this.btnSaveExit.mouseClicked(mouseX, mouseY, button) ? true : true;
         }
      } else if (this.header.mouseClicked(mouseX, mouseY, button)) {
         this.quickScoreboardMode = false;
         return true;
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         return activeTab != null && activeTab.mouseClicked(mouseX, mouseY, button) ? true : super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.showExitConfirmation) {
         if (this.pendingExitAction != null && button == 0) {
            Runnable action = this.pendingExitAction;
            this.pendingExitAction = null;
            action.run();
            return true;
         } else {
            return true;
         }
      } else if (this.header.mouseReleased(mouseX, mouseY, button)) {
         return true;
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         return activeTab != null && activeTab.mouseReleased(mouseX, mouseY, button) ? true : super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.showExitConfirmation) {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      } else if (this.header.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
         return true;
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         return activeTab != null && activeTab.mouseDragged(mouseX, mouseY, button, dragX, dragY) ? true : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public void onFilesDrop(List<Path> paths) {
      if (!this.showExitConfirmation) {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         if (activeTab != null) {
            activeTab.onFilesDrop(paths);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
      if (this.showExitConfirmation) {
         return super.mouseScrolled(mouseX, mouseY, delta);
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         if (activeTab instanceof ElementConfigContent elementContent
            && elementContent.isMouseInSecondaryTabArea(mouseX, mouseY)
            && activeTab.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
         }

         if (this.header.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
         } else {
            return activeTab != null && activeTab.mouseScrolled(mouseX, mouseY, delta) ? true : super.mouseScrolled(mouseX, mouseY, delta);
         }
      }
   }

   public boolean charTyped(char codePoint, int modifiers) {
      if (this.showExitConfirmation) {
         return super.charTyped(codePoint, modifiers);
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         return activeTab != null && activeTab.charTyped(codePoint, modifiers) ? true : super.charTyped(codePoint, modifiers);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.showExitConfirmation) {
         if (keyCode == 256) {
            this.showExitConfirmation = false;
            return true;
         } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      } else {
         ConfigTabContent activeTab = this.header.getSelectedTabContent();
         if (activeTab != null && activeTab.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         } else if (this.quickScoreboardMode && keyCode == 256) {
            this.onClose();
            return true;
         } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      }
   }

   private void renderGildedBlackstoneBackground(GuiGraphics guiGraphics) {
      RenderSystem.setShaderTexture(0, this.resolveBackgroundTexture());
      RenderSystem.setShaderColor(0.25F, 0.25F, 0.25F, 1.0F);
      int size = 32;
      int cols = this.width / size + 1;
      int rows = this.height / size + 1;

      for (int x = 0; x < cols; x++) {
         for (int y = 0; y < rows; y++) {
            guiGraphics.blit(this.resolveBackgroundTexture(), x * size, y * size, 0.0F, 0.0F, size, size, size, size);
         }
      }

      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private ResourceLocation resolveBackgroundTexture() {
      String material = ClientConfigManager.getGuiBackgroundMaterial();
      if (material != null && material.contains(":")) {
         String[] split = material.split(":", 2);
         if (split.length == 2 && !split[0].isEmpty() && !split[1].isEmpty()) {
            ResourceLocation texture = new ResourceLocation(split[0], "textures/block/" + split[1] + ".png");
            return this.minecraft != null && !this.minecraft.getResourceManager().getResource(texture).isEmpty() ? texture : BACKGROUND_TEXTURE;
         } else {
            return BACKGROUND_TEXTURE;
         }
      } else {
         return BACKGROUND_TEXTURE;
      }
   }
}
