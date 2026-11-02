package cn.bfops.crawl.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin({LivingEntity.class})
public interface LivingEntityJumpingAccessor {
   @Accessor("jumping")
   boolean gd656taczcrawtweak$isJumping();
}
