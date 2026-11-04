package cn.bfops;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.*;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

@Mod("battlefield_ops")
public final class BattlefieldOps {
    public static final Logger LOG=LogUtils.getLogger();
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static Engine engine;
    public BattlefieldOps(){
        IEventBus bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var container=net.minecraftforge.fml.ModLoadingContext.get();
        container.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,cn.bfops.crawl.CrawlConfig.SPEC,"battlefield_ops-crawl.toml");
        container.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,BattleServerMode.SPEC,"battlefield_ops-server.toml");
        Network.register();
        cn.bfops.voice.network.VoiceNetwork.register();
        // Every sound event is registered in one pass, here, because a DeferredRegister may only be written
        // to once the registries are bootstrapped -- which is exactly and only this point.
        Sounds.registerAll();
        Sounds.REGISTRY.register(bus);
        SupportItems.registerAll();
        SupportItems.REGISTRY.register(bus);
        // Client-only and behind the dist check, so a server never resolves those classes.
        if(net.minecraftforge.fml.loading.FMLEnvironment.dist==net.minecraftforge.api.distmarker.Dist.CLIENT)
            cn.bfops.client.SupportItemClient.register(bus);
        cn.bfops.voice.FrontVoiceSounds.registerAll();
        cn.bfops.voice.FrontVoiceSounds.REGISTRY.register(bus);
        GunFeedbackCompat.register();FinalHonours.register();
        cn.bfops.voice.server.event.FrontVoiceServerEvents.registerAll();
        MinecraftForge.EVENT_BUS.register(new cn.bfops.voice.server.command.FrontVoiceCommandEntrypoint());
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, (LivingDeathEvent e)->{if(engine!=null&&!e.isCanceled())engine.cosmeticKills.queue(e.getEntity(),e.getSource());});
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e)->{if(engine!=null){VehicleKillCompat.check(e.getEntity());engine.atmosphereTick(e.getEntity());}});
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityLeaveLevelEvent e)->{if(engine!=null){engine.finalHonours.mineLeft(e.getEntity());VehicleKillCompat.check(e.getEntity());}});
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,(net.minecraftforge.event.entity.EntityMountEvent e)->{if(engine!=null&&!e.isCanceled()&&e.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer p)engine.vehicleMount(p,e.getEntityBeingMounted(),e.isMounting());});
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e)->BattleServerMode.started(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e)->BattleServerMode.stop());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e)->{if(engine!=null)if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) engine.tick();});
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e)->e.getDispatcher().register(Commands.literal("bf")
            .executes(c->{if(engine!=null)engine.open(c.getSource().getPlayerOrException());else c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("本子服未开启大战场模式，管理员可使用 /bf mode on"));return 1;})
            .then(Commands.literal("chat")
                .then(Commands.literal("local").executes(c->RoomAudience.channel(c.getSource(),false)))
                .then(Commands.literal("world").requires(s->s.hasPermission(2)).executes(c->RoomAudience.channel(c.getSource(),true))))
            .then(Commands.literal("mode").requires(s->s.hasPermission(2))
                .executes(c->{c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(BattleServerMode.running()?"大战场模式：已开启":"大战场模式：已关闭"),false);return 1;})
                .then(Commands.literal("on").executes(c->BattleServerMode.command(c.getSource(),true)))
                .then(Commands.literal("off").executes(c->BattleServerMode.command(c.getSource(),false))))
            .then(Commands.literal("level").executes(c->{
                if(engine==null)return 0;
                try{int level=engine.playerLevel(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("战地等级："+level),false);return 1;}
                catch(java.io.IOException ex){c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("等级存档读取失败，原文件已保留"));return 0;}
            }).then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).requires(source->source.hasPermission(2))
                .then(Commands.argument("level",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,100)).executes(c->{
                    if(engine==null)return 0;
                    var target=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player");int level=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"level");
                    try{engine.setPlayerLevel(target,level);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("已设置 "+target.getScoreboardName()+" 的战地等级为 "+level),true);return 1;}
                    catch(java.io.IOException ex){c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("等级保存失败，原文件已保留"));return 0;}
                }))))));
        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (engine == null || !(event.getEntity() instanceof ServerPlayer player)
                    || !engine.members.containsKey(player.getUUID())) return;
            event.setCanceled(true);
            ServerPlayer attacker = VehicleKillCompat.attacker(event.getSource());
            ServerPlayer credited = attacker != null ? attacker
                    : player.getKillCredit() instanceof ServerPlayer killer ? killer : engine.recentAttacker(player);
            var focus = attacker != null ? attacker : event.getSource().getEntity();
            engine.death(player, credited, focus);
        });
        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent e)->{
            if(engine!=null&&e.getEntity() instanceof ServerPlayer p){
                ServerPlayer damageAttacker=VehicleKillCompat.attacker(e.getSource());
                if(engine.blockDamage(p,damageAttacker!=null?damageAttacker:e.getSource().getEntity())){e.setCanceled(true);return;}
                if(e.getAmount()>0)engine.noteDamage(p,damageAttacker,e.getSource());
                // Hit marker and the assist/headshot ledger. Recorded before the down attempt so the blow
                // that downs someone is itself in the ledger when the knockdown settles the kill.
                if(!p.level().isClientSide()&&damageAttacker!=null){
                    // Impact height: a projectile's own position when there is one, otherwise the attacker's
                    // eye line, which is where a melee blow lands.
                    ServerPlayer attacker=damageAttacker;
                    double hitY=e.getSource().getDirectEntity()!=null
                            ? e.getSource().getDirectEntity().getY() : attacker.getEyeY();
                    engine.hitNotice(attacker,p,e.getAmount(),hitY,GunFeedbackCompat.nativeMarker(e.getSource()));
                }
            }
        });
        // Decide lethality after armour, resistance and absorption have reduced damage.
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                (net.minecraftforge.event.entity.living.LivingDamageEvent e)->{
            if(engine!=null && e.getEntity() instanceof ServerPlayer p
                    && VehicleKillCompat.attacker(e.getSource())!=null
                    && engine.tryDown(p,VehicleKillCompat.attacker(e.getSource()),e.getAmount())) e.setAmount(0);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p){if(engine!=null)engine.recover(p);BattleServerMode.login(p);}});
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p)engine.disconnected(p);});
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(net.minecraftforge.event.entity.player.AttackEntityEvent e)->{
            if(engine!=null&&engine.isDowned(e.getEntity()))e.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(PlayerInteractEvent.RightClickItem e)->{
            if(engine!=null&&engine.isDowned(e.getEntity())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(PlayerInteractEvent.RightClickBlock e)->{
            if(engine!=null&&engine.isDowned(e.getEntity())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(PlayerInteractEvent.EntityInteract e)->{
            if(engine!=null&&engine.isDowned(e.getEntity())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH,(PlayerInteractEvent.RightClickItem e)->{
            if(engine!=null&&e.getEntity() instanceof ServerPlayer player&&engine.support.use(player,e.getHand(),null)){
                e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH,(PlayerInteractEvent.RightClickBlock e)->{
            if(engine!=null&&e.getEntity() instanceof ServerPlayer player&&engine.support.use(player,e.getHand(),null)){
                e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH,(PlayerInteractEvent.EntityInteract e)->{
            if(engine!=null&&e.getEntity() instanceof ServerPlayer player&&e.getTarget() instanceof ServerPlayer target&&engine.support.use(player,e.getHand(),target)){
                e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ItemTossEvent e)->BattleActions.returnToss(e));
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e)->{if(engine!=null&&engine.members.containsKey(e.getPlayer().getUUID()))e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{if(engine!=null&&e.getEntity() instanceof ServerPlayer p&&engine.members.containsKey(p.getUUID()))e.setCanceled(true);});
        // Right-click interaction (doors, buttons, support equipment placement, vehicle boarding) must keep
        // working during a match. Only items that reshape the terrain are refused; block break/place is
        // already cancelled on the two lines above, which is what actually protects the map.
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->{
            if(engine==null||!engine.members.containsKey(e.getEntity().getUUID()))return;
            var held=e.getItemStack();
            if(held.getItem() instanceof net.minecraft.world.item.BucketItem
                    || held.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)
                    || held.is(net.minecraft.world.item.Items.FIRE_CHARGE))
                e.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e)->{
            if(e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&engine!=null&&e.getTarget() instanceof ServerPlayer target&&e.getEntity() instanceof ServerPlayer helper){
                if(engine.reviveStart(helper,target))e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide()&&engine!=null&&e.getEntity().getTags().contains("bfops_vehicle")&&!engine.vehicles.contains(e.getEntity().getUUID()))e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{
            if(!e.getLevel().isClientSide()&&e.getEntity().getTags().contains("bfops_support_prop")&&!(e.getEntity() instanceof SupportProp))e.setCanceled(true);
            if(!e.getLevel().isClientSide()&&e.getEntity().getTags().contains("bfops_beacon")
                    &&(engine==null||!engine.knownBeacon(e.getEntity().getUUID())||!(e.getEntity() instanceof DeployBeacon))){
                e.setCanceled(true);
                if(engine!=null)engine.restoreBeacon(e.getEntity().getUUID());
            }
        });
    }
}
