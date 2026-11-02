package cn.bfops.crawl.mixin.server;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import cn.bfops.crawl.mixin.accessor.LivingEntityJumpingAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.tacz.guns.entity.shooter.LivingEntityCrawl"},
   priority = 2000,
   remap = false
)
public abstract class TaczLivingEntityCrawlMixin {
   @Shadow
   @Final
   private LivingEntity shooter;
   @Shadow
   @Final
   private ShooterDataHolder data;

   @Inject(
      method = {"crawl"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gd656taczcrawtweak$crawl(boolean isCrawl, CallbackInfo ci) {
      if (this.shooter instanceof Player player) {
         if (isCrawl && !TaczPronePartService.prepareProneEntry(player)) {
            this.data.isCrawling = false;
            TaczPronePartService.onCrawlStateChanged(player, false);
            ci.cancel();
            return;
         }

         TaczPronePartService.onCrawlStateChanged(player, isCrawl);
      }

      this.data.isCrawling = isCrawl;
      ci.cancel();
   }

   @Inject(
      method = {"tickCrawling"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gd656taczcrawtweak$tickCrawling(CallbackInfo ci) {
      if (this.shooter instanceof Player player && cn.bfops.MovementHandoff.proneOwned(player)) {
         ci.cancel();
         return;
      }
      boolean jumping = ((LivingEntityJumpingAccessor)this.shooter).gd656taczcrawtweak$isJumping();
      String cancelReason = this.gd656taczcrawtweak$getCancelReason(jumping);
      if (cancelReason != null) {
         this.data.isCrawling = false;
      }

      if (this.data.isCrawling) {
         if (this.shooter instanceof Player player) {
            cn.bfops.CrawlPose.force(player,Pose.SWIMMING);
         } else {
            this.shooter.setPose(Pose.SWIMMING);
         }
      } else if (this.shooter instanceof Player player) {
         cn.bfops.CrawlPose.force(player,null);
      }

      if (this.shooter instanceof Player player) {
         TaczPronePartService.onCrawlStateChanged(player, this.data.isCrawling);
      }

      this.gd656taczcrawtweak$updateServerCrawlState();
      ci.cancel();
   }

   @Unique
   private String gd656taczcrawtweak$getCancelReason(boolean jumping) {
      if (this.shooter.isSpectator()) {
         return "spectator";
      } else if (this.shooter.isPassenger()) {
         return "passenger";
      } else if (jumping) {
         return "jumping";
      } else if (this.shooter.isSwimming()) {
         return "swimming";
      } else {
         return !this.shooter.onGround() ? "not_on_ground" : null;
      }
   }

   private void gd656taczcrawtweak$updateServerCrawlState() {
      if (this.shooter != null && this.shooter.getUUID() != null && this.data != null) {
         TaczCrawlShootLimiter.updateServerCrawlState(this.shooter.getUUID(), this.data.isCrawling, System.currentTimeMillis());
      }
   }
}
