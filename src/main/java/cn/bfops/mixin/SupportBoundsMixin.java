package cn.bfops.mixin;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ArmorStand.class)
public abstract class SupportBoundsMixin {
    @Inject(method="getDefaultDimensions",at=@At("HEAD"),cancellable=true,require=0)
    private void bfops$bounds(Pose pose,CallbackInfoReturnable<EntityDimensions> cir){
        var dimensions=cn.bfops.PropVitals.dimensions((ArmorStand)(Object)this);if(dimensions!=null)cir.setReturnValue(dimensions);
    }
    @Inject(method="setItemSlot",at=@At("RETURN"),require=0)
    private void bfops$refresh(EquipmentSlot slot,ItemStack stack,CallbackInfo ci){
        var self=(ArmorStand)(Object)this;if(slot==EquipmentSlot.HEAD&&cn.bfops.PropVitals.owned(self))self.refreshDimensions();
    }
}
