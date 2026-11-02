package cn.bfops.voice.server.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.server.audio.FrontVoiceCueEmitter;
import cn.bfops.voice.server.compat.TaczCompat;

import java.util.UUID;

public final class FrontVoiceProjectileTrackerEvents {
    private final FrontVoiceServerRuntime runtime;

    public FrontVoiceProjectileTrackerEvents(FrontVoiceServerRuntime runtime) {
        this.runtime = runtime;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }
        long gameTick = event.getServer().getTickCount();
        tickTrackedProjectiles(gameTick);
        runtime.pruneExpiredState(gameTick);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event == null || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!TaczCompat.isTaczBulletEntity(entity)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        ServerPlayer shooter = TaczCompat.resolveBulletOwner(entity);
        if (shooter == null) {
            shooter = runtime.matchRecentShotShooter(level, entity);
        }
        FrontVoiceServerRuntime.TrackedProjectile track = new FrontVoiceServerRuntime.TrackedProjectile(
            entity,
            entity.position(),
            shooter == null ? null : shooter.getUUID()
        );
        runtime.trackedProjectiles.put(entity.getUUID(), track);
        FrontVoiceServerRuntime.ProjectileCueType cueType = resolveProjectileCueType(track, entity);
        if (cueType == FrontVoiceServerRuntime.ProjectileCueType.SNIPER
            || cueType == FrontVoiceServerRuntime.ProjectileCueType.RPG) {
            handleTrackedProjectileNearPass(
                track,
                cueType,
                entity.position(),
                entity.position().add(entity.getDeltaMovement()),
                level.getServer().getTickCount()
            );
        }
    }

    private void tickTrackedProjectiles(long gameTick) {
        runtime.trackedProjectiles.entrySet().removeIf(entry -> {
            FrontVoiceServerRuntime.TrackedProjectile track = entry.getValue();
            Entity projectile = track.projectile();
            if (projectile == null || !projectile.isAlive() || projectile.level().isClientSide()) {
                return true;
            }
            FrontVoiceServerRuntime.ProjectileCueType cueType = resolveProjectileCueType(track, projectile);
            if (cueType == FrontVoiceServerRuntime.ProjectileCueType.NONE) {
                track.incrementProbeTicks();
                if (track.probeTicks() > 5) {
                    return true;
                }
                return false;
            }
            Vec3 previousPosition = track.lastPosition();
            Vec3 currentPosition = projectile.position();
            handleTrackedProjectileNearPass(track, cueType, previousPosition, currentPosition, gameTick);
            track.setLastPosition(currentPosition);
            return false;
        });
    }

    private FrontVoiceServerRuntime.ProjectileCueType resolveProjectileCueType(
        FrontVoiceServerRuntime.TrackedProjectile track,
        Entity projectile
    ) {
        FrontVoiceServerRuntime.ProjectileCueType cachedType = track.cueType();
        if (cachedType != FrontVoiceServerRuntime.ProjectileCueType.UNKNOWN) {
            return cachedType;
        }
        FrontVoiceServerRuntime.ProjectileCueType resolvedType = FrontVoiceServerRuntime.ProjectileCueType.NONE;
        if (TaczCompat.isSniperBulletEntity(projectile)) {
            resolvedType = FrontVoiceServerRuntime.ProjectileCueType.SNIPER;
        } else if (TaczCompat.isRpgBulletEntity(projectile)) {
            resolvedType = FrontVoiceServerRuntime.ProjectileCueType.RPG;
        } else {
            ServerPlayer shooter = resolveTrackedProjectileShooter(track);
            if (shooter != null) {
                if (TaczCompat.isSniperGun(shooter.getMainHandItem())) {
                    resolvedType = FrontVoiceServerRuntime.ProjectileCueType.SNIPER;
                } else if (TaczCompat.isRpgGun(shooter.getMainHandItem())) {
                    resolvedType = FrontVoiceServerRuntime.ProjectileCueType.RPG;
                }
            }
        }
        track.setCueType(resolvedType);
        return resolvedType;
    }

    private void handleTrackedProjectileNearPass(
        FrontVoiceServerRuntime.TrackedProjectile track,
        FrontVoiceServerRuntime.ProjectileCueType cueType,
        Vec3 from,
        Vec3 to,
        long gameTick
    ) {
        if (cueType == null || cueType == FrontVoiceServerRuntime.ProjectileCueType.NONE || from == null || to == null) {
            return;
        }
        Entity projectile = track.projectile();
        if (projectile == null || !(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer shooter = resolveTrackedProjectileShooter(track);
        if (shooter == null || shooter.server == null || shooter.isSpectator()) {
            return;
        }
        double radius = cueType == FrontVoiceServerRuntime.ProjectileCueType.RPG
            ? FrontVoiceServerRuntime.RPG_NEARPASS_RADIUS
            : FrontVoiceServerRuntime.SNIPER_CORRIDOR_RADIUS;
        AABB scanBox = new AABB(from, to).inflate(radius);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, scanBox, candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            if (!runtime.isHostilePlayer(target, shooter)) {
                continue;
            }
            if (!FrontVoiceServerRuntime.segmentIntersectsInflatedBox(from, to, target.getBoundingBox(), radius)) {
                continue;
            }
            if (cueType == FrontVoiceServerRuntime.ProjectileCueType.RPG) {
                tryPlayRpgCue(target, gameTick);
            } else if (cueType == FrontVoiceServerRuntime.ProjectileCueType.SNIPER) {
                tryPlaySniperCue(target, gameTick);
            }
        }
    }

    private ServerPlayer resolveTrackedProjectileShooter(FrontVoiceServerRuntime.TrackedProjectile track) {
        Entity projectile = track.projectile();
        if (projectile == null || !(projectile.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID shooterId = track.shooterId();
        if (shooterId != null) {
            ServerPlayer cachedShooter = level.getServer().getPlayerList().getPlayer(shooterId);
            if (cachedShooter != null) {
                return cachedShooter;
            }
        }
        ServerPlayer resolvedShooter = TaczCompat.resolveBulletOwner(projectile);
        if (resolvedShooter == null) {
            resolvedShooter = runtime.matchRecentShotShooter(level, projectile);
        }
        if (resolvedShooter != null) {
            track.setShooterId(resolvedShooter.getUUID());
        }
        return resolvedShooter;
    }

    private void tryPlaySniperCue(ServerPlayer target, long gameTick) {
        long lastTick = runtime.lastSniperCueTick.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.SNIPER_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastSniperCueTick.put(target.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(target, VoiceCue.SNIPER_ALERT);
    }

    private void tryPlayRpgCue(ServerPlayer target, long gameTick) {
        long lastTick = runtime.lastRpgCueTick.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTick - lastTick < FrontVoiceServerRuntime.RPG_COOLDOWN_TICKS) {
            return;
        }
        runtime.lastRpgCueTick.put(target.getUUID(), gameTick);
        FrontVoiceCueEmitter.emitCue(target, VoiceCue.RPG_ALERT);
    }
}
