package cn.bfops;

/** Headless checks for the follow-heading rotation angle. */
public final class MapRotationSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        double north = MapRotation.radians(180f);
        double south = MapRotation.radians(0f);
        double west = MapRotation.radians(90f);
        double east = MapRotation.radians(270f);

        check(Math.abs(north) < 1e-9, "facing north (yaw 180) keeps the map north-up");
        check(Math.abs(Math.abs(south) - Math.PI) < 1e-9, "facing south (yaw 0) flips the map 180°");
        check(Math.abs(west - Math.PI / 2) < 1e-9, "facing west (yaw 90) rotates +90°");
        check(Math.abs(east + Math.PI / 2) < 1e-9 || Math.abs(east - 3 * Math.PI / 2) < 1e-9,
                "facing east (yaw 270) rotates -90°");

        // A full turn is the same rotation: the cosine and sine must match even if the raw angle wraps.
        double a0 = MapRotation.radians(180f), a1 = MapRotation.radians(180f + 360f);
        check(Math.abs(Math.cos(a0) - Math.cos(a1)) < 1e-9 && Math.abs(Math.sin(a0) - Math.sin(a1)) < 1e-9,
                "a full turn changes nothing");
        System.out.println("MapRotationSim: " + checks + " checks passed");
    }
}
