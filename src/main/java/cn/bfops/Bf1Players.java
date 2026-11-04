package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
/** Original BF1 player-mask canvas: heading zero in Minecraft points south (180 degrees from texture north). */
final class Bf1Players {
 static final int SELF=0xffffd23f,ALLY=0xff619fea,SQUAD=0xff6efb91;
 static void draw(GuiGraphics g,int x,int y,float heading,boolean self,boolean squad,float alpha,int size){
  int color=self?SELF:squad?SQUAD:ALLY;
  g.pose().pushPose();
  try{
   g.pose().translate(x,y,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(heading+180));
   g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);
   Bf1Ui.icon(g,self?"player_map_self_mask":"player_map_default_mask",-size/2,-size/2,size);
  }finally{g.setColor(1f,1f,1f,1f);g.pose().popPose();}
 }
}
