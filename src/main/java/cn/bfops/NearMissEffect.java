package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Shader-pack-safe edge pressure and tiny camera shake; does not modify aiming or movement stats. */
@EventBusSubscriber(modid="battlefield_ops",value=Dist.CLIENT)
public final class NearMissEffect {
    private static float level;
    private static long updated;
    static void receive(JsonObject packet){
        decay();level=Math.min(.15f,level+.1f);
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(ClientPrefs.data().bulletWhizz&&ClientPrefs.sfxEnabled()&&!(net.neoforged.fml.ModList.get().isLoaded("tacztweaks")&&mc.getSoundManager().getSoundEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacztweaks","generic.whizz.rifle"))!=null)){
            String category=Engine.str(packet,"category","rifle");if(!java.util.Set.of("smg","rifle","sniper","rpg").contains(category))category="rifle";
            var sound=Sounds.get("whizz."+category);if(sound!=null)mc.level.playLocalSound(packet.get("x").getAsDouble(),packet.get("y").getAsDouble(),packet.get("z").getAsDouble(),sound,SoundSource.PLAYERS,.8f,1,false);
        }
    }
    private static void decay(){long now=System.nanoTime();if(updated!=0)level=Math.max(0,level-(now-updated)*1e-9f*.14f);updated=now;}
    static void reset(){level=0;updated=0;}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        decay();if(!ClientPrefs.data().suppressionEffects||level<=0||!MatchView.canProne()||net.neoforged.fml.ModList.get().isLoaded("tacz_presence"))return;
        double time=System.nanoTime()*1e-9;event.setYaw(event.getYaw()+(float)Math.sin(time*31)*level*.4f);event.setPitch(event.getPitch()+(float)Math.sin(time*37)*level*.4f);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event){
        decay();var mc=Minecraft.getInstance();if(!ClientPrefs.data().suppressionEffects||level<=0||mc.screen!=null||mc.options.hideGui||!MatchView.canProne()||net.neoforged.fml.ModList.get().isLoaded("tacz_presence"))return;
        var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        for(int i=0;i<12;i++){int alpha=(int)(level*65*(1-i/12f));int edge=Math.max(1,(int)(Math.min(w,h)*.012));int p=i*edge;
            int c=alpha<<24|0x10161e;g.fill(p,p,w-p,p+edge,c);g.fill(p,h-p-edge,w-p,h-p,c);g.fill(p,p+edge,p+edge,h-p-edge,c);g.fill(w-p-edge,p+edge,w-p,h-p-edge,c);}
    }
}
