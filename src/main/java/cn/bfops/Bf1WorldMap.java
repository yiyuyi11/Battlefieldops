package cn.bfops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
/** Optional world-map overlay, using the installed Xaero GUI's native camera and physical-pixel scale. */
public final class Bf1WorldMap {
 public static boolean active(){return NativeXaero.room()&&Client.state.has("me")&&NativeXaero.enabled()&&Minecraft.getInstance().player!=null&&!ClientPrefs.minimapHidden();}
 public static void draw(GuiGraphics g,double cx,double cz,double scale,double guiScale,ResourceKey<Level> dimension){
  var mc=Minecraft.getInstance();if(!active()||mc.level==null||!mc.level.dimension().equals(dimension)||!Double.isFinite(scale)||scale<=0||guiScale<=0)return;
  var arena=FactionArt.arena();if(arena==null)return;var state=Client.state;var options=ClientPrefs.minimap();int w=g.guiWidth(),h=g.guiHeight();
  java.util.function.BiFunction<Double,Double,int[]> project=(x,z)->{int px=(int)Math.round(w/2.0+(x-cx)*scale/guiScale),py=(int)Math.round(h/2.0+(z-cz)*scale/guiScale);return px<12||px>w-12||py<36||py>h-36?null:new int[]{px,py};};
  int team=Engine.num(state.getAsJsonObject("me"),"team",-1),stage=Engine.num(state,"stage",0);
  g.pose().pushPose();g.pose().translate(0,0,400);g.enableScissor(4,30,w-4,h-30);
  try{
   if(options.objectives){var flags=MapIcons.array(state,"flags");for(int i=0;i<arena.points.size();i++){
    var point=arena.points.get(i);if(!CombatHud.active(arena,point,stage))continue;int[] at=project.apply(point.x,point.z);if(at==null)continue;
    var flag=i<flags.size()?flags.get(i).getAsJsonObject():new com.google.gson.JsonObject();
    MapIcons.capture(g,mc.font,point.id,at[0],at[1],flag.has("progress")?flag.get("progress").getAsFloat():50,Engine.num(flag,"owner",-1),team,false,0);
   }
   for(var value:MapIcons.array(state,"deploySites")){var site=value.getAsJsonObject();String kind=Engine.str(site,"kind","");if(!java.util.Set.of("BASE","AIR","BEACON").contains(kind))continue;int[] at=project.apply(site.get("x").getAsDouble(),site.get("z").getAsDouble());if(at!=null)MapIcons.site(g,mc.font,kind,at[0],at[1],false,0);}}
   if(options.support)for(var pad:arena.supplyPoints())if(pad.team()<0||pad.team()==team){int[] at=project.apply(pad.x(),pad.z());if(at!=null)MapIcons.marker(g,at[0],at[1],0,pad.health()?cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.MEDICAL_BOX:cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX,"",1);}
   MapIcons.contacts(g,mc.font,state,project,0,options);
   int[] self=project.apply(mc.player.getX(),mc.player.getZ());if(self!=null)Bf1Players.draw(g,self[0],self[1],mc.player.getYRot(),true,false,1,PlayerMarkerSize.self(ClientPrefs.selfArrowSize()));
  }finally{g.setColor(1f,1f,1f,1f);g.disableScissor();g.pose().popPose();}
 }
}
