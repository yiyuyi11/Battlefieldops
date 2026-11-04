package cn.bfops;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * User-supplied screen backgrounds.
 *
 * <p>Drop PNG files into {@code config/battlefield_ops/backgrounds/} and they become selectable as the
 * backdrop of every mod screen; an empty selection keeps the built-in BF1 plate. The list is scanned on
 * demand, so adding a file does not need a game restart — the settings screen has a "rescan" row.
 *
 * <p>Textures are registered lazily, one per file, and released when the folder is rescanned or the list
 * is cleared at logout: a screenshot-sized PNG left registered after the player swaps images would sit in
 * VRAM for the rest of the session.
 */
final class ScreenBackground {
    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static List<String> names = List.of();
    private static boolean scanned;

    private ScreenBackground() {}

    static Path folder() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("battlefield_ops/backgrounds");
    }

    /** Available image names (file names without extension), sorted. */
    static List<String> names() {
        if (!scanned) refresh();
        return names;
    }

    static void refresh() {
        release();
        scanned = true;
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                names = stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(name -> name.substring(0, name.length() - 4))
                        .sorted()
                        .toList();
            }
        } catch (Exception ex) {
            BattlefieldOps.LOG.warn("Cannot scan the background folder", ex);
            names = List.of();
        }
    }

    static void release() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : TEXTURES.values()) manager.release(id);
        TEXTURES.clear();
        SIZES.clear();
        scanned = false;
    }

    /** The texture for {@code name}, loading it on first use; null when the file is gone or unreadable. */
    private static ResourceLocation texture(String name) {
        ResourceLocation cached = TEXTURES.get(name);
        if (cached != null) return cached;
        Path file = folder().resolve(name + ".png");
        if (!Files.isRegularFile(file)) return null;
        try (InputStream stream = Files.newInputStream(file)) {
            var image = com.mojang.blaze3d.platform.NativeImage.read(stream);
            var id = Minecraft.getInstance().getTextureManager()
                    .register("bfops_bg_" + name.replaceAll("[^a-zA-Z0-9_]", "_"), new DynamicTexture(image));
            TEXTURES.put(name, id);
            SIZES.put(name, new int[]{image.getWidth(), image.getHeight()});
            return id;
        } catch (Exception ex) {
            BattlefieldOps.LOG.warn("Cannot load background {}", file, ex);
            return null;
        }
    }

    /** True when the player has picked a background that actually loads. */
    static boolean custom() {
        String name = ClientPrefs.screenBackground();
        if (name == null || name.isBlank()) return false;
        return names().contains(name) && texture(name) != null;
    }

    /**
     * Paints the chosen backdrop (or nothing, when none is selected), then the veil.
     *
     * @param veil packed ARGB veil drawn over the image so panel text stays readable
     */
    static boolean draw(GuiGraphics g, int width, int height, int veil) {
        String name = ClientPrefs.screenBackground();
        if (name == null || name.isBlank() || width <= 0 || height <= 0) return false;
        ResourceLocation id = texture(name);
        if (id == null) return false;
        int[] size = SIZES.getOrDefault(name, new int[]{16, 16});
        double scale = Math.max(width / (double) size[0], height / (double) size[1]);
        int w = (int) Math.ceil(size[0] * scale), h = (int) Math.ceil(size[1] * scale);
        g.blit(id, (width - w) / 2, (height - h) / 2, w, h, 0, 0, size[0], size[1], size[0], size[1]);
        if ((veil >>> 24) != 0) g.fill(0, 0, width, height, veil);
        return true;
    }
}
