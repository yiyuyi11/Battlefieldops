package cn.bfops;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
final class GearNames {
    private static final Map<String,String> names=new HashMap<>();
    static void clear(){names.clear();}
    static String display(String kind,String id,String fallback){
        if(id==null||id.isBlank())return fallback==null?"默认":fallback;
        if(!kind.equals("attachment")&&!kind.equals("gun")&&fallback!=null&&!fallback.equals(id)&&!fallback.isBlank())return I18n.exists(fallback)?I18n.get(fallback):fallback;
        if(kind.equals("vehicle")&&Client.state.has("names")&&Client.state.getAsJsonObject("names").has(id))return Client.state.getAsJsonObject("names").get(id).getAsString();
        String key=kind+":"+id;String result=names.get(key);if(result!=null)return result;
        try{
            if(kind.equals("attachment")||kind.equals("gun")){
                Class<?> api=Class.forName("com.tacz.guns.api.TimelessAPI");
                var index=(Optional<?>)api.getMethod(kind.equals("attachment")?"getClientAttachmentIndex":"getClientGunIndex",ResourceLocation.class).invoke(null,new ResourceLocation(id));
                if(index.isPresent()){String raw=(String)index.get().getClass().getMethod("getName").invoke(index.get());if(raw!=null&&!raw.isBlank())result=I18n.exists(raw)?I18n.get(raw):raw;}
            }
            if(result==null){ResourceLocation resource=ResourceLocation.tryParse(id);if(resource!=null&&BuiltInRegistries.ITEM.containsKey(resource)){String value=new ItemStack(BuiltInRegistries.ITEM.get(resource)).getHoverName().getString();if(!value.isBlank())result=value;}}
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){BattlefieldOps.LOG.debug("Name unavailable: {}",id,ex);}
        if(result==null)result=fallback==null||fallback.isBlank()?Catalog.prettify(id):I18n.exists(fallback)?I18n.get(fallback):fallback;
        if(names.size()>2048)names.clear();names.put(key,result);return result;
    }
}
