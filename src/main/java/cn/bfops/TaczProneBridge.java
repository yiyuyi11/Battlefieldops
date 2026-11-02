package cn.bfops;
import net.minecraft.server.level.ServerPlayer;
final class TaczProneBridge {
    static void set(ServerPlayer player,boolean prone)throws ReflectiveOperationException{
        Class<?> type=Class.forName("com.tacz.guns.api.entity.IGunOperator");
        Object op=type.getMethod("fromLivingEntity",net.minecraft.world.entity.LivingEntity.class).invoke(null,player);
        type.getMethod("crawl",boolean.class).invoke(op,prone);
    }
    static void clear(ServerPlayer player){try{set(player,false);}catch(ReflectiveOperationException|LinkageError ignored){}}
}
