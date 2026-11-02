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
        draw(gui,()->CaptureDiamondRenderer.drawCaptureIconInHud(gui,font,glyph,false,x,y,radius,
                MarkerVisualRules.displayProgress(rawProgress,selfTeam),alpha,MarkerVisualRules.teamToken(owner,selfTeam),
                "NEUTRAL",.68f,.5f,selected,hovered));
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
    private static void draw(GuiGraphics gui,Runnable action){
        gui.flush();RenderSystem.setShaderColor(1,1,1,1);
        try{action.run();}finally{gui.flush();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);}
    }
}
