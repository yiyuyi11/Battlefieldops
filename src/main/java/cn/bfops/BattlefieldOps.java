package cn.bfops;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

@Mod("battlefield_ops")
public final class BattlefieldOps {
    public static final Logger LOG=LogUtils.getLogger();
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static Engine engine;
    public BattlefieldOps(IEventBus bus){
        bus.addListener(Network::register);
        GunFeedbackCompat.register();
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (LivingDeathEvent e)->{if(engine!=null&&!e.isCanceled())engine.cosmeticKills.queue(e.getEntity(),e.getSource());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post e)->VehicleKillCompat.check(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->VehicleKillCompat.check(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->{engine=new Engine(e.getServer());});
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->{if(engine!=null)engine.shutdown();engine=null;});
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{if(engine!=null)engine.tick();});
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e)->e.getDispatcher().register(Commands.literal("bf").executes(c->{if(engine!=null)engine.state(c.getSource().getPlayerOrException(),"",true,"");return 1;})));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p&&engine.members.containsKey(p.getUUID())){e.setCanceled(true);engine.death(p,e.getSource().getEntity() instanceof ServerPlayer k?k:p.getKillCredit() instanceof ServerPlayer k?k:null);}});
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e)->{
            if(engine!=null&&e.getEntity() instanceof ServerPlayer p){
                if(engine.blockDamage(p,e.getSource().getEntity())){e.setCanceled(true);return;}
                // Hit marker and the assist/headshot ledger. Recorded before the down attempt so the blow
                // that downs someone is itself in the ledger when the knockdown settles the kill.
                if(!p.level().isClientSide()&&e.getSource().getEntity() instanceof ServerPlayer attacker){
                    // Impact height: a projectile's own position when there is one, otherwise the attacker's
                    // eye line, which is where a melee blow lands.
                    double hitY=e.getSource().getDirectEntity()!=null
                            ? e.getSource().getDirectEntity().getY() : attacker.getEyeY();
                    engine.hitNotice(attacker,p,e.getAmount(),hitY,GunFeedbackCompat.nativeMarker(e.getSource()));
                }
                // A lethal hit downs the player instead of killing them, when the map uses a revive system.
                if(e.getSource().getEntity() instanceof ServerPlayer downer
                        && engine.tryDown(p,downer,e.getAmount())){e.setCanceled(true);return;}
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p)engine.recover(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p)engine.leave(p);});
        NeoForge.EVENT_BUS.addListener((ItemTossEvent e)->{if(engine!=null&&engine.members.containsKey(e.getPlayer().getUUID()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e)->{if(engine!=null&&engine.members.containsKey(e.getPlayer().getUUID()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p&&engine.members.containsKey(p.getUUID()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->{if(engine!=null&&engine.members.containsKey(e.getEntity().getUUID()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e)->{
            if(e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&engine!=null&&e.getTarget() instanceof ServerPlayer target&&e.getEntity() instanceof ServerPlayer helper){
                if(engine.reviveStart(helper,target))e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide()&&engine!=null&&e.getEntity().getTags().contains("bfops_vehicle")&&!engine.vehicles.contains(e.getEntity().getUUID()))e.setCanceled(true);});
    }
}
