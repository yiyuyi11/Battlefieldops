package cn.bfops.killicon.client.gui.elements;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import cn.bfops.killicon.client.gui.GuiConstants;
import cn.bfops.killicon.client.gui.elements.entries.FixedChoiceConfigEntry;

public class ChoiceListDialog {
   private final Minecraft minecraft;
   private Consumer<String> onConfirm;
   private Runnable onCancel;
   private boolean visible = false;
   private String title = "";
   private String filterText = "";
   private int cursorPosition = 0;
   private int selectionAnchor = -1;
   private final ArrayDeque<ChoiceListDialog.EditState> undoStack = new ArrayDeque<>();
   private int displayOffset = 0;
   private String selectedValue = "";
   private final List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
   private final List<FixedChoiceConfigEntry.Choice> filteredChoices = new ArrayList<>();
   private final List<GDRowRenderer> rowRenderers = new ArrayList<>();
   private Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate;
   private Consumer<FixedChoiceConfigEntry.Choice> onExtraAction;
   private Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate;
   private Consumer<FixedChoiceConfigEntry.Choice> onDirectAction;
   private String extraActionText = "";
   private int extraActionColor = -65536;
   private int extraActionWidth = 17;
   private boolean isDraggingList = false;
   private double lastMouseY = 0.0;
   private double scrollY = 0.0;
   private double targetScrollY = 0.0;
   private float inputHoverProgress = 0.0F;
   private long lastFrameTime;
   private GDButton confirmButton;
   private GDButton cancelButton;
   private GDTextRenderer titleRenderer;
   private static final int PANEL_WIDTH = 140;
   private static final int BUTTON_HEIGHT = 17;
   private static final int INPUT_HEIGHT = 17;
   private static final int HUE_BAR_WIDTH = 17;
   private static final int PAD = 8;
   private static final int LIST_HEIGHT = 115;
   private static final int PANEL_HEIGHT = 165;

   public ChoiceListDialog(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel) {
      this.show(initialValue, title, options, onConfirm, onCancel, null, null, "", -65536, 17, null, null);
   }

   public void show(
      String initialValue,
      String title,
      List<FixedChoiceConfigEntry.Choice> options,
      Consumer<String> onConfirm,
      Runnable onCancel,
      Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
      String extraActionText,
      int extraActionColor,
      int extraActionWidth
   ) {
      this.show(
         initialValue,
         title,
         options,
         onConfirm,
         onCancel,
         extraActionVisiblePredicate,
         onExtraAction,
         extraActionText,
         extraActionColor,
         extraActionWidth,
         null,
         null
      );
   }

