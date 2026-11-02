package cn.bfops;

/**
 * Follow-heading map rotation, as a pure angle.
 *
 * <p>The map stays north-up at 0°; "follow heading" rotates the whole map (terrain, markers, labels
 * together, in one pose transform) so the direction the player faces is at the top. The angle is derived
 * from Minecraft yaw: yaw 0 faces +Z (south), yaw 180 faces -Z (north), and the rotation is
 * {@code 180° - yaw} so a north-facing player gets 0° — north stays up — and a south-facing player gets
 * 180°, flipping the map to put south at the top.
 *
 * <p>Kept pure so {@code MapRotationSim} can pin the four cardinal directions; the actual screen-space
 * spin direction is a rendering detail of the pose transform and is verified in-game.
 */
final class MapRotation {
    private MapRotation() { }

    /** Rotation in radians for a given yaw in degrees. 0 facing north, ±π/2 facing east/west, ±π south. */
    static double radians(float yawDegrees) {
        return Math.toRadians(180.0 - yawDegrees);
    }
}
