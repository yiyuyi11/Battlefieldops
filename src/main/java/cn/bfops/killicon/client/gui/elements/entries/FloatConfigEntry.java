package cn.bfops.killicon.client.gui.elements.entries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.TextInputDialog;

public class FloatConfigEntry extends GDRowRenderer {
   private float value;
   private final float defaultValue;
   private final Consumer<Float> onValueChange;
   private final String key;
   private final TextInputDialog textInputDialog;
   private final String configName;

   public FloatConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      float initialValue,
      float defaultValue,
      Consumer<Float> onValueChange,
      TextInputDialog textInputDialog
   ) {
      this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, () -> true);
   }

   public FloatConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      float initialValue,
      float defaultValue,
      Consumer<Float> onValueChange,
      TextInputDialog textInputDialog,
      Supplier<Boolean> activeCondition
   ) {
      super(x1, y1, x2, y2, bgColor, bgAlpha, false);
      this.key = configId;
      this.setActiveCondition(activeCondition);
      this.setSeparateFirstColumn(true);
      this.setHoverInfo(configName, "   " + description);
      this.value = initialValue;
      this.defaultValue = defaultValue;
      this.onValueChange = onValueChange;
      this.textInputDialog = textInputDialog;
      this.configName = configName;
      this.addNameColumn(configName, configId, -1, -5592406, true, false);
      this.addColoredColumn(this.parseColoredText(this.value), 60, false, false, btn -> {
         if (this.textInputDialog != null) {
            String initialText = String.format(Locale.ROOT, "%.2f", this.value);
            this.textInputDialog.show(initialText, this.configName, newValue -> {
               try {
                  float f = Float.parseFloat(newValue);
                  this.value = f;
                  this.updateState();
                  if (this.onValueChange != null) {
                     this.onValueChange.accept(this.value);
                  }
               } catch (NumberFormatException var3x) {
               }
            }, this::isValidFloat);
         }
      });
      this.addColumn("↺", 17, this.getResetButtonColor(), true, true, btn -> {
         if (!(Math.abs(this.value - this.defaultValue) < 1.0E-4F)) {
            this.value = this.defaultValue;
            this.updateState();
            if (this.onValueChange != null) {
               this.onValueChange.accept(this.value);
            }
         }
      });
   }

   public String getKey() {
      return this.key;
   }

   private boolean isValidFloat(String text) {
      if (text == null || text.isEmpty()) {
         return false;
      } else {
         return text.equals("-") ? false : text.matches("^-?\\d+(\\.\\d{0,2})?$") || text.matches("^-?\\.\\d{1,2}$");
      }
   }

   private void updateState() {
      GDRowRenderer.Column controlCol = this.getColumn(1);
      if (controlCol != null) {
         controlCol.coloredTexts = this.parseColoredText(this.value);
         controlCol.textRenderer = null;
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

   private int getResetButtonColor() {
      return Math.abs(this.value - this.defaultValue) < 1.0E-4F ? -5592406 : GuiConstants.COLOR_GOLD;
   }

   private List<GDTextRenderer.ColoredText> parseColoredText(float val) {
      List<GDTextRenderer.ColoredText> list = new ArrayList<>();
      String text = String.format(Locale.ROOT, "%.2f", val);
      int dotIndex = text.indexOf(46);
      if (dotIndex != -1) {
         String intPart = text.substring(0, dotIndex);
         list.add(new GDTextRenderer.ColoredText(" " + intPart, -1));
         list.add(new GDTextRenderer.ColoredText(".", -1));
         String decPart = text.substring(dotIndex + 1);
         list.add(new GDTextRenderer.ColoredText(decPart, -5592406));
      } else {
         list.add(new GDTextRenderer.ColoredText(" " + text, -1));
      }

      return list;
   }
}
