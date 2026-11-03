package cn.bfops;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
public final class TaczAdsNativeSim {
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var gun=new GunData();Field aim=GunData.class.getDeclaredField("aimTime");aim.setAccessible(true);aim.setFloat(gun,.4f);
        var modifier=new AdsModifier();var cache=modifier.initCache(ItemStack.EMPTY,gun);cache.setValue(.3f);
        var properties=new AttachmentCacheProperty();Field values=AttachmentCacheProperty.class.getDeclaredField("cacheValues");values.setAccessible(true);((Map)values.get(properties)).put(modifier.getId(),cache);
        var rows=modifier.getPropertyDiagramsData(ItemStack.EMPTY,gun,properties);if(rows.size()!=1)throw new AssertionError("native ADS row missing");
        var row=rows.getFirst();if(Math.abs(row.modifier().doubleValue()+.1)>1e-5||row.positivelyBetter())throw new AssertionError("native ADS delta/direction incorrect");
        if(!row.negativeString().contains("0.30s"))throw new AssertionError("native ADS unit/value missing");
        if(Math.abs(row.defaultPercent()+row.modifierPercent()-.6)>1e-5)throw new AssertionError("native chart maps changed ADS correctly");
        System.out.println("TaczAdsNativeSim: actual installed TaCZ ADS formatter and diagram arithmetic passed");
    }
}
