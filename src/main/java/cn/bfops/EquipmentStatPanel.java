package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
final class EquipmentStatPanel {
    private EquipmentStatPanel(){}
    /** Source 656 property diagram: label, base bar plus modifier segment, current value. */
    static void diagram(GuiGraphics g,Font font,Layout.Rect r,EquipmentStatistics.Row value){
        int color=value.changed()?(value.improved()?0xff55ddaa:0xffff7777):0xffd7d7d7;
        int labelW=Math.max(36,(int)(r.w()*.29)),valueW=Math.max(36,(int)(r.w()*.26));
        int barX=r.x()+labelW,barW=Math.max(4,r.w()-labelW-valueW-8),y=r.y()+5;
        g.drawString(font,Ui.clip(font,value.title(),labelW-5),r.x()+2,r.y()+3,Ui.TEXT_DIM,true);
        g.fill(barX,y,barX+barW,y+5,0xc0000000);
        int base=(int)Math.round(barW*value.baseBar()),current=(int)Math.round(barW*value.currentBar());
        g.fill(barX,y,barX+base,y+5,0xffd7d7d7);
        if(value.changed())g.fill(barX+Math.min(base,current),y,barX+Math.max(base,current),y+5,color);
        String text=value.after();
        g.drawString(font,Ui.clip(font,text,valueW),barX+barW+5,r.y()+3,color,true);
    }
    static void row(GuiGraphics g,Font font,Layout.Rect r,EquipmentStatistics.Row value,boolean compact){
        int color=value.changed()?value.improved()?0xff59ddc8:0xffff947c:Ui.TEXT_DIM;
        g.drawString(font,Ui.clip(font,value.title(),Math.max(8,r.w()-8)),r.x()+4,r.y()+2,Ui.ACCENT,true);
        String text=value.before()+" → "+value.after();
        g.drawString(font,Ui.clip(font,text,Math.max(8,r.w()-8)),r.x()+4,r.y()+13,color,true);
        int y=r.y()+25,w=Math.max(1,r.w()-8);g.fill(r.x()+4,y,r.x()+4+w,y+5,0xff15232e);
        g.fill(r.x()+4,y,r.x()+4+(int)Math.round(w*value.baseBar()),y+2,0xff8197a5);
        g.fill(r.x()+4,y+3,r.x()+4+(int)Math.round(w*value.currentBar()),y+5,color);
    }
    static void preview(GuiGraphics g,Font font,Layout.Rect body,String kind,String id,String name,Map<String,String> choices){
        preview(g,font,body,kind,id,name,choices,30f,-15f);
    }
    /** Same preview, with the gun model's user-controlled yaw/pitch so the bench model can be rotated. */
    static void preview(GuiGraphics g,Font font,Layout.Rect body,String kind,String id,String name,Map<String,String> choices,float yaw,float pitch){
        var report=EquipmentStatistics.read(kind,id,choices);
        // Square preview box at the top of the pane; the stats start below it, so the model can never overlap them.
        int image=Math.max(56,Math.min(120,body.h()/3));
        int boxX=body.x()+(body.w()-image)/2;
        if(kind.equals("gun"))drawGunModel(g,id,boxX,body.y()+3,image,image,yaw,pitch,choices);
        else AssetIcons.draw(g,kind,id,body.x()+6,body.y()+3,body.w()-12,image);
        int y=body.y()+image+7;g.drawString(font,Ui.clip(font,name,body.w()-8),body.x()+4,y,Ui.TEXT,true);y+=14;
        g.drawString(font,"原始 → 当前 · 绿优 / 橙劣",body.x()+4,y,Ui.TEXT_DIM,true);y+=12;
        if(report.rows().isEmpty())g.drawWordWrap(font,net.minecraft.network.chat.Component.literal(report.warning()),body.x()+4,y,Math.max(8,body.w()-8),Ui.TEXT_DIM);
        else for(var value:report.rows()){if(y+32>body.bottom())break;row(g,font,new Layout.Rect(body.x(),y,body.w(),32),value,true);y+=34;}
    }

    /** Draws the gun (with its selected attachments) through TaCZ's own item renderer, scaled into the box. */
    private static void drawGunModel(GuiGraphics g,String id,int x,int y,int w,int h,float yaw,float pitch,Map<String,String> choices){
        ItemStack gun=GunPreview.assembled(id,choices);
        if(gun.isEmpty()){AssetIcons.draw(g,"gun",id,x,y,w,h);return;}
        // Attachments are written onto the display stack so the bench shows the configured gun, and the item
        // renderer supplies the correct model and texture (the Bedrock path was drawing it untextured/red).
        float scale=Math.min(w,h)/16f;
        g.pose().pushPose();
        try{
            g.pose().translate(x+w/2f,y+h/2f,120);
            g.pose().scale(scale,scale,1);
            g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
            g.renderItem(gun,-8,-8);
        }finally{g.pose().popPose();}
    }

    /** Builds a display-only gun stack through TaCZ's public builder (optional dependency, so reflective). */
    private static ItemStack buildGun(String id){
        try{
            var level=net.minecraft.client.Minecraft.getInstance().level;
            if(level==null)return ItemStack.EMPTY;
            Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            Object instance=builder.getMethod("create").invoke(null);
            builder.getMethod("setId",net.minecraft.resources.ResourceLocation.class)
                    .invoke(instance,net.minecraft.resources.ResourceLocation.parse(id));
            return (ItemStack)builder.getMethod("build",net.minecraft.core.HolderLookup.Provider.class)
                    .invoke(instance,level.registryAccess());
        }catch(Throwable ex){return ItemStack.EMPTY;}
    }
}
