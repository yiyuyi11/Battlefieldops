package cn.bfops.crawl;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="battlefield_ops")
public final class CrawlEvents {
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e){
        if(!e.getEntity().level().isClientSide()||e.getEntity().isLocalPlayer())TaczPronePartService.onPlayerTick(e.getEntity());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){clear(e);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){clear(e);}
    private static void clear(PlayerEvent e){TaczPronePartService.clearPlayer(e.getEntity());cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter.clear(e.getEntity().getUUID());}
}
