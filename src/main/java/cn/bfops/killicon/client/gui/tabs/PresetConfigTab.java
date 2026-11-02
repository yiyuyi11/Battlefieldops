package cn.bfops.killicon.client.gui.tabs;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.config.PresetPackManager;
import cn.bfops.killicon.client.gui.ConfigScreenHeader;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.ElementPreview;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.screens.ElementConfigBuilder;
import cn.bfops.killicon.client.gui.screens.ElementConfigBuilderRegistry;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;
import cn.bfops.killicon.client.textures.ExternalTextureManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.client.util.I18nCompat;

public class PresetConfigTab extends ConfigTabContent {
   private final Map<String, ElementPreview> previewElements = new HashMap<>();
   private PresetConfigTab.PanelState state = PresetConfigTab.PanelState.HIDDEN;
   private boolean initialized = false;
   private float currentTranslation;
   private float targetTranslation;
   private long lastFrameTime;
   private static final int ANCHOR_SQUARE_SIZE = 10;
   private static final float ANCHOR_OVERLAY_MAX_ALPHA = 0.2F;
   private static final float ANCHOR_LINE_ALPHA = 0.4F;
   private static final long ANCHOR_FADE_IN_MS = 200L;
   private static final long ANCHOR_FADE_OUT_MS = 100L;
   private static final long ANCHOR_SWITCH_HOLD_MS = 1000L;
   private static final long ANCHOR_REVERT_MS = 500L;
   private float anchorOverlayAlpha = 0.0F;
   private boolean anchorOverlayShowing = false;
   private long anchorOverlayStateStartTime = 0L;
   private String candidateAnchorId = null;
   private float candidateProgress = 0.0F;
   private long candidateStartTime = 0L;
   private boolean candidateFadingOut = false;
   private long candidateFadeOutStartTime = 0L;
   private PresetConfigTab.PanelState rightPanelState = PresetConfigTab.PanelState.HIDDEN;
   private float currentRightTranslation;
   private float targetRightTranslation;
   private boolean rightPanelInitialized = false;
   private final List<GDRowRenderer> presetRows = new ArrayList<>();
   private double scrollY = 0.0;
   private double targetScrollY = 0.0;
   private static final double SCROLL_SMOOTHING = 15.0;
   private boolean isDragging = false;
   private double lastMouseY = 0.0;
   private int createRowIndex = -1;
   private static final int TRIGGER_ZONE_WIDTH = 8;
   private boolean isEditMode = false;
   private boolean isExportMode = false;
   private Set<String> resetCompletedStates = new HashSet<>();
   private Map<String, Long> resetCompletedTimes = new HashMap<>();
   private GDTextRenderer rightTitleRenderer;
   private GDTextRenderer rightInfoRenderer;
   private GDButton rightAddButton;
   private GDButton rightSoundButton;
   private boolean isAddElementExpanded = false;
   private List<GDRowRenderer> availableElementRows = new ArrayList<>();
   private double elementListScrollY = 0.0;
   private double targetElementListScrollY = 0.0;
   private boolean isElementListDragging = false;
   private double elementListLastMouseY = 0.0;
   private List<GDRowRenderer> currentElementRows = new ArrayList<>();
   private double contentScrollY = 0.0;
   private double targetContentScrollY = 0.0;
   private boolean isContentDragging = false;
   private double contentLastMouseY = 0.0;
   private boolean valorantPresetFolderExpanded = false;
   private boolean battlefieldPresetFolderExpanded = false;
   private boolean leftRequireExitBeforeAutoOpen = false;
   private boolean rightRequireExitBeforeAutoOpen = false;
   private boolean resetPanelPositions = false;
   private int lastScreenWidth = -1;
   private int lastScreenHeight = -1;
   private final Map<String, Deque<PresetConfigTab.UndoState>> undoStacks = new HashMap<>();
   private final Map<String, Deque<PresetConfigTab.UndoState>> redoStacks = new HashMap<>();
   private String draggingElementId = null;
   private JsonObject dragStartConfig = null;
   private ConfigScreenHeader header;
   private GDTextRenderer tutorialRenderer;
   private int tutorialCurrentStep = 9;
   private PresetConfigTab.GuidePhase tutorialPhase = PresetConfigTab.GuidePhase.ACTIVE;
   private long tutorialPhaseStartAt = 0L;
   private float tutorialExitDirection = 0.0F;
   private float tutorialTextWidth = 0.0F;
   private float tutorialTextHeight = 0.0F;
   private float tutorialInterruptKick = 0.0F;
   private float tutorialRenderX = 0.0F;
   private float tutorialRenderY = 0.0F;
   private long tutorialLastRenderAt = 0L;
   private boolean tutorialTriggeredLeft = false;
   private boolean tutorialTriggeredRight = false;

   public PresetConfigTab(Minecraft minecraft) {
      super(minecraft, "battlefield_ops.client.gui.config.tab.preset");
      this.lastFrameTime = Util.getMillis();
      this.rebuildPresetList();
   }

   public void setHeader(ConfigScreenHeader header) {
      this.header = header;
   }

   @Override
   public void onTabOpen() {
      super.onTabOpen();
      this.state = PresetConfigTab.PanelState.HIDDEN;
      this.rightPanelState = PresetConfigTab.PanelState.HIDDEN;
      this.leftRequireExitBeforeAutoOpen = true;
      this.rightRequireExitBeforeAutoOpen = true;
      this.resetPanelPositions = true;
      this.tutorialTriggeredLeft = false;
      this.tutorialTriggeredRight = false;
      this.tutorialRenderer = null;
      this.tutorialCurrentStep = ClientConfigManager.shouldRunPresetTutorial() ? ClientConfigManager.getPresetTutorialStep() : 9;
      this.tutorialPhase = PresetConfigTab.GuidePhase.ENTER;
      this.tutorialPhaseStartAt = System.currentTimeMillis();
      this.tutorialInterruptKick = 0.0F;
      this.tutorialLastRenderAt = 0L;
   }

   private void updatePreviews() {
      String currentId = ClientConfigManager.getCurrentPresetId();
      Set<String> elementIds = ElementConfigManager.getElementIds(currentId);
      this.previewElements.keySet().removeIf(id -> !elementIds.contains(id));

      for (String elementId : elementIds) {
         ElementPreview preview = this.previewElements.computeIfAbsent(elementId, ElementPreview::new);
         JsonObject config = ElementConfigManager.getElementConfig(currentId, elementId);
         preview.updateConfig(config);
      }
   }

