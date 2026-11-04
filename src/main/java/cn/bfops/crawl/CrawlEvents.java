package cn.bfops.crawl;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid="battlefield_ops")
public final class CrawlEvents {
    @SubscribeEvent public static void tick(PlayerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!e.player.level().isClientSide()||e.player.isLocalPlayer())TaczPronePartService.onPlayerTick(e.player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){clear(e);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){clear(e);}
    private static void clear(PlayerEvent e){TaczPronePartService.clearPlayer(e.getEntity());cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter.clear(e.getEntity().getUUID());}
}
