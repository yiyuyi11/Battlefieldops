package cn.bfops;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Edits TaCZ's native option rather than introducing a second Z binding or divergent preference. */
public final class ProneControls {
    private static boolean resolved,owned;
    private static ModConfigSpec.BooleanValue option;
    private static ModConfigSpec.BooleanValue option(){
        if(!resolved){resolved=true;try{Object value=Class.forName("com.tacz.guns.config.client.KeyConfig").getField("HOLD_TO_CRAWL").get(null);if(value instanceof ModConfigSpec.BooleanValue b)option=b;}
            catch(ReflectiveOperationException|LinkageError ignored){}}
        return option;
    }
    public static boolean available(){return option()!=null;}
    public static boolean hold(){return available()&&option().get();}
    static void toggle(){if(!available())return;release();boolean next=!option().get();option().set(next);option().save();if(next)Native.clearPose();}
    public static void pressed(boolean holding){owned=holding;}
    public static void released(){owned=false;}
    public static void tick(){if(owned)try{Native.tick();}catch(LinkageError|RuntimeException ex){owned=false;}}
    private static void release(){if(!owned)return;owned=false;Native.clearPose();}
    /** Loaded only on the TaCZ path; the outer settings class remains usable without TaCZ installed. */
    private static final class Native {
        static void tick(){
            var mc=Minecraft.getInstance();
            var key=com.mojang.blaze3d.platform.InputConstants.getKey(com.tacz.guns.client.input.CrawlKey.CRAWL_KEY.saveString().split(":",2)[0]);
            boolean physical=key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(),key.getValue());
            if(mc.player==null){owned=false;return;}
            if(!hold()||mc.screen!=null||!mc.isWindowActive()||!physical||mc.player.isPassenger()||mc.player.isSpectator()||CrawlPose.downed(mc.player))release();
        }
        static void clearPose(){var player=Minecraft.getInstance().player;
            if(player!=null&&!CrawlPose.downed(player)){
                var op=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player);
                if(op.isCrawl())op.crawl(false);
            }
        }
    }
    static void reset(){owned=false;}
}
