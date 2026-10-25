package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searchable picker over every registered item, with the catalogue built locally.
 *
 * <p><b>Why local.</b> The item registry is synchronised to the client, so the client already has the whole
 * list. The first version asked the server for it page by page, which had three consequences: names came
 * back translated in the <em>server's</em> language (a Chinese client on an English server could not search
 * 铁胸甲), each page cost a round trip, and — because the arrival was routed through the editor screen's
 * callback — the list stayed empty while the picker itself was the open screen. Building the list here
 * removes all three.
 *
 * <p>Search is live: the box filters on every keystroke against ids and localised names, with the same
 * {@code @modid} syntax the weapon and vehicle pickers use.
 */
final class ItemPickerScreen extends FrontScreen {
    private final FrontScreen parent;
    private final Consumer<String> onPick;

    private String query = "";
    private int first;
    private Form listForm;
    private EditBox search;
    private boolean queryChanged;

    /** Filtered results for the current query; recomputed only when the query changes. */
    private List<ItemCatalog.Entry> results;

    ItemPickerScreen(FrontScreen parent, Consumer<String> onPick) {
        super("选择物品");
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected String subtitle() {
        return "全部物品 · " + ItemCatalog.all().size() + " 项";
    }

    /** Rebuilds after the search box changes, rather than rebuilding on every keystroke inside the box. */
    @Override
    protected void poll() {
        if (queryChanged) {
            queryChanged = false;
            rebuild();
        }
    }

    private List<ItemCatalog.Entry> filtered() {
        if (results == null) results = ItemCatalog.search(ItemCatalog.all(), query);
        return results;
    }

    @Override
    protected void rebuild() {
        // The box is recreated on every rebuild, which would otherwise drop focus mid-word.
        boolean focused = search != null && search.isFocused();
        int caret = search == null ? 0 : search.getCursorPosition();
        clearWidgets();

        listForm = newForm(Ui.headerBody(layout.left));
        search = edit(listForm.take(16, 3), query, 64, text -> {
            if (!text.equals(query)) {
                query = text;
                results = null;
                first = 0;
                queryChanged = true;
            }
        });
        search.setHint(net.minecraft.network.chat.Component.literal("名称 / @模组名"));
        listForm.add(search);
        if (focused) {
            setFocused(search);
            search.setFocused(true);
            search.setCursorPosition(Math.min(caret, query.length()));
        }

        List<ItemCatalog.Entry> all = filtered();
        int rows = rowsThatFit();
        // Clamp the window so shrinking the window or the result set cannot leave the list scrolled past
        // its own end, which would render an empty pane with no way back except retyping the query.
        first = Math.max(0, Math.min(first, Math.max(0, all.size() - 1)));

        listForm.section(all.size() + " 个结果");
        int shown = Math.min(rows, all.size() - first);
        for (int i = 0; i < shown; i++) {
            ItemCatalog.Entry entry = all.get(first + i);
            place(listForm, listForm.take(Layout.ROW), entry.name(),
                    "@" + entry.source(), "item", entry.id(), true, () -> onPick.accept(entry.id()));
        }
        if (all.isEmpty()) {
            listForm.paragraph(query.isBlank()
                    ? "物品注册表为空，这在正常的客户端上不应该发生。"
                    : "没有匹配的物品。可用 @模组名 限定来源，或用更短的关键词。", Ui.TEXT_FAINT);
        }

        Layout.Rect[] pager = listForm.pair(Layout.ROW);
        place(listForm, pager[0], "上一页", "", "", "", first > 0, () -> {
            first = Math.max(0, first - rows);
            rebuild();
        });
        place(listForm, pager[1], "下一页", "", "", "", first + rows < all.size(), () -> {
            first = Math.min(Math.max(0, all.size() - 1), first + rows);
            rebuild();
        });
        listForm.finish();
        buildBar();
    }

    /** How many result rows the pane can show alongside the search box, the header and the pager. */
    private int rowsThatFit() {
        int available = Math.max(0, listForm.area().h() - 16 - 12 - Layout.ROW - 12);
        return Math.max(1, available / (Layout.ROW + 2));
    }

    private Form barForm;

    private void buildBar() {
        Layout.Rect bar = layout.bar;
        if (bar.h() <= 0) return;
        Form form = newForm(bar.inset(2));
        Layout.Rect[] cells = Layout.columns(bar.inset(2), 2, 3);
        place(form, cells[0], "返回", "", "", "", true, this::onClose);
        // No confirm button on purpose: clicking a row both chooses and returns. A second confirmation for
        // one item is friction with nothing to confirm.
        place(form, cells[1], "取消", "", "", "", true, this::onClose);
        form.finish();
        barForm = form;
    }

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (listForm == null) return;
        Ui.header(graphics, font, layout.left, "物品选择");
        Layout.Rect body = listForm.area();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        listForm.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        Layout.Rect right = Ui.header(graphics, font, layout.right, "说明");
        int y = right.y() + 6;
        graphics.drawString(font, "输入即在全部已注册物品中筛选。", right.x() + 6, y, Ui.TEXT_DIM, false);
        y += 12;
        graphics.drawString(font, "@模组名 限定来源，例如 @minecraft 。", right.x() + 6, y, Ui.TEXT_DIM, false);
        y += 12;
        graphics.drawString(font, "点一行即选中并返回，无需再确认。", right.x() + 6, y, Ui.TEXT_DIM, false);
        y += 16;
        String trimmed = query.trim();
        if (!trimmed.isEmpty()) {
            graphics.drawString(font, Ui.clip(font, "当前筛选：" + trimmed, right.w() - 12),
                    right.x() + 6, y, Ui.TEXT_WARN, false);
        } else {
            graphics.drawString(font, Ui.clip(font, "共 " + ItemCatalog.all().size() + " 项，请用搜索缩小范围",
                    right.w() - 12), right.x() + 6, y, Ui.TEXT_FAINT, false);
        }
        // Name a couple of real namespaces so @modid is discoverable without guessing pack names.
        y += 14;
        List<String> sources = ItemCatalog.sources(ItemCatalog.all());
        if (!sources.isEmpty()) {
            String sample = "@" + sources.getFirst()
                    + (sources.size() > 1 ? "  @" + sources.get(1) : "");
            graphics.drawString(font, Ui.clip(font, "例如：" + sample, right.w() - 12),
                    right.x() + 6, y, Ui.TEXT_FAINT, false);
        }
        if (barForm != null) barForm.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return false;
        int rows = rowsThatFit();
        int size = filtered().size();
        if (vertical < 0) {
            first = Math.min(Math.max(0, size - 1), first + rows);
        } else {
            first = Math.max(0, first - rows);
        }
        rebuild();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
