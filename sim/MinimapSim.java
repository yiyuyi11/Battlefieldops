package cn.bfops;

/**
 * Geometry-only checks for the minimap projection. Lives in {@code cn.bfops} because {@link Minimap}
 * is package-private; it never touches the renderer, so it runs headless.
 */
public final class MinimapSim {
    private static int failures = 0;

    public static void main(String[] args) {
        Model.Arena arena = new Model.Arena();
        arena.boundary.add(new Model.Vec(-100, -50));
        arena.boundary.add(new Model.Vec(300, -50));
        arena.boundary.add(new Model.Vec(300, 150));
        arena.boundary.add(new Model.Vec(-100, 150));

        Minimap map = Minimap.fit(arena, 400, 8, 120, 120, 0, 0);
        check("centre X", Math.abs(map.cx - 100) < 1e-9, "cx=" + map.cx);
        check("centre Z", Math.abs(map.cz - 50) < 1e-9, "cz=" + map.cz);
        check("span covers boundary", map.span >= 400, "span=" + map.span);

        for (Model.Vec vertex : arena.boundary) {
            int sx = map.sx(vertex.x()), sy = map.sy(vertex.z());
            check("vertex inside panel " + vertex.x() + "," + vertex.z(),
                    sx >= 400 && sx <= 520 && sy >= 8 && sy <= 128, "-> " + sx + "," + sy);
        }

        check("centre maps to panel centre",
                Math.abs(map.sx(100) - 460) <= 1 && Math.abs(map.sy(50) - 68) <= 1,
                map.sx(100) + "," + map.sy(50));

        for (double worldX : new double[]{-100, 0, 100, 250}) {
            double back = map.worldX(map.sx(worldX));
            check("round trip X " + worldX, Math.abs(back - worldX) < 1.0, "back=" + back);
        }

        int dx = map.sx(200) - map.sx(100);
        int dy = map.sy(150) - map.sy(50);
        check("square aspect", Math.abs(dx - dy) <= 1, "dx=" + dx + " dy=" + dy);

        Minimap empty = Minimap.fit(new Model.Arena(), 0, 0, 100, 100, 12, 34);
        check("empty arena fallback", empty.span == 256 && empty.cx == 12 && empty.cz == 34,
                "span=" + empty.span + " cx=" + empty.cx);

        check("colour blue", Minimap.color(0) == 0x77bbff, Integer.toHexString(Minimap.color(0)));
        check("colour red", Minimap.color(1) == 0xff7777, Integer.toHexString(Minimap.color(1)));
        check("colour neutral", Minimap.color(-1) == 0xcccccc, Integer.toHexString(Minimap.color(-1)));
        check("holder blue", Minimap.holder(0).equals("蓝"), Minimap.holder(0));

        System.out.println(failures == 0 ? "ALL MINIMAP TESTS PASSED" : failures + " MINIMAP TESTS FAILED");
        corners();
        preferences();

        if (failures != 0) System.exit(1);
    }


    /**
     * The minimap corner setting.
     *
     * <p>Checked headlessly because the failure modes are invisible from the code: a minimap half off the
     * screen, or one drawn straight over the ticket bar. Both only show up for a particular window size.
     */
    private static void corners() {
        // Default is the top-left, which is what the operator asked for.
        check("default corner", Briefing.normaliseCorner(null).equals("TOP_LEFT"), "null defaults to TOP_LEFT");
        check("blank corner", Briefing.normaliseCorner("  ").equals("TOP_LEFT"), "blank defaults to TOP_LEFT");
        check("unknown corner", Briefing.normaliseCorner("MIDDLE").equals("TOP_LEFT"),
                "an invented corner falls back to TOP_LEFT");
        check("lower case accepted", Briefing.normaliseCorner("bottom_right").equals("BOTTOM_RIGHT"),
                "corner names are case-insensitive");

        // Every corner stays fully on screen at a range of window sizes, both for the default minimap
        // size and for the largest the HUD will draw.
        for (int[] size : new int[][]{{320, 240}, {640, 480}, {1280, 720}, {1920, 1080}, {3840, 2160}}) {
            for (int mapSize : new int[]{64, 116, 120}) {
                int w = size[0], h = size[1];
                for (String corner : Briefing.MINIMAP_CORNERS) {
                    int[] at = Briefing.minimapOrigin(corner, w, h, mapSize);
                    String at2 = corner + " size=" + mapSize + " window=" + w + "x" + h;
                    check(at2 + " starts on screen", at[0] >= 0 && at[1] >= 0,
                            at[0] + "," + at[1]);
                    check(at2 + " fits horizontally", at[0] + mapSize <= w,
                            "x=" + at[0] + " + " + mapSize + " > " + w);
                    check(at2 + " fits vertically", at[1] + mapSize <= h,
                            "y=" + at[1] + " + " + mapSize + " > " + h);
                }
            }
        }

        // The four corners must actually be four different positions.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String corner : Briefing.MINIMAP_CORNERS) {
            int[] at = Briefing.minimapOrigin(corner, 1280, 720, 116);
            seen.add(at[0] + "," + at[1]);
        }
        check("corners are distinct", seen.size() == 4, "distinct positions=" + seen.size());

