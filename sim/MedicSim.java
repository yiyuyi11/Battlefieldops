package cn.bfops;

/**
 * Downed and revive arithmetic.
 *
 * <p>The failure modes here are the ones that get somebody killed: a clock that reads one second when it means
 * zero, a revive bar that fills while nobody is there, or a revive that restores more health than the map
 * allows. All three are arithmetic, so all three are checked here.
 */
public final class MedicSim {
    private static int checks;

    public static void main(String[] args) {
        clocks();
        progress();
        health();
        System.out.println("MedicSim: " + checks + " checks passed");
    }

    private static void clocks() {
        check(Medic.bleedOutTicks(30) == 600, "30 seconds is 600 ticks");
        check(Medic.bleedOutTicks(0) == 20, "a zero-second bleed-out still lasts one second rather than none");
        check(Medic.bleedOutTicks(-5) == 20, "a negative duration is treated as the minimum");
        check(Medic.reviveTicks(4) == 80, "4 seconds of revive is 80 ticks");
        check(Medic.reviveTicks(0) == 20, "a zero revive duration still takes one second");

        // Expiry is inclusive: at the deadline the player is gone.
        check(!Medic.expired(1_200, 1_199), "one tick before the deadline is not expired");
        check(Medic.expired(1_200, 1_200), "the deadline itself is expired");
        check(Medic.expired(1_200, 5_000), "well past the deadline is expired");

        // The countdown rounds up, so it never shows 0 while a tick remains.
        check(Medic.secondsLeft(1_200, 1_200) == 0, "no time left reads zero");
        check(Medic.secondsLeft(1_200, 1_181) == 1, "a partial second reads one");
        check(Medic.secondsLeft(1_200, 1_100) == 5, "five seconds left reads five");
        check(Medic.secondsLeft(1_200, 2_000) == 0, "an overrun never reads negative");
    }

    private static void progress() {
        int required = Medic.reviveTicks(4);

        check(Medic.progress(0, true, required) == 1, "helping advances the bar");
        check(Medic.progress(10, true, required) == 11, "and keeps advancing");
        check(Medic.progress(10, false, required) == 8, "walking away loses progress, twice as fast");
        check(Medic.progress(0, false, required) == 0, "progress never goes negative");
        check(Medic.progress(required - 1, true, required) == required, "the last tick completes it");
        check(Medic.progress(required, true, required) == required, "it never exceeds the requirement");
        check(Medic.progress(required + 50, true, required) == required, "and never overflows");

        // A zero requirement means "already done", not a division by zero.
        check(Medic.progress(5, true, 0) == 0, "a zero requirement yields no progress");

        check(Medic.fraction(0, required) == 0, "an empty bar is zero");
        check(Medic.fraction(required, required) == 1, "a full bar is one");
        check(Math.abs(Medic.fraction(required / 2, required) - 0.5) < 1e-9, "half is half");
        check(Medic.fraction(999, required) == 1, "an overrun bar clamps to full");
        check(Medic.fraction(0, 0) == 1, "a zero requirement reads as complete");

        // A full revive takes exactly the configured time when somebody commits.
        int ticks = 0;
        int progress = 0;
        while (progress < required && ticks < 1_000) {
            progress = Medic.progress(progress, true, required);
            ticks++;
        }
        check(ticks == required, "a committed revive takes exactly the configured ticks (" + ticks + ")");
    }

    private static void health() {
        check(Medic.revivedHealth(12, 20) == 12, "a configured value is used as-is");
        check(Medic.revivedHealth(0, 20) == 6, "zero means 30% of maximum");
        check(Medic.revivedHealth(999, 20) == 20, "an oversized value is clamped to maximum");
        check(Medic.revivedHealth(-5, 20) == 6, "a negative value falls back to the default fraction");
        check(Medic.revivedHealth(0.1f, 20) == 1, "a revive never kills the player it just saved");
        check(Medic.revivedHealth(0, 1) == 1, "and works for a one-health maximum");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
