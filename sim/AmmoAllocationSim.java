package cn.bfops;
import java.util.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
public final class AmmoAllocationSim {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var ammo=List.of(new ItemStack(Items.ARROW),new ItemStack(Items.SNOWBALL),new ItemStack(Items.EGG),new ItemStack(Items.FIRE_CHARGE));
        var inventory=new ItemStackHandler(12);
        AmmoAllocation.topUp(inventory,ammo,128);
        for(ItemStack type:ammo)check(count(inventory,type)==32,"each station supplied");
        inventory.setStackInSlot(1,ItemStack.EMPTY);
        check(AmmoAllocation.topUp(inventory,ammo,128)>0,"secondary ammunition replenished despite full primary");
        for(ItemStack type:ammo)check(count(inventory,type)==32,"each type restored independently");
        check(AmmoAllocation.topUp(inventory,ammo,128)==0,"full rack unchanged");
        var small=new ItemStackHandler(4);AmmoAllocation.topUp(small,ammo,1024);
        for(ItemStack type:ammo)check(count(small,type)>0,"limited rack gives every station a stack first");
        System.out.println("AmmoAllocationSim OK: four stations, depleted secondary, idempotence, limited slots");
    }
    static int count(ItemStackHandler inventory,ItemStack type){int count=0;for(int i=0;i<inventory.getSlots();i++){var s=inventory.getStackInSlot(i);if(ItemStack.isSameItemSameComponents(s,type))count+=s.getCount();}return count;}
}