        // Left corners hug the left edge, right corners hug the right.
        int[] topLeft = Briefing.minimapOrigin("TOP_LEFT", 1280, 720, 116);
        int[] bottomLeft = Briefing.minimapOrigin("BOTTOM_LEFT", 1280, 720, 116);
        int[] topRight = Briefing.minimapOrigin("TOP_RIGHT", 1280, 720, 116);
        int[] bottomRight = Briefing.minimapOrigin("BOTTOM_RIGHT", 1280, 720, 116);
        check("left corners share x", topLeft[0] == bottomLeft[0], "x differs");
        check("right corners share x", topRight[0] == bottomRight[0], "x differs");
        check("left is left of right", topLeft[0] < topRight[0], "left=" + topLeft[0] + " right=" + topRight[0]);
        check("top corners share y", topLeft[1] == topRight[1], "y differs");
        check("bottom corners share y", bottomLeft[1] == bottomRight[1], "y differs");
        check("top is above bottom", topLeft[1] < bottomLeft[1], "top=" + topLeft[1] + " bottom=" + bottomLeft[1]);

        // A top corner must clear the ticket bar, which is drawn across the top centre.
        check("top corner clears the ticket bar", topLeft[1] >= Briefing.MINIMAP_TOP_INSET,
                "y=" + topLeft[1]);

        // The cycle visits every corner exactly once.
        String corner = "TOP_LEFT";
        java.util.List<String> visited = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            visited.add(corner);
            corner = Briefing.nextCorner(corner);
        }
        check("the cycle returns to the start", corner.equals("TOP_LEFT"), "ended at " + corner);
        check("the cycle visits all four", new java.util.HashSet<>(visited).size() == 4,
                "visited=" + visited);

        // Labels exist for every corner, so the settings row never shows a raw enum name.
        for (String each : Briefing.MINIMAP_CORNERS) {
            check("label for " + each, !Briefing.minimapCornerName(each).equals(each),
                    "raw name shown");
        }
    }


    /**
     * Client preference resolution: the player's own corner wins over the operator's default.
     *
     * <p>Checked here rather than through {@link ClientPrefs} because that class touches the config
     * directory; the rule being asserted is the same one it implements — empty means "follow the server",
     * anything else means "mine".
     */
    private static void preferences() {
            // empty preference -> server value wins
            check("empty follows server", resolve("", "BOTTOM_RIGHT").equals("BOTTOM_RIGHT"),
                    "empty preference should follow the server");
            check("blank follows server", resolve("   ", "TOP_LEFT").equals("TOP_LEFT"),
                    "blank preference should follow the server");
            // an explicit choice wins over the server
            check("mine wins", resolve("TOP_RIGHT", "BOTTOM_LEFT").equals("TOP_RIGHT"),
                    "an explicit choice should win");
            // unknown values on either side fall back to the default rather than drawing off-screen
            check("bad mine falls back", resolve("MIDDLE", "BOTTOM_LEFT").equals("BOTTOM_LEFT"),
                    "an unknown client value should follow the server");
            check("bad server falls back", resolve("", "NONSENSE").equals("TOP_LEFT"),
                    "an unknown server value should fall back to the default");
            check("both bad falls back", resolve("X", "Y").equals("TOP_LEFT"),
                    "two unknown values should fall back to the default");
            // case and whitespace are tolerated on both sides
            check("lower case mine", resolve("bottom_right", "TOP_LEFT").equals("BOTTOM_RIGHT"),
                    "client values are case-insensitive");

        // Cycling from a server default must move to the *next* corner, not jump somewhere unrelated.
        String start = resolve("", "TOP_LEFT");
        check("cycle starts next", Briefing.nextCorner(start).equals("TOP_RIGHT"),
                "the first cycle press moves one step from the server default");
        // And cycling then resolving keeps the player's choice, even if the server changes.
        String chosen = Briefing.nextCorner(start);
        check("choice sticks", resolve(chosen, "BOTTOM_RIGHT").equals(chosen),
                "once chosen, a server change no longer moves the minimap");
    }

    /** The same precedence rule ClientPrefs applies. */
    private static String resolve(String mine, String server) {
        // Exactly what ClientPrefs.corner does: only a real corner is a choice.
        if (Briefing.isCorner(mine)) return Briefing.normaliseCorner(mine);
        return Briefing.normaliseCorner(server);
    }

    private static void check(String label, boolean ok, String detail) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            failures++;
            System.out.println("  FAIL  " + label + " -> " + detail);
        }
    }
}
