package cn.bfops.killicon.client.gui.tabs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.InfiniteGridWidget;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;

public class SoundConfigContent extends ConfigTabContent {
   private final String presetId;
   private final Runnable onClose;
   private int calculatedBottom;
   private GDButton saveButton;
   private boolean isConfirmingReset = false;
   private long resetConfirmTime = 0L;
   private List<String> soundNames = new ArrayList<>();
   private String selectedSoundName;
   private InfiniteGridWidget gridWidget;
   private ExternalSoundManager.SoundData cachedSoundData;
   private String cachedSoundDataName;
   private boolean isCommonExpanded = false;
   private boolean isScrollingExpanded = false;
   private boolean isBattlefield1Expanded = false;
   private boolean isCardExpanded = false;
   private boolean isComboExpanded = false;
   private boolean isValorantExpanded = false;
   private boolean isHonorExpanded = false;
   private boolean isSelectingSound = false;
   private boolean isSelectOfficialExpanded = true;
   private boolean isSelectCustomExpanded = true;
   private ExternalSoundManager.SoundSlotDefinition selectedSlot = null;
   private boolean lastSelectingSound = false;

   public SoundConfigContent(Minecraft minecraft, String presetId, Runnable onClose) {
      super(minecraft, Component.translatable("battlefield_ops.client.gui.config.sound.title"));
      this.presetId = presetId;
      this.onClose = onClose;
      this.updateSoundRows();
   }

   @Override
   protected void updateSubtitle(int x1, int y1, int x2) {
      if (this.isSelectingSound) {
         String text = I18n.get("battlefield_ops.client.gui.config.sound.select.subtitle", new Object[0]);
         if (this.subtitleRenderer == null) {
            this.subtitleRenderer = new GDTextRenderer(text, x1, y1, x2, y1 + 9, 1.0F, -1, false);
         } else {
            this.subtitleRenderer.setX1(x1);
            this.subtitleRenderer.setY1(y1);
            this.subtitleRenderer.setX2(x2);
            this.subtitleRenderer.setY2(y1 + 9);
            this.subtitleRenderer.setText(text);
            this.subtitleRenderer.setColor(-1);
         }

         this.calculatedBottom = y1 + 9;
      } else {
         String presetName = ElementConfigManager.getPresetDisplayName(this.presetId);
         List<GDTextRenderer.ColoredText> presetTexts = new ArrayList<>();
         presetTexts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.config.generic.current_preset", new Object[0]), -1));
         presetTexts.add(new GDTextRenderer.ColoredText("[" + this.presetId + "] ", -5592406));
         presetTexts.add(new GDTextRenderer.ColoredText(presetName, GuiConstants.COLOR_GOLD));
         if (this.subtitleRenderer == null) {
            this.subtitleRenderer = new GDTextRenderer(presetTexts, x1, y1, x2, y1 + 9, 1.0F, false);
         } else {
            this.subtitleRenderer.setX1(x1);
            this.subtitleRenderer.setY1(y1);
            this.subtitleRenderer.setX2(x2);
            this.subtitleRenderer.setY2(y1 + 9);
            this.subtitleRenderer.setColoredTexts(presetTexts);
         }

         this.calculatedBottom = y1 + 9;
      }
   }

