package cn.bfops;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * 1.20.1 drawing entry points.
 *
 * <p>1.21 replaced the shared {@code Tesselator.getBuilder()} with a per-draw {@code begin(...)} that
 * returns a builder and a {@code buildOrThrow()} that returns mesh data. 1.20.1 still has one shared
 * builder: it must be begun, filled, ended and then uploaded. These two helpers keep the call sites
 * readable instead of repeating that dance in every renderer.
 */
public final class Draw {
    private Draw() {}

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(mode, format);
        return builder;
    }

    public static BufferBuilder positionColor(VertexFormat.Mode mode) {
        return begin(mode, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void end(BufferBuilder builder) {
        // 1.20.1's Tesselator ends and uploads the shared builder itself; ending it here first made the
        // second call throw "Not building!".
        Tesselator.getInstance().end();
    }
}
