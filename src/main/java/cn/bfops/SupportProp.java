package cn.bfops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

final class SupportProp extends ArmorStand {
    private final ConquestSupport runtime;
    SupportProp(ServerLevel level,double x,double y,double z,ConquestSupport runtime){super(level,x,y,z);this.runtime=runtime;PropVitals.initialize(this);}
    @Override public boolean hurt(DamageSource source,float amount){return amount>0&&runtime.damage(this,source,amount);}
    @Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand){return InteractionResult.FAIL;}
}
