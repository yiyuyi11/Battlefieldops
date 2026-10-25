package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One control row, following the reference UI's shape: a flat body, a centred label, a thin accent
 * underline that lights up on hover, and an optional icon on the left.
 *
 * <p>Two changes from the previous version fix reported bugs rather than restyling them. The body is
 * now <em>opaque</em>, so a row is never translucent over the pane behind it and over the world at
 * once — that double transparency is what made rows look like they floated on another layer. And the
 * label / value split (dim label left, bright value right) means a row can say
 * {@code 出生点   东侧} instead of cramming both into one string, which is what the old UI did and
 * why long values were silently truncated.
 *
 * <p>A disabled row is dimmed but still drawn: "this action exists but you cannot use it" is
 * information, and hiding the row entirely was why the deployment page looked half-empty in some
 * states.
 */
final class FrontButton extends Button {
    private final String label;
    private final String value;
    private final String iconKind;
    private final String iconId;
    /** Primary rows are taller and use the accent colour, so the page's main action stands out. */
    boolean primary;

    FrontButton(String label, int x, int y, int w, int h, Runnable action) {
        this(label, "", x, y, w, h, "", "", true, action);
    }

    FrontButton(String label, int x, int y, int w, int h, String iconKind, String iconId, Runnable action) {
        this(label, "", x, y, w, h, iconKind, iconId, true, action);
    }

    FrontButton(String label, String value, int x, int y, int w, int h,
                String iconKind, String iconId, boolean enabled, Runnable action) {
        super(x, y, w, h, Component.literal(compose(label, value)), b -> action.run(), DEFAULT_NARRATION);
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
        this.iconKind = iconKind == null ? "" : iconKind;
        this.iconId = iconId == null ? "" : iconId;
        this.active = enabled;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(compose(label,value))));
    }

    private static String compose(String label, String value) {
        String head = label == null ? "" : label;
        return value == null || value.isEmpty() ? head : head + "  " + value;
    }

    /**
     * Diagonal hatching over a disabled row.
     *
     * <p>Sparse on purpose: a fully hatched body would compete with the label, and the point is only
     * to make "unavailable" distinguishable from "unselected" without changing the row's text colour
     * contract. Two pixels of every eight is enough to read as texture at any GUI scale.
     */
    private void hatch(GuiGraphics g) {
        int y0 = getY() + 2;
        int y1 = getY() + height - 2;
        int x0 = getX() + 3;
        int x1 = getX() + width - 3;
        if (y1 <= y0 || x1 <= x0) return;
        int color = Ui.mix(Ui.FRAME, Ui.PANEL_SUNKEN, 0.45);
        for (int x = x0; x < x1; x++) {
            int offset = (x - x0) % 8;
            if (offset != 0 && offset != 1) continue;
            for (int y = y0; y < y1; y++) {
                if ((x - x0 + y - y0) % 8 >= 2) continue;
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    void setValue(String text) {
        setMessage(Component.literal(compose(label, text)));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean on = isHoveredOrFocused() && active;
        int body = !active ? Ui.PANEL_SUNKEN
                : primary ? (on ? Ui.mix(Ui.ACCENT, Ui.PANEL_ALT, 0.72) : Ui.mix(Ui.ACCENT, Ui.PANEL_ALT, 0.86))
                : (on ? Ui.PANEL_ALT : Ui.PANEL);
        g.fill(getX()+2,getY(),getX()+width-2,getY()+height,body);
        g.fill(getX(),getY()+2,getX()+width,getY()+height-2,body);
        if(on){g.fill(getX()+2,getY(),getX()+width-2,getY()+1,Ui.FRAME_LIGHT);}
        if(primary)g.fill(getX(),getY()+3,getX()+2,getY()+height-3,Ui.ACCENT);
        // A disabled row gets a hatched body instead of a flat one. "Dimmed" alone was too close to
        // "unselected" at a glance, and an operator repeatedly clicking a row that looks normal is the
        // most common way a disabled control reads as a broken one.
        if (!active) hatch(g);
        // 1px solid underline, never a translucent stroke: alpha lines over terrain are exactly what
        // read as blur.
        int underline = !active ? Ui.FRAME
                : primary ? Ui.ACCENT
                : (on ? Ui.FRAME_LIGHT : Ui.ACCENT_DIM);
        g.fill(getX() + 1, getY() + height - 1, getX() + width - 1, getY() + height, underline);
        // Hover leaves a bracket on the trailing edge so the pointer's target is unambiguous even when
        // the row body is already bright (a primary action, or a selected tab-like row).
        if (on) {
            g.fill(getX() + width - 3, getY() + 3, getX() + width - 1, getY() + height - 3, Ui.ACCENT);
        }

        int inset = 5;
        int textLeft = getX() + inset;
        if (!iconId.isEmpty() && height >= 14) {
            int box = height - 6;
            int boxWidth = Math.min(box + 8, Math.max(12, width / 3));
            AssetIcons.draw(g, iconKind, iconId, textLeft, getY() + 3, boxWidth, box);
            textLeft += boxWidth + 4;
        }
        int actionIcon=UiIcons.action(label);
        if(iconId.isEmpty()&&actionIcon>=0&&width>=76&&height>=14){
            // Never taller than the row minus its frame, so the glyph cannot touch the underline or the
            // top border. Shrinking below 16px crops the glyph instead of resampling it, which is what
            // keeps it sharp.
            int glyph=Math.max(8, Math.min(UiIcons.CELL, height-4));
            UiIcons.draw(g,actionIcon,textLeft,getY()+(height-glyph)/2,glyph);
            textLeft+=glyph+4;
        }
        int textRight = getX() + width - inset;
        int textY = getY() + (height - 8) / 2;

        int valueWidth = value.isEmpty() ? 0 : font.width(value);
        int room = Math.max(0, textRight - textLeft);
        if (valueWidth > 0 && valueWidth + 12 <= room) {
            String labelText = Ui.clip(font, label, room - valueWidth - 6);
            g.drawString(font, labelText, textLeft, textY,
                    !active ? Ui.TEXT_FAINT : (on ? Ui.TEXT : Ui.TEXT_DIM), false);
            g.drawString(font, value, textRight - valueWidth, textY,
                    !active ? Ui.TEXT_FAINT : (primary ? Ui.TEXT : Ui.ACCENT), false);
        } else {
            String text = value.isEmpty() ? label : label + "  " + value;
            g.drawString(font, Ui.clip(font, text, room), textLeft, textY,
                    !active ? Ui.TEXT_FAINT : (on || primary ? Ui.TEXT : Ui.TEXT_DIM), false);
        }
    }
}
