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
    boolean primary;boolean showActionIcon=true;boolean preview;
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
     * <p>Two pixels of every eight, one stripe per eight columns. It used to be a per-pixel loop that
     * tested every pixel of the row and filled roughly a sixteenth of them — a few thousand fills per
     * disabled row per frame, which is the kind of cost that makes a list screen feel heavy. Drawing one
     * column per stripe keeps the pattern (still reads as texture at any GUI scale) at a fraction of the
     * calls.
     */
    private void hatch(GuiGraphics g) {
        int y0 = getY() + 2;
        int y1 = getY() + height - 2;
        int x0 = getX() + 3;
        int x1 = getX() + width - 3;
        if (y1 <= y0 || x1 <= x0) return;
        int color = Ui.mix(Ui.FRAME, Ui.PANEL_SUNKEN, 0.45);
        for (int x = x0; x < x1; x += 8) {
            int offset = (x - x0) % 8;
            int startY = y0 + ((offset * 8) % 8);
            for (int y = startY; y < y1; y += 8) {
                g.fill(x, y, x + 1, Math.min(y + 2, y1), color);
            }
        }
    }

    void setValue(String text) {
        setMessage(Component.literal(compose(label, text)));
    }

    /** A white glyph on a white row is invisible; the selected row inverts its icon. */
    private static void tinted(GuiGraphics g, boolean inverted, Runnable draw) {
        if (inverted) g.setColor(.13f, .15f, .17f, 1f);
        try {
            draw.run();
        } finally {
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean on = isHoveredOrFocused() && active;
        boolean reference = Ui.bf1();
        long now=System.nanoTime();float dt=Math.min(.1f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        hoverProgress=Math.clamp(hoverProgress+(on||selected?1:-1)*dt*5,0,1);
        // White-on-black is the reference look for *list rows*. Large cells (the deploy screen's weapon and
        // gear tiles) keep a dark body with a thin accent instead: a wall of white blocks is not the
        // reference and it also made the selected tile indistinguishable from the rest.
        boolean lit = reference && active && !header && (on || selected) && height <= 24;
        int base = reference
                ? (header ? 0x66000000 : !active ? 0x2a000000 : lit ? 0xf2f4f5f6 : 0x330b0e11)
                : (header ? 0xb0202a34 : !active ? 0x30203038 : Ui.PANEL_ALT);
        g.fill(getX(),getY(),getX()+width,getY()+height,base);
        if(!reference&&(on||selected)&&active&&!header)g.blitSprite(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("battlefield_ops","bf1/button"),getX(),getY(),width,height);
        if(header)g.fill(getX(),getY()+height-1,getX()+width,getY()+height,reference?0x24ffffff:Ui.FRAME);
        if(selected&&active&&!reference){
            g.fill(getX(),getY(),getX()+width,getY()+height,0x585f7c8a);
            g.fill(getX(),getY(),getX()+2,getY()+height,Ui.ACCENT);
        }
        if(reference&&selected&&active){
            g.fill(getX(),getY(),getX()+2,getY()+height,height<=24?0xff14181b:0xffffffff);
            if(height>24)g.fill(getX(),getY(),getX()+width,getY()+1,0x38ffffff);
        }
        if(hoverProgress>0&&!reference){float ease=1-(float)Math.pow(1-hoverProgress,3);
            g.fill(getX(),getY()+height-1,getX()+(int)(width*ease),getY()+height,0xff8a9aa8);}
        if(reference&&on&&active&&!header)g.fill(getX(),getY()+height-1,getX()+width,getY()+height,0x90ffffff);
        if(primary && active)g.fill(getX(),getY(),getX()+width,getY()+height,on?0xffffffff:0xffe3e5e5);
        if(preview&&height>=22){
            int artH=Math.max(10,height-13);
            tinted(g,lit,()->AssetIcons.draw(g,iconKind,iconId,getX()+3,getY()+1,width-6,artH));
            g.drawCenteredString(font,Ui.clip(font,label,width-6),getX()+width/2,getY()+height-10,active?Ui.TEXT:Ui.TEXT_FAINT);
            return;
        }
        int inset = 5;
        int textLeft = getX() + inset;
        if (!iconId.isEmpty() && height >= 14) {
            int box = height - 6;
            int boxWidth = Math.min(box + 8, Math.max(12, width / 3));
            if (lit) g.setColor(.13f, .15f, .17f, 1f);
            try {
                AssetIcons.draw(g, iconKind, iconId, textLeft, getY() + 3, boxWidth, box);
            } finally {
                g.setColor(1f, 1f, 1f, 1f);
            }
            textLeft += boxWidth + 4;
        }
        int actionIcon=showActionIcon&&!primary?UiIcons.action(label):-1;
        if(iconId.isEmpty()&&actionIcon>=0&&width>=76&&height>=14){
            // Never taller than the row minus its frame, so the glyph cannot touch the underline or the
            // top border. Shrinking below 16px crops the glyph instead of resampling it, which is what
            // keeps it sharp.
            int glyph=Math.max(8, Math.min(UiIcons.CELL, height-4));
            int plateY=getY()+(height-glyph)/2;
            if(glyph<height-2){
                g.fill(textLeft,plateY,textLeft+glyph,plateY+glyph,
                        !active?Ui.PANEL_SUNKEN:(on?Ui.mix(Ui.ACCENT,Ui.PANEL_SUNKEN,0.84):Ui.PANEL_SUNKEN));
                g.fill(textLeft,plateY,textLeft+glyph,plateY+1,
                        !active?Ui.BEVEL_LO:(on?Ui.FRAME_LIGHT:Ui.BEVEL_HI));
            }
            if (lit) g.setColor(.13f, .15f, .17f, 1f);
            try {
                UiIcons.draw(g, actionIcon, textLeft, getY() + (height - glyph) / 2, glyph);
            } finally {
                g.setColor(1f, 1f, 1f, 1f);
            }
            textLeft+=glyph+4;
        }
        int textRight = getX() + width - inset;
        int textY = getY() + (height - 8) / 2;

        int ink = lit ? 0xff14181b : 0;
        int valueWidth = value.isEmpty() ? 0 : font.width(value);
        int room = Math.max(0, textRight - textLeft);
        int labelColour = lit ? ink : !active ? Ui.TEXT_FAINT : primary ? 0xff14181b : header ? Ui.ACCENT : (on ? Ui.TEXT : reference ? 0xffe9eef1 : Ui.TEXT_DIM);
        int valueColour = lit ? 0xff2a3034 : !active ? Ui.TEXT_FAINT : (primary ? 0xff14181b : reference ? 0xffc9d2d8 : Ui.ACCENT);
        if (valueWidth > 0 && valueWidth + 12 <= room) {
            String labelText = Ui.clip(font, label, room - valueWidth - 6);
            g.drawString(font, labelText, textLeft, textY, labelColour, false);
            g.drawString(font, value, textRight - valueWidth, textY, valueColour, false);
        } else {
            String text = value.isEmpty() ? label : label + "  " + value;
            g.drawString(font, Ui.clip(font, text, room), textLeft, textY, labelColour, false);
        }
    }
}
