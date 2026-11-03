package cn.bfops;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.conquest.client.render.CaptureDiamondRenderer;

/** Coordinate/score adapter around the original 656 renderer, not another geometry implementation. */
final class DeployIcon {
    private DeployIcon(){}
    static void capture(GuiGraphics gui,Font font,String glyph,float x,float y,float radius,
                        float rawProgress,float alpha,int owner,int selfTeam,boolean selected,boolean hovered){
        if(alpha<=.01f)return;
        draw(gui,()->CaptureDiamondRenderer.drawCaptureIconInHud(gui,font,"",false,x,y,radius,
                MarkerVisualRules.displayProgress(rawProgress,selfTeam),alpha,MarkerVisualRules.teamToken(owner,selfTeam),
                "NEUTRAL",.68f,.5f,selected,hovered));
        pointLetter(gui,font,glyph,x,y,radius,alpha);
    }
    static void spawn(GuiGraphics gui,Font font,String glyph,float x,float y,float radius,
                      float alpha,int color,boolean selected,boolean hovered){
        if(alpha<=.01f)return;
        String token=(color&0xffffff)==(TacticalColors.ENEMY&0xffffff)?"ENEMY":"ALLY";
        draw(gui,()->CaptureDiamondRenderer.drawCaptureIconInHud(gui,font,glyph,true,x,y,radius,
                100,alpha,token,"NEUTRAL",.68f,.5f,selected,hovered));
    }
    static void teammate(GuiGraphics gui,Font font,float x,float y,float radius,float yaw,
                         float alpha,int color,int symbolRgb,boolean selected,boolean hovered){
        if(alpha<=.01f)return;
        draw(gui,()->{
            gui.pose().pushPose();
            try{
                gui.pose().translate(x,y,0);gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-yaw));
                CaptureDiamondRenderer.drawCaptureIconInHud(gui,font,"",true,0,0,radius,100,alpha,"ALLY","NEUTRAL",.48960003f,.5f,selected,hovered);
                CaptureDiamondRenderer.drawCenteredHudText(gui,font,"▽",0,-2.3f,symbolRgb,alpha,true,.75f);
            }finally{gui.pose().popPose();}
        });
    }
    static void queue(GuiGraphics gui,Font font,String glyph,CaptureQueue.Slot slot,float rawProgress,int owner,int self){
        draw(gui,()->CaptureDiamondRenderer.drawCaptureIconInHud(gui,font,glyph,false,slot.x(),slot.y(),slot.radius(),
                MarkerVisualRules.displayProgress(rawProgress,self),slot.alpha(),MarkerVisualRules.teamToken(owner,self),"NEUTRAL",slot.textScale(),1,false,false));
    }
    private static void pointLetter(GuiGraphics g,Font font,String glyph,float x,float y,float radius,float alpha){
        int size=Math.max(4,Math.round(radius*.85f));
        if(glyph!=null&&glyph.matches("[A-Ha-h]")){
            RenderSystem.enableBlend();g.setColor(1,1,1,alpha);
            try{Bf1Ui.icon(g,"objectives_"+glyph.toLowerCase(java.util.Locale.ROOT)+"_hud_default",Math.round(x-size/2f),Math.round(y-size/2f),size);}
            finally{g.setColor(1,1,1,1);}
        }else if(glyph!=null)g.drawCenteredString(font,glyph,Math.round(x),Math.round(y)-4,((int)(alpha*255)<<24)|0xffffff);
    }
    static void base(GuiGraphics g,Font font,float x,float y,float radius,int team,int color,boolean selected){
        spawn(g,font,"",x,y,radius,1,color,selected,false);
        int size=Math.max(14,Math.round(radius*2.3f));
        Bf1Ui.icon(g,FactionArt.icon(team),Math.round(x-size/2f),Math.round(y-size/2f),size);
    }
    private static void draw(GuiGraphics gui,Runnable action){
        gui.flush();RenderSystem.setShaderColor(1,1,1,1);
        try{action.run();}finally{gui.flush();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);}
    }
}
