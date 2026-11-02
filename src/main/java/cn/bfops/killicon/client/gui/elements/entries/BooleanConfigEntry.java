package cn.bfops.killicon.client.gui.elements.entries;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.GDRowRenderer;

public class BooleanConfigEntry extends GDRowRenderer {
   private boolean value;
   private final boolean defaultValue;
   private final String key;
   private final Consumer<Boolean> onValueChange;

   public BooleanConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      boolean initialValue,
      boolean defaultValue,
      Consumer<Boolean> onValueChange
   ) {
      this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, () -> true);
   }

   public BooleanConfigEntry(
      int x1,
      int y1,
      int x2,
      int y2,
      int bgColor,
      float bgAlpha,
      String configName,
      String configId,
      String description,
      boolean initialValue,
      boolean defaultValue,
      Consumer<Boolean> onValueChange,
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
      this.addNameColumn(configName, configId, -1, -5592406, true, false);
      this.addColumn(this.getValueText(), 60, this.getValueColor(), false, true, btn -> {
         this.value = !this.value;
         this.updateState();
         if (this.onValueChange != null) {
            this.onValueChange.accept(this.value);
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

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void updateState() {
      GDRowRenderer.Column controlCol = this.getColumn(1);
      if (controlCol != null) {
         controlCol.text = this.getValueText();
         controlCol.color = this.getValueColor();
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

   private String getValueText() {
      return this.value ? "ON" : "OFF";
   }

   private int getValueColor() {
      return this.value ? GuiConstants.COLOR_GOLD : -5592406;
   }

   private int getResetButtonColor() {
      return this.value == this.defaultValue ? -5592406 : GuiConstants.COLOR_GOLD;
   }
}
