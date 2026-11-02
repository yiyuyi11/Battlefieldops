package cn.bfops;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * The one reflective write in the mod: setting a camera's position.
 *
 * <h2>Why this is a separate class</h2>
 *
 * <p>Vanilla's {@link Camera} exposes {@code getPosition()} publicly and keeps the setter {@code protected}.
 * There is no supported way for another mod to move the camera, so this has to reach past the access
 * modifier — and everything about that belongs in one small, auditable place rather than being spread
 * through the code that decides where the camera should go.
 *
 * <p>{@link Cinematic} decides the pose. This class only writes it. The split means the phase machine, the
 * easing and the timings are ordinary Java that {@code CinematicSim} can run headlessly, while the one piece
 * that needs a running game is eight lines long and does nothing but call a method.
 *
 * <h2>Resolution happens once, and failure is permanent and quiet</h2>
 *
 * <p>The setter is looked up on first use and cached, including a cached <em>failure</em>. Retrying a
 * reflective lookup every frame would mean sixty exceptions a second in the log for the rest of the session
 * — and the caller has nothing better to do on failure than fall back to a vanilla camera, which is what it
 * does anyway.
 *
 * <p>Both the {@code (Vec3)} and {@code (double,double,double)} overloads are tried, and both the Mojang and
 * SRG names, because which one exists depends on the mapping set the runtime was built with. Nothing else is
 * attempted: a field-level fallback would be guessing at a layout that has no reason to be stable.
 */
final class CameraAccess {
    private static boolean resolved;
    private static boolean usable;
    private static Method setter;
    private static boolean takesVec3;

    private CameraAccess() { }

    /** True when the camera can be moved at all. */
    static boolean usable() {
        resolve();
        return usable;
    }

    /**
     * Writes a camera position.
     *
     * @return true when the write happened; false when the camera cannot be moved or the write failed
     */
    static boolean move(Camera camera, Vec3 position) {
        resolve();
        if (!usable || camera == null || position == null) return false;
        try {
            if (takesVec3) {
                setter.invoke(camera, position);
            } else {
                setter.invoke(camera, position.x, position.y, position.z);
            }
            return true;
        } catch (Throwable ex) {
            // One failure means every later call will fail the same way. Retire the handle rather than
            // flooding the log, and let the caller fall back to a vanilla camera.
            usable = false;
            BattlefieldOps.LOG.warn("Camera position write failed; cinematics disabled for this session", ex);
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        for (String name : new String[] { "setPosition", "m_90584_" }) {
            try {
                setter = Camera.class.getDeclaredMethod(name, Vec3.class);
                takesVec3 = true;
            } catch (NoSuchMethodException first) {
                try {
                    setter = Camera.class.getDeclaredMethod(name, double.class, double.class, double.class);
                    takesVec3 = false;
                } catch (NoSuchMethodException second) {
                    continue;
                }
            }
            try {
                setter.setAccessible(true);
                usable = true;
                return;
            } catch (Throwable ex) {
                BattlefieldOps.LOG.warn("Camera setter found but not accessible; cinematics disabled", ex);
                usable = false;
                return;
            }
        }
        BattlefieldOps.LOG.warn("No camera position setter found; cinematics disabled "
                + "(the rest of the mod is unaffected)");
        usable = false;
    }
}
