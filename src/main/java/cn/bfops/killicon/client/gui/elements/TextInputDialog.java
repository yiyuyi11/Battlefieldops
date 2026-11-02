package cn.bfops.killicon.client.gui.elements;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import cn.bfops.killicon.client.gui.GuiConstants;

public class TextInputDialog {
   private final Minecraft minecraft;
   private Consumer<String> onConfirm;
   private Runnable onCancel;
   private Consumer<String> currentConfirmAction;
   private Predicate<String> currentValidator;
   private int showToken = 0;
   private boolean visible = false;
   private String title = "";
   private String text = "";
   private int cursorPosition = 0;
   private int selectionAnchor = -1;
   private final ArrayDeque<TextInputDialog.EditState> undoStack = new ArrayDeque<>();
   private int displayOffset = 0;
   private GDTextRenderer titleRenderer;
   private float hoverProgress = 0.0F;
   private long lastFrameTime;
   private static final int INPUT_WIDTH = 140;
   private static final int INPUT_HEIGHT = 17;
   private static final int BUTTON_HEIGHT = 17;
   private static final int BUTTON_WIDTH = 69;
   private GDButton confirmButton;
   private GDButton cancelButton;

   public TextInputDialog(Minecraft minecraft, Consumer<String> onConfirm, Runnable onCancel) {
      this.minecraft = minecraft;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
   }

   public void show(String initialText, String title, Consumer<String> onConfirmAction) {
      this.show(initialText, title, onConfirmAction, null);
   }

   public void show(String initialText, String title, Consumer<String> onConfirmAction, Predicate<String> validator) {
      this.text = initialText;
      this.title = title;
      this.visible = true;
      this.showToken++;
      this.cursorPosition = this.text.length();
      this.selectionAnchor = -1;
      this.undoStack.clear();
      this.displayOffset = 0;
      this.hoverProgress = 0.0F;
      this.lastFrameTime = System.currentTimeMillis();
      this.currentConfirmAction = onConfirmAction;
      this.currentValidator = validator;
      if (this.titleRenderer == null) {
         this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0F, GuiConstants.COLOR_GOLD, false);
      } else {
         this.titleRenderer.setText(title);
         this.titleRenderer.setColor(GuiConstants.COLOR_GOLD);
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

   private void confirm() {
      boolean isValid = this.currentValidator == null || this.currentValidator.test(this.text);
      if (isValid) {
         int token = this.showToken;
         if (this.currentConfirmAction != null) {
            this.currentConfirmAction.accept(this.text);
         } else if (this.onConfirm != null) {
            this.onConfirm.accept(this.text);
         }

         if (this.showToken == token) {
            this.visible = false;
         }
      }
   }

   private void cancel() {
      int token = this.showToken;
      if (this.onCancel != null) {
         this.onCancel.run();
      }

      if (this.showToken == token) {
         this.visible = false;
      }
   }

   public boolean isVisible() {
      return this.visible;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.visible) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int dimColor = -2008791996;
         guiGraphics.fill(0, 0, screenWidth, screenHeight, dimColor);
         int inputX = (screenWidth - 140) / 2;
         int inputY = (screenHeight - 17) / 2;
         int fontHeight = 9;
         int titleY = inputY - fontHeight - 8 + 6;
         int buttonsY = inputY + 17 + 1;
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

         int inputBgColor = GuiConstants.COLOR_BLACK & 16777215 | 1912602624;
         guiGraphics.fill(inputX, inputY, inputX + 140, inputY + 17, inputBgColor);
         boolean isHovered = mouseX >= inputX && mouseX <= inputX + 140 && mouseY >= inputY && mouseY <= inputY + 17;
         long currentTime = System.currentTimeMillis();
         float dt = (float)(currentTime - this.lastFrameTime) / 1000.0F;
         this.lastFrameTime = currentTime;
         if (isHovered) {
            this.hoverProgress = Math.min(1.0F, this.hoverProgress + dt * 2.0F);
         } else {
            this.hoverProgress = Math.max(0.0F, this.hoverProgress - dt * 2.0F);
         }

         this.renderHoverTrail(guiGraphics, inputX, inputY, 140, 17);
         boolean isValid = this.currentValidator == null || this.currentValidator.test(this.text);
         this.renderInputText(guiGraphics, inputX, inputY, 140, 17, isValid);
         int btnY = inputY + 17 + 1;
         if (this.cancelButton != null) {
            this.cancelButton.setX(inputX);
            this.cancelButton.setY(btnY);
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.confirmButton != null) {
            int w1 = 69;
            this.confirmButton.setX(inputX + w1 + 1);
            this.confirmButton.setY(btnY);
            this.confirmButton.active = isValid;
            if (!isValid) {
               this.confirmButton.setTextColor(-5592406);
            } else {
               this.confirmButton.setTextColor(-1);
            }

            this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         guiGraphics.pose().popPose();
      }
   }

