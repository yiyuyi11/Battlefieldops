package cn.bfops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure decisions used by the match loop: where a player may respawn and who is highlighted.
 *
 * <p>Deliberately free of Minecraft types (positions are {@link At}, not {@code Vec3}) so every rule
 * here can be verified headless. The engine adapts entity positions into {@link At} at the call
 * site, which keeps the arithmetic and the packet/entity plumbing separate.
 */
final class Tactics {
    /** Teammates glow through walls up to this distance, so a squad can find each other. */
    static final double MATE_GLOW_RANGE = 64.0;
    /** Enemies only glow when they are close and actually visible; see {@link #enemyVisible}. */
    static final double ENEMY_GLOW_RANGE = 32.0;
    /** A spawn with an enemy inside this radius is only used when nothing safer exists. */
    static final double SPAWN_DANGER_RADIUS = 16.0;

    /** A bare position, so this class needs no Minecraft dependency. */
    record At(double x, double y, double z) {}

    private Tactics() {
    }

    /**
     * Enemies are highlighted only under the conditions a player could reasonably spot them:
     * inside the range and with line of sight. Wall-hack highlighting is worse than none, so this
     * deliberately refuses to glow through terrain.
     */
    static boolean enemyVisible(At viewer, At target, boolean lineOfSight) {
        if (viewer.equals(target)) return false;
        return distanceSqr(viewer, target) <= ENEMY_GLOW_RANGE * ENEMY_GLOW_RANGE && lineOfSight;
    }

    static boolean mateVisible(At viewer, At target) {
        return !viewer.equals(target) && distanceSqr(viewer, target) <= MATE_GLOW_RANGE * MATE_GLOW_RANGE;
    }

    private static double distanceSqr(At a, At b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Picks the spawn farthest from any living enemy, mirroring the reference behaviour: prefer a
     * genuinely safe spawn, fall back to the least contested one rather than refusing to deploy.
     *
     * @param candidates own-team spawns that are legal for the current stage
     * @param enemies    living enemy positions; may be empty
     */
    static Model.Spawn safestSpawn(List<Model.Spawn> candidates, List<At> enemies) {
        if (candidates == null || candidates.isEmpty()) return null;
        Model.Spawn best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Model.Spawn spawn : candidates) {
            double nearest = nearestEnemySqr(spawn, enemies);
            if (nearest > bestScore) {
                bestScore = nearest;
                best = spawn;
            }
        }
        return best;
    }

    private static double nearestEnemySqr(Model.Spawn spawn, List<At> enemies) {
        double nearest = Double.POSITIVE_INFINITY;
        for (At enemy : enemies) {
            double dx = enemy.x - spawn.x, dy = enemy.y - spawn.y, dz = enemy.z - spawn.z;
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
    }

    /** True when a spawn point is too close to an enemy for a fair fight. */
    static boolean spawnUnderFire(Model.Spawn spawn, List<At> enemies) {
        return nearestEnemySqr(spawn, enemies) < SPAWN_DANGER_RADIUS * SPAWN_DANGER_RADIUS;
    }

    /**
     * Chooses a spawn for a deploying player: the selected one when it is safe, otherwise the
     * safest alternative, otherwise the selected one anyway. Refusing to deploy is a worse outcome
     * than a risky spawn, because a stuck player cannot act at all.
     */
    static Model.Spawn chooseSpawn(Model.Spawn selected, List<Model.Spawn> candidates, List<At> enemies) {
        if (selected == null) return safestSpawn(candidates, enemies);
        if (candidates == null || candidates.isEmpty()) return selected;
        if (!spawnUnderFire(selected, enemies)) return selected;
        Model.Spawn safer = safestSpawn(candidates, enemies);
        return safer == null ? selected : safer;
    }

    /**
     * Farthest-from-enemy ordering, so a breakthrough push always starts away from the defenders.
     */
    static List<Model.Spawn> byThreat(List<Model.Spawn> candidates, final List<At> enemies) {
        List<Model.Spawn> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(spawn -> -nearestEnemySqr(spawn, enemies)));
        return ordered;
    }

    /** Counts players per team, used by the side bar and by the join balance check. */
    static int population(List<Integer> teams, int team) {
        int count = 0;
        for (int value : teams) if (value == team) count++;
        return count;
    }

    /**
     * Horizontal distance used for objective capture, matching the engine's own check. Vertical
     * distance is handled separately against {@code Point.height}, so it is not part of this.
     */
    static double flatDistance(double x0, double z0, double x1, double z1) {
        return Math.hypot(x0 - x1, z0 - z1);
    }
}
