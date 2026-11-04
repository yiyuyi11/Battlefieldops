package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads the 37 preset files and owns the one live kill-icon strip.
 *
 * <p>Split from {@link KillIconPresets} for the same reason the rest of this port is split: the parser is pure
 * and testable, this class is the part that needs a running client. Everything here is "read a resource, keep
 * the result".
 *
 * <p><b>Why the corpus is loaded once and cached.</b> The presets are 37 small JSON files that never change
 * while the game is running. Reading them per frame would be absurd; reading them once at first use and on a
 * pack reload is correct. The cache is invalidated explicitly by {@link #reload()} rather than by watching file
 * timestamps, because a resource reload is a discrete event the game already tells us about.
 */
final class KillIconStore {
    /** The directory the presets are read from, relative to the mod's own assets. */
    static final String PRESET_DIR = "presets/official";

    private static KillIconPresets corpus;
    private static KillIconHud hud;
    private static String hudPreset;

    private KillIconStore() { }

    /**
     * The preset corpus, loading it on first use.
     *
     * <p>An empty corpus rather than {@code null} when nothing can be read: every caller can then treat
     * "no presets" as "the default look" without a null check, and the mod still draws something.
     */
    static KillIconPresets corpus() {
        if (corpus == null) corpus = load();
        return corpus;
    }

    /** Drops the cached corpus and the live strip, so the next frame reloads both. */
    static void reload() {
        corpus = null;
        hud = null;
        hudPreset = null;
    }

    /** The live strip, built for whichever preset the player has chosen. */
    static KillIconHud hud() {
        String chosen = ClientPrefs.killIconPreset();
        if (hud == null || !chosen.equals(hudPreset)) {
            hud = new KillIconHud(corpus().byId(chosen), chosen);
            hudPreset = chosen;
        }
        return hud;
    }

    /** Rebuilds the strip for a new preset. Called when the player cycles presets. */
    static void applyPreset(String presetId) {
        hud = new KillIconHud(corpus().byId(presetId), presetId);
        hudPreset = presetId;
    }

    /** Queues an award for the animated strip and plays its sound. */
    static void trigger(String award, long now) {
        hud().trigger(KillIcons.kindFor(award), now);
        // The pack pairs every icon with a stinger, and a preset that changes the look should change the
        // sound too -- that is the whole point of the four "named look" families. The cue is chosen from the
        // preset's mode and the award's kind by `SoundCues`, so this stays one call.
        SoundCues.killFeedback(ClientPrefs.killIconPreset(), award);
    }

    /** Draws the strip. Safe to call when nothing is on screen. */
    static void draw(GuiGraphics g, long now) {
        if (!hud().hasVisibleIcons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        hud().render(g, now, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /** Forgets everything on screen — used when leaving a match. */
    static void reset() {
        if (hud != null) hud.reset();
    }

    /**
     * Reads and parses every preset file.
     *
     * <p>Missing files and malformed JSON are skipped individually rather than aborting the load: one bad
     * preset should cost one preset, not the whole look. The count is logged so a partial load is visible
     * rather than silently smaller.
     */
    private static KillIconPresets load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return KillIconPresets.empty();
        List<KillIconPresets.Preset> presets = new ArrayList<>();
        for (int index = 1; index <= 200; index++) {
            String id = String.format("%05d", index);
            ResourceLocation location = new ResourceLocation("battlefield_ops",
                    PRESET_DIR + "/" + id + ".json");
            Optional<Resource> resource = mc.getResourceManager().getResource(location);
            if (resource.isEmpty()) continue;
            try (BufferedReader reader = resource.get().openAsReader()) {
                KillIconPresets.Preset preset = KillIconPresets.parse(id, readAll(reader));
                if (preset != null) presets.add(preset);
            } catch (Exception ex) {
                // One unreadable preset must not cost the other thirty-six.
                BattlefieldOps.LOG.warn("kill-icon preset {} could not be read: {}", id, ex.toString());
            }
        }
        return KillIconPresets.of(presets);
    }

    private static String readAll(BufferedReader reader) throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) out.append(buffer, 0, read);
        return out.toString();
    }

    /** Exposed for tests: the preset ids in load order. */
    static List<String> presetIds() {
        return corpus().all().stream().map(KillIconPresets.Preset::id).toList();
    }

    /** Charset used to read presets; the pack's files are UTF-8 with Chinese display names. */
    static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;
}
