package cn.bfops;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** The same reference elite art for editor, map, world markers and the current-role HUD. */
final class HeroIcons {
    static void draw(GuiGraphics g,String type,float x,float y,int size,float alpha){
        if(EliteRules.Kind.of(type)==null||alpha<=.01f)return;
        g.flush();RenderSystem.setShaderColor(1,1,1,1);
        int left=Math.round(x)-size/2,top=Math.round(y)-size/2;
        g.fill(left-1,top-1,left+size+1,top+size+1,(Math.round(alpha*88)<<24)|0x08121c);
        g.setColor(.4f,.72f,.98f,alpha);
        try{AssetIcons.draw(g,"elite",type,Math.round(x)-size/2,Math.round(y)-size/2,size,size);}
        finally{g.flush();g.setColor(1f,1f,1f,1f);RenderSystem.setShaderColor(1,1,1,1);}
    }
}
