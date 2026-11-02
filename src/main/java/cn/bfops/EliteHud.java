package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.google.gson.*;

final class EliteHud {
    static void draw(GuiGraphics g,Minecraft mc,JsonObject state){
        if(!state.has("me")||mc.player==null)return;var me=state.getAsJsonObject("me");
        String active=Engine.str(me,"eliteType","");
        if(!active.isEmpty()&&!mc.player.isPassenger()&&!ClientPrefs.corner(state.has("hud")?Engine.str(state.getAsJsonObject("hud"),"minimapCorner","TOP_LEFT"):"TOP_LEFT").equals("TOP_RIGHT"))
            HeroIcons.draw(g,active,g.guiWidth()-16,18,12,1);
        if(!me.has("alive")||!me.get("alive").getAsBoolean()||me.has("downed")&&me.get("downed").getAsBoolean()||!state.has("elitePoints"))return;
        JsonObject nearest=null;double best=Double.MAX_VALUE;
        for(var e:state.getAsJsonArray("elitePoints")){
            var row=e.getAsJsonObject();double d=mc.player.distanceToSqr(row.get("x").getAsDouble(),row.get("y").getAsDouble(),row.get("z").getAsDouble());
            double radius=row.get("radius").getAsDouble();if(d<=radius*radius&&d<best){best=d;nearest=row;}
        }
        if(nearest!=null){
            String reason=Engine.str(nearest,"reason","");
            String text=reason.isEmpty()?"["+Client.ELITE_PICKUP.getTranslatedKeyMessage().getString()+"] 领取 "+Engine.str(nearest,"name",""):reason;
            int y=g.guiHeight()/2+44;
            HeroIcons.draw(g,Engine.str(nearest,"type",""),g.guiWidth()/2f,y-10,12,1);
            TacticalColors.label(g,mc.font,text,g.guiWidth()/2,y,reason.isEmpty()?TacticalColors.SELF:TacticalColors.NEUTRAL);
        }
    }
}
