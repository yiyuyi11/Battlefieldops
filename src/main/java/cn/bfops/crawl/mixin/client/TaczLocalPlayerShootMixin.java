package cn.bfops.crawl.mixin.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.client.player.LocalPlayer;
import cn.bfops.crawl.compat.tacz.TaczCrawlShootLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
   targets = {"com.tacz.guns.client.gameplay.LocalPlayerShoot"},
   remap = false
)
public abstract class TaczLocalPlayerShootMixin {
   @Shadow
   @org.spongepowered.asm.mixin.Final
   private LocalPlayer player;

   @Inject(
      method = {"shoot"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gd656taczcrawtweak$blockShootWhenCrawlStateChanging(CallbackInfoReturnable<ShootResult> cir) {
      if(!cn.bfops.Client.inBattleRoom())return;
      if (this.player != null && this.player.getUUID() != null) {
         if(cn.bfops.CrawlPose.downed(this.player)){cir.setReturnValue(ShootResult.COOL_DOWN);return;}
         boolean crawling = IClientPlayerGunOperator.fromLocalPlayer(this.player).isCrawl();
         boolean blocked = TaczCrawlShootLimiter.shouldBlockClientShoot(this.player.getUUID(), crawling, System.currentTimeMillis());
         if (blocked) {
            cir.setReturnValue(ShootResult.COOL_DOWN);
         }
      }
   }
}
