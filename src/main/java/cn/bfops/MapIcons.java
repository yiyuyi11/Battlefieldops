package cn.bfops;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.function.DoubleToIntFunction;
import cn.bfops.conquest.client.marker.TacticalMarkerRenderer;
import cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType;
import cn.bfops.conquest.client.render.CaptureDiamondRenderer;

/** Shared native 656 icons. Coordinates follow the map; glyphs remain upright while it rotates. */
final class MapIcons {
    /** How long a vanished enemy stays on the map as a fading outline. */
    private static final long GHOST_MS = 2500L;

    private record Ghost(double x, double z, long at) { }

    /** Drops every after-image, so a new match or a map change never inherits old contacts. */
    static void clearGhosts() {
        GHOSTS.clear();
    }

    /** Last seen enemy positions, keyed by the mark's identity so a fresh sighting replaces its ghost. */
    private static final java.util.Map<String, Ghost> GHOSTS = new java.util.concurrent.ConcurrentHashMap<>();


    private MapIcons() {}
    static void upright(GuiGraphics g,int x,int y,double rotation,Runnable draw){
        g.pose().pushPose();
        try {
            g.pose().translate(x,y,0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)-rotation));
            g.pose().translate(-x,-y,0);
            draw.run();
        } finally {g.pose().popPose();}
    }
    static void nativeMarker(GuiGraphics g,float x,float y,float alpha,float shape,float scale,MarkerVisualType visual,String role){
        if(scale<=.01f||shape<=.01f)return;
        String art=switch(visual){
            case AMMO_BOX, AMMO_BAG, NEED_AMMO_ALLY, NEED_AMMO_SQUAD -> "pickups_ammobag_default_default";
            case MEDICAL_BOX, MEDICAL_BAG -> "pickups_medbag_default_default";
            case DOWNED_ALLY, DOWNED_SQUAD, RESCUE_ALLY, RESCUE_SQUAD -> "death_hud_default";
            case ALLY_PERSISTENT, SQUAD_PERSISTENT -> switch(role){
                case "assault" -> "kit_assault_hud_default";
                case "support" -> "kit_medic_hud_default";
                case "engineer" -> "kit_support_hud_default";
                case "recon" -> "kit_scout_hud_default";
                default -> "player_hud_default";
            };
            case ENEMY_GROUND, ENEMY_TRACKED, AUTO_ENEMY -> "player_map_enemy";
            default -> null;
        };
        if(art!=null){
            boolean squad=visual==MarkerVisualType.SQUAD_PERSISTENT||visual==MarkerVisualType.DOWNED_SQUAD||visual==MarkerVisualType.RESCUE_SQUAD;
            int color=squad?TacticalColors.MATE:visual==MarkerVisualType.ENEMY_TRACKED||visual==MarkerVisualType.ENEMY_GROUND||visual==MarkerVisualType.AUTO_ENEMY?TacticalColors.ENEMY:TacticalColors.FRIEND;
            int size=Math.max(8,Math.round((art.startsWith("kit_")?22:18)*scale));
            g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);
            try{Bf1Ui.icon(g,art,Math.round(x-size/2f),Math.round(y-size/2f),size);}finally{g.setColor(1,1,1,1);}
            return;
        }
        g.flush();RenderSystem.setShaderColor(1,1,1,1);
        try {TacticalMarkerRenderer.drawMarker(g,x,y,alpha,shape,0,false,visual,scale,-1,role,false);}
        finally {g.flush();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);}
    }
    static void capture(GuiGraphics g,Font f,String id,int x,int y,float progress,int owner,int self,boolean selected,double rotation){
        upright(g,x,y,rotation,()->DeployIcon.capture(g,f,id,x,y,6.5f,progress,1,owner,self,selected,false));
    }
    static void marker(GuiGraphics g,int x,int y,double rotation,MarkerVisualType type,String role,float alpha){
        upright(g,x,y,rotation,()->nativeMarker(g,x,y,alpha,.95f*alpha,.65f,type,role));
    }
    static MarkerVisualType supportType(String kind){
        return switch(kind){
            case "radio_beacon" -> MarkerVisualType.BEACON_FRIENDLY;
            case "ammo_box" -> MarkerVisualType.AMMO_BOX;
            case "med_box" -> MarkerVisualType.MEDICAL_BOX;
            case "ammo_bag" -> MarkerVisualType.AMMO_BAG;
            case "medic_bag" -> MarkerVisualType.MEDICAL_BAG;
            default -> null;
        };
    }
    static void site(GuiGraphics g,Font font,String kind,int x,int y,boolean selected,double rotation){
        upright(g,x,y,rotation,()->{
            switch(kind){
                case "BEACON" -> nativeMarker(g,x,y,1,.95f,.65f,MarkerVisualType.BEACON_FRIENDLY,"");
                case "TEAMMATE" -> nativeMarker(g,x,y,1,.95f,.65f,MarkerVisualType.SQUAD_PERSISTENT,"");
                case "VEHICLE" -> vehicle(g,font,x,y,true,selected);
                case "OBJECTIVE", "POINT", "CAPTURE" -> {} // already rendered with its ownership/progress
                default -> DeployIcon.base(g,font,x,y,6.5f,Client.state.has("me")?Engine.num(Client.state.getAsJsonObject("me"),"team",-1):-1,TacticalColors.FRIEND,selected);
            }
        });
    }
    private static void vehicle(GuiGraphics g,Font font,int x,int y,boolean friendly,boolean selected){
        int rgb=(friendly?TacticalColors.FRIEND:TacticalColors.ENEMY)&0xffffff;
        g.flush();RenderSystem.setShaderColor(1,1,1,1);
        try {g.setColor((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,1);
            Bf1Ui.icon(g,"vehicles_tank_default_default",x-7,y-7,14);g.setColor(1,1,1,1);
            if(selected)SelectionReticle.draw(g,x,y,9);}
        finally {g.flush();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);}
    }
    static void contacts(GuiGraphics g,Font font,JsonObject state,DoubleToIntFunction sx,DoubleToIntFunction sy,double rotation){contacts(g,font,state,sx,sy,rotation,null);}
    static void contacts(GuiGraphics g,Font font,JsonObject state,DoubleToIntFunction sx,DoubleToIntFunction sy,double rotation,MinimapOptions options){
        contacts(g,font,state,(wx,wz)->new int[]{sx.applyAsInt(wx),sy.applyAsInt(wz)},rotation,options);
    }
    static void contacts(GuiGraphics g,Font font,JsonObject state,java.util.function.BiFunction<Double,Double,int[]> project,double rotation,MinimapOptions options){
        contacts(g,font,state,project,rotation,options,0);
    }
    static void contacts(GuiGraphics g,Font font,JsonObject state,java.util.function.BiFunction<Double,Double,int[]> project,double rotation,MinimapOptions options,float headingOffset){
        for(var e:array(state,"hazards")){var row=e.getAsJsonObject();if(!position(row))continue;int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at!=null)marker(g,at[0],at[1],rotation,MarkerVisualType.GRENADE_HOSTILE,"",.95f);}
        if(options==null||options.objectives)for(var e:array(state,"elitePoints")){
            var row=e.getAsJsonObject();int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;int x=at[0],y=at[1];
            upright(g,x,y,rotation,()->HeroIcons.draw(g,Engine.str(row,"type",""),x,y,12,Engine.str(row,"reason","").isEmpty()?1:.4f));
        }
        if(options==null||options.support)for(var e:array(state,"supportDevices")){
            if(!e.isJsonObject())continue;var row=e.getAsJsonObject();if(!position(row))continue;
            int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;int x=at[0],y=at[1];
            String kind=Engine.str(row,"kind","");var type=supportType(kind);
            if(type!=null)marker(g,x,y,rotation,type,"",1);
            else upright(g,x,y,rotation,()->SupportMarkers.drawIcon(g,kind,x,y,5));
        }
        if(options==null||options.vehicles)for(var e:array(state,"vehicles")){
            if(!e.isJsonObject())continue;var row=e.getAsJsonObject();if(!position(row))continue;
            int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;int x=at[0],y=at[1];
            upright(g,x,y,rotation,()->{
                boolean friendly=bool(row,"friendly");
                vehicle(g,font,x,y,friendly,false);
                if(row.has("occupants")&&row.get("occupants").getAsInt()>0)
                    g.fill(x-2,y+7,x+3,y+8,friendly?TacticalColors.FRIEND:TacticalColors.ENEMY);
                if(friendly&&row.has("health")&&row.get("health").getAsFloat()<.35f)
                    TacticalColors.label(g,font,"!",x+8,y-8,TacticalColors.MARK);
            });
        }
        java.util.Set<String> liveEnemies=new java.util.HashSet<>();
        for(var e:array(state,"marks")){
            if(!e.isJsonObject())continue;var row=e.getAsJsonObject();if(!position(row))continue;
            int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;int px=at[0],py=at[1];
            boolean enemy="ENEMY".equalsIgnoreCase(Engine.str(row,"kind","POSITION"));
            if(enemy){
                // Remember where an enemy was last seen: the server drops the mark on expiry, and a map that
                // forgets instantly plays worse than one that keeps a fading trace for a couple of seconds.
                String key=Engine.str(row,"target",Engine.str(row,"name",px+":"+py));
                liveEnemies.add(key);
                GHOSTS.put(key,new Ghost(row.get("x").getAsDouble(),row.get("z").getAsDouble(),System.currentTimeMillis()));
            }
            if(enemy)marker(g,px,py,rotation,MarkerVisualType.ENEMY_TRACKED,"",Minimap.fade(row));
            else TacticalColors.dot(g,px,py,2,TacticalColors.withAlpha(TacticalColors.FRIEND,Minimap.fade(row)));
        }
        long now=System.currentTimeMillis();
        GHOSTS.entrySet().removeIf(entry->now-entry.getValue().at()>GHOST_MS);
        for(var entry:GHOSTS.entrySet()){
            if(liveEnemies.contains(entry.getKey()))continue;
            var ghost=entry.getValue();
            double left=1.0-(now-ghost.at())/(double)GHOST_MS;
            if(left<=0.02)continue;
            // Hollow, dimmer square: clearly "no longer there", not a second live contact.
            int[] ghostAt=project.apply(ghost.x(),ghost.z());if(ghostAt==null)continue;
            int colour=TacticalColors.withAlpha(0xffa64b50,(float)(left*0.55));
            g.fill(ghostAt[0]-1,ghostAt[1]-1,ghostAt[0]+2,ghostAt[1],colour);
            g.fill(ghostAt[0]-1,ghostAt[1]+1,ghostAt[0]+2,ghostAt[1]+2,colour);
            g.fill(ghostAt[0]-1,ghostAt[1],ghostAt[0],ghostAt[1]+1,colour);
            g.fill(ghostAt[0]+1,ghostAt[1],ghostAt[0]+2,ghostAt[1]+1,colour);
        }
        for(var e:array(state,"pads")){
            if(!e.isJsonObject())continue;var row=e.getAsJsonObject();if(!position(row))continue;
            int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;
            long readyIn=Engine.num(row,"readyIn",0);
            if(readyIn<=0)continue;
            // Countdown under the pad, so "why is nothing happening" is answerable at a glance.
            TacticalColors.label(g,font,readyIn+"s",at[0]-4,at[1]+7,0xffd9a566);
        }
        for(var e:array(state,"mates")){
            if(!e.isJsonObject())continue;var row=e.getAsJsonObject();if(!position(row)||bool(row,"self")||bool(row,"hostile"))continue;
            if(row.has("alive")&&!bool(row,"alive")&&!bool(row,"downed"))continue;
            boolean squad=bool(row,"squad"),down=bool(row,"downed");
            var type=bool(row,"hostile")?MarkerVisualType.ENEMY_TRACKED:down?(squad?MarkerVisualType.DOWNED_SQUAD:MarkerVisualType.DOWNED_ALLY):(squad?MarkerVisualType.SQUAD_PERSISTENT:MarkerVisualType.ALLY_PERSISTENT);
            String role=MateMarker.stem(Engine.str(row,"kit",""));
            int[] at=project.apply(row.get("x").getAsDouble(),row.get("z").getAsDouble());if(at==null)continue;int x=at[0],y=at[1];
            String elite=Engine.str(row,"elite","");
            if(!bool(row,"hostile")&&!down&&EliteRules.Kind.of(elite)!=null)upright(g,x,y,rotation,()->HeroIcons.draw(g,elite,x,y,11,1));
            else if(!down){float yaw=row.has("yaw")?row.get("yaw").getAsFloat():0;
                // The map pose already carries its rotation, so preserve world heading for player masks.
                Bf1Players.draw(g,x,y,yaw+headingOffset,false,squad,1,PlayerMarkerSize.ALLY);
            }else marker(g,x,y,rotation,type,role==null?"":role,1);
            if(options!=null&&options.names&&!bool(row,"hostile"))upright(g,x,y,rotation,()->TacticalColors.label(g,font,Engine.str(row,"name",""),x,y+11,squad?TacticalColors.SELF:TacticalColors.FRIEND));
        }
    }
    private static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    private static boolean position(JsonObject o){return o.has("x")&&o.has("z");}
    static JsonArray array(JsonObject o,String key){return o!=null&&o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
}
