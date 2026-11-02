package cn.bfops.killicon.client.gui.tabs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.ConfirmDialog;
import cn.bfops.killicon.client.gui.elements.GDButton;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.InfiniteGridWidget;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.elements.entries.ActionConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.BooleanConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.FixedChoiceConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.FloatConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.HexColorConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.IntegerConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.StringConfigEntry;
import cn.bfops.killicon.client.gui.screens.ElementConfigBuilder;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.impl.Battlefield1Renderer;
import cn.bfops.killicon.client.render.impl.BonusListRenderer;
import cn.bfops.killicon.client.render.impl.CardBarRenderer;
import cn.bfops.killicon.client.render.impl.CardRenderer;
import cn.bfops.killicon.client.render.impl.ComboIconRenderer;
import cn.bfops.killicon.client.render.impl.ComboSubtitleRenderer;
import cn.bfops.killicon.client.render.impl.HitInfoRenderer;
import cn.bfops.killicon.client.render.impl.HonorRenderer;
import cn.bfops.killicon.client.render.impl.ScoreSubtitleRenderer;
import cn.bfops.killicon.client.render.impl.ScrollingIconRenderer;
import cn.bfops.killicon.client.render.impl.SubtitleRenderer;
import cn.bfops.killicon.client.render.impl.ValorantIconRenderer;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.client.textures.ExternalTextureManager;
import cn.bfops.killicon.client.util.GifToSpriteSheetConverter;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.BonusType;
import cn.bfops.killicon.common.config.Category;
import cn.bfops.killicon.common.config.ElementConfigRegistry;
import cn.bfops.killicon.common.honor.HonorRegistry;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementConfigContent extends ConfigTabContent {
   private static final Logger LOGGER = LoggerFactory.getLogger("battlefield_ops-gui");
   private final String presetId;
   private final String elementId;
   private final ElementConfigBuilder builder;
   private final Runnable onClose;
   private final JsonObject initialConfig;
   private GDTextRenderer elementInfoRenderer;
   private int calculatedBottom;
   private InfiniteGridWidget gridWidget;
   private ScrollingIconRenderer scrollingPreviewRenderer = new ScrollingIconRenderer();
   private long lastPreviewTriggerTime = 0L;
   private int previewKillTypeIndex = 0;
   private ComboIconRenderer comboPreviewRenderer = new ComboIconRenderer();
   private long lastComboPreviewTriggerTime = 0L;
   private int previewComboCount = 1;
   private int previewComboKillTypeIndex = 0;
   private ValorantIconRenderer valorantPreviewRenderer = new ValorantIconRenderer();
   private long lastValorantPreviewTriggerTime = 0L;
   private int previewValorantComboCount = 1;
   private CardRenderer cardPreviewRenderer = new CardRenderer();
   private long lastCardPreviewTriggerTime = 0L;
   private int previewCardComboCount = 1;
   private int previewCardKillTypeIndex = 0;
   private CardBarRenderer cardBarPreviewRenderer = new CardBarRenderer();
   private long lastCardBarPreviewTriggerTime = 0L;
   private int previewCardBarComboCount = 1;
   private int previewCardBarKillTypeIndex = 0;
   private Battlefield1Renderer battlefield1PreviewRenderer = new Battlefield1Renderer();
   private long lastBattlefieldPreviewTriggerTime = 0L;
   private int previewBattlefieldKillTypeIndex = 0;
   private SubtitleRenderer subtitlePreviewRenderer = new SubtitleRenderer();
   private long lastSubtitlePreviewTriggerTime = 0L;
   private long lastHonorPreviewTriggerTime = 0L;
   private ComboSubtitleRenderer comboSubtitlePreviewRenderer = ComboSubtitleRenderer.getInstance();
   private long lastComboSubtitlePreviewTriggerTime = 0L;
   private ScoreSubtitleRenderer scorePreviewRenderer = ScoreSubtitleRenderer.getInstance();
   private long lastScorePreviewTriggerTime = 0L;
   private BonusListRenderer bonusListPreviewRenderer = BonusListRenderer.getInstance();
   private long lastBonusListPreviewTriggerTime = 0L;
   private HitInfoRenderer hitInfoPreviewRenderer = new HitInfoRenderer();
   private long lastHitInfoPreviewTriggerTime = 0L;
   private final List<ElementConfigContent.SecondaryTab> secondaryTabs = new ArrayList<>();
   private ElementConfigContent.SecondaryTab selectedSecondaryTab;
   private final List<GDRowRenderer> allConfigRows = new ArrayList<>();
   private final Map<String, Boolean> generalFolderExpanded = new LinkedHashMap<>();
   private double secondaryScrollX = 0.0;
   private double secondaryTargetScrollX = 0.0;
   private double secondaryMaxScroll = 0.0;
   private boolean secondaryDragging = false;
   private boolean secondaryPressed = false;
   private double secondaryLastMouseX = 0.0;
   private int secondaryAreaX1 = 0;
   private int secondaryAreaX2 = 0;
   private int secondaryAreaTop = 0;
   private int secondaryAreaBottom = 0;
   private long lastSecondaryRenderTime = 0L;
   private static final long PREVIEW_TRIGGER_INTERVAL_MS = 2000L;
   private static final long PREVIEW_SUBTITLE_TRIGGER_INTERVAL_MS = 2000L;
   private static final int[] PREVIEW_KILL_TYPES = new int[]{0, 1, 2, 3, 4, 5, 7, 8};
   private static final long PREVIEW_COMBO_TRIGGER_INTERVAL_MS = 1200L;
   private static final long PREVIEW_VALORANT_TRIGGER_INTERVAL_MS = 1350L;
   private static final int[] PREVIEW_COMBO_KILL_TYPES = new int[]{0, 1, 2, 3};
   private static final long PREVIEW_CARD_TRIGGER_INTERVAL_MS = 1200L;
   private static final long PREVIEW_CARD_BAR_TRIGGER_INTERVAL_MS = 1200L;
   private static final long PREVIEW_BATTLEFIELD_TRIGGER_INTERVAL_MS = 1600L;
   private static final int PREVIEW_BONUS_COUNT = 3;
   private static final int[] PREVIEW_BATTLEFIELD_KILL_TYPES = new int[]{0, 1, 2, 3, 5};
   private static final RandomSource PREVIEW_RANDOM = RandomSource.create();
   private GDButton saveButton;
   private GDButton previewPauseButton;
   private GDButton previewSoundButton;
   private ConfirmDialog textureResetDialog;
   private ConfirmDialog textureBindingDialog;
   private boolean isConfirmingReset = false;
   private long resetConfirmTime = 0L;
   private boolean previewPaused = false;
   private boolean previewSoundEnabled = false;

   public ElementConfigContent(Minecraft minecraft, String presetId, String elementId, ElementConfigBuilder builder, Runnable onClose) {
      super(minecraft, Component.translatable("battlefield_ops.client.gui.config.element.title"));
      this.presetId = presetId;
      this.elementId = elementId;
      this.builder = builder;
      this.onClose = onClose;
      this.textureResetDialog = new ConfirmDialog(minecraft, null, null);
      this.textureBindingDialog = new ConfirmDialog(minecraft, null, null);
      JsonObject current = ElementConfigManager.getElementConfig(presetId, elementId);
      this.initialConfig = current != null ? current.deepCopy() : new JsonObject();
      this.previewSoundEnabled = ClientConfigManager.isElementPreviewSoundEnabled();
      PreviewRenderTimeContext.setPaused(false);
      this.rebuildUI();
   }

   private void rebuildUI() {
      this.configRows.clear();
      if (this.builder != null) {
         this.builder.build(this);
      }

      this.allConfigRows.clear();
      this.allConfigRows.addAll(this.configRows);
      this.refreshVisibleRows();
   }

   private void refreshVisibleRows() {
      this.configRows.clear();
      this.updateRowActiveConditions();
      if (this.isKillIconElement() && ElementTextureDefinition.hasTextures(this.elementId)) {
         if (this.selectedSecondaryTab == null) {
            this.ensureSecondaryTabs();
         }

         if (this.selectedSecondaryTab != null) {
            boolean isGeneral = "general".equals(this.selectedSecondaryTab.elementId);

            for (GDRowRenderer row : this.allConfigRows) {
               String key = this.getConfigKey(row);
               if (key == null) {
                  if (isGeneral) {
                     this.configRows.add(row);
                  }
               } else {
                  String textureTab = ElementConfigRegistry.getTextureTab(this.elementId, key);
                  boolean isTextureKey = textureTab != null;
                  if (isGeneral) {
                     if (!isTextureKey) {
                        this.configRows.add(row);
                     }
                  } else if (isTextureKey && textureTab.equals(this.selectedSecondaryTab.elementId)) {
                     this.configRows.add(row);
                  }
               }
            }

            if (isGeneral) {
               this.applyGeneralFolderGrouping();
            } else {
               this.sortConfigRows();
            }
         }
      } else {
         this.configRows.addAll(this.allConfigRows);
         if (!this.isSubtitleElement() && !this.isKillIconElement()) {
            this.sortConfigRows();
         } else {
            this.applyGeneralFolderGrouping();
         }
      }
   }

   private void applyGeneralFolderGrouping() {
      List<GDRowRenderer> sourceRows = new ArrayList<>(this.configRows);
      Map<String, List<GDRowRenderer>> groupedRows = new LinkedHashMap<>();

      for (String categoryId : this.getGeneralFolderOrder()) {
         groupedRows.put(categoryId, new ArrayList<>());
         this.generalFolderExpanded.putIfAbsent(categoryId, false);
      }

      for (GDRowRenderer row : sourceRows) {
         this.applyFolderChildIndent(row, 1);
         String key = this.getConfigKey(row);
         String categoryId = this.resolveGeneralFolderCategory(key);
         groupedRows.computeIfAbsent(categoryId, k -> {
            this.generalFolderExpanded.putIfAbsent(k, false);
            return new ArrayList<>();
         }).add(row);
      }

      this.configRows.clear();

      for (String categoryId : this.getGeneralFolderOrder()) {
         List<GDRowRenderer> rows = groupedRows.get(categoryId);
         if (rows != null && !rows.isEmpty()) {
            this.configRows.add(this.createGeneralFolderHeader(categoryId));
            if (this.generalFolderExpanded.getOrDefault(categoryId, false)) {
               for (GDRowRenderer row : rows) {
                  this.applyFolderChildIndent(row, 3);
               }

               this.configRows.addAll(rows);
            }
         }
      }

      List<GDRowRenderer> otherRows = groupedRows.get("other");
      if (otherRows != null && !otherRows.isEmpty()) {
         this.configRows.addAll(otherRows);
      }
   }

   private void applyFolderChildIndent(GDRowRenderer row, int indentSpaces) {
      if (row != null && row.getColumn(0) != null) {
         GDRowRenderer.Column first = row.getColumn(0);
         if (first != null) {
            if (first.text != null) {
               first.text = this.applyLeadingSpaces(first.text, indentSpaces);
            } else {
               if (first.coloredTexts != null && !first.coloredTexts.isEmpty()) {
                  GDTextRenderer.ColoredText token = first.coloredTexts.get(0);
                  if (token != null && token.text != null) {
                     token.text = this.applyLeadingSpaces(token.text, indentSpaces);
                  }
               }
            }
         }
      }
   }

   private String applyLeadingSpaces(String text, int indentSpaces) {
      if (text != null && !text.isEmpty()) {
         String normalized = text.stripLeading();
         return " ".repeat(Math.max(0, indentSpaces)) + normalized;
      } else {
         return text;
      }
   }

   private List<String> getGeneralFolderOrder() {
      List<String> order = new ArrayList<>();
      order.add("visibility");
      if (this.isSubtitleElement() || "kill_icon/honor".equals(this.elementId)) {
         order.add("content");
      }

      order.add("position");
      order.add("color");
      order.add("effect");
      order.add("timing");
      if (this.isSubtitleElement()) {
         order.add("behavior");
      }

      return order;
   }

   private GDRowRenderer createGeneralFolderHeader(String categoryId) {
      GDRowRenderer row = new GDRowRenderer(0, 0, 0, 0, GuiConstants.COLOR_BLACK, 0.75F, true);
      row.setBackgroundAlpha(0.75F);
      boolean expanded = this.generalFolderExpanded.getOrDefault(categoryId, false);
      String arrow = expanded ? "▼" : "▶";
      String key = "battlefield_ops.client.gui.config.folder.general." + categoryId;
      String label = I18nCompat.exists(key) ? I18n.get(key, new Object[0]) : categoryId;
      row.addColumn(" " + label, -1, GuiConstants.COLOR_GOLD, true, false, button -> {
         this.generalFolderExpanded.put(categoryId, !this.generalFolderExpanded.getOrDefault(categoryId, false));
         this.refreshVisibleRows();
         int sw = this.minecraft.getWindow().getGuiScaledWidth();
         int sh = this.minecraft.getWindow().getGuiScaledHeight();
         this.updateConfigRowsLayout(sw, sh);
      });
      row.addColumn(arrow, 20, GuiConstants.COLOR_GOLD, true, true, button -> {
         this.generalFolderExpanded.put(categoryId, !this.generalFolderExpanded.getOrDefault(categoryId, false));
         this.refreshVisibleRows();
         int sw = this.minecraft.getWindow().getGuiScaledWidth();
         int sh = this.minecraft.getWindow().getGuiScaledHeight();
         this.updateConfigRowsLayout(sw, sh);
      });
      return row;
   }

   private String resolveGeneralFolderCategory(String key) {
      if (key == null) {
         return "other";
      } else {
         Category category = ElementConfigRegistry.getCategory(this.elementId, key);
         return category == null ? "other" : category.name().toLowerCase();
      }
   }

   private void updateRowActiveConditions() {
      for (GDRowRenderer row : this.allConfigRows) {
         String key = this.getConfigKey(row);
         if (key != null) {
            String dep = ElementConfigRegistry.getDependsOn(this.elementId, key);
            if (dep != null) {
               row.setActiveCondition(() -> {
                  JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                  return config == null || !config.has(dep) || config.get(dep).getAsBoolean();
               });
            }

            if (key.startsWith("texture_style_")) {
               String textureKey = key.substring("texture_style_".length());
               String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
               Supplier<Boolean> original = row.getActiveCondition();
               row.setActiveCondition(() -> {
                  JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                  String mode = config != null && config.has(modeKey) ? config.get(modeKey).getAsString() : "official";
                  return "official".equalsIgnoreCase(mode) && original.get();
               });
            }

            if (key.startsWith("custom_texture_")) {
               String textureKey = key.substring("custom_texture_".length());
               String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
               Supplier<Boolean> original = row.getActiveCondition();
               row.setActiveCondition(() -> {
                  JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                  String mode = config != null && config.has(modeKey) ? config.get(modeKey).getAsString() : "official";
                  return "custom".equalsIgnoreCase(mode) && original.get();
               });
            }

            if (key.startsWith("vanilla_texture_")) {
               String textureKey = key.substring("vanilla_texture_".length());
               String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
               Supplier<Boolean> original = row.getActiveCondition();
               row.setActiveCondition(() -> {
                  JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                  String mode = config != null && config.has(modeKey) ? config.get(modeKey).getAsString() : "official";
                  return "vanilla".equalsIgnoreCase(mode) && original.get();
               });
            }
         }
      }

      for (GDRowRenderer rowx : this.allConfigRows) {
         String key = this.getConfigKey(rowx);
         if (key != null && !key.equals("visible")) {
            Supplier<Boolean> specificCondition = rowx.getActiveCondition();
            rowx.setActiveCondition(() -> {
               JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
               boolean isVisible = config == null || !config.has("visible") || config.get("visible").getAsBoolean();
               return !isVisible ? false : specificCondition.get();
            });
         }
      }
   }

   private String getConfigKey(GDRowRenderer row) {
      if (row instanceof ActionConfigEntry) {
         return ((ActionConfigEntry)row).getKey();
      } else if (row instanceof BooleanConfigEntry) {
         return ((BooleanConfigEntry)row).getKey();
      } else if (row instanceof IntegerConfigEntry) {
         return ((IntegerConfigEntry)row).getKey();
      } else if (row instanceof FloatConfigEntry) {
         return ((FloatConfigEntry)row).getKey();
      } else if (row instanceof StringConfigEntry) {
         return ((StringConfigEntry)row).getKey();
      } else if (row instanceof HexColorConfigEntry) {
         return ((HexColorConfigEntry)row).getKey();
      } else {
         return row instanceof FixedChoiceConfigEntry ? ((FixedChoiceConfigEntry)row).getKey() : null;
      }
   }

   public String getPresetId() {
      return this.presetId;
   }

   public String getElementId() {
      return this.elementId;
   }

   @Override
   protected void updateSubtitle(int x1, int y1, int x2) {
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

      String nameKey = "battlefield_ops.element.name." + this.elementId.replace("/", ".");
      String elementName = I18nCompat.exists(nameKey) ? I18n.get(nameKey, new Object[0]) : this.elementId;
      List<GDTextRenderer.ColoredText> elementTexts = new ArrayList<>();
      elementTexts.add(new GDTextRenderer.ColoredText(I18n.get("battlefield_ops.client.gui.config.generic.current_element", new Object[0]), -1));
      elementTexts.add(new GDTextRenderer.ColoredText("[" + this.elementId + "] ", -5592406));
      elementTexts.add(new GDTextRenderer.ColoredText(elementName, GuiConstants.COLOR_GOLD));
      int line2Y = y1 + 9 + 2;
      if (this.elementInfoRenderer == null) {
         this.elementInfoRenderer = new GDTextRenderer(elementTexts, x1, line2Y, x2, line2Y + 9, 1.0F, false);
      } else {
         this.elementInfoRenderer.setX1(x1);
         this.elementInfoRenderer.setY1(line2Y);
         this.elementInfoRenderer.setX2(x2);
         this.elementInfoRenderer.setY2(line2Y + 9);
         this.elementInfoRenderer.setColoredTexts(elementTexts);
      }

      this.calculatedBottom = line2Y + 9;
   }

   @Override
   public void updateLayout(int screenWidth, int screenHeight) {
      super.updateLayout(screenWidth, screenHeight);
      this.area1Bottom = this.calculatedBottom;
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

      this.updateSecondaryTabLayout(screenWidth);
   }

   @Override
   public void onTabOpen() {
      super.onTabOpen();
      if (ClientConfigManager.shouldShowElementIntro()) {
         ClientConfigManager.markElementIntroShown();
         if (this.elementId != null && !this.elementId.startsWith("subtitle/")) {
            this.promptDialog
               .show(
                  I18n.get("battlefield_ops.client.gui.prompt.element_intro", new Object[0]),
                  PromptDialog.PromptType.INFO,
                  () -> this.promptDialog
                        .show(I18n.get("battlefield_ops.client.gui.prompt.element_texture_intro", new Object[0]), PromptDialog.PromptType.INFO, null)
               );
         } else {
            this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.element_intro", new Object[0]), PromptDialog.PromptType.INFO, null);
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      boolean resetDialogVisible = this.textureResetDialog != null && this.textureResetDialog.isVisible();
      boolean bindingDialogVisible = this.textureBindingDialog != null && this.textureBindingDialog.isVisible();
      boolean promptVisible = this.promptDialog.isVisible();
      boolean dialogVisible = resetDialogVisible
         || bindingDialogVisible
         || promptVisible
         || this.textInputDialog.isVisible()
         || this.colorPickerDialog.isVisible()
         || this.choiceListDialog.isVisible();
      int effectiveMouseX = dialogVisible ? -1 : mouseX;
      int effectiveMouseY = dialogVisible ? -1 : mouseY;
      if (this.gridWidget == null) {
         this.updateLayout(screenWidth, screenHeight);
      }

      if (this.gridWidget != null) {
         guiGraphics.pose().pushPose();
         this.gridWidget.render(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick, Collections.emptyList());
         guiGraphics.pose().popPose();
         if (this.isKillIconElement() && this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId)) {
            PreviewTextureFocusContext.activate(this.elementId, this.selectedSecondaryTab.elementId);
         } else {
            PreviewTextureFocusContext.clear();
         }

         PreviewRenderTimeContext.beginPreviewFrame();

         try {
            this.renderScrollingPreview(guiGraphics, partialTick);
            this.renderComboPreview(guiGraphics, partialTick);
            this.renderValorantPreview(guiGraphics, partialTick);
            this.renderCardPreview(guiGraphics, partialTick);
            this.renderCardBarPreview(guiGraphics, partialTick);
            this.renderBattlefieldPreview(guiGraphics, partialTick);
            this.renderKillFeedPreview(guiGraphics, partialTick);
            this.renderComboSubtitlePreview(guiGraphics, partialTick);
            this.renderScorePreview(guiGraphics, partialTick);
            this.renderBonusListPreview(guiGraphics, partialTick);
            this.renderHitInfoPreview(guiGraphics, partialTick);
            this.renderHonorPreview(guiGraphics, partialTick);
         } finally {
            PreviewRenderTimeContext.endPreviewFrame();
         }

         PreviewTextureFocusContext.clear();
      }

      if (this.elementInfoRenderer != null) {
         this.elementInfoRenderer.render(guiGraphics, partialTick);
      }

      if (dialogVisible) {
         this.renderSecondaryTabs(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick);
      }

      if (resetDialogVisible) {
         super.render(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick, screenWidth, screenHeight, headerHeight);
      } else {
         super.render(guiGraphics, mouseX, mouseY, partialTick, screenWidth, screenHeight, headerHeight);
      }

      if (!dialogVisible) {
         this.renderSecondaryTabs(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick);
      }

      if (this.isConfirmingReset) {
         long elapsed = System.currentTimeMillis() - this.resetConfirmTime;
         if (elapsed > 3000L) {
            this.isConfirmingReset = false;
            if (this.resetButton != null) {
               this.resetButton.setMessage(Component.translatable("battlefield_ops.client.gui.config.button.reset_element"));
               this.resetButton.setTextColor(-1);
            }
         }
      }

      if (resetDialogVisible) {
         this.textureResetDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      if (bindingDialogVisible) {
         this.textureBindingDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   @Override
   protected void updateScroll(float dt, int screenHeight) {
      if (this.useDefaultScroll) {
         int contentY = 34 + this.getContentTopOffset();
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

   @Override
   protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      if (this.configRows.isEmpty()) {
         Component noContent = Component.translatable("battlefield_ops.client.gui.config.no_content");
         int textWidth = this.minecraft.font.width(noContent);
         int area1Right = (screenWidth - 16) / 3 + 8;
         int contentX = area1Right + 8 + (screenWidth - area1Right - 16 - textWidth) / 2;
         int contentY = 34 + this.getContentTopOffset() + (screenHeight - (34 + this.getContentTopOffset()) - 9) / 2;
         guiGraphics.drawString(this.minecraft.font, noContent, contentX, contentY, -5592406, true);
      } else {
         if (this.useDefaultScroll) {
            float dt = this.minecraft.getTimer().getRealtimeDeltaTicks() / 20.0F;
            if (this.isDragging) {
               double diff = (double)mouseY - this.lastMouseY;
               this.targetScrollY -= diff;
               this.lastMouseY = (double)mouseY;
            }

            this.updateScroll(dt, screenHeight);
            int area1Right = (screenWidth - 16) / 3 + 8;
            int contentX = area1Right + 8;
            int contentY = 34 + this.getContentTopOffset();
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

   @Override
   protected void updateConfigRowsLayout(int screenWidth, int screenHeight) {
      int area1Right = (screenWidth - 16) / 3 + 8;
      int contentX = area1Right + 8;
      int contentY = 34 + this.getContentTopOffset();
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

   private void renderScrollingPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/scrolling".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         boolean isSpecific = this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId);
         if (isSpecific) {
            String tex = this.selectedSecondaryTab.elementId;
            int killType = KillTypeRegistry.getKillTypeByTextureKey(tex);
            if (killType != -1 && !this.previewPaused && now - this.lastPreviewTriggerTime >= 2000L) {
               this.scrollingPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1));
               this.triggerPreviewSound(killType, 1);
               this.lastPreviewTriggerTime = now;
            }
         } else if (!this.previewPaused && now - this.lastPreviewTriggerTime >= 2000L) {
            int killType = PREVIEW_KILL_TYPES[this.previewKillTypeIndex % PREVIEW_KILL_TYPES.length];
            this.previewKillTypeIndex++;
            this.scrollingPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1));
            this.triggerPreviewSound(killType, 1);
            this.lastPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.scrollingPreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderComboPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/combo".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         boolean isSpecific = this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId);
         if (isSpecific) {
            String tex = this.selectedSecondaryTab.elementId;
            int combo = 1;

            try {
               if (tex.startsWith("combo_")) {
                  combo = Integer.parseInt(tex.substring(6));
               }
            } catch (Exception var12) {
            }

            if (!this.previewPaused && now - this.lastComboPreviewTriggerTime >= 1200L) {
               this.comboPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(0, -1, combo));
               this.triggerPreviewSound(0, combo);
               this.lastComboPreviewTriggerTime = now;
            }
         } else if (!this.previewPaused && now - this.lastComboPreviewTriggerTime >= 1200L) {
            int killType = PREVIEW_COMBO_KILL_TYPES[this.previewComboKillTypeIndex % PREVIEW_COMBO_KILL_TYPES.length];
            int comboCount = this.previewComboCount;
            this.previewComboKillTypeIndex++;
            this.previewComboCount = this.previewComboCount >= 6 ? 1 : this.previewComboCount + 1;
            this.comboPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1, comboCount));
            this.triggerPreviewSound(killType, comboCount);
            this.lastComboPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.comboPreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderValorantPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/valorant".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastValorantPreviewTriggerTime >= 1350L) {
            int killType = 0;
            int comboCount;
            if (this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId)) {
               comboCount = !"bar".equals(this.selectedSecondaryTab.elementId) && !"large_sparks".equals(this.selectedSecondaryTab.elementId) ? 3 : 5;
               if ("headshot".equals(this.selectedSecondaryTab.elementId) || "x_sparks".equals(this.selectedSecondaryTab.elementId)) {
                  killType = 1;
               }
            } else {
               comboCount = this.previewValorantComboCount;
               this.previewValorantComboCount = this.previewValorantComboCount >= 5 ? 1 : this.previewValorantComboCount + 1;
            }

            this.valorantPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1, comboCount));
            this.triggerPreviewSound(killType, comboCount);
            this.lastValorantPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.valorantPreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderCardPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/card".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId).deepCopy();
         boolean isSpecific = this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId);
         if (isSpecific) {
            String tex = this.selectedSecondaryTab.elementId;
            String team = "ct";
            if (tex.contains("_ct")) {
               team = "ct";
            } else if (tex.contains("_t")) {
               team = "t";
            }

            config.addProperty("team", team);
            config.addProperty("dynamic_card_style", false);
            int killType = 0;
            if (tex.startsWith("headshot")) {
               killType = 1;
            } else if (tex.startsWith("explosion")) {
               killType = 2;
            } else if (tex.startsWith("crit")) {
               killType = 3;
            }

            if (tex.startsWith("light")) {
               config.addProperty("show_light", true);
            } else {
               config.addProperty("show_light", false);
            }

            if (!this.previewPaused && now - this.lastCardPreviewTriggerTime >= 1200L) {
               this.cardPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1, 1));
               this.triggerPreviewSound(killType, 1);
               this.lastCardPreviewTriggerTime = now;
            }
         } else if (!this.previewPaused && now - this.lastCardPreviewTriggerTime >= 1200L) {
            int killTypex = PREVIEW_COMBO_KILL_TYPES[this.previewCardKillTypeIndex % PREVIEW_COMBO_KILL_TYPES.length];
            int comboCount = this.previewCardComboCount;
            this.previewCardKillTypeIndex++;
            this.previewCardComboCount = this.previewCardComboCount >= 6 ? 1 : this.previewCardComboCount + 1;
            this.cardPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killTypex, -1, comboCount));
            this.triggerPreviewSound(killTypex, comboCount);
            this.lastCardPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.cardPreviewRenderer.renderPreviewAt(guiGraphics, partialTick, originX, originY, config)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderCardBarPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/card_bar".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId).deepCopy();
         boolean isSpecific = this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId);
         if (isSpecific) {
            String tex = this.selectedSecondaryTab.elementId;
            String team = "ct";
            if (tex.contains("_ct")) {
               team = "ct";
            } else if (tex.contains("_t")) {
               team = "t";
            }

            config.addProperty("team", team);
            config.addProperty("dynamic_card_style", false);
            if (!this.previewPaused && now - this.lastCardBarPreviewTriggerTime >= 1200L) {
               this.cardBarPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(0, -1, 1));
               this.triggerPreviewSound(0, 1);
               this.lastCardBarPreviewTriggerTime = now;
            }
         } else if (!this.previewPaused && now - this.lastCardBarPreviewTriggerTime >= 1200L) {
            int killType = PREVIEW_COMBO_KILL_TYPES[this.previewCardBarKillTypeIndex % PREVIEW_COMBO_KILL_TYPES.length];
            int comboCount = this.previewCardBarComboCount;
            this.previewCardBarKillTypeIndex++;
            this.previewCardBarComboCount = this.previewCardBarComboCount >= 6 ? 1 : this.previewCardBarComboCount + 1;
            this.cardBarPreviewRenderer.trigger(IHudRenderer.TriggerContext.of(killType, -1, comboCount));
            this.triggerPreviewSound(killType, comboCount);
            this.lastCardBarPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.cardBarPreviewRenderer.renderPreviewAt(guiGraphics, partialTick, originX, originY, config)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderBattlefieldPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/battlefield1".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         boolean isSpecific = this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId);
         if (isSpecific) {
            String tex = this.selectedSecondaryTab.elementId;
            int killType = -1;
            if ("default".equals(tex)) {
               killType = 0;
            } else if ("headshot".equals(tex)) {
               killType = 1;
            } else if ("explosion".equals(tex)) {
               killType = 2;
            } else if ("crit".equals(tex)) {
               killType = 3;
            } else if ("destroy_vehicle".equals(tex)) {
               killType = 5;
            }

            if (killType != -1 && !this.previewPaused && now - this.lastBattlefieldPreviewTriggerTime >= 1600L) {
               String weaponName = this.resolveRandomItemName();
               this.battlefield1PreviewRenderer.triggerPreview(killType, weaponName, "Minecraft_GD656", "20");
               this.triggerPreviewSound(killType, 1);
               this.lastBattlefieldPreviewTriggerTime = now;
            }
         } else if (!this.previewPaused && now - this.lastBattlefieldPreviewTriggerTime >= 1600L) {
            int killTypex = PREVIEW_BATTLEFIELD_KILL_TYPES[this.previewBattlefieldKillTypeIndex % PREVIEW_BATTLEFIELD_KILL_TYPES.length];
            this.previewBattlefieldKillTypeIndex++;
            String weaponName = this.resolveRandomItemName();
            this.battlefield1PreviewRenderer.triggerPreview(killTypex, weaponName, "Minecraft_GD656", "20");
            this.triggerPreviewSound(killTypex, 1);
            this.lastBattlefieldPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.battlefield1PreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderKillFeedPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("subtitle/kill_feed".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastSubtitlePreviewTriggerTime >= 2000L) {
            int killType = PREVIEW_KILL_TYPES[this.previewKillTypeIndex % PREVIEW_KILL_TYPES.length];
            this.previewKillTypeIndex++;
            String weaponName = this.resolveRandomItemName();
            this.subtitlePreviewRenderer.triggerPreview(killType, weaponName, "Minecraft_GD656");
            this.triggerPreviewSound(killType, 1);
            this.lastSubtitlePreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.subtitlePreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderComboSubtitlePreview(GuiGraphics guiGraphics, float partialTick) {
      if ("subtitle/combo".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastComboSubtitlePreviewTriggerTime >= 2000L) {
            int combo = 1 + PREVIEW_RANDOM.nextInt(8);
            boolean isAssist = PREVIEW_RANDOM.nextBoolean();
            this.comboSubtitlePreviewRenderer.triggerPreview(isAssist, combo);
            this.triggerPreviewSound(isAssist ? 4 : 0, combo);
            this.lastComboSubtitlePreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.comboSubtitlePreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderScorePreview(GuiGraphics guiGraphics, float partialTick) {
      if ("subtitle/score".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastScorePreviewTriggerTime >= 2000L) {
            float score = 0.1F + PREVIEW_RANDOM.nextFloat() * 99.9F;
            this.scorePreviewRenderer.addScore(score);
            this.lastScorePreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.scorePreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderBonusListPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("subtitle/bonus_list".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastBonusListPreviewTriggerTime >= 2000L) {
            JsonObject config = ElementConfigManager.getElementConfig(this.presetId, "subtitle/bonus_list");
            if (config == null) {
               return;
            }

            boolean enableKillFeed = config.has("enable_kill_feed") && config.get("enable_kill_feed").getAsBoolean();
            String weaponName = enableKillFeed ? this.resolveRandomItemName() : "";
            String victimName = "Minecraft_GD656";

            for (int type : this.resolveRandomBonusTypes(3)) {
               float score = 0.1F + PREVIEW_RANDOM.nextFloat() * 99.9F;
               String extraData = this.resolveBonusPreviewExtraData(type, config);
               this.bonusListPreviewRenderer.triggerPreview(type, score, extraData, weaponName, victimName, config);
            }

            this.triggerPreviewSound(0, 1);
            this.lastBonusListPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.bonusListPreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderHonorPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("kill_icon/honor".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastHonorPreviewTriggerTime >= 1000L && !HonorRenderer.getInstance().isDisplaying()) {
            List<String> honorIds = new ArrayList<>(HonorRegistry.getIds());
            if (!honorIds.isEmpty()) {
               Random random = new Random();
               String id = honorIds.get(random.nextInt(honorIds.size()));
               String extra = random.nextBoolean() ? id + ":style=m" : id;
               HonorRenderer.getInstance().trigger(IHudRenderer.TriggerContext.of(0, extra));
            }

            this.lastHonorPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         float baseW = 88.0F;
         float baseH = 49.0F;
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> HonorRenderer.getInstance().renderAt(guiGraphics, partialTick, originX, originY, baseW, baseH)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderHitInfoPreview(GuiGraphics guiGraphics, float partialTick) {
      if ("subtitle/hit_info".equals(this.elementId) && this.gridWidget != null) {
         long now = System.currentTimeMillis();
         if (!this.previewPaused && now - this.lastHitInfoPreviewTriggerTime >= 2000L) {
            this.hitInfoPreviewRenderer.triggerPreview();
            this.lastHitInfoPreviewTriggerTime = now;
         }

         float originX = this.gridWidget.getOriginX();
         float originY = this.gridWidget.getOriginY();
         int scissorX1 = this.gridWidget.getX();
         int scissorY1 = this.gridWidget.getY();
         int scissorX2 = scissorX1 + this.gridWidget.getWidth();
         int scissorY2 = scissorY1 + this.gridWidget.getHeight();
         guiGraphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         this.renderPreviewWithRotation(
            guiGraphics, partialTick, originX, originY, () -> this.hitInfoPreviewRenderer.renderAt(guiGraphics, partialTick, originX, originY)
         );
         RenderSystem.disableBlend();
         guiGraphics.disableScissor();
      }
   }

   private void renderPreviewWithRotation(GuiGraphics guiGraphics, float partialTick, float originX, float originY, Runnable renderTask) {
      JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
      float rotationAngle = config != null && config.has("rotation_angle") ? config.get("rotation_angle").getAsFloat() : 0.0F;
      if (Math.abs(rotationAngle) <= 0.001F) {
         renderTask.run();
      } else {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(originX, originY, 0.0F);
         guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationAngle));
         guiGraphics.pose().translate(-originX, -originY, 0.0F);
         renderTask.run();
         guiGraphics.pose().popPose();
      }
   }

   private void triggerPreviewSound(int killType, int comboCount) {
      if (this.previewSoundEnabled && this.elementId != null && this.elementId.contains("/")) {
         String[] parts = this.elementId.split("/", 2);
         if (parts.length == 2) {
            SoundTriggerManager.tryPlaySound(parts[0], parts[1], killType, Math.max(1, comboCount), false, false);
         }
      }
   }

   private String resolveRandomItemName() {
      for (int i = 0; i < 10; i++) {
         Optional<Reference<Item>> holder = BuiltInRegistries.ITEM.getRandom(PREVIEW_RANDOM);
         if (holder.isPresent()) {
            Item item = (Item)holder.get().value();
            if (item != Items.AIR) {
               return new ItemStack(item).getHoverName().getString();
            }
         }
      }

      return new ItemStack(Items.IRON_SWORD).getHoverName().getString();
   }

   private List<Integer> resolveRandomBonusTypes(int count) {
      List<Integer> types = new ArrayList<>();

      for (String name : BonusType.getAllNames()) {
         int type = BonusType.getTypeByName(name);
         if (type >= 0) {
            types.add(type);
         }
      }

      for (int i = types.size() - 1; i > 0; i--) {
         int j = PREVIEW_RANDOM.nextInt(i + 1);
         Collections.swap(types, i, j);
      }

      return (List<Integer>)(types.size() <= count ? types : new ArrayList<>(types.subList(0, count)));
   }

   private String resolveBonusPreviewExtraData(int type, JsonObject config) {
      int defaultValue = PREVIEW_RANDOM.nextInt(100) + 1;
      int oneBulletValue = PREVIEW_RANDOM.nextInt(9) + 2;
      String candidate = type == 20 ? String.valueOf(oneBulletValue) : String.valueOf(defaultValue);
      String format = BonusListRenderer.getEffectiveFormat(type, candidate, config);
      return !format.contains("<combo>") && !format.contains("<multi_kill>") && !format.contains("<distance>") && !format.contains("<streak>") ? "" : candidate;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.textureResetDialog != null && this.textureResetDialog.isVisible()) {
         return this.textureResetDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.textureBindingDialog != null && this.textureBindingDialog.isVisible()) {
         return this.textureBindingDialog.keyPressed(keyCode, scanCode, modifiers);
      } else if (super.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else if (keyCode == 256) {
         this.closeContent();
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected void updateResetButtonState() {
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
      int row1ButtonWidth = (totalWidth - 2) / 3;
      if (this.resetButton == null) {
         this.resetButton = new GDButton(
            x1, row1Y, row1ButtonWidth, buttonHeight, Component.translatable("battlefield_ops.client.gui.config.button.reset_element"), btn -> {
               if (this.isConfirmingReset) {
                  JsonObject safeDefaults = ElementConfigManager.getResetDefaultConfig(this.presetId, this.elementId);
                  if (!safeDefaults.entrySet().isEmpty()) {
                     ElementConfigManager.setElementConfig(this.presetId, this.elementId, safeDefaults);
                     ExternalTextureManager.resetTexturesForElement(this.presetId, this.elementId);
                  }

                  this.isConfirmingReset = false;
                  this.closeContent();
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
      int pauseX = x1 + row1ButtonWidth + 1;
      if (this.previewPauseButton == null) {
         this.previewPauseButton = new GDButton(
            pauseX,
            row1Y,
            row1ButtonWidth,
            buttonHeight,
            Component.translatable("battlefield_ops.client.gui.config.button.pause_preview"),
            btn -> {
               this.previewPaused = !this.previewPaused;
               PreviewRenderTimeContext.setPaused(this.previewPaused);
               btn.setMessage(
                  Component.translatable(
                     this.previewPaused ? "battlefield_ops.client.gui.config.button.play_preview" : "battlefield_ops.client.gui.config.button.pause_preview"
                  )
               );
            }
         );
      }

      this.previewPauseButton.setX(pauseX);
      this.previewPauseButton.setY(row1Y);
      this.previewPauseButton.setWidth(row1ButtonWidth);
      this.previewPauseButton
         .setMessage(
            Component.translatable(
               this.previewPaused ? "battlefield_ops.client.gui.config.button.play_preview" : "battlefield_ops.client.gui.config.button.pause_preview"
            )
         );
      this.previewPauseButton.render(guiGraphics, mouseX, mouseY, partialTick);
      int soundX = pauseX + row1ButtonWidth + 1;
      if (this.previewSoundButton == null) {
         this.previewSoundButton = new GDButton(
            soundX,
            row1Y,
            row1ButtonWidth,
            buttonHeight,
            Component.translatable("battlefield_ops.client.gui.config.button.play_sound_preview"),
            btn -> {
               this.previewSoundEnabled = !this.previewSoundEnabled;
               ClientConfigManager.setElementPreviewSoundEnabled(this.previewSoundEnabled);
               btn.setMessage(
                  Component.translatable(
                     this.previewSoundEnabled
                        ? "battlefield_ops.client.gui.config.button.disable_sound_preview"
                        : "battlefield_ops.client.gui.config.button.play_sound_preview"
                  )
               );
            }
         );
      }

      this.previewSoundButton.setX(soundX);
      this.previewSoundButton.setY(row1Y);
      this.previewSoundButton.setWidth(row1ButtonWidth);
      this.previewSoundButton
         .setMessage(
            Component.translatable(
               this.previewSoundEnabled
                  ? "battlefield_ops.client.gui.config.button.disable_sound_preview"
                  : "battlefield_ops.client.gui.config.button.play_sound_preview"
            )
         );
      this.previewSoundButton.render(guiGraphics, mouseX, mouseY, partialTick);
      int row2ButtonWidth = (totalWidth - 1) / 2;
      if (this.cancelButton == null) {
         this.cancelButton = new GDButton(
            x1,
            row2Y,
            row2ButtonWidth,
            buttonHeight,
            Component.translatable("battlefield_ops.client.gui.button.cancel"),
            btn -> this.discardElementChangesAndClose()
         );
      }

      this.cancelButton.setX(x1);
      this.cancelButton.setY(row2Y);
      this.cancelButton.setWidth(row2ButtonWidth);
      this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.saveButton == null) {
         this.saveButton = new GDButton(
            x1 + row2ButtonWidth + 1,
            row2Y,
            row2ButtonWidth,
            buttonHeight,
            Component.translatable("battlefield_ops.client.gui.config.button.save_and_exit"),
            btn -> this.closeContent()
         );
      }

      this.saveButton.setX(x1 + row2ButtonWidth + 1);
      this.saveButton.setY(row2Y);
      this.saveButton.setWidth(row2ButtonWidth);
      this.saveButton.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.textureResetDialog != null && this.textureResetDialog.isVisible()) {
         return this.textureResetDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.textureBindingDialog != null && this.textureBindingDialog.isVisible()) {
         return this.textureBindingDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseClicked(mouseX, mouseY, button);
      } else if (this.getTextInputDialog().isVisible()) {
         return this.getTextInputDialog().mouseClicked(mouseX, mouseY, button);
      } else if (this.getColorPickerDialog().isVisible()) {
         return this.getColorPickerDialog().mouseClicked(mouseX, mouseY, button);
      } else if (this.getChoiceListDialog().isVisible()) {
         return this.getChoiceListDialog().mouseClicked(mouseX, mouseY, button);
      } else if (button == 1 && "subtitle/score".equals(this.elementId) && this.gridWidget != null && this.gridWidget.isMouseOver(mouseX, mouseY)) {
         this.scorePreviewRenderer.resetPreview();
         return true;
      } else if (this.gridWidget != null && this.gridWidget.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.previewPauseButton != null && this.previewPauseButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.previewSoundButton != null && this.previewSoundButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.saveButton != null && this.saveButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         return this.handleSecondaryTabClick(mouseX, mouseY, button) ? true : super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.handleSecondaryTabRelease()) {
         return true;
      } else {
         return this.gridWidget != null && this.gridWidget.mouseReleased(mouseX, mouseY, button) ? true : super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.handleSecondaryTabDrag(mouseX, mouseY, dragX)) {
         return true;
      } else {
         return this.gridWidget != null && this.gridWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)
            ? true
            : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.textureResetDialog != null && this.textureResetDialog.isVisible()) {
         return this.textureResetDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.textureBindingDialog != null && this.textureBindingDialog.isVisible()) {
         return this.textureBindingDialog.mouseScrolled(mouseX, mouseY, delta);
      } else if (this.promptDialog.isVisible()) {
         return this.promptDialog.mouseScrolled(mouseX, mouseY, delta);
      } else {
         return this.handleSecondaryTabScroll(mouseX, mouseY, delta) ? true : super.mouseScrolled(mouseX, mouseY, delta);
      }
   }

   @Override
   public boolean charTyped(char codePoint, int modifiers) {
      if (this.textureResetDialog != null && this.textureResetDialog.isVisible()) {
         return this.textureResetDialog.charTyped(codePoint, modifiers);
      } else if (this.textureBindingDialog != null && this.textureBindingDialog.isVisible()) {
         return this.textureBindingDialog.charTyped(codePoint, modifiers);
      } else {
         return this.promptDialog.isVisible() ? this.promptDialog.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
      }
   }

   @Override
   public void onFilesDrop(List<Path> paths) {
      if (paths != null && !paths.isEmpty()) {
         if (this.isKillIconElement() && ElementTextureDefinition.hasTextures(this.elementId)) {
            if (this.selectedSecondaryTab != null && !"general".equals(this.selectedSecondaryTab.elementId)) {
               Path pngPath = null;
               Path gifPath = null;

               for (Path path : paths) {
                  String lower = path.toString().toLowerCase();
                  if (lower.endsWith(".png")) {
                     pngPath = path;
                     break;
                  }

                  if (lower.endsWith(".gif")) {
                     gifPath = path;
                     break;
                  }
               }

               if (pngPath == null && gifPath == null) {
                  this.promptDialog
                     .show(I18n.get("battlefield_ops.client.gui.prompt.texture_drop_invalid", new Object[0]), PromptDialog.PromptType.ERROR, null);
               } else {
                  String textureKey = this.selectedSecondaryTab.elementId;
                  String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
                  String customKey = ElementTextureDefinition.getCustomTextureKey(textureKey);
                  Path finalGifPath = gifPath;
                  Path finalPngPath = pngPath;
                  Runnable importAction = () -> {
                     if (finalGifPath != null) {
                        this.handleGifDrop(finalGifPath, textureKey, modeKey, customKey);
                     } else if (finalPngPath != null) {
                        String customFileName = ExternalTextureManager.createCustomTextureFromFile(
                           this.presetId, finalPngPath, finalPngPath.getFileName().toString(), false, null, null, null
                        );
                        if (customFileName != null) {
                           JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                           if (current == null) {
                              current = new JsonObject();
                           }

                           current.addProperty(modeKey, "custom");
                           current.addProperty(customKey, customFileName);
                           ElementConfigManager.setElementConfig(this.presetId, this.elementId, current);
                           this.rebuildUI();
                           Runnable showSuccess = () -> this.promptDialog
                                 .show(
                                    I18n.get("battlefield_ops.client.gui.prompt.texture_replace_success", new Object[0]),
                                    PromptDialog.PromptType.SUCCESS,
                                    null
                                 );
                           this.showTextureBindingPrompt(
                              textureKey,
                              customFileName,
                              false,
                              () -> this.applyTextureBindingMeta(textureKey, customFileName, false),
                              showSuccess,
                              showSuccess
                           );
                        } else {
                           this.promptDialog
                              .show(I18n.get("battlefield_ops.client.gui.prompt.texture_replace_fail", new Object[0]), PromptDialog.PromptType.ERROR, null);
                        }
                     }
                  };
                  importAction.run();
               }
            } else {
               this.promptDialog
                  .show(I18n.get("battlefield_ops.client.gui.prompt.texture_drop_wrong_tab", new Object[0]), PromptDialog.PromptType.WARNING, null);
            }
         }
      }
   }

   private void openTextureImportDialog() {
      this.getTextInputDialog().show("", I18n.get("battlefield_ops.client.gui.prompt.texture_import_title", new Object[0]), input -> {
         Path path = this.tryParsePath(input);
         if (path != null) {
            this.onFilesDrop(List.of(path));
         }
      }, input -> this.isExistingFileWithExtension(input, "png", "gif"));
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

   private void handleGifDrop(Path gifPath, String textureKey, String modeKey, String customKey) {
      try {
         File tempFile = File.createTempFile("gd656_gif_convert_", ".png");
         tempFile.deleteOnExit();
         GifToSpriteSheetConverter.ConversionResult result = GifToSpriteSheetConverter.convertGifToSpriteSheet(gifPath, tempFile.toPath());
         if (result.success && result.outputPng != null) {
            String customFileName = ExternalTextureManager.createCustomTextureFromFile(
               this.presetId, result.outputPng.toPath(), gifPath.getFileName().toString(), true, result.frameCount, result.intervalMs, "vertical"
            );
            if (customFileName != null) {
               JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
               if (current == null) {
                  current = new JsonObject();
               }

               current.addProperty(modeKey, "custom");
               current.addProperty(customKey, customFileName);
               ElementConfigManager.setElementConfig(this.presetId, this.elementId, current);
               this.rebuildUI();
               Runnable showSuccess = () -> this.promptDialog
                     .show(I18n.get("battlefield_ops.client.gui.prompt.gif_import_success", new Object[0]), PromptDialog.PromptType.SUCCESS, null);
               this.showTextureBindingPrompt(
                  textureKey, customFileName, true, () -> this.applyTextureBindingMeta(textureKey, customFileName, true), showSuccess, showSuccess
               );
            } else {
               this.promptDialog
                  .show(I18n.get("battlefield_ops.client.gui.prompt.texture_replace_fail", new Object[0]), PromptDialog.PromptType.ERROR, null);
            }
         } else {
            this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.gif_convert_fail", new Object[0]), PromptDialog.PromptType.ERROR, null);
         }
      } catch (IOException var10) {
         LOGGER.error("GIF conversion failed", var10);
         this.promptDialog
            .show(
               I18n.get("battlefield_ops.client.gui.prompt.gif_convert_fail", new Object[0]) + ": " + var10.getMessage(),
               PromptDialog.PromptType.ERROR,
               null
            );
      }
   }

   public void handleTextureBindingChanged(String textureKey, String fileName, boolean gifDerived) {
      if (textureKey != null && fileName != null) {
         this.showTextureBindingPrompt(textureKey, fileName, gifDerived, () -> this.applyTextureBindingMeta(textureKey, fileName, gifDerived), null, null);
      }
   }

   public void rebuildUIFromConfig() {
      this.rebuildUI();
   }

   private void showTextureBindingPrompt(String textureKey, String fileName, boolean gifDerived, Runnable onConfirm, Runnable onCancel, Runnable onComplete) {
      if (this.textureBindingDialog != null) {
         if (fileName != null && !fileName.isEmpty()) {
            this.getChoiceListDialog().hide();
            String message = gifDerived
               ? I18n.get("battlefield_ops.client.gui.prompt.texture_binding_gif", new Object[0])
               : I18n.get("battlefield_ops.client.gui.prompt.texture_binding_png", new Object[0]);
            this.textureBindingDialog.show(message, ConfirmDialog.PromptType.INFO, () -> this.runAsyncTask(onConfirm, () -> {
                  this.rebuildUI();
                  if (onComplete != null) {
                     onComplete.run();
                  }
               }), onCancel);
         }
      }
   }

   private void resetTextureAnimationConfigs(String textureKey) {
      if (textureKey != null) {
         JsonObject defaults = ElementConfigManager.getResetDefaultConfig(this.presetId, this.elementId);
         if (defaults != null) {
            JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
            JsonObject target = current != null ? current : defaults.deepCopy();
            String prefix = "anim_" + textureKey + "_";
            String[] keys = new String[]{
               prefix + "enable_texture_animation",
               prefix + "texture_animation_total_frames",
               prefix + "texture_animation_interval_ms",
               prefix + "texture_animation_orientation",
               prefix + "texture_animation_loop",
               prefix + "texture_animation_play_style",
               prefix + "texture_frame_width_ratio",
               prefix + "texture_frame_height_ratio",
               prefix + "texture_scale",
               prefix + "texture_final_opacity",
               prefix + "texture_x_offset",
               prefix + "texture_y_offset"
            };

            for (String key : keys) {
               if (defaults.has(key)) {
                  target.add(key, defaults.get(key));
               }
            }

            ElementConfigManager.setElementConfig(this.presetId, this.elementId, target);
         }
      }
   }

   private void resetTextureAnimationControls(String textureKey) {
      if (textureKey != null) {
         JsonObject defaults = ElementConfigManager.getResetDefaultConfig(this.presetId, this.elementId);
         if (defaults != null) {
            JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
            JsonObject target = current != null ? current : defaults.deepCopy();
            String prefix = "anim_" + textureKey + "_";
            String[] keys = new String[]{
               prefix + "enable_texture_animation",
               prefix + "texture_animation_total_frames",
               prefix + "texture_animation_interval_ms",
               prefix + "texture_animation_orientation",
               prefix + "texture_animation_loop",
               prefix + "texture_animation_play_style"
            };

            for (String key : keys) {
               if (defaults.has(key)) {
                  target.add(key, defaults.get(key));
               }
            }

            ElementConfigManager.setElementConfig(this.presetId, this.elementId, target);
         }
      }
   }

   private void applyTextureBindingMeta(String textureKey, String fileName, boolean gifDerived) {
      if (gifDerived) {
         this.applyGifAnimationMetaToSharedTextures(fileName);
      } else {
         boolean customTexture = ExternalTextureManager.isCustomTextureName(fileName);
         boolean vanillaTexture = ExternalTextureManager.isVanillaTexturePath(fileName);
         boolean officialTexture = !customTexture && !vanillaTexture;
         String defaultOfficial = ElementTextureDefinition.getTextureFileName(this.presetId, this.elementId, textureKey);
         boolean useDefaultOfficial = officialTexture && defaultOfficial != null && defaultOfficial.equals(fileName);
         if (useDefaultOfficial) {
            this.resetTextureAnimationConfigs(textureKey);
         } else {
            this.applyStaticImageAnimationMeta(textureKey, fileName);
         }
      }
   }

   private void applyStaticImageAnimationMeta(String textureKey, String fileName) {
      JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
      if (current == null) {
         current = new JsonObject();
      }

      String prefix = "anim_" + textureKey + "_";
      current.addProperty(prefix + "enable_texture_animation", false);
      current.addProperty(prefix + "texture_animation_total_frames", 1);
      current.addProperty(prefix + "texture_animation_interval_ms", 100);
      current.addProperty(prefix + "texture_animation_orientation", "vertical");
      current.addProperty(prefix + "texture_animation_loop", false);
      current.addProperty(prefix + "texture_animation_play_style", "sequential");
      this.applyTextureFrameRatio(current, textureKey, fileName);
      ElementConfigManager.setElementConfig(this.presetId, this.elementId, current);
   }

   private boolean applyTextureFrameRatio(JsonObject target, String textureKey, String fileName) {
      if (target != null && textureKey != null && fileName != null && !fileName.isEmpty()) {
         ExternalTextureManager.TextureDimensions dims = ExternalTextureManager.getLogicalTextureDimensions(this.presetId, fileName);
         if (dims != null && dims.width > 0 && dims.height > 0) {
            float base = (float)Math.min(dims.width, dims.height);
            if (base <= 0.0F) {
               return false;
            } else {
               String prefix = "anim_" + textureKey + "_";
               target.addProperty(prefix + "texture_frame_width_ratio", (float)dims.width / base);
               target.addProperty(prefix + "texture_frame_height_ratio", (float)dims.height / base);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void applyGifAnimationMetaToSharedTextures(String targetFileName) {
      JsonObject meta = ExternalTextureManager.getCustomMeta(this.presetId, targetFileName);
      if (meta != null && meta.has("gif") && meta.get("gif").getAsBoolean()) {
         int frames = meta.has("frames") ? meta.get("frames").getAsInt() : 1;
         int interval = meta.has("interval") ? meta.get("interval").getAsInt() : 100;
         String orientation = meta.has("orientation") ? meta.get("orientation").getAsString() : "vertical";
         JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
         if (current == null) {
            current = new JsonObject();
         }

         for (String textureKey : ElementTextureDefinition.getTextures(this.elementId)) {
            String selectedFile = ElementTextureDefinition.getSelectedTextureFileName(this.presetId, this.elementId, textureKey, current);
            if (targetFileName.equals(selectedFile)) {
               String prefix = "anim_" + textureKey + "_";
               current.addProperty(prefix + "enable_texture_animation", true);
               current.addProperty(prefix + "texture_animation_total_frames", frames);
               current.addProperty(prefix + "texture_animation_interval_ms", interval);
               current.addProperty(prefix + "texture_animation_orientation", orientation);
               current.addProperty(prefix + "texture_animation_loop", true);
               this.applyTextureFrameRatio(current, textureKey, targetFileName);
            }
         }

         ElementConfigManager.setElementConfig(this.presetId, this.elementId, current);
      }
   }

   private void runAsyncTask(Runnable action, Runnable onComplete) {
      CompletableFuture.runAsync(() -> {
         if (action != null) {
            try {
               action.run();
            } catch (Exception var2x) {
            }
         }
      }).whenComplete((v, t) -> {
         if (onComplete != null) {
            this.minecraft.execute(onComplete);
         }
      });
   }

   private void resetTextureRelatedConfigs(String textureKey) {
      if (textureKey != null) {
         JsonObject defaults = ElementConfigManager.getResetDefaultConfig(this.presetId, this.elementId);
         if (defaults != null) {
            JsonObject current = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
            JsonObject target = current != null ? current : defaults.deepCopy();
            String prefix = "anim_" + textureKey + "_";
            String officialKey = ElementTextureDefinition.getOfficialTextureKey(textureKey);
            String customKey = ElementTextureDefinition.getCustomTextureKey(textureKey);
            String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
            String vanillaKey = ElementTextureDefinition.getVanillaTextureKey(textureKey);
            String[] keys = new String[]{
               prefix + "enable_texture_animation",
               prefix + "texture_animation_total_frames",
               prefix + "texture_animation_interval_ms",
               prefix + "texture_animation_orientation",
               prefix + "texture_animation_loop",
               prefix + "texture_animation_play_style",
               prefix + "texture_frame_width_ratio",
               prefix + "texture_frame_height_ratio",
               prefix + "texture_scale",
               prefix + "texture_final_opacity",
               prefix + "texture_x_offset",
               prefix + "texture_y_offset",
               officialKey,
               customKey,
               modeKey,
               vanillaKey
            };

            for (String key : keys) {
               if (defaults.has(key)) {
                  target.add(key, defaults.get(key));
               }
            }

            ElementConfigManager.setElementConfig(this.presetId, this.elementId, target);
         }
      }
   }

   public boolean isKillIconElement() {
      return this.elementId != null && this.elementId.startsWith("kill_icon");
   }

   public boolean isSubtitleElement() {
      return this.elementId != null && this.elementId.startsWith("subtitle/");
   }

   public boolean isMouseInSecondaryTabArea(double mouseX, double mouseY) {
      if (!this.isKillIconElement()) {
         return false;
      } else {
         this.updateSecondaryTabLayout(this.minecraft.getWindow().getGuiScaledWidth());
         return mouseX >= (double)this.secondaryAreaX1
            && mouseX <= (double)this.secondaryAreaX2
            && mouseY >= (double)this.secondaryAreaTop
            && mouseY <= (double)this.secondaryAreaBottom;
      }
   }

   public int getSecondaryTabHeight() {
      return 17;
   }

   private int getContentTopOffset() {
      return this.isKillIconElement() ? this.getSecondaryTabHeight() + 1 : 0;
   }

   private void updateSecondaryTabLayout(int screenWidth) {
      if (!this.isKillIconElement()) {
         this.secondaryTabs.clear();
         this.selectedSecondaryTab = null;
      } else {
         this.ensureSecondaryTabs();
         int padding = 8;
         int area1Right = (screenWidth - 2 * padding) / 3 + padding;
         this.secondaryAreaX1 = area1Right + padding;
         this.secondaryAreaX2 = screenWidth - padding;
         int baseTop = 26 + padding;
         this.secondaryAreaTop = baseTop;
         this.secondaryAreaBottom = baseTop + this.getSecondaryTabHeight();
         int currentX = 0;

         for (ElementConfigContent.SecondaryTab tab : this.secondaryTabs) {
            tab.width = this.minecraft.font.width(tab.label) + 10;
            tab.x = currentX;
            tab.height = this.getSecondaryTabHeight();
            currentX += tab.width + 2;
         }

         int totalWidth = Math.max(0, currentX - 2);
         int visibleWidth = this.secondaryAreaX2 - this.secondaryAreaX1;
         this.secondaryMaxScroll = (double)Math.max(0, totalWidth - visibleWidth);
         this.secondaryTargetScrollX = Mth.clamp(this.secondaryTargetScrollX, 0.0, this.secondaryMaxScroll);
         if (this.secondaryScrollX > this.secondaryMaxScroll) {
            this.secondaryScrollX = this.secondaryMaxScroll;
         }
      }
   }

   private void ensureSecondaryTabs() {
      if (this.secondaryTabs.isEmpty()) {
         String generalKey = "battlefield_ops.client.gui.config.tab.general";
         String generalLabel = I18nCompat.exists(generalKey) ? I18n.get(generalKey, new Object[0]) : "General";
         this.secondaryTabs.add(new ElementConfigContent.SecondaryTab("general", generalLabel));
         if (!ElementTextureDefinition.hasTextures(this.elementId)) {
            if (this.selectedSecondaryTab == null && !this.secondaryTabs.isEmpty()) {
               this.selectedSecondaryTab = this.secondaryTabs.get(0);
            }
         } else {
            for (String texture : ElementTextureDefinition.getTextures(this.elementId)) {
               String key = "battlefield_ops.client.gui.config.tab.texture." + texture;
               String label = I18nCompat.exists(key) ? I18n.get(key, new Object[0]) : texture;
               this.secondaryTabs.add(new ElementConfigContent.SecondaryTab(texture, label));
            }

            if (this.selectedSecondaryTab == null && !this.secondaryTabs.isEmpty()) {
               this.selectedSecondaryTab = this.secondaryTabs.get(0);
            }
         }
      }
   }

   private String getElementDisplayName(String id) {
      String nameKey = "battlefield_ops.element.name." + id.replace("/", ".");
      return I18nCompat.exists(nameKey) ? I18n.get(nameKey, new Object[0]) : id;
   }

   private void renderSecondaryTabs(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.isKillIconElement() && !this.secondaryTabs.isEmpty()) {
         float dt = 0.0F;
         long now = System.currentTimeMillis();
         if (this.lastSecondaryRenderTime > 0L) {
            dt = (float)(now - this.lastSecondaryRenderTime) / 1000.0F;
         }

         this.lastSecondaryRenderTime = now;
         if (!this.secondaryDragging) {
            double diff = this.secondaryTargetScrollX - this.secondaryScrollX;
            if (Math.abs(diff) < 0.1) {
               this.secondaryScrollX = this.secondaryTargetScrollX;
            } else {
               this.secondaryScrollX += diff * 15.0 * (double)dt;
            }
         }

         int localMouseX = (int)((double)(mouseX - this.secondaryAreaX1) + this.secondaryScrollX);
         boolean inArea = mouseX >= this.secondaryAreaX1
            && mouseX <= this.secondaryAreaX2
            && mouseY >= this.secondaryAreaTop
            && mouseY <= this.secondaryAreaBottom;
         int borderColor = this.getRegionBorderColor();
         guiGraphics.enableScissor(this.secondaryAreaX1, this.secondaryAreaTop, this.secondaryAreaX2, this.secondaryAreaBottom);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate((float)((double)this.secondaryAreaX1 - this.secondaryScrollX), 0.0F, 0.0F);
         this.renderSecondaryBaseLine(guiGraphics, borderColor);
         JsonObject currentConfig = ElementConfigManager.getElementConfig(this.presetId, this.elementId);

         for (ElementConfigContent.SecondaryTab tab : this.secondaryTabs) {
            boolean isSelected = tab == this.selectedSecondaryTab;
            boolean hovered = inArea && localMouseX >= tab.x && localMouseX <= tab.x + tab.width;
            boolean isModified = false;
            if (!"general".equals(tab.elementId)) {
               String fileName = ElementTextureDefinition.getSelectedTextureFileName(this.presetId, this.elementId, tab.elementId, currentConfig);
               boolean textureModified = fileName != null && ExternalTextureManager.isTextureModified(this.presetId, fileName);
               boolean styleModified = this.isTextureStyleModified(tab.elementId, currentConfig);
               isModified = textureModified || styleModified;
            }

            int baseColor = isSelected ? -1 : (hovered ? -10066330 : -5592406);
            if (isModified) {
               baseColor = isSelected ? GuiConstants.COLOR_GOLD : (hovered ? GuiConstants.COLOR_DARK_GOLD_ORANGE : GuiConstants.COLOR_GOLD_ORANGE);
            }

            int textX = tab.x + (tab.width - this.minecraft.font.width(tab.label)) / 2;
            int textY = this.secondaryAreaTop + (this.getSecondaryTabHeight() - 9) / 2;
            guiGraphics.drawString(this.minecraft.font, tab.label, textX, textY, baseColor, true);
            if (isSelected) {
               int borderY = this.secondaryAreaTop;
               guiGraphics.fill(tab.x, borderY, tab.x + tab.width, borderY + 1, borderColor);
               guiGraphics.fill(tab.x, borderY, tab.x + 1, this.secondaryAreaBottom, borderColor);
               guiGraphics.fill(tab.x + tab.width - 1, borderY, tab.x + tab.width, this.secondaryAreaBottom, borderColor);
            }
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
      }
   }

   private void resetPreviews() {
      HudElementManager.clearAllPreviews();
      this.scrollingPreviewRenderer = new ScrollingIconRenderer();
      this.comboPreviewRenderer = new ComboIconRenderer();
      this.valorantPreviewRenderer = new ValorantIconRenderer();
      this.cardPreviewRenderer = new CardRenderer();
      this.cardBarPreviewRenderer = new CardBarRenderer();
      this.battlefield1PreviewRenderer = new Battlefield1Renderer();
      this.subtitlePreviewRenderer = new SubtitleRenderer();
      this.comboSubtitlePreviewRenderer.resetPreview();
      this.scorePreviewRenderer.resetPreview();
      this.bonusListPreviewRenderer.resetPreview();
      this.hitInfoPreviewRenderer = new HitInfoRenderer();
      this.lastPreviewTriggerTime = 0L;
      this.lastComboPreviewTriggerTime = 0L;
      this.lastValorantPreviewTriggerTime = 0L;
      this.lastCardPreviewTriggerTime = 0L;
      this.lastCardBarPreviewTriggerTime = 0L;
      this.lastBattlefieldPreviewTriggerTime = 0L;
      this.lastSubtitlePreviewTriggerTime = 0L;
      this.lastScorePreviewTriggerTime = 0L;
      this.lastBonusListPreviewTriggerTime = 0L;
      this.lastHitInfoPreviewTriggerTime = 0L;
   }

   private void closeContent() {
      this.resetPreviews();
      if (this.onClose != null) {
         this.onClose.run();
      }
   }

   private boolean handleSecondaryTabClick(double mouseX, double mouseY, int button) {
      if (!this.isKillIconElement()) {
         return false;
      } else if (button != 0 && button != 1) {
         return false;
      } else {
         this.updateSecondaryTabLayout(this.minecraft.getWindow().getGuiScaledWidth());
         if (!(mouseX < (double)this.secondaryAreaX1)
            && !(mouseX > (double)this.secondaryAreaX2)
            && !(mouseY < (double)this.secondaryAreaTop)
            && !(mouseY > (double)this.secondaryAreaBottom)) {
            int localMouseX = (int)(mouseX - (double)this.secondaryAreaX1 + this.secondaryScrollX);

            for (ElementConfigContent.SecondaryTab tab : this.secondaryTabs) {
               if (localMouseX >= tab.x && localMouseX <= tab.x + tab.width) {
                  if (button == 1) {
                     if ("general".equals(tab.elementId)) {
                        return false;
                     }

                     JsonObject currentConfig = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                     String fileName = ElementTextureDefinition.getSelectedTextureFileName(this.presetId, this.elementId, tab.elementId, currentConfig);
                     boolean textureModified = fileName != null && ExternalTextureManager.isTextureModified(this.presetId, fileName);
                     boolean styleModified = this.isTextureStyleModified(tab.elementId, currentConfig);
                     if (!textureModified && !styleModified) {
                        return false;
                     }

                     if (this.textureResetDialog != null) {
                        String textureKey = tab.elementId;
                        this.textureResetDialog
                           .show(
                              I18n.get("battlefield_ops.client.gui.prompt.texture_reset_confirm", new Object[0]), ConfirmDialog.PromptType.WARNING, () -> {
                                 JsonObject config = ElementConfigManager.getElementConfig(this.presetId, this.elementId);
                                 List<String> affectedKeys = this.getTextureKeysBySelectedFile(config, fileName);
                                 if (affectedKeys.isEmpty()) {
                                    affectedKeys = new ArrayList<>();
                                    affectedKeys.add(textureKey);
                                 }

                                 for (String key : affectedKeys) {
                                    this.resetTextureRelatedConfigs(key);
                                 }

                                 this.rebuildUI();
                                 if (textureModified && fileName != null) {
                                    this.runAsyncTask(() -> ExternalTextureManager.resetTextureWithBackupSilent(this.presetId, fileName), null);
                                 }
                              }
                           );
                     }

                     return true;
                  }

                  if (this.selectedSecondaryTab != tab) {
                     this.selectedSecondaryTab = tab;
                     this.refreshVisibleRows();
                     this.resetPreviews();
                  }

                  this.secondaryPressed = true;
                  this.secondaryDragging = false;
                  this.secondaryLastMouseX = mouseX;
                  return true;
               }
            }

            if (button == 0) {
               this.secondaryPressed = true;
               this.secondaryDragging = false;
               this.secondaryLastMouseX = mouseX;
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private boolean isTextureStyleModified(String textureKey, JsonObject currentConfig) {
      if (textureKey == null) {
         return false;
      } else {
         JsonObject defaults = ElementConfigManager.getResetDefaultConfig(this.presetId, this.elementId);
         if (defaults == null) {
            return false;
         } else {
            String officialKey = ElementTextureDefinition.getOfficialTextureKey(textureKey);
            String customKey = ElementTextureDefinition.getCustomTextureKey(textureKey);
            String modeKey = ElementTextureDefinition.getTextureModeKey(textureKey);
            String vanillaKey = ElementTextureDefinition.getVanillaTextureKey(textureKey);
            String prefix = "anim_" + textureKey + "_";

            for (String key : defaults.keySet()) {
               if ((key.startsWith(prefix) || key.equals(customKey) || key.equals(officialKey) || key.equals(modeKey) || key.equals(vanillaKey))
                  && this.isConfigValueModified(defaults, currentConfig, key)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private boolean isConfigValueModified(JsonObject defaults, JsonObject currentConfig, String key) {
      if (key == null) {
         return false;
      } else {
         JsonElement defaultValue = defaults.get(key);
         JsonElement currentValue = currentConfig != null ? currentConfig.get(key) : null;
         if (currentValue == null && defaultValue != null) {
            return false;
         } else if ((defaultValue == null || defaultValue.isJsonNull())
            && currentValue != null
            && currentValue.isJsonPrimitive()
            && currentValue.getAsJsonPrimitive().isString()
            && currentValue.getAsString().isBlank()) {
            return false;
         } else if ((currentValue == null || currentValue.isJsonNull())
            && defaultValue != null
            && defaultValue.isJsonPrimitive()
            && defaultValue.getAsJsonPrimitive().isString()
            && defaultValue.getAsString().isBlank()) {
            return false;
         } else if (defaultValue == null && currentValue == null) {
            return false;
         } else {
            if (defaultValue != null && currentValue != null && defaultValue.isJsonPrimitive() && currentValue.isJsonPrimitive()) {
               JsonPrimitive dv = defaultValue.getAsJsonPrimitive();
               JsonPrimitive cv = currentValue.getAsJsonPrimitive();
               if (dv.isNumber() && cv.isNumber()) {
                  return Math.abs(dv.getAsDouble() - cv.getAsDouble()) > 1.0E-6;
               }
            }

            return defaultValue == null || !defaultValue.equals(currentValue);
         }
      }
   }

   private List<String> getTextureKeysBySelectedFile(JsonObject currentConfig, String fileName) {
      if (fileName == null) {
         return new ArrayList<>();
      } else {
         List<String> keys = new ArrayList<>();

         for (String textureKey : ElementTextureDefinition.getTextures(this.elementId)) {
            String selectedFile = ElementTextureDefinition.getSelectedTextureFileName(this.presetId, this.elementId, textureKey, currentConfig);
            if (fileName.equals(selectedFile)) {
               keys.add(textureKey);
            }
         }

         return keys;
      }
   }

   private void discardElementChangesAndClose() {
      ElementConfigManager.setElementConfig(this.presetId, this.elementId, this.initialConfig);
      ExternalTextureManager.revertPendingTextureReplacementsForElement(this.presetId, this.elementId);
      this.closeContent();
   }

   private boolean handleSecondaryTabRelease() {
      if (!this.isKillIconElement()) {
         return false;
      } else if (!this.secondaryDragging && !this.secondaryPressed) {
         return false;
      } else {
         this.secondaryDragging = false;
         this.secondaryPressed = false;
         return true;
      }
   }

   private boolean handleSecondaryTabDrag(double mouseX, double mouseY, double dragX) {
      if (!this.isKillIconElement()) {
         return false;
      } else {
         this.updateSecondaryTabLayout(this.minecraft.getWindow().getGuiScaledWidth());
         if (this.secondaryDragging) {
            this.secondaryTargetScrollX -= dragX;
            this.secondaryScrollX = this.secondaryTargetScrollX;
            this.clampSecondaryScroll();
            return true;
         } else if (this.secondaryPressed
            && mouseY <= (double)(this.secondaryAreaTop + 20)
            && mouseX >= (double)this.secondaryAreaX1
            && mouseX <= (double)this.secondaryAreaX2) {
            this.secondaryDragging = true;
            this.secondaryTargetScrollX -= dragX;
            this.secondaryScrollX = this.secondaryTargetScrollX;
            this.clampSecondaryScroll();
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean handleSecondaryTabScroll(double mouseX, double mouseY, double delta) {
      if (!this.isKillIconElement()) {
         return false;
      } else {
         this.updateSecondaryTabLayout(this.minecraft.getWindow().getGuiScaledWidth());
         if (mouseX >= (double)this.secondaryAreaX1 && mouseX <= (double)this.secondaryAreaX2 && mouseY <= (double)(this.secondaryAreaTop + 20)) {
            this.secondaryTargetScrollX -= delta * 40.0;
            this.clampSecondaryScroll();
            return true;
         } else {
            return false;
         }
      }
   }

   private void clampSecondaryScroll() {
      this.secondaryTargetScrollX = Mth.clamp(this.secondaryTargetScrollX, 0.0, this.secondaryMaxScroll);
      if (this.secondaryDragging) {
         this.secondaryScrollX = this.secondaryTargetScrollX;
      }
   }

   private int getRegionBorderColor() {
      return -2136298838;
   }

   private void renderSecondaryBaseLine(GuiGraphics guiGraphics, int color) {
      int lineY = this.secondaryAreaBottom - 1;
      int drawEnd = Math.max(this.secondaryAreaX2 - this.secondaryAreaX1 + (int)this.secondaryMaxScroll, 5000);
      if (this.selectedSecondaryTab == null) {
         guiGraphics.fill(0, lineY, drawEnd, lineY + 1, color);
      } else {
         if (this.selectedSecondaryTab.x > 0) {
            guiGraphics.fill(0, lineY, this.selectedSecondaryTab.x, lineY + 1, color);
         }

         int tabRight = this.selectedSecondaryTab.x + this.selectedSecondaryTab.width;
         if (tabRight < drawEnd) {
            guiGraphics.fill(tabRight, lineY, drawEnd, lineY + 1, color);
         }
      }
   }

   private static class SecondaryTab {
      String elementId;
      String label;
      int x;
      int width;
      int height;

      SecondaryTab(String elementId, String label) {
         this.elementId = elementId;
         this.label = label;
      }
   }
}
