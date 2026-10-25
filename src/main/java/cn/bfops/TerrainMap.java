package cn.bfops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A baked overview of a map's surface: one colour per sampled block, covering the whole playable area.
 *
 * <p><b>Why this exists.</b> The map widgets read terrain from the client's loaded chunks. A client only
 * holds the chunks its view distance covers, so anything beyond that simply was not there to draw and the
 * canvas showed its "unknown" checkerboard — the reported "areas the player is not in do not display".
 * The client cannot fix this by asking: the vanilla client chunk source returns either nothing or an
 * empty placeholder, and never fetches. Only the server can produce the data.
 *
 * <p><b>Why a bake rather than forcing chunks to stay loaded.</b> Keeping an entire map's chunks resident
 * would cost memory and entity ticking for the whole match, on every map, permanently. Baking instead
 * costs one bounded pass, is written to disk once, and is then free forever: the colours are static
 * terrain, and a tactical overview does not need to react to a player placing a block.
 *
 * <p>Colours are stored in the same packed layout the terrain texture uses (ABGR in the low three bytes),
 * so the client blits them without a conversion step.
 */
final class TerrainMap {
    /** Upper bound on total samples. 128×128 of overview is plenty and keeps the payload small. */
    static final int MAX_PIXELS = 128 * 128;
    /** Never fewer than this per axis, even for a tiny map. */
    static final int MIN_AXIS = 16;

    final String id;
    final String signature;
    /** World-space lower corner of the sampled rectangle. */
    final double x0, z0;
    /** World-space size of the sampled rectangle, in blocks. */
    final double spanX, spanZ;
    final int w, h;
    /** {@code w*h} packed colours; 0 means "no data" (outside the world, or unreadable). */
    final int[] argb;

    TerrainMap(String id, String signature, double x0, double z0, double spanX, double spanZ,
               int w, int h, int[] argb) {
        this.id = id == null ? "" : id;
        this.signature = signature == null ? "" : signature;
        this.x0 = x0;
        this.z0 = z0;
        this.spanX = spanX;
        this.spanZ = spanZ;
        this.w = Math.max(1, w);
        this.h = Math.max(1, h);
        this.argb = argb == null ? new int[0] : argb;
    }

    boolean valid() {
        return w > 0 && h > 0 && argb.length == w * h;
    }

    /**
     * The colour at a world position, or 0 when the position is outside the baked rectangle.
     *
     * <p>Returning 0 rather than a guessed colour is what lets the caller distinguish "we have data and it
     * is this" from "we have nothing here", which is the difference between drawing terrain and drawing
     * the placeholder pattern.
     */
    int sample(double worldX, double worldZ) {
        if (!valid() || spanX <= 0 || spanZ <= 0) return 0;
        double fx = (worldX - x0) / spanX;
        double fz = (worldZ - z0) / spanZ;
        if (fx < 0 || fx >= 1 || fz < 0 || fz >= 1) return 0;
        int px = (int) (fx * w);
        int pz = (int) (fz * h);
        if (px < 0) px = 0;
        if (pz < 0) pz = 0;
        if (px >= w) px = w - 1;
        if (pz >= h) pz = h - 1;
        return argb[pz * w + px];
    }

    /**
     * Chooses sample dimensions for a world rectangle, preserving aspect within {@link #MAX_PIXELS}.
     *
     * <p>Kept here rather than in the bake so the arithmetic can be checked without a running server: the
     * failure mode is a silently distorted map (a 2000×200 strip squashed into a square), which is exactly
     * the kind of thing that looks "almost right" and is hard to notice.
     */
    static int[] dimensions(double spanX, double spanZ) {
        double sx = Math.max(1, spanX);
        double sz = Math.max(1, spanZ);
        double ratio = sx / sz;
        int w, h;
        if (ratio >= 1) {
            w = (int) Math.round(Math.sqrt(MAX_PIXELS * ratio));
            h = (int) Math.round(MAX_PIXELS / (double) Math.max(1, w));
        } else {
            h = (int) Math.round(Math.sqrt(MAX_PIXELS / ratio));
            w = (int) Math.round(MAX_PIXELS / (double) Math.max(1, h));
        }
        w = Math.max(MIN_AXIS, Math.min((int) Math.sqrt(MAX_PIXELS * 4), Math.max(1, w)));
        h = Math.max(MIN_AXIS, Math.min((int) Math.sqrt(MAX_PIXELS * 4), Math.max(1, h)));
        // Clamp the product, shrinking the longer axis, so the payload never exceeds the sample budget.
        while ((long) w * h > MAX_PIXELS) {
            if (w >= h) w--; else h--;
        }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    /**
     * Signature of the terrain a map would bake.
     *
     * <p>Includes the boundary, the height window, the dimension and the sample dimensions. Changing any
     * of them must invalidate the cached bake, or an operator who redraws their map boundary keeps seeing
     * the old map's shape forever — a cache that is wrong is worse than no cache.
     */
    static String signature(Model.Arena arena, int w, int h) {
        StringBuilder text = new StringBuilder();
        text.append(arena.id).append('|').append(arena.dimension).append('|')
                .append(arena.minY).append(':').append(arena.maxY).append('|')
                .append(w).append('x').append(h).append('|');
        if (arena.boundary != null) {
            for (Model.Vec v : arena.boundary) {
                if (v == null) continue;
                text.append(Math.round(v.x())).append(',').append(Math.round(v.z())).append(';');
            }
        }
        return digest(text.toString());
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) out.append(String.format("%02x", hash[i]));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Compact wire form: gzip then base64.
     *
     * <p>Terrain colours are smooth and highly repetitive, so deflate does most of the work; base64 keeps
     * the result safe inside a JSON string without escaping. A raw 128×128 map is 64&nbsp;KiB of ints,
     * which is far too much to re-send on every state tick — hence the compression, and the rule that this
     * is only ever sent once per client per map.
     */
    String encode() {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(16 + argb.length * 4);
            java.io.DataOutputStream data = new java.io.DataOutputStream(raw);
            data.writeInt(w);
            data.writeInt(h);
            data.writeDouble(x0);
            data.writeDouble(z0);
            data.writeDouble(spanX);
            data.writeDouble(spanZ);
            for (int value : argb) data.writeInt(value);
            data.flush();
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
                gzip.write(raw.toByteArray());
            }
            return Base64.getEncoder().encodeToString(packed.toByteArray());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("地形编码失败", ex);
        }
    }

    /** Decodes {@link #encode()}; returns {@code null} for anything malformed rather than throwing. */
    static TerrainMap decode(String id, String signature, String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
                gzip.transferTo(raw);
            }
            java.io.DataInputStream data = new java.io.DataInputStream(
                    new ByteArrayInputStream(raw.toByteArray()));
            int w = data.readInt();
            int h = data.readInt();
            if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS * 4) return null;
            double x0 = data.readDouble();
            double z0 = data.readDouble();
            double spanX = data.readDouble();
            double spanZ = data.readDouble();
            int[] argb = new int[w * h];
            for (int i = 0; i < argb.length; i++) argb[i] = data.readInt();
            return new TerrainMap(id, signature, x0, z0, spanX, spanZ, w, h, argb);
        } catch (java.io.IOException | RuntimeException ex) {
            return null;
        }
    }
}
