package cn.bfops.crawl.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import cn.bfops.crawl.compat.tacz.TaczPronePartService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({MouseHandler.class})
public abstract class TaczMouseHandlerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Redirect(
      method = {"turnPlayer"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )
   )
   private void gd656taczcrawtweak$applyProneRotationDamping(LocalPlayer player, double yawTurn, double pitchTurn) {
      if (player != null && player == this.minecraft.player) {
         double adjustedYawTurn = TaczPronePartService.dampClientYawTurn(player, yawTurn);
         player.turn(adjustedYawTurn, pitchTurn);
      } else {
         player.turn(yawTurn, pitchTurn);
      }
   }
}
