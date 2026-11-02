package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;

final class DeploymentGlyph {
    static void draw(GuiGraphics g,String kind,int x,int y,int size,int color){
        if("VEHICLE".equals(kind)){
            Ui.rectOutline(g,new Layout.Rect(x-size,y-size,size*2+1,size*2+1),color);
        }else if("BEACON".equals(kind)){
            // The reference pack's own beacon art (a tinted glow icon) when present; the halo-and-bar
            // shape stays as the fallback so the deploy marker survives a missing texture.
            if(!SupportMarkers.drawIcon(g,"radio_beacon",x,y,size)){
                TacticalColors.halo(g,x,y,size+2,color);
                g.fill(x-1,y-size,x+2,y+size+1,color);
            }
        }else if("TEAMMATE".equals(kind)){
            TacticalColors.halo(g,x,y,size+1,color);TacticalColors.dot(g,x,y,2,color);
        }else{
            g.fill(x-1,y-size,x+2,y+size+1,color);g.fill(x-size,y-1,x+size+1,y+2,color);
        }
    }
}
