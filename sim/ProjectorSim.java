package cn.bfops;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Headless checks for the world-to-screen projection.
 *
 * <p>Every world-space overlay in the mod — capture markers, health bars, muzzle flashes, deploy markers —
 * goes through {@link Projector}. A wrong sign in it does not throw or log; it mirrors every marker
 * horizontally, or makes them shrink as they approach the screen edge, and the only way to notice is to
 * look at the game and think "that feels off". That is not a reliable test, so the arithmetic is asserted
 * here against hand-computed expectations instead.
 *
 * <p>The expectations are written from the geometry rather than captured from a run: at yaw 0 the camera
 * looks towards +Z with its left towards +X, so a point to the world's -X must land on the right half of
 * the screen. Stating that independently is what makes the check meaningful — a golden value copied from a
 * buggy implementation would just enshrine the bug.
 */
public final class ProjectorSim {
    private static int checks;

    /** A camera at the origin looking down +Z, which is yaw 0 in vanilla's convention. */
    private static final Vec3 EYE = new Vec3(0, 0, 0);
    private static final Vector3f FORWARD = new Vector3f(0, 0, 1);
    /** Vanilla's left at yaw 0 is +X, so screen-right is -X. */
    private static final Vector3f LEFT = new Vector3f(1, 0, 0);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    /** 90 degrees: tanHalf is exactly 1, so vertical offsets map linearly and the numbers are readable. */
    private static final double FOV = 90.0;
    /** Square window, so aspect is 1 and horizontal/vertical are comparable. */
    private static final int W = 100, H = 100;

    public static void main(String[] args) {
        straightAhead();
        horizontalSign();
        verticalSign();
        behindCamera();
        offScreen();
        aspectHandling();
        depthScaling();
        System.out.println("ProjectorSim: " + checks + " checks passed");
    }

    /** A point dead ahead lands at the exact centre, at a depth equal to its distance. */
    private static void straightAhead() {
        Projector.Screen at = project(0, 0, 10);
        check(at != null, "a point ahead projects");
        check(Math.abs(at.x() - 50) < 1e-4, "dead ahead is horizontally centred (x=" + at.x() + ")");
        check(Math.abs(at.y() - 50) < 1e-4, "dead ahead is vertically centred (y=" + at.y() + ")");
        check(Math.abs(at.depth() - 10) < 1e-9, "depth is the forward distance (got " + at.depth() + ")");
    }

    /**
     * The one sign in the projection worth checking twice.
     *
     * <p>At yaw 0 the camera looks towards +Z and its left is +X. A point at world -X is therefore to the
     * viewer's <em>right</em>, so it must land on the right half of the screen. If this inverts, every
     * marker and every health bar is mirrored, which reads as "the overlay is slightly wrong" rather than
     * as an obvious failure.
     */
    private static void horizontalSign() {
        Projector.Screen leftOfWorld = project(-5, 0, 10);
        check(leftOfWorld != null && leftOfWorld.x() > 50,
                "a point at -X appears right of centre (x=" + (leftOfWorld == null ? "null" : leftOfWorld.x()) + ")");

        Projector.Screen rightOfWorld = project(5, 0, 10);
        check(rightOfWorld != null && rightOfWorld.x() < 50,
                "a point at +X appears left of centre (x=" + (rightOfWorld == null ? "null" : rightOfWorld.x()) + ")");

        // Symmetry: equal and opposite offsets must be equidistant from the centre.
        check(Math.abs((leftOfWorld.x() - 50) + (rightOfWorld.x() - 50)) < 1e-4,
                "opposite offsets are symmetric about the centre");
    }

    /** A point above the camera must appear above the screen centre. */
    private static void verticalSign() {
        Projector.Screen above = project(0, 5, 10);
        check(above != null && above.y() < 50,
                "a point above appears above centre (y=" + (above == null ? "null" : above.y()) + ")");

        Projector.Screen below = project(0, -5, 10);
        check(below != null && below.y() > 50,
                "a point below appears below centre (y=" + (below == null ? "null" : below.y()) + ")");
    }

    /**
     * The failure this guards against is specific and ugly: dividing by a negative depth mirrors the point
     * onto the opposite side of the screen, so a marker behind you appears in front of you.
     */
    private static void behindCamera() {
        check(project(0, 0, -10) == null, "a point directly behind is rejected");
        check(project(0, 0, -0.001) == null, "a point just behind the near plane is rejected");
        check(project(0, 0, 0) == null, "a point at the camera is rejected");
        // Something behind and to the side, which is where mirroring is most visible.
        check(project(100, 0, -10) == null, "a point behind and off-axis is rejected, not mirrored");
    }

