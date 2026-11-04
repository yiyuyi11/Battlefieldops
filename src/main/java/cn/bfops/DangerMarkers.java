package cn.bfops;
import java.util.*;import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.*;import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.registries.BuiltInRegistries;import net.minecraft.world.effect.MobEffectInstance;import net.minecraft.world.effect.MobEffectCategory;
/** Nearby loaded hazards only; attribution and room membership are server-authoritative. */
final class DangerMarkers {
 private static final double RANGE=32;
 private static final Map<Class<?>,Predicate<Entity>> classifiers=new HashMap<>();
 private static double number(Object value,String method){try{return ((Number)value.getClass().getMethod(method).invoke(value)).doubleValue();}catch(ReflectiveOperationException|RuntimeException ex){return -1;}}
 private static Predicate<Entity> classifier(Entity sample){
  String name=sample.getClass().getName(),path=BuiltInRegistries.ENTITY_TYPE.getKey(sample.getType()).getPath().toLowerCase(Locale.ROOT);
  if(path.contains("smoke")||path.contains("decoy")||path.contains("stun")||path.contains("flash"))return e->false;
  if(name.startsWith("me.xjqsh.lrtactical.")){
   try{var damage=sample.getClass().getMethod("getDamage");return e->{try{return ((Number)damage.invoke(e)).doubleValue()>0;}catch(ReflectiveOperationException ex){return false;}};}catch(NoSuchMethodException ignored){}
   try{var cloud=sample.getClass().getMethod("getCloudData");return e->{try{Object data=cloud.invoke(e);if(Boolean.TRUE.equals(data.getClass().getMethod("isIgnite").invoke(data)))return true;for(Object effect:(List<?>)data.getClass().getMethod("getEffectInstances").invoke(data))if(effect instanceof MobEffectInstance m&&m.getEffect().getCategory()==MobEffectCategory.HARMFUL)return true;}catch(ReflectiveOperationException|RuntimeException ignored){}return false;};}catch(NoSuchMethodException ignored){}
   return e->false;
  }
  // TaCZ uses the same kinetic entity for ordinary and explosive rounds; read its actual flag.
  for(Class<?> type=sample.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals("com.tacz.guns.entity.EntityKineticBullet")){
   try{var active=type.getDeclaredField("explosion");var damage=type.getDeclaredField("explosionDamage");active.setAccessible(true);damage.setAccessible(true);return e->{try{return active.getBoolean(e)&&damage.getFloat(e)>0;}catch(IllegalAccessException ex){return false;}};}catch(ReflectiveOperationException|RuntimeException ignored){return e->false;}
  }
  String ns=BuiltInRegistries.ENTITY_TYPE.getKey(sample.getType()).getNamespace();
  if(!(ns.equals("superbwarfare")||ns.startsWith("tacz")||name.startsWith("com.atsuishio.superbwarfare.")))return e->false;
  if(!(path.contains("grenade")||path.contains("bomb")||path.contains("mine")||path.contains("claymore")||path.equals("c4")||path.contains("molotov")))return e->false;
  return e->{double damage=number(e,"getExplosionDamage");return damage<0||damage>0;};
 }
 private static UUID owner(Entity e){
  Entity current=e;for(int i=0;i<4;i++){
   if(current instanceof ServerPlayer p)return p.getUUID();
   if(current instanceof Projectile projectile){current=projectile.getOwner();if(current==null)return null;continue;}
   if(current instanceof OwnableEntity owned)return owned.getOwnerUUID();
   if(current.getFirstPassenger()!=null){current=current.getFirstPassenger();continue;}
   return null;
  }return null;
 }
 static List<Map<String,Object>> snapshot(ServerPlayer observer,Model.Arena map,Engine.Member viewer,Engine engine){
  if(viewer==null||!viewer.alive||viewer.team<0||!observer.level().dimension().location().toString().equals(map.dimension))return List.of();
  var entities=observer.level().getEntities(observer,observer.getBoundingBox().inflate(RANGE),e->!e.isRemoved()&&(e instanceof Projectile||e instanceof OwnableEntity)&&classifiers.computeIfAbsent(e.getClass(),c->classifier(e)).test(e));
  var out=new ArrayList<Map<String,Object>>();entities.sort(Comparator.comparingDouble(observer::distanceToSqr));
  for(Entity entity:entities){if(observer.distanceToSqr(entity)>RANGE*RANGE)continue;
   UUID owner=owner(entity);Engine.Member member=owner==null?null:engine.members.get(owner);
   if(member==null||member.team<0||member.team==viewer.team||!Objects.equals(member.map,viewer.map)||!Model.contains(map.boundary,entity.getX(),entity.getZ()))continue;
   out.add(Map.of("id",entity.getId(),"uuid",entity.getUUID().toString(),"x",entity.getX(),"y",entity.getY()+.3,"z",entity.getZ(),"kind","GRENADE_HOSTILE"));if(out.size()>=32)break;
  }return out;
 }
 private DangerMarkers(){}
}
