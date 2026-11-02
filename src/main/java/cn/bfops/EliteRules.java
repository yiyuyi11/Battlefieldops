package cn.bfops;

import java.util.*;

/** Fixed reference hero types. These equipment templates are never deployment classes. */
final class EliteRules {
    enum Kind {
        ANTIVEHICLE("antivehicle","反载具兵"),FLAMETROOPER("flametrooper","喷火兵"),RUNNER("runner","突击兵（精英）"),SENTRY("sentry","哨戒兵"),TRENCHRAIDER("trenchraider","堑壕兵");
        final String key,name;
        Kind(String key,String name){this.key=key;this.name=name;}
        static Kind fromInput(String input){
            if(input==null)return null;
            for(var k:values())if(k.key.equalsIgnoreCase(input)||k.name.equals(input))return k;
            if(input.equals("反载具"))return ANTIVEHICLE;
            if(input.equals("精英突击兵"))return RUNNER;
            return null;
        }
        static Kind of(String key){for(var k:values())if(k.key.equals(key))return k;return null;}
    }
    static final class Template {
        public String type="antivehicle";
        public Model.Kit kit=new Model.Kit();
    }
    static final class Spawn {
        public String id="精英刷新点1",type="antivehicle";
        public double x,y=64,z,radius=3;
        public int team=-1,sector=-1,respawnSeconds=180,initialDelaySeconds=0;
        public boolean enabled=true;
    }
    static void complete(Model.Arena map){
        if(map.eliteKits==null)map.eliteKits=new ArrayList<>();
        for(var kind:Kind.values())if(map.eliteKits.stream().noneMatch(t->t!=null&&kind.key.equals(t.type))){
            Template t=new Template();t.type=kind.key;t.kit.id=kind.name;map.eliteKits.add(t);
        }
    }
    static Template template(Model.Arena map,String type){return map.eliteKits.stream().filter(t->t.type.equals(type)).findFirst().orElse(null);}
    static boolean configured(Template template){return template!=null&&(!template.kit.primary.isEmpty()||!template.kit.secondary.isEmpty()||!template.kit.gear().items.isEmpty()||!template.kit.gear().armour.isEmpty());}
    static void validate(Model.Arena map){
        Model.require(map.eliteKits!=null&&map.eliteKits.size()<=5,"精英兵仅允许参考端固定五类");
        Set<String> types=new HashSet<>();
        for(var t:map.eliteKits){
            Kind kind=t==null?null:Kind.of(t.type);
            Model.require(kind!=null&&types.add(t.type)&&t.kit!=null&&kind.name.equals(t.kit.id),"精英兵不能新增、重复或改名");
            t.kit.validateOptions();Model.require(t.kit.primary!=null&&t.kit.secondary!=null&&t.kit.primary.length()<160&&t.kit.secondary.length()<160,"精英武器 ID 无效");
            Model.range(t.kit.ammoStacks,0,12,"精英备用弹药组数");
        }
        Model.require(map.eliteSpawns!=null&&map.eliteSpawns.size()<=32,"精英刷新点最多 32 个");Set<String> ids=new HashSet<>();
        for(var s:map.eliteSpawns){
            Model.require(s!=null&&s.id!=null&&!s.id.isBlank()&&s.id.length()<=32&&ids.add(s.id),"精英刷新点名称无效或重复");
            Model.require(Kind.of(s.type)!=null,"精英刷新点类型无效");
            Model.require(Double.isFinite(s.x)&&Double.isFinite(s.y)&&Double.isFinite(s.z)&&Model.contains(map.boundary,s.x,s.z)&&s.y>=map.minY&&s.y<=map.maxY,"精英刷新点必须位于地图边界和高度内");
            Model.require(Double.isFinite(s.radius)&&s.radius>=1&&s.radius<=8,"精英领取距离为 1～8 格");
            Model.range(s.team,-1,1,"精英刷新点阵营");Model.range(s.sector,-1,15,"精英刷新点阶段");
            Model.range(s.respawnSeconds,0,3600,"精英刷新冷却");Model.range(s.initialDelaySeconds,0,3600,"精英首次刷新延迟");
        }
    }
    static long delayRemaining(long start,Spawn point,long now){return Math.max(0,start+point.initialDelaySeconds*20L-now);}
    static String scope(int team,String type){return team+":"+type;}
    static boolean reachable(Spawn s,int team,int stage,boolean breakthrough,double x,double y,double z){
        return s.enabled&&(s.team<0||s.team==team)&&(!breakthrough||s.sector<0||s.sector==stage)&&
            (s.x-x)*(s.x-x)+(s.y-y)*(s.y-y)+(s.z-z)*(s.z-z)<=s.radius*s.radius;
    }
    private EliteRules(){}
}