   private void rebuildPresetList() {
      this.updatePreviews();
      this.updateAvailableElementRows();
      this.updateCurrentElementRows();
      this.presetRows.clear();
      this.createRowIndex = -1;
      Set<String> presets = ConfigManager.getPresetIds();
      List<String> sortedPresets = new ArrayList<>(presets);
      Collections.sort(sortedPresets);
      String currentId = ClientConfigManager.getCurrentPresetId();
      List<String> battlefieldPresets = new ArrayList<>();
      List<String> valorantPresets = new ArrayList<>();
      List<String> ungroupedOfficialPresets = new ArrayList<>();
      List<String> customPresets = new ArrayList<>();

      for (String id : sortedPresets) {
         if (this.isBattlefieldModePreset(id)) {
            battlefieldPresets.add(id);
         } else if (this.isValorantIconModePreset(id)) {
            valorantPresets.add(id);
         } else if (ElementConfigManager.isOfficialPreset(id)) {
            ungroupedOfficialPresets.add(id);
         } else {
            customPresets.add(id);
         }
      }

      int visualIndex = 0;

      for (String idx : ungroupedOfficialPresets) {
         this.addPresetRow(currentId, idx, visualIndex, 1);
         visualIndex++;
      }

      if (!battlefieldPresets.isEmpty()) {
         this.addBattlefieldPresetFolderRow();
         visualIndex++;
         if (this.battlefieldPresetFolderExpanded) {
            for (String battlefieldPresetId : battlefieldPresets) {
               this.addPresetRow(currentId, battlefieldPresetId, visualIndex, 3);
               visualIndex++;
            }
         }
      }

      if (!valorantPresets.isEmpty()) {
         this.addValorantPresetFolderRow();
         visualIndex++;
         if (this.valorantPresetFolderExpanded) {
            for (String valorantPresetId : valorantPresets) {
               this.addPresetRow(currentId, valorantPresetId, visualIndex, 3);
               visualIndex++;
            }
         }
      }

      for (String idx : customPresets) {
         this.addPresetRow(currentId, idx, visualIndex, 1);
         visualIndex++;
      }

      GDRowRenderer createRow = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, false);
      createRow.addColumn(I18n.get("battlefield_ops.client.gui.config.preset.create_new", new Object[0]), -1, -16711936, true, true, btn -> {
         String newId = ElementConfigManager.createNewPreset();
         ConfigManager.setCurrentPresetId(newId);
         this.rebuildPresetList();
      });
      this.createRowIndex = this.presetRows.size();
      this.presetRows.add(createRow);
      GDRowRenderer toggleRow = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, false);
      String toggleText = this.isEditMode
         ? I18n.get("battlefield_ops.client.gui.config.preset.exit_edit_mode", new Object[0])
         : I18n.get("battlefield_ops.client.gui.config.preset.enter_edit_mode", new Object[0]);
      int toggleColor = this.isEditMode ? -1 : -7876885;
      toggleRow.addColumn(toggleText, -1, toggleColor, true, true, btn -> {
         this.isEditMode = !this.isEditMode;
         if (this.isEditMode) {
            this.isExportMode = false;
         }

         this.rebuildPresetList();
      });
      this.presetRows.add(toggleRow);
      GDRowRenderer exportToggleRow = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.3F, false);
      String exportToggleText = this.isExportMode
         ? I18n.get("battlefield_ops.client.gui.config.preset.exit_export_mode", new Object[0])
         : I18n.get("battlefield_ops.client.gui.config.preset.enter_export_mode", new Object[0]);
      int exportToggleColor = this.isExportMode ? -1 : -7876885;
      exportToggleRow.addColumn(exportToggleText, -1, exportToggleColor, true, true, btn -> {
         this.isExportMode = !this.isExportMode;
         if (this.isExportMode) {
            this.isEditMode = false;
         }

         this.rebuildPresetList();
      });
      this.presetRows.add(exportToggleRow);
   }

   private void addPresetRow(String currentId, String id, int rowIndex, int indentSpaces) {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.0F, false);
      row.setBackgroundAlpha(rowIndex % 2 != 0 ? 0.15F : 0.3F);
      String displayName = " ".repeat(Math.max(1, indentSpaces)) + ElementConfigManager.getPresetDisplayName(id);
      int nameColor;
      if (ElementConfigManager.isOfficialPreset(id)) {
         nameColor = this.isEditMode ? -5592406 : GuiConstants.COLOR_GOLD;
      } else {
         nameColor = -1;
      }

      row.addColumn(displayName, -1, nameColor, false, false);
      int idWidth = this.isEditMode ? 60 : 54;
      int idColor = id.equals(currentId) ? GuiConstants.COLOR_GOLD : -1;
      if (this.isExportMode) {
         String exportLabel = I18n.get("battlefield_ops.client.gui.config.preset.action.export", new Object[0]);
         row.addColumn(exportLabel, idWidth, -1, true, true, btn -> this.openExportDialog(id));
      } else if (this.isEditMode) {
         if (ElementConfigManager.isOfficialPreset(id)) {
            boolean configReset = this.resetCompletedStates.contains(id + ":reset_all");
            String label = configReset
               ? I18n.get("battlefield_ops.client.gui.config.preset.action.reset_config.done", new Object[0])
               : I18n.get("battlefield_ops.client.gui.config.preset.action.reset_config", new Object[0]);
            row.addColumn(label, 60, configReset ? -5592406 : -1, true, true, configReset ? null : btn -> {
               ConfigManager.resetPresetConfig(id);
               ExternalTextureManager.markPendingTextureReset(id);
               ExternalSoundManager.markPendingSoundReset(id);
               this.resetCompletedStates.add(id + ":reset_all");
               this.resetCompletedTimes.put(id + ":reset_all", System.currentTimeMillis());
               this.rebuildPresetList();
            });
         } else {
            row.addColumn(
               I18n.get("battlefield_ops.client.gui.config.preset.action.edit_id", new Object[0]),
               30,
               -5592406,
               true,
               true,
               btn -> this.openRenameIdDialog(id)
            );
            row.addColumn(I18n.get("battlefield_ops.client.gui.config.preset.action.delete", new Object[0]), 30, -65536, true, true, btn -> {
               if (ElementConfigManager.deletePreset(id)) {
                  if (id.equals(ClientConfigManager.getCurrentPresetId())) {
                     ConfigManager.setCurrentPresetId("00001");
                  }

                  this.rebuildPresetList();
               }
            });
         }
      } else {
         row.addColumn(id, idWidth, idColor, true, true);
         row.getColumn(1).onClick = btn -> {
            if (btn == 0) {
               ConfigManager.setCurrentPresetId(id);
               this.rebuildPresetList();
            }
         };
      }

      if (this.isEditMode && !ElementConfigManager.isOfficialPreset(id)) {
         List<GDRowRenderer.Column> nameHoverCols = new ArrayList<>();
         nameHoverCols.add(
            this.createActionColumn(
               I18n.get("battlefield_ops.client.gui.config.preset.action.edit_name_tooltip", new Object[0]), -5592406, btn -> this.openRenameDialog(id)
            )
         );
         row.setColumnHoverReplacement(0, nameHoverCols);
      }

      this.presetRows.add(row);
   }

   private void addValorantPresetFolderRow() {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_GOLD, 0.75F, true);
      String label = I18n.get("battlefield_ops.client.gui.config.preset.folder.valorant_icon_mode", new Object[0]);
      row.addColumn(" " + label, -1, -1, true, false, button -> {
         this.valorantPresetFolderExpanded = !this.valorantPresetFolderExpanded;
         this.rebuildPresetList();
      });
      row.addColumn(this.valorantPresetFolderExpanded ? "▼" : "▶", 20, -1, true, true, button -> {
         this.valorantPresetFolderExpanded = !this.valorantPresetFolderExpanded;
         this.rebuildPresetList();
      });
      this.presetRows.add(row);
   }

   private void addBattlefieldPresetFolderRow() {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_GOLD, 0.75F, true);
      String label = I18n.get("battlefield_ops.client.gui.config.preset.folder.battlefield_icon_mode", new Object[0]);
      row.addColumn(" " + label, -1, -1, true, false, button -> {
         this.battlefieldPresetFolderExpanded = !this.battlefieldPresetFolderExpanded;
         this.rebuildPresetList();
      });
      row.addColumn(this.battlefieldPresetFolderExpanded ? "▼" : "▶", 20, -1, true, true, button -> {
         this.battlefieldPresetFolderExpanded = !this.battlefieldPresetFolderExpanded;
         this.rebuildPresetList();
      });
      this.presetRows.add(row);
   }

   private boolean isValorantIconModePreset(String presetId) {
      if (ElementConfigManager.isOfficialPreset(presetId) && presetId != null && presetId.length() == 5) {
         try {
            int id = Integer.parseInt(presetId);
            return id >= 9 && id <= 34;
         } catch (NumberFormatException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isBattlefieldModePreset(String presetId) {
      if (ElementConfigManager.isOfficialPreset(presetId) && presetId != null && presetId.length() == 5) {
         try {
            int id = Integer.parseInt(presetId);
            return id == 4 || id == 5 || id == 7 || id == 36 || id == 37;
         } catch (NumberFormatException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private int parsePresetNumericId(String presetId) {
      if (presetId != null && presetId.length() == 5) {
         try {
            return Integer.parseInt(presetId);
         } catch (NumberFormatException var3) {
            return Integer.MAX_VALUE;
         }
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private void openExportDialog(String presetId) {
      String currentName = ElementConfigManager.getPresetDisplayName(presetId);
      this.textInputDialog
         .show(
            currentName,
            I18n.get("battlefield_ops.client.gui.config.export.dialog.title", new Object[0]),
            exportName -> {
               boolean success = PresetPackManager.exportPreset(presetId, exportName);
               if (success) {
                  this.promptDialog
                     .show(
                        I18n.get("battlefield_ops.client.config.export.success.prompt", new Object[]{exportName}), PromptDialog.PromptType.SUCCESS, () -> {
                        }
                     );
                  this.openExportFolder();
               }
            }
         );
   }

   private void openExportFolder() {
      try {
         File dir = PresetPackManager.getExportDir().toFile();
         Util.getPlatform().openFile(dir);
      } catch (Exception var2) {
      }
   }

   private void openImportDuplicateDialog(File file, String originalId) {
      String randomId = PresetPackManager.generateRandomId();
      String prompt = I18n.get("battlefield_ops.client.config.import.duplicate_id", new Object[]{originalId});
      this.textInputDialog.show(randomId, prompt, newId -> {
         if (PresetPackManager.importPreset(file, newId)) {
            this.rebuildPresetList();
            this.promptDialog.show(I18n.get("battlefield_ops.client.config.import.success", new Object[]{newId}), PromptDialog.PromptType.SUCCESS, () -> {
            });
         }
      }, this.createPresetIdValidator(null, false));
   }

   @Override
   public void onFilesDrop(List<Path> paths) {
      for (Path path : paths) {
         File file = path.toFile();
         String name = file.getName().toLowerCase();
         if (name.endsWith(".gdpack") || name.endsWith(".zip")) {
            String status = PresetPackManager.checkImport(file);
            if ("valid".equals(status)) {
               String originalId = PresetPackManager.getPresetIdFromPack(file);
               if (originalId != null && ElementConfigManager.getActivePresets().containsKey(originalId)) {
                  this.openImportDuplicateDialog(file, originalId);
               } else {
                  String targetId = originalId != null ? originalId : PresetPackManager.generateRandomId();
                  if (ElementConfigManager.getActivePresets().containsKey(targetId)) {
                     this.openImportDuplicateDialog(file, targetId);
                  } else if (PresetPackManager.importPreset(file, targetId)) {
                     this.rebuildPresetList();
                     this.promptDialog
                        .show(I18n.get("battlefield_ops.client.config.import.success", new Object[]{targetId}), PromptDialog.PromptType.SUCCESS, () -> {
                        });
                  }
               }
            } else {
               ClientMessageLogger.chatError("battlefield_ops.client.config.import.invalid_format");
            }
         }
      }
   }

   private void updateAvailableElementRows() {
      this.availableElementRows.clear();
      String currentPresetId = ClientConfigManager.getCurrentPresetId();
      Set<String> availableTypes = ElementConfigManager.getAvailableElementTypes(currentPresetId);
      List<String> sortedTypes = new ArrayList<>(availableTypes);
      Collections.sort(sortedTypes);

      for (int i = 0; i < sortedTypes.size(); i++) {
         String type = sortedTypes.get(i);
         GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.0F, false);
         if (i % 2 != 0) {
            row.setBackgroundAlpha(0.15F);
         } else {
            row.setBackgroundAlpha(0.3F);
         }

         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText(" [" + type + "] ", -5592406));
         String nameKey = "battlefield_ops.element.name." + type.replace("/", ".");
         String name = I18nCompat.exists(nameKey) ? I18n.get(nameKey, new Object[0]) : type;
         texts.add(new GDTextRenderer.ColoredText(name, GuiConstants.COLOR_GOLD));
         row.addColoredColumn(texts, -1, false, false, null);
         row.addColumn(I18n.get("battlefield_ops.client.gui.config.preset.add_button", new Object[0]), 40, -1, true, true, btn -> {
            ElementConfigManager.addElement(currentPresetId, type);
            this.updateAvailableElementRows();
            this.updateCurrentElementRows();
            this.updatePreviews();
         });
         this.availableElementRows.add(row);
      }
   }

   private GDRowRenderer.Column createActionColumn(String text, int color, Consumer<Integer> onClick) {
      GDRowRenderer.Column col = new GDRowRenderer.Column();
      col.text = text;
      col.color = color;
      col.isCentered = true;
      col.onClick = onClick;
      col.isDarker = true;
      return col;
   }

   private GDRowRenderer.Column createSpacerColumn() {
      GDRowRenderer.Column col = new GDRowRenderer.Column();
      col.text = "";
      col.color = -5592406;
      col.isCentered = true;
      col.onClick = null;
      col.isDarker = true;
      return col;
   }

   private void openRenameDialog(String presetId) {
      String currentName = ElementConfigManager.getPresetDisplayName(presetId);
      this.textInputDialog.show(currentName, I18n.get("battlefield_ops.client.gui.config.dialog.enter_text", new Object[0]), newName -> {
         ElementConfigManager.setPresetDisplayName(presetId, newName);
         this.rebuildPresetList();
      });
   }

   private void openRenameIdDialog(String currentId) {
      this.textInputDialog.show(currentId, I18n.get("battlefield_ops.client.gui.config.dialog.enter_text", new Object[0]), newId -> {
         if (ElementConfigManager.renamePresetId(currentId, newId)) {
            if (currentId.equals(ClientConfigManager.getCurrentPresetId())) {
               ConfigManager.setCurrentPresetId(newId);
            }

            this.rebuildPresetList();
         }
      }, this.createPresetIdValidator(currentId, true));
   }

   private Predicate<String> createPresetIdValidator(String currentId, boolean allowSame) {
      return input -> {
         if (input == null || !input.matches("^\\d{5}$")) {
            return false;
         } else {
            return allowSame && currentId != null && input.equals(currentId) ? true : !ElementConfigManager.presetExists(input);
         }
      };
   }

   @Override
   public float getSidebarOffset() {
      return this.currentTranslation;
   }

   @Override
   public boolean isSidebarFloating() {
      return true;
   }

   @Override
   protected void updateConfigRowsLayout(int screenWidth, int screenHeight) {
      int contentX = 8;
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
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int panelWidth = area1Right + 8;
      this.updateResetButtonState();
      this.updatePresetResetState();
      boolean isDialogVisible = this.textInputDialog.isVisible() || this.promptDialog.isVisible();
      boolean isPanelOpen = this.state == PresetConfigTab.PanelState.OPEN;
      boolean isRightPanelOpen = this.rightPanelState == PresetConfigTab.PanelState.OPEN;
      int panelMouseX = isDialogVisible ? -10000 : mouseX;
      int panelMouseY = isDialogVisible ? -10000 : mouseY;
      int bgMouseX = !isDialogVisible && !isPanelOpen && !isRightPanelOpen ? mouseX : -10000;
      int bgMouseY = !isDialogVisible && !isPanelOpen && !isRightPanelOpen ? mouseY : -10000;
      long currentTime = Util.getMillis();
      float deltaTime = (float)(currentTime - this.lastFrameTime) / 1000.0F;
      this.lastFrameTime = currentTime;
      boolean sizeChanged = screenWidth != this.lastScreenWidth || screenHeight != this.lastScreenHeight;
      if (sizeChanged) {
         this.lastScreenWidth = screenWidth;
         this.lastScreenHeight = screenHeight;
         this.resetPanelPositions = true;
         this.leftRequireExitBeforeAutoOpen = true;
         this.rightRequireExitBeforeAutoOpen = true;
      }

      boolean rightPanelVisible = this.rightPanelState != PresetConfigTab.PanelState.HIDDEN || this.currentRightTranslation < (float)panelWidth - 0.5F;
      boolean leftPanelVisible = this.state != PresetConfigTab.PanelState.HIDDEN || this.currentTranslation > (float)(-panelWidth) + 0.5F;
      if (this.state == PresetConfigTab.PanelState.HIDDEN) {
         this.targetTranslation = (float)(-panelWidth);
         if (panelMouseX > 8) {
            this.leftRequireExitBeforeAutoOpen = false;
         }

         if (!isRightPanelOpen && panelMouseX >= 0 && panelMouseX <= 8 && !this.leftRequireExitBeforeAutoOpen && !this.isAnyElementPressed()) {
            this.state = PresetConfigTab.PanelState.OPEN;
         }
      } else if (this.state == PresetConfigTab.PanelState.PEEK) {
         this.targetTranslation = (float)(8 - panelWidth);
         if (panelMouseX <= 8) {
            this.state = PresetConfigTab.PanelState.OPEN;
         } else if (panelMouseX > 8) {
            this.state = PresetConfigTab.PanelState.HIDDEN;
         }
      } else if (this.state == PresetConfigTab.PanelState.OPEN) {
         this.targetTranslation = 0.0F;
         if (!rightPanelVisible && panelMouseX > panelWidth) {
            this.state = PresetConfigTab.PanelState.HIDDEN;
            this.resetLeftPanelModesIfNeeded();
         }
      }

      if (!this.initialized) {
         this.currentTranslation = (float)(-panelWidth);
         this.initialized = true;
      }

      if (this.resetPanelPositions) {
         if (this.state == PresetConfigTab.PanelState.OPEN) {
            this.currentTranslation = 0.0F;
            this.targetTranslation = 0.0F;
         } else if (this.state == PresetConfigTab.PanelState.PEEK) {
            this.currentTranslation = (float)(8 - panelWidth);
            this.targetTranslation = (float)(8 - panelWidth);
         } else {
            this.currentTranslation = (float)(-panelWidth);
            this.targetTranslation = (float)(-panelWidth);
         }
      }

      float speed = 15.0F;
      if (Math.abs(this.targetTranslation - this.currentTranslation) > 0.1F) {
         this.currentTranslation = this.currentTranslation + (this.targetTranslation - this.currentTranslation) * speed * deltaTime;
      } else {
         this.currentTranslation = this.targetTranslation;
      }

      int rightTriggerZoneStart = screenWidth - 8;
      if (this.rightPanelState == PresetConfigTab.PanelState.HIDDEN) {
         this.targetRightTranslation = (float)panelWidth;
         if (panelMouseX < rightTriggerZoneStart) {
            this.rightRequireExitBeforeAutoOpen = false;
         }

         if (!isPanelOpen
            && panelMouseX >= rightTriggerZoneStart
            && panelMouseX <= screenWidth
            && !this.rightRequireExitBeforeAutoOpen
            && !this.isAnyElementPressed()) {
            this.rightPanelState = PresetConfigTab.PanelState.OPEN;
         }
      } else if (this.rightPanelState == PresetConfigTab.PanelState.PEEK) {
         this.targetRightTranslation = (float)(panelWidth - 8);
         if (panelMouseX >= rightTriggerZoneStart) {
            this.rightPanelState = PresetConfigTab.PanelState.OPEN;
         } else if (panelMouseX < rightTriggerZoneStart) {
            this.rightPanelState = PresetConfigTab.PanelState.HIDDEN;
         }
      } else if (this.rightPanelState == PresetConfigTab.PanelState.OPEN) {
         this.targetRightTranslation = 0.0F;
         int rightPanelX = screenWidth - panelWidth;
         if (!leftPanelVisible && panelMouseX < rightPanelX) {
            this.rightPanelState = PresetConfigTab.PanelState.HIDDEN;
         }
      }

      if (!this.rightPanelInitialized) {
         this.currentRightTranslation = (float)panelWidth;
         this.rightPanelInitialized = true;
      }

      if (this.resetPanelPositions) {
         if (this.rightPanelState == PresetConfigTab.PanelState.OPEN) {
            this.currentRightTranslation = 0.0F;
            this.targetRightTranslation = 0.0F;
         } else if (this.rightPanelState == PresetConfigTab.PanelState.PEEK) {
            this.currentRightTranslation = (float)(panelWidth - 8);
            this.targetRightTranslation = (float)(panelWidth - 8);
         } else {
            this.currentRightTranslation = (float)panelWidth;
            this.targetRightTranslation = (float)panelWidth;
         }

         this.resetPanelPositions = false;
      }

      if (Math.abs(this.targetRightTranslation - this.currentRightTranslation) > 0.1F) {
         this.currentRightTranslation = this.currentRightTranslation + (this.targetRightTranslation - this.currentRightTranslation) * speed * deltaTime;
      } else {
         this.currentRightTranslation = this.targetRightTranslation;
      }

      if (!this.tutorialTriggeredLeft && this.state == PresetConfigTab.PanelState.OPEN) {
         this.tutorialTriggeredLeft = true;
         this.markTutorialAction(1);
      }

      if (!this.tutorialTriggeredRight && this.rightPanelState == PresetConfigTab.PanelState.OPEN) {
         this.tutorialTriggeredRight = true;
         this.markTutorialAction(2);
      }

      if (!this.configRows.isEmpty()) {
         for (GDRowRenderer row : this.configRows) {
            row.render(guiGraphics, panelMouseX, panelMouseY, partialTick);
         }
      }

      List<ElementPreview> renderList = new ArrayList<>(this.previewElements.values());

      for (ElementPreview preview : renderList) {
         preview.updatePosition(screenWidth, screenHeight);
      }

      this.renderAnchorOverlay(guiGraphics, partialTick, screenWidth, screenHeight, panelWidth, mouseX, mouseY);
      renderList.sort((a, b) -> {
         boolean aIsDragged = this.draggingElementId != null && a.getElementId().equals(this.draggingElementId);
         boolean bIsDragged = this.draggingElementId != null && b.getElementId().equals(this.draggingElementId);
         if (aIsDragged && !bIsDragged) {
            return 1;
         } else {
            return !aIsDragged && bIsDragged ? -1 : Integer.compare(a.getZIndex(), b.getZIndex());
         }
      });
      ElementPreview hoveredElement = null;
      if (this.draggingElementId != null && this.previewElements.containsKey(this.draggingElementId)) {
         hoveredElement = this.previewElements.get(this.draggingElementId);
      } else {
         for (int i = renderList.size() - 1; i >= 0; i--) {
            ElementPreview preview = renderList.get(i);
            if (preview.isMouseOver((double)bgMouseX, (double)bgMouseY)) {
               hoveredElement = preview;
               break;
            }
         }
      }

      for (ElementPreview preview : renderList) {
         preview.render(guiGraphics, partialTick, screenWidth, preview == hoveredElement, (double)bgMouseX, (double)bgMouseY);
      }

      this.renderTutorialOverlay(guiGraphics, partialTick, screenWidth, screenHeight);
      this.renderPanelDirectionArrows(guiGraphics, screenWidth, screenHeight);
      if (this.currentElementRows.isEmpty() && !isDialogVisible) {
         Font font = this.minecraft.font;
         String hintText = I18n.get("battlefield_ops.client.gui.config.preset.hint_add_element", new Object[0]);
         int goldBarBottom = 26;
         float leftVisibleWidth = Math.max(0.0F, this.currentTranslation + (float)panelWidth);
         float rightVisibleWidth = Math.max(0.0F, (float)panelWidth - this.currentRightTranslation);
         float area2X1 = Math.max(8.0F, leftVisibleWidth);
         float area2Y1 = (float)goldBarBottom + 8.0F;
         float area2X2 = Math.min((float)screenWidth - 8.0F, (float)screenWidth - rightVisibleWidth);
         float area2Y2 = (float)screenHeight - 8.0F;
         float padding = 8.0F;
         float maxWidthFloat = Math.max(0.0F, area2X2 - area2X1 - 2.0F * padding);
         int maxWidth = (int)maxWidthFloat;
         List<FormattedCharSequence> lines = font.split(Component.literal(hintText), maxWidth);
         float totalHeight = (float)(lines.size() * 9);
         float startY = area2Y1 + (area2Y2 - area2Y1 - totalHeight) / 2.0F;
         float centerX = (area2X1 + area2X2) / 2.0F;
         float currentY = startY;

         for (FormattedCharSequence line : lines) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, currentY, 0.0F);
            guiGraphics.drawCenteredString(font, line, 0, 0, -5592406);
            guiGraphics.pose().popPose();
            currentY += 9.0F;
         }
      }

      int top = 26;
      float visibleWidth = this.currentTranslation + (float)panelWidth;
      if (visibleWidth > 0.0F) {
         guiGraphics.enableScissor(0, top, (int)Math.ceil((double)visibleWidth), screenHeight);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(this.currentTranslation, 0.0F, 0.0F);
         guiGraphics.fill(0, top, panelWidth, screenHeight, 1296319556);
         if (this.titleRenderer != null) {
            this.titleRenderer.render(guiGraphics, partialTick);
         }

         if (this.subtitleRenderer != null) {
            this.subtitleRenderer.render(guiGraphics, partialTick);
         }

         GDRowRenderer hoveredRow = null;

         for (GDRowRenderer row : this.configRows) {
            if (row.isHovered((double)panelMouseX, (double)panelMouseY)) {
               hoveredRow = row;
               break;
            }
         }

         if (hoveredRow != null && hoveredRow.getHoverTitle() != null) {
            this.renderDynamicDescription(guiGraphics, hoveredRow, partialTick);
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
         this.renderPresetList(guiGraphics, panelMouseX, panelMouseY, partialTick, panelWidth, screenHeight, deltaTime);
         int buttonsScissorWidth = (int)Math.ceil((double)visibleWidth);
         guiGraphics.enableScissor(0, top, buttonsScissorWidth, screenHeight);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(this.currentTranslation, 0.0F, 0.0F);
         int translatedMouseX = (int)((float)panelMouseX - this.currentTranslation);
         this.renderSideButtonsInPanel(guiGraphics, translatedMouseX, panelMouseY, partialTick, screenWidth, screenHeight);
         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
      }

      float rightVisibleWidth = (float)panelWidth - this.currentRightTranslation;
      int rightPanelX = screenWidth - panelWidth;
      if (rightVisibleWidth > 0.0F) {
         int scissorX = (int)((float)screenWidth - rightVisibleWidth);
         guiGraphics.enableScissor(scissorX, top, screenWidth, screenHeight);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(this.currentRightTranslation, 0.0F, 0.0F);
         guiGraphics.fill(rightPanelX, top, rightPanelX + panelWidth, screenHeight, 1296319556);
         guiGraphics.pose().popPose();
         int contentX = (int)((float)rightPanelX + this.currentRightTranslation + 8.0F);
         int contentY = top + 8;
         int contentWidth = panelWidth - 16;
         int lineHeight = 9;
         int textSpacing = 4;
         int borderPadding = 1;
         int titleBoxHeight = borderPadding + lineHeight + textSpacing + lineHeight + borderPadding;
         int borderColor = -2136298838;
         guiGraphics.fill(contentX, contentY, contentX + contentWidth, contentY + 1, borderColor);
         guiGraphics.fill(contentX, contentY + titleBoxHeight - 1, contentX + contentWidth, contentY + titleBoxHeight, borderColor);
         guiGraphics.fill(contentX, contentY + 1, contentX + 1, contentY + titleBoxHeight - 1, borderColor);
         guiGraphics.fill(contentX + contentWidth - 1, contentY + 1, contentX + contentWidth, contentY + titleBoxHeight - 1, borderColor);
         if (this.rightTitleRenderer == null) {
            this.rightTitleRenderer = new GDTextRenderer(
               I18n.get("battlefield_ops.client.gui.config.preset.element_control_title", new Object[0]),
               contentX + borderPadding,
               contentY + borderPadding,
               contentX + contentWidth - borderPadding,
               contentY + borderPadding + lineHeight,
               1.0F,
               GuiConstants.COLOR_GOLD,
               false
            );
         } else {
            this.rightTitleRenderer.setX1(contentX + borderPadding);
            this.rightTitleRenderer.setY1(contentY + borderPadding);
            this.rightTitleRenderer.setX2(contentX + contentWidth - borderPadding);
            this.rightTitleRenderer.setY2(contentY + borderPadding + lineHeight);
         }

         this.rightTitleRenderer.renderInternal(guiGraphics, partialTick, true, deltaTime);
         String currentId = ClientConfigManager.getCurrentPresetId();
         String currentName = ElementConfigManager.getPresetDisplayName(currentId);
         List<GDTextRenderer.ColoredText> infoTexts = new ArrayList<>();
         infoTexts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.config.element.current_preset", new Object[0]), -1));
         infoTexts.add(new GDTextRenderer.ColoredText("[" + currentId + "]", -5592406));
         infoTexts.add(new GDTextRenderer.ColoredText(currentName, GuiConstants.COLOR_GOLD));
         int infoY = contentY + borderPadding + lineHeight + textSpacing;
         if (this.rightInfoRenderer == null) {
            this.rightInfoRenderer = new GDTextRenderer(
               infoTexts, contentX + borderPadding, infoY, contentX + contentWidth - borderPadding, infoY + lineHeight, 1.0F, false
            );
         } else {
            this.rightInfoRenderer.setX1(contentX + borderPadding);
            this.rightInfoRenderer.setY1(infoY);
            this.rightInfoRenderer.setX2(contentX + contentWidth - borderPadding);
            this.rightInfoRenderer.setY2(infoY + lineHeight);
            this.rightInfoRenderer.setColoredTexts(infoTexts);
         }

         this.rightInfoRenderer.renderInternal(guiGraphics, partialTick, true, deltaTime);
         int buttonHeight = 17;
         int buttonPadding = 1;
         int buttonSpacing = 1;
         int totalButtonsHeight = buttonHeight * 2 + buttonSpacing;
         int bottomBoxHeight;
         if (this.isAddElementExpanded) {
            bottomBoxHeight = totalButtonsHeight + 51 + 4;
         } else {
            bottomBoxHeight = buttonPadding + totalButtonsHeight + buttonPadding;
         }

         int bottomBoxY = screenHeight - 8 - bottomBoxHeight;
         guiGraphics.fill(contentX, bottomBoxY, contentX + contentWidth, bottomBoxY + 1, borderColor);
         guiGraphics.fill(contentX, bottomBoxY + bottomBoxHeight - 1, contentX + contentWidth, bottomBoxY + bottomBoxHeight, borderColor);
         guiGraphics.fill(contentX, bottomBoxY + 1, contentX + 1, bottomBoxY + bottomBoxHeight - 1, borderColor);
         guiGraphics.fill(contentX + contentWidth - 1, bottomBoxY + 1, contentX + contentWidth, bottomBoxY + bottomBoxHeight - 1, borderColor);
         if (this.isAddElementExpanded) {
            int listTop = bottomBoxY + 1;
            int soundButtonY = bottomBoxY + bottomBoxHeight - buttonPadding - buttonHeight;
            int addButtonY = soundButtonY - buttonSpacing - buttonHeight;
            int listBottom = addButtonY - 2;
            int listHeight = listBottom - listTop;
            this.updateElementListScroll(deltaTime, (double)mouseY, listHeight);
            guiGraphics.enableScissor(contentX + 1, listTop, contentX + contentWidth - 1, listBottom);
            if (this.availableElementRows.isEmpty()) {
               guiGraphics.drawCenteredString(
                  Minecraft.getInstance().font,
                  I18n.get("battlefield_ops.client.gui.config.preset.all_elements_added", new Object[0]),
                  contentX + contentWidth / 2,
                  listTop + (listBottom - listTop) / 2 - 4,
                  -5592406
               );
            } else {
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(0.0F, (float)(-this.elementListScrollY), 0.0F);
               int currentY = listTop;

               for (GDRowRenderer rowx : this.availableElementRows) {
                  rowx.setX1(contentX + 1);
                  rowx.setY1(currentY);
                  rowx.setX2(contentX + contentWidth - 1);
                  rowx.setY2(currentY + 17);
                  double scrolledY = (double)currentY - this.elementListScrollY;
                  if (scrolledY + 17.0 > (double)listTop && scrolledY < (double)listBottom) {
                     rowx.render(guiGraphics, mouseX, (int)((double)mouseY + this.elementListScrollY), partialTick);
                  }

                  currentY += 18;
               }

               guiGraphics.pose().popPose();
            }

            guiGraphics.disableScissor();
            guiGraphics.enableScissor(scissorX, top, screenWidth, screenHeight);
         }

         int soundButtonY = bottomBoxY + bottomBoxHeight - buttonPadding - buttonHeight;
         int addButtonY = soundButtonY - buttonSpacing - buttonHeight;
         if (this.rightAddButton == null) {
            this.rightAddButton = new GDButton(
               contentX + buttonPadding,
               addButtonY,
               contentWidth - 2 * buttonPadding,
               buttonHeight,
               Component.translatable("battlefield_ops.client.gui.config.preset.add_element"),
               btn -> {
                  this.isAddElementExpanded = !this.isAddElementExpanded;
                  if (this.isAddElementExpanded) {
                     btn.setMessage(Component.translatable("battlefield_ops.client.gui.config.preset.close_selection"));
                     this.updateAvailableElementRows();
                  } else {
                     btn.setMessage(Component.translatable("battlefield_ops.client.gui.config.preset.add_element"));
                  }
               }
            );
         } else {
            this.rightAddButton.setX(contentX + buttonPadding);
            this.rightAddButton.setY(addButtonY);
            this.rightAddButton.setWidth(contentWidth - 2 * buttonPadding);
         }

         this.rightAddButton.render(guiGraphics, mouseX, mouseY, partialTick);
         if (this.rightSoundButton == null) {
            this.rightSoundButton = new GDButton(
               contentX + buttonPadding,
               soundButtonY,
               contentWidth - 2 * buttonPadding,
               buttonHeight,
               Component.translatable("battlefield_ops.client.gui.config.preset.configure_sound"),
               btn -> {
                  if (this.header != null) {
                     String currentPresetId = ClientConfigManager.getCurrentPresetId();
                     this.header.setOverrideContent(new SoundConfigContent(this.minecraft, currentPresetId, () -> this.header.setOverrideContent(null)));
                  }
               }
            );
         } else {
            this.rightSoundButton.setX(contentX + buttonPadding);
            this.rightSoundButton.setY(soundButtonY);
            this.rightSoundButton.setWidth(contentWidth - 2 * buttonPadding);
         }

         this.rightSoundButton.render(guiGraphics, mouseX, mouseY, partialTick);
         int middleBoxY = contentY + titleBoxHeight + 8;
         int middleBoxBottom = bottomBoxY - 8;
         int middleBoxHeight = middleBoxBottom - middleBoxY;
         if (middleBoxHeight > 0) {
            guiGraphics.fill(contentX, middleBoxY, contentX + contentWidth, middleBoxY + 1, borderColor);
            guiGraphics.fill(contentX, middleBoxBottom - 1, contentX + contentWidth, middleBoxBottom, borderColor);
            guiGraphics.fill(contentX, middleBoxY + 1, contentX + 1, middleBoxBottom - 1, borderColor);
            guiGraphics.fill(contentX + contentWidth - 1, middleBoxY + 1, contentX + contentWidth, middleBoxBottom - 1, borderColor);
            int contentListTop = middleBoxY + 1;
            int contentListBottom = middleBoxBottom - 1;
            int contentListHeight = contentListBottom - contentListTop;
            if (this.currentElementRows.isEmpty()) {
               guiGraphics.drawCenteredString(
                  Minecraft.getInstance().font,
                  Component.translatable("battlefield_ops.client.gui.config.preset.no_elements"),
                  contentX + contentWidth / 2,
                  middleBoxY + middleBoxHeight / 2 - 4,
                  -5592406
               );
            } else {
               this.updateContentScroll(deltaTime, (double)mouseY, contentListHeight);
               guiGraphics.enableScissor(contentX + 1, contentListTop, contentX + contentWidth - 1, contentListBottom);
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(0.0F, (float)(-this.contentScrollY), 0.0F);
               int currentY = contentListTop;

               for (GDRowRenderer rowx : this.currentElementRows) {
                  rowx.setX1(contentX + 1);
                  rowx.setY1(currentY);
                  rowx.setX2(contentX + contentWidth - 1);
                  rowx.setY2(currentY + 17);
                  double scrolledY = (double)currentY - this.contentScrollY;
                  if (scrolledY + 17.0 > (double)contentListTop && scrolledY < (double)contentListBottom) {
                     rowx.render(guiGraphics, mouseX, (int)((double)mouseY + this.contentScrollY), partialTick);
                  }

                  currentY += 18;
               }

               guiGraphics.pose().popPose();
               guiGraphics.disableScissor();
               guiGraphics.enableScissor(scissorX, top, screenWidth, screenHeight);
            }
         }

         guiGraphics.disableScissor();
      }

      this.textInputDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      this.promptDialog.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderSideButtonsInPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int buttonY = screenHeight - 8 - 17 - 1 - 17;
      int totalWidth = area1Right - 8;
      int buttonWidth = (totalWidth - 1) / 2;
      int buttonHeight = 17;
      int x1 = 8;
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

   private void updatePresetResetState() {
      if (!this.resetCompletedStates.isEmpty()) {
         long now = System.currentTimeMillis();
         List<String> expired = new ArrayList<>();

         for (Entry<String, Long> entry : this.resetCompletedTimes.entrySet()) {
            if (now - entry.getValue() > 3000L) {
               expired.add(entry.getKey());
            }
         }

         if (!expired.isEmpty()) {
            for (String key : expired) {
               this.resetCompletedTimes.remove(key);
               this.resetCompletedStates.remove(key);
            }

            this.rebuildPresetList();
         }
      }
   }

   private void resetLeftPanelModesIfNeeded() {
      if (this.isEditMode || this.isExportMode) {
         this.isEditMode = false;
         this.isExportMode = false;
         this.rebuildPresetList();
      }
   }

   private PresetConfigTab.GuideStep getGuideStep(int step) {
      if (step == 1) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('←' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step1", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(1, PresetConfigTab.GuideDock.TOP_LEFT, texts);
      } else if (step == 2) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(
            new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step2", new Object[0]) + " ", GuiConstants.COLOR_GOLD)
         );
         texts.add(new GDTextRenderer.ColoredText(String.valueOf('→'), -1));
         return new PresetConfigTab.GuideStep(2, PresetConfigTab.GuideDock.TOP_RIGHT, texts);
      } else if (step == 3) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step3", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(3, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else if (step == 4) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step4", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(4, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else if (step == 5) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step5", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(5, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else if (step == 6) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step6", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(6, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else if (step == 7) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step7", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(7, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else if (step == 8) {
         List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
         texts.add(new GDTextRenderer.ColoredText('↑' + " ", -1));
         texts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.preset_tutorial.step8", new Object[0]), GuiConstants.COLOR_GOLD));
         return new PresetConfigTab.GuideStep(8, PresetConfigTab.GuideDock.BOTTOM_CENTER, texts);
      } else {
         return null;
      }
   }

   private void markTutorialAction(int step) {
      if (this.tutorialCurrentStep == step && this.tutorialCurrentStep <= 8 && ClientConfigManager.shouldRunPresetTutorial()) {
         this.startTutorialExit();
      }
   }

   private void startTutorialExit() {
      if (this.tutorialCurrentStep <= 8 && this.tutorialPhase != PresetConfigTab.GuidePhase.EXIT) {
         PresetConfigTab.GuideStep guideStep = this.getGuideStep(this.tutorialCurrentStep);
         if (guideStep == null) {
            this.tutorialCurrentStep = 9;
            ClientConfigManager.setPresetTutorialStep(9);
         } else {
            this.tutorialPhase = PresetConfigTab.GuidePhase.EXIT;
            this.tutorialPhaseStartAt = System.currentTimeMillis();
            this.tutorialExitDirection = guideStep.dock == PresetConfigTab.GuideDock.TOP_RIGHT ? 1.0F : -1.0F;
            this.tutorialInterruptKick = this.tutorialExitDirection * 5.0F;
         }
      }
   }

   private void advanceTutorialStep() {
      this.tutorialCurrentStep++;
      ClientConfigManager.setPresetTutorialStep(this.tutorialCurrentStep);
      this.tutorialRenderer = null;
      this.tutorialTextWidth = 0.0F;
      this.tutorialTextHeight = 0.0F;
      this.tutorialInterruptKick = 0.0F;
      this.tutorialLastRenderAt = 0L;
      if (this.tutorialCurrentStep <= 8) {
         this.tutorialPhase = PresetConfigTab.GuidePhase.ENTER;
         this.tutorialPhaseStartAt = System.currentTimeMillis();
      }
   }

   private float easeOutQuad(float t) {
      return 1.0F - (1.0F - t) * (1.0F - t);
   }

   private float easeInQuad(float t) {
      return t * t;
   }

   private float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float p = t - 1.0F;
      return 1.0F + c3 * p * p * p + c1 * p * p;
   }

   private float computeBounceOffset(PresetConfigTab.GuideDock dock) {
      long activeElapsedMs = System.currentTimeMillis() - this.tutorialPhaseStartAt;
      if (activeElapsedMs < 3000L) {
         return 0.0F;
      } else {
         float t = (float)((activeElapsedMs - 3000L) % 3000L) / 3000.0F;
         float offset = 0.0F;
         if (t < 0.1F) {
            offset = 8.0F * this.easeOutBack(t / 0.1F);
         } else if (t < 0.2F) {
            float p = (t - 0.1F) / 0.1F;
            offset = 8.0F * (1.0F - this.easeInQuad(p));
         } else if (t < 0.28F) {
            offset = 4.0F * this.easeOutBack((t - 0.2F) / 0.08F);
         } else if (t < 0.37F) {
            float p = (t - 0.28F) / 0.09F;
            offset = 4.0F * (1.0F - this.easeInQuad(p));
         }

         if (dock == PresetConfigTab.GuideDock.TOP_RIGHT) {
            return -offset;
         } else {
            return dock == PresetConfigTab.GuideDock.BOTTOM_CENTER ? -offset : offset;
         }
      }
   }

   private void ensureTutorialRenderer(PresetConfigTab.GuideStep step) {
      int width = this.minecraft.font.width(this.getPlainGuideText(step.texts));
      int height = 9;
      this.tutorialTextWidth = (float)width;
      this.tutorialTextHeight = (float)height;
      if (this.tutorialRenderer == null) {
         this.tutorialRenderer = new GDTextRenderer(step.texts, 0, 0, Math.max(1, width + 2), height + 2, 1.0F, false);
      } else {
         this.tutorialRenderer.setColoredTexts(step.texts);
      }
   }

   private String getPlainGuideText(List<GDTextRenderer.ColoredText> texts) {
      StringBuilder sb = new StringBuilder();

      for (GDTextRenderer.ColoredText token : texts) {
         if (token != null && token.text != null) {
            sb.append(token.text);
         }
      }

      return sb.toString();
   }

   private void renderTutorialOverlay(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
      if (ClientConfigManager.shouldRunPresetTutorial() && this.tutorialCurrentStep <= 8) {
         PresetConfigTab.GuideStep step = this.getGuideStep(this.tutorialCurrentStep);
         if (step != null) {
            this.ensureTutorialRenderer(step);
            float boxWidth = this.tutorialTextWidth + 2.0F;
            float boxHeight = this.tutorialTextHeight + 2.0F;
            float topY = 26.0F;
            float baseX;
            float baseY;
            if (step.dock == PresetConfigTab.GuideDock.TOP_LEFT) {
               baseX = 8.0F;
               baseY = topY + 8.0F;
            } else if (step.dock == PresetConfigTab.GuideDock.TOP_RIGHT) {
               baseX = (float)(screenWidth - 8) - boxWidth;
               baseY = topY + 8.0F;
            } else {
               baseX = ((float)screenWidth - boxWidth) * 0.5F;
               baseY = (float)(screenHeight - 8) - boxHeight;
            }

            float elapsed = (float)(System.currentTimeMillis() - this.tutorialPhaseStartAt) / 1000.0F;
            float moveX = 0.0F;
            float moveY = 0.0F;
            if (this.tutorialPhase == PresetConfigTab.GuidePhase.ENTER) {
               float p = Mth.clamp(elapsed / 0.22F, 0.0F, 1.0F);
               float eased = this.easeOutQuad(p);
               if (step.dock == PresetConfigTab.GuideDock.TOP_LEFT) {
                  moveX = (-boxWidth - 24.0F) * (1.0F - eased);
               } else if (step.dock == PresetConfigTab.GuideDock.TOP_RIGHT) {
                  moveX = (boxWidth + 24.0F) * (1.0F - eased);
               } else {
                  moveY = (boxHeight + 24.0F) * (1.0F - eased);
               }

               if (p >= 1.0F) {
                  this.tutorialPhase = PresetConfigTab.GuidePhase.ACTIVE;
                  this.tutorialPhaseStartAt = System.currentTimeMillis();
               }
            } else if (this.tutorialPhase == PresetConfigTab.GuidePhase.EXIT) {
               float px = Mth.clamp(elapsed / 0.14F, 0.0F, 1.0F);
               float easedx = this.easeInQuad(px);
               float interrupt = this.tutorialInterruptKick * (1.0F - px);
               if (step.dock == PresetConfigTab.GuideDock.BOTTOM_CENTER) {
                  moveY = (boxHeight + 30.0F) * easedx;
               } else {
                  moveX = this.tutorialExitDirection * (boxWidth + 30.0F) * easedx + interrupt;
               }

               if (px >= 1.0F) {
                  this.advanceTutorialStep();
                  return;
               }
            } else {
               float bounce = this.computeBounceOffset(step.dock);
               if (step.dock == PresetConfigTab.GuideDock.BOTTOM_CENTER) {
                  moveY = bounce;
               } else {
                  moveX = bounce;
               }
            }

            float targetX = baseX + moveX;
            float targetY = baseY + moveY;
            long now = System.currentTimeMillis();
            if (this.tutorialLastRenderAt == 0L) {
               this.tutorialRenderX = targetX;
               this.tutorialRenderY = targetY;
               this.tutorialLastRenderAt = now;
            } else {
               float dt = (float)(now - this.tutorialLastRenderAt) / 1000.0F;
               this.tutorialLastRenderAt = now;
               float smoothFactor = 1.0F - (float)Math.exp((double)(-18.0F * dt));
               this.tutorialRenderX = this.tutorialRenderX + (targetX - this.tutorialRenderX) * smoothFactor;
               this.tutorialRenderY = this.tutorialRenderY + (targetY - this.tutorialRenderY) * smoothFactor;
            }

            int renderWidth = Math.max(1, (int)Math.ceil((double)boxWidth));
            int renderHeight = Math.max(1, (int)Math.ceil((double)boxHeight));
            this.tutorialRenderer.setX1(0);
            this.tutorialRenderer.setY1(0);
            this.tutorialRenderer.setX2(renderWidth);
            this.tutorialRenderer.setY2(renderHeight);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(this.tutorialRenderX, this.tutorialRenderY, 0.0F);
            this.tutorialRenderer.render(guiGraphics, partialTick, false);
            guiGraphics.pose().popPose();
         }
      }
   }

   private void renderAnchorOverlay(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight, int panelWidth, int mouseX, int mouseY) {
      long now = System.currentTimeMillis();
      String pressedElementId = null;

      for (ElementPreview preview : this.previewElements.values()) {
         if (preview.isPressed()) {
            pressedElementId = preview.getElementId();
            break;
         }
      }

      if (pressedElementId != null) {
         if (!this.anchorOverlayShowing) {
            this.anchorOverlayShowing = true;
            this.anchorOverlayStateStartTime = now;
         }
      } else if (this.anchorOverlayShowing) {
         this.anchorOverlayShowing = false;
         this.anchorOverlayStateStartTime = now;
      }

      if (this.anchorOverlayShowing) {
         float t = (float)(now - this.anchorOverlayStateStartTime) / 200.0F;
         this.anchorOverlayAlpha = 0.2F * Math.min(1.0F, t);
      } else if (this.anchorOverlayAlpha > 0.0F) {
         float t = (float)(now - this.anchorOverlayStateStartTime) / 100.0F;
         this.anchorOverlayAlpha = 0.2F * Math.max(0.0F, 1.0F - t);
         if (t >= 1.0F) {
            this.anchorOverlayAlpha = 0.0F;
         }
      }

      if (!(this.anchorOverlayAlpha <= 0.001F) && pressedElementId != null) {
         ElementPreview previewx = this.previewElements.get(pressedElementId);
         if (previewx != null) {
            int goldBarBottom = 26;
            float leftVisibleWidth = Math.max(0.0F, this.currentTranslation + (float)panelWidth);
            float rightVisibleWidth = Math.max(0.0F, (float)panelWidth - this.currentRightTranslation);
            int gx = (int)Math.max(8.0F, leftVisibleWidth);
            int gy = goldBarBottom + 8;
            int gx2 = (int)Math.min((float)screenWidth - 8.0F, (float)screenWidth - rightVisibleWidth);
            int gy2 = screenHeight - 8;
            int gw = Math.max(1, gx2 - gx);
            int gh = Math.max(1, gy2 - gy);
            String presetId = ClientConfigManager.getCurrentPresetId();
            String anchor = "bottom_center";
            JsonObject cfg = ElementConfigManager.getElementConfig(presetId, pressedElementId);
            if (cfg != null && cfg.has("screen_anchor")) {
               anchor = cfg.get("screen_anchor").getAsString();
            }

            float[][] norms = new float[][]{
               {0.0F, 0.0F}, {0.0F, 0.5F}, {0.0F, 1.0F}, {0.5F, 0.0F}, {0.5F, 0.5F}, {0.5F, 1.0F}, {1.0F, 0.0F}, {1.0F, 0.5F}, {1.0F, 1.0F}
            };
            String[] ids = new String[]{
               "top_left", "middle_left", "bottom_left", "top_center", "center", "bottom_center", "top_right", "middle_right", "bottom_right"
            };
            float anchorAX = (float)gx + 0.5F * (float)gw;
            float anchorAY = (float)gy + 1.0F * (float)gh;

            for (int i = 0; i < ids.length; i++) {
               if (ids[i].equals(anchor)) {
                  anchorAX = (float)gx + norms[i][0] * (float)gw;
                  anchorAY = (float)gy + norms[i][1] * (float)gh;
                  break;
               }
            }

            float centerX = (float)previewx.getPreviewX() + (float)previewx.getPreviewWidth() / 2.0F;
            float centerY = (float)previewx.getPreviewY() + (float)previewx.getPreviewHeight() / 2.0F;
            int[][] rects = new int[9][4];

            for (int ix = 0; ix < ids.length; ix++) {
               float nx = norms[ix][0];
               float ny = norms[ix][1];
               int sx = (int)((float)gx + nx * (float)gw) - (nx > 0.5F ? 10 : (nx == 0.5F ? 5 : 0));
               int sy = (int)((float)gy + ny * (float)gh) - (ny > 0.5F ? 10 : (ny == 0.5F ? 5 : 0));
               rects[ix][0] = sx;
               rects[ix][1] = sy;
               rects[ix][2] = sx + 10;
               rects[ix][3] = sy + 10;
            }

            String hoverAnchor = null;

            for (int ix = 0; ix < ids.length; ix++) {
               if (mouseX >= rects[ix][0] && mouseX <= rects[ix][2] && mouseY >= rects[ix][1] && mouseY <= rects[ix][3]) {
                  if (!ids[ix].equals(anchor)) {
                     hoverAnchor = ids[ix];
                  }
                  break;
               }
            }

            if (hoverAnchor != null) {
               if (!hoverAnchor.equals(this.candidateAnchorId)) {
                  this.candidateAnchorId = hoverAnchor;
                  this.candidateStartTime = now;
                  this.candidateFadingOut = false;
               }

               this.candidateProgress = (float)(now - this.candidateStartTime) / 1000.0F;
               if (this.candidateProgress >= 1.0F) {
                  this.switchAnchor(pressedElementId, anchor, hoverAnchor, screenWidth, screenHeight);
                  this.candidateAnchorId = null;
                  this.candidateProgress = 0.0F;
                  this.candidateFadingOut = false;
                  anchor = hoverAnchor;
               }
            } else if (this.candidateAnchorId != null) {
               if (!this.candidateFadingOut) {
                  this.candidateFadingOut = true;
                  this.candidateFadeOutStartTime = now;
               }

               float revert = (float)(now - this.candidateFadeOutStartTime) / 500.0F;
               this.candidateProgress = Math.max(0.0F, 1.0F - revert);
               if (revert >= 1.0F) {
                  this.candidateAnchorId = null;
                  this.candidateProgress = 0.0F;
                  this.candidateFadingOut = false;
               }
            }

            int alphaInt = (int)(this.anchorOverlayAlpha * 255.0F) << 24;

            for (int ixx = 0; ixx < ids.length; ixx++) {
               int rgb;
               if (ids[ixx].equals(anchor)) {
                  rgb = GuiConstants.COLOR_GOLD & 16777215;
               } else if (ids[ixx].equals(this.candidateAnchorId)) {
                  rgb = interpolateColor(11184810, GuiConstants.COLOR_GOLD & 16777215, this.candidateProgress);
               } else {
                  rgb = 11184810;
               }

               guiGraphics.fill(rects[ixx][0], rects[ixx][1], rects[ixx][2], rects[ixx][3], alphaInt | rgb);
            }

            boolean[] hit = new boolean[]{isOnBorder(anchorAX, gx, gw), isOnBorder(centerY, gy, gh), isOnBorder(centerX, gx, gw), isOnBorder(anchorAY, gy, gh)};
            int[][] pairs = new int[][]{{0, 1}, {2, 3}};
            int[] chosen = pairs[0];
            int bestScore = (hit[pairs[0][0]] ? 1 : 0) + (hit[pairs[0][1]] ? 1 : 0);

            for (int[] pair : pairs) {
               int score = (hit[pair[0]] ? 1 : 0) + (hit[pair[1]] ? 1 : 0);
               if (score < bestScore) {
                  chosen = pair;
                  bestScore = score;
               }
            }

            int lineColor = 1711276032 | GuiConstants.COLOR_GOLD & 16777215;
            if (chosen[0] == 0 || chosen[1] == 0) {
               fillVLine(guiGraphics, anchorAX, anchorAY, centerY, lineColor, gx, gy, gw, gh);
            }

            if (chosen[0] == 1 || chosen[1] == 1) {
               fillHLine(guiGraphics, anchorAX, centerX, centerY, lineColor, gx, gy, gw, gh);
            }

            if (chosen[0] == 2 || chosen[1] == 2) {
               fillVLine(guiGraphics, centerX, centerY, anchorAY, lineColor, gx, gy, gw, gh);
            }

            if (chosen[0] == 3 || chosen[1] == 3) {
               fillHLine(guiGraphics, centerX, anchorAX, anchorAY, lineColor, gx, gy, gw, gh);
            }
         }
      }
   }

   private static void fillVLine(GuiGraphics guiGraphics, float x, float y1, float y2, int color, int gx, int gy, int gw, int gh) {
      int xi = clampEdge(Math.round(x), gx, gx + gw);
      int y1i = clampEdge(Math.round(y1), gy, gy + gh);
      int y2i = clampEdge(Math.round(y2), gy, gy + gh);
      int ya = Math.min(y1i, y2i);
      int yb = Math.max(y1i, y2i);
      if (yb > ya) {
         guiGraphics.fill(xi, ya, xi + 1, yb, color);
      }
   }

   private static void fillHLine(GuiGraphics guiGraphics, float x1, float x2, float y, int color, int gx, int gy, int gw, int gh) {
      int x1i = clampEdge(Math.round(x1), gx, gx + gw);
      int x2i = clampEdge(Math.round(x2), gx, gx + gw);
      int yi = clampEdge(Math.round(y), gy, gy + gh);
      int xa = Math.min(x1i, x2i);
      int xb = Math.max(x1i, x2i);
      if (xb > xa) {
         guiGraphics.fill(xa, yi, xb, yi + 1, color);
      }
   }

   private static int clampEdge(int v, int min, int maxExclusive) {
      return Math.max(min, Math.min(maxExclusive - 1, v));
   }

   private void switchAnchor(String elementId, String oldAnchor, String newAnchor, int screenWidth, int screenHeight) {
      String presetId = ClientConfigManager.getCurrentPresetId();
      JsonObject cfg = ElementConfigManager.getElementConfig(presetId, elementId);
      if (cfg != null) {
         int oldX = cfg.has("x_offset") ? cfg.get("x_offset").getAsInt() : 0;
         int oldY = cfg.has("y_offset") ? cfg.get("y_offset").getAsInt() : 0;
         int newX = ScreenAnchor.translateXOffset(oldAnchor, newAnchor, oldX, screenWidth);
         int newY = ScreenAnchor.translateYOffset(oldAnchor, newAnchor, oldY, screenHeight);
         ElementConfigManager.updateConfigValue(presetId, elementId, "screen_anchor", newAnchor);
         ElementConfigManager.updateConfigValue(presetId, elementId, "x_offset", String.valueOf(newX));
         ElementConfigManager.updateConfigValue(presetId, elementId, "y_offset", String.valueOf(newY));
         ElementPreview preview = this.previewElements.get(elementId);
         if (preview != null) {
            JsonObject synced = ElementConfigManager.getElementConfig(presetId, elementId);
            preview.updateConfig(synced);
         }
      }
   }

   private static int interpolateColor(int c1, int c2, float t) {
      float clamped = Math.max(0.0F, Math.min(1.0F, t));
      int r = (int)((float)(c1 >> 16 & 0xFF) + (float)((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * clamped);
      int g = (int)((float)(c1 >> 8 & 0xFF) + (float)((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * clamped);
      int b = (int)((float)(c1 & 0xFF) + (float)((c2 & 0xFF) - (c1 & 0xFF)) * clamped);
      return r << 16 | g << 8 | b;
   }

   private static boolean isOnBorder(float coord, int gx, int gw) {
      return Math.abs(coord - (float)gx) < 1.0F || Math.abs(coord - (float)(gx + gw)) < 1.0F;
   }

   private void renderPanelDirectionArrows(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
      int centerY = screenHeight / 2;
      int leftX = 4;
      int rightX = screenWidth - 4;
      String leftArrow = this.state == PresetConfigTab.PanelState.OPEN ? "<" : ">";
      String rightArrow = this.rightPanelState == PresetConfigTab.PanelState.OPEN ? ">" : "<";
      int arrowColor = this.isAnyElementPressed() ? -5592406 : GuiConstants.COLOR_GOLD;
      guiGraphics.drawCenteredString(this.minecraft.font, leftArrow, leftX, centerY - 9 / 2, arrowColor);
      guiGraphics.drawCenteredString(this.minecraft.font, rightArrow, rightX, centerY - 9 / 2, arrowColor);
   }

   private boolean isAnyElementPressed() {
      for (ElementPreview preview : this.previewElements.values()) {
         if (preview.isPressed()) {
            return true;
         }
      }

      return false;
   }

   private boolean hasFloatChanged(JsonObject before, JsonObject after, String key) {
      return before != null && after != null && before.has(key) && after.has(key)
         ? Math.abs(before.get(key).getAsFloat() - after.get(key).getAsFloat()) > 1.0E-4F
         : false;
   }

   private boolean hasIntChanged(JsonObject before, JsonObject after, String key) {
      return before != null && after != null && before.has(key) && after.has(key) ? before.get(key).getAsInt() != after.get(key).getAsInt() : false;
   }

   @Override
   public boolean charTyped(char codePoint, int modifiers) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.charTyped(codePoint, modifiers);
      } else {
         return this.textInputDialog.isVisible() ? this.textInputDialog.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.textInputDialog.isVisible()) {
         return this.textInputDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (Screen.hasControlDown() && keyCode == 90 && this.tryUndo()) {
         this.markTutorialAction(6);
         return true;
      } else {
         return Screen.hasControlDown() && keyCode == 89 && this.tryRedo() ? true : super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.textInputDialog.isVisible() && this.textInputDialog.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int area1Right = (screenWidth - 16) / 3 + 8;
         int panelWidth = area1Right + 8;
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         if (this.state == PresetConfigTab.PanelState.HIDDEN) {
            if (mouseX <= 8.0) {
               this.state = PresetConfigTab.PanelState.OPEN;
               this.leftRequireExitBeforeAutoOpen = false;
               return true;
            }
         } else if (this.state == PresetConfigTab.PanelState.PEEK) {
            if (mouseX <= 8.0) {
               this.state = PresetConfigTab.PanelState.OPEN;
               return true;
            }
         } else if (this.state == PresetConfigTab.PanelState.OPEN) {
            if (mouseX <= 8.0) {
               this.state = PresetConfigTab.PanelState.HIDDEN;
               this.leftRequireExitBeforeAutoOpen = true;
               this.resetLeftPanelModesIfNeeded();
               return true;
            }

            if (mouseX > (double)panelWidth) {
               this.state = PresetConfigTab.PanelState.HIDDEN;
               this.resetLeftPanelModesIfNeeded();
               return true;
            }

            if (this.resetButton != null && this.resetButton.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }

            if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }

            int startY = this.area1Bottom + 8;
            int buttonHeight = 17;
            int endY = screenHeight - 8 - buttonHeight - 1 - buttonHeight - 8;
            if (mouseY >= (double)startY && mouseY <= (double)endY && mouseX <= (double)panelWidth) {
               float translatedMouseX = (float)mouseX - this.currentTranslation;
               double adjustedMouseY = mouseY + this.scrollY;

               for (GDRowRenderer row : this.presetRows) {
                  if (row.mouseClicked((double)translatedMouseX, adjustedMouseY, button)) {
                     return true;
                  }
               }

               this.isDragging = true;
               this.lastMouseY = mouseY;
               return true;
            }

            if (mouseX <= (double)panelWidth) {
               return true;
            }
         }

         if (this.rightPanelState == PresetConfigTab.PanelState.HIDDEN) {
            int rightTriggerZoneStart = screenWidth - 8;
            if (mouseX >= (double)rightTriggerZoneStart) {
               this.rightPanelState = PresetConfigTab.PanelState.OPEN;
               this.rightRequireExitBeforeAutoOpen = false;
               return true;
            }
         } else if (this.rightPanelState == PresetConfigTab.PanelState.PEEK) {
            int rightTriggerZoneStart = screenWidth - 8;
            if (mouseX >= (double)rightTriggerZoneStart) {
               this.rightPanelState = PresetConfigTab.PanelState.OPEN;
               return true;
            }
         } else if (this.rightPanelState == PresetConfigTab.PanelState.OPEN) {
            int rightPanelX = screenWidth - panelWidth;
            int rightTriggerZoneStart = screenWidth - 8;
            if (mouseX >= (double)rightTriggerZoneStart) {
               this.rightPanelState = PresetConfigTab.PanelState.HIDDEN;
               this.rightRequireExitBeforeAutoOpen = true;
               return true;
            }

            if (mouseX < (double)rightPanelX) {
               this.rightPanelState = PresetConfigTab.PanelState.HIDDEN;
               return true;
            }

            int contentX = rightPanelX + 8;
            int buttonHeightx = 17;
            int buttonPadding = 1;
            int buttonSpacing = 1;
            int totalButtonsHeight = buttonHeightx * 2 + buttonSpacing;
            int bottomBoxHeight;
            if (this.isAddElementExpanded) {
               bottomBoxHeight = totalButtonsHeight + 51 + 4;
            } else {
               bottomBoxHeight = buttonPadding + totalButtonsHeight + buttonPadding;
            }

            int bottomBoxY = screenHeight - 8 - bottomBoxHeight;
            int soundButtonY = bottomBoxY + bottomBoxHeight - buttonPadding - buttonHeightx;
            int addButtonY = soundButtonY - buttonSpacing - buttonHeightx;
            if (this.rightAddButton != null && this.rightAddButton.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }

            if (this.rightSoundButton != null && this.rightSoundButton.mouseClicked(mouseX, mouseY, button)) {
               return true;
            }

            if (this.isAddElementExpanded) {
               int listTop = bottomBoxY + 1;
               int listBottom = addButtonY - 2;
               if (mouseY >= (double)listTop && mouseY <= (double)listBottom && mouseX >= (double)contentX && mouseX <= (double)(contentX + (panelWidth - 16))) {
                  double adjustedMouseY = mouseY + this.elementListScrollY;

                  for (GDRowRenderer rowx : this.availableElementRows) {
                     if (rowx.mouseClicked(mouseX, adjustedMouseY, button)) {
                        return true;
                     }
                  }

                  this.isElementListDragging = true;
                  this.elementListLastMouseY = mouseY;
                  return true;
               }
            }

            int top = 26;
            int contentY = top + 8;
            int titleBoxHeight = 24;
            int middleBoxY = contentY + titleBoxHeight + 8;
            int middleBoxBottom = bottomBoxY - 8;
            if (mouseY >= (double)middleBoxY
               && mouseY <= (double)middleBoxBottom
               && mouseX >= (double)contentX
               && mouseX <= (double)(contentX + (panelWidth - 16))) {
               double adjustedMouseY = mouseY + this.contentScrollY;

               for (GDRowRenderer rowxx : this.currentElementRows) {
                  if (rowxx.mouseClicked(mouseX, adjustedMouseY, button)) {
                     return true;
                  }
               }

               this.isContentDragging = true;
               this.contentLastMouseY = mouseY;
               return true;
            }

            return true;
         }

         List<ElementPreview> clickList = new ArrayList<>(this.previewElements.values());

         for (ElementPreview preview : clickList) {
            preview.updatePosition(screenWidth, screenHeight);
         }

         clickList.sort(Comparator.comparingInt(ElementPreview::getZIndex).reversed());

         for (ElementPreview preview : clickList) {
            ElementPreview.PreviewInteractionResult result = preview.mouseClicked(mouseX, mouseY, button);
            if (result != ElementPreview.PreviewInteractionResult.PASS) {
               if (result == ElementPreview.PreviewInteractionResult.DOUBLE_CLICK) {
                  String id = preview.getElementId();
                  this.markTutorialAction(8);
                  String currentId = ClientConfigManager.getCurrentPresetId();
                  if (this.header != null) {
                     ElementConfigBuilder builder = ElementConfigBuilderRegistry.getBuilder(id);
                     this.header.setOverrideContent(new ElementConfigContent(this.minecraft, currentId, id, builder, () -> {
                        this.header.setOverrideContent(null);
                        preview.resetVisualState();
                        this.updatePreviews();
                        this.updateCurrentElementRows();
                     }));
                  }

                  return true;
               }

               if (result != ElementPreview.PreviewInteractionResult.RIGHT_CLICK) {
                  this.draggingElementId = preview.getElementId();
                  String currentPresetId = ClientConfigManager.getCurrentPresetId();
                  JsonObject config = ElementConfigManager.getElementConfig(currentPresetId, this.draggingElementId);
                  if (config != null) {
                     this.dragStartConfig = config.deepCopy();
                  }

                  return true;
               }

               String id = preview.getElementId();
               this.markTutorialAction(7);
               String currentId = ClientConfigManager.getCurrentPresetId();
               JsonObject config = ElementConfigManager.getElementConfig(currentId, id);
               boolean isVisible = config != null && (!config.has("visible") || config.get("visible").getAsBoolean());
               if (config != null) {
                  this.pushUndoState(currentId, id, config);
               }

               ElementConfigManager.updateConfigValue(currentId, id, "visible", String.valueOf(!isVisible));
               this.updateCurrentElementRows();
               this.updatePreviews();
               this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
               return true;
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      if (!this.configRows.isEmpty()) {
         for (GDRowRenderer row : this.configRows) {
            row.render(guiGraphics, mouseX, mouseY, partialTick);
         }
      }
   }

   private void renderPresetList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int panelWidth, int screenHeight, float dt) {
      int startY = this.area1Bottom + 8;
      int buttonHeight = 17;
      int endY = screenHeight - 8 - buttonHeight - 1 - buttonHeight - 8;
      int listHeight = endY - startY;
      if (listHeight > 0) {
         this.updateScroll(dt, (double)mouseY, listHeight);
         float translatedMouseX = (float)mouseX - this.currentTranslation;
         int visibleWidth = (int)Math.ceil((double)(this.currentTranslation + (float)panelWidth));
         if (visibleWidth > 0) {
            if (visibleWidth > 0 && endY > startY) {
               guiGraphics.enableScissor(0, startY, visibleWidth, endY);
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate((double)this.currentTranslation, -this.scrollY, 0.0);
               int currentY = startY;

               for (int i = 0; i < this.presetRows.size(); i++) {
                  GDRowRenderer row = this.presetRows.get(i);
                  int rowHeight = 17;
                  if (i == this.createRowIndex && i > 0) {
                     currentY++;
                  }

                  int rowX1 = 8;
                  int rowX2 = panelWidth - 8;
                  row.setBounds(rowX1, currentY, rowX2, currentY + rowHeight);
                  row.render(guiGraphics, (int)translatedMouseX, (int)((double)mouseY + this.scrollY), partialTick);
                  currentY += rowHeight + 1;
               }

               guiGraphics.pose().popPose();
               guiGraphics.disableScissor();
            }
         }
      }
   }

   private void updateScroll(float dt, double currentMouseY, int viewHeight) {
      int contentHeight = this.getPresetListContentHeight();
      double maxScroll = (double)Math.max(0, contentHeight - viewHeight);
      if (this.isDragging) {
         double diff = currentMouseY - this.lastMouseY;
         this.targetScrollY -= diff;
         this.lastMouseY = currentMouseY;
      }

      this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
      double diff = this.targetScrollY - this.scrollY;
      if (Math.abs(diff) < 0.01) {
         this.scrollY = this.targetScrollY;
      } else {
         this.scrollY += diff * 15.0 * (double)dt;
      }
   }

   private int getPresetListContentHeight() {
      int contentHeight = this.presetRows.size() * 18;
      if (this.createRowIndex > 0) {
         contentHeight++;
      }

      return contentHeight;
   }

   private boolean tryUndo() {
      String presetId = ClientConfigManager.getCurrentPresetId();
      if (this.undoStacks.containsKey(presetId) && !this.undoStacks.get(presetId).isEmpty()) {
         PresetConfigTab.UndoState state = this.undoStacks.get(presetId).pop();
         JsonObject currentConfig = ElementConfigManager.getElementConfig(presetId, state.elementId);
         if (currentConfig != null) {
            this.redoStacks.computeIfAbsent(presetId, k -> new ArrayDeque<>()).push(new PresetConfigTab.UndoState(state.elementId, currentConfig));
         }

         ElementConfigManager.setElementConfig(presetId, state.elementId, state.configSnapshot);
         this.updatePreviews();
         this.updateCurrentElementRows();
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         return true;
      } else {
         return false;
      }
   }

   private boolean tryRedo() {
      String presetId = ClientConfigManager.getCurrentPresetId();
      if (this.redoStacks.containsKey(presetId) && !this.redoStacks.get(presetId).isEmpty()) {
         PresetConfigTab.UndoState state = this.redoStacks.get(presetId).pop();
         JsonObject currentConfig = ElementConfigManager.getElementConfig(presetId, state.elementId);
         if (currentConfig != null) {
            this.undoStacks.computeIfAbsent(presetId, k -> new ArrayDeque<>()).push(new PresetConfigTab.UndoState(state.elementId, currentConfig));
         }

         ElementConfigManager.setElementConfig(presetId, state.elementId, state.configSnapshot);
         this.updatePreviews();
         this.updateCurrentElementRows();
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         return true;
      } else {
         return false;
      }
   }

   private void pushUndoState(String presetId, String elementId, JsonObject config) {
      this.undoStacks.computeIfAbsent(presetId, k -> new ArrayDeque<>()).push(new PresetConfigTab.UndoState(elementId, config));
      Deque<PresetConfigTab.UndoState> redoStack = this.redoStacks.computeIfAbsent(presetId, k -> new ArrayDeque<>());
      redoStack.clear();
   }

   private void updateElementListScroll(float dt, double currentMouseY, int viewHeight) {
      int contentHeight = this.availableElementRows.size() * 18;
      double maxScroll = (double)Math.max(0, contentHeight - viewHeight);
      if (this.isElementListDragging) {
         double diff = currentMouseY - this.elementListLastMouseY;
         this.targetElementListScrollY -= diff;
         this.elementListLastMouseY = currentMouseY;
      }

      this.targetElementListScrollY = Math.max(0.0, Math.min(maxScroll, this.targetElementListScrollY));
      double diff = this.targetElementListScrollY - this.elementListScrollY;
      if (Math.abs(diff) < 0.01) {
         this.elementListScrollY = this.targetElementListScrollY;
      } else {
         this.elementListScrollY += diff * 15.0 * (double)dt;
      }
   }

   private void updateCurrentElementRows() {
      this.currentElementRows.clear();
      String currentId = ClientConfigManager.getCurrentPresetId();
      Set<String> elementIds = ElementConfigManager.getElementIds(currentId);
      List<String> sortedIds = new ArrayList<>(elementIds);
      Collections.sort(sortedIds);

      for (int i = 0; i < sortedIds.size(); i++) {
         String id = sortedIds.get(i);
         this.currentElementRows.add(this.createElementRow(currentId, id, this.currentElementRows.size()));
      }
   }

   private GDRowRenderer createElementRow(String currentId, String id, int rowIndex) {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.0F, false);
      row.setBackgroundAlpha(rowIndex % 2 != 0 ? 0.15F : 0.3F);
      JsonObject config = ElementConfigManager.getElementConfig(currentId, id);
      boolean isVisible = config != null && (!config.has("visible") || config.get("visible").getAsBoolean());
      List<GDTextRenderer.ColoredText> texts = new ArrayList<>();
      texts.add(new GDTextRenderer.ColoredText(" ", -1));
      texts.add(new GDTextRenderer.ColoredText("[" + id + "] ", isVisible ? -5592406 : -10066330));
      String nameKey = "battlefield_ops.element.name." + id.replace("/", ".");
      String name = I18nCompat.exists(nameKey) ? I18n.get(nameKey, new Object[0]) : id;
      texts.add(new GDTextRenderer.ColoredText(name, isVisible ? GuiConstants.COLOR_GOLD : -5592406));
      row.addColoredColumn(texts, -1, false, false, null);
      row.addColumn(I18n.get("battlefield_ops.client.gui.config.preset.configure_button", new Object[0]), 30, -1, true, true, btn -> {
         if (this.header != null) {
            ElementConfigBuilder builder = ElementConfigBuilderRegistry.getBuilder(id);
            this.header.setOverrideContent(new ElementConfigContent(this.minecraft, currentId, id, builder, () -> {
               this.header.setOverrideContent(null);
               this.updatePreviews();
               this.updateCurrentElementRows();
            }));
         }
      });
      row.getColumn(1).isDarker = true;
      int toggleWidth = 17;
      String toggleText = isVisible ? "\ud83d\udc41" : "×";
      row.addColumn(toggleText, toggleWidth, -1, false, true, btn -> {
         JsonObject beforeConfig = ElementConfigManager.getElementConfig(currentId, id);
         if (beforeConfig != null) {
            this.pushUndoState(currentId, id, beforeConfig);
         }

         ElementConfigManager.updateConfigValue(currentId, id, "visible", String.valueOf(!isVisible));
         this.updateCurrentElementRows();
         this.updatePreviews();
      });
      int deleteWidth = 17;
      row.addColumn("×", deleteWidth, -65536, true, true, btn -> {
         ElementConfigManager.removeElementFromPreset(currentId, id);
         this.updateCurrentElementRows();
         this.updateAvailableElementRows();
         this.updatePreviews();
      });
      row.getColumn(3).isDarker = true;
      row.setOnHover(hovered -> {
         ElementPreview preview = this.previewElements.get(id);
         if (preview != null) {
            preview.setExternalHover(hovered);
         }
      });
      return row;
   }

   private void updateContentScroll(float dt, double currentMouseY, int viewHeight) {
      int contentHeight = this.currentElementRows.size() * 18;
      double maxScroll = (double)Math.max(0, contentHeight - viewHeight);
      if (this.isContentDragging) {
         double diff = currentMouseY - this.contentLastMouseY;
         this.targetContentScrollY -= diff;
         this.contentLastMouseY = currentMouseY;
      }

      this.targetContentScrollY = Math.max(0.0, Math.min(maxScroll, this.targetContentScrollY));
      double diff = this.targetContentScrollY - this.contentScrollY;
      if (Math.abs(diff) < 0.01) {
         this.contentScrollY = this.targetContentScrollY;
      } else {
         this.contentScrollY += diff * 15.0 * (double)dt;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.textInputDialog.isVisible()) {
         return true;
      } else {
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int area1Right = (screenWidth - 16) / 3 + 8;
         int panelWidth = area1Right + 8;
         if (this.state == PresetConfigTab.PanelState.OPEN && mouseX <= (double)panelWidth) {
            this.targetScrollY -= delta * 40.0;
            return true;
         } else {
            int rightPanelX = screenWidth - panelWidth;
            if (this.rightPanelState == PresetConfigTab.PanelState.OPEN && mouseX >= (double)rightPanelX) {
               if (this.isAddElementExpanded) {
                  int bottomBoxHeight = 72;
                  int bottomBoxY = this.minecraft.getWindow().getGuiScaledHeight() - 8 - bottomBoxHeight;
                  if (mouseY >= (double)bottomBoxY) {
                     this.targetElementListScrollY -= delta * 40.0;
                     return true;
                  }
               }

               this.targetContentScrollY -= delta * 40.0;
               return true;
            } else {
               return super.mouseScrolled(mouseX, mouseY, delta);
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.promptDialog.isVisible()) {
         return true;
      } else if (this.textInputDialog.isVisible()) {
         return true;
      } else {
         this.isDragging = false;
         this.isElementListDragging = false;
         this.isContentDragging = false;
         if (this.draggingElementId != null && this.dragStartConfig != null) {
            String currentPresetId = ClientConfigManager.getCurrentPresetId();
            JsonObject currentConfig = ElementConfigManager.getElementConfig(currentPresetId, this.draggingElementId);
            if (currentConfig != null && !currentConfig.equals(this.dragStartConfig)) {
               this.pushUndoState(currentPresetId, this.draggingElementId, this.dragStartConfig);
               if (this.hasIntChanged(this.dragStartConfig, currentConfig, "x_offset") || this.hasIntChanged(this.dragStartConfig, currentConfig, "y_offset")) {
                  this.markTutorialAction(3);
               } else if (this.hasFloatChanged(this.dragStartConfig, currentConfig, "rotation_angle")) {
                  this.markTutorialAction(4);
               } else if (this.hasFloatChanged(this.dragStartConfig, currentConfig, "scale")) {
                  this.markTutorialAction(5);
               }
            }

            this.draggingElementId = null;
            this.dragStartConfig = null;
         }

         if (this.state == PresetConfigTab.PanelState.OPEN) {
            return true;
         } else {
            for (ElementPreview preview : this.previewElements.values()) {
               if (preview.mouseReleased(mouseX, mouseY, button)) {
                  return true;
               }
            }

            return super.mouseReleased(mouseX, mouseY, button);
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.promptDialog.isVisible()) {
         return true;
      } else if (this.textInputDialog.isVisible()) {
         return true;
      } else if (this.state == PresetConfigTab.PanelState.OPEN) {
         return true;
      } else {
         for (ElementPreview preview : this.previewElements.values()) {
            int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
            if (preview.mouseDragged(mouseX, mouseY, button, dragX, dragY, screenWidth, screenHeight)) {
               return true;
            }
         }

         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   private static enum GuideDock {
      TOP_LEFT,
      TOP_RIGHT,
      BOTTOM_CENTER;
   }

   private static enum GuidePhase {
      ENTER,
      ACTIVE,
      EXIT;
   }

   private static class GuideStep {
      final int index;
      final PresetConfigTab.GuideDock dock;
      final List<GDTextRenderer.ColoredText> texts;

      GuideStep(int index, PresetConfigTab.GuideDock dock, List<GDTextRenderer.ColoredText> texts) {
         this.index = index;
         this.dock = dock;
         this.texts = texts;
      }
   }

   private static enum PanelState {
      HIDDEN,
      PEEK,
      OPEN;
   }

   private static class UndoState {
      final String elementId;
      final JsonObject configSnapshot;

      UndoState(String elementId, JsonObject config) {
         this.elementId = elementId;
         this.configSnapshot = config.deepCopy();
      }
   }
}
