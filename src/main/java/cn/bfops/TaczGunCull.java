package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Optional;

/** Distance branch of 656taczculling. Does not cull player bodies, outlines or vehicles. */
public final class TaczGunCull {
    private static boolean resolved,available;
    private static Method gun,display,lod;
    public static boolean cull(LivingEntity owner,ItemStack stack){
        var mc=Minecraft.getInstance();
        if(!ClientPrefs.data().distantGunCull||!(owner instanceof Player)||owner==mc.player||mc.player==null||owner.level()!=mc.player.level()||stack.isEmpty())return false;
        double distance=owner.distanceToSqr(mc.player);
        if(distance<=64*64)return false;
        if(!resolved){resolved=true;try{
            gun=Class.forName("com.tacz.guns.api.item.IGun").getMethod("getIGunOrNull",ItemStack.class);
            display=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getGunDisplay",ItemStack.class);
            lod=Class.forName("com.tacz.guns.client.resource.GunDisplayInstance").getMethod("getLodModel");available=true;
        }catch(ReflectiveOperationException|LinkageError ex){available=false;}}
        if(!available)return false;
        try{
            if(gun.invoke(null,stack)==null)return false;
            Object value=display.invoke(null,stack);boolean hasLod=false;
            if(value instanceof Optional<?> optional&&optional.isPresent()){
                Object model=lod.invoke(optional.get());hasLod=model instanceof Optional<?> o?o.isPresent():model!=null;
            }
            return distance>(hasLod?192*192:96*96);
        }catch(ReflectiveOperationException|RuntimeException ex){available=false;return false;}
    }
}
