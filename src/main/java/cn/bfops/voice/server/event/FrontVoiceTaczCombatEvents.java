package cn.bfops.voice.server.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.server.audio.FrontVoiceCueEmitter;
import cn.bfops.voice.server.compat.TaczCompat;

import java.util.UUID;
import java.util.function.Consumer;

public final class FrontVoiceTaczCombatEvents {
    private final FrontVoiceServerRuntime runtime;

    public FrontVoiceTaczCombatEvents(FrontVoiceServerRuntime runtime) {
        this.runtime = runtime;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.server == null || victim.isSpectator()) {
            return;
        }
        handleTaczKillFallback(victim, event);
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player && player.server != null && !player.isSpectator()) {
            tryPlaySniperCueOnHit(player, event);
            tryPlayRpgCueOnHit(player, event);
        }
    }

    // === TaCZ event handlers (reflection; no compile-time TaCZ dependency) ===

    void handleGunFire(Event event) {
        if (event == null) {
            return;
        }
        try {
            Object logicalSide = event.getClass().getMethod("getLogicalSide").invoke(event);
            if (logicalSide == null || !"SERVER".equals(String.valueOf(logicalSide))) {
                return;
            }
            Object shooterObject = event.getClass().getMethod("getShooter").invoke(event);
            if (!(shooterObject instanceof ServerPlayer shooter) || shooter.server == null || shooter.isSpectator()) {
                return;
            }
            long gameTick = shooter.server.getTickCount();
            long lastTick = runtime.lastGunEventTick.getOrDefault(shooter.getUUID(), Long.MIN_VALUE / 2L);
            if (gameTick == lastTick) {
                return;
            }
            runtime.lastGunEventTick.put(shooter.getUUID(), gameTick);
            Object gunStackObject = event.getClass().getMethod("getGunItemStack").invoke(event);
            recordRecentShot(shooter, gunStackObject, gameTick);
            handleFireCorridor(shooter, gunStackObject);
        } catch (Throwable ignored) {
        }
    }

    void handleReload(Event event) {
        if (event == null) {
            return;
        }
        try {
            Object logicalSide = event.getClass().getMethod("getLogicalSide").invoke(event);
            if (logicalSide == null || !"SERVER".equals(String.valueOf(logicalSide))) {
                return;
            }
            Object entityObject = event.getClass().getMethod("getEntity").invoke(event);
            if (!(entityObject instanceof ServerPlayer player) || player.server == null || player.isSpectator()) {
                return;
            }
            Object gunStackObject = event.getClass().getMethod("getGunItemStack").invoke(event);
            ResourceLocation gunId = TaczCompat.resolveGunId(
                gunStackObject instanceof ItemStack itemStack ? itemStack : player.getMainHandItem()
            );
            if (gunId == null) {
                return;
            }
            long gameTick = player.server.getTickCount();
            long lastTick = runtime.lastReloadCueTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
            if (gameTick - lastTick < FrontVoiceServerRuntime.RELOADING_COOLDOWN_TICKS) {
                return;
            }
            runtime.lastReloadCueTick.put(player.getUUID(), gameTick);
            FrontVoiceCueEmitter.emitCue(player, VoiceCue.RELOADING);
        } catch (Throwable ignored) {
        }
    }

    void handleKill(Event event) {
        if (event == null) {
            return;
        }
        try {
            Object logicalSide = event.getClass().getMethod("getLogicalSide").invoke(event);
            if (logicalSide == null || !"SERVER".equals(String.valueOf(logicalSide))) {
                return;
            }
            Object attackerObject = event.getClass().getMethod("getAttacker").invoke(event);
            Object killedObject = event.getClass().getMethod("getKilledEntity").invoke(event);
            if (!(attackerObject instanceof ServerPlayer attacker)
                || !(killedObject instanceof ServerPlayer killedPlayer)
                || attacker.server == null
                || attacker.isSpectator()
                || killedPlayer == attacker) {
                return;
            }
            Object headshotObject = event.getClass().getMethod("isHeadShot").invoke(event);
            processTaczPlayerKill(attacker, killedPlayer, headshotObject instanceof Boolean headshot && headshot);
        } catch (Throwable ignored) {
        }
    }

    void handleHurtPre(Event event) {
        recordTaczHeadshotMarker(event);
    }

    void handleHurtPost(Event event) {
        recordTaczHeadshotMarker(event);
    }

    private void recordTaczHeadshotMarker(Event event) {
        if (event == null) {
            return;
        }
        try {
            Object logicalSide = event.getClass().getMethod("getLogicalSide").invoke(event);
            if (logicalSide == null || !"SERVER".equals(String.valueOf(logicalSide))) {
                return;
            }
            Object hurtObject = event.getClass().getMethod("getHurtEntity").invoke(event);
            Object attackerObject = event.getClass().getMethod("getAttacker").invoke(event);
            Object headshotObject = event.getClass().getMethod("isHeadShot").invoke(event);
            if (!(hurtObject instanceof ServerPlayer victim)
                || !(attackerObject instanceof ServerPlayer attacker)
                || attacker.server == null
                || attacker.isSpectator()
                || !(headshotObject instanceof Boolean headshot)
                || !headshot) {
                return;
            }
            runtime.recentHeadshotVictims.put(victim.getUUID(), new FrontVoiceServerRuntime.HeadshotMarker(attacker.getUUID(), attacker.server.getTickCount()));
        } catch (Throwable ignored) {
        }
    }

    private void handleFireCorridor(ServerPlayer shooter, Object gunStackObject) {
        if (shooter == null || shooter.server == null || !shooter.isAlive()) {
            return;
        }
        var origin = shooter.getEyePosition();
        var look = shooter.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            return;
        }
        var direction = look.normalize();
        long gameTick = shooter.server.getTickCount();
        var scanBox = FrontVoiceServerRuntime.corridorBounds(
            origin,
            direction,
            FrontVoiceServerRuntime.SUPPRESSION_CORRIDOR_LENGTH,
            FrontVoiceServerRuntime.SUPPRESSION_CORRIDOR_RADIUS
        );
        for (ServerPlayer target : shooter.serverLevel().getEntitiesOfClass(ServerPlayer.class, scanBox, candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            if (!runtime.isHostilePlayer(target, shooter)) {
                continue;
            }
            if (!FrontVoiceServerRuntime.isInsideFireCorridor(
                origin,
                direction,
                target.getBoundingBox(),
                FrontVoiceServerRuntime.SUPPRESSION_CORRIDOR_LENGTH,
                FrontVoiceServerRuntime.SUPPRESSION_CORRIDOR_RADIUS
            )) {
                continue;
            }
            boolean machineGunSource = gunStackObject instanceof ItemStack itemStack
                ? TaczCompat.isMachineGun(itemStack)
                : TaczCompat.isMachineGun(shooter.getMainHandItem());
            FrontVoiceServerRuntime.SuppressionState state = registerSuppressionStack(target, gameTick, machineGunSource);
            tryPlaySuppressedCue(target, shooter, state, gameTick);
        }
    }

    private FrontVoiceServerRuntime.SuppressionState registerSuppressionStack(ServerPlayer target, long gameTick, boolean machineGunSource) {
        FrontVoiceServerRuntime.SuppressionState state = runtime.suppressionStates.get(target.getUUID());
        if (state == null || gameTick - state.windowStartTick() >= FrontVoiceServerRuntime.SUPPRESSION_WINDOW_TICKS) {
            state = new FrontVoiceServerRuntime.SuppressionState(gameTick, 0, true);
            runtime.suppressionStates.put(target.getUUID(), state);
        }
        state.incrementStacks(machineGunSource);
        return state;
    }

    private void tryPlaySuppressedCue(
        ServerPlayer target,
        ServerPlayer shooter,
        FrontVoiceServerRuntime.SuppressionState state,
        long gameTick
    ) {
        int stacks = state.stacks();
        if (stacks < FrontVoiceServerRuntime.SUPPRESSION_TRIGGER_STACKS) {
            return;
        }
        if (stacks >= FrontVoiceServerRuntime.SUPPRESSING_HOSTILE_TRIGGER_STACKS) {
            tryPlaySuppressingHostileCue(shooter, gameTick);
        }
        long lastTick = runtime.lastSuppressedCueTick.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.SUPPRESSED_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastSuppressedCueTick.put(target.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(
            target,
            state.allMachineGun() ? VoiceCue.MACHINE_GUN_ALERT : VoiceCue.SUPPRESSED
        );
    }

    private void tryPlaySuppressingHostileCue(ServerPlayer shooter, long gameTick) {
        if (shooter == null || shooter.server == null || shooter.isSpectator()) {
            return;
        }
        long lastTick = runtime.lastSuppressingHostileCueTick.getOrDefault(shooter.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.SUPPRESSING_HOSTILE_COOLDOWN_TICKS) {
            return;
        }
        if (!canPlayAggressiveCue(shooter.getUUID(), gameTick)) {
            return;
        }
        runtime.lastSuppressingHostileCueTick.put(shooter.getUUID(), gameTick);
        markAggressiveCuePlayed(shooter.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(shooter, VoiceCue.SUPPRESSING_HOSTILE);
    }

    private void tryPlaySniperCueOnHit(ServerPlayer target, LivingIncomingDamageEvent event) {
        DamageSource damageSource = event.getSource();
        if (damageSource == null) {
            return;
        }
        ServerPlayer shooter = resolveDamageSourceShooter(damageSource);
        if (shooter == null || shooter.isSpectator() || !runtime.isHostilePlayer(target, shooter)) {
            return;
        }
        if (!TaczCompat.isSniperBulletEntity(damageSource.getDirectEntity())
            && !TaczCompat.isSniperGun(shooter.getMainHandItem())) {
            return;
        }
        tryPlaySniperCue(target, target.server.getTickCount());
    }

    private void tryPlayRpgCueOnHit(ServerPlayer target, LivingIncomingDamageEvent event) {
        DamageSource damageSource = event.getSource();
        if (damageSource == null) {
            return;
        }
        ServerPlayer shooter = resolveDamageSourceShooter(damageSource);
        if (shooter == null || shooter.isSpectator() || !runtime.isHostilePlayer(target, shooter)) {
            return;
        }
        if (!TaczCompat.isRpgBulletEntity(damageSource.getDirectEntity())
            && !TaczCompat.isRpgGun(shooter.getMainHandItem())) {
            return;
        }
        tryPlayRpgCue(target, target.server.getTickCount());
    }

    private ServerPlayer resolveDamageSourceShooter(DamageSource damageSource) {
        Entity attacker = damageSource.getEntity();
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        return TaczCompat.resolveBulletOwner(damageSource.getDirectEntity());
    }

    private void tryPlaySniperCue(ServerPlayer target, long gameTick) {
        long lastTick = runtime.lastSniperCueTick.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.SNIPER_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastSniperCueTick.put(target.getUUID(), gameTick);
        runtime.lastHigherPriorityDamageCueTick.put(target.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(target, VoiceCue.SNIPER_ALERT);
    }

    private void tryPlayRpgCue(ServerPlayer target, long gameTick) {
        long lastTick = runtime.lastRpgCueTick.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.RPG_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastRpgCueTick.put(target.getUUID(), gameTick);
        runtime.lastHigherPriorityDamageCueTick.put(target.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(target, VoiceCue.RPG_ALERT);
    }

    private boolean canPlayAggressiveCue(UUID shooterId, long gameTick) {
        long lastTick = runtime.lastAggressiveCueTick.getOrDefault(shooterId, Long.MIN_VALUE / 2L);
        return gameTick - lastTick >= FrontVoiceServerRuntime.AGGRESSIVE_CUE_SEPARATION_TICKS;
    }

    private void markAggressiveCuePlayed(UUID shooterId, long gameTick) {
        runtime.lastAggressiveCueTick.put(shooterId, gameTick);
    }

    private boolean handleNearbyNiceShot(ServerPlayer killer) {
        boolean played = false;
        var scanBox = killer.getBoundingBox().inflate(5.0D);
        for (ServerPlayer observer : killer.serverLevel().getEntitiesOfClass(
            ServerPlayer.class,
            scanBox,
            candidate -> candidate.isAlive() && !candidate.isSpectator() && candidate != killer
        )) {
            if (!runtime.isAlliedPlayer(observer, killer)) {
                continue;
            }
            if (observer.distanceToSqr(killer) > 25.0D) {
                continue;
            }
            FrontVoiceCueEmitter.emitCue(observer, VoiceCue.NICE_SHOT);
            played = true;
        }
        return played;
    }

    private void handleNearbyFriendlyFire(ServerPlayer attacker, ServerPlayer killedPlayer, long gameTick) {
        var scanBox = killedPlayer.getBoundingBox().inflate(FrontVoiceServerRuntime.FRIENDLY_FIRE_RADIUS);
        for (ServerPlayer observer : killedPlayer.serverLevel().getEntitiesOfClass(
            ServerPlayer.class,
            scanBox,
            candidate -> candidate.isAlive() && !candidate.isSpectator() && candidate != killedPlayer
        )) {
            if (!runtime.isAlliedPlayer(observer, killedPlayer)) {
                continue;
            }
            if (observer.distanceToSqr(killedPlayer) > FrontVoiceServerRuntime.FRIENDLY_FIRE_RADIUS * FrontVoiceServerRuntime.FRIENDLY_FIRE_RADIUS) {
                continue;
            }
            long lastTick = runtime.lastFriendlyFireCueTick.getOrDefault(observer.getUUID(), Long.MIN_VALUE / 2L);
            if (gameTick - lastTick < FrontVoiceServerRuntime.FRIENDLY_FIRE_COOLDOWN_TICKS) {
                continue;
            }
            runtime.lastFriendlyFireCueTick.put(observer.getUUID(), gameTick);
            FrontVoiceCueEmitter.emitCue(observer, VoiceCue.FRIENDLY_FIRE);
        }
    }

    private void processTaczPlayerKill(ServerPlayer attacker, ServerPlayer killedPlayer, boolean headshot) {
        long gameTick = attacker.server.getTickCount();
        Long lastHandledTick = runtime.recentlyHandledKillVictims.get(killedPlayer.getUUID());
        if (lastHandledTick != null && gameTick - lastHandledTick < FrontVoiceServerRuntime.RECENT_HANDLED_KILL_TICKS) {
            return;
        }
        runtime.recentlyHandledKillVictims.put(killedPlayer.getUUID(), gameTick);
        if (runtime.isAlliedPlayer(killedPlayer, attacker)) {
            handleNearbyFriendlyFire(attacker, killedPlayer, gameTick);
            return;
        }
        boolean playedNiceShot = false;
        if (headshot) {
            playedNiceShot = handleNearbyNiceShot(attacker);
        }
        if (!playedNiceShot) {
            FrontVoiceCueEmitter.emitCue(attacker, VoiceCue.KILL_ENEMY);
        }
    }

    private void handleTaczKillFallback(ServerPlayer victim, LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (source == null || !TaczCompat.isTaczBulletEntity(source.getDirectEntity())) {
            return;
        }
        ServerPlayer attacker = null;
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof ServerPlayer player) {
            attacker = player;
        } else {
            attacker = TaczCompat.resolveBulletOwner(source.getDirectEntity());
            if (attacker == null) {
                attacker = runtime.matchRecentShotShooter(victim.serverLevel(), source.getDirectEntity());
            }
        }
        if (attacker == null || attacker.isSpectator() || !runtime.isHostilePlayer(victim, attacker)) {
            return;
        }
        long gameTick = victim.server.getTickCount();
        FrontVoiceServerRuntime.HeadshotMarker marker = runtime.recentHeadshotVictims.get(victim.getUUID());
        boolean headshot = marker != null
            && gameTick - marker.tick() <= FrontVoiceServerRuntime.RECENT_HEADSHOT_MARKER_TICKS
            && attacker.getUUID().equals(marker.attackerId());
        processTaczPlayerKill(attacker, victim, headshot);
    }

    private void recordRecentShot(ServerPlayer shooter, Object gunStackObject, long gameTick) {
        ResourceLocation gunId = TaczCompat.resolveGunId(
            gunStackObject instanceof ItemStack itemStack ? itemStack : shooter.getMainHandItem()
        );
        if (gunId == null) {
            return;
        }
        runtime.recentShots.put(
            shooter.getUUID(),
            new FrontVoiceServerRuntime.RecentShot(shooter.getUUID(), shooter.serverLevel(), gunId, shooter.getEyePosition(), gameTick)
        );
    }

    static void registerTaczListeners(FrontVoiceServerRuntime runtime) {
        FrontVoiceTaczCombatEvents self = new FrontVoiceTaczCombatEvents(runtime);
        registerTacz(self::handleGunFire, "com.tacz.guns.api.event.common.GunFireEvent");
        registerTacz(self::handleGunFire, "com.tacz.guns.api.event.common.GunShootEvent");
        registerTacz(self::handleReload, "com.tacz.guns.api.event.common.GunReloadEvent");
        registerTacz(self::handleKill, "com.tacz.guns.api.event.common.EntityKillByGunEvent");
        registerTacz(self::handleHurtPre, "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
        registerTacz(self::handleHurtPost, "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerTacz(Consumer<Event> handler, String className) {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(className);
            NeoForge.EVENT_BUS.addListener((Class) type, handler);
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
    }
}
