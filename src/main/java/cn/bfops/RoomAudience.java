package cn.bfops;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Room and lobby are distinct audiences. Only an explicit admin world channel crosses them. */
@EventBusSubscriber(modid="battlefield_ops")
public final class RoomAudience {
    private static final Set<UUID> WORLD=new HashSet<>();
    private record Recent(String display,String name,String room,long at){}
    private static final Map<UUID,Recent> RECENT=new HashMap<>();
    public static String room(ServerPlayer player){
        var engine=BattlefieldOps.engine;if(engine==null||player==null)return "";
        var member=engine.members.get(player.getUUID());return member==null?"":member.map;
    }
    public static boolean member(ServerPlayer player){return player!=null&&!room(player).isEmpty();}
    public static boolean same(ServerPlayer a,ServerPlayer b){return !BattleServerMode.running()||room(a).equals(room(b));}
    public static int channel(net.minecraft.commands.CommandSourceStack source,boolean world){
        if(!BattleServerMode.running()){source.sendFailure(Component.literal("本子服未开启大战场模式，聊天使用服务器原有设置"));return 0;}
        try{
            var player=source.getPlayerOrException();
            if(world){if(!source.hasPermission(2)){source.sendFailure(Component.literal("世界广播模式需要管理员权限"));return 0;}WORLD.add(player.getUUID());}
            else WORLD.remove(player.getUUID());
            source.sendSuccess(()->Component.literal(world?"已切换到世界广播模式：此后聊天会跨房间发送；/bf chat local 恢复隔离":"已恢复房间／大厅隔离聊天"),false);return 1;
        }catch(Exception ex){source.sendFailure(Component.literal("此命令需要由玩家执行"));return 0;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void chat(ServerChatEvent event){
        if(!BattleServerMode.running()||event.isCanceled())return;
        var source=event.getPlayer();boolean world=WORLD.contains(source.getUUID())&&source.hasPermissions(2);
        String scope=room(source),label=world?"世界":scope.isEmpty()?"大厅":"房间 "+scope;
        Component text=Component.literal("["+label+"] ").withStyle(world?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.AQUA)
                .append(source.getDisplayName()).append(Component.literal(": ")).append(event.getMessage());
        event.setCanceled(true);
        for(var target:source.server.getPlayerList().getPlayers())if(world||same(source,target))target.sendSystemMessage(text);
        BattlefieldOps.LOG.info("[{}] {}: {}",label,source.getScoreboardName(),event.getRawText());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer p){
            WORLD.remove(p.getUUID());RECENT.put(p.getUUID(),new Recent(p.getDisplayName().getString(),p.getScoreboardName(),room(p),System.currentTimeMillis()));
        }
    }
    public static boolean system(PlayerList list,Component message,boolean overlay){
        if(!BattleServerMode.running()||!(message.getContents() instanceof TranslatableContents content))return false;
        String key=content.getKey();
        if(!key.startsWith("death.")&&!key.startsWith("multiplayer.player.")&&!key.startsWith("chat.type.advancement."))return false;
        String display="";Object[] args=content.getArgs();
        if(args.length>0)display=args[0] instanceof Component c?c.getString():String.valueOf(args[0]);
        String scope="";boolean found=false;
        for(var p:list.getPlayers())if(display.equals(p.getDisplayName().getString())||display.equals(p.getScoreboardName())){scope=room(p);found=true;break;}
        long now=System.currentTimeMillis();RECENT.entrySet().removeIf(e->now-e.getValue().at>10000);
        if(!found)for(var recent:RECENT.values())if(display.equals(recent.display)||display.equals(recent.name)){scope=recent.room;break;}
        for(var p:list.getPlayers())if(room(p).equals(scope))p.sendSystemMessage(message,overlay);
        return true;
    }
    public static boolean teamSystem(PlayerList list,net.minecraft.world.entity.player.Player source,Component message,boolean except){
        if(!BattleServerMode.running()||!(source instanceof ServerPlayer p))return false;
        for(var target:list.getPlayers())if(same(p,target)&&(except?!p.isAlliedTo(target):p.isAlliedTo(target)))target.sendSystemMessage(message);
        return true;
    }
    public static void clear(){WORLD.clear();RECENT.clear();}
    private RoomAudience(){}
}
