package cn.bfops;

/**
 * A smoothed 2-D position, for elements that must follow a moving world point without jitter.
 *
 * <h2>The problem this replaces</h2>
 *
 * <p>The HUD minimap used to snap its centre to a multiple of eight blocks
 * ({@code Math.floor(playerX / 8) * 8}). That was a deliberate trade — quantising the centre meant the
 * terrain texture underneath only had to be re-baked when the player crossed a grid line, instead of on
 * every frame of a walk. The cost was a map that visibly stepped: eight blocks of movement, then a jump.
 * On a large screen that jump is several pixels, and it is the single most obvious difference between
 * this mod's map and the reference pack's, which uses a real renderer and therefore moves continuously.
 *
 * <p>Quantising is not needed for correctness, only to bound bake work, and that bound is better
 * enforced directly (see {@link Minimap}'s bake-eligibility check, which compares against a movement
 * threshold rather than against a grid). So the snap can go, and this class is what replaces it: a
 * continuously interpolated centre that is <em>also</em> steady when the player stands still.
 *
 * <h2>Why it keeps a fractional position instead of just smoothing the drawn integer</h2>
 *
 * <p>Smoothing only the rounded pixel would leave the map's world-to-screen transform oscillating by one
 * pixel while the underlying sample point stayed put — markers would shimmer against the terrain. The
 * position kept here is therefore in world blocks and continuous, and rounding happens once, at the
 * projection step.
 */
final class MapFollow {
    /**
     * Seconds for half the remaining distance to be covered.
     *
     * <p>Short enough that the map does not lag noticeably behind a sprinting player, long enough that
     * server-snapshot steps (the player's position arrives at 20 Hz, the map draws at 60+) are smoothed
     * into continuous motion. This is the number that decides whether the map "feels like glass".
     */
    private static final double HALF_LIFE = 0.07;

    /**
     * How far the centre may drift from the true position before it is snapped instead.
     *
     * <p>A teleport — respawn, deploy, a vehicle seat change — is not something to animate; a map that
     * slides across the whole arena to the player's new position looks broken and takes a full second.
     * Beyond this distance the follow is set directly, so teleports are instant and walking is smooth.
     */
    private static final double TELEPORT_BLOCKS = 48.0;

    private double x;
    private double z;
    private boolean primed;

    /** @return the smoothed coordinates, in the same world space as the arguments */
    double[] to(double targetX, double targetZ, double deltaSeconds) {
        if (!primed) {
            primed = true;
            x = targetX;
            z = targetZ;
            return new double[] { x, z };
        }
        double gap = Math.hypot(targetX - x, targetZ - z);
        if (gap > TELEPORT_BLOCKS) {
            x = targetX;
            z = targetZ;
            return new double[] { x, z };
        }
        x = Smooth.to(x, targetX, deltaSeconds, HALF_LIFE);
        z = Smooth.to(z, targetZ, deltaSeconds, HALF_LIFE);
        return new double[] { x, z };
    }

    /** True while the centre is still catching up, so callers can skip work that only matters at rest. */
    boolean moving() {
        return primed;
    }

    void reset() {
        primed = false;
    }
}
