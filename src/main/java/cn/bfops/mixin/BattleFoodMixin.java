package cn.bfops.mixin;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only vanilla food regeneration is gated: medical equipment and potion healing keep their own rules. */
@Mixin(FoodData.class)
public abstract class BattleFoodMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),require=0)
    private boolean bfops$foodRule(net.minecraft.world.level.GameRules rules,
            net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> key,Player player){
        boolean value=rules.getBoolean(key);
        return key==net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION?cn.bfops.BattleActions.foodRule(player,value):value;
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"),require=0)
    private void bfops$foodHeal(Player player,float amount){
        if(cn.bfops.BattleActions.foodHealing(player))player.heal(amount);
    }
}
