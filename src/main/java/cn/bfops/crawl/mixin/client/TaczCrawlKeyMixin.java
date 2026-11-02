package cn.bfops.crawl.mixin.client;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.CrawlKey;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.util.InputExtraCheck;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo @Mixin(targets="com.tacz.guns.client.input.CrawlKey",remap=false)
public abstract class TaczCrawlKeyMixin {
    @Inject(method="onCrawlPress",at=@At("HEAD"),cancellable=true,require=0)
    private static void bfops$input(InputEvent.Key event,CallbackInfo ci){
        ci.cancel(); // Replace the original handler, so one key cannot toggle twice.
        if(cn.bfops.MovementHandoff.active())return; // Battlefield Movement owns Z: sprint slides, otherwise prone.
        if(!InputExtraCheck.isInGame()||!CrawlKey.CRAWL_KEY.matches(event.getKey(),event.getScanCode())||(!cn.bfops.MatchView.canProne()&&!SyncConfig.ENABLE_CRAWL.get()))return;
        var player=Minecraft.getInstance().player;
        if(player==null||player.isSpectator()||player.isPassenger()||cn.bfops.CrawlPose.downed(player))return;
        IClientPlayerGunOperator op=IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean hold=cn.bfops.ProneControls.hold();
        if(event.getAction()==1){cn.bfops.ProneControls.pressed(hold);op.crawl(hold||!op.isCrawl());}
        else if(event.getAction()==0&&hold){cn.bfops.ProneControls.released();op.crawl(false);}
    }
}
