package cn.bfops;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which Valorant skin each preset number configures.
 *
 * <p>The pack has 26 Valorant presets ({@code 00009}–{@code 00034}), one per cosmetic skin, and each ships both
 * art and a stinger. The mapping below is read off the presets' own {@code display_name} values ("Valorant
 * Prime", "Valorant Gaia's Vengeance V2", …) rather than parsed from them at runtime, because a display name is
 * prose. The skin names are the pack's, not the game's: the pack writes "Gaia's Vengeance" for what the sound
 * files call {@code gaiavengeance}, and those are the strings {@link SoundBank}'s cues use.
 *
 * <p>Twenty-six presets but only eleven sound sets: a colourway ({@code V1}/{@code V2}/{@code V3}) is a recolour,
 * not a new sound, so four presets share one stinger. The one set with no preset is {@code gaia} — the pack ships
 * its five stingers but no preset selects that skin, which is noted here rather than silently dropped.
 */
final class ValorantPresets {
    private static final Map<Integer, String> SKIN = new LinkedHashMap<>();

    static {
        SKIN.put(9, "prime");
        SKIN.put(10, "glitchpop");
        SKIN.put(11, "singularity");
        SKIN.put(12, "singularity");
        SKIN.put(13, "singularity");
        SKIN.put(14, "gaiavengeance");
        SKIN.put(15, "gaiavengeance");
        SKIN.put(16, "gaiavengeance");
        SKIN.put(17, "gaiavengeance");
        SKIN.put(18, "bubblegumdeathwish");
        SKIN.put(19, "bubblegumdeathwish");
        SKIN.put(20, "bubblegumdeathwish");
        SKIN.put(21, "bubblegumdeathwish");
        SKIN.put(22, "champions2021");
        SKIN.put(23, "preludetochaos");
        SKIN.put(24, "preludetochaos");
        SKIN.put(25, "preludetochaos");
        SKIN.put(26, "primordium");
        SKIN.put(27, "primordium");
        SKIN.put(28, "primordium");
        SKIN.put(29, "primordium");
        SKIN.put(30, "radiantcrisis001");
        SKIN.put(31, "rgx11zpro");
        SKIN.put(32, "rgx11zpro");
        SKIN.put(33, "rgx11zpro");
        SKIN.put(34, "rgx11zpro");
    }

    private ValorantPresets() { }

    /** The skin for a preset number, or {@code null} when that preset is not a Valorant one. */
    static String skinFor(int presetNumber) {
        return SKIN.get(presetNumber);
    }

    /** The skin for a preset id such as {@code "00022"}, or {@code null}. */
    static String skinFor(String presetId) {
        if (presetId == null || presetId.isBlank()) return null;
        try {
            return skinFor(Integer.parseInt(presetId));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static int size() {
        return SKIN.size();
    }

    /** The distinct skins, for a test to compare against the 11 sound sets. */
    static java.util.List<String> skins() {
        return SKIN.values().stream().distinct().toList();
    }
}
