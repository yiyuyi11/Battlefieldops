package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.function.DoubleToIntFunction;

final class SupportMarkers {
    /**
     * One icon per support-device kind, shared by the minimap and the in-world overlay so the two
     * surfaces cannot disagree about what a beacon looks like.
     *
     * <p>The box kinds use the reference pack's own 256x256 marker art ({@link AssetIcons#supply}); the
     * beacon uses the reference's {@code hud_beacon.png} tinted friendly blue, and the ground sensor its
     * pre-coloured {@code hud_ground_sensor.png}. A shape fallback survives a missing texture.
     */
    static boolean drawIcon(GuiGraphics g,String kind,int cx,int cy,int size){return drawIcon(g,kind,cx,cy,size,1);}
    static boolean drawIcon(GuiGraphics g,String kind,int cx,int cy,int size,float opacity){
        if(opacity<=.01f)return true;
        var mc=Minecraft.getInstance();
        if(mc==null)return false;
        if(kind.equals("radio_beacon")){
            ResourceLocation loc=new ResourceLocation("battlefield_ops","textures/gui/hud_beacon.png");
            if(mc.getResourceManager().getResource(loc).isEmpty())return false;
            int tint=TacticalColors.FRIEND;
            g.setColor(((tint>>16)&0xff)/255f,((tint>>8)&0xff)/255f,(tint&0xff)/255f,opacity);
            int d=Math.max(8,size*2);
            g.blit(loc,cx-d/2,cy-d/2,d,d,0,0,128,128,128,128);
            g.setColor(1f,1f,1f,1f);
            return true;
        }
        if(kind.equals("ground_sensor")){
            ResourceLocation loc=new ResourceLocation("battlefield_ops","textures/gui/hud_ground_sensor.png");
            if(mc.getResourceManager().getResource(loc).isEmpty())return false;
            // Pre-coloured cyan art: drawn 1:1, no tint, no fractional scale.
            g.setColor(1f,1f,1f,opacity);g.blit(loc,cx-8,cy-8,16,16,0,0,16,16,16,16);g.setColor(1f,1f,1f,1f);
            return true;
        }
        if(kind.equals("ammo_box")||kind.equals("med_box")){
            g.setColor(.4f,.72f,.98f,opacity);
            boolean drawn=AssetIcons.supply(g,kind.equals("med_box")?"MEDICAL":"AMMO",cx,cy,size);
            g.setColor(1f,1f,1f,1f);
            return drawn;
        }
        return false;
    }

    static void draw(GuiGraphics g,JsonObject state,DoubleToIntFunction sx,DoubleToIntFunction sz){
        if(state==null||!state.has("supportDevices"))return;
        for(var row:state.getAsJsonArray("supportDevices")){
            var item=row.getAsJsonObject();int x=sx.applyAsInt(item.get("x").getAsDouble()),z=sz.applyAsInt(item.get("z").getAsDouble());
            String kind=Engine.str(item,"kind","");
            if(!drawIcon(g,kind,x,z,5))Ui.handle(g,x,z,3,TacticalColors.FRIEND);
        }
    }
}
