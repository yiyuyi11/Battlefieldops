package cn.bfops;

import java.util.*;

/** Reference PlayerClassType: exactly four identities; equipment is configured inside these slots. */
final class FixedClasses {
    enum Role {
        ASSAULT("突击兵","assault","1"),SUPPORT("支援兵","support","2"),ENGINEER("工程兵","engineer","3"),RECON("侦察兵","recon","4");
        final String name,stem,digit;
        Role(String name,String stem,String digit){this.name=name;this.stem=stem;this.digit=digit;}
    }
    static Role resolve(String id){
        if(id==null)return null;String key=id.trim().toLowerCase(Locale.ROOT);
        if(key.equals("medic")||key.equals("医疗兵"))return Role.SUPPORT;
        for(var role:Role.values())if(key.equals(role.name)||key.equals(role.stem)||key.equals(role.digit))return role;
        return null;
    }
    static String stem(String id){Role role=resolve(id);return role==null?null:role.stem;}
    static void validateActive(Model.Arena map){
        Model.require(map.kits!=null&&!map.kits.isEmpty()&&map.kits.size()<=4,"兵种只能为参考端四类，不能新建自定义兵种");
        Set<Role> seen=EnumSet.noneOf(Role.class);
        for(var kit:map.kits){
            Role role=kit==null?null:resolve(kit.id);
            Model.require(role!=null&&kit.id.equals(role.name)&&seen.add(role),"兵种仅允许突击兵、支援兵、工程兵、侦察兵各一个，不能改名或重复");
        }
    }
    /** Missing slots are restored, so deleting a slot in a forged save cannot remove a fixed class. */
    static void complete(Model.Arena map){
        validateActive(map);EnumMap<Role,Model.Kit> chosen=new EnumMap<>(Role.class);
        for(var kit:map.kits)chosen.put(resolve(kit.id),kit);
        map.kits=new ArrayList<>();
        for(var role:Role.values()){
            Model.Kit kit=chosen.get(role);if(kit==null){kit=new Model.Kit();kit.id=role.name;}
            map.kits.add(kit);
        }
    }
    /** Read-time compatibility only. Save requests use strict validation, never this migration. */
    static boolean migrateLegacy(Model.Arena map){
        Model.require(map.kits!=null&&map.kits.size()<=16,"旧版配装数量无效");
        if(map.legacyKits==null)map.legacyKits=new ArrayList<>();
        List<Model.Kit> source=new ArrayList<>(map.kits);
        Model.require(source.stream().noneMatch(Objects::isNull),"旧版配装不能为空");
        boolean movedElite=false;
        if(map.eliteKits==null)map.eliteKits=new ArrayList<>();
        for(var iterator=source.iterator();iterator.hasNext();){
            var kit=iterator.next();var hero=EliteRules.Kind.fromInput(kit.id);
            if(hero!=null&&resolve(kit.id)==null){
                if(EliteRules.template(map,hero.key)==null){var template=new EliteRules.Template();template.type=hero.key;template.kit=kit;kit.id=hero.name;map.eliteKits.add(template);}
                else map.legacyKits.add(kit);
                iterator.remove();movedElite=true;
            }
        }
        boolean changed=movedElite||source.size()!=4;
        for(int i=0;i<source.size();i++)if(i>=4||!source.get(i).id.equals(Role.values()[i].name))changed=true;
        if(!changed)return false;
        EnumMap<Role,Model.Kit> chosen=new EnumMap<>(Role.class);Set<Model.Kit> used=Collections.newSetFromMap(new IdentityHashMap<>());
        // Exact identities win over aliases, independent of the old list's order.
        for(var role:Role.values())for(var kit:source)if(role.name.equals(kit.id)&&!chosen.containsKey(role)){chosen.put(role,kit);used.add(kit);}
        for(var kit:source){var role=resolve(kit.id);if(!used.contains(kit)&&role!=null&&!chosen.containsKey(role)){chosen.put(role,kit);used.add(kit);}}
        for(var kit:source)if(!used.contains(kit)&&resolve(kit.id)==null){
            for(var role:Role.values())if(!chosen.containsKey(role)){chosen.put(role,kit);used.add(kit);break;}
        }
        for(var kit:source)if(!used.contains(kit))map.legacyKits.add(kit);
        map.kits=new ArrayList<>();
        for(var role:Role.values()){
            var kit=chosen.get(role);if(kit==null)kit=new Model.Kit();kit.id=role.name;map.kits.add(kit);
        }
        return true;
    }
    /** Swap an archived loadout into a fixed identity, retaining the replaced equipment in the archive. */
    static void restore(Model.Arena map,int roleIndex,int legacyIndex){
        complete(map);Model.require(legacyIndex>=0&&legacyIndex<map.legacyKits.size(),"旧配装不存在");
        Model.require(roleIndex>=0&&roleIndex<4,"兵种无效");
        Model.Kit incoming=map.legacyKits.get(legacyIndex),previous=map.kits.get(roleIndex);
        String oldName=incoming.id;incoming.id=Role.values()[roleIndex].name;
        previous.id=oldName;map.legacyKits.set(legacyIndex,previous);map.kits.set(roleIndex,incoming);
    }
    private FixedClasses(){}
}
