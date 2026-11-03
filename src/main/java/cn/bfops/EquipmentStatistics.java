package cn.bfops;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
/** Read-only stacks, native attachment evaluation. Never change the held weapon or spawn a vehicle. */
final class EquipmentStatistics {
    record Row(String key,String title,String before,String after,double baseBar,double currentBar,double delta,boolean biggerBetter){
        boolean changed(){return Math.abs(delta)>1e-7;}
        boolean improved(){return changed()&&(delta>0)==biggerBetter;}
    }
    record Report(String source,List<Row> rows,String warning){}
    private static final Map<String,Report> CACHE=new LinkedHashMap<>(96,.75f,true);
    static void clear(){CACHE.clear();}
    static Report read(String kind,String id,Map<String,String> selected){return read(kind,id,selected,null);}
    static Report read(String kind,String id,Map<String,String> selected,VehicleArmament configured){
        if(id==null||id.isEmpty())return new Report("",List.of(),"请先选择装备");
        VehicleArmament armament=configured;
        if(armament==null&&kind.equals("vehicle")){var cached=FactionArt.arena();if(cached!=null)armament=cached.armament(id);}
        String key=BalanceDisplay.revision()+":"+kind+":"+id+new TreeMap<>(selected)+BattlefieldOps.GSON.toJson(armament);Report old=CACHE.get(key);if(old!=null)return old;
        Report result;
        try{result=kind.equals("vehicle")?vehicle(id,selected,armament):gun(id,selected);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){BattlefieldOps.LOG.debug("Equipment stats unavailable: {}",id,ex);result=new Report("",List.of(),"当前装备未提供可读取的属性，未使用估算数值");}
        CACHE.put(key,result);while(CACHE.size()>96)CACHE.remove(CACHE.keySet().iterator().next());return result;
    }
    private static Object invoke(Object value,String name)throws ReflectiveOperationException{return value.getClass().getMethod(name).invoke(value);}
    private static double number(Object value){return ((Number)value).doubleValue();}
    static double ratio(double value){return Double.isFinite(value)?Math.clamp(value,0,1):0;}
    static String plain(String value){return value==null?"":value.replaceAll("§.","");}
    private static Report gun(String id,Map<String,String> selected)throws ReflectiveOperationException{
        Class<?> api;
        try{api=Class.forName("com.tacz.guns.api.TimelessAPI");}catch(ClassNotFoundException ex){return sbwItem(id);}
        Object index=((Optional<?>)api.getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,ResourceLocation.parse(id))).orElse(null);
        if(index==null)return sbwItem(id);
        Object data=invoke(index,"getGunData");Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
        Object bare=builder.getMethod("create").invoke(null);builder.getMethod("setId",ResourceLocation.class).invoke(bare,ResourceLocation.parse(id));
        List<?> modes=(List<?>)invoke(data,"getFireModeSet");if(!modes.isEmpty())builder.getMethod("setFireMode",Class.forName("com.tacz.guns.api.item.gun.FireMode")).invoke(bare,modes.getFirst());
        ItemStack base=(ItemStack)builder.getMethod("build",net.minecraft.core.HolderLookup.Provider.class).invoke(bare,Minecraft.getInstance().player.registryAccess());
        ItemStack current=base.copy();Class<?> gun=Class.forName("com.tacz.guns.api.item.IGun");Object item=gun.getMethod("getIGunOrNull",ItemStack.class).invoke(null,current);
        for(String attachment:selected.values())if(!attachment.isEmpty()){
            ItemStack part=Fittings.attachment(attachment);
            Model.require((boolean)gun.getMethod("allowAttachment",ItemStack.class,ItemStack.class).invoke(item,current,part),"配件不兼容");
            gun.getMethod("installAttachment",net.minecraft.core.HolderLookup.Provider.class,ItemStack.class,ItemStack.class).invoke(item,Minecraft.getInstance().player.registryAccess(),current,part);
        }
        Class<?> cache=Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty"),gunData=data.getClass();Object before=cache.getConstructor().newInstance(),after=cache.getConstructor().newInstance();
        cache.getMethod("eval",ItemStack.class,gunData).invoke(before,base,data);cache.getMethod("eval",ItemStack.class,gunData).invoke(after,current,data);
        Class<?> manager=Class.forName("com.tacz.guns.resource.modifier.AttachmentPropertyManager"),modifier=Class.forName("com.tacz.guns.api.modifier.IAttachmentModifier");
        Map<?,?> modifiers=(Map<?,?>)manager.getMethod("getModifiers").invoke(null);List<Row> rows=new ArrayList<>();
        String fire=modes.isEmpty()?"未知":switch(modes.getFirst().toString()){case "AUTO"->"全自动";case "SEMI"->"半自动";case "BURST"->"连发";default->modes.getFirst().toString();};
        rows.add(new Row("FIRE_MODE","默认射击模式",fire,fire,0,0,0,false));
        try{
            Class<?> utils=Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            var ammo=utils.getMethod("getAmmoCountWithAttachment",ItemStack.class,gunData);
            double b=number(ammo.invoke(null,base,data)),c=number(ammo.invoke(null,current,data));
            rows.add(new Row("AMMO_CAPACITY","弹匣容量",format(b),format(c),ratio(b/100),ratio(c/100),c-b,true));
            double sprint=number(invoke(data,"getSprintTime"));
            rows.add(new Row("SPRINT_TIME","冲刺转开火",format(sprint)+"s",format(sprint)+"s",ratio(sprint/.5),ratio(sprint/.5),0,false));
        }catch(ReflectiveOperationException|RuntimeException ex){BattlefieldOps.LOG.debug("Ammo/sprint property unavailable: {}",id,ex);}
        for(var entry:modifiers.entrySet())try{
            Method diagram=modifier.getMethod("getPropertyDiagramsData",ItemStack.class,gunData,cache);
            List<?> bases=(List<?>)diagram.invoke(entry.getValue(),base,data,before),changed=(List<?>)diagram.invoke(entry.getValue(),current,data,after);
            for(int i=0;i<Math.min(bases.size(),changed.size());i++){
                Object b=bases.get(i),c=changed.get(i);String title=(String)invoke(c,"titleKey");double delta=number(invoke(c,"modifier"));
                if(!Double.isFinite(delta))continue;
                String shown=(String)invoke(c,delta>1e-7?"positivelyString":delta< -1e-7?"negativeString":"defaultString");
                rows.add(new Row(entry.getKey()+":"+i,I18n.exists(title)?I18n.get(title):title,plain((String)invoke(b,"defaultString")),plain(shown),ratio(number(invoke(c,"defaultPercent"))),ratio(number(invoke(c,"defaultPercent"))+number(invoke(c,"modifierPercent"))),delta,(boolean)invoke(c,"positivelyBetter")));
            }
        }catch(ReflectiveOperationException|RuntimeException ex){BattlefieldOps.LOG.debug("Modifier diagram unavailable: {}",entry.getKey(),ex);}
        try{
            Class<?> utils=Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            rows.removeIf(r->{String k=r.key.toLowerCase(Locale.ROOT);return (k.contains("damage")&&!k.contains("range")&&!k.contains("explosion"))||k.contains("headshot");});
            String[][] values={{"getDamageWithAttachment","服务端基础伤害","DAMAGE"},{"getHeadshotMultiplier","服务端爆头倍率","HEADSHOT"}};
            for(var value:values){
                var method=utils.getMethod(value[0],ItemStack.class,gunData);
                double factor=BalanceDisplay.factor(id,value[2].equals("HEADSHOT"));
                double b=number(method.invoke(null,base,data))*factor,c=number(method.invoke(null,current,data))*factor;
                double span=Math.max(1,Math.max(b,c))*1.15;
                rows.add(new Row(value[2],value[1],format(b),format(c),ratio(b/span),ratio(c/span),c-b,true));
            }
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Native damage preview unavailable",ex);}
        rows.sort(Comparator.comparingInt((Row row)->row.key.toLowerCase(Locale.ROOT).contains("ads")?0:row.key.toLowerCase(Locale.ROOT).contains("recoil")?1:2).thenComparing(Row::key));
        return new Report("TaCZ 原生配件属性",List.copyOf(rows),rows.isEmpty()?"此枪包没有提供属性图表接口":"不含距离、目标护甲及角色技能等实战变量");
    }
    private static Object sbwData(Object defaults)throws ReflectiveOperationException{
        Class<?> function=Class.forName("kotlin.jvm.functions.Function0"),data=Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
        Object supplier=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,args)->{
            if(m.getName().equals("invoke"))return defaults;if(m.getName().equals("toString"))return "Preview gun data";if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return p==args[0];return null;
        });
        return data.getMethod("from",ItemStack.class,function).invoke(null,new ItemStack(Items.PAPER),supplier);
    }
    private static Report sbwItem(String id)throws ReflectiveOperationException{
        ResourceLocation key=ResourceLocation.parse(id);if(!BuiltInRegistries.ITEM.containsKey(key))return new Report("",List.of(),"此装备没有可读取属性");
        Class<?> data=Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");Object current=data.getMethod("from",ItemStack.class).invoke(null,new ItemStack(BuiltInRegistries.ITEM.get(key)));
        var rows=new ArrayList<Row>();sbwRows(rows,"",current,current,id);return new Report("卓越前线原生属性",List.copyOf(rows),"按原生属性显示；没有提供的字段不作推算");
    }
    private static Report vehicle(String id,Map<String,String> selected,VehicleArmament configured)throws ReflectiveOperationException{
        Class<?> vehicle=Class.forName("com.atsuishio.superbwarfare.data.vehicle.VehicleData");Object defaults=vehicle.getMethod("getDefault",String.class).invoke(null,id);
        Map<?,?> weapons=(Map<?,?>)invoke(defaults,"weapons");List<Row> rows=new ArrayList<>();
        for(var entry:weapons.entrySet())try{
            String station=entry.getKey().toString();Object base=sbwData(entry.getValue()),current=sbwData(entry.getValue());
            VehicleArmament.Station arm=configured==null?null:configured.forStation(station);
            if(arm!=null){Object attachment=current.getClass().getField("attachment").get(current);Class<?> type=Class.forName("com.atsuishio.superbwarfare.data.gun.value.AttachmentType");
                for(var value:arm.attachments.entrySet())attachment.getClass().getMethod("set",type,int.class).invoke(attachment,Enum.valueOf(type.asSubclass(Enum.class),value.getKey()),value.getValue());
            }
            if(selected.containsKey(station)&&!selected.get(station).isEmpty()){
                Object index=current.getClass().getField("selectedAmmoType").get(current);index.getClass().getMethod("set",int.class).invoke(index,Integer.parseInt(selected.get(station)));
            }
            sbwRows(rows,station+" · ",base,current);
        }catch(ReflectiveOperationException|RuntimeException ex){BattlefieldOps.LOG.debug("Vehicle station stats unavailable: {}",entry.getKey(),ex);}
        return new Report("卓越前线武器站原生属性",List.copyOf(rows),rows.isEmpty()?"当前载具未提供可读取的武器站属性":"含地图配置的武器站配件与所选弹种；按原生值显示，不含实际命中判定");
    }
    private static void sbwRows(List<Row> rows,String station,Object base,Object current)throws ReflectiveOperationException{sbwRows(rows,station,base,current,"");}
    private static void sbwRows(List<Row> rows,String station,Object base,Object current,String id)throws ReflectiveOperationException{
        Class<?> prop=Class.forName("com.atsuishio.superbwarfare.data.gun.GunProp");Method get=current.getClass().getMethod("get",prop);
        String[][] specs={{"DAMAGE","伤害","+"},{"RPM","射速 / 分","+"},{"MAGAZINE","弹容","+"},{"RECOIL_X","水平后坐力","-"},{"RECOIL_Y","垂直后坐力","-"},{"RECOIL","后坐力","-"},{"SPREAD","散布","-"},{"ZOOM_SPREAD_RATE","瞄准散布倍率","-"},{"VELOCITY","弹速（原生值）","+"},{"WEIGHT","重量（原生值）","-"},{"NORMAL_RELOAD_TIME","战术换弹 / 秒","-"},{"EMPTY_RELOAD_TIME","空仓换弹 / 秒","-"},{"SHOOT_DELAY_TIME","开火延迟 / 秒","-"},{"DEFAULT_ZOOM","瞄准倍率","+"},{"HEADSHOT","爆头倍率","+"},{"EXPLOSION_DAMAGE","爆炸伤害","+"},{"EXPLOSION_RADIUS","爆炸范围","+"}};
        for(var spec:specs)try{
            Object field=prop.getField(spec[0]).get(null);Object b=get.invoke(base,field),c=get.invoke(current,field);if(!(b instanceof Number)||!(c instanceof Number))continue;
            double original=number(b),changed=number(c);
            if(spec[0].equals("DAMAGE")||spec[0].equals("HEADSHOT")||spec[0].equals("EXPLOSION_DAMAGE")){double factor=BalanceDisplay.factor(id,spec[0].equals("HEADSHOT"));original*=factor;changed*=factor;}
            if(spec[0].endsWith("TIME")){original/=20;changed/=20;}
            if(!Double.isFinite(original)||!Double.isFinite(changed))continue;
            double scale=Math.max(1,Math.max(Math.abs(original),Math.abs(changed)))*1.15;
            rows.add(new Row(station+spec[0],station+spec[1],format(original),format(changed),ratio(original/scale),ratio(changed/scale),changed-original,spec[2].equals("+")));
        }catch(NoSuchFieldException ignored){}
    }
    static String format(double value){String text=String.format(Locale.ROOT,"%.3f",value).replaceAll("0+$","");return text.endsWith(".")?text.substring(0,text.length()-1):text;}
}