   @Override
   public void updateLayout(int screenWidth, int screenHeight) {
      super.updateLayout(screenWidth, screenHeight);
      this.area1Bottom = this.calculatedBottom;
      if (this.titleRenderer != null) {
         if (this.isSelectingSound) {
            this.titleRenderer.setText(I18n.get("battlefield_ops.client.gui.config.sound.select.title", new Object[0]));
         } else {
            this.titleRenderer.setText(I18n.get("battlefield_ops.client.gui.config.sound.title", new Object[0]));
         }
      }

      int padding = 8;
      int buttonHeight = 17;
      int row2Y = screenHeight - padding - buttonHeight;
      int row1Y = row2Y - 1 - buttonHeight;
      int gridY = this.calculatedBottom + padding;
      int area1Right = (screenWidth - 2 * padding) / 3 + padding;
      int gridWidth = area1Right - padding;
      int gridHeight = row1Y - gridY - padding;
      if (gridHeight > 0 && gridWidth > 0) {
         if (this.gridWidget == null) {
            this.gridWidget = new InfiniteGridWidget(padding, gridY, gridWidth, gridHeight);
         } else {
            this.gridWidget.setBounds(padding, gridY, gridWidth, gridHeight);
         }
      }
   }

   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      if (this.gridWidget != null) {
         this.gridWidget.render(guiGraphics, mouseX, mouseY, partialTick, null);
         if (this.selectedSoundName != null) {
            this.renderWaveform(guiGraphics, this.gridWidget.getX(), this.gridWidget.getY(), this.gridWidget.getWidth(), this.gridWidget.getHeight());
         }
      }

