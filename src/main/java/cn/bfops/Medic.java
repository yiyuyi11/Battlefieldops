package cn.bfops;

/**
 * Downed / revive rules: how long a downed player has, and how fast a teammate can bring them back.
 *
 * <p><b>What "downed" means here.</b> A lethal hit does not kill outright. The player drops with one health
 * point, can crawl slowly, and starts a bleed-out clock. A teammate who right-clicks them with an empty hand and
 * stays close brings them back. If nobody comes, the clock runs out and the death happens as it always did.
 *
 * <p><b>Why the progress decays.</b> A revive that only counts up means clicking once and walking away still
 * finishes the job, which removes the risk that makes a revive decision interesting. Progress therefore
 * falls back when the helper stops, so helping is something you commit to.
 *
 * <p>Kept free of Minecraft types so the arithmetic — the part that gets a player killed by a rounding mistake —
 * can be checked headless.
 */
final class Medic {
    static boolean continuingRescue(long now, long lastHeld, boolean validTarget) {
        return validTarget && now >= lastHeld && now - lastHeld <= 6;
    }

    /** Ticks a downed player has before bleeding out, from the map's configured seconds. */
    static long bleedOutTicks(int seconds) {
        return Math.max(1, seconds) * 20L;
    }

    /** Ticks of revive channel needed to bring someone back. */
    static int reviveTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }

    /** Whether the bleed-out clock has run out. */
    static boolean expired(long bleedOutAt, long nowTicks) {
        return nowTicks >= bleedOutAt;
    }

    /** Seconds left on the clock, rounded up, never negative. */
    static int secondsLeft(long bleedOutAt, long nowTicks) {
        long ticks = bleedOutAt - nowTicks;
        if (ticks <= 0) return 0;
        return (int) ((ticks + 19) / 20);
    }

    /**
     * Advance the revive bar by one tick.
     *
     * <p>Falls back twice as slowly as it fills: a helper who has to step away briefly does not lose everything,
     * but a helper who is not committed makes no progress either.
     *
     * @param current  progress so far, in ticks
     * @param helped   whether a teammate is actively reviving this tick
     * @param required ticks needed for a complete revive
     * @return the new progress, clamped to {@code [0, required]}
     */
    static int progress(int current, boolean helped, int required) {
        if (required <= 0) return 0;
        int next = helped ? current + 1 : current - 2;
        return Math.max(0, Math.min(required, next));
    }

    /** 0..1, for the progress bar. */
    static double fraction(int progress, int required) {
        if (required <= 0) return 1;
        return Math.max(0, Math.min(1, progress / (double) required));
    }

    /** Health to restore on a successful revive, clamped so a map cannot configure an invalid value. */
    static float revivedHealth(float configured, float maxHealth) {
        float value = configured <= 0 ? maxHealth * 0.3f : configured;
        return Math.max(1, Math.min(Math.max(1, maxHealth), value));
    }
}
