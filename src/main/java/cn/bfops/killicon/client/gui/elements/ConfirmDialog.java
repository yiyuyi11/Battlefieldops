package cn.bfops.killicon.client.gui.elements;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import cn.bfops.killicon.client.gui.GuiConstants;

public class ConfirmDialog {
   private final Minecraft minecraft;
   private Runnable onConfirm;
   private Runnable onCancel;
   private Runnable currentConfirmAction;
   private Runnable currentCancelAction;
   private boolean visible = false;
   private String title = "";
   private String message = "";
   private ConfirmDialog.PromptType type = ConfirmDialog.PromptType.INFO;
   private GDTextRenderer titleRenderer;
   private GDTextRenderer messageRenderer;
   private GDButton confirmButton;
   private GDButton cancelButton;
   private static final int INPUT_WIDTH = 140;
   private static final int INPUT_HEIGHT = 17;
   private static final int BUTTON_HEIGHT = 17;

   public ConfirmDialog(Minecraft minecraft, Runnable onConfirm, Runnable onCancel) {
      this.minecraft = minecraft;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
   }

   public void show(String message, ConfirmDialog.PromptType type, Runnable onConfirmAction) {
      this.show(message, type, onConfirmAction, null);
   }

   public void show(String message, ConfirmDialog.PromptType type, Runnable onConfirmAction, Runnable onCancelAction) {
      this.message = message;
      this.type = type == null ? ConfirmDialog.PromptType.INFO : type;
      this.title = this.type.getTitle();
      this.visible = true;
      this.currentConfirmAction = onConfirmAction;
      this.currentCancelAction = onCancelAction;
      if (this.titleRenderer == null) {
         this.titleRenderer = new GDTextRenderer(this.title, 0, 0, 0, 0, 1.0F, this.type.getColor(), false);
         this.titleRenderer.setCentered(true);
      } else {
         this.titleRenderer.setText(this.title);
         this.titleRenderer.setColor(this.type.getColor());
         this.titleRenderer.setCentered(true);
      }

      if (this.messageRenderer == null) {
         this.messageRenderer = new GDTextRenderer(message, 0, 0, 0, 0, 1.0F, -1, true);
         this.messageRenderer.setCentered(false);
      } else {
         this.messageRenderer.setText(message);
         this.messageRenderer.setColor(-1);
         this.messageRenderer.setCentered(false);
      }

      int w1 = 69;
      int w2 = 139 - w1;
      this.confirmButton = new GDButton(
         0, 0, w2, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.confirm", new Object[0])), btn -> this.confirm()
      );
      this.cancelButton = new GDButton(
         0, 0, w1, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.cancel", new Object[0])), btn -> this.cancel()
      );
   }

   private void confirm() {
      if (this.currentConfirmAction != null) {
         this.currentConfirmAction.run();
      } else if (this.onConfirm != null) {
         this.onConfirm.run();
      }

      this.visible = false;
   }

   private void cancel() {
      if (this.currentCancelAction != null) {
         this.currentCancelAction.run();
      } else if (this.onCancel != null) {
         this.onCancel.run();
      }

      this.visible = false;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (this.confirmButton != null && this.confirmButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         return this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button) ? true : true;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      return this.visible;
   }

   public boolean charTyped(char codePoint, int modifiers) {
      return this.visible;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 257 || keyCode == 335) {
         this.confirm();
         return true;
      } else if (keyCode == 256) {
         this.cancel();
         return true;
      } else {
         return true;
      }
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.visible) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int dimColor = -2008791996;
         guiGraphics.fill(0, 0, screenWidth, screenHeight, dimColor);
         int fontHeight = 9;
         int maxWidth = 140;
         List<FormattedCharSequence> lines = this.minecraft.font.split(Component.literal(this.message), maxWidth);
         int messageHeight = Math.max(17, lines.size() * fontHeight);
         int inputX = (screenWidth - 140) / 2;
         int inputY = (screenHeight - messageHeight) / 2;
         int titleY = inputY - fontHeight - 8 + 6;
         int buttonsY = inputY + messageHeight + 1;
         int containerTop = titleY - 8;
         int containerLeft = inputX - 8;
         int containerRight = inputX + 140 + 8;
         int containerBottom = buttonsY + 17 + 8;
         guiGraphics.fill(containerLeft, containerTop, containerRight, containerBottom, 1296319556);
         if (this.titleRenderer != null) {
            this.titleRenderer.setX1(inputX);
            this.titleRenderer.setY1(titleY);
            this.titleRenderer.setX2(inputX + 140);
            this.titleRenderer.setY2(titleY + fontHeight);
            this.titleRenderer.render(guiGraphics, partialTick);
         }

         if (this.messageRenderer != null) {
            this.messageRenderer.setX1(inputX);
            this.messageRenderer.setY1(inputY);
            this.messageRenderer.setX2(inputX + 140);
            this.messageRenderer.setY2(inputY + messageHeight);
            this.messageRenderer.render(guiGraphics, partialTick);
         }

         int btnY = inputY + messageHeight + 1;
         if (this.cancelButton != null) {
            this.cancelButton.setX(inputX);
            this.cancelButton.setY(btnY);
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.confirmButton != null) {
            int w1 = 69;
            this.confirmButton.setX(inputX + w1 + 1);
            this.confirmButton.setY(btnY);
            this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         guiGraphics.pose().popPose();
      }
   }

   public static enum PromptType {
      WARNING("battlefield_ops.client.gui.prompt.warning", GuiConstants.COLOR_GOLD_ORANGE),
      INFO("battlefield_ops.client.gui.prompt.info", -7876885),
      ERROR("battlefield_ops.client.gui.prompt.error", -65536),
      SUCCESS("battlefield_ops.client.gui.prompt.success", -16711936);

      private final String titleKey;
      private final int color;

      private PromptType(String titleKey, int color) {
         this.titleKey = titleKey;
         this.color = color;
      }

      public String getTitle() {
         return I18n.get(this.titleKey, new Object[0]);
      }

      public int getColor() {
         return this.color;
      }
   }
}
