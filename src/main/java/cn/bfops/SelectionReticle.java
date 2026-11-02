package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
/** Four separated crosshair arms confirm the selected target without covering its icon. */
final class SelectionReticle {
    static int[][] arms(int x,int y,int radius){int r=Math.max(8,radius);return new int[][]{{x-r-8,y,x-r-2,y},{x+r+2,y,x+r+8,y},{x,y-r-8,x,y-r-2},{x,y+r+2,x,y+r+8}};}
    static void draw(GuiGraphics g,int x,int y,int radius){for(var line:arms(x,y,radius)){Ui.line(g,line[0],line[1],line[2],line[3],0xf0000000,3);Ui.line(g,line[0],line[1],line[2],line[3],TacticalColors.SELF,1);}}
}
