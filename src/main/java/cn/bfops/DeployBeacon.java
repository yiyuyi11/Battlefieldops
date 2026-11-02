package cn.bfops;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/** Uses vanilla network visuals; server-only behaviour avoids a mandatory client entity type. */
final class DeployBeacon extends ArmorStand {
    DeployBeacon(ServerLevel level,double x,double y,double z){super(level,x,y,z);PropVitals.initialize(this);}
    @Override public boolean hurt(DamageSource source,float amount) {
        return amount>0 && BattlefieldOps.engine!=null && BattlefieldOps.engine.damageBeacon(this,source,amount);
    }
    @Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand) {
        return InteractionResult.FAIL; // Not an equipment container.
    }
}
