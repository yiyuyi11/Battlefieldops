package cn.bfops.crawl.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Entity.class})
public abstract class EntityProneMovementMixin {
   @ModifyVariable(
      method = {"move"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Vec3 gd656taczcrawtweak$clipProneMovement(Vec3 movement, MoverType moverType) {
      if ((Object)this instanceof Player player) {
         return moverType != MoverType.SELF && moverType != MoverType.PLAYER ? movement : TaczPronePartService.clipProneMovement(player, movement);
      } else {
         return movement;
      }
   }
}
