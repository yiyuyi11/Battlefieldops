package cn.bfops.crawl.mixin.server;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
   targets = {"com.tacz.guns.entity.shooter.LivingEntityShoot"},
   remap = false
)
public abstract class TaczLivingEntityShootMixin {
   @Shadow
   @org.spongepowered.asm.mixin.Final
   private LivingEntity shooter;

   @Inject(
      method = {"shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gd656taczcrawtweak$blockShootWhenCrawlStateChanging(
      Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, CallbackInfoReturnable<ShootResult> cir
   ) {
      if(cn.bfops.BattlefieldOps.engine==null||this.shooter==null||!cn.bfops.BattlefieldOps.engine.members.containsKey(this.shooter.getUUID()))return;
      if (this.shooter != null) {
         if(this.shooter instanceof net.minecraft.world.entity.player.Player p && (cn.bfops.CrawlPose.downed(p)||(cn.bfops.BattlefieldOps.engine!=null&&cn.bfops.BattlefieldOps.engine.isDowned(p)))){cir.setReturnValue(ShootResult.COOL_DOWN);return;}
         UUID shooterId = this.shooter.getUUID();
         if (shooterId != null) {
            boolean crawling = IGunOperator.fromLivingEntity(this.shooter).getDataHolder().isCrawling;
            boolean blocked = TaczCrawlShootLimiter.shouldBlockServerShoot(shooterId, crawling, System.currentTimeMillis());
            if (blocked) {
               cir.setReturnValue(ShootResult.COOL_DOWN);
            }
         }
      }
   }
   @Inject(method="shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JF)Lcom/tacz/guns/api/entity/ShootResult;",at=@At("HEAD"),cancellable=true,require=0)
   private void bfops$charged(Supplier<Float> pitch,Supplier<Float> yaw,long timestamp,float charge,CallbackInfoReturnable<ShootResult> cir){
       gd656taczcrawtweak$blockShootWhenCrawlStateChanging(pitch,yaw,timestamp,cir);
   }
}
