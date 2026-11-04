package cn.bfops;
import com.google.gson.JsonObject;
import java.util.*;
final class EquipmentCategories {
    static String of(String kind,String id){
        if(LrtEquipment.explicit(id))return LrtEquipment.label(id.substring(id.indexOf(':')+1).split("/",2)[0]);
        String type="";JsonObject catalog=Client.catalog;
        String key=kind.equals("gun")?"gunTypes":"vehicleTypes";
        if(catalog.has(key)&&catalog.getAsJsonObject(key).has(id))type=catalog.getAsJsonObject(key).get(id).getAsString();
        if(type.isEmpty()&&Client.state.has(key)&&Client.state.getAsJsonObject(key).has(id))type=Client.state.getAsJsonObject(key).get(id).getAsString();
        if(kind.equals("gun")){
            if(type.isEmpty())try{var api=Class.forName("com.tacz.guns.api.TimelessAPI");Object index=((Optional<?>)api.getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation(id))).orElse(null);if(index!=null)type=String.valueOf(index.getClass().getMethod("getType").invoke(index));}catch(ReflectiveOperationException|LinkageError ignored){}
            return switch(type.toLowerCase(Locale.ROOT)){case "rifle","assault_rifle"->"步枪";case "smg","submachine_gun"->"冲锋枪";case "mg","machine_gun","lmg"->"机枪";case "sniper","sniper_rifle"->"狙击枪";case "dmr"->"精确射击步枪";case "pistol","handgun"->"手枪";case "shotgun"->"霰弹枪";case "rpg","launcher"->"发射器";case "knife","melee"->"近战";default->type.isBlank()?"其他武器":type;};
        }
        if(kind.equals("vehicle"))return type.isBlank()?"其他载具":type.split(" / ")[0];
        return "普通物品";
    }
    private EquipmentCategories(){}
}
