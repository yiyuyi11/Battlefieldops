package cn.bfops.killicon.client.gui.tabs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import cn.bfops.killicon.client.bridge.ClientBridge;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.gui.elements.ConfirmDialog;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.elements.entries.BooleanConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.FixedChoiceConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.HexColorConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.IntegerConfigEntry;

public class GlobalConfigTab extends ConfigTabContent {
   private ConfirmDialog aceLagConfirmDialog;
   private final List<FixedChoiceConfigEntry.Choice> vanillaBlockChoices;

   public GlobalConfigTab(Minecraft minecraft) {
      super(minecraft, "battlefield_ops.client.gui.config.tab.global");
      this.aceLagConfirmDialog = new ConfirmDialog(minecraft, null, null);
      this.vanillaBlockChoices = this.buildVanillaBlockChoices();
      this.configRows
         .add(
            new BooleanConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.enable_sound", new Object[0]),
               "enable_sound",
               I18n.get("battlefield_ops.client.gui.config.global.enable_sound.desc", new Object[0]),
               ClientConfigManager.isEnableSound(),
               true,
               ClientConfigManager::setEnableSound
            )
         );
      this.configRows
         .add(
            new IntegerConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.sound_volume", new Object[0]),
               "sound_volume",
               I18n.get("battlefield_ops.client.gui.config.global.sound_volume.desc", new Object[0]),
               ClientConfigManager.getSoundVolume(),
               100,
               ClientConfigManager::setSoundVolume,
               this.getTextInputDialog(),
               ClientConfigManager::isEnableSound,
               value -> {
                  if (value == null || value.isEmpty()) {
                     return false;
                  } else if (!value.matches("^-?\\d+$")) {
                     return false;
                  } else {
                     try {
                        int parsed = Integer.parseInt(value);
                        return parsed >= 0 && parsed <= 200;
                     } catch (NumberFormatException var2x) {
                        return false;
                     }
                  }
               }
            )
         );
      this.configRows
         .add(
            new BooleanConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.enable_icon_antialiasing", new Object[0]),
               "enable_icon_antialiasing",
               I18n.get("battlefield_ops.client.gui.config.global.enable_icon_antialiasing.desc", new Object[0]),
               ClientConfigManager.isEnableIconAntialiasing(),
               true,
               ClientConfigManager::setEnableIconAntialiasing
            )
         );
      this.configRows
         .add(
            new FixedChoiceConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.single_line_subtitle_compression", new Object[0]),
               "single_line_subtitle_compression_mode",
               I18n.get("battlefield_ops.client.gui.config.global.single_line_subtitle_compression.desc", new Object[0]),
               ClientConfigManager.getSingleLineSubtitleCompressionMode(),
               "scroll",
               List.of(
                  new FixedChoiceConfigEntry.Choice(
                     "scroll", I18n.get("battlefield_ops.client.gui.config.global.single_line_subtitle_compression.scroll", new Object[0])
                  ),
                  new FixedChoiceConfigEntry.Choice(
                     "ellipsis", I18n.get("battlefield_ops.client.gui.config.global.single_line_subtitle_compression.ellipsis", new Object[0])
                  )
               ),
               ClientConfigManager::setSingleLineSubtitleCompressionMode,
               this.getChoiceListDialog()
            )
         );
      this.configRows
         .add(
            new HexColorConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.gui_theme_color_primary", new Object[0]),
               "gui_theme_color_primary",
               I18n.get("battlefield_ops.client.gui.config.global.gui_theme_color_primary.desc", new Object[0]),
               ClientConfigManager.getGuiThemeColorPrimary(),
               "#FFB840",
               value -> {
                  ClientConfigManager.setGuiThemeColorPrimary(value);
                  this.showGuiReloadPrompt();
               },
               this.getTextInputDialog(),
               this.getColorPickerDialog()
            )
         );
      this.configRows
         .add(
            new FixedChoiceConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.gui_background_material", new Object[0]),
               "gui_background_material",
               I18n.get("battlefield_ops.client.gui.config.global.gui_background_material.desc", new Object[0]),
               ClientConfigManager.getGuiBackgroundMaterial(),
               "minecraft:cut_copper",
               this.vanillaBlockChoices,
               value -> {
                  ClientConfigManager.setGuiBackgroundMaterial(value);
                  this.showGuiReloadPrompt();
               },
               this.getChoiceListDialog()
            )
         );
      this.configRows
         .add(
            new BooleanConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.disable_tacz_kill_sound", new Object[0]),
               "disable_tacz_kill_sound",
               I18n.get("battlefield_ops.client.gui.config.global.disable_tacz_kill_sound.desc", new Object[0]),
               ClientConfigManager.isDisableTaczKillSound(),
               false,
               ClientConfigManager::setDisableTaczKillSound,
               () -> ClientBridge.loader().isModLoaded("tacz")
            )
         );
      this.configRows
         .add(
            new BooleanConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.enable_bonus_message", new Object[0]),
               "enable_bonus_message",
               I18n.get("battlefield_ops.client.gui.config.global.enable_bonus_message.desc", new Object[0]),
               ClientConfigManager.isShowBonusMessage(),
               false,
               ClientConfigManager::setShowBonusMessage
            )
         );
      this.configRows
         .add(
            new BooleanConfigEntry(
               0,
               0,
               0,
               0,
               1296319556,
               0.3019608F,
               I18n.get("battlefield_ops.client.gui.config.global.enable_ace_lag", new Object[0]),
               "enable_ace_lag",
               I18n.get("battlefield_ops.client.gui.config.global.enable_ace_lag.desc", new Object[0]),
               ClientConfigManager.isEnableAceLag(),
               false,
               ClientConfigManager::setEnableAceLag
            )
         );
      IntegerConfigEntry[] aceLagEntryRef = new IntegerConfigEntry[1];
      IntegerConfigEntry aceLagEntry = new IntegerConfigEntry(
         0,
         0,
         0,
         0,
         1296319556,
         0.3019608F,
         I18n.get("battlefield_ops.client.gui.config.global.ace_lag_intensity", new Object[0]),
         "ace_lag_intensity",
         I18n.get("battlefield_ops.client.gui.config.global.ace_lag_intensity.desc", new Object[0]),
         ClientConfigManager.getAceLagIntensity(),
         5,
         value -> {
            if (value > 50) {
               this.aceLagConfirmDialog
                  .show(
                     I18n.get("battlefield_ops.client.gui.prompt.ace_lag_confirm", new Object[0]),
                     ConfirmDialog.PromptType.WARNING,
                     () -> ClientConfigManager.setAceLagIntensity(value),
                     () -> {
                        if (aceLagEntryRef[0] != null) {
                           aceLagEntryRef[0].setValue(ClientConfigManager.getAceLagIntensity());
                        }
                     }
                  );
            } else {
               ClientConfigManager.setAceLagIntensity(value);
            }
         },
         this.getTextInputDialog(),
         ClientConfigManager::isEnableAceLag,
         value -> {
            if (value == null || value.isEmpty()) {
               return false;
            } else if (!value.matches("^-?\\d+$")) {
               return false;
            } else {
               try {
                  int parsed = Integer.parseInt(value);
                  return parsed >= 1 && parsed <= 100;
               } catch (NumberFormatException var2x) {
                  return false;
               }
            }
         }
      );
      aceLagEntryRef[0] = aceLagEntry;
      this.configRows.add(aceLagEntry);
      this.sortConfigRows();
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int headerHeight) {
      boolean dialogVisible = this.aceLagConfirmDialog != null && this.aceLagConfirmDialog.isVisible();
      int effectiveMouseX = dialogVisible ? -1 : mouseX;
      int effectiveMouseY = dialogVisible ? -1 : mouseY;
      super.render(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick, screenWidth, screenHeight, headerHeight);
      if (dialogVisible) {
         this.aceLagConfirmDialog.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return this.aceLagConfirmDialog != null && this.aceLagConfirmDialog.isVisible()
         ? this.aceLagConfirmDialog.mouseClicked(mouseX, mouseY, button)
         : super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      return this.aceLagConfirmDialog != null && this.aceLagConfirmDialog.isVisible()
         ? this.aceLagConfirmDialog.mouseScrolled(mouseX, mouseY, delta)
         : super.mouseScrolled(mouseX, mouseY, delta);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.aceLagConfirmDialog != null && this.aceLagConfirmDialog.isVisible()
         ? this.aceLagConfirmDialog.keyPressed(keyCode, scanCode, modifiers)
         : super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char codePoint, int modifiers) {
      return this.aceLagConfirmDialog != null && this.aceLagConfirmDialog.isVisible()
         ? this.aceLagConfirmDialog.charTyped(codePoint, modifiers)
         : super.charTyped(codePoint, modifiers);
   }

   private void showGuiReloadPrompt() {
      this.promptDialog.show(I18n.get("battlefield_ops.client.gui.prompt.gui_partial_reload", new Object[0]), PromptDialog.PromptType.INFO, null);
      this.promptDialog.setDismissible(false);
   }

   private List<FixedChoiceConfigEntry.Choice> buildVanillaBlockChoices() {
      List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
      List<Block> blocks = new ArrayList<>();

      for (Block block : BuiltInRegistries.BLOCK) {
         ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
         if (key != null && "minecraft".equals(key.getNamespace()) && !"minecraft:air".equals(key.toString())) {
            blocks.add(block);
         }
      }

      blocks.sort(Comparator.comparing(blockx -> blockx.getName().getString()));

      for (Block blockx : blocks) {
         ResourceLocation key = BuiltInRegistries.BLOCK.getKey(blockx);
         if (key != null) {
            BlockState state = blockx.defaultBlockState();
            if (state.canOcclude() && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
               ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "textures/block/" + key.getPath() + ".png");
               if (!this.minecraft.getResourceManager().getResource(textureLocation).isEmpty()) {
                  String label = blockx.getName().getString();
                  choices.add(new FixedChoiceConfigEntry.Choice(key.toString(), label + " (" + key + ")"));
               }
            }
         }
      }

      return choices;
   }
}
