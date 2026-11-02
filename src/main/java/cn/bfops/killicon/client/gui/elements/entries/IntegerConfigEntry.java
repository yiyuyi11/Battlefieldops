package cn.bfops.killicon.client.gui.elements.entries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;
import cn.bfops.killicon.client.gui.elements.GDTextRenderer;
import cn.bfops.killicon.client.gui.elements.TextInputDialog;

public class IntegerConfigEntry extends GDRowRenderer {
   private int value;
   private final int defaultValue;
   private final Consumer<Integer> onValueChange;
   private final TextInputDialog textInputDialog;
   private final String configName;
   private final String key;
   private final Predicate<String> validator;

   public IntegerConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      int initialValue,
      int defaultValue,
      Consumer<Integer> onValueChange,
      TextInputDialog textInputDialog
   ) {
      this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, () -> true);
   }

   public IntegerConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      int initialValue,
      int defaultValue,
      Consumer<Integer> onValueChange,
      TextInputDialog textInputDialog,
      Supplier<Boolean> activeCondition
   ) {
      this(
         x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, activeCondition, null
      );
   }

   public IntegerConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      int initialValue,
      int defaultValue,
      Consumer<Integer> onValueChange,
      TextInputDialog textInputDialog,
      Supplier<Boolean> activeCondition,
      Predicate<String> validator
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
      this.validator = validator == null ? this::isValidInteger : validator;
      this.addNameColumn(configName, configId, -1, -5592406, true, false);
      this.addColoredColumn(this.parseColoredText(String.valueOf(this.value)), 60, false, false, btn -> {
         if (this.textInputDialog != null) {
            this.textInputDialog.show(String.valueOf(this.value), this.configName, newValue -> {
               try {
                  if (this.validator != null && !this.validator.test(newValue)) {
                     return;
                  }

                  int parsed = Integer.parseInt(newValue);
                  this.value = parsed;
                  this.updateState();
                  if (this.onValueChange != null) {
                     this.onValueChange.accept(this.value);
                  }
               } catch (NumberFormatException var3x) {
               }
            }, this.validator);
         }
      });
      this.addColumn("↺", 17, this.getResetButtonColor(), true, true, btn -> {
         if (this.value != this.defaultValue) {
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

   public void setValue(int value) {
      this.value = value;
      this.updateState();
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private boolean isValidInteger(String text) {
      if (text == null || text.isEmpty()) {
         return false;
      } else {
         return text.equals("-") ? false : text.matches("^-?\\d+$");
      }
   }

   private void updateState() {
      GDRowRenderer.Column controlCol = this.getColumn(1);
      if (controlCol != null) {
         controlCol.coloredTexts = this.parseColoredText(String.valueOf(this.value));
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
      return this.value == this.defaultValue ? -5592406 : GuiConstants.COLOR_GOLD;
   }

   private List<GDTextRenderer.ColoredText> parseColoredText(String text) {
      List<GDTextRenderer.ColoredText> list = new ArrayList<>();
      String processingText = " " + (text == null ? "" : text);
      Pattern pattern = Pattern.compile("<.*?>");
      Matcher matcher = pattern.matcher(processingText);

      int lastEnd;
      for (lastEnd = 0; matcher.find(); lastEnd = matcher.end()) {
         if (matcher.start() > lastEnd) {
            list.add(new GDTextRenderer.ColoredText(processingText.substring(lastEnd, matcher.start()), -1));
         }

         list.add(new GDTextRenderer.ColoredText(matcher.group(), GuiConstants.COLOR_GOLD));
      }

      if (lastEnd < processingText.length()) {
         list.add(new GDTextRenderer.ColoredText(processingText.substring(lastEnd), -1));
      }

      return list;
   }
}
