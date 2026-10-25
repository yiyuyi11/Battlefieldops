package cn.bfops;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

final class AmmoAllocation {
    static int target(int total,int types){return types<=0?0:Math.max(1,total/types);}
    static int topUp(IItemHandler inventory,List<ItemStack> ammo,int total){
        if(total<=0||ammo.isEmpty())return 0;
        int quota=target(total,ammo.size()),placed=0;
        // One stack per type per pass: reserve space for every gun station before adding extras.
        for(int pass=0;pass<quota;pass++){
            boolean changed=false;
            for(ItemStack template:ammo){
                int have=0;
                for(int slot=0;slot<inventory.getSlots();slot++){
                    ItemStack stack=inventory.getStackInSlot(slot);
                    if(ItemStack.isSameItemSameComponents(stack,template))have+=stack.getCount();
                }
                int count=Math.min(template.getMaxStackSize(),Math.max(0,quota-have));
                if(count==0)continue;
                ItemStack remaining=template.copyWithCount(count);
                for(int slot=0;slot<inventory.getSlots()&&!remaining.isEmpty();slot++)remaining=inventory.insertItem(slot,remaining,false);
                int added=count-remaining.getCount();placed+=added;changed|=added>0;
            }
            if(!changed)break;
        }
        return placed;
    }
}
