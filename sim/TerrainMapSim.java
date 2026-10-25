package cn.bfops;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Checks the baked-terrain plumbing, including the payload budget.
 *
 * <p>The payload check is the important one here. A 128×128 overview is 64&nbsp;KiB of raw ints; if the
 * compressed form did not fit the packet limit the feature would work perfectly in a unit test and fail
 * the first time a real player opened the map, with the server throwing on an oversized response.
 */
public final class TerrainMapSim {
    private static int checks;

    public static void main(String[] args) {
        dimensions();
        sampling();
        encoding();
        payloadBudget();
        signature();
        bounds();
        System.out.println("TerrainMapSim: " + checks + " checks passed");
    }

    /** Sample dimensions keep the map's aspect ratio and stay inside the pixel budget. */
    private static void dimensions() {
        int[] square = TerrainMap.dimensions(200, 200);
        check(Math.abs(square[0] - square[1]) <= 1, "a square map samples square");
        check((long) square[0] * square[1] <= TerrainMap.MAX_PIXELS, "a square map stays in budget");

        // A wide strip must not be squashed into a square: that is the distortion nobody notices until
        // the map looks subtly wrong.
        int[] wide = TerrainMap.dimensions(2000, 100);
        check(wide[0] > wide[1] * 4, "a wide map samples wide (got " + wide[0] + "x" + wide[1] + ")");
        check((long) wide[0] * wide[1] <= TerrainMap.MAX_PIXELS, "a wide map stays in budget");

        int[] tall = TerrainMap.dimensions(100, 2000);
        check(tall[1] > tall[0] * 4, "a tall map samples tall (got " + tall[0] + "x" + tall[1] + ")");
        check((long) tall[0] * tall[1] <= TerrainMap.MAX_PIXELS, "a tall map stays in budget");

        // Degenerate input must not produce a zero-sized or oversized texture.
        int[] tiny = TerrainMap.dimensions(0, 0);
        check(tiny[0] >= TerrainMap.MIN_AXIS && tiny[1] >= TerrainMap.MIN_AXIS,
                "a zero-area map still samples a minimum size");
        int[] huge = TerrainMap.dimensions(1_000_000, 1_000_000);
        check((long) huge[0] * huge[1] <= TerrainMap.MAX_PIXELS, "a huge map is still capped");
    }

    /** Sampling maps world coordinates onto the right pixel and refuses everything outside. */
    private static void sampling() {
        int w = 4, h = 4;
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xff000000 | i;
        TerrainMap map = new TerrainMap("m", "sig", 0, 0, 40, 40, w, h, argb);

        check(map.valid(), "a 4x4 map with 16 colours is valid");
        check(map.sample(5, 5) == (0xff000000 | 0), "the first pixel covers the first quarter");
        check(map.sample(35, 35) == (0xff000000 | 15), "the last pixel covers the last quarter");
        check(map.sample(15, 5) == (0xff000000 | 1), "the second column resolves");
        check(map.sample(-1, 5) == 0, "left of the rectangle has no data");
        check(map.sample(40, 5) == 0, "the right edge is exclusive");
        check(map.sample(5, -0.001) == 0, "above the rectangle has no data");
        check(map.sample(1_000_000, 1_000_000) == 0, "far outside has no data");

        // A zero colour means "no data" and must survive the lookup, not be mistaken for a colour.
        TerrainMap holes = new TerrainMap("m", "sig", 0, 0, 8, 8, 2, 2, new int[]{0, 0, 0, 0});
        check(holes.sample(2, 2) == 0, "an unsampled pixel reads as no data");

        // A malformed map must not throw from a render path.
        TerrainMap broken = new TerrainMap("m", "sig", 0, 0, 8, 8, 2, 2, new int[3]);
        check(!broken.valid(), "a short colour array is invalid");
        check(broken.sample(2, 2) == 0, "an invalid map samples as no data");
    }

