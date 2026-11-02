package cn.bfops.killicon.client.gui.elements.entries;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.ChoiceListDialog;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;

public class FixedChoiceConfigEntry extends GDRowRenderer {
   private final List<FixedChoiceConfigEntry.Choice> choices;
   private int index;
   private final String defaultValue;
   private final Consumer<String> onValueChange;
   private final String key;
   private final ChoiceListDialog choiceListDialog;
   private final String configName;
   private final Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate;
   private final Consumer<FixedChoiceConfigEntry.Choice> onExtraAction;
   private final Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate;
   private final Consumer<FixedChoiceConfigEntry.Choice> onDirectAction;
   private final String extraActionText;
   private final int extraActionColor;
   private final int extraActionWidth;

   public FixedChoiceConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      String initialValue,
      String defaultValue,
      List<FixedChoiceConfigEntry.Choice> choices,
      Consumer<String> onValueChange,
      ChoiceListDialog choiceListDialog
   ) {
      this(
         x1,
         y1,
         x2,
         y2,
         bgColor,
         bgAlpha,
         configName,
         configId,
         description,
         initialValue,
         defaultValue,
         choices,
         onValueChange,
         choiceListDialog,
         () -> true,
         null,
         null,
         "",
         -65536,
         17,
         null,
         null
      );
   }

   public FixedChoiceConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      String initialValue,
      String defaultValue,
      List<FixedChoiceConfigEntry.Choice> choices,
      Consumer<String> onValueChange,
      ChoiceListDialog choiceListDialog,
      Supplier<Boolean> activeCondition
   ) {
      this(
         x1,
         y1,
         x2,
         y2,
         bgColor,
         bgAlpha,
         configName,
         configId,
         description,
         initialValue,
         defaultValue,
         choices,
         onValueChange,
         choiceListDialog,
         activeCondition,
         null,
         null,
         "",
         -65536,
         17,
         null,
         null
      );
   }

   public FixedChoiceConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      String initialValue,
      String defaultValue,
      List<FixedChoiceConfigEntry.Choice> choices,
      Consumer<String> onValueChange,
      ChoiceListDialog choiceListDialog,
      Supplier<Boolean> activeCondition,
      Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
      String extraActionText,
      int extraActionColor,
      int extraActionWidth
   ) {
      this(
         x1,
         y1,
         x2,
         y2,
         bgColor,
         bgAlpha,
         configName,
         configId,
         description,
         initialValue,
         defaultValue,
         choices,
         onValueChange,
         choiceListDialog,
         activeCondition,
         extraActionVisiblePredicate,
         onExtraAction,
         extraActionText,
         extraActionColor,
         extraActionWidth,
         null,
         null
      );
   }

   public FixedChoiceConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      String initialValue,
      String defaultValue,
      List<FixedChoiceConfigEntry.Choice> choices,
      Consumer<String> onValueChange,
      ChoiceListDialog choiceListDialog,
      Supplier<Boolean> activeCondition,
      Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
      String extraActionText,
      int extraActionColor,
      int extraActionWidth,
      Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onDirectAction
   ) {
      super(x1, y1, x2, y2, bgColor, bgAlpha, false);
      this.key = configId;
      this.setActiveCondition(activeCondition);
      this.setSeparateFirstColumn(true);
      this.setHoverInfo(configName, "   " + description);
      this.choices = choices;
      this.defaultValue = defaultValue;
      this.onValueChange = onValueChange;
      this.choiceListDialog = choiceListDialog;
      this.configName = configName;
      this.extraActionVisiblePredicate = extraActionVisiblePredicate;
      this.onExtraAction = onExtraAction;
      this.directActionPredicate = directActionPredicate;
      this.onDirectAction = onDirectAction;
      this.extraActionText = extraActionText == null ? "" : extraActionText;
      this.extraActionColor = extraActionColor;
      this.extraActionWidth = extraActionWidth <= 0 ? 17 : extraActionWidth;
      this.index = this.resolveIndex(initialValue, defaultValue);
      this.addNameColumn(configName, configId, -1, -5592406, true, false);
      this.addColumn(this.getValueText(), 60, GuiConstants.COLOR_GOLD, false, true, btn -> this.openDialog());
      this.addColumn("↺", 17, this.getResetButtonColor(), true, true, btn -> {
         if (this.hasDefaultChoice()) {
            if (!this.getCurrentValue().equals(this.defaultValue)) {
               this.index = this.resolveIndex(this.defaultValue, this.getCurrentValue());
               this.updateState();
               if (this.onValueChange != null) {
                  this.onValueChange.accept(this.getCurrentValue());
               }
            }
         }
      });
   }

   public String getKey() {
      return this.key;
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private int resolveIndex(String value, String fallback) {
      if (this.choices != null) {
         for (int i = 0; i < this.choices.size(); i++) {
            if (this.choices.get(i).value().equals(value)) {
               return i;
            }
         }

         for (int ix = 0; ix < this.choices.size(); ix++) {
            if (this.choices.get(ix).value().equals(fallback)) {
               return ix;
            }
         }
      }

      return 0;
   }

   private String getCurrentValue() {
      return this.choices.get(this.index).value();
   }

   private String getValueText() {
      return this.choices.get(this.index).label();
   }

   private void updateState() {
      GDRowRenderer.Column controlCol = this.getColumn(1);
      if (controlCol != null) {
         controlCol.text = this.getValueText();
         controlCol.color = GuiConstants.COLOR_GOLD;
         if (controlCol.textRenderer != null) {
            controlCol.textRenderer.setText(controlCol.text);
            controlCol.textRenderer.setColor(controlCol.color);
         }
      }

      GDRowRenderer.Column resetCol = this.getColumn(2);
      if (resetCol != null) {
         resetCol.color = this.getResetButtonColor();
         resetCol.text = "↺";
         if (resetCol.textRenderer != null) {
            resetCol.textRenderer.setColor(resetCol.color);
            resetCol.textRenderer.setText(resetCol.text);
         }
      }
   }

   private void openDialog() {
      if (this.choiceListDialog != null) {
         this.choiceListDialog
            .show(
               this.getCurrentValue(),
               this.configName,
               this.choices,
               newValue -> {
                  if (newValue == null || !newValue.equals(this.getCurrentValue())) {
                     this.index = this.resolveIndex(newValue, this.defaultValue);
                     this.updateState();
                     if (this.onValueChange != null) {
                        this.onValueChange.accept(this.getCurrentValue());
                     }
                  }
               },
               null,
               this.extraActionVisiblePredicate,
               this.onExtraAction,
               this.extraActionText,
               this.extraActionColor,
               this.extraActionWidth,
               this.directActionPredicate,
               this.onDirectAction
            );
      }
   }

   private int getResetButtonColor() {
      if (!this.hasDefaultChoice()) {
         return -5592406;
      } else {
         return this.getCurrentValue().equals(this.defaultValue) ? -5592406 : GuiConstants.COLOR_GOLD;
      }
   }

   private boolean hasDefaultChoice() {
      if (this.choices != null && !this.choices.isEmpty()) {
         for (FixedChoiceConfigEntry.Choice choice : this.choices) {
            if (choice.value().equals(this.defaultValue)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static class Choice {
      private final String value;
      private final String label;

      public Choice(String value, String label) {
         this.value = value;
         this.label = label;
      }

      public String value() {
         return this.value;
      }

      public String label() {
         return this.label;
      }
   }
}
