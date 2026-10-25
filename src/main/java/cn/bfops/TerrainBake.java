package cn.bfops;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Bakes a {@link TerrainMap} for one arena, a few chunks at a time, and caches it on disk.
 *
 * <p><b>The budget is the whole point.</b> A map can cover thousands of blocks. Loading all of it in one
 * tick would stall the server for as long as worldgen takes, which on a fresh map is seconds. So the bake
 * walks a fixed number of <em>new</em> chunks per tick and resumes next tick. Nothing is kept loaded:
 * chunks are fetched through the normal server chunk source, which unloads them again on its own schedule,
 * so the map's chunks do not become a permanent memory cost the way forced chunk tickets would.
 *
 * <p><b>Each chunk is touched once.</b> Two pixels of the same chunk share the load. A per-bake set of
 * visited chunk keys is what guarantees that; without it a 128×128 sample over 200 blocks would call into
 * the chunk source thousands of times for the same few hundred chunks.
 *
 * <p><b>Progress is monotonic and resumable only through the disk cache.</b> If the server stops mid-bake
 * the work is simply lost and restarts later; there is no partial file to corrupt, because the cache is
 * written once, atomically, when the bake completes.
 */
final class TerrainBake {
    /** New chunks loaded per server tick. 24 keeps a tick well under a millisecond of chunk lookup. */
    static final int CHUNKS_PER_TICK = 24;
    /** Hard cap on chunks a single bake may touch, so a pathological boundary cannot loop forever. */
    static final int MAX_CHUNKS = 4096;

    private final Model.Arena arena;
    private final int w, h;
    private final double x0, z0, spanX, spanZ;
    private final String signature;

    private final int[] colors;
    private final int[] heights;
    /** Chunks already loaded by this bake, so each is fetched once. */
    private final Set<Long> visited = new HashSet<>();
    private int cursor;
    private int chunkCount;
    private boolean finished;
    private boolean abandoned;

    /**
     * @param arena  the map to bake; its boundary defines the sampled rectangle
     */
    TerrainBake(Model.Arena arena) {
        this.arena = arena;
        double[] box = bounds(arena);
        this.x0 = box[0];
        this.z0 = box[1];
        this.spanX = Math.max(1, box[2] - box[0]);
        this.spanZ = Math.max(1, box[3] - box[1]);
        int[] dims = TerrainMap.dimensions(spanX, spanZ);
        this.w = dims[0];
        this.h = dims[1];
        this.signature = TerrainMap.signature(arena, w, h);
        this.colors = new int[w * h];
        this.heights = new int[w * h];
    }

    /**
     * The world rectangle a bake covers: the boundary's bounding box.
     *
     * <p>Falls back to a small box around the origin for a map with no usable boundary, so the caller
     * cannot be handed a zero-sized rectangle that produces an empty map.
     */
    static double[] bounds(Model.Arena arena) {
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        if (arena != null && arena.boundary != null) {
            for (Model.Vec v : arena.boundary) {
                if (v == null) continue;
                minX = Math.min(minX, v.x());
                maxX = Math.max(maxX, v.x());
                minZ = Math.min(minZ, v.z());
                maxZ = Math.max(maxZ, v.z());
            }
        }
        if (minX > maxX || minZ > maxZ) {
            return new double[]{-64, -64, 64, 64};
        }
        // Half a block of slack so boundary vertices themselves fall inside the sampled rectangle
        // instead of being clipped by the strict "< 1" test in TerrainMap.sample.
        return new double[]{minX - 0.5, minZ - 0.5, maxX + 0.5, maxZ + 0.5};
    }

    boolean finished() {
        return finished;
    }

    boolean abandoned() {
        return abandoned;
    }

    String signature() {
        return signature;
    }

    /** 0..1, for the "preparing map" progress line. */
    double progress() {
        return w * h == 0 ? 1 : Math.min(1, cursor / (double) (w * h));
    }

    /**
     * Advances the bake by at most {@link #CHUNKS_PER_TICK} new chunks.
     *
     * @return true when the bake is complete and {@link #result()} is ready
     */
    boolean step(ServerLevel level) {
        if (finished) return true;
        if (abandoned) return false;
        if (level == null || arena == null) {
            abandoned = true;
            return false;
        }
        int newChunks = 0;
        while (cursor < w * h) {
            int sx = cursor % w;
            int sz = cursor / w;
            double worldX = x0 + (sx + 0.5) / w * spanX;
            double worldZ = z0 + (sz + 0.5) / h * spanZ;
            int bx = (int) Math.floor(worldX);
            int bz = (int) Math.floor(worldZ);

            long key = (((long) (bx >> 4)) << 32) ^ ((bz >> 4) & 0xffffffffL);
            if (!visited.contains(key)) {
                if (chunkCount >= MAX_CHUNKS) {
                    // Too large to bake within the cap. Abandon rather than half-draw: a map that looks
                    // complete but is missing a region is worse than one that clearly has no overview.
                    abandoned = true;
                    return false;
                }
                if (newChunks >= CHUNKS_PER_TICK) {
                    // Budget for this tick is spent. The pixel is deliberately not consumed, so the next
                    // tick resumes exactly here instead of skipping it.
                    return false;
                }
                visited.add(key);
                chunkCount++;
                newChunks++;
            }

            colors[cursor] = sample(level, bx, bz, sx, sz, cursor);
            cursor++;
        }
        finished = true;
        return true;
    }