    /** The wire form round-trips exactly, and rejects corruption instead of half-decoding. */
    private static void encoding() {
        int w = 32, h = 24;
        int[] argb = new int[w * h];
        Random random = new Random(1234);
        for (int i = 0; i < argb.length; i++) argb[i] = 0xff000000 | random.nextInt(0xffffff);
        TerrainMap original = new TerrainMap("m", "abc", -100, -50, 400, 300, w, h, argb);

        String encoded = original.encode();
        TerrainMap decoded = TerrainMap.decode("m", "abc", encoded);
        check(decoded != null, "an encoded map decodes");
        check(decoded.valid(), "the decoded map is valid");
        check(decoded.w == w && decoded.h == h, "dimensions survive the round trip");
        check(decoded.x0 == -100 && decoded.z0 == -50, "the origin survives");
        check(decoded.spanX == 400 && decoded.spanZ == 300, "the span survives");
        for (int i = 0; i < argb.length; i++) {
            check(decoded.argb[i] == argb[i], "colour " + i + " survives");
        }

        // Every malformed input must produce null, not an exception and not a partial map.
        check(TerrainMap.decode("m", "abc", "") == null, "empty data decodes to null");
        check(TerrainMap.decode("m", "abc", null) == null, "null data decodes to null");
        check(TerrainMap.decode("m", "abc", "not-base64!!!") == null, "garbage decodes to null");
        check(TerrainMap.decode("m", "abc", "AAAA") == null, "truncated data decodes to null");
    }