    private static void offScreen() {
        // 90 fov means the visible half-width at depth 10 is 10; +-11 is outside.
        check(project(11, 0, 10) == null, "a point outside the frustum is dropped at margin 1.0");
        check(project(9, 0, 10) != null, "a point inside the frustum is kept");
        // With a margin of 1.4 the same point is kept, which is what stops markers popping at the edge.
        Projector.Screen nearEdge = project(11, 0, 10, 1.4f);
        check(nearEdge != null, "a slightly off-screen point survives at margin 1.4");
    }

    /**
     * A wide window must extend the horizontal field, not stretch the vertical.
     *
     * <p>The bug this catches: applying the aspect ratio to the wrong axis yields a projection that looks
     * correct on a square window and is visibly wrong on every real one.
     */
    private static void aspectHandling() {
        Projector.Screen square = Projector.project(EYE, FORWARD, LEFT, UP, 5, 0, 10, FOV, 100, 100, 1.4f);
        Projector.Screen wide = Projector.project(EYE, FORWARD, LEFT, UP, 5, 0, 10, FOV, 200, 100, 1.4f);
        check(square != null && wide != null, "both window shapes project the same point");

        // Vertical FOV is fixed, so widening the window widens the *horizontal* field: the same point must
        // sit closer to the centre as a fraction of the screen. Doubling the width halves the normalised
        // horizontal offset, exactly — any other relationship means aspect was applied to the wrong axis.
        double squareOffset = (square.x() - 50) / 100.0;
        double wideOffset = (wide.x() - 100) / 200.0;
        // The point is at +X, which appears left of centre, so both offsets are negative — the sign has
        // already been checked in horizontalSign(), so all that matters here is that they agree and are
        // non-zero. Asserting a specific sign would duplicate that check and, as it turned out, get it wrong.
        check(squareOffset != 0 && Math.signum(wideOffset) == Math.signum(squareOffset),
                "both offsets share a sign and are non-zero (square=" + squareOffset
                        + ", wide=" + wideOffset + ")");
        check(Math.abs(wideOffset - squareOffset / 2) < 1e-4,
                "doubling the width halves the normalised horizontal offset ("
                        + squareOffset + " -> " + wideOffset + ")");
        // Magnitude, not signed value: the offsets are negative because the point sits left of centre, so a
        // raw "<" comparison would test the wrong thing and pass or fail depending only on which side of the
        // screen the sample point happens to be on.
        check(Math.abs(wideOffset) < Math.abs(squareOffset),
                "widening the window pulls the point toward the centre, not away from it (|"
                        + wideOffset + "| < |" + squareOffset + "|)");

        // Vertical must be untouched by the width.
        double squareY = square.y();
        double wideY = wide.y();
        check(Math.abs(squareY - wideY) < 1e-4,
                "widening the window does not move the point vertically ("
                        + squareY + " vs " + wideY + ")");
    }

    /**
     * Apparent size follows depth, not straight-line distance.
     *
     * <p>These differ at the edge of a wide view, and using the wrong one makes markers shrink as they
     * approach the screen border — a subtle "the overlay breathes" effect that is hard to attribute.
     */
    private static void depthScaling() {
        Projector.Screen centre = project(0, 0, 20);
        // At depth 20 with a 90 degree fov the visible half-width is exactly 20, so +-15 is inside the
        // frustum while still off-axis enough for the straight-line distance to differ measurably.
        Projector.Screen corner = project(15, 0, 20);
        check(centre != null && corner != null, "both in-frustum at a wide margin");
        check(Math.abs(centre.depth() - corner.depth()) < 1e-9,
                "depth ignores horizontal offset at equal forward distance ("
                        + centre.depth() + " vs " + corner.depth() + ")");
        // The straight-line distance is larger for the corner, which is exactly the difference that matters.
        check(Math.hypot(15, 20) > corner.depth(),
                "straight-line distance exceeds depth off-axis, so the two are not interchangeable");
    }

    // ------------------------------------------------------------------ helpers

    private static Projector.Screen project(double x, double y, double z) {
        return project(x, y, z, 1.0f);
    }

    private static Projector.Screen project(double x, double y, double z, float margin) {
        return Projector.project(EYE, FORWARD, LEFT, UP, x, y, z, FOV, W, H, margin);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }
}