    /** Height and surface colour for one sample, with the same slope shading the live map uses. */
    private int sample(ServerLevel level, int bx, int bz, int sx, int sz, int index) {
        try {
            return sampleUnsafe(level, bx, bz, sx, sz, index);
        } catch (RuntimeException ex) {
            // One unreadable position must not abandon an otherwise fine map. The pixel simply has no data.
            BattlefieldOps.LOG.debug("Cannot sample terrain at " + bx + "," + bz, ex);
            return 0;
        }
    }

    private int sampleUnsafe(ServerLevel level, int bx, int bz, int sx, int sz, int index) {
        // Deliberately the *loading* form. Reading with requireChunk=false — which is what the client
        // must do, and what caused the original "half the map is missing" symptom — returns null for every
        // chunk outside the view distance and would bake a hole into the map. Loading here is the whole
        // point of the bake, and it is exactly what the per-tick chunk budget exists to pay for.
        var chunk = level.getChunk(bx >> 4, bz >> 4);
        if (chunk == null) return 0;
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
        if (y <= arena.minY || y > arena.maxY + 1) return 0;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, Math.min(y, level.getMaxBuildHeight() - 1), bz);
        BlockState state = chunk.getBlockState(pos);
        int rgb = state.getMapColor(level, pos).col;
        int previous = sx > 0 ? heights[index - 1] : y;
        heights[index] = y;
        double slope = (y - previous) / Math.max(1, spanX / w);
        double light = Math.max(.6, Math.min(1.2, .94 + slope * .08));
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * light));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * light));
        int b = Math.min(255, (int) ((rgb & 255) * light));
        // Same packing the terrain texture expects (ABGR in the low bytes), so the client needs no
        // per-pixel conversion when it falls back to this layer.
        return 0xff000000 | b << 16 | g << 8 | r;
    }

    TerrainMap result() {
        return new TerrainMap(arena.id, signature, x0, z0, spanX, spanZ, w, h, colors);
    }

    // ---------------------------------------------------------------- disk cache

    private static Path cacheFile(Path folder, String id) {
        return folder.resolve("terrain").resolve(id + ".terrain");
    }

    /**
     * Reads a cached bake, or {@code null} when there is none or it does not match.
     *
     * <p>Any problem — missing file, older format, different signature — is treated as "no cache" rather
     * than an error. A corrupt cache must never stop a map from opening; the worst case is re-baking.
     */
    static TerrainMap load(Path folder, String id, String signature) {
        Path file = cacheFile(folder, id);
        if (!Files.isRegularFile(file)) return null;
        try {
            String[] lines = Files.readString(file).split("\n", 3);
            if (lines.length < 3) return null;
            if (!lines[0].trim().equals(signature)) return null;
            return TerrainMap.decode(id, signature, lines[2].trim());
        } catch (IOException | RuntimeException ex) {
            BattlefieldOps.LOG.debug("Ignoring unreadable terrain cache for " + id, ex);
            return null;
        }
    }

    /**
     * Writes a bake, replacing any previous one.
     *
     * <p>Written to a temporary file and moved into place: a server stopping mid-write must not leave a
     * half-file that a later {@link #load} would have to defend against.
     */
    static void store(Path folder, TerrainMap map) {
        if (map == null || !map.valid()) return;
        try {
            Path directory = folder.resolve("terrain");
            Files.createDirectories(directory);
            Path temp = directory.resolve(map.id + ".terrain.tmp");
            Path dest = directory.resolve(map.id + ".terrain");
            Files.writeString(temp, map.signature + "\n" + map.id + "\n" + map.encode());
            try {
                Files.move(temp, dest, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                Files.move(temp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ex) {
            // A failed cache write costs a re-bake next time, nothing more. Encoding is included here
            // because it can fail too, and it runs on the server tick.
            BattlefieldOps.LOG.warn("Cannot cache terrain for " + map.id, ex);
        }
    }

    /** Removes a cached bake, used when the operator changes the map. */
    static void invalidate(Path folder, String id) {
        try {
            Files.deleteIfExists(cacheFile(folder, id));
            Files.deleteIfExists(folder.resolve("terrain").resolve(id + ".terrain.tmp"));
        } catch (IOException ex) {
            BattlefieldOps.LOG.debug("Cannot invalidate terrain cache for " + id, ex);
        }
    }
}
