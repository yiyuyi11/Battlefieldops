package cn.bfops;

import java.util.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Owned support devices use model-sized hitboxes and ten health, never vanilla stand break rules. */
public final class PropVitals {
    public static final float MAX_HEALTH=10;
    private static final Map<SupportRules.Kind,EntityDimensions> DIMENSIONS=new EnumMap<>(SupportRules.Kind.class);
    public static boolean owned(ArmorStand entity){return ConquestEquipment.kind(entity.getItemBySlot(EquipmentSlot.HEAD))!=null;}
    public static EntityDimensions dimensions(ArmorStand entity){
        var kind=ConquestEquipment.kind(entity.getItemBySlot(EquipmentSlot.HEAD));if(kind==null)return null;
        return DIMENSIONS.computeIfAbsent(kind,k->{
            float width=.6f,height=.6f;
            try(var stream=PropVitals.class.getResourceAsStream("/assets/battlefield_ops/geo/"+k.id+".geo.json")){
                if(stream!=null){
                    var mesh=ReferenceGeometry.bake(com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                    float scale=switch(k){case AMMO_BOX,MED_BOX->.88f;case RADIO_BEACON->1.1f;case AMMO_BAG,MEDIC_BAG->2;default->1;};
                    float half=0,top=0;
                    for(var v:mesh.vertices()){half=Math.max(half,Math.max(Math.abs(v.x()),Math.abs(v.z()))*scale);top=Math.max(top,v.y()*scale+.05f);}
                    width=Math.clamp(half*2+.06f,.15f,4);height=Math.clamp(top+.06f,.12f,4);
                }
            }catch(Exception ex){BattlefieldOps.LOG.warn("Cannot derive support hitbox for {}",k.id,ex);}
            return EntityDimensions.scalable(width,height);
        });
    }
    static void initialize(ArmorStand entity){
        var attribute=entity.getAttribute(Attributes.MAX_HEALTH);if(attribute!=null)attribute.setBaseValue(MAX_HEALTH);
        entity.setHealth(MAX_HEALTH);entity.setNoGravity(true);entity.setInvisible(true);
    }
    static boolean damage(ArmorStand entity,float amount){
        if(!Float.isFinite(amount)||amount<=0||!entity.isAlive())return false;
        entity.setHealth(Math.max(0,entity.getHealth()-amount));
        entity.level().broadcastEntityEvent(entity,(byte)2);
        return entity.getHealth()<=0;
    }
    private PropVitals(){}
}
