package cn.bfops;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.file.*;

/** Each backend opts in independently; an installed client never enables a server. */
public final class BattleServerMode {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static volatile boolean clientActive;
    private static MinecraftServer server;
    private static Engine recovery;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Enable Battlefield rooms on THIS backend. False leaves normal servers untouched.",
                "Admins can persistently switch with /bf mode on or /bf mode off.").define("battlefieldMode",false);
        SPEC=b.build();
    }
    static void started(MinecraftServer value){
        server=value;
        if(ENABLED.get())enable();
        else{
            Path folder=value.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("battlefield_ops/players");
            if(Files.isDirectory(folder))try(var files=Files.list(folder)){
                if(files.anyMatch(p->p.getFileName().toString().endsWith(".nbt")))recovery=new Engine(value);
            }catch(Exception ex){BattlefieldOps.LOG.error("Cannot prepare previous-session inventory recovery",ex);}
        }
    }
    private static void enable(){
        if(server==null||BattlefieldOps.engine!=null)return;
        BattlefieldOps.engine=recovery!=null?recovery:new Engine(server);recovery=null;

    }
    static void stop(){
        if(BattlefieldOps.engine!=null)BattlefieldOps.engine.shutdown();
        if(recovery!=null)recovery.shutdown();
        BattlefieldOps.engine=null;recovery=null;server=null;RoomAudience.clear();
    }
    static void login(ServerPlayer p){
        if(BattlefieldOps.engine==null&&recovery!=null)recovery.recover(p);
        if(BattlefieldOps.engine==null)LobbyAccess.remove(p);
        sync(p);
    }
    public static int command(net.minecraft.commands.CommandSourceStack source,boolean enabled){
        try{
            if(enabled)enable();
            else if(BattlefieldOps.engine!=null){BattlefieldOps.engine.deactivate();BattlefieldOps.engine=null;}
            if(!enabled){EditorFlight.all(server);RoomAudience.clear();}
            ENABLED.set(enabled);ENABLED.save();
            if(server!=null)for(var p:server.getPlayerList().getPlayers())sync(p);
            source.sendSuccess(()->Component.literal(enabled?"本子服已开启大战场模式":"本子服已关闭大战场模式，玩家背包和位置已恢复"),false);return 1;
        }catch(Exception ex){source.sendFailure(Component.literal("模式切换失败："+ex.getMessage()+"；备份文件已保留"));return 0;}
    }
    static void sync(ServerPlayer p){Network.mode(p,BattlefieldOps.engine!=null);}
    public static boolean running(){return BattlefieldOps.engine!=null;}
    private BattleServerMode(){}
}
