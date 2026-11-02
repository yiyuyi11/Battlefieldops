package cn.bfops;

import java.util.*;

/** Reference constants: packs 5s, boxes 16 uses / 30s per recipient, sensor 4s / 10m / 2s. */
final class SupportRules {
    enum Kind {
        AMMO_BAG("ammo_bag","弹药包",100),MEDIC_BAG("medic_bag","医疗包",100),
        AMMO_BOX("ammo_box","弹药箱",100),MED_BOX("med_box","医疗箱",100),
        RADIO_BEACON("radio_beacon","复活信标",1200),GROUND_SENSOR("ground_sensor","地面传感器",100);
        final String id,label;final int cooldown;
        Kind(String id,String label,int cooldown){this.id=id;this.label=label;this.cooldown=cooldown;}
        String key(){return "battlefield_ops:"+id;}
        static Kind parse(String id){if(id==null)return null;String value=id.substring(id.indexOf(':')+1);return Arrays.stream(values()).filter(k->k.id.equals(value)).findFirst().orElse(null);}
    }
    static final int BOX_USES=16,BOX_COOLDOWN=600,BOX_LIFE=12000,SENSOR_INTERVAL=80,SENSOR_MARK_TICKS=40,BEACON_USES=6;
    static int consumeBeacon(int current,boolean deployed){return Math.max(0,current-(deployed?1:0));}
    static String feedbackIcon(String label){return label.contains("医疗")?"medic":label.contains("信标")?"rescue":"mark";}
    static final double SENSOR_RADIUS=10,BOX_RADIUS=2;
    static final class Budget {
  int remaining;final long cooldown;final Map<UUID,Long> cooldowns=new HashMap<>();
  Budget(int remaining){this(remaining,BOX_COOLDOWN);}
  Budget(int remaining,long cooldown){this.remaining=remaining;this.cooldown=cooldown;}
        boolean ready(UUID player,long now){return remaining>0&&now>=cooldowns.getOrDefault(player,Long.MIN_VALUE);}
  boolean consume(UUID player,long now,boolean changed){if(!changed||!ready(player,now))return false;remaining--;cooldowns.put(player,cooldown<=0?now:now+cooldown);return true;}
    }
    static boolean sameRoomTeam(String a,int teamA,String b,int teamB){return a!=null&&a.equals(b)&&teamA>=0&&teamA==teamB;}
    static boolean sensorContact(String map,int team,String targetMap,int targetTeam,boolean alive,double distanceSquared){
        return map!=null&&map.equals(targetMap)&&team>=0&&targetTeam>=0&&team!=targetTeam&&alive
                &&Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=SENSOR_RADIUS*SENSOR_RADIUS;
    }
}
