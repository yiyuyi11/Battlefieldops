package cn.bfops.crawl.mixin.client;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
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
   targets = {"com.tacz.guns.client.gameplay.LocalPlayerCrawl"},
   priority = 2000,
   remap = false
)
public abstract class TaczLocalPlayerCrawlMixin {
   @Shadow
   @Final
   private static int COOLDOWN_TICKS;
   @Shadow
   @org.spongepowered.asm.mixin.Final
   private LocalPlayer player;
   @Shadow
   private boolean isCrawling;
   @Shadow
   private int crawCooldownTicks;

   @Inject(
      method = {"crawl"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gd656taczcrawtweak$crawl(boolean isCrawl, CallbackInfo ci) {
      if (isCrawl && this.crawCooldownTicks > 0) {
         ci.cancel();
      } else if (isCrawl && (this.player.isSpectator() || this.player.isPassenger() || !this.player.onGround())) {
         ci.cancel();
      } else if (isCrawl && !TaczPronePartService.prepareProneEntry(this.player)) {
         ci.cancel();
      } else {
         this.isCrawling = isCrawl;
         this.crawCooldownTicks = COOLDOWN_TICKS;
         TaczPronePartService.onCrawlStateChanged(this.player, isCrawl);
         this.gd656taczcrawtweak$updateClientCrawlState();
         if(cn.bfops.MatchView.clearingProne()){ci.cancel();return;}
         if(cn.bfops.MatchView.canProne()){
            var packet=cn.bfops.Client.request("prone",cn.bfops.Client.currentMap());packet.addProperty("value",isCrawl);cn.bfops.Client.send(packet);
         }else cn.bfops.Network.sendToServer(new ClientMessagePlayerCrawl(isCrawl));
         ci.cancel();
      }
   }

   @Inject(
      method = {"tickCrawl"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gd656taczcrawtweak$tickCrawl(CallbackInfo ci) {
      // The movement addon owns its own prone pose; do not clear it every tick.
      if (cn.bfops.MovementHandoff.proneOwned(this.player)) {
         ci.cancel();
         return;
      }
      if (this.crawCooldownTicks > 0) {
         this.crawCooldownTicks--;
      }

      boolean jumping = ((LivingEntityJumpingAccessor)this.player).gd656taczcrawtweak$isJumping();
      String cancelReason = this.gd656taczcrawtweak$getCancelReason(jumping);
      if (cancelReason != null) {
         this.isCrawling = false;
      }

      if (this.isCrawling) {
         cn.bfops.CrawlPose.force(this.player,Pose.SWIMMING);
      } else {
         cn.bfops.CrawlPose.force(this.player,null);
      }

      TaczPronePartService.onCrawlStateChanged(this.player, this.isCrawling);
      this.gd656taczcrawtweak$updateClientCrawlState();
      ci.cancel();
   }

   @Unique
   private String gd656taczcrawtweak$getCancelReason(boolean jumping) {
      if (this.player.isSpectator()) {
         return "spectator";
      } else if (this.player.isPassenger()) {
         return "passenger";
      } else if (jumping) {
         return "jumping";
      } else if (this.player.isSwimming()) {
         return "swimming";
      } else {
         return !this.player.onGround() ? "not_on_ground" : null;
      }
   }

   private void gd656taczcrawtweak$updateClientCrawlState() {
      if (this.player != null && this.player.getUUID() != null) {
         TaczCrawlShootLimiter.updateClientCrawlState(this.player.getUUID(), this.isCrawling, System.currentTimeMillis());
      }
   }
}
