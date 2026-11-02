package cn.bfops;

import net.minecraft.client.Minecraft;

/**
 * Frame-rate independent smoothing for every animated value in the HUD.
 *
 * <h2>Why a shared helper</h2>
 *
 * <p>Every animated element in this mod — the HUD's inertial sway, the minimap's follow, the compass's
 * heading, a mark's fade — needs the same thing: move a displayed value towards a target without
 * depending on how many frames the client happens to render. Written inline at each site, that is the
 * single easiest way to ship an animation that feels different at 60 fps and at 144 fps, and the bug is
 * invisible to whoever wrote it because their machine is the only one they tested on.
 *
 * <p>The rule, stated once here so the sites can stay short: the step is a function of <b>elapsed
 * time</b>, never of frame count.
 *
 * <pre>{@code
 * // correct: half-life in seconds, so any frame rate converges in the same wall-clock time
 * value += (target - value) * Smooth.factor(dt, halfLife);
 *
 * // wrong: a fixed fraction per frame. At 144 fps this is 2.4x faster than at 60 fps.
 * value += (target - value) * 0.2;
 * }</pre>
 *
 * <h2>Why exponential rather than linear</h2>
 *
 * <p>Exponential approach is what the reference pack's motion HUD uses ({@code damping}, a coefficient
 * rather than a per-frame step, in {@code gd656motionhud-client.toml}) and it has two properties that
 * matter for a HUD:
 *
 * <ul>
 *   <li>It can never overshoot, so nothing needs a spring or a clamp on the far side.</li>
 *   <li>It is stateless apart from one float per value — no velocity to store, no accumulator to reset
 *       when the FOV or window size changes mid-match.</li>
 * </ul>
 *
 * <p>The cost of exponential over linear is that it never quite arrives. The snap threshold below is
 * what stops a value from creeping forever at the sub-pixel level, which would otherwise mean a HUD
 * that re-renders at a different angle every frame while the player stands still.
 */
final class Smooth {
    /**
     * Below this the value is treated as arrived.
     *
     * <p>Values here are mostly screen-space offsets in pixels or degrees of pitch; a twentieth of a
     * pixel (0.05) is under half of any HUD's smallest feature, so snapping is invisible. It also
     * guarantees the render path reaches a genuinely static state, which matters for the minimap
     * bake: a value that keeps changing by 1e-9 would otherwise invalidate the cached texture forever.
     */
    static final double SNAP = 0.05;

    private Smooth() { }

    /**
     * The fraction of the remaining distance to cover this frame.
     *
     * @param deltaSeconds elapsed since the previous frame
     * @param halfLife     seconds for the remaining error to halve; larger is slower and softer
     * @return 0..1, the share of the gap to close now
     */
    static double factor(double deltaSeconds, double halfLife) {
        if (halfLife <= 0) return 1.0;
        if (deltaSeconds <= 0) return 0.0;
        // 0.5^(dt/halfLife), which is 1 - e^(-dt * ln2 / halfLife). Written with pow so the "half-life"
        // parameter means literally what it says, rather than a rate constant the caller has to convert.
        return 1.0 - Math.pow(0.5, deltaSeconds / halfLife);
    }

    /** Exponential approach of {@code current} towards {@code target}, frame-rate independent. */
    static double to(double current, double target, double deltaSeconds, double halfLife) {
        double next = current + (target - current) * factor(deltaSeconds, halfLife);
        return Math.abs(target - next) < SNAP ? target : next;
    }

    /** Float overload, for call sites that store angles and poses as floats. */
    static float to(float current, float target, double deltaSeconds, double halfLife) {
        return (float) to((double) current, (double) target, deltaSeconds, halfLife);
    }

    /**
     * Angle approach that takes the short way round.
     *
     * <p>Needed for anything driven by yaw: 359° and 1° are two degrees apart, but a plain linear
     * blend would sweep 358° the wrong way through the whole circle. The client's own camera code
     * wraps the same way, so matching it here is what keeps a smoothed compass agreeing with the
     * crosshair.
     *
     * @param current  degrees, any range
     * @param target   degrees, any range
     * @param halfLife seconds for the remaining error to halve
     * @return the smoothed angle, wrapped to [-180, 180)
     */
    static float angle(float current, float target, double deltaSeconds, double halfLife) {
        float delta = wrapDegrees(target - current);
        float next = current + delta * (float) factor(deltaSeconds, halfLife);
        if (Math.abs(delta) < SNAP) next = target;
        return wrapDegrees(next);
    }

    /**
     * Wraps a degree delta into [-180, 180), the shortest signed path between two headings.
     *
     * <p>Deliberately identical to vanilla's {@code Mth.wrapDegrees}, down to the half-open range: 180 folds
     * to -180 rather than staying at 180. Matching vanilla matters more here than picking a tidier range —
     * this value is compared against camera angles that the game itself wrapped, and a convention that
     * disagreed at the single boundary point would make a smoothed heading flip sign for one frame as it
     * crossed due south.
     */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /**
     * Seconds since the previous frame.
     *
     * <p>Taken from the client's own delta tracker rather than from {@code System.nanoTime()} differences
     * held in a field of our own: the client already computes this, already clamps it, and already
     * freezes it while a menu is open. A second clock here would disagree with the one the camera and
     * the entity interpolators use, which is precisely the sort of mismatch that shows up as a HUD
     * that lags behind the world by one frame.
     *
     * <p><b>Why {@code getRealtimeDeltaTicks} and not {@code getGameTimeDeltaTicks}.</b> The game-time
     * variant is scaled by {@code /tick freeze}, {@code /tick rate} and the pause state, so a paused or
     * slowed world would freeze or stretch the HUD animation with it — a menu that stops the minimap
     * sliding back is not what "smooth" means. Real-time is the wall-clock frame delta, which is what an
     * animation should follow.
     *
     * <p>Clamped to 0.1 s: a stall (loading a dimension, a GC pause, alt-tabbing out) produces a
     * legitimate but enormous delta, and feeding that to an exponential approach makes the value jump
     * straight to its target with no smoothing at all. Capping means the worst case is a visible
     * animation that is briefly slower than intended, rather than no animation.
     */
    static double deltaSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 0.05;
        float seconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0F;
        if (!(seconds > 0)) return 0.05;
        return Math.min(0.1, seconds);
    }
}
