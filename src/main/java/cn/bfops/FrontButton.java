package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat source-style button with a 200 ms eased hover underline and native equipment artwork. */
final class FrontButton extends Button {
    private final String label;
    private final String value;
    private final String iconKind;
    private final String iconId;
    /** Icons are reserved for class and objective markers; action rows stay text-only. */
    boolean primary;boolean showActionIcon=true;
    /** Selected rows (the room list's current entry) get a tinted fill and a left accent bar. */
    boolean selected;
    /** Group headers (a mode name over its rooms) read as a band, not as a clickable row. */
    boolean header;
    private float hoverProgress;
    private long lastFrame=System.nanoTime();

    FrontButton(String label, int x, int y, int w, int h, Runnable action) {
        this(label, "", x, y, w, h, "", "", true, action);
    }

    FrontButton(String label, int x, int y, int w, int h, String iconKind, String iconId, Runnable action) {
        this(label, "", x, y, w, h, iconKind, iconId, true, action);
    }

    FrontButton(String label, String value, int x, int y, int w, int h,
                String iconKind, String iconId, boolean enabled, Runnable action) {
        super(x, y, w, h, Component.literal(compose(label, value)), b -> {
            var screen=Minecraft.getInstance().screen;
            if(screen!=null)screen.setFocused(b);
            action.run();
        }, DEFAULT_NARRATION);
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
        long now=System.nanoTime();float dt=Math.min(.1f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        hoverProgress=Math.clamp(hoverProgress+(on?1:-1)*dt*5,0,1);
        g.fill(getX(),getY(),getX()+width,getY()+height,header?0xb0202a34:!active?0x30203038:Ui.PANEL_ALT);
        if(header)g.fill(getX(),getY()+height-1,getX()+width,getY()+height,Ui.FRAME);
        if(selected&&active){
            g.fill(getX(),getY(),getX()+width,getY()+height,0x585f7c8a);
            g.fill(getX(),getY(),getX()+2,getY()+height,Ui.ACCENT);
        }
        if(hoverProgress>0){float ease=1-(float)Math.pow(1-hoverProgress,3);
            g.fill(getX(),getY()+height-1,getX()+(int)(width*ease),getY()+height,0xff8a9aa8);}
        if(primary)g.fill(getX(),getY()+3,getX()+2,getY()+height-3,Ui.ACCENT);
        int inset = 5;
        int textLeft = getX() + inset;
        if (!iconId.isEmpty() && height >= 14) {
            int box = height - 6;
            int boxWidth = Math.min(box + 8, Math.max(12, width / 3));
            AssetIcons.draw(g, iconKind, iconId, textLeft, getY() + 3, boxWidth, box);
            textLeft += boxWidth + 4;
        }
        int actionIcon=showActionIcon?UiIcons.action(label):-1;
        if(iconId.isEmpty()&&actionIcon>=0&&width>=76&&height>=14){
            // Never taller than the row minus its frame, so the glyph cannot touch the underline or the
            // top border. Shrinking below 16px crops the glyph instead of resampling it, which is what
            // keeps it sharp.
            int glyph=Math.max(8, Math.min(UiIcons.CELL, height-4));
            // A 1px plate behind the glyph: several of the icons are open shapes (the chevrons, the
            // compass) whose thin strokes vanish against the panel body unless they get a slightly
            // darker ground. On hover the plate picks up the accent, which ties the icon to the row state.
            int plateY=getY()+(height-glyph)/2;
            if(glyph<height-2){
                g.fill(textLeft,plateY,textLeft+glyph,plateY+glyph,
                        !active?Ui.PANEL_SUNKEN:(on?Ui.mix(Ui.ACCENT,Ui.PANEL_SUNKEN,0.84):Ui.PANEL_SUNKEN));
                g.fill(textLeft,plateY,textLeft+glyph,plateY+1,
                        !active?Ui.BEVEL_LO:(on?Ui.FRAME_LIGHT:Ui.BEVEL_HI));
            }
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
                    !active ? Ui.TEXT_FAINT : header ? Ui.ACCENT : (on ? Ui.TEXT : Ui.TEXT_DIM), false);
            g.drawString(font, value, textRight - valueWidth, textY,
                    !active ? Ui.TEXT_FAINT : (primary ? Ui.TEXT : Ui.ACCENT), false);
        } else {
            String text = value.isEmpty() ? label : label + "  " + value;
            g.drawString(font, Ui.clip(font, text, room), textLeft, textY,
                    !active ? Ui.TEXT_FAINT : header ? Ui.ACCENT : (on || primary ? Ui.TEXT : Ui.TEXT_DIM), false);
        }
    }
}
