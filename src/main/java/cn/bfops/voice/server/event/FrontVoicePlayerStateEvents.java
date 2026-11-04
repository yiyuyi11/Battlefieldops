package cn.bfops.voice.server.event;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.network.S2CConfigSyncPayload;
import cn.bfops.voice.network.VoiceNetwork;
import cn.bfops.voice.server.audio.FrontVoiceCueEmitter;
import cn.bfops.voice.server.compat.LrCompat;
import cn.bfops.voice.server.compat.TaczCompat;

import java.util.UUID;

public final class FrontVoicePlayerStateEvents {
    private final FrontVoiceServerRuntime runtime;

    public FrontVoicePlayerStateEvents(FrontVoiceServerRuntime runtime) {
        this.runtime = runtime;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        FrontVoiceCueEmitter.emitCue(player, VoiceCue.DEATH);
        runtime.clearPlayerTracking(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingIncomingDamage(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null || player.isSpectator()) {
            return;
        }
        float remainingHealth = player.getHealth() + player.getAbsorptionAmount() - event.getAmount();
        long gameTick = player.server.getTickCount();
        boolean higherPriorityPlayed = tryPlayExplosionCloseCue(player, event, remainingHealth, gameTick);
        higherPriorityPlayed |= tryPlayLowHealthPanicCue(player, remainingHealth, gameTick);
        tryPlayHeavyHurtCue(player, event, remainingHealth, gameTick, higherPriorityPlayed);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if (!(event.player instanceof ServerPlayer player) || player.getServer() == null || player.isSpectator()) {
            return;
        }
        handleBurningCue(player);
        handleAmmoEmptyCue(player);
        handleGrenadeAlertCue(player);
        handleThrowGrenadeCue(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VoiceNetwork.sendToPlayer(player,
                new S2CConfigSyncPayload(VoiceConfig.isHudAllowCustomMessages(), VoiceConfig.isHudEnableQuickChat()));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            runtime.clearPlayerTracking(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            runtime.clearPlayerTracking(player.getUUID());
        }
    }

    private void handleBurningCue(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (isBurning(player)) {
            if (runtime.burningPlayers.add(playerId)) {
                FrontVoiceCueEmitter.emitCue(player, VoiceCue.BURNING);
            }
            return;
        }
        runtime.burningPlayers.remove(playerId);
    }

    private void handleAmmoEmptyCue(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long currentTick = player.server.getTickCount();
        long gunSignature = TaczCompat.getHeldGunStateSignature(player);
        if (gunSignature == 0L) {
            runtime.ammoWatchStates.remove(playerId);
            runtime.ammoEmptyPlayers.remove(playerId);
            return;
        }

        FrontVoiceServerRuntime.AmmoWatchState state = runtime.ammoWatchStates.get(playerId);
        boolean shouldProbe = state == null
            || state.gunSignature() != gunSignature
            || currentTick >= state.nextCheckTick();
        if (!shouldProbe) {
            return;
        }

        boolean isEmpty = TaczCompat.isHeldGunCompletelyEmpty(player);
        long nextCheckTick = currentTick + (isEmpty
            ? FrontVoiceServerRuntime.EMPTY_AMMO_RECHECK_TICKS
            : FrontVoiceServerRuntime.HELD_GUN_IDLE_RECHECK_TICKS);
        runtime.ammoWatchStates.put(playerId, new FrontVoiceServerRuntime.AmmoWatchState(gunSignature, nextCheckTick));
        if (isEmpty) {
            if (runtime.ammoEmptyPlayers.add(playerId)) {
                FrontVoiceCueEmitter.emitCue(player, VoiceCue.REQUEST_AMMO);
            }
            return;
        }
        runtime.ammoEmptyPlayers.remove(playerId);
    }

    private void handleGrenadeAlertCue(ServerPlayer player) {
        if (player.tickCount % FrontVoiceServerRuntime.GRENADE_ALERT_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        UUID playerId = player.getUUID();
        AABB scanBox = player.getBoundingBox().inflate(FrontVoiceServerRuntime.GRENADE_ALERT_RADIUS);
        boolean grenadeNearby = false;
        for (Entity entity : player.level().getEntities(player, scanBox, LrCompat::isExplosiveGrenadeEntity)) {
            LivingEntity grenadeOwner = LrCompat.resolveThrowableOwner(entity);
            if (grenadeOwner != null && grenadeOwner.getUUID().equals(playerId)) {
                continue;
            }
            if (entity.distanceToSqr(player) <= FrontVoiceServerRuntime.GRENADE_ALERT_RADIUS * FrontVoiceServerRuntime.GRENADE_ALERT_RADIUS) {
                grenadeNearby = true;
                break;
            }
        }
        if (grenadeNearby) {
            if (runtime.grenadeAlertPlayers.add(playerId)) {
                FrontVoiceCueEmitter.emitCue(player, VoiceCue.GRENADE_ALERT);
            }
        } else {
            runtime.grenadeAlertPlayers.remove(playerId);
        }
    }

    private void handleThrowGrenadeCue(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!player.isUsingItem()) {
            boolean wasC4 = runtime.c4PreparingPlayers.remove(playerId);
            if (wasC4) {
                Integer preparedSlot = runtime.c4PreparedSlot.remove(playerId);
                if (preparedSlot != null && preparedSlot == player.getInventory().selected) {
                    FrontVoiceCueEmitter.emitCue(player, VoiceCue.PLACE_C4);
                }
            }
            runtime.grenadePreparingPlayers.remove(playerId);
            runtime.preparingThrowableKinds.remove(playerId);
            return;
        }

        LrCompat.ThrowableCueKind kind = LrCompat.resolveThrowableCueKind(player.getUseItem());

        if (kind == LrCompat.ThrowableCueKind.EXPLOSIVE_GRENADE
            && LrCompat.isRemoteDetonation(player.getUseItem())) {
            if (runtime.c4PreparingPlayers.add(playerId)) {
                runtime.c4PreparedSlot.put(playerId, player.getInventory().selected);
            }
            return;
        }

        runtime.c4PreparingPlayers.remove(playerId);
        runtime.c4PreparedSlot.remove(playerId);

        if (kind != LrCompat.ThrowableCueKind.EXPLOSIVE_GRENADE
            && kind != LrCompat.ThrowableCueKind.MOLOTOV
            && kind != LrCompat.ThrowableCueKind.SMOKE) {
            runtime.grenadePreparingPlayers.remove(playerId);
            runtime.preparingThrowableKinds.remove(playerId);
            return;
        }
        LrCompat.ThrowableCueKind previousKind = runtime.preparingThrowableKinds.put(playerId, kind);
        boolean firstTrigger = runtime.grenadePreparingPlayers.add(playerId);
        if (!firstTrigger && previousKind == kind) {
            return;
        }
        FrontVoiceCueEmitter.emitCue(
            player,
            kind == LrCompat.ThrowableCueKind.SMOKE ? VoiceCue.THROW_SMOKE : VoiceCue.THROW_GRENADE
        );
    }

    private boolean isBurning(ServerPlayer player) {
        if (player.isOnFire()) {
            return true;
        }
        for (MobEffect effect : player.getActiveEffectsMap().keySet()) {
            if (net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect).equals(FrontVoiceServerRuntime.LR_FLAMMABLE_EFFECT_ID)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryPlayExplosionCloseCue(ServerPlayer player, LivingHurtEvent event, float remainingHealth, long gameTick) {
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION) || remainingHealth <= 0.0F) {
            return false;
        }
        long lastTick = runtime.lastExplosionCloseCueTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.EXPLOSION_CLOSE_COOLDOWN_TICKS) {
            return false;
        }
        runtime.lastExplosionCloseCueTick.put(player.getUUID(), gameTick);
        runtime.lastHigherPriorityDamageCueTick.put(player.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(player, VoiceCue.EXPLOSION_CLOSE);
        return true;
    }

    private boolean tryPlayLowHealthPanicCue(ServerPlayer player, float remainingHealth, long gameTick) {
        UUID playerId = player.getUUID();
        if (remainingHealth < FrontVoiceServerRuntime.LOW_HEALTH_PANIC_THRESHOLD && remainingHealth > 0.0F) {
            if (runtime.lowHealthPlayers.add(playerId)) {
                runtime.lastHigherPriorityDamageCueTick.put(playerId, gameTick);
                FrontVoiceCueEmitter.emitCue(player, VoiceCue.LOW_HEALTH_PANIC);
                return true;
            }
            return false;
        }
        runtime.lowHealthPlayers.remove(playerId);
        return false;
    }

    private void tryPlayHeavyHurtCue(
        ServerPlayer player,
        LivingHurtEvent event,
        float remainingHealth,
        long gameTick,
        boolean higherPriorityPlayed
    ) {
        if (remainingHealth <= 0.0F || event.getAmount() <= 0.0F) {
            return;
        }

        if (!TaczCompat.isTaczBulletEntity(event.getSource().getDirectEntity())) {
            return;
        }

        long lastHigherPriorityTick = runtime.lastHigherPriorityDamageCueTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
        if (higherPriorityPlayed || lastHigherPriorityTick == gameTick) {
            return;
        }
        long lastTick = runtime.lastHeavyHurtCueTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.HEAVY_HURT_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastHeavyHurtCueTick.put(player.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(player, VoiceCue.HEAVY_HURT);
    }
}