   private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h) {
      if (!(this.hoverProgress <= 0.001F)) {
         int color = GuiConstants.COLOR_GOLD;
         float totalLength = (float)(w + h + w + h);
         float currentLength = totalLength * this.easeOut(this.hoverProgress);
         float drawn = 0.0F;
         if (currentLength > 0.0F) {
            float segLen = Math.min((float)w, currentLength);
            guiGraphics.fill(x, y + h - 1, x + (int)segLen, y + h, color);
            drawn += (float)w;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)h, rem);
            guiGraphics.fill(x + w - 1, y + h - (int)segLen, x + w, y + h, color);
            drawn += (float)h;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)w, rem);
            guiGraphics.fill(x + w - (int)segLen, y, x + w, y + 1, color);
            drawn += (float)w;
         }

         if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min((float)h, rem);
            guiGraphics.fill(x, y, x + 1, y + (int)segLen, color);
         }
      }
   }

   private float easeOut(float t) {
      return 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
   }

   private void renderInputText(GuiGraphics guiGraphics, int x, int y, int w, int h, boolean isValid) {
      guiGraphics.enableScissor(x, y, x + w, y + h);
      int textWidth = this.minecraft.font.width(this.text);
      int boxInnerWidth = w - 4;
      String textBeforeCursor = this.text.substring(0, this.cursorPosition);
      int cursorXRel = this.minecraft.font.width(textBeforeCursor);
      if (cursorXRel < this.displayOffset) {
         this.displayOffset = cursorXRel;
      } else if (cursorXRel > this.displayOffset + boxInnerWidth) {
         this.displayOffset = cursorXRel - boxInnerWidth;
      }

      if (textWidth < boxInnerWidth) {
         this.displayOffset = 0;
      }

      int drawX = x + 2 - this.displayOffset;
      int drawY = y + (h - 9) / 2;
      if (this.hasSelection()) {
         int selStartRel = this.minecraft.font.width(this.text.substring(0, this.getSelectionStart()));
         int selEndRel = this.minecraft.font.width(this.text.substring(0, this.getSelectionEnd()));
         int sx = drawX + selStartRel;
         int ex = drawX + selEndRel;
         guiGraphics.fill(sx, drawY - 1, ex, drawY + 9 + 1, 1728042752);
      }

      int textColor = isValid ? -1 : -65536;
      guiGraphics.drawString(this.minecraft.font, this.text, drawX, drawY, textColor, true);
      if (System.currentTimeMillis() / 500L % 2L == 0L) {
         int cx = drawX + cursorXRel;
         if (cx >= x && cx <= x + w) {
            guiGraphics.fill(cx, drawY - 1, cx + 1, drawY + 9 + 1, -1);
         }
      }

      guiGraphics.disableScissor();
   }

   public boolean charTyped(char codePoint, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (net.minecraft.util.StringUtil.isAllowedChatCharacter(codePoint)) {
         this.replaceSelection(String.valueOf(codePoint));
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else {
         boolean controlDown = Screen.hasControlDown() || (modifiers & 2) != 0;
         boolean shiftDown = Screen.hasShiftDown() || (modifiers & 1) != 0;
         if (controlDown && keyCode == 65) {
            this.selectionAnchor = 0;
            this.cursorPosition = this.text.length();
            return true;
         } else if (controlDown && keyCode == 67) {
            if (this.hasSelection() && this.minecraft != null && this.minecraft.keyboardHandler != null) {
               this.minecraft.keyboardHandler.setClipboard(this.text.substring(this.getSelectionStart(), this.getSelectionEnd()));
            }

            return true;
         } else if (controlDown && keyCode == 90) {
            this.undoEdit();
            return true;
         } else if ((keyCode != 86 || !controlDown) && (keyCode != 260 || !shiftDown)) {
            if (keyCode == 259) {
               if (this.hasSelection()) {
                  this.deleteSelection();
                  return true;
               } else {
                  if (this.cursorPosition > 0) {
                     this.pushUndoState();
                     this.text = this.text.substring(0, this.cursorPosition - 1) + this.text.substring(this.cursorPosition);
                     this.cursorPosition--;
                  }

                  return true;
               }
            } else if (keyCode == 261) {
               if (this.hasSelection()) {
                  this.deleteSelection();
                  return true;
               } else {
                  if (this.cursorPosition < this.text.length()) {
                     this.pushUndoState();
                     this.text = this.text.substring(0, this.cursorPosition) + this.text.substring(this.cursorPosition + 1);
                  }

                  return true;
               }
            } else if (keyCode == 263) {
               if (shiftDown) {
                  if (this.selectionAnchor < 0) {
                     this.selectionAnchor = this.cursorPosition;
                  }
               } else {
                  this.selectionAnchor = -1;
               }

               if (this.cursorPosition > 0) {
                  this.cursorPosition--;
               }

               return true;
            } else if (keyCode == 262) {
               if (shiftDown) {
                  if (this.selectionAnchor < 0) {
                     this.selectionAnchor = this.cursorPosition;
                  }
               } else {
                  this.selectionAnchor = -1;
               }

               if (this.cursorPosition < this.text.length()) {
                  this.cursorPosition++;
               }

               return true;
            } else if (keyCode == 268) {
               if (shiftDown) {
                  if (this.selectionAnchor < 0) {
                     this.selectionAnchor = this.cursorPosition;
                  }
               } else {
                  this.selectionAnchor = -1;
               }

               this.cursorPosition = 0;
               return true;
            } else if (keyCode == 269) {
               if (shiftDown) {
                  if (this.selectionAnchor < 0) {
                     this.selectionAnchor = this.cursorPosition;
                  }
               } else {
                  this.selectionAnchor = -1;
               }

               this.cursorPosition = this.text.length();
               return true;
            } else if (keyCode == 257 || keyCode == 335) {
               this.confirm();
               return true;
            } else if (keyCode == 256) {
               this.cancel();
               return true;
            } else {
               return true;
            }
         } else {
            this.insertClipboardText();
            return true;
         }
      }
   }

   private void insertClipboardText() {
      if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
         String clipboard = this.minecraft.keyboardHandler.getClipboard();
         if (clipboard != null && !clipboard.isEmpty()) {
            String sanitized = clipboard.replace("\r", "").replace("\n", "");
            if (!sanitized.isEmpty()) {
               this.replaceSelection(sanitized);
            }
         }
      }
   }

   private boolean hasSelection() {
      return this.selectionAnchor >= 0 && this.selectionAnchor != this.cursorPosition;
   }

   private int getSelectionStart() {
      return Math.min(this.selectionAnchor, this.cursorPosition);
   }

   private int getSelectionEnd() {
      return Math.max(this.selectionAnchor, this.cursorPosition);
   }

   private void deleteSelection() {
      if (this.hasSelection()) {
         this.pushUndoState();
         int start = this.getSelectionStart();
         int end = this.getSelectionEnd();
         this.text = this.text.substring(0, start) + this.text.substring(end);
         this.cursorPosition = start;
         this.selectionAnchor = -1;
      }
   }

   private void replaceSelection(String insertText) {
      if (insertText == null) {
         insertText = "";
      }

      this.pushUndoState();
      if (this.hasSelection()) {
         int start = this.getSelectionStart();
         int end = this.getSelectionEnd();
         this.text = this.text.substring(0, start) + insertText + this.text.substring(end);
         this.cursorPosition = start + insertText.length();
         this.selectionAnchor = -1;
      } else {
         this.text = this.text.substring(0, this.cursorPosition) + insertText + this.text.substring(this.cursorPosition);
         this.cursorPosition = this.cursorPosition + insertText.length();
      }
   }

   private void pushUndoState() {
      if (this.undoStack.size() >= 64) {
         this.undoStack.removeFirst();
      }

      this.undoStack.addLast(new TextInputDialog.EditState(this.text, this.cursorPosition, this.selectionAnchor));
   }

   private void undoEdit() {
      if (!this.undoStack.isEmpty()) {
         TextInputDialog.EditState state = this.undoStack.removeLast();
         this.text = state.text;
         this.cursorPosition = Math.max(0, Math.min(state.cursorPosition, this.text.length()));
         this.selectionAnchor = state.selectionAnchor < 0 ? -1 : Math.max(0, Math.min(state.selectionAnchor, this.text.length()));
      }
   }

   private static class EditState {
      final String text;
      final int cursorPosition;
      final int selectionAnchor;

      EditState(String text, int cursorPosition, int selectionAnchor) {
         this.text = text;
         this.cursorPosition = cursorPosition;
         this.selectionAnchor = selectionAnchor;
      }
   }
}
