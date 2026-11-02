package cn.bfops.voice.server.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.server.compat.GdcqCompat;
import cn.bfops.voice.server.compat.LrCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FrontVoiceServerRuntime {
    static final long HEAVY_HURT_COOLDOWN_TICKS = 200L;
    static final long EXPLOSION_CLOSE_COOLDOWN_TICKS = 20L;
    static final long EMPTY_AMMO_RECHECK_TICKS = 20L;
    static final long HELD_GUN_IDLE_RECHECK_TICKS = 60L;
    static final int GRENADE_ALERT_SCAN_INTERVAL_TICKS = 5;
    static final double GRENADE_ALERT_RADIUS = 5.0D;
    static final float LOW_HEALTH_PANIC_THRESHOLD = 5.0F;
    static final long RELOADING_COOLDOWN_TICKS = 20L;
    static final double SUPPRESSION_CORRIDOR_LENGTH = 24.0D;
    static final double SUPPRESSION_CORRIDOR_RADIUS = 1.6D;
    static final long SUPPRESSION_WINDOW_TICKS = 20L;
    static final int SUPPRESSION_TRIGGER_STACKS = 6;
    static final int SUPPRESSING_HOSTILE_TRIGGER_STACKS = 9;
    static final long SUPPRESSED_COOLDOWN_TICKS = 80L;
    static final long SUPPRESSING_HOSTILE_COOLDOWN_TICKS = 80L;
    static final double SNIPER_CORRIDOR_RADIUS = 5.0D;
    static final double RPG_NEARPASS_RADIUS = 10.0D;
    static final long SNIPER_COOLDOWN_TICKS = 60L;
    static final long RPG_COOLDOWN_TICKS = 60L;
    static final long AGGRESSIVE_CUE_SEPARATION_TICKS = 20L;
    static final long RECENT_SHOT_ASSOCIATION_TICKS = 4L;
    static final double RECENT_SHOT_ASSOCIATION_DISTANCE = 8.0D;
    static final long RECENT_HEADSHOT_MARKER_TICKS = 10L;
    static final long RECENT_HANDLED_KILL_TICKS = 10L;
    static final double FRIENDLY_FIRE_RADIUS = 2.0D;
    static final long FRIENDLY_FIRE_COOLDOWN_TICKS = 80L;
    static final ResourceLocation LR_FLAMMABLE_EFFECT_ID = ResourceLocation.fromNamespaceAndPath("lrtactical", "flammable");

    final Set<UUID> burningPlayers = new HashSet<>();
    final Set<UUID> ammoEmptyPlayers = new HashSet<>();
    final Set<UUID> grenadeAlertPlayers = new HashSet<>();
    final Set<UUID> lowHealthPlayers = new HashSet<>();
    final Set<UUID> grenadePreparingPlayers = new HashSet<>();
    final Set<UUID> c4PreparingPlayers = new HashSet<>();
    final Map<UUID, Integer> c4PreparedSlot = new HashMap<>();
    final Map<UUID, LrCompat.ThrowableCueKind> preparingThrowableKinds = new HashMap<>();
    final Map<UUID, Long> lastHeavyHurtCueTick = new HashMap<>();
    final Map<UUID, Long> lastExplosionCloseCueTick = new HashMap<>();
    final Map<UUID, Long> lastHigherPriorityDamageCueTick = new HashMap<>();
    final Map<UUID, Long> lastReloadCueTick = new HashMap<>();
    final Map<UUID, AmmoWatchState> ammoWatchStates = new HashMap<>();
    final Map<UUID, Long> lastSuppressedCueTick = new HashMap<>();
    final Map<UUID, Long> lastSuppressingHostileCueTick = new HashMap<>();
    final Map<UUID, Long> lastSniperCueTick = new HashMap<>();
    final Map<UUID, Long> lastRpgCueTick = new HashMap<>();
    final Map<UUID, Long> lastGunEventTick = new HashMap<>();
    final Map<UUID, Long> lastAggressiveCueTick = new HashMap<>();
    final Map<UUID, SuppressionState> suppressionStates = new HashMap<>();
    final Map<UUID, TrackedProjectile> trackedProjectiles = new HashMap<>();
    final Map<UUID, RecentShot> recentShots = new HashMap<>();
    final Map<UUID, HeadshotMarker> recentHeadshotVictims = new HashMap<>();
    final Map<UUID, Long> recentlyHandledKillVictims = new HashMap<>();
    final Map<UUID, Long> lastFriendlyFireCueTick = new HashMap<>();

    boolean isHostilePlayer(ServerPlayer target, ServerPlayer source) {
        if (target == null || source == null || target == source) {
            return false;
        }
        if (target.getUUID().equals(source.getUUID())) {
            return false;
        }
        if (target.isSpectator() || source.isSpectator()) {
            return false;
        }
        if (target.isAlliedTo(source) || source.isAlliedTo(target)) {
            return false;
        }
        return !GdcqCompat.isSameCamp(target, source);
    }

    boolean isAlliedPlayer(ServerPlayer target, ServerPlayer source) {
        if (target == null || source == null || target == source) {
            return false;
        }
        if (target.getUUID().equals(source.getUUID())) {
            return false;
        }
        if (target.isSpectator() || source.isSpectator()) {
            return false;
        }
        return !isHostilePlayer(target, source);
    }

    void clearPlayerTracking(UUID playerId) {
        if (playerId == null) {
            return;
        }
        burningPlayers.remove(playerId);
        ammoEmptyPlayers.remove(playerId);
        grenadeAlertPlayers.remove(playerId);
        lowHealthPlayers.remove(playerId);
        grenadePreparingPlayers.remove(playerId);
        c4PreparingPlayers.remove(playerId);
        c4PreparedSlot.remove(playerId);
        preparingThrowableKinds.remove(playerId);
        lastHeavyHurtCueTick.remove(playerId);
        lastExplosionCloseCueTick.remove(playerId);
        lastHigherPriorityDamageCueTick.remove(playerId);
        lastReloadCueTick.remove(playerId);
        lastSuppressedCueTick.remove(playerId);
        lastSuppressingHostileCueTick.remove(playerId);
        lastSniperCueTick.remove(playerId);
        lastRpgCueTick.remove(playerId);
        lastGunEventTick.remove(playerId);
        lastAggressiveCueTick.remove(playerId);
        ammoWatchStates.remove(playerId);
        suppressionStates.remove(playerId);
        recentShots.remove(playerId);
        recentHeadshotVictims.remove(playerId);
        recentlyHandledKillVictims.remove(playerId);
        lastFriendlyFireCueTick.remove(playerId);
    }

    void pruneExpiredState(long gameTick) {
        suppressionStates.entrySet().removeIf(entry -> gameTick - entry.getValue().windowStartTick() >= SUPPRESSION_WINDOW_TICKS);
        recentShots.entrySet().removeIf(entry -> gameTick - entry.getValue().tick() > RECENT_SHOT_ASSOCIATION_TICKS);
        recentHeadshotVictims.entrySet().removeIf(entry -> gameTick - entry.getValue().tick() > RECENT_HEADSHOT_MARKER_TICKS);
        recentlyHandledKillVictims.entrySet().removeIf(entry -> gameTick - entry.getValue() > RECENT_HANDLED_KILL_TICKS);
    }

    ServerPlayer matchRecentShotShooter(ServerLevel level, Entity projectile) {
        if (level == null || projectile == null) {
            return null;
        }
        ResourceLocation projectileGunId = projectileGunId(projectile);
        if (projectileGunId == null) {
            return null;
        }
        long gameTick = level.getServer().getTickCount();
        RecentShot bestMatch = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (RecentShot shot : recentShots.values()) {
            if (shot == null
                || shot.level() != level
                || gameTick - shot.tick() > RECENT_SHOT_ASSOCIATION_TICKS
                || !projectileGunId.equals(shot.gunId())) {
                continue;
            }
            double distanceSq = shot.origin().distanceToSqr(projectile.position());
            if (distanceSq > RECENT_SHOT_ASSOCIATION_DISTANCE * RECENT_SHOT_ASSOCIATION_DISTANCE) {
                continue;
            }
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestMatch = shot;
            }
        }
        if (bestMatch == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(bestMatch.shooterId());
    }

    ResourceLocation projectileGunId(Entity projectile) {
        if (projectile == null) {
            return null;
        }
        try {
            Object result = projectile.getClass().getMethod("getGunId").invoke(projectile);
            return result instanceof ResourceLocation resourceLocation ? resourceLocation : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    String safeGunId(Entity projectile) {
        if (projectile == null) {
            return "unknown";
        }
        try {
            Object result = projectile.getClass().getMethod("getGunId").invoke(projectile);
            return String.valueOf(result);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    static AABB corridorBounds(Vec3 origin, Vec3 direction, double length, double radius) {
        Vec3 corridorEnd = origin.add(direction.scale(length));
        return new AABB(origin, corridorEnd).inflate(radius, 4.0D, radius);
    }

    static boolean isInsideFireCorridor(Vec3 origin, Vec3 direction, AABB targetBox, double length, double radius) {
        Vec3 targetCenter = targetBox.getCenter();
        Vec3 relative = targetCenter.subtract(origin);
        double forwardDistance = relative.dot(direction);
        if (forwardDistance < 0.0D || forwardDistance > length) {
            return false;
        }
        Vec3 closestPoint = origin.add(direction.scale(forwardDistance));
        double allowedRadius = radius + Math.max(targetBox.getXsize(), targetBox.getZsize()) * 0.5D;
        return closestPoint.distanceToSqr(targetCenter) <= allowedRadius * allowedRadius;
    }

    static boolean segmentIntersectsInflatedBox(Vec3 from, Vec3 to, AABB targetBox, double inflation) {
        if (from == null || to == null || targetBox == null || inflation < 0.0D) {
            return false;
        }
        AABB expandedBox = targetBox.inflate(inflation);
        if (expandedBox.contains(from) || expandedBox.contains(to)) {
            return true;
        }
        return expandedBox.clip(from, to).isPresent();
    }

    record AmmoWatchState(long gunSignature, long nextCheckTick) {
    }

    static final class SuppressionState {
        private long windowStartTick;
        private int stacks;
        private boolean allMachineGun;

        SuppressionState(long windowStartTick, int stacks, boolean allMachineGun) {
            this.windowStartTick = windowStartTick;
            this.stacks = stacks;
            this.allMachineGun = allMachineGun;
        }

        long windowStartTick() {
            return windowStartTick;
        }

        int stacks() {
            return stacks;
        }

        boolean allMachineGun() {
            return allMachineGun;
        }

        void incrementStacks(boolean machineGunSource) {
            this.stacks++;
            this.allMachineGun &= machineGunSource;
        }
    }

    enum ProjectileCueType {
        UNKNOWN,
        SNIPER,
        RPG,
        NONE
    }

    static final class TrackedProjectile {
        private final Entity projectile;
        private Vec3 lastPosition;
        private UUID shooterId;
        private int probeTicks;
        private ProjectileCueType cueType;

        TrackedProjectile(Entity projectile, Vec3 lastPosition, UUID shooterId) {
            this.projectile = projectile;
            this.lastPosition = lastPosition;
            this.shooterId = shooterId;
            this.cueType = ProjectileCueType.UNKNOWN;
        }

        Entity projectile() {
            return projectile;
        }

        Vec3 lastPosition() {
            return lastPosition;
        }

        void setLastPosition(Vec3 lastPosition) {
            this.lastPosition = lastPosition;
        }

        UUID shooterId() {
            return shooterId;
        }

        void setShooterId(UUID shooterId) {
            this.shooterId = shooterId;
        }

        int probeTicks() {
            return probeTicks;
        }

        void incrementProbeTicks() {
            this.probeTicks++;
        }

        ProjectileCueType cueType() {
            return cueType;
        }

        void setCueType(ProjectileCueType cueType) {
            this.cueType = cueType == null ? ProjectileCueType.UNKNOWN : cueType;
        }
    }

    record RecentShot(UUID shooterId, ServerLevel level, ResourceLocation gunId, Vec3 origin, long tick) {
    }

    record HeadshotMarker(UUID attackerId, long tick) {
    }
}
