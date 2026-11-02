package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The kill-icon preset picker.
 *
 * <p>F9 cycles presets one at a time, which is fine for trying them out and hopeless for finding a particular
 * one among 37. This is the list: every preset the build ships, with its pack name and the element it draws.
 *
 * <p><b>Why the list is filtered rather than paged.</b> 37 entries do not fit on one page, so this follows
 * {@link ItemPickerScreen}'s pattern exactly — a search box, a window of rows, and a pager. The search matches
 * the display name, the preset id and the mode key, because all three are things a player might remember.
 *
 * <p><b>Why choosing writes the preference immediately.</b> There is no OK button. The choice is a single value
 * with an obvious meaning, and a preview behind the screen would be misleading if it survived a Cancel that did
 * not apply the change.
 */
final class KillIconPickerScreen extends FrontScreen {
    private List<KillIconPresets.Preset> results;
    private Form listForm;
    private EditBox search;
    private String query = "";
    private int first;
    private boolean queryChanged;
    private final FrontScreen parent;

    KillIconPickerScreen(FrontScreen parent) {
        super("击杀图标预设");
        this.parent=parent;
    }

    @Override
    protected String subtitle() {
        int total = KillIconStore.corpus().size();
        int shown = results == null ? total : results.size();
        return shown == total ? "共 " + total + " 个预设" : "显示 " + shown + " / " + total + " 个预设";
    }

    private List<KillIconPresets.Preset> filtered() {
        if (results == null) results = filter(query);
        return results;
    }

    /** Rebuilds after the search box changes, rather than on every keystroke inside the box. */
    @Override
    protected void poll() {
        if (queryChanged) {
            queryChanged = false;
            rebuild();
        }
    }

    @Override
    protected void rebuildContent() {
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
        search.setHint(net.minecraft.network.chat.Component.literal("名称 / 编号 / 模式"));
        listForm.add(search);
        if (focused) {
            setFocused(search);
            search.setFocused(true);
            search.setCursorPosition(Math.min(caret, query.length()));
        }

        List<KillIconPresets.Preset> all = filtered();
        int rows = rowsThatFit();
        // Clamp so shrinking the window cannot leave the list scrolled past its own end.
        first = Math.max(0, Math.min(first, Math.max(0, all.size() - 1)));

        String current = ClientPrefs.killIconPreset();
        listForm.section(all.size() + " 个结果");
        int shown = Math.min(rows, all.size() - first);
        for (int i = 0; i < shown; i++) {
            KillIconPresets.Preset preset = all.get(first + i);
            boolean active = preset.id().equals(current);
            String label = (active ? "✔ " : "") + preset.name();
            // The value is the id plus the mode: those are what distinguish two presets whose names look
            // alike -- the 26 Valorant skins differ only by colourway.
            String value = preset.id() + "  " + modeLabel(preset.mode());
            place(listForm, listForm.take(Layout.ROW), label, value, "", "", true,
                    () -> choose(preset));
        }
        if (all.isEmpty()) {
            listForm.paragraph(query.isBlank()
                    ? "没有可用的预设。这不应该发生：预设在模组自带资源里。"
                    : "没有匹配的预设。可试更短的关键词，或用编号（如 00037）。", Ui.TEXT_FAINT);
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
        place(listForm, listForm.take(Layout.ROW), "返回", "", "", "", true, this::onClose);
        listForm.finish();
    }

    private void choose(KillIconPresets.Preset preset) {
        ClientPrefs.setKillIconPreset(preset.id());
        // Rebuild the live strip immediately, so the change is in effect the moment the screen closes.
        KillIconStore.applyPreset(preset.id());
        NativeKillIcons.preset(preset.id());
        ok("已选择：" + preset.name());
        rebuild();
    }

    @Override public void onClose(){minecraft.setScreen(parent);}

    @Override
    protected void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.header(graphics,font,layout.left,"官方预设");listForm.render(graphics,mouseX,mouseY,partialTick);
        var body=Ui.header(graphics,font,layout.right,"当前预设");
        graphics.drawWordWrap(font,net.minecraft.network.chat.Component.literal("当前："+ClientPrefs.killIconPreset()+"。预设包含图标、字幕、声音和动画配置。完整的元素编辑、动画预览及导入导出请使用原版击杀图标设置界面。"),body.x()+6,body.y()+6,Math.max(10,body.w()-12),Ui.TEXT);
    }

    /** How many result rows the pane can show alongside the search box, the header and the pager. */
    private int rowsThatFit() {
        int available = Math.max(0, listForm.area().h() - 16 - 12 - Layout.ROW - 12);
        return Math.max(1, available / (Layout.ROW + 2));
    }

    /** Presets matching a query, by display name, id or mode. */
    static List<KillIconPresets.Preset> filter(String query) {
        List<KillIconPresets.Preset> all = KillIconStore.corpus().all();
        if (query == null || query.isBlank()) return all;
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<KillIconPresets.Preset> out = new ArrayList<>();
        for (KillIconPresets.Preset preset : all) {
            if (preset.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || preset.id().contains(needle)
                    || modeLabel(preset.mode()).toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || (preset.mode() != null
                    && preset.mode().toLowerCase(java.util.Locale.ROOT).contains(needle))) {
                out.add(preset);
            }
        }
        return out;
    }

    /**
     * A short Chinese label for an element key.
     *
     * <p>The pack's modes are English keys ({@code kill_icon/card_bar}); showing one raw in a Chinese UI reads
     * like a leak. The mapping is deliberately not exhaustive-safe: an unmapped key shows as itself rather than
     * as "unknown", so a mode added by a future pack version stays visible instead of hidden.
     */
    static String modeLabel(String mode) {
        if (mode == null) return "";
        return switch (mode) {
            case "kill_icon/scrolling" -> "滚动图标";
            case "kill_icon/card" -> "卡牌";
            case "kill_icon/card_bar" -> "卡牌光条";
            case "kill_icon/combo" -> "连杀图标";
            case "kill_icon/valorant" -> "Valorant";
            case "kill_icon/honor" -> "荣誉徽章";
            case "kill_icon/battlefield1" -> "战地1";
            case "subtitle" -> "仅字幕";
            default -> mode;
        };
    }
}