    /**
     * The compressed payload must fit comfortably inside the state packet.
     *
     * <p>Tested at the worst case the feature can produce (full 128×128) with two kinds of content:
     * realistic smooth terrain, which compresses very well, and pure noise, which barely compresses at
     * all. If even noise fits, no real map can overflow the packet.
     */
    private static void payloadBudget() {
        int[] dims = TerrainMap.dimensions(2000, 2000);
        int w = dims[0], h = dims[1];
        check((long) w * h <= TerrainMap.MAX_PIXELS, "the worst case is still within the pixel budget");

        // Smooth terrain: neighbouring pixels differ slightly, like real ground colours.
        int[] smooth = new int[w * h];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int shade = 40 + (x * 60 / Math.max(1, w)) + (z * 30 / Math.max(1, h));
                smooth[z * w + x] = 0xff000000 | (shade << 16) | (shade << 8) | shade;
            }
        }
        int smoothLength = new TerrainMap("m", "sig", 0, 0, 2000, 2000, w, h, smooth).encode().length();

        // Noise: the pathological case for a compressor.
        int[] noise = new int[w * h];
        Random random = new Random(99);
        for (int i = 0; i < noise.length; i++) noise[i] = 0xff000000 | random.nextInt(0xffffff);
        int noiseLength = new TerrainMap("m", "sig", 0, 0, 2000, 2000, w, h, noise).encode().length();

        // Network.State allows 200000 characters and send() throws above 190000.
        check(smoothLength < 190000, "smooth terrain fits the packet (" + smoothLength + " chars)");
        check(noiseLength < 190000, "even pure noise fits the packet (" + noiseLength + " chars)");
        // Realistic terrain should be far smaller; this is the case that actually ships.
        check(smoothLength < 40000, "real terrain stays small (" + smoothLength + " chars)");
        // Printed so the real numbers are visible rather than only asserted: these are what determine
        // whether the feature is cheap enough to ship.
        System.out.println("  payload " + w + "x" + h + ": terrain " + smoothLength
                + " chars, noise " + noiseLength + " chars (limit 190000)");

        // And the encoded form must still decode at that size.
        TerrainMap roundTrip = TerrainMap.decode("m", "sig",
                new TerrainMap("m", "sig", 0, 0, 2000, 2000, w, h, smooth).encode());
        check(roundTrip != null && roundTrip.w == w && roundTrip.h == h,
                "a full-size map survives the round trip");
    }

    /** The signature must change whenever the baked content would change, and not otherwise. */
    private static void signature() {
        Model.Arena arena = arena(0, 0, 100, 100);
        int[] dims = TerrainMap.dimensions(100, 100);
        String base = TerrainMap.signature(arena, dims[0], dims[1]);

        check(base.equals(TerrainMap.signature(arena, dims[0], dims[1])),
                "the same map produces the same signature");

        Model.Arena moved = arena(0, 0, 100, 100);
        moved.boundary.set(0, new Model.Vec(5, moved.boundary.getFirst().z()));
        check(!base.equals(TerrainMap.signature(moved, dims[0], dims[1])),
                "moving a boundary vertex invalidates the bake");

        Model.Arena taller = arena(0, 0, 100, 100);
        taller.maxY = 400;
        check(!base.equals(TerrainMap.signature(taller, dims[0], dims[1])),
                "changing the height window invalidates the bake");

        Model.Arena other = arena(0, 0, 100, 100);
        other.dimension = "minecraft:the_nether";
        check(!base.equals(TerrainMap.signature(other, dims[0], dims[1])),
                "changing the dimension invalidates the bake");

        check(!base.equals(TerrainMap.signature(arena, dims[0] + 1, dims[1])),
                "changing the resolution invalidates the bake");

        // A map with no boundary must still produce a stable signature rather than throwing.
        Model.Arena empty = new Model.Arena();
        empty.boundary = new ArrayList<>();
        check(TerrainMap.signature(empty, 16, 16).length() > 0, "a boundless map still has a signature");
    }

    /** The baked rectangle must cover the boundary, with slack so vertices are not clipped. */
    private static void bounds() {
        Model.Arena arena = arena(10, 20, 110, 220);
        double[] box = TerrainBake.bounds(arena);
        check(box[0] <= 10 && box[1] <= 20, "the box starts at or before the minimum corner");
        check(box[2] >= 110 && box[3] >= 220, "the box ends at or after the maximum corner");
        // Half a block of slack, so a vertex exactly on the edge is inside the sampled rectangle.
        check(box[0] < 10 && box[2] > 110, "the box has slack beyond the vertices");

        // The sampled rectangle must actually contain every boundary vertex.
        int[] dims = TerrainMap.dimensions(box[2] - box[0], box[3] - box[1]);
        TerrainMap map = new TerrainMap("m", "sig", box[0], box[1],
                box[2] - box[0], box[3] - box[1], dims[0], dims[1], new int[dims[0] * dims[1]]);
        for (Model.Vec v : arena.boundary) {
            double fx = (v.x() - map.x0) / map.spanX;
            double fz = (v.z() - map.z0) / map.spanZ;
            check(fx >= 0 && fx < 1 && fz >= 0 && fz < 1,
                    "vertex (" + v.x() + "," + v.z() + ") is inside the baked rectangle");
        }

        // No usable boundary: a small box around the origin, never an inverted or empty rectangle.
        Model.Arena none = new Model.Arena();
        none.boundary = new ArrayList<>();
        double[] fallback = TerrainBake.bounds(none);
        check(fallback[2] > fallback[0] && fallback[3] > fallback[1],
                "a boundless map falls back to a usable rectangle");

        Model.Arena single = new Model.Arena();
        single.boundary.add(new Model.Vec(5, 5));
        double[] one = TerrainBake.bounds(single);
        check(one[2] > one[0] && one[3] > one[1],
                "a single-vertex boundary still yields a non-degenerate rectangle");
    }

    private static Model.Arena arena(double minX, double minZ, double maxX, double maxZ) {
        Model.Arena arena = new Model.Arena();
        arena.id = "m";
        arena.boundary = new ArrayList<>(List.of(
                new Model.Vec(minX, minZ),
                new Model.Vec(maxX, minZ),
                new Model.Vec(maxX, maxZ),
                new Model.Vec(minX, maxZ)));
        return arena;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
