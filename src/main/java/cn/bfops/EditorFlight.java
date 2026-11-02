package cn.bfops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
/** Persist the original mode/position on the player so disconnects cannot strand an editor. */
@EventBusSubscriber(modid="battlefield_ops")
public final class EditorFlight {
    private static final String KEY="bfops_editor_return";
    private static net.minecraft.nbt.CompoundTag data(ServerPlayer p){try{return (net.minecraft.nbt.CompoundTag)p.getClass().getMethod("getPersistentData").invoke(p);}catch(ReflectiveOperationException ex){throw new IllegalStateException("无法保存编辑前状态",ex);}}
    static boolean active(ServerPlayer p){return data(p).contains(KEY);}
    static void start(ServerPlayer p,Model.Arena map){
        Model.require(p.hasPermissions(2)&&BattleServerMode.running(),"只有管理员可使用快速编辑");
        Model.require(BattlefieldOps.engine!=null&&!BattlefieldOps.engine.members.containsKey(p.getUUID()),"请先退出房间再编辑");
        if(!data(p).contains(KEY)){
            var n=new net.minecraft.nbt.CompoundTag();n.putInt("mode",p.gameMode.getGameModeForPlayer().getId());n.putString("dimension",p.level().dimension().location().toString());n.putDouble("x",p.getX());n.putDouble("y",p.getY());n.putDouble("z",p.getZ());n.putFloat("yaw",p.getYRot());n.putFloat("pitch",p.getXRot());data(p).put(KEY,n);
        }
        p.stopRiding();p.setGameMode(GameType.SPECTATOR);
        if(map!=null){var dim=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(map.dimension));var level=p.server.getLevel(dim);if(level!=null){double[] bounds=map.extent(map.boundary);double ground=map.spawns.stream().mapToDouble(s->s.y).max().orElse(64);p.teleportTo(level,bounds[0],ground+40,bounds[1],0,65);}}
        Network.send(p,java.util.Map.of("type","editorFlight","active",true));
    }
    static void restore(ServerPlayer p){
        if(!data(p).contains(KEY))return;
        var n=data(p).getCompound(KEY);
        try{var level=p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(n.getString("dimension"))));if(level!=null)p.teleportTo(level,n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),n.getFloat("yaw"),n.getFloat("pitch"));}
        finally{p.setGameMode(GameType.byId(n.getInt("mode")));data(p).remove(KEY);p.fallDistance=0;}
    }
    static void all(net.minecraft.server.MinecraftServer server){if(server!=null)for(var p:server.getPlayerList().getPlayers())restore(p);}
    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)restore(p);}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)restore(p);}
    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent e){all(e.getServer());}
}
