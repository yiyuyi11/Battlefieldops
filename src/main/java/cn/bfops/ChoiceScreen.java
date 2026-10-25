package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;
import java.util.function.Consumer;

/**
 * Searchable picker: a scrolling row list on the left, one large preview on the right.
 *
 * <p>Same shape as the reference UI's choice dialog, and on the same shell as the rest of this mod so
 * the panel behind the list cannot come out at a different opacity than the preview beside it. The
 * preview is drawn from the installed gun/vehicle pack's real icon art via {@link AssetIcons}, which is
 * why picking a weapon now shows the weapon rather than its internal id.
 */
final class ChoiceScreen extends FrontScreen {
    record Choice(String id,String name,String kind,String icon){Choice(String id,String name,String kind){this(id,name,kind,id);}}

    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<String> chosen;
    private final String prompt;

    private String query = "";
    private Choice preview;

    private Form listForm;
    private EditBox search;
    private int hovered;
    private boolean searchChanged;
    @Override protected void poll(){if(searchChanged){searchChanged=false;rebuild();}}

    ChoiceScreen(Screen parent, String title, List<Choice> choices, Consumer<String> chosen) {
        super(title);
        this.parent = parent;
        this.prompt = title;
        this.choices = List.copyOf(choices);
        this.chosen = chosen;
        if (!choices.isEmpty()) preview = choices.getFirst();
    }

    private List<Choice> filtered() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return choices;
        return choices.stream()
                .filter(c -> LibrarySearch.matches(needle,c.id,c.name,LibrarySource.searchable(c.icon,c.kind)))
                .toList();
    }

    @Override
    protected String subtitle() {
        return "@模组名 + 名称 · "+filtered().size()+" 项";
    }

    @Override
    protected void rebuild() {
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        clearWidgets();
        listForm = newForm(Ui.headerBody(layout.left));
        search = edit(listForm.take(16, 4), query, 64, text -> {
            query = text;
            hovered = 0;
            searchChanged = true;
        });
        search.setHint(net.minecraft.network.chat.Component.literal("名称 / @模组名"));
        listForm.add(search);
        if(focused){setFocused(search);search.setFocused(true);search.setCursorPosition(Math.min(cursor,query.length()));}

        List<Choice> all = filtered();
        if (preview == null || !all.contains(preview)) preview = all.isEmpty() ? null : all.getFirst();

        int visible = Math.max(1, Layout.fitRows(
                new Layout.Rect(0, 0, 1, Math.max(0, listForm.free() - 2)), Layout.ROW + 4, 2));
        // `hovered` is derived from the preview, never carried independently. The two used to drift:
        // the wheel moved `hovered` while the highlight followed `preview`, so scrolling and the "●"
        // marker disagreed and the next rebuild jumped the list to a different page.
        hovered = preview == null ? 0 : Math.max(0, all.indexOf(preview));
        int from = (hovered / visible) * visible;
        for (int i = from; i < Math.min(all.size(), from + visible); i++) {
            Choice choice = all.get(i);
            Layout.Rect row = listForm.take(Layout.ROW + 4);
            place(listForm, row, choice.name, choice.id.equals(preview == null ? "" : preview.id) ? "●" : "",
                    choice.kind, choice.icon, true, () -> {
                        preview = choice;
                        rebuild();
                    });
        }
        if (all.isEmpty()) listForm.paragraph("没有符合条件的条目。", Ui.TEXT_FAINT);
        listForm.finish();
        buildBar();
    }

    private void buildBar() {
        Layout.Rect bar = layout.bar;
        if (bar.h() <= 0) return;
        Form barForm = newForm(bar.inset(2));
        Layout.Rect[] cells = Layout.columns(bar.inset(2), 3, 3);
        place(barForm, cells[0], "返回", "", "", "", true, this::onClose);
        place(barForm, cells[2], "确认选择", "", "", "", preview != null, () -> {
            if (preview == null) return;
            chosen.accept(preview.id);
            onClose();
        });
        barForm.finish();
        this.barForm = barForm;
    }

    private Form barForm;

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (listForm == null) return;
        Ui.header(graphics, font, layout.left, "可选项");
        Layout.Rect body = listForm.area();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        listForm.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        Layout.Rect previewBody = Ui.header(graphics, font, layout.right, "预览");
        if (preview == null) {
            graphics.drawCenteredString(font, "没有可预览的条目", previewBody.cx(), previewBody.cy() - 4,
                    Ui.TEXT_FAINT);
        } else {
            previewPanel(graphics, previewBody.pad().inset(2), preview);
        }
        if (barForm != null) barForm.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Big icon plus facts. The icon is drawn at an integer scale where possible and never smaller than
     * the source texture, so a gun slot texture is not resampled down into a blurry 16px smudge.
     */
    private void previewPanel(GuiGraphics graphics, Layout.Rect body, Choice choice) {
        Ui.sunken(graphics, body);
        int nameY = body.bottom() - 30;
        Layout.Rect icon = new Layout.Rect(body.x() + 6, body.y() + 6, body.w() - 12,
                Math.max(16, nameY - body.y() - 12));
        AssetIcons.draw(graphics, choice.kind, choice.icon, icon.x(), icon.y(), icon.w(), icon.h());
        graphics.drawString(font, Ui.clip(font, choice.name, body.w() - 12),
                body.x() + 6, nameY + 2, Ui.TEXT, false);
        graphics.drawString(font, Ui.clip(font, "@"+LibrarySource.name(choice.icon)+" · "+choice.icon, body.w() - 12),
                body.x() + 6, nameY + 13, Ui.TEXT_FAINT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if(vertical==0)return false;
        if (listForm != null && listForm.wheel(mouseX, mouseY, vertical)) return true;
        // Otherwise page the selection, which is the useful thing when the wheel is over the preview.
        List<Choice> all = filtered();
        if (all.isEmpty()) return false;
        int index = preview == null ? 0 : all.indexOf(preview);
        hovered = Math.max(0, Math.min(all.size() - 1, index + (vertical > 0 ? -1 : 1)));
        preview = all.get(hovered);
        rebuild();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The list is driven from the preview, so the arrow keys walk it directly. Without this the
        // picker was mouse-only, which is a real limitation for an inventory-sized catalogue.
        if (layout != null && (keyCode == 265 || keyCode == 264)) {
            List<Choice> all = filtered();
            if (!all.isEmpty()) {
                int index = preview == null ? -1 : all.indexOf(preview);
                int next = Math.max(0, Math.min(all.size() - 1, index + (keyCode == 265 ? -1 : 1)));
                preview = all.get(next);
                rebuild();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Kept for callers that build choices from a catalogue page. */
    static List<Choice> fromCatalog(JsonObject catalog, String kind) {
        JsonArray entries = catalog.has("entries") ? catalog.getAsJsonArray("entries") : new JsonArray();
        List<Choice> out = new ArrayList<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String entryKind = Engine.str(entry, "kind", "");
            if (!kind.isEmpty() && !kind.equals(entryKind)) continue;
            String id = Engine.str(entry, "id", "");
            out.add(new Choice(id, Engine.str(entry, "name", id), entryKind));
        }
        return out;
    }
}
