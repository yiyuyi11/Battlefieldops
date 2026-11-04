package cn.bfops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
/** Tagged vanilla compass; never overwrites an occupied slot or reissues during an active match. */
@EventBusSubscriber(modid="battlefield_ops")
public final class LobbyAccess {
    static Runnable clientOpen=()->{};
    private static long clientOpenedAt;
    private static final java.util.Map<java.util.UUID,Long> opened=new java.util.HashMap<>();
    private static boolean isToken(ItemStack stack){return stack.is(Items.COMPASS)&&stack.hasTag()&&stack.getTag().getBoolean("bfops_room_list");}
    static void remove(ServerPlayer player){
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(isToken(player.getInventory().getItem(slot)))player.getInventory().setItem(slot,ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        Engine engine=BattlefieldOps.engine;if(engine==null||net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount()%40!=0)return;
        for(ServerPlayer player:net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            var member=engine.members.get(player.getUUID());if(member!=null)continue;
            if(!Network.supported(player))continue;
            boolean found=false;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(isToken(player.getInventory().getItem(slot))){if(found)player.getInventory().setItem(slot,ItemStack.EMPTY);found=true;}
            if(found||player.getInventory().getFreeSlot()<0)continue;
            var tag=new CompoundTag();tag.putBoolean("bfops_room_list",true);
            ItemStack token=new ItemStack(Items.COMPASS);token.setTag(tag);
            token.setHoverName(Component.literal("作战大厅 · 房间列表"));
            int hotbar=-1;for(int slot=0;slot<9;slot++)if(player.getInventory().getItem(slot).isEmpty()){hotbar=slot;break;}
            if(hotbar>=0)player.getInventory().setItem(hotbar,token);else player.getInventory().add(token);
            player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        }
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        activate(event);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void block(PlayerInteractEvent.RightClickBlock event){
        if(isToken(event.getItemStack())){event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);activate(event);}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void entity(PlayerInteractEvent.EntityInteract event){activate(event);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void entityAt(PlayerInteractEvent.EntityInteractSpecific event){activate(event);}
    private static void activate(PlayerInteractEvent event){
        if(!isToken(event.getItemStack()))return;
        if(event.getLevel().isClientSide()){
            consume(event);
            long now=System.nanoTime();if(now-clientOpenedAt>=250_000_000L){clientOpenedAt=now;clientOpen.run();}
            return;
        }
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        Engine engine=BattlefieldOps.engine;if(engine==null||engine.members.containsKey(player.getUUID())||!Network.supported(player))return;
        consume(event);
        long now=player.server.getTickCount();Long previous=opened.get(player.getUUID());
        if(previous!=null&&now>=previous&&now-previous<5)return;
        opened.put(player.getUUID(),now);engine.open(player);
    }
    private static void consume(PlayerInteractEvent event){
        var result=net.minecraft.world.InteractionResult.SUCCESS;
        if(event instanceof PlayerInteractEvent.RightClickItem e){e.setCanceled(true);e.setCancellationResult(result);}
        else if(event instanceof PlayerInteractEvent.RightClickBlock e){e.setCanceled(true);e.setCancellationResult(result);}
        else if(event instanceof PlayerInteractEvent.EntityInteract e){e.setCanceled(true);e.setCancellationResult(result);}
        else if(event instanceof PlayerInteractEvent.EntityInteractSpecific e){e.setCanceled(true);e.setCancellationResult(result);}
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){opened.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void toss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if(BattlefieldOps.engine==null||!(event.getPlayer() instanceof ServerPlayer player)||!isToken(event.getEntity().getItem()))return;
        event.setCanceled(true);player.getInventory().add(event.getEntity().getItem());
    }
}
