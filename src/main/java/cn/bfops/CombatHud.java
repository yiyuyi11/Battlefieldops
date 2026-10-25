package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Match-only combat presentation; the full roster belongs to the held F6 overlay. */
final class CombatHud {
    static int mapSize(int width,int height){return Math.max(64,Math.min(116,Math.min(width/3-12,height/2-16)));}
    static boolean active(Model.Arena arena,Model.Point point,int stage){return !arena.mode.equals("BREAKTHROUGH")||point.sector==stage;}
    static void draw(GuiGraphics g,TerrainPreview terrain){
        var mc=Minecraft.getInstance();JsonObject state=Client.state,me=state.getAsJsonObject("me");
        Model.Arena arena=BattlefieldOps.GSON.fromJson(state.get("arena"),Model.Arena.class);
        int width=g.guiWidth(),height=g.guiHeight(),bar=Math.min(260,width-16),x=(width-bar)/2;
        int[] tickets=BattlefieldOps.GSON.fromJson(state.get("tickets"),int[].class);
        if(tickets==null||tickets.length<2)return;
        boolean breakthrough=arena.mode.equals("BREAKTHROUGH");
        g.fill(x,6,x+bar,28,0xc0152027);
        int half=(bar-16)/2;
        g.fill(x+4,25,x+4+(int)(half*Math.min(1,tickets[0]/(double)Math.max(1,arena.tickets))),27,0xff65b5fa);
        if(!breakthrough)g.fill(x+bar-4-(int)(half*Math.min(1,tickets[1]/(double)Math.max(1,arena.tickets))),25,x+bar-4,27,0xffef7272);
        String right=breakthrough?"防守方":"红方 "+tickets[1];
        g.drawString(mc.font,"蓝方 "+tickets[0],x+6,11,0x83bfff,false);
        g.drawString(mc.font,right,x+bar-6-mc.font.width(right),11,0xff9999,false);
        long remaining=Math.max(0,arena.roundMinutes*60L-Engine.num(state,"elapsed",0));
        g.drawCenteredString(mc.font,String.format(java.util.Locale.ROOT,"%02d:%02d",remaining/60,remaining%60),width/2,11,0xffffff);
        int stage=Engine.num(state,"stage",0);
        JsonArray flags=state.has("flags")?state.getAsJsonArray("flags"):new JsonArray();
        var indices=new java.util.ArrayList<Integer>();
        for(int i=0;i<arena.points.size()&&i<flags.size();i++)if(active(arena,arena.points.get(i),stage))indices.add(i);
        int columns=Math.max(1,bar/30),count=Math.min(columns,indices.size());
        for(int n=0;n<indices.size();n++){
            int i=indices.get(n),row=n/columns,col=n%columns,px=(width-count*30)/2+col*30,py=32+row*22;
            var f=flags.get(i).getAsJsonObject();int owner=Engine.num(f,"owner",-1),color=TacticalColors.objective(owner,Engine.num(me,"team",-1));
            g.fill(px,py,px+26,py+18,0xb0152027);g.fill(px,py+16,px+26,py+18,color);
            g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(arena.points.get(i).id,22),px+13,py+3,color);
            if(f.has("contested")&&f.get("contested").getAsBoolean())g.drawString(mc.font,"!",px+23,py-2,0xffdd77,false);
        }
        JsonObject hud=state.has("hud")?state.getAsJsonObject("hud"):new JsonObject();
        boolean allowed=!hud.has("minimap")||hud.get("minimap").getAsBoolean();
        boolean xaero=hud.has("xaero")&&hud.get("xaero").getAsBoolean(),hidden=hud.has("hideXaero")&&hud.get("hideXaero").getAsBoolean();
        // The player's own choice wins over the server default, and the toggle hides it outright. Checked
        // before the size computation so a hidden minimap costs nothing to draw.
        if(Briefing.showHudMinimap(allowed,xaero,hidden) && !ClientPrefs.minimapHidden()){
            int size=mapSize(width,height);
            // Position comes from the operator's setting, defaulting to the top-left. Computed by a pure
            // helper so the "half off screen" and "on top of the ticket bar" cases are covered by tests
            // rather than by whoever happens to be playing at a given window size.
            String corner=ClientPrefs.corner(Engine.str(hud,"minimapCorner","TOP_LEFT"));
            int[] origin=Briefing.minimapOrigin(corner,width,height,size);
            int mapX=origin[0],mapY=origin[1];
            // The in-match minimap is centred on the player, so it is mostly covered by loaded chunks —
            // but the parts of the map it reaches past the view distance still need the bake.
            Client.wantTerrain(arena.id);
            Minimap map=Minimap.local(mapX,mapY,size,mc.player.getX(),mc.player.getZ(),256).terrain(terrain);
            map.draw(g,mc.font,arena,state,Engine.num(me,"team",-1),mc.player.getX(),mc.player.getZ(),mc.player.getYRot(),false);
            g.fill(mapX-2,mapY+size+3,mapX+mc.font.width("青自己 蓝友 红敌")+2,mapY+size+15,0xe0000000);
            g.drawString(mc.font,"青自己 蓝友 红敌",mapX,mapY+size+5,0xa3c8dc,false);
        }
        // Downed state: show the bleed-out clock without obscuring crawling or aiming.
        if(me.has("downed")&&me.get("downed").getAsBoolean()){
            int left=Engine.num(state,"downLeft",0);
            g.drawCenteredString(mc.font,"你倒下了",width/2,height/2-40,0xffff6b6b);
            g.drawCenteredString(mc.font,"出血倒计时 "+left+"s · 队友空手按住右键救援",
                    width/2,height/2-26,0xffffd479);
            int barW=120, filled=(int)Math.round(barW*Math.max(0,Math.min(1,left/(double)Math.max(1,arena.downSeconds))));
            g.fill(width/2-barW/2,height/2-12,width/2+barW/2,height/2-8,0xff000000);
            g.fill(width/2-barW/2,height/2-12,width/2-barW/2+filled,height/2-8,0xffff6b6b);
        }
        if (me.has("downed") && me.get("downed").getAsBoolean()) {
            double fraction = Medic.fraction(Engine.num(state,"reviveProgress",0), Medic.reviveTicks(arena.reviveSeconds));
            if (fraction > 0) {
                int px=width/2-60, py=height/2+4;
                g.fill(px-3,py-3,px+123,py+21,0xe0080d12);
                g.drawCenteredString(mc.font,"正在救援 "+(int)(fraction*100)+"%",width/2,py,0xff65dbe8);
                g.fill(px,py+13,px+(int)(120*fraction),py+17,0xff65dbe8);
            }
        }
        int outside=Engine.num(me,"outside",0);
        if(outside>0)g.drawCenteredString(mc.font,"离开战区 · "+Math.max(0,arena.outsideSeconds-outside)+" 秒后退出",width/2,Math.max(92,height/2-30),0xff9a70);
        if(!me.get("alive").getAsBoolean())g.drawCenteredString(mc.font,"部署等待 "+Engine.num(state,"respawnRemaining",0)+"s · "+Client.OPEN.getTranslatedKeyMessage().getString(),width/2,height/2+22,0xffdd88);
        String hint=Engine.str(state,"objectiveHint","");
        if(!hint.isEmpty())g.drawCenteredString(mc.font,hint,width/2,height-76,0xffffca77);
        // Only the objective the player is actually standing in gets a capture detail line.
        for(int i:indices){var p=arena.points.get(i);if(!me.get("alive").getAsBoolean()||Engine.num(state,"objective",-1)!=i)continue;
            var f=flags.get(i).getAsJsonObject();int progress=Engine.num(f,"progress",50);if(Engine.num(me,"team",-1)==1)progress=100-progress;int px=width/2-52,py=height-67;
            g.fill(px,py,px+104,py+20,0xc0152027);g.drawCenteredString(mc.font,p.id+" "+(f.has("contested")&&f.get("contested").getAsBoolean()?"争夺中":Engine.num(f,"owner",-1)==Engine.num(me,"team",-1)?"我方控制":"占领中"),width/2,py+3,0xffffff);
            g.fill(px+2,py+15,px+2+progress,py+18,TacticalColors.FRIEND);g.fill(px+2+progress,py+15,px+102,py+18,TacticalColors.ENEMY);break;}
    }
}
