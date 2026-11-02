package cn.bfops.mixin;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class RoomSystemMessageMixin {
    @Inject(method="broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$system(Component message,boolean overlay,CallbackInfo ci){if(cn.bfops.RoomAudience.system((PlayerList)(Object)this,message,overlay))ci.cancel();}
    @Inject(method="broadcastSystemToTeam",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$team(Player player,Component message,CallbackInfo ci){if(cn.bfops.RoomAudience.teamSystem((PlayerList)(Object)this,player,message,false))ci.cancel();}
    @Inject(method="broadcastSystemToAllExceptTeam",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$except(Player player,Component message,CallbackInfo ci){if(cn.bfops.RoomAudience.teamSystem((PlayerList)(Object)this,player,message,true))ci.cancel();}
}