   public void show(
      String initialValue,
      String title,
      List<FixedChoiceConfigEntry.Choice> options,
      Consumer<String> onConfirm,
      Runnable onCancel,
      Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
      String extraActionText,
      int extraActionColor,
      int extraActionWidth,
      Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
      Consumer<FixedChoiceConfigEntry.Choice> onDirectAction
   ) {
      this.title = title;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
      this.extraActionVisiblePredicate = extraActionVisiblePredicate;
      this.onExtraAction = onExtraAction;
      this.directActionPredicate = directActionPredicate;
      this.onDirectAction = onDirectAction;
      this.extraActionText = extraActionText == null ? "" : extraActionText;
      this.extraActionColor = extraActionColor;
      this.extraActionWidth = extraActionWidth <= 0 ? 17 : extraActionWidth;
      this.visible = true;
      this.lastFrameTime = System.currentTimeMillis();
      this.inputHoverProgress = 0.0F;
      this.filterText = "";
      this.cursorPosition = 0;
      this.selectionAnchor = -1;
      this.undoStack.clear();
      this.displayOffset = 0;
      this.choices.clear();
      if (options != null) {
         this.choices.addAll(options);
      }

      this.selectedValue = this.resolveValue(initialValue);
      this.applyFilter();
      int w1 = 69;
      int w2 = 139 - w1;
      this.confirmButton = new GDButton(
         0, 0, w2, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.confirm", new Object[0])), btn -> this.confirm()
      );
      this.cancelButton = new GDButton(
         0, 0, w1, 17, Component.literal(I18n.get("battlefield_ops.client.gui.config.cancel", new Object[0])), btn -> this.cancel()
      );
      if (this.titleRenderer == null) {
         this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0F, GuiConstants.COLOR_GOLD, false);
      } else {
         this.titleRenderer.setText(title);
         this.titleRenderer.setColor(GuiConstants.COLOR_GOLD);
      }
   }

   public boolean isVisible() {
      return this.visible;
   }

   public void hide() {
      this.visible = false;
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (this.visible) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int dimColor = -2008791996;
         guiGraphics.fill(0, 0, screenWidth, screenHeight, dimColor);
         int centerX = (screenWidth - 140) / 2;
         int centerY = (screenHeight - 165) / 2 + 20;
         int fontHeight = 9;
         int titleY = centerY - fontHeight - 8 + 6;
         int buttonsY = centerY + 165 - 17;
         int containerTop = titleY - 8;
         int containerLeft = centerX - 8;
         int containerRight = centerX + 140 + 8;
         int containerBottom = buttonsY + 17 + 8;
         guiGraphics.fill(containerLeft, containerTop, containerRight, containerBottom, 1296319556);
         if (this.titleRenderer != null) {
            this.titleRenderer.setX1(centerX);
            this.titleRenderer.setY1(titleY);
            this.titleRenderer.setX2(centerX + 140);
            this.titleRenderer.setY2(titleY + fontHeight);
            this.titleRenderer.render(guiGraphics, partialTick);
         }

         int listY = buttonsY - 8 - 17 - 8 - 115;
         int listW = 140;
         int listH = 115;
         int inputY = buttonsY - 8 - 17;
         int inputW = 140;
         long currentTime = System.currentTimeMillis();
         float dt = (float)(currentTime - this.lastFrameTime) / 1000.0F;
         this.lastFrameTime = currentTime;
         boolean isHoveringInput = mouseX >= centerX && mouseX <= centerX + inputW && mouseY >= inputY && mouseY <= inputY + 17;
         if (isHoveringInput) {
            this.inputHoverProgress = Math.min(1.0F, this.inputHoverProgress + dt * 2.0F);
         } else {
            this.inputHoverProgress = Math.max(0.0F, this.inputHoverProgress - dt * 2.0F);
         }

         if (this.isDraggingList) {
            double diff = (double)mouseY - this.lastMouseY;
            this.targetScrollY -= diff;
            this.lastMouseY = (double)mouseY;
         }

         this.updateScroll(dt, listH);
         int listBgColor = GuiConstants.COLOR_BLACK & 16777215 | 1493172224;
         guiGraphics.fill(centerX, listY, centerX + listW, listY + listH, listBgColor);
         this.renderList(guiGraphics, mouseX, mouseY, partialTick, centerX, listY, centerX + listW, listY + listH);
         int inputBgColor = GuiConstants.COLOR_BLACK & 16777215 | 1912602624;
         guiGraphics.fill(centerX, inputY, centerX + inputW, inputY + 17, inputBgColor);
         this.renderHoverTrail(guiGraphics, centerX, inputY, inputW, 17, this.inputHoverProgress);
         this.renderInputText(guiGraphics, centerX, inputY, inputW, 17);
         int btnRightX = centerX + 69 + 1;
         if (this.cancelButton != null) {
            this.cancelButton.setX(centerX);
            this.cancelButton.setY(buttonsY);
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         if (this.confirmButton != null) {
            this.confirmButton.setX(btnRightX);
            this.confirmButton.setY(buttonsY);
            this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
         }

         guiGraphics.pose().popPose();
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (this.confirmButton != null && this.confirmButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int centerX = (screenWidth - 140) / 2;
         int centerY = (screenHeight - 165) / 2 + 20;
         int buttonsY = centerY + 165 - 17;
         int inputY = buttonsY - 8 - 17;
         int listY = inputY - 8 - 115;
         int listX = centerX;
         if (mouseX >= (double)centerX && mouseX <= (double)(centerX + 140) && mouseY >= (double)inputY && mouseY <= (double)(inputY + 17)) {
            this.setCursorByMouse((int)mouseX, centerX);
            this.selectionAnchor = -1;
            return true;
         } else if (mouseX >= (double)centerX && mouseX <= (double)(centerX + 140) && mouseY >= (double)listY && mouseY <= (double)(listY + 115)) {
            double adjustedMouseY = mouseY + this.scrollY;
            int rowHeight = 17;
            int selectWidth = 30;

            for (int i = 0; i < this.filteredChoices.size(); i++) {
               FixedChoiceConfigEntry.Choice choice = this.filteredChoices.get(i);
               boolean hasExtraAction = this.hasExtraAction(choice);
               int extraWidth = hasExtraAction ? this.extraActionWidth : 0;
               int selectX1 = listX + 140 - selectWidth - extraWidth;
               int extraX1 = listX + 140 - extraWidth;
               int rowTop = listY + i * (rowHeight + 1);
               int rowBottom = rowTop + rowHeight;
               if (adjustedMouseY >= (double)rowTop && adjustedMouseY <= (double)rowBottom) {
                  if (button == 0 && this.isDirectAction(choice)) {
                     if (this.onDirectAction != null) {
                        this.onDirectAction.accept(choice);
                     }
                  } else if (button == 0 && hasExtraAction && mouseX >= (double)extraX1) {
                     if (this.onExtraAction != null) {
                        this.onExtraAction.accept(choice);
                     }
                  } else if (button == 0 && mouseX >= (double)selectX1) {
                     this.selectChoice(choice);
                  }
                  break;
               }
            }

            this.isDraggingList = true;
            this.lastMouseY = mouseY;
            return true;
         } else {
            return true;
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDraggingList = false;
      return false;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (!this.visible) {
         return false;
      } else {
         int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         int centerX = (screenWidth - 140) / 2;
         int centerY = (screenHeight - 165) / 2 + 20;
         int buttonsY = centerY + 165 - 17;
         int listY = buttonsY - 8 - 17 - 8 - 115;
         if (mouseX >= (double)centerX && mouseX <= (double)(centerX + 140) && mouseY >= (double)listY && mouseY <= (double)(listY + 115)) {
            this.targetScrollY -= delta * 40.0;
            return true;
         } else {
            return true;
         }
      }
   }

   public boolean charTyped(char codePoint, int modifiers) {
      if (!this.visible) {
         return false;
      } else if ((codePoint >= 32 && codePoint != 127)) {
         this.replaceSelection(String.valueOf(codePoint));
         this.applyFilter();
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
            this.cursorPosition = this.filterText.length();
            return true;
         } else if (controlDown && keyCode == 67) {
            if (this.hasSelection() && this.minecraft != null && this.minecraft.keyboardHandler != null) {
               this.minecraft.keyboardHandler.setClipboard(this.filterText.substring(this.getSelectionStart(), this.getSelectionEnd()));
            }

            return true;
         } else if (controlDown && keyCode == 90) {
            this.undoEdit();
            this.applyFilter();
            return true;
         } else if ((!controlDown || keyCode != 86) && (!shiftDown || keyCode != 260)) {
            if (keyCode == 259) {
               if (this.hasSelection()) {
                  this.deleteSelection();
                  this.applyFilter();
                  return true;
               } else {
                  if (this.cursorPosition > 0) {
                     this.pushUndoState();
                     this.filterText = this.filterText.substring(0, this.cursorPosition - 1) + this.filterText.substring(this.cursorPosition);
                     this.cursorPosition--;
                     this.applyFilter();
                  }

                  return true;
               }
            } else if (keyCode == 261) {
               if (this.hasSelection()) {
                  this.deleteSelection();
                  this.applyFilter();
                  return true;
               } else {
                  if (this.cursorPosition < this.filterText.length()) {
                     this.pushUndoState();
                     this.filterText = this.filterText.substring(0, this.cursorPosition) + this.filterText.substring(this.cursorPosition + 1);
                     this.applyFilter();
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

               if (this.cursorPosition < this.filterText.length()) {
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

               this.cursorPosition = this.filterText.length();
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
            this.applyFilter();
            return true;
         }
      }
   }

   private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int y1, int x2, int y2) {
      if (y2 > y1) {
         guiGraphics.enableScissor(x1, y1, x2, y2);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0, -this.scrollY, 0.0);
         int rowHeight = 17;

         for (int i = 0; i < this.filteredChoices.size(); i++) {
            FixedChoiceConfigEntry.Choice choice = this.filteredChoices.get(i);
            int rowTop = y1 + i * (rowHeight + 1);
            int rowBottom = rowTop + rowHeight;
            float actualTop = (float)rowTop - (float)this.scrollY;
            float actualBottom = (float)rowBottom - (float)this.scrollY;
            if (actualBottom > (float)y1 && actualTop < (float)y2) {
               this.renderChoiceRow(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY), partialTick, i, choice, x1, rowTop, x2, rowBottom);
            }
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
      }
   }

   private void renderChoiceRow(
      GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, FixedChoiceConfigEntry.Choice choice, int x1, int y1, int x2, int y2
   ) {
      int rowBgColor = GuiConstants.COLOR_BLACK & 16777215;
      float alpha = index % 2 == 1 ? 0.1F : 0.3F;

      while (this.rowRenderers.size() <= index) {
         this.rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2, rowBgColor, alpha, false));
      }

      GDRowRenderer renderer = this.rowRenderers.get(index);
      renderer.setBounds(x1, y1, x2, y2);
      renderer.setBackgroundColor(rowBgColor);
      renderer.setBackgroundAlpha(alpha);
      renderer.resetColumnConfig();
      boolean selected = choice.value().equals(this.selectedValue);
      int firstColor = selected ? GuiConstants.COLOR_GOLD : -1;
      int secondColor = selected ? -5592406 : -1;
      if (this.isDirectAction(choice)) {
         renderer.addColumn(" " + choice.label(), -1, GuiConstants.COLOR_GOLD, true, true, btn -> {
            if (this.onDirectAction != null) {
               this.onDirectAction.accept(choice);
            }
         });
         renderer.render(guiGraphics, mouseX, mouseY, partialTick);
      } else {
         renderer.addColumn(" " + choice.label(), -1, firstColor, true, false, null);
         renderer.addColumn(
            I18n.get("battlefield_ops.client.gui.choice.select", new Object[0]), 30, secondColor, false, true, btn -> this.selectChoice(choice)
         );
         if (this.hasExtraAction(choice)) {
            renderer.addColumn(this.extraActionText, this.extraActionWidth, this.extraActionColor, true, true, btn -> {
               if (this.onExtraAction != null) {
                  this.onExtraAction.accept(choice);
               }
            });
         }

         renderer.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   private void renderInputText(GuiGraphics guiGraphics, int x, int y, int w, int h) {
      guiGraphics.enableScissor(x, y, x + w, y + h);
      int textWidth = this.minecraft.font.width(this.filterText);
      int boxInnerWidth = w - 4;
      String textBeforeCursor = this.filterText.substring(0, this.cursorPosition);
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
         int selStartRel = this.minecraft.font.width(this.filterText.substring(0, this.getSelectionStart()));
         int selEndRel = this.minecraft.font.width(this.filterText.substring(0, this.getSelectionEnd()));
         int sx = drawX + selStartRel;
         int ex = drawX + selEndRel;
         guiGraphics.fill(sx, drawY - 1, ex, drawY + 9 + 1, 1728042752);
      }

      guiGraphics.drawString(this.minecraft.font, this.filterText, drawX, drawY, -1, true);
      if (System.currentTimeMillis() / 500L % 2L == 0L) {
         int cx = drawX + cursorXRel;
         if (cx >= x && cx <= x + w) {
            guiGraphics.fill(cx, drawY - 1, cx + 1, drawY + 9 + 1, -1);
         }
      }

      guiGraphics.disableScissor();
   }

   private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h, float progress) {
      if (!(progress <= 0.001F)) {
         int color = GuiConstants.COLOR_GOLD;
         float totalLength = (float)(w + h + w + h);
         float currentLength = totalLength * this.easeOut(progress);
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

   private void setCursorByMouse(int mouseX, int inputX) {
      int mouseOffset = mouseX - (inputX + 2) + this.displayOffset;
      if (mouseOffset <= 0) {
         this.cursorPosition = 0;
      } else {
         int pos = 0;

         for (int i = 1; i <= this.filterText.length(); pos = i++) {
            int width = this.minecraft.font.width(this.filterText.substring(0, i));
            if (width >= mouseOffset) {
               pos = i;
               break;
            }
         }

         this.cursorPosition = pos;
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
         this.filterText = this.filterText.substring(0, start) + this.filterText.substring(end);
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
         this.filterText = this.filterText.substring(0, start) + insertText + this.filterText.substring(end);
         this.cursorPosition = start + insertText.length();
         this.selectionAnchor = -1;
      } else {
         this.filterText = this.filterText.substring(0, this.cursorPosition) + insertText + this.filterText.substring(this.cursorPosition);
         this.cursorPosition = this.cursorPosition + insertText.length();
      }
   }

   private void pushUndoState() {
      if (this.undoStack.size() >= 64) {
         this.undoStack.remove(0);
      }

      this.undoStack.addLast(new ChoiceListDialog.EditState(this.filterText, this.cursorPosition, this.selectionAnchor));
   }

   private void undoEdit() {
      if (!this.undoStack.isEmpty()) {
         ChoiceListDialog.EditState state = this.undoStack.removeLast();
         this.filterText = state.text;
         this.cursorPosition = Math.max(0, Math.min(state.cursorPosition, this.filterText.length()));
         this.selectionAnchor = state.selectionAnchor < 0 ? -1 : Math.max(0, Math.min(state.selectionAnchor, this.filterText.length()));
      }
   }

   private void updateScroll(float dt, int viewHeight) {
      int contentHeight = this.filteredChoices.size() * 18;
      double maxScroll = (double)Math.max(0, contentHeight - viewHeight);
      this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
      double diff = this.targetScrollY - this.scrollY;
      if (Math.abs(diff) < 0.01) {
         this.scrollY = this.targetScrollY;
      } else {
         this.scrollY += diff * 15.0 * (double)dt;
      }
   }

   private void applyFilter() {
      String filter = this.filterText == null ? "" : this.filterText.toLowerCase(Locale.ROOT);
      this.filteredChoices.clear();

      for (FixedChoiceConfigEntry.Choice choice : this.choices) {
         String label = choice.label() == null ? "" : choice.label();
         String value = choice.value() == null ? "" : choice.value();
         if (filter.isEmpty() || label.toLowerCase(Locale.ROOT).contains(filter) || value.toLowerCase(Locale.ROOT).contains(filter)) {
            this.filteredChoices.add(choice);
         }
      }

      this.targetScrollY = 0.0;
      this.scrollY = 0.0;
      this.cursorPosition = Math.min(this.cursorPosition, this.filterText.length());
      if (this.selectionAnchor >= 0) {
         this.selectionAnchor = Math.min(this.selectionAnchor, this.filterText.length());
      }
   }

   private String resolveValue(String value) {
      if (value != null) {
         for (FixedChoiceConfigEntry.Choice choice : this.choices) {
            if (choice.value().equals(value)) {
               return value;
            }
         }
      }

      return !this.choices.isEmpty() ? this.choices.get(0).value() : "";
   }

   private void selectChoice(FixedChoiceConfigEntry.Choice choice) {
      if (choice != null) {
         this.selectedValue = choice.value();
      }
   }

   private boolean hasExtraAction(FixedChoiceConfigEntry.Choice choice) {
      return choice != null && this.extraActionVisiblePredicate != null && this.onExtraAction != null && this.extraActionVisiblePredicate.test(choice);
   }

   private boolean isDirectAction(FixedChoiceConfigEntry.Choice choice) {
      return choice != null && this.directActionPredicate != null && this.onDirectAction != null && this.directActionPredicate.test(choice);
   }

   private void confirm() {
      if (this.onConfirm != null) {
         String result = this.selectedValue;
         if ((result == null || result.isEmpty()) && !this.choices.isEmpty()) {
            result = this.choices.get(0).value();
         }

         this.onConfirm.accept(result);
      }

      this.visible = false;
   }

   private void cancel() {
      if (this.onCancel != null) {
         this.onCancel.run();
      }

      this.visible = false;
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
