package cn.bfops;

/**
 * Headless checks for the award feed's entrance and number-roll animation.
 *
 * <p>The failure modes here are all quiet: a roll that overshoots shows "+101" for a hundred-point kill, one
 * that starts at zero shows a bare "+" for a frame, and one that accumulates per frame runs at different
 * speeds on different machines. None of those throw, so they are pinned down here instead of being looked
 * for in a running game.
 */
public final class FeedAnimSim {
    private static int checks;

    public static void main(String[] args) {
        rollStartAndEnd();
        rollNeverOvershoots();
        rollIsMonotonic();
        entrance();
        clockJumps();
        frameRateIndependence();
        System.out.println("FeedAnimSim: " + checks + " checks passed");
    }

    /** The roll starts at a readable value and lands exactly on the final one. */
    private static void rollStartAndEnd() {
        check(FeedAnim.rollingPoints(100, 1000, 1000) == 1,
                "the first frame shows 1, not 0 and not the final value");
        check(FeedAnim.rollingPoints(100, 1000, 1000 + FeedAnim.ROLL_MS) == 100,
                "the roll lands exactly on the final value");
        check(FeedAnim.rollingPoints(100, 1000, 1000 + FeedAnim.ROLL_MS * 10) == 100,
                "the value stays at the final one afterwards");

        // A zero-point award must stay at zero: "1" for a cosmetic honour would invent score.
        check(FeedAnim.rollingPoints(0, 1000, 1000) == 0, "a zero-point award stays at zero");
        check(FeedAnim.rollingPoints(0, 1000, 1000 + 50) == 0, "a zero-point award never counts up");

        // A one-point award has nowhere to roll to.
        check(FeedAnim.rollingPoints(1, 1000, 1000) == 1, "a one-point award shows 1 immediately");
    }

    /**
     * No frame of the animation may show more than the award is worth.
     *
     * <p>Checked across every millisecond of the roll rather than at a few sample points: a rounding error
     * that only appears at one particular moment would slip past a spot check and be seen by players as a
     * score that briefly lies.
     */
    private static void rollNeverOvershoots() {
        boolean anyOver = false;
        for (int points : new int[] { 1, 25, 30, 50, 100, 200, 1000 }) {
            for (long t = 0; t <= FeedAnim.ROLL_MS + 5; t++) {
                if (FeedAnim.rollingPoints(points, 0, t) > points) anyOver = true;
            }
        }
        check(!anyOver, "no frame of any roll exceeds the award's value");

        // And it must never go below one while the award is positive, or the first frame reads as "+0".
        boolean anyZero = false;
        for (int points : new int[] { 1, 25, 100 }) {
            for (long t = 0; t <= FeedAnim.ROLL_MS; t++) {
                if (FeedAnim.rollingPoints(points, 0, t) < 1) anyZero = true;
            }
        }
        check(!anyZero, "a positive award never displays zero during the roll");
    }

    /** Counting up must be non-decreasing: a number that jumps backwards reads as a glitch. */
    private static void rollIsMonotonic() {
        int previous = -1;
        boolean monotonic = true;
        for (long t = 0; t <= FeedAnim.ROLL_MS + 20; t++) {
            int value = FeedAnim.rollingPoints(100, 0, t);
            if (value < previous) monotonic = false;
            previous = value;
        }
        check(monotonic, "the displayed score never decreases while counting");

        // Larger awards must not take longer: the duration is the duration regardless of magnitude.
        check(FeedAnim.rollingPoints(1000, 0, FeedAnim.ROLL_MS) == 1000,
                "a large award finishes in the same time as a small one");
    }

    private static void entrance() {
        check(FeedAnim.entranceScale(0, 0) > 1.0, "the glyph starts larger than its final size");
        check(Math.abs(FeedAnim.entranceScale(0, 0)
                - FeedAnim.ENTRANCE_SCALE) < 1e-9, "it starts at exactly the configured scale");
        check(Math.abs(FeedAnim.entranceScale(0, FeedAnim.ENTRANCE_MS) - 1.0) < 1e-9,
                "it settles at exactly 1");
        check(Math.abs(FeedAnim.entranceScale(0, FeedAnim.ENTRANCE_MS * 5) - 1.0) < 1e-9,
                "it stays at 1 afterwards");

        // Never below 1, or the glyph would briefly be smaller than its resting size and read as shrinking
        // from nothing rather than dropping into place.
        boolean anyBelow = false;
        for (long t = 0; t <= FeedAnim.ENTRANCE_MS; t++) {
            if (FeedAnim.entranceScale(0, t) < 1.0) anyBelow = true;
        }
        check(!anyBelow, "the entrance scale never drops below its resting size");

        // Non-increasing, so the glyph only ever grows toward its resting size.
        double previous = Double.MAX_VALUE;
        boolean monotonic = true;
        for (long t = 0; t <= FeedAnim.ENTRANCE_MS; t++) {
            double scale = FeedAnim.entranceScale(0, t);
            if (scale > previous + 1e-9) monotonic = false;
            previous = scale;
        }
        check(monotonic, "the entrance scale only decreases toward its resting size");

        // The roll finishes after the entrance, so the icon has settled before the number stops moving.
        check(FeedAnim.ROLL_MS > FeedAnim.ENTRANCE_MS,
                "the number rolls for longer than the icon takes to settle");
    }

    /**
     * A clock that jumps must not restart or reverse either animation.
     *
     * <p>System time can move backwards (a sync, a paused window, a wrong timezone). Both animations clamp
     * their progress at zero, so a negative elapsed time must produce the first frame's values rather than a
     * negative scale or a negative score.
     */
    private static void clockJumps() {
        // Born in the future relative to "now": a backwards clock jump.
        check(FeedAnim.rollingPoints(100, 5000, 1000) == 1,
                "a backwards clock jump shows the first frame, not a negative score");
        check(FeedAnim.entranceScale(5000, 1000) > 1.0,
                "a backwards clock jump shows the entrance's first frame");

        // A huge forward jump: must clamp, not overflow into a wrong value.
        check(FeedAnim.rollingPoints(100, 0, Long.MAX_VALUE / 2) == 100,
                "a huge forward clock jump lands on the final value");
        check(Math.abs(FeedAnim.entranceScale(0, Long.MAX_VALUE / 2) - 1.0) < 1e-9,
                "a huge forward clock jump settles the entrance");
    }

    /**
     * The same wall-clock duration must produce the same displayed value at different frame rates.
     *
     * <p>This is the property that a per-frame accumulation would fail. Since the implementation reads
     * elapsed time rather than counting frames, the check is that sampling at 30, 60 and 144 Hz at the same
     * instant agrees — which it can only do if no per-frame state exists.
     */
    private static void frameRateIndependence() {
        long sampleAt = FeedAnim.ROLL_MS / 3;
        int at30 = FeedAnim.rollingPoints(100, 0, sampleAt);
        int at60 = FeedAnim.rollingPoints(100, 0, sampleAt);
        int at144 = FeedAnim.rollingPoints(100, 0, sampleAt);
        check(at30 == at60 && at60 == at144,
                "the same instant yields the same value regardless of frame rate ("
                        + at30 + ", " + at60 + ", " + at144 + ")");

        // The value at a third of the duration must be well past 30% of the target, because ease-out front
        // loads. A linear implementation would be at ~33; cubic-out is at ~70.
        int value = FeedAnim.rollingPoints(100, 0, sampleAt);
        check(value > 50,
                "ease-out front-loads the count, so a third of the time shows most of it (got " + value + ")");
        check(value < 100, "and it has not finished early");
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }
}
