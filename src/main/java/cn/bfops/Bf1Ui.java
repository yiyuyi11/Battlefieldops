package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Presentation only; original BF1 atlas crops are recorded in bf1_asset_manifest.json. */
final class Bf1Ui {
    static void icon(GuiGraphics g, String name, int x, int y, int size) {
        AssetIcons.draw(g, "bf1", name, x, y, size, size);
    }
    /**
     * The backdrop shared by every mod screen: the player's own image when they picked one, otherwise the
     * built-in BF1 plate, always under a dark veil so panel text stays readable.
     */
    static void screenBackdrop(GuiGraphics g, int width, int height) {
        int veil = Minecraft.getInstance().level != null ? 0x9c0a0d10 : 0x70080b0e;
        if (ScreenBackground.draw(g, width, height, veil)) return;
        var texture = new ResourceLocation("battlefield_ops", "textures/gui/bf1/modify_background.png");
        double scale = Math.max(width / 1600.0, height / 900.0);
        int w = (int) Math.ceil(1600 * scale), h = (int) Math.ceil(900 * scale);
        g.blit(texture, (width - w) / 2, (height - h) / 2, w, h, 0, 0, 1600, 900, 1600, 900);
        g.fill(0, 0, width, height, veil);
    }

    static void lobbyBackground(GuiGraphics g,int width,int height) {
        var tex=new ResourceLocation("battlefield_ops","textures/gui/bf1/lobby_background.png");
        double scale=Math.max(width/(double)2048,height/(double)722);
        int w=(int)Math.ceil(2048*scale),h=(int)Math.ceil(722*scale);
        g.blit(tex,(width-w)/2,(height-h)/2,w,h,0,0,2048,722,2048,722);
        backdrop(g,width,height);
    }
    static void backdrop(GuiGraphics g, int width, int height) {
        g.fill(0,0,width,height,0x98101418);
        Ui.gradient(g,new Layout.Rect(0,0,width,Math.min(90,height)),0xe0080b0e,0x00101418);
        Ui.gradient(g,new Layout.Rect(0,Math.max(0,height-100),width,Math.min(100,height)),0x00101418,0xf0080b0e);
    }
    static void deployShade(GuiGraphics g, int width, int height) {
        Ui.gradient(g,new Layout.Rect(0,0,width,50),0xc0080b0e,0x00080b0e);
        Ui.gradient(g,new Layout.Rect(0,Math.max(0,height-120),width,Math.min(120,height)),0x00080b0e,0xe8080b0e);
    }
}
