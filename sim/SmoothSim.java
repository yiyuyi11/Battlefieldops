package cn.bfops;

/**
 * Headless checks for the frame-rate-independent smoothing primitives.
 *
 * <p>These are the pieces every animation in the HUD now depends on, and their failure mode is subtle
 * rather than loud: a wrong half-life does not throw, it just feels wrong on somebody else's monitor. So
 * the properties that must hold are asserted directly — convergence, monotonic approach, angle wrapping,
 * and above all that the same wall-clock duration produces the same result at different frame rates.
 *
 * <p>Only the pure arithmetic is exercised. {@code Smooth.deltaSeconds()} reads the client's delta
 * tracker and is therefore untestable here by design — it is the one method in that class with no
 * decision worth testing, since its whole job is to return a number the game already computed.
 */
public final class SmoothSim {
    private static int checks;

    public static void main(String[] args) {
        frameRateIndependence();
        convergence();
        angleWrapping();
        factorBounds();
        mapFollow();
        System.out.println("SmoothSim: " + checks + " checks passed");
    }

    /**
     * The single property the whole helper exists for: 60 fps and 144 fps must agree.
     *
     * <p>Simulated by stepping the same elapsed time in different numbers of sub-steps. This is exactly
     * what a fixed-per-frame step would fail, which is why it is the first check rather than a footnote.
     */
    private static void frameRateIndependence() {
        // Sampled at 1/6 s. That instant is a whole number of frames at 30, 60 and 144 fps (5, 10 and 24),
        // so all three variants advance exactly the same amount of wall-clock time — the comparison is then
        // about the interpolation, not about the frame counts. Sampling at one second would be useless: by
        // then every variant has converged to the target and a broken implementation would pass by accident.
        double at60 = step(0, 100, 1.0 / 60, 0.1, 10);
        double at144 = step(0, 100, 1.0 / 144, 0.1, 24);
        check(Math.abs(at60 - at144) < 0.5,
                "60 fps and 144 fps agree over 1/6 s (60=" + at60 + ", 144=" + at144 + ")");
        double at30 = step(0, 100, 1.0 / 30, 0.1, 5);
        check(Math.abs(at30 - at144) < 0.5,
                "30 fps agrees with 144 fps as well (30=" + at30 + ", 144=" + at144 + ")");

        // A fixed fraction per frame is the bug this replaces; assert it really does diverge over the same
        // 1/6 s, so this test fails if somebody reverts the helper to "value += (target-value) * 0.2".
        double naive60 = naive(0, 100, 0.2, 10);
        double naive144 = naive(0, 100, 0.2, 24);
        check(Math.abs(naive60 - naive144) > 5,
                "a fixed per-frame fraction would diverge (60=" + naive60 + ", 144=" + naive144 + ")");
    }

    private static void convergence() {
        double dt = 1.0 / 60;
        double value = 0;
        for (int i = 0; i < 600; i++) value = Smooth.to(value, 100, dt, 0.1);
        check(value == 100, "converges exactly to the target, not merely close (got " + value + ")");

        // Monotonic: an exponential approach must never overshoot or reverse.
        double previous = 0;
        boolean monotonic = true;
        value = 0;
        for (int i = 0; i < 200; i++) {
            value = Smooth.to(value, 100, dt, 0.1);
            if (value < previous - 1e-9) monotonic = false;
            previous = value;
        }
        check(monotonic, "never overshoots or reverses while approaching");

        value = 100;
        for (int i = 0; i < 600; i++) value = Smooth.to(value, 0, dt, 0.1);
        check(value == 0, "converges downward as well (got " + value + ")");

        // Standing still must produce a genuinely static value: this is what lets the map bake stay cached.
        check(Smooth.to(42, 42, dt, 0.1) == 42, "an already-arrived value does not creep");
    }

    private static void angleWrapping() {
        // 359 -> 1 must go forward through 0, not backward through 180.
        float wrappedShort = Smooth.angle(359f, 1f, 0.05, 0.05);
        check(wrappedShort > 350f || wrappedShort < 10f,
                "359 to 1 takes the short way round (got " + wrappedShort + ")");

        check(Smooth.wrapDegrees(370f) == 10f, "wrapDegrees(370) == 10");
        check(Smooth.wrapDegrees(-370f) == -10f, "wrapDegrees(-370) == -10");
        // Half-open range [-180, 180), matching vanilla Mth.wrapDegrees: 180 folds to -180.
        check(Smooth.wrapDegrees(180f) == -180f, "wrapDegrees(180) folds to -180, as vanilla does");
        check(Smooth.wrapDegrees(-180f) == -180f, "wrapDegrees(-180) stays at -180");
        check(Smooth.wrapDegrees(0f) == 0f, "wrapDegrees keeps zero");
        check(Smooth.wrapDegrees(179.9f) == 179.9f, "wrapDegrees keeps just under 180");

        float jumped = Smooth.angle(170f, -170f, 0.05, 0.05);
        check(jumped >= -180f && jumped <= 180f, "angle output stays in range (got " + jumped + ")");
    }

    private static void factorBounds() {
        check(Smooth.factor(0, 0.1) == 0.0, "zero delta means no movement");
        check(Smooth.factor(1.0 / 60, 0) == 1.0, "a zero half-life snaps immediately");
        double f = Smooth.factor(0.1, 0.1);
        check(Math.abs(f - 0.5) < 1e-9, "one half-life closes exactly half the gap (got " + f + ")");
        check(Smooth.factor(-1, 0.1) == 0.0, "a negative delta cannot move backwards");
        check(Smooth.factor(100, 0.1) <= 1.0, "a very large delta is still clamped to 1");
    }

    /**
     * The map centre's follow behaviour, including the teleport rule.
     *
     * <p>The teleport case is the one with a visible failure mode: if a respawn 200 blocks away were
     * interpolated rather than snapped, the map would sweep across the whole arena and take about a second
     * to arrive, during which every marker on it is wrong.
     */
    private static void mapFollow() {
        MapFollow follow = new MapFollow();
        // First sample primes: no glide-in from an arbitrary origin.
        double[] primed = follow.to(1000, 2000, 1.0 / 60);
        check(primed[0] == 1000 && primed[1] == 2000, "first sample jumps straight to the target");

        // Walking: a small step is followed smoothly, arriving within a few frames.
        double[] near = follow.to(1001, 2000, 1.0 / 60);
        check(near[0] > 1000 && near[0] <= 1001, "a small step is interpolated, not snapped");

        // Teleport: a jump beyond the threshold lands immediately.
        double[] far = follow.to(1300, 2000, 1.0 / 60);
        check(far[0] == 1300, "a teleport snaps instead of gliding (got " + far[0] + ")");

        // Reset must re-prime, so the next session does not slide in from the old position.
        follow.reset();
        double[] afterReset = follow.to(-500, -500, 1.0 / 60);
        check(afterReset[0] == -500, "reset re-primes instead of gliding from the old centre");
    }

    /** Steps {@code seconds} of simulated time in {@code frames} steps. */
    private static double step(double from, double to, double dt, double halfLife, int frames) {
        double value = from;
        for (int i = 0; i < frames; i++) value = Smooth.to(value, to, dt, halfLife);
        return value;
    }

    /** The anti-pattern the helper replaces, kept so the divergence check above is meaningful. */
    private static double naive(double from, double to, double fraction, int frames) {
        double value = from;
        for (int i = 0; i < frames; i++) value += (to - value) * fraction;
        return value;
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }
}