      super.renderContent(guiGraphics, mouseX, mouseY, partialTick, screenWidth, screenHeight, headerHeight);
   }

   private void renderWaveform(GuiGraphics guiGraphics, int x, int y, int width, int height) {
      if (!this.selectedSoundName.equals(this.cachedSoundDataName)) {
         this.cachedSoundData = ExternalSoundManager.getSoundData(this.selectedSoundName);
         this.cachedSoundDataName = this.selectedSoundName;
         if (this.cachedSoundData == null) {
            String baseName = this.selectedSoundName.replaceFirst("[.][^.]+$", "");
            this.cachedSoundData = ExternalSoundManager.getSoundData(baseName);
         }
      }

      if (this.cachedSoundData != null && this.cachedSoundData.pcmData != null && this.cachedSoundData.pcmData.length != 0) {
         String baseName = this.selectedSoundName.replaceFirst("[.][^.]+$", "");
         if (!ExternalSoundManager.isSoundPlaying(baseName) && !ExternalSoundManager.isSoundPlaying(this.selectedSoundName)) {
            boolean var32 = false;
         } else {
            boolean var10000 = true;
         }

         int samples = this.cachedSoundData.pcmData.length / 2;
         if (samples != 0) {
            float progress = 0.0F;
            if (ExternalSoundManager.isSoundPlaying(baseName) || ExternalSoundManager.isSoundPlaying(this.selectedSoundName)) {
               progress = ExternalSoundManager.getSoundProgress(baseName);
               if (progress == 0.0F) {
                  progress = ExternalSoundManager.getSoundProgress(this.selectedSoundName);
               }
            }

            guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            int centerY = y + (height + 2) / 2;
            int colorGold = GuiConstants.COLOR_GOLD;
            int colorPlayed = GuiConstants.COLOR_GOLD_ORANGE;
            guiGraphics.fill(x, centerY, x + width, centerY + 1, colorGold & 16777215 | 1073741824);
            double samplesPerPixel = (double)samples / (double)width;

            for (int px = 0; px < width; px++) {
               int startSample = (int)((double)px * samplesPerPixel);
               int endSample = (int)((double)(px + 1) * samplesPerPixel);
               if (endSample > samples) {
                  endSample = samples;
               }

               if (startSample < endSample) {
                  short min = 32767;
                  short max = -32768;

                  for (int i = startSample; i < endSample; i++) {
                     int idx = i * 2;
                     if (idx + 1 >= this.cachedSoundData.pcmData.length) {
                        break;
                     }

                     short sample = (short)(this.cachedSoundData.pcmData[idx + 1] << 8 | this.cachedSoundData.pcmData[idx] & 255);
                     if (sample < min) {
                        min = sample;
                     }

                     if (sample > max) {
                        max = sample;
                     }
                  }

                  if (min <= max) {
                     float normMin = (float)min / 32768.0F;
                     float normMax = (float)max / 32768.0F;
                     int hMax = (int)(normMax * (float)(height / 2 - 4));
                     int hMin = (int)(normMin * (float)(height / 2 - 4));
                     if (hMax == hMin) {
                        hMax++;
                     }

                     int y1_line = centerY - hMax;
                     int y2_line = centerY - hMin;
                     float currentProgressX = (float)px / (float)width;
                     int color = currentProgressX <= progress ? colorPlayed : colorGold;
                     guiGraphics.fill(x + px, y1_line, x + px + 1, y2_line, color);
                  }
               }
            }

            guiGraphics.disableScissor();
         }
      }
   }

   @Override
   protected void updateResetButtonState() {
      if (!this.isSelectingSound) {
         if (this.isConfirmingReset && this.resetButton != null) {
            long elapsed = System.currentTimeMillis() - this.resetConfirmTime;
            if (elapsed > 3000L) {
               this.isConfirmingReset = false;
               this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.reset_element"));
               this.resetButton.setTextColor(-1);
            } else {
               this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.confirm_reset"));
               this.resetButton.setTextColor(-65536);
            }
         }
      }
   }

   @Override
   protected void renderSideButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int buttonHeight = 17;
      int padding = 8;
      int row2Y = screenHeight - padding - buttonHeight;
      int row1Y = row2Y - 1 - buttonHeight;
      int totalWidth = area1Right - padding;
      int x1 = padding + (int)this.getSidebarOffset();
      int row1ButtonWidth = (totalWidth - 1) / 2;
      if (this.isSelectingSound) {
         if (this.resetButton == null) {
            this.resetButton = new GDButton(
               x1, row1Y, row1ButtonWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.config.sound.reset_current"), btn -> {
                  if (this.selectedSlot != null) {
                     ExternalSoundManager.resetSoundSelectionToDefault(this.presetId, this.selectedSlot.slotId());
                     this.selectedSoundName = ExternalSoundManager.getSelectedSoundBaseName(this.presetId, this.selectedSlot.slotId());
                     this.cachedSoundDataName = null;
                     this.updateSoundRows();
                  }
               }
            );
         }

         this.resetButton.setX(x1);
         this.resetButton.setY(row1Y);
         this.resetButton.setWidth(row1ButtonWidth);
         this.resetButton.render(guiGraphics, mouseX, mouseY, partialTick);
         if (this.cancelButton == null) {
            this.cancelButton = new GDButton(
               x1 + row1ButtonWidth + 1,
               row1Y,
               row1ButtonWidth,
               buttonHeight,
               Component.translatable("battlefield_ops.client.gui.button.cancel"),
               btn -> this.cancelSoundSelection()
            );
         }

         this.cancelButton.setX(x1 + row1ButtonWidth + 1);
         this.cancelButton.setY(row1Y);
         this.cancelButton.setWidth(row1ButtonWidth);
         this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         if (this.saveButton == null) {
            this.saveButton = new GDButton(
               x1,
               row2Y,
               totalWidth,
               buttonHeight,
               Component.translatable("battlefield_ops.client.gui.config.button.save_and_exit"),
               btn -> this.cancelSoundSelection()
            );
         }

         this.saveButton.setX(x1);
         this.saveButton.setY(row2Y);
         this.saveButton.setWidth(totalWidth);
         this.saveButton.render(guiGraphics, mouseX, mouseY, partialTick);
      } else {
         if (this.resetButton == null) {
            this.resetButton = new GDButton(
               x1, row1Y, row1ButtonWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.config.button.reset_element"), btn -> {
                  if (this.isConfirmingReset) {
                     ExternalSoundManager.markPendingSoundReset(this.presetId);
                     this.isConfirmingReset = false;
                     btn.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.reset_element"));
                     btn.setTextColor(-1);
                     if (this.onClose != null) {
                        this.onClose.run();
                     }
                  } else {
                     this.isConfirmingReset = true;
                     this.resetConfirmTime = System.currentTimeMillis();
                     btn.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.confirm_reset"));
                     btn.setTextColor(-65536);
                  }
               }
            );
         }

         this.resetButton.setX(x1);
         this.resetButton.setY(row1Y);
         this.resetButton.setWidth(row1ButtonWidth);
         this.resetButton.render(guiGraphics, mouseX, mouseY, partialTick);
         if (this.cancelButton == null) {
            this.cancelButton = new GDButton(
               x1 + row1ButtonWidth + 1,
               row1Y,
               row1ButtonWidth,
               buttonHeight,
               Component.translatable("battlefield_ops.client.gui.button.cancel"),
               btn -> this.discardSoundChangesAndClose()
            );
         }

         this.cancelButton.setX(x1 + row1ButtonWidth + 1);
         this.cancelButton.setY(row1Y);
         this.cancelButton.setWidth(row1ButtonWidth);
         this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         if (this.saveButton == null) {
            this.saveButton = new GDButton(
               x1, row2Y, totalWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.config.button.save_and_exit"), btn -> {
                  if (this.onClose != null) {
                     this.onClose.run();
                  }
               }
            );
         }

         this.saveButton.setX(x1);
         this.saveButton.setY(row2Y);
         this.saveButton.setWidth(totalWidth);
         this.saveButton.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (keyCode == 256) {
         if (this.isSelectingSound) {
            this.cancelSoundSelection();
         } else if (this.onClose != null) {
            this.onClose.run();
         }

         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.colorPickerDialog.isVisible()) {
         return this.colorPickerDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.resetButton != null && this.resetButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         return this.saveButton != null && this.saveButton.mouseClicked(mouseX, mouseY, button) ? true : super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public void onFilesDrop(List<Path> paths) {
      if (paths != null && !paths.isEmpty()) {
         if (this.isSelectingSound && this.selectedSlot != null) {
            Path oggPath = null;
            Path wavPath = null;

            for (Path path : paths) {
               String lower = path.toString().toLowerCase();
               if (lower.endsWith(".ogg")) {
                  oggPath = path;
                  break;
               }

               if (lower.endsWith(".wav")) {
                  wavPath = path;
               }
            }

            Path targetPath = oggPath != null ? oggPath : wavPath;
            if (targetPath == null) {
               this.promptDialog
                  .show(I18n.get("battlefield_ops.client.gui.prompt.sound_replace_invalid", new Object[0]), PromptDialog.PromptType.ERROR, null);
            } else {
               Runnable importAction = () -> {
                  String originalName = targetPath.getFileName().toString();
                  String baseName = ExternalSoundManager.createCustomSoundFromFile(this.presetId, targetPath, originalName);
                  if (baseName != null) {
                     this.isSelectCustomExpanded = true;
                     this.selectSoundForSlot(baseName);
                  } else {
                     this.promptDialog
                        .show(I18n.get("battlefield_ops.client.gui.prompt.sound_replace_fail", new Object[0]), PromptDialog.PromptType.ERROR, null);
                  }
               };
               importAction.run();
            }
         }
      }
   }

   @Override
   public void onTabOpen() {
      ExternalSoundManager.ensureSoundFilesForPreset(this.presetId, false);
      this.updateSoundRows();
      if (ClientConfigManager.shouldShowSoundIntro()) {
         ClientConfigManager.markSoundIntroShown();
         this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.sound_intro", new Object[0]), PromptDialog.PromptType.INFO, null);
      }
   }

   private void updateSoundRows() {
      this.configRows.clear();
      if (this.isSelectingSound != this.lastSelectingSound) {
         this.resetButton = null;
         this.cancelButton = null;
         this.saveButton = null;
         this.isConfirmingReset = false;
         this.lastSelectingSound = this.isSelectingSound;
      }

      if (this.isSelectingSound && ClientConfigManager.shouldShowSoundSelectIntro()) {
         ClientConfigManager.markSoundSelectIntroShown();
         this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.sound_select_intro", new Object[0]), PromptDialog.PromptType.INFO, null);
      }

      if (this.isSelectingSound && this.selectedSlot != null) {
         this.buildSoundSelectionRows();
      } else {
         this.buildSoundSlotRows();
      }

      if (this.minecraft != null) {
         this.updateConfigRowsLayout(this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight());
      }
   }

   private void discardSoundChangesAndClose() {
      if (this.onClose != null) {
         this.onClose.run();
      }
   }

   private void cancelSoundSelection() {
      this.isSelectingSound = false;
      this.updateSoundRows();
   }

   private void addCategoryHeader(String titleKey, boolean expanded, Runnable onToggle) {
      GDRowRenderer header = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_GOLD, 0.75F, true);
      header.addColumn(I18n.get(titleKey, new Object[0]), -1, -1, true, false, idx -> onToggle.run());
      header.addColumn(expanded ? "▼" : "▶", 20, -1, true, true, idx -> onToggle.run());
      this.configRows.add(header);
   }

   private void buildSoundSlotRows() {
      Map<ExternalSoundManager.SoundElementGroup, List<ExternalSoundManager.SoundSlotDefinition>> groupedSlots = new LinkedHashMap<>();

      for (ExternalSoundManager.SoundSlotDefinition slot : ExternalSoundManager.getSoundSlotDefinitions()) {
         groupedSlots.computeIfAbsent(slot.group(), key -> new ArrayList<>()).add(slot);
      }

      for (Entry<ExternalSoundManager.SoundElementGroup, List<ExternalSoundManager.SoundSlotDefinition>> entry : groupedSlots.entrySet()) {
         ExternalSoundManager.SoundElementGroup group = entry.getKey();
         boolean expanded = this.isGroupExpanded(group);
         this.addCategoryHeader(group.titleKey(), expanded, () -> {
            this.setGroupExpanded(group, !this.isGroupExpanded(group));
            this.updateSoundRows();
         });
         if (expanded) {
            for (ExternalSoundManager.SoundSlotDefinition slot : entry.getValue()) {
               this.addSoundSlotRow(slot);
            }
         }
      }
   }

   private boolean isGroupExpanded(ExternalSoundManager.SoundElementGroup group) {
      return switch (group) {
         case COMMON -> this.isCommonExpanded;
         case SCROLLING -> this.isScrollingExpanded;
         case BATTLEFIELD1 -> this.isBattlefield1Expanded;
         case CARD -> this.isCardExpanded;
         case COMBO -> this.isComboExpanded;
         case VALORANT -> this.isValorantExpanded;
         case HONOR -> this.isHonorExpanded;
      };
   }

   private void setGroupExpanded(ExternalSoundManager.SoundElementGroup group, boolean expanded) {
      switch (group) {
         case COMMON:
            this.isCommonExpanded = expanded;
            break;
         case SCROLLING:
            this.isScrollingExpanded = expanded;
            break;
         case BATTLEFIELD1:
            this.isBattlefield1Expanded = expanded;
            break;
         case CARD:
            this.isCardExpanded = expanded;
            break;
         case COMBO:
            this.isComboExpanded = expanded;
            break;
         case VALORANT:
            this.isValorantExpanded = expanded;
            break;
         case HONOR:
            this.isHonorExpanded = expanded;
      }
   }

   private void buildSoundSelectionRows() {
      String selectedBaseName = this.selectedSlot == null ? null : ExternalSoundManager.getSelectedSoundBaseName(this.presetId, this.selectedSlot.slotId());
      this.addCategoryHeader("battlefield_ops.client.gui.config.sound.select.group.official", this.isSelectOfficialExpanded, () -> {
         this.isSelectOfficialExpanded = !this.isSelectOfficialExpanded;
         this.updateSoundRows();
      });
      if (this.isSelectOfficialExpanded) {
         this.soundNames = new ArrayList<>(ExternalSoundManager.getOfficialSoundFileNames());
         Collections.sort(this.soundNames);

         for (String soundName : this.soundNames) {
            String baseName = soundName.replaceFirst("[.][^.]+$", "");
            this.addSelectableSoundRow(soundName, baseName, false, selectedBaseName);
         }
      }

      this.addCategoryHeader("battlefield_ops.client.gui.config.sound.select.group.custom", this.isSelectCustomExpanded, () -> {
         this.isSelectCustomExpanded = !this.isSelectCustomExpanded;
         this.updateSoundRows();
      });
      if (this.isSelectCustomExpanded) {
         for (String baseName : ExternalSoundManager.getCustomSoundBaseNames(this.presetId)) {
            String label = ExternalSoundManager.getSoundDisplayName(this.presetId, baseName);
            this.addSelectableSoundRow(label, baseName, true, selectedBaseName);
         }
      }
   }

   private void addImportSoundRow() {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, 1296319556, 0.3F, false);
      row.addColumn(I18n.get("battlefield_ops.client.gui.config.generic.import_audio_file", new Object[0]), -1, -1, false, false, null);
      row.addColumn(I18n.get("battlefield_ops.client.gui.action.open", new Object[0]), 60, -16711936, true, true, idx -> this.openSoundImportDialog());
      this.configRows.add(row);
   }

   private void openSoundImportDialog() {
      this.getTextInputDialog().show("", I18n.get("battlefield_ops.client.gui.prompt.sound_import_title", new Object[0]), input -> {
         Path path = this.tryParsePath(input);
         if (path != null) {
            this.onFilesDrop(List.of(path));
         }
      }, input -> this.isExistingFileWithExtension(input, "ogg", "wav"));
   }

   private Path tryParsePath(String rawInput) {
      if (rawInput == null) {
         return null;
      } else {
         String trimmed = rawInput.trim();
         if (trimmed.isEmpty()) {
            return null;
         } else {
            try {
               return Path.of(trimmed);
            } catch (Exception var4) {
               return null;
            }
         }
      }
   }

   private boolean isExistingFileWithExtension(String rawInput, String... extensions) {
      Path path = this.tryParsePath(rawInput);
      if (path != null && Files.isRegularFile(path) && extensions != null && extensions.length != 0) {
         String lower = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);

         for (String extension : extensions) {
            if (extension != null && !extension.isBlank() && lower.endsWith("." + extension.toLowerCase(Locale.ROOT))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void addSoundSlotRow(ExternalSoundManager.SoundSlotDefinition slot) {
      String baseName = ExternalSoundManager.getSelectedSoundBaseName(this.presetId, slot.slotId());
      String displayName = ExternalSoundManager.getSoundDisplayName(this.presetId, baseName);
      boolean modified = ExternalSoundManager.isSoundSelectionModified(this.presetId, slot.slotId());
      String extension = ExternalSoundManager.getSoundExtensionForPreset(this.presetId, baseName);
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.0F, false);
      row.addColumn(" " + I18n.get(slot.titleKey(), new Object[0]), -1, -1, false, false, idx -> {
         this.selectedSoundName = baseName;
         this.cachedSoundDataName = null;
      });
      row.addColumn(extension, 36, -5592406, false, true, null);
      row.addColumn("▶", 20, -1, true, true, idx -> {
         this.selectedSoundName = baseName;
         this.cachedSoundDataName = null;
         this.playPreviewSlot(slot);
      });
      int nameColor = modified ? GuiConstants.COLOR_GOLD : -5592406;
      row.addColumn(displayName, 80, nameColor, false, false, idx -> {
         this.selectedSlot = slot;
         this.selectedSoundName = baseName;
         this.isSelectingSound = true;
         this.updateSoundRows();
      });
      GDRowRenderer.Column hoverCol = new GDRowRenderer.Column();
      hoverCol.text = I18n.get("battlefield_ops.client.gui.config.sound.select.hover", new Object[0]);
      hoverCol.color = nameColor;
      hoverCol.isCentered = false;
      hoverCol.onClick = btn -> {
         this.selectedSlot = slot;
         this.selectedSoundName = baseName;
         this.isSelectingSound = true;
         this.updateSoundRows();
      };
      row.setColumnHoverReplacement(3, List.of(hoverCol));
      row.addColumn("↺", 17, modified ? GuiConstants.COLOR_GOLD : -5592406, true, true, idx -> {
         if (modified) {
            ExternalSoundManager.resetSoundSelectionToDefault(this.presetId, slot.slotId());
            this.selectedSoundName = ExternalSoundManager.getSelectedSoundBaseName(this.presetId, slot.slotId());
            this.cachedSoundDataName = null;
            this.updateSoundRows();
         }
      });
      this.configRows.add(row);
   }

   private void addSelectableSoundRow(String label, String baseName, boolean isCustom, String selectedBaseName) {
      boolean isSelected = baseName != null && baseName.equals(selectedBaseName);
      String extension = ExternalSoundManager.getSoundExtensionForPreset(this.presetId, baseName);
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.0F, false);
      row.addColumn(label, -1, isSelected ? GuiConstants.COLOR_GOLD : -1, false, false, idx -> {
         this.selectedSoundName = baseName;
         this.cachedSoundDataName = null;
      });
      row.addColumn(extension, 36, -5592406, false, true, null);
      row.addColumn("▶", 20, -1, true, true, idx -> {
         this.selectedSoundName = baseName;
         this.cachedSoundDataName = null;
         this.playPreviewSound(baseName);
      });
      if (isSelected) {
         row.addColumn(I18n.get("battlefield_ops.client.gui.config.sound.select.selected", new Object[0]), 36, -5592406, true, true, null);
      } else {
         row.addColumn(
            I18n.get("battlefield_ops.client.gui.config.sound.select", new Object[0]),
            36,
            GuiConstants.COLOR_GOLD,
            true,
            true,
            idx -> this.selectSoundForSlot(baseName)
         );
      }

      if (isCustom) {
         row.addColumn(
            "×",
            17,
            -65536,
            true,
            true,
            idx -> {
               boolean deleted = ExternalSoundManager.deleteCustomSound(this.presetId, baseName);
               if (!deleted) {
                  this.promptDialog
                     .show(I18n.get("battlefield_ops.client.gui.prompt.sound_delete_fail", new Object[0]), PromptDialog.PromptType.ERROR, null);
                  this.updateSoundRows();
               } else {
                  if (baseName.equals(this.selectedSoundName)) {
                     this.selectedSoundName = this.selectedSlot == null
                        ? null
                        : ExternalSoundManager.getSelectedSoundBaseName(this.presetId, this.selectedSlot.slotId());
                     this.cachedSoundData = null;
                     this.cachedSoundDataName = null;
                  }

                  this.updateSoundRows();
               }
            }
         );
      }

      this.configRows.add(row);
   }

   private void selectSoundForSlot(String baseName) {
      if (this.selectedSlot != null) {
         ExternalSoundManager.setSoundSelection(this.presetId, this.selectedSlot.slotId(), baseName);
         this.selectedSoundName = baseName;
         this.cachedSoundDataName = null;
         this.updateSoundRows();
      }
   }

   private void playPreviewSlot(ExternalSoundManager.SoundSlotDefinition slot) {
      if (slot != null) {
         if (slot.group() == ExternalSoundManager.SoundElementGroup.VALORANT) {
            ExternalSoundManager.playConfiguredSound(this.presetId, slot.slotId(), false, 1.0F);
         } else {
            ExternalSoundManager.playConfiguredSound(this.presetId, slot.slotId());
         }
      }
   }

   private void playPreviewSound(String baseName) {
      if (baseName != null && !baseName.isEmpty()) {
         ExternalSoundManager.playSound(baseName, false, 1.0F);
      }
   }
}
