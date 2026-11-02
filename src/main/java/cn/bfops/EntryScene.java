package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/** Server-timed room briefing. Periodic snapshots correct the local presentation clock. */
@EventBusSubscriber(modid="battlefield_ops",value=Dist.CLIENT)
public final class EntryScene {
    private static JsonObject copy;
    private static String token="";
    private static int elapsed,duration;
    static boolean active(){return copy!=null;}
    static JsonObject copy(){return copy;}
    static int duration(){return duration;}
    static int elapsed(){return elapsed;}
    static boolean receive(JsonObject state){
        var mc=Minecraft.getInstance();
        if(!state.has("entry")||!state.get("entry").isJsonObject()){
            boolean was=active();clear();
            if(was&&state.has("me"))mc.setScreen(new BattleScreen());
            return false;
        }
        var data=state.getAsJsonObject("entry");String id=Engine.str(data,"token","");
        if(id.isEmpty())return false;
        int length=Math.clamp(Engine.num(data,"duration",140),20,1200);
        int progress=Math.max(0,length+20-Engine.num(data,"remaining",length+20));
        boolean fresh=!id.equals(token)||!active();
        copy=data;duration=length;elapsed=fresh?progress:Math.max(elapsed,progress);token=id;
        if(fresh&&data.has("camera")){
            var c=data.getAsJsonArray("camera");if(c.size()==5)Cinematic.entryView(c.get(0).getAsDouble(),c.get(1).getAsDouble(),c.get(2).getAsDouble(),c.get(3).getAsFloat(),c.get(4).getAsFloat());
        }
        if(!(mc.screen instanceof EntryScreen))mc.setScreen(new EntryScreen());
        return true;
    }
    static void clear(){
        boolean was=active();copy=null;token="";elapsed=duration=0;
        var mc=Minecraft.getInstance();if(was)Cinematic.clear();
        if(mc.screen instanceof EntryScreen)mc.setScreen(null);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(!active())return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||!Client.serverSupported()){clear();return;}
        elapsed=Math.min(duration+20,elapsed+1);
        if(!(mc.screen instanceof EntryScreen))mc.setScreen(new EntryScreen());
    }
    @SubscribeEvent public static void hide(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event){if(active())event.setCanceled(true);}
    private EntryScene(){}
}
