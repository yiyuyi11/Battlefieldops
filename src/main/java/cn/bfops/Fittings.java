package cn.bfops;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Server-generated compatible options, revalidated against the actual deployed equipment. */
final class Fittings {
    record Option(String id,String name,String kind,String icon){}
    record Group(String id,String name,List<Option> options){}
    static ItemStack attachment(String id)throws ReflectiveOperationException{
        Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
        Object value=builder.getMethod("create").invoke(null);
        builder.getMethod("setId",ResourceLocation.class).invoke(value,ResourceLocation.parse(id));
        return (ItemStack)builder.getMethod("build").invoke(value);
    }
    static List<Group> gunOptions(ItemStack gun)throws ReflectiveOperationException{
        Class<?> api=Class.forName("com.tacz.guns.api.TimelessAPI"),igun=Class.forName("com.tacz.guns.api.item.IGun");
        Object item=igun.getMethod("getIGunOrNull",ItemStack.class).invoke(null,gun);
        Model.require(item!=null,"该物品不是 TaCZ 枪械");
        Map<String,List<Option>> groups=new LinkedHashMap<>();
        for(Object obj:(Iterable<?>)api.getMethod("getAllCommonAttachmentIndex").invoke(null)){
            var entry=(Map.Entry<?,?>)obj;String id=entry.getKey().toString();
            Object type=entry.getValue().getClass().getMethod("getType").invoke(entry.getValue());
            ItemStack stack=attachment(id);
            if((boolean)igun.getMethod("allowAttachment",ItemStack.class,ItemStack.class).invoke(item,gun,stack)
                    &&(boolean)igun.getMethod("allowAttachmentType",ItemStack.class,type.getClass()).invoke(item,gun,type)){
                groups.computeIfAbsent(type.toString(),k->new ArrayList<>()).add(new Option(id,stack.getHoverName().getString(),"attachment",id));
            }
        }
        return groups.entrySet().stream().map(e->new Group(e.getKey(),slotName(e.getKey()),e.getValue())).toList();
    }
    static String slotName(String type){return switch(type){case "SCOPE"->"瞄具";case "MUZZLE"->"枪口";case "GRIP"->"握把";case "STOCK"->"枪托";case "EXTENDED_MAG"->"弹匣";case "LASER"->"激光";default->type;};}
    static void applyGun(ItemStack gun,Map<String,String> selected,HolderLookup.Provider registry)throws ReflectiveOperationException{
        if(selected.isEmpty())return;
        validate(gunOptions(gun),selected);
        Class<?> type=Class.forName("com.tacz.guns.api.item.IGun");Object item=type.getMethod("getIGunOrNull",ItemStack.class).invoke(null,gun);
        for(String id:selected.values())if(!id.isEmpty()){
            ItemStack attachment=attachment(id);
            Model.require((boolean)type.getMethod("allowAttachment",ItemStack.class,ItemStack.class).invoke(item,gun,attachment),"配件组合不兼容："+id);
            type.getMethod("installAttachment",HolderLookup.Provider.class,ItemStack.class,ItemStack.class).invoke(item,registry,gun,attachment);
        }
    }
    static List<Group> vehicleOptions(Entity vehicle)throws ReflectiveOperationException{
        List<Group> out=new ArrayList<>();Object all=VehicleEquip.weapons(vehicle);
        if(!(all instanceof Map<?,?> map))return out;
        for(var weapon:map.entrySet()){
            List<Option> options=new ArrayList<>();int index=0;
            for(Object consumer:(Iterable<?>)weapon.getValue().getClass().getMethod("getProcessedAmmoConsumers").invoke(weapon.getValue())){
                consumer.getClass().getMethod("init").invoke(consumer);
                ItemStack stack=(ItemStack)consumer.getClass().getMethod("getStack").invoke(consumer);
                String raw=String.valueOf(consumer.getClass().getMethod("getAmmo").invoke(consumer));
                options.add(new Option(""+index++,stack.isEmpty()?raw:stack.getHoverName().getString(),"item",stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            }
            if(!options.isEmpty())out.add(new Group(weapon.getKey().toString(),"武器站 · "+weapon.getKey(),options));
        }
        return out;
    }
    static void applyVehicle(Entity vehicle,Map<String,String> selected)throws ReflectiveOperationException{
        if(selected.isEmpty())return;
        validate(vehicleOptions(vehicle),selected);
        for(var choice:selected.entrySet())if(!choice.getValue().isEmpty()){
            Object data=vehicle.getClass().getMethod("getGunData",String.class).invoke(vehicle,choice.getKey());
            Model.require(data!=null,"载具武器站不可用："+choice.getKey());
            data.getClass().getMethod("changeAmmoConsumer",int.class,Entity.class).invoke(data,Integer.parseInt(choice.getValue()),vehicle);
            java.util.function.Consumer<Object> persist=value->{};
            vehicle.getClass().getMethod("modifyGunData",String.class,java.util.function.Consumer.class).invoke(vehicle,choice.getKey(),persist);
        }
    }
    /**
     * Fits the attachments the operator configured for this vehicle.
     *
     * <p>Superb Warfare stores a weapon's attachments as an integer index per slot on the gun's own data
     * ({@code GunData.attachment}), reached through the vehicle's {@code getGunData(name)}. The slot
     * names are the five the addon defines, which is why they are a fixed list rather than something read
     * back: the enum is the API.
     *
     * <p>Everything is best-effort. A weapon that defines no attachments, an index it does not have, or a
     * future addon version that renames a method must not prevent a vehicle from being deployed — the
     * caller logs and continues.
     */
    static void applyVehicleAttachments(Entity vehicle, VehicleArmament armament)
            throws ReflectiveOperationException {
        if (vehicle == null || armament == null || armament.stations.isEmpty()) return;
        Class<?> typeClass = Class.forName("com.atsuishio.superbwarfare.data.gun.value.AttachmentType");
        for (VehicleArmament.Station station : armament.stations) {
            if (station == null || station.attachments.isEmpty()) continue;
            Object data;
            try {
                data = vehicle.getClass().getMethod("getGunData", String.class)
                        .invoke(vehicle, station.station);
            } catch (ReflectiveOperationException ex) {
                BattlefieldOps.LOG.warn("No gun data for station " + station.station, ex);
                continue;
            }
            if (data == null) continue;
            for (var entry : station.attachments.entrySet()) {
                try {
                    Object slot = Enum.valueOf(typeClass.asSubclass(Enum.class), entry.getKey());
                    Object attachment = data.getClass().getField("attachment").get(data);
                    if (attachment == null) continue;
                    attachment.getClass().getMethod("set", typeClass, int.class)
                            .invoke(attachment, slot, entry.getValue());
                } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                    BattlefieldOps.LOG.warn("Cannot set attachment "
                            + entry.getKey() + " on station " + station.station, ex);
                }
            }
        }
    }

    static void validate(List<Group> groups,Map<String,String> selected){
        Model.require(selected.size()<=32,"改装项目过多");
        for(var entry:selected.entrySet()){
            Group group=groups.stream().filter(g->g.id.equals(entry.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("改装槽不存在："+entry.getKey()));
            Model.require(entry.getValue().isEmpty()||group.options.stream().anyMatch(o->o.id.equals(entry.getValue())),"该配件或弹种不兼容："+entry.getValue());
        }
    }

    /**
     * Applies the map's armament limits on top of the compatibility check.
     *
     * <p>{@link #validate} answers "can this weapon physically take it". This answers "does the operator
     * allow it", which is a different question and the one that has to be enforced server-side: without
     * it the restriction is only cosmetic, because a client can send any station index the weapon
     * reports as compatible.
     *
     * <p>Each vehicle station's option id is the index into that station's processed ammunition
     * consumers, and the option's {@code icon} is the ammunition item id. The allow-list is matched on
     * the item id, which is what the operator configured; a station whose ids cannot be resolved to items
     * is left alone rather than refused, because refusing to start a tank over an unreadable addon
     * format is worse than allowing its default rounds.
     */
    static void restrict(List<Group> groups, Map<String, String> selected, VehicleArmament armament) {
        if (armament == null || armament.stations.isEmpty()) return;
        for (var choice : selected.entrySet()) {
            if (choice.getValue() == null || choice.getValue().isEmpty()) continue;
            VehicleArmament.Station station = armament.forStation(choice.getKey());
            if (station == null || station.ammo.isEmpty()) continue;
            Group group = groups.stream().filter(g -> g.id.equals(choice.getKey())).findFirst().orElse(null);
            if (group == null) continue;
            Option option = group.options.stream()
                    .filter(o -> o.id.equals(choice.getValue())).findFirst().orElse(null);
            if (option == null) continue;
            String itemId = option.icon == null ? "" : option.icon;
            if (itemId.isEmpty()) continue;
            Model.require(station.allowsAmmo(itemId),
                    "该载具的「" + choice.getKey() + "」武器站不允许使用此弹药：" + itemId);
        }
    }
}
