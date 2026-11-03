package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector3f;
/** GuiGraphics 1.21.1 scissors use screen coordinates, independently of the pose matrix. */
final class GuiClip {
    static void push(GuiGraphics g,int x,int y,int width,int height){
        var bounds=bounds(g.pose().last().pose(),x,y,width,height);
        g.flush();g.enableScissor(bounds[0],bounds[1],bounds[2],bounds[3]);
    }
    static int[] bounds(org.joml.Matrix4f pose,int x,int y,int width,int height){
        float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
        for(int dx:new int[]{0,width})for(int dy:new int[]{0,height}){
            var point=pose.transformPosition(new Vector3f(x+dx,y+dy,0));
            left=Math.min(left,point.x);right=Math.max(right,point.x);
            top=Math.min(top,point.y);bottom=Math.max(bottom,point.y);
        }
        return new int[]{(int)Math.floor(left),(int)Math.floor(top),(int)Math.ceil(right),(int)Math.ceil(bottom)};
    }
    static void pop(GuiGraphics g){g.flush();g.disableScissor();}
}
