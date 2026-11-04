package cn.bfops;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;

/** Shared gameplay guards; never reference client classes from the dedicated server. */
public final class BattleActions {
    public static boolean downed(Player player){
        return CrawlPose.downed(player)||(BattlefieldOps.engine!=null&&BattlefieldOps.engine.isDowned(player));
    }
    public static boolean foodHealing(Player player){
        if(BattlefieldOps.engine==null)return !CrawlPose.downed(player);
        return BattlefieldOps.engine.foodHealing(player);
    }
    public static boolean foodRule(Player player,boolean worldDefault){
        return BattlefieldOps.engine==null?worldDefault:BattlefieldOps.engine.foodRule(player,worldDefault);
    }
    static void returnToss(ItemTossEvent event){
        if(BattlefieldOps.engine==null||!BattlefieldOps.engine.members.containsKey(event.getPlayer().getUUID()))return;
        // Toss fires AFTER inventory removal. Cancelling alone destroys the removed stack.
        ItemStack remaining=event.getEntity().getItem().copy();
        boolean restored=event.getPlayer().getInventory().add(remaining);
        if(restored||remaining.isEmpty())event.setCanceled(true);
        else event.getEntity().setItem(remaining); // Never lose a remainder if an inventory mod rejects it.
        event.getPlayer().getInventory().setChanged();
        event.getPlayer().containerMenu.broadcastChanges();
        if(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p)p.inventoryMenu.sendAllDataToRemote();
    }
    private BattleActions(){}
}
