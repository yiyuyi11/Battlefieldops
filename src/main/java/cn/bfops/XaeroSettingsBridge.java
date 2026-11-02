package cn.bfops;
import java.util.*;
/** A draft of Xaero's current profile; changes are committed only by Apply. */
final class XaeroSettingsBridge {
    final Map<String,Object> values=new LinkedHashMap<>();
    final Map<String,List<?>> valid=new HashMap<>();
    private Object manager,profile,module,transform;
    boolean ready;String error="";
    XaeroSettingsBridge(){try{
        if(!NativeXaero.available())return;
        manager=XaeroGate.configManager();profile=manager.getClass().getMethod("getCurrentProfile").invoke(manager);
        Class<?> config=Class.forName("xaero.lib.common.config.Config"),opt=Class.forName("xaero.lib.common.config.option.ConfigOption");
        for(String key:List.of("SIZE","ZOOM","UI_SCALE","OPACITY","NORTH_LOCKED","SHAPE")){
            Object option=option(key);values.put(key,config.getMethod("get",opt).invoke(profile,option));
            try{valid.put(key,(List<?>)option.getClass().getMethod("getValidValues").invoke(option));}catch(NoSuchMethodException ignored){}
        }
        module=Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
        Object current=module.getClass().getMethod("getConfirmedTransform").invoke(module);transform=current.getClass().getMethod("copy").invoke(current);
        ready=true;
    }catch(ReflectiveOperationException|RuntimeException ex){error="无法读取 Xaero 配置："+FrontScreen.describe(ex);}}
    private Object option(String key)throws ReflectiveOperationException{return Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions").getField(key).get(null);}
    Object get(String key){return values.get(key);}
    String value(String key){return String.valueOf(get(key));}
    @SuppressWarnings("unchecked") String caption(String key){try{Object o=option(key);var display=(java.util.function.BiFunction<Object,Object,Object>)o.getClass().getMethod("getDisplayGetter").invoke(o);Object text=display.apply(o,get(key));return text instanceof net.minecraft.network.chat.Component c?c.getString():value(key);}catch(ReflectiveOperationException|RuntimeException ex){return value(key);}}
    java.util.List<ChoiceScreen.Choice> shapeChoices(){Object old=get("SHAPE");var list=new java.util.ArrayList<ChoiceScreen.Choice>();for(var v:valid.getOrDefault("SHAPE",java.util.List.of())){values.put("SHAPE",v);list.add(new ChoiceScreen.Choice(String.valueOf(v),caption("SHAPE"),""));}values.put("SHAPE",old);return list;}
    void defaults(){try{for(String key:values.keySet())values.put(key,option(key).getClass().getMethod("getDefaultValue").invoke(option(key)));transform=transform.getClass().getConstructor().newInstance();}catch(ReflectiveOperationException ex){throw new IllegalArgumentException("无法读取 Xaero 默认配置",ex);}}
    void cycle(String key){var list=valid.get(key);if(list!=null&&!list.isEmpty())values.put(key,list.get(Math.floorMod(list.indexOf(get(key))+1,list.size())));else if(get(key) instanceof Boolean b)values.put(key,!b);}
    int offset(String key){if(transform==null)return 0;try{return transform.getClass().getField(key).getInt(transform);}catch(ReflectiveOperationException ex){return 0;}}
    void offset(String key,int n){try{Model.require(Math.abs(n)<=16384,"地图偏移过大");transform.getClass().getField(key).setInt(transform,n);}catch(ReflectiveOperationException ex){throw new IllegalArgumentException("无法修改地图位置",ex);}}
    String corner(){try{return (transform.getClass().getField("fromBottom").getBoolean(transform)?"BOTTOM":"TOP")+"_"+(transform.getClass().getField("fromRight").getBoolean(transform)?"RIGHT":"LEFT");}catch(ReflectiveOperationException ex){return "TOP_LEFT";}}
    void corner(String name){try{transform.getClass().getField("fromRight").setBoolean(transform,name.endsWith("RIGHT"));transform.getClass().getField("fromBottom").setBoolean(transform,name.startsWith("BOTTOM"));transform.getClass().getField("centered").setBoolean(transform,false);}catch(ReflectiveOperationException ex){throw new IllegalArgumentException("无法修改地图位置",ex);}}
    void apply()throws ReflectiveOperationException{
        if(!ready)return;
        // Native valid-value sets are authoritative: never assume size/opacity units across releases.
        for(var entry:values.entrySet())if(valid.containsKey(entry.getKey()))Model.require(valid.get(entry.getKey()).contains(entry.getValue()),"Xaero 参数不在有效范围："+entry.getKey());
        Class<?> config=Class.forName("xaero.lib.common.config.Config"),opt=Class.forName("xaero.lib.common.config.option.ConfigOption");
        Map<String,Object> before=new LinkedHashMap<>();Object oldTransform=module.getClass().getMethod("getConfirmedTransform").invoke(module);
        try{
            for(var e:values.entrySet()){Object o=option(e.getKey());before.put(e.getKey(),config.getMethod("get",opt).invoke(profile,o));config.getMethod("set",opt,Object.class).invoke(profile,o,e.getValue());}
            module.getClass().getMethod("setTransform",transform.getClass()).invoke(module,transform);
            Object mod=Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
            Object channel=mod.getClass().getMethod("getHudConfigs").invoke(mod);
            Object io=channel.getClass().getMethod("getClientConfigProfileIO").invoke(channel);
            io.getClass().getMethod("save",Class.forName("xaero.lib.common.config.profile.ConfigProfile")).invoke(io,profile);
            Object settings=mod.getClass().getMethod("getSettings").invoke(mod);settings.getClass().getMethod("saveSettings").invoke(settings);
        }catch(ReflectiveOperationException|RuntimeException ex){
            for(var e:before.entrySet())config.getMethod("set",opt,Object.class).invoke(profile,option(e.getKey()),e.getValue());
            module.getClass().getMethod("setTransform",transform.getClass()).invoke(module,oldTransform);throw ex;
        }
    }
}
