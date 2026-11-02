package cn.bfops;

/**
 * Entrance and number-roll animation for the award feed.
 *
 * <h2>What this reproduces</h2>
 *
 * <p>The reference pack's {@code gd656killicon} animates each award with {@code DigitalScrollEffect} — a
 * value rolled from old to new over a duration, with a choice of easing ({@code QUINTIC_OUT},
 * {@code CUBIC_OUT}, {@code LINEAR}) — plus {@code IconEntranceBackground} for the glyph's entrance. Both are
 * reproduced here as pure arithmetic, because the arithmetic is the whole feature.
 *
 * <h2>Why the numbers roll rather than appear</h2>
 *
 * <p>A score that appears at its final value is read once. A score that counts up is read as an
 * <em>event</em> — the eye catches the motion and then settles, which is what makes a "+100" feel like a
 * reward rather than like a label. That is a real perceptual effect and the reason the reference pack spends
 * a whole class on it.
 *
 * <h2>Why the easing is cubic and not linear</h2>
 *
 * <p>Linear counting looks mechanical and, worse, looks slow at the start: the visible digits barely change
 * in the first tenth of the duration. Ease-out cubic front-loads the movement so the number seems to leap
 * toward its target and settle, which reads as decisive. The reference pack's default is {@code CUBIC_OUT}
 * among the three it offers; that default is followed.
 *
 * <h2>Frame-rate independence</h2>
 *
 * <p>Progress is computed from elapsed milliseconds against a fixed duration, not accumulated per frame. An
 * accumulated animation would run at different speeds on a 60 Hz and a 144 Hz machine — the exact defect that
 * {@link Smooth} exists to avoid elsewhere in this mod, and it would be strange to fix it in one place and
 * reintroduce it in another.
 */
final class FeedAnim {
    /** How long the score counts up. Short enough to finish while the award is still being read. */
    static final long ROLL_MS = 260L;

    /** How long the glyph's entrance lasts. Deliberately shorter than the roll: the icon settles first. */
    static final long ENTRANCE_MS = 180L;

    /** The glyph starts this much larger than its final size, as a fraction. */
    static final double ENTRANCE_SCALE = 1.6;

    private FeedAnim() { }

    /**
     * The score to display right now, counting up toward its final value.
     *
     * @param points final score
     * @param bornAt when the award was created, in wall-clock milliseconds
     * @return the partially-counted score, never above {@code points}
     */
    static int rollingPoints(int points, long bornAt, long now) {
        if (points <= 0) return points;
        double progress = progress(bornAt, now, ROLL_MS);
        if (progress >= 1) return points;
        // Ease-out cubic, matching the reference pack's default easing.
        double eased = easeOutCubic(progress);
        int shown = (int) Math.round(points * eased);
        // Never overshoot and never show more than the award is worth: a round-off that displayed +101 for a
        // hundred-point kill would be the kind of detail players notice and report.
        if (shown > points) shown = points;
        // Counting from a floor of one rather than zero: a "+0" that then becomes "+100" reads as a bug for
        // the frames it is up, and the first frame matters because that is the one the player is looking at.
        if (shown < 1 && points > 0) shown = 1;
        return shown;
    }

    /**
     * Scale multiplier for the glyph's entrance.
     *
     * <p>Starts above 1 and eases down to exactly 1, so the icon appears to drop into place. The value is
     * applied by drawing the glyph at a larger size, which for the bitmap glyphs in {@link FeedIcons} means
     * simply drawing each lit pixel as a larger square.
     *
     * @return a multiplier at or above 1
     */
    static double entranceScale(long bornAt, long now) {
        double progress = progress(bornAt, now, ENTRANCE_MS);
        if (progress >= 1) return 1.0;
        double eased = easeOutCubic(progress);
        return 1.0 + (ENTRANCE_SCALE - 1.0) * (1.0 - eased);
    }

    /** True while the number is still counting, so the caller knows whether to re-read the value. */
    static boolean rolling(long bornAt, long now) {
        return progress(bornAt, now, ROLL_MS) < 1;
    }

    /**
     * 0..1 progress through a fixed duration.
     *
     * <p>Clamped at both ends. A clock that jumps backwards — a system time change, or a paused window — must
     * not make the animation restart or run negative, and a clock that jumps forward must not make it run past
     * its end.
     */
    private static double progress(long bornAt, long now, long durationMs) {
        if (durationMs <= 0) return 1.0;
        double value = (now - bornAt) / (double) durationMs;
        return Math.max(0, Math.min(1, value));
    }

    /** Ease-out cubic: fast at the start, settling at the end. */
    private static double easeOutCubic(double t) {
        double inverse = 1.0 - t;
        return 1.0 - inverse * inverse * inverse;
    }
}
