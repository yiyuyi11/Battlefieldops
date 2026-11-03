package cn.bfops;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import cn.bfops.conquest.client.marker.TacticalMarkerRenderer;
import cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType;
import cn.bfops.conquest.client.compat.ClientWeaponAimCompat;

/** Original 656 icon drawing with our server's room-authorised snapshots and progress convention. */
final class WorldMarkers {
    /**
     * World markers stay out of the way unless you are looking at them: a device only draws when it is
     * within about ten degrees of the crosshair, or when it is close enough to matter.
     */
    private static boolean aimed(Minecraft mc, net.minecraft.client.Camera camera, double x, double y, double z) {
        var pos = camera.getPosition();
        double dx = x - pos.x, dy = y - pos.y, dz = z - pos.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 6.0) return true;
        var look = camera.getLookVector();
        double dot = (dx * look.x + dy * look.y + dz * look.z) / length;
        return dot > 0.985;
    }


    private static final double MAX_DISTANCE=512;
    private static final MarkerAnimation ANIMATION=new MarkerAnimation();
    static void reset(){ANIMATION.reset();}
    static void draw(GuiGraphics g,Minecraft mc){
        JsonObject state=Client.state;
        if(state==null||!state.has("inMatch")||!state.get("inMatch").getAsBoolean()||!state.has("arena")||!state.has("me"))return;
        if(state.has("hud")&&state.getAsJsonObject("hud").has("markers")&&!state.getAsJsonObject("hud").get("markers").getAsBoolean())return;
        Model.Arena arena=FactionArt.arena();
        Camera camera=Projector.camera(mc);if(arena==null||camera==null)return;
        int self=Engine.num(state.getAsJsonObject("me"),"team",-1),stage=Engine.num(state,"stage",0);
        if(self<0)return;
        ANIMATION.begin(arena.dimension+":"+arena.id+":"+self,System.nanoTime()/1_000_000);
        ClientWeaponAimCompat.tickFade(mc,ANIMATION.delta());
        float aim=ClientWeaponAimCompat.fadeAlpha();
        JsonArray flags=state.has("flags")?state.getAsJsonArray("flags"):new JsonArray();
        for(int i=0;i<arena.points.size();i++){
            var point=arena.points.get(i);if(!CombatHud.active(arena,point,stage))continue;
            var at=project(mc,camera,point.x,point.y+3.5,point.z);if(at==null)continue;
            float x=Math.max(9.5f,Math.min(g.guiWidth()-9.5f,at.x())),y=Math.max(9.5f,Math.min(g.guiHeight()-9.5f,at.y()));
            var flag=i<flags.size()?flags.get(i).getAsJsonObject():new JsonObject();int owner=Engine.num(flag,"owner",-1);
            float progress=flag.has("progress")?flag.get("progress").getAsFloat():50;
            float alpha=ANIMATION.alpha("point:"+point.id,MarkerVisualRules.alpha(g.guiWidth(),g.guiHeight(),x,y,Cinematic.active())*aim);
            DeployIcon.capture(g,mc.font,point.id,x,y,7.5f,progress,alpha,owner,self,false,false);
            focusLabel(g,mc,point.id+" · "+distance(camera,point.x,point.y,point.z)+"m",x,y,alpha,TacticalColors.objective(owner,self));
        }
        // Only fixed bases: owned objectives and dynamic sites already have their own markers.
        if(state.has("deploySites"))for(var item:state.getAsJsonArray("deploySites")){
            var site=item.getAsJsonObject();String kind=Engine.str(site,"kind","BASE");
            if(!kind.equals("BASE")&&!kind.equals("AIR"))continue;
            spawn(g,mc,camera,site.get("x").getAsDouble(),site.get("y").getAsDouble()+2.8,site.get("z").getAsDouble(),Engine.str(site,"id",""),Engine.str(site,"label","出生点"),aim);
        }else for(var site:arena.spawns){if(site.team==self&&(!arena.isStaged()||site.sector==stage))spawn(g,mc,camera,site.x,site.y+2.8,site.z,site.id,site.id,aim);}
        for(var pad:arena.supplyPoints()){
            if(pad.team()>=0&&pad.team()!=self)continue;
            var at=project(mc,camera,pad.x(),pad.y()+2,pad.z());if(at==null)continue; if(!aimed(mc,camera,pad.x(),pad.y()+2,pad.z()))continue;
            tactical(g,at.x(),at.y(),1,.9f,1,pad.health()?MarkerVisualType.MEDICAL_BOX:MarkerVisualType.AMMO_BOX,"");
            focusLabel(g,mc,"补给 "+pad.id()+" · "+distance(camera,pad.x(),pad.y(),pad.z())+"m",at.x(),at.y(),1,TacticalColors.FRIEND);
        }
        if(state.has("supportDevices"))for(var row:state.getAsJsonArray("supportDevices")){
            var device=row.getAsJsonObject();String kind=Engine.str(device,"kind","");var spec=SupportRules.Kind.parse(kind);if(spec==null)continue;
            double x=device.get("x").getAsDouble(),y=device.get("y").getAsDouble()+1,z=device.get("z").getAsDouble();var at=project(mc,camera,x,y,z);if(at==null)continue; if(!aimed(mc,camera,x,y,z))continue;
            MarkerVisualType visual=switch(kind){case "radio_beacon"->MarkerVisualType.BEACON_FRIENDLY;case "ammo_box"->MarkerVisualType.AMMO_BOX;case "med_box"->MarkerVisualType.MEDICAL_BOX;case "ammo_bag"->MarkerVisualType.AMMO_BAG;case "medic_bag"->MarkerVisualType.MEDICAL_BAG;default->null;};
            float entry=ANIMATION.entry("device:"+kind+":"+x+":"+z,Integer.MAX_VALUE);
            if(visual!=null)tactical(g,at.x(),at.y()-MarkerVisualRules.entryOffset(entry),1,.9f,entry,visual,"");
            else SupportMarkers.drawIcon(g,kind,Math.round(at.x()),Math.round(at.y()),7,entry);
            focusLabel(g,mc,spec.label+(kind.equals("ground_sensor")?"":" · "+Engine.num(device,"left",0)+"次")+" · "+distance(camera,x,y,z)+"m",at.x(),at.y(),entry,TacticalColors.FRIEND);
        }
        if(state.has("hazards"))for(var value:state.getAsJsonArray("hazards")){
            var hazard=value.getAsJsonObject();double x=hazard.get("x").getAsDouble(),y=hazard.get("y").getAsDouble(),z=hazard.get("z").getAsDouble();
            var entity=mc.level.getEntity(Engine.num(hazard,"id",-1));if(entity!=null&&entity.getUUID().toString().equals(Engine.str(hazard,"uuid",""))){var pos=entity.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));x=pos.x;y=pos.y+.3;z=pos.z;}
            double distance=camera.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z));if(distance>32)continue;var at=project(mc,camera,x,y,z);if(at==null)continue;
            tactical(g,at.x(),at.y(),.95f,1,(float)Math.clamp(1-distance/60,.45,.95),MarkerVisualType.GRENADE_HOSTILE,"");
            focusLabel(g,mc,"敌方危险物 · "+Math.round(distance)+"m",at.x(),at.y(),.95f,TacticalColors.ENEMY);
        }
        if(state.has("marks"))for(var item:state.getAsJsonArray("marks")){
            var mark=item.getAsJsonObject();float opacity=Minimap.fade(mark);if(opacity<=.02f)continue;
            double x=mark.get("x").getAsDouble(),y=mark.get("y").getAsDouble(),z=mark.get("z").getAsDouble();
            if(mark.has("entityId")){var entity=mc.level.getEntity(Engine.num(mark,"entityId",-1));
                if(entity!=null&&entity.getUUID().toString().equals(Engine.str(mark,"target",""))){var pos=entity.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));x=pos.x;y=pos.y+entity.getBbHeight()+.35;z=pos.z;}}
            var at=project(mc,camera,x,y,z);if(at==null)continue;
            String kind=Engine.str(mark,"kind","POSITION");
            double distance=camera.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z));
            if(kind.equals("ENEMY")){EnemyTag.draw(g,at.x(),at.y(),distance,Engine.str(mark,"kit",""),opacity);continue;}
            if(kind.equals("POSITION")){
                // A place mark is just a small arrow: the label text made it compete with the callouts that
                // actually carry information, and the shape keeps it distinct from every other marker.
                positionArrow(g,at.x(),at.y(),opacity);
                continue;
            }
            MarkerVisualType visual=switch(kind){case "REQUEST_AMMO"->MarkerVisualType.NEED_AMMO_ALLY;case "REQUEST_HEAL"->MarkerVisualType.MEDICAL_BAG;case "MATE"->MarkerVisualType.ALLY_PERSISTENT;default->MarkerVisualType.OBSERVE_GROUND;};
            tactical(g,at.x(),at.y(),opacity,.9f*opacity,.6f,visual,"");
            focusLabel(g,mc,Engine.str(mark,"name",""),at.x(),at.y(),opacity,TacticalColors.FRIEND);
        }
        if(state.has("elitePoints"))for(var item:state.getAsJsonArray("elitePoints")){
            var point=item.getAsJsonObject();var at=project(mc,camera,point.get("x").getAsDouble(),point.get("y").getAsDouble()+1.2,point.get("z").getAsDouble());if(at==null)continue;
            String reason=Engine.str(point,"reason","");HeroIcons.draw(g,Engine.str(point,"type",""),at.x(),at.y(),12,reason.isEmpty()?.9f:.4f);
            focusLabel(g,mc,Engine.str(point,"name","")+(reason.isEmpty()?" · 可领取":" · "+reason),at.x(),at.y(),1,TacticalColors.FRIEND);
        }
        if(state.has("mates"))for(var item:state.getAsJsonArray("mates")){
            var mate=item.getAsJsonObject();if(mate.has("hostile")&&mate.get("hostile").getAsBoolean())continue;
            if(mate.has("alive")&&!mate.get("alive").getAsBoolean())continue;
            String uuid=Engine.str(mate,"uuid",""),name=Engine.str(mate,"name","");if(uuid.equals(mc.player.getUUID().toString())||name.equals(mc.player.getScoreboardName()))continue;
            double x=mate.get("x").getAsDouble(),y=mate.get("y").getAsDouble()+2.3,z=mate.get("z").getAsDouble();
            try{var entity=mc.level.getPlayerByUUID(java.util.UUID.fromString(uuid));if(entity!=null){var pos=entity.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));x=pos.x;y=pos.y+entity.getBbHeight()+.55;z=pos.z;}}catch(IllegalArgumentException ignored){}
            var at=project(mc,camera,x,y,z);if(at==null)continue;
            boolean squad=mate.has("squad")&&mate.get("squad").getAsBoolean(),down=mate.has("downed")&&mate.get("downed").getAsBoolean();
            if(down&&(!mate.has("rescuable")||!mate.get("rescuable").getAsBoolean()||camera.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z))>96))continue;
            MarkerVisualType visual=down?(squad?MarkerVisualType.DOWNED_SQUAD:MarkerVisualType.DOWNED_ALLY):(squad?MarkerVisualType.SQUAD_PERSISTENT:MarkerVisualType.ALLY_PERSISTENT);
            if(down&&mate.has("rescueCall")&&mate.get("rescueCall").getAsBoolean())visual=squad?MarkerVisualType.RESCUE_SQUAD:MarkerVisualType.RESCUE_ALLY;
            String key="mate:"+(uuid.isEmpty()?name:uuid);float entry=ANIMATION.entry(key,Integer.MAX_VALUE);
            float alpha=MarkerVisualRules.alpha(g.guiWidth(),g.guiHeight(),at.x(),at.y(),Cinematic.active());
            String roleId=Engine.str(mate,"kit","");
            double mateDistance=camera.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z));
            // Squad members always draw; a non-squad teammate only when signalling support nearby.
            boolean showClass=squad||FixedClasses.resolve(roleId)==FixedClasses.Role.SUPPORT&&mateDistance<=32;
            if(!down&&!showClass)continue;
            // The class glyph is a proximity detail, matching the enemy tag: squad icons stay visible at any
            // range, but the class only appears once the teammate is close enough to read it.
            String stem=mateDistance<=32?MateMarker.stem(roleId):null;
            String elite=Engine.str(mate,"elite","");
            if(!down&&EliteRules.Kind.of(elite)!=null)HeroIcons.draw(g,elite,at.x(),at.y()-MarkerVisualRules.entryOffset(entry),12,alpha);
            else tactical(g,at.x(),at.y()-MarkerVisualRules.entryOffset(entry),alpha,.9f,entry*MarkerVisualRules.friendScale(camera.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z))),visual,stem==null?"":stem);
            focusLabel(g,mc,name+(down?" · 等待救援":""),at.x(),at.y(),1,MateMarker.labelTint(squad,down));
        }
    }
    private static void spawn(GuiGraphics g,Minecraft mc,Camera camera,double x,double y,double z,String id,String label,float aim){
        var at=project(mc,camera,x,y,z);if(at==null)return;
        float alpha=ANIMATION.alpha("spawn:"+id,MarkerVisualRules.alpha(g.guiWidth(),g.guiHeight(),at.x(),at.y(),Cinematic.active())*aim);
        DeployIcon.spawn(g,mc.font,"▼",at.x(),at.y(),7.5f,alpha,TacticalColors.FRIEND,false,false);
        focusLabel(g,mc,label+" · "+distance(camera,x,y,z)+"m",at.x(),at.y(),alpha,TacticalColors.FRIEND);
    }
    private static Projector.Screen project(Minecraft mc,Camera camera,double x,double y,double z){if(camera.getPosition().distanceToSqr(x,y,z)>MAX_DISTANCE*MAX_DISTANCE)return null;return Projector.project(mc,camera,x,y,z,1.4f);}
    private static long distance(Camera c,double x,double y,double z){return Math.round(c.getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z)));}
    private static void focusLabel(GuiGraphics g,Minecraft mc,String text,float x,float y,float alpha,int color){
        if(alpha>.01f&&MarkerVisualRules.crosshairDistance(g.guiWidth(),g.guiHeight(),x,y)<=24)TacticalColors.label(g,mc.font,text,Math.round(x),Math.round(y)+12,TacticalColors.withAlpha(color,HudLabelStyle.focusedOpacity(alpha)));
    }
    /**
     * The place-mark arrow: a short stem over a downward head, drawn with fills so it needs no texture and
     * cannot be confused with the circular callout icons.
     */
    private static void positionArrow(net.minecraft.client.gui.GuiGraphics g, float x, float y, float alpha) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        int colour = (a << 24) | 0xE8E2D2;
        int cx = Math.round(x), cy = Math.round(y);
        // stem, 1px wide, three pixels tall, centred above the head
        for (int i = 0; i < 3; i++) g.fill(cx, cy - 6 + i, cx + 1, cy - 5 + i, colour);
        // head: a downward triangle, four rows
        g.fill(cx - 3, cy - 3, cx + 4, cy - 2, colour);
        g.fill(cx - 2, cy - 2, cx + 3, cy - 1, colour);
        g.fill(cx - 1, cy - 1, cx + 2, cy, colour);
        g.fill(cx, cy, cx + 1, cy + 1, colour);
    }

    private static void tactical(GuiGraphics g,float x,float y,float alpha,float shape,float entry,MarkerVisualType visual,String role){
        if(entry<=.01f||shape<=.01f)return;g.flush();RenderSystem.setShaderColor(1,1,1,1);
        try{TacticalMarkerRenderer.drawMarker(g,x,y,alpha,shape,0,false,visual,entry,-1,role,false);}
        finally{g.flush();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.enableCull();}
    }
}
