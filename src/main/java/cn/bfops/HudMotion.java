package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Inertial sway for the match HUD: the overlay trails the view slightly and settles back when the player
 * stops moving it.
 *
 * <h2>What this reproduces, and from where</h2>
 *
 * <p>The reference pack does this with a mod of four classes whose entire config is three numbers —
 * {@code gd656motionhud-client.toml}:
 *
 * <pre>
 * maxOffset   = 25.0   # largest offset, in pixels
 * damping     = 0.25   # how fast it recentres
 * sensitivity = 0.1    # how far it moves per unit of view change
 * </pre>
 *
 * <p>That is the whole feature. It is worth copying precisely <em>because</em> it is so small: the
 * effect is a translated HUD, which costs one {@code pose().translate} per frame and nothing else. No
 * extra draw calls, no textures, no geometry — which is why it is the first item in the frame-cost
 * budget rather than the last.
 *
 * <h2>Why the offset is derived from lag, not from a stored "centre"</h2>
 *
 * <p>The obvious implementation keeps a reference heading and offsets by the difference from it. That
 * has a failure mode: the reference has to be re-based whenever the player respawns, changes vehicle,
 * or is teleported by the match, and every re-base is a visible snap.
 *
 * <p>Instead this tracks a <em>smoothed</em> copy of the player's own yaw and pitch, and offsets by how
 * far the real view has run ahead of it. Turning quickly opens a gap and the HUD sways; holding still
 * closes the gap and the sway returns to zero on its own. There is no centre to maintain, so there is
 * nothing to reset and no snap to see — the value is self-correcting by construction.
 *
 * <p>The gap is also naturally bounded: it can only grow as fast as the view turns, so the offset is
 * clamped to the reference's 25 px and in practice rarely reaches it.
 */
final class HudMotion {
    /** Largest offset in pixels, matching the reference pack's {@code maxOffset}. */
    private static final double MAX_OFFSET = 25.0;

    /**
     * Pixels of offset per degree of view lag.
     *
     * <p>The reference uses {@code sensitivity = 0.1} against its own internal units, which are not
     * comparable; this is tuned so a normal 90°/second mouse sweep produces roughly a 10 px sway —
     * visible enough to feel, far short of the 25 px clamp.
     */
    private static final double SENSITIVITY = 0.30;

    /** Vertical sway is deliberately weaker than horizontal: a HUD that slides up and down reads as broken. */
    private static final double VERTICAL_SCALE = 0.45;

    /** Seconds for half the view lag to be caught up. Lower is snappier, higher is floatier. */
    private static final double YAW_HALF_LIFE = 0.06;
    private static final double PITCH_HALF_LIFE = 0.09;

    private static double smoothYaw;
    private static double smoothPitch;
    private static boolean primed;

    private static double offsetX;
    private static double offsetY;

    private HudMotion() { }

    /**
     * Advances the sway by one frame and returns the current offset.
     *
     * <p>Called once per HUD frame from {@link CombatHud}. Returns {@code {0,0}} — after having advanced
     * the internal state — whenever the effect must be suppressed, so switching the vanilla view-bob
     * option off, or opening a screen, does not freeze the smoothing and then jump when play resumes.
     *
     * @return {@code {dx, dy}} in GUI pixels, both already rounded by the caller
     */
    static double[] advance() {
        Entity player = Minecraft.getInstance().player;
        double dt = Smooth.deltaSeconds();

        if (player == null) {
            primed = false;
            offsetX = 0;
            offsetY = 0;
            return new double[] { 0, 0 };
        }

        // Prime on the first frame so the very first offset is zero rather than "however far the view
        // happens to be from 0,0" — which on a world spawn is a large, arbitrary jump.
        if (!primed) {
            primed = true;
            smoothYaw = player.getYRot();
            smoothPitch = player.getXRot();
        }

        double yaw = player.getYRot();
        // Yaw wraps, so the approach has to take the short way round or a step across the 180° seam
        // would register as a 360° flick and slam the offset to its clamp for a few frames.
        smoothYaw = yaw + Smooth.wrapDegrees((float) (smoothYaw - yaw)) * Math.pow(0.5, dt / YAW_HALF_LIFE);
        smoothPitch = Smooth.to(smoothPitch, player.getXRot(), dt, PITCH_HALF_LIFE);

        double lagYaw = Smooth.wrapDegrees((float) (yaw - smoothYaw));
        double lagPitch = player.getXRot() - smoothPitch;

        // Sign: turning right (increasing yaw) should push the HUD left, which is the direction the
        // world appears to slide in the opposite of the turn. Negated so the two agree.
        offsetX = clamp(-lagYaw * SENSITIVITY, MAX_OFFSET);
        offsetY = clamp(lagPitch * SENSITIVITY * VERTICAL_SCALE, MAX_OFFSET);

        if (!enabled()) return new double[] { 0, 0 };
        return new double[] { offsetX, offsetY };
    }

    /**
     * Whether the sway may be applied at all.
     *
     * <p>Tied to the vanilla <b>View Bobbing</b> option on purpose. A player who turned bobbing off has
     * said they want a still camera; moving their HUD instead would be honouring the letter of that
     * setting while ignoring what it was asked for. It also gives the effect a discoverable off switch
     * without adding a config file entry the operator would have to explain.
     */
    private static boolean enabled() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.bobView().get();
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** Drops the accumulated state, e.g. on logout, so the next match starts centred. */
    static void reset() {
        primed = false;
        smoothYaw = 0;
        smoothPitch = 0;
        offsetX = 0;
        offsetY = 0;
    }
}
