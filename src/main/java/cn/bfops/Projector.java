package cn.bfops;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * World-to-screen projection, in one place.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Every overlay in this mod that is anchored to the world needs the same three steps: take a world
 * position, express it relative to the camera, and divide by depth to get a screen position. That arithmetic
 * was written out three times — {@link WorldMarkers}, {@link Nameplates}, {@link MuzzleGlow} — and a fourth
 * copy was about to be added for deploy-phase markers.
 *
 * <p>Four copies of a projection is not a style problem, it is a correctness problem, and the reason is
 * specific: the projection depends on conventions that are easy to state and easy to get wrong. Yaw
 * zero looks towards +Z; a point behind the camera must be rejected <em>before</em> dividing, because the
 * perspective divide would mirror it onto the opposite side of the screen; and the aspect ratio must divide
 * the horizontal axis and not the vertical. Four implementations means four chances to disagree, and the
 * disagreement shows up as markers that are correct in one overlay and drift in another.
 *
 * <h2>Why it reads the camera's basis instead of recomputing it</h2>
 *
 * <p>The obvious implementation derives forward/right/up from yaw and pitch. That was what the three
 * earlier copies did, and it breaks the moment something else moves the camera — a death cam, a deploy
 * camera, the third-person orbit, or a shader mod that posts its own transform. {@link Camera} already
 * holds its basis as {@code getLookVector()}, {@code getUpVector()} and {@code getLeftVector()}, and those
 * are public, so this class asks the camera where it is pointing rather than assuming.
 *
 * <p>That is not a stylistic preference here. <b>The death camera in {@link Cinematic} takes over the
 * view</b> with a reflective override so it can travel instead of teleport; once that is active, the
 * camera's basis is no longer a function of the player's yaw and pitch, and any overlay that recomputed it
 * would silently be projecting against the wrong orientation for the whole sequence.
 *
 * <h2>The pure core is separated so it can be tested</h2>
 *
 * <p>{@link Camera} cannot be constructed in a headless test — it needs a level and an entity. So the
 * arithmetic lives in {@link #project}, which takes plain values, and the camera overload is a thin
 * adapter. {@code ProjectorSim} exercises the pure form against hand-computed expectations; without that
 * split, the one piece of geometry every world-space overlay depends on would be the only untested code in
 * the mod.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
final class Projector {
    /**
     * The vertical field of view actually used this frame, captured from {@link ViewportEvent.ComputeFov}.
     *
     * <p>{@code -1} means "not yet captured", in which case {@link #project} falls back to
     * {@code mc.options.fov()}. That fallback is a placeholder for the very first frames before the event
     * fires, not the normal path: the configured FOV is the base value, but sprinting, a TaCZ scope, a
     * vehicle optic and third-person all modify it per frame, and a marker projected against the base value
     * drifts off its anchor the moment any of those is active. The event fires before the GUI render pass
     * that consumes it, so by the time any overlay reads this it is this frame's true value.
     */
    private static double currentFovDegrees = -1.0;

    /**
     * A projected point.
     *
     * @param x      GUI pixels from the left
     * @param y      GUI pixels from the top
     * @param depth  blocks in front of the camera, for distance-scaling and sorting
     */
    record Screen(float x, float y, double depth) { }

    private Projector() { }

    /**
     * Records the FOV the world will be rendered with this frame.
     *
     * <p>Subscribed to {@link ViewportEvent.ComputeFov}, which NeoForge fires every frame while the camera's
     * FOV is computed — after sprint/scope/vehicle modifiers, before the render. Reading the same value here
     * that the renderer uses is what keeps a marker pinned to its world position while a player zooms.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void captureFov(ViewportEvent.ComputeFov event) {
        // The hand pass also fires this event with its own FOV after the world pass.
        if(event.usedConfiguredFov()) currentFovDegrees = event.getFOV();
    }

    /**
     * Projects a world position through the main camera.
     *
     * @param margin fraction of the screen a point may sit outside before being dropped; {@code 1.0} is the
     *               exact screen edge, {@code 1.3} keeps a marker just off screen so it does not pop as it
     *               crosses the border
     * @return the screen position, or {@code null} when the point should not be drawn
     */
    static Screen project(Minecraft mc, Camera camera, double x, double y, double z, float margin) {
        if (camera == null) return null;
        double fov = currentFovDegrees > 0 ? currentFovDegrees : mc.options.fov().get();
        return project(camera.getPosition(), camera.getLookVector(), camera.getLeftVector(),
                camera.getUpVector(), x, y, z,
                fov,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                margin);
    }

    /**
     * Projects a world position without dropping it for falling off-screen.
     *
     * <p>The deploy-area mask needs every vertex of the boundary polygon even when that vertex is outside
     * the window: the scanline fill clips its runs against the window itself, and a vertex dropped by the
     * margin check would silently amputate the polygon at the screen edge and dim land the player may
     * deploy on. Only the near-plane rejection remains, because a vertex behind the camera cannot be
     * clipped back into view — it must invalidate the whole polygon instead.
     */
    static Screen projectPoly(Minecraft mc, Camera camera, double x, double y, double z) {
        if (camera == null) return null;
        double fov = currentFovDegrees > 0 ? currentFovDegrees : mc.options.fov().get();
        return project(camera.getPosition(), camera.getLookVector(), camera.getLeftVector(),
                camera.getUpVector(), x, y, z,
                fov,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                Float.POSITIVE_INFINITY);
    }

    /**
     * The projection itself, over plain values.
     *
     * <p>Basis vectors follow vanilla's {@link Camera}: {@code forward} and {@code up} point the way the
     * names say, and {@code left} points to the viewer's left. Screen-right is therefore <em>minus</em>
     * left, which is the one sign in here worth double-checking: at yaw 0 the camera looks towards +Z, its
     * left is +X, and the viewer's right is -X. Getting that backwards mirrors every marker horizontally —
     * subtle enough to look almost right, and wrong on every single objective.
     *
     * @return {@code {screenX, screenY, depth}}, or {@code null} when behind the camera, past the near
     *         plane, or outside the margin
     */
    static Screen project(Vec3 eye, Vector3f forward, Vector3f left, Vector3f up,
                          double x, double y, double z,
                          double fovDegrees, int width, int height, float margin) {
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;

        // Depth along the camera's own forward axis: the divisor the perspective divide needs, and the
        // distance that governs apparent size. Not the straight-line distance to the eye — a marker at the
        // edge of a wide view is farther in a straight line than it is deep, and using that would make
        // markers shrink as they approach the screen edge.
        double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();
        // Reject at or behind the near plane. Dividing a negative depth by itself flips the sign of the
        // result, which is exactly the "marker jumps to the other side of the screen" failure.
        if (depth <= 0.05) return null;

        double right = -(dx * left.x() + dy * left.y() + dz * left.z());
        double above = dx * up.x() + dy * up.y() + dz * up.z();

        double tanHalf = Math.tan(Math.toRadians(fovDegrees) * 0.5);
        if (!(tanHalf > 0)) return null;
        // The vertical half-extent is tanHalf; the horizontal one is tanHalf * aspect. Swapping these yields
        // a projection that is correct only on a square window, which is why it is stated rather than assumed.
        double aspect = width / (double) Math.max(1, height);

        double ndcX = (right / depth) / (tanHalf * aspect);
        double ndcY = (above / depth) / tanHalf;
        if (ndcX < -margin || ndcX > margin || ndcY < -margin || ndcY > margin) return null;

        return new Screen(
                (float) ((ndcX * 0.5 + 0.5) * width),
                (float) ((0.5 - ndcY * 0.5) * height),
                depth);
    }

    /** The current camera, or {@code null} before a level exists. */
    static Camera camera(Minecraft mc) {
        return mc == null || mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
    }
}
