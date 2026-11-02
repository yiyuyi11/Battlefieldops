package cn.bfops.crawl.mixin.server;

import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
   targets = {"com.tacz.guns.util.EntityUtil"},
   remap = false
)
public abstract class TaczEntityUtilProneHitboxMixin {
   @Inject(
      method = {"getHitResult"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void gd656taczcrawtweak$resolvePronePartHit(
      Projectile bulletEntity, Entity entity, Vec3 startVec, Vec3 endVec, CallbackInfoReturnable<EntityResult> cir
   ) {
      TaczPronePartService.ProneHitResult hit = TaczPronePartService.tryResolvePartHit(entity, startVec, endVec);
      if (hit != null) {
         cir.setReturnValue(new EntityResult(hit.hitPart(), hit.hitPos(), hit.headshot()));
      }
   }
}
