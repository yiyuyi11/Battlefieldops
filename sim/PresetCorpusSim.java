package cn.bfops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the preset corpus and the Valorant skin table against the pack's actual files.
 *
 * <p>{@code KillIconStore} reads presets out of the running game's resource manager, which a headless test cannot
 * do. So this reads the same files straight off disk and asserts the two things the store depends on: that every
 * preset parses to a name and a mode, and that the skin table's display names are consistent with the presets'.
 *
 * <p>The skin table is the interesting half. It maps 26 preset numbers onto 11 sound sets, and it was wrong on
 * the first attempt — presets 15/16/17 were filed under {@code gaia} when their own {@code display_name} says
 * "Gaia's Vengeance". A table that has been wrong once is worth pinning to the source it claims to summarise.
 */
public final class PresetCorpusSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final Path DIR = Path.of("src", "main", "resources", "assets", "battlefield_ops",
            "presets/official");

    public static void main(String[] args) throws IOException {
        corpusShape();
        elementCoverage();
        displayNames();
        skinTable();
        modeCoverage();
        System.out.println("PresetCorpusSim: " + checks + " checks passed");
    }

    /**
     * A preset configures several elements at once, and none may be dropped.
     *
     * <p>This is the check that caught a real modelling bug: the record kept only the <em>first</em>
     * {@code kill_icon/*} element, so {@code 00003}'s {@code card_bar} and {@code 00037}'s {@code honor} were
     * silently discarded. Every element key that appears in the corpus must be reachable from the parsed preset.
     */
    static void elementCoverage() throws IOException {
        // The eight icon modes and six subtitle renderers the pack actually uses, taken from the corpus.
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("kill_icon/battlefield1", 1);
        expected.put("kill_icon/card", 1);
        expected.put("kill_icon/card_bar", 1);
        expected.put("kill_icon/combo", 1);
        expected.put("kill_icon/honor", 1);
        expected.put("kill_icon/scrolling", 6);
        expected.put("kill_icon/valorant", 26);
        expected.put("subtitle/bonus_list", 9);
        expected.put("subtitle/combo", 1);
        expected.put("subtitle/hit_info", 1);
        expected.put("subtitle/kill_feed", 7);
        expected.put("subtitle/score", 9);

        Map<String, Integer> found = new LinkedHashMap<>();
        for (Path file : presetFiles()) {
            String id = file.getFileName().toString().replace(".json", "");
            KillIconPresets.Preset preset = KillIconPresets.parse(id,
                    Files.readString(file, StandardCharsets.UTF_8));
            check(preset != null, "preset " + id + " parses");
            if (preset == null) continue;
            for (String key : preset.elementKeys()) {
                found.merge(key, 1, Integer::sum);
            }
        }
        check(found.equals(expected),
                "every element key is counted exactly as the corpus has it.\n  expected " + expected
                        + "\n  found    " + found);

        // The two multi-element presets specifically: keeping only one would lose a whole half of the look.
        KillIconPresets.Preset cs2 = parse("00003");
        check(cs2.has("kill_icon/card"), "00003 configures the card mode");
        check(cs2.has("kill_icon/card_bar"), "00003 also configures the card bar, which must not be dropped");
        check(cs2.elementKeys().size() == 2, "00003 configures exactly two elements: " + cs2.elementKeys());

        KillIconPresets.Preset bf6 = parse("00037");
        check(bf6.has("kill_icon/scrolling"), "00037 configures the scrolling strip");
        check(bf6.has("kill_icon/honor"), "00037 also configures the honour badge row");
        check(bf6.has("subtitle/kill_feed"), "and a kill feed subtitle");
        check(bf6.has("subtitle/bonus_list"), "and a bonus list subtitle");
        check(bf6.has("subtitle/score"), "and a score subtitle");
        check(bf6.has("subtitle/hit_info"), "and a hit-info subtitle");
        check(bf6.elementKeys().size() == 6, "00037 configures six elements: " + bf6.elementKeys());

        // The two subtitle-only presets configure several subtitle elements and no icon at all.
        for (String id : List.of("00005", "00006")) {
            KillIconPresets.Preset preset = parse(id);
            check(preset != null, id + " parses");
            boolean anyIcon = preset.elementKeys().stream().anyMatch(k -> k.startsWith("kill_icon/"));
            check(!anyIcon, id + " configures no kill_icon element, which is why it is subtitle-only");
            check(preset.mode().equals("subtitle"), id + " reports a subtitle mode, got " + preset.mode());
            check(!preset.elementKeys().isEmpty(), id + " still configures subtitle elements");
        }

        // Session lookups are safe for a key the preset does not have, and for a null key.
        check(bf6.element("kill_icon/card") == null, "a missing element reads as null");
        check(bf6.element(null) == null, "a null element key reads as null");
        check(!bf6.has("kill_icon/card"), "has() is false for a missing element");
        check(!bf6.has(null), "has() is false for a null key");
        check(bf6.element("kill_icon/honor") != null, "a present element returns its body");
        check(bf6.elementKeys().equals(bf6.elementKeys().stream().sorted().toList()),
                "element keys come back sorted, so the config GUI can list them stably");
    }

    private static KillIconPresets.Preset parse(String id) throws IOException {
        return KillIconPresets.parse(id,
                Files.readString(DIR.resolve(id + ".json"), StandardCharsets.UTF_8));
    }

    private static List<Path> presetFiles() throws IOException {
        try (var stream = Files.list(DIR)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    /** All 37 presets are present, parse, and carry a name and a mode. */
    static void corpusShape() throws IOException {
        List<Path> files;
        try (var stream = Files.list(DIR)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        check(files.size() == 37, "the pack ships 37 presets, found " + files.size());

        List<String> ids = new ArrayList<>();
        int parsed = 0;
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".json", "");
            ids.add(id);
            // The file stem must be a five-digit number: `ClientPrefs` stores the id, and `KillIconStore`
            // enumerates `%05d` names, so a non-numeric stem would be an unselectable preset.
            check(id.matches("\\d{5}"), "preset id is a five-digit number: " + id);
            String json = Files.readString(file, StandardCharsets.UTF_8);
            KillIconPresets.Preset preset = KillIconPresets.parse(id, json);
            check(preset != null, "preset " + id + " parses");
            if (preset == null) continue;
            parsed++;
            check(preset.id().equals(id), "the parsed id matches the file: " + id);
            check(preset.name() != null && !preset.name().isBlank(),
                    "preset " + id + " has a display name");
            check(preset.mode() != null && !preset.mode().isBlank(),
                    "preset " + id + " resolves a mode");
            // Only the scrolling mode has a scroll config. The other modes are configured by their own
            // keys, so a non-scrolling preset correctly reports `null` here rather than a defaulted one --
            // defaulting it would make "this preset scrolls" and "this preset has scroll numbers" the same
            // question, which is exactly the confusion the mode field exists to prevent.
            if (preset.usesScrolling()) {
                check(preset.config() != null, "a scrolling preset has a resolved config: " + id);
            } else {
                check(preset.config() == null,
                        "a non-scrolling preset has no scroll config: " + id + " is " + preset.mode());
            }
            check(preset.raw() != null, "preset " + id + " keeps its raw JSON");
        }
        check(parsed == 37, "all 37 parsed, got " + parsed);
        // Ids are contiguous 1..37, so `KillIconStore`'s enumeration finds every one and none twice.
        for (int i = 1; i <= 37; i++) {
            check(ids.contains(String.format("%05d", i)),
                    "preset " + String.format("%05d", i) + " exists, so enumeration has no gap");
        }
    }

    /** The display names the preset cycle shows are the pack's own, not invented. */
    static void displayNames() throws IOException {
        Map<String, String> names = loadNames();
        check(names.size() == 37, "37 names read, got " + names.size());
        check(names.get("00001").equals("六五六自制预设"), "00001 is the pack's own custom preset");
        check(names.get("00037").equals("Battlefield 6模式"), "00037 is the Battlefield 6 look");
        check(names.get("00004").equals("Battlefield 1模式"), "00004 is Battlefield 1");
        check(names.get("00007").equals("Battlefield 5模式"), "00007 is Battlefield 5");
        check(names.get("00003").equals("CS2卡牌模式"), "00003 is the CS2 card mode");
        // The Valorant presets all announce themselves, which is what the skin table is checked against.
        for (int i = 9; i <= 34; i++) {
            String name = names.get(String.format("%05d", i));
            check(name != null && (name.startsWith("Valorant") || name.startsWith("Champions")),
                    "preset " + i + " is a Valorant skin, found " + name);
        }
    }

    /**
     * The skin table agrees with the presets' own names, skin by skin.
     *
     * <p>This is the check that would have caught the first-draft error: it derives the expected skin from the
     * display name and compares against {@link ValorantPresets}. Anything the derivation cannot classify fails
     * loudly rather than being skipped.
     */
    static void skinTable() throws IOException {
        Map<String, String> names = loadNames();
        check(ValorantPresets.size() == 26, "26 Valorant presets are mapped, found " + ValorantPresets.size());

        int mapped = 0;
        for (int i = 1; i <= 37; i++) {
            String id = String.format("%05d", i);
            String skin = ValorantPresets.skinFor(id);
            if (skin == null) {
                // A non-Valorant preset must genuinely not be one: its name must not start with "Valorant".
                String name = names.get(id);
                check(name == null || !name.startsWith("Valorant"),
                        "preset " + id + " has no skin only because it is not a Valorant preset: " + name);
                continue;
            }
            mapped++;
            String name = names.get(id);
            check(name != null && (name.startsWith("Valorant") || name.startsWith("Champions")),
                    "a preset with a skin is a Valorant preset: " + id + " is " + name);
            // The preset's name must contain the skin's words, in the pack's own spelling. "Valorant Gaia's
            // Vengeance" flattens to `valorantgaiasvengeance`, which contains `gaiavengeance` once the
            // apostrophe is gone — the apostrophe and the spaces are the only differences allowed. The leading
            // "Valorant" is kept rather than stripped, so the comparison is against the name as shipped.
            String flattenedName = name.toLowerCase(java.util.Locale.ROOT)
                    .replace("'", "").replace(" ", "");
            // `gaiavengeance` needs its possessive `s` removing to match the flattened name.
            String comparable = skin.equals("gaiavengeance") ? "gaiasvengeance" : skin;
            check(flattenedName.contains(comparable),
                    "preset " + id + " (" + name + " -> " + flattenedName + ") matches skin `" + skin + "`");
            // A colourway suffix in the name must not change the skin: a recolour is not a new sound.
            boolean colourway = name.matches(".*\\bV[123]\\b.*");
            check(!colourway || skin.contains("gaiavengeance") || skin.contains("singularity")
                            || skin.contains("bubblegumdeathwish") || skin.contains("preludetochaos")
                            || skin.contains("primordium") || skin.contains("rgx11zpro"),
                    "a colourway preset maps to a skin that has colourways: " + id + " " + skin);
        }
        check(mapped == 26, "all 26 Valorant presets are mapped, found " + mapped);

        // 26 presets collapse onto 10 *used* skins, because a colourway is a recolour rather than a new sound.
        // The pack ships 11 sound sets: `gaia` has five stingers and no preset, so the used count is one lower
        // than the sound-set count. Both numbers are asserted, so if the pack ever gains a Gaia preset this is
        // the check that reports it.
        List<String> skins = ValorantPresets.skins();
        check(skins.size() == 10, "26 presets collapse onto 10 skins, found " + skins.size() + ": " + skins);
        for (String skin : skins) {
            check(!SoundBank.variants("valorant_" + skin).isEmpty(),
                    "skin `" + skin + "` has a sound cue");
            check(SoundBank.variants("valorant_" + skin).size() == 5,
                    "skin `" + skin + "` has all five escalating stings");
        }
        // Every skin's five stings are distinct events, so the escalation is audible.
        for (String skin : skins) {
            var cue = SoundBank.variants("valorant_" + skin);
            check(cue.stream().distinct().count() == 5, "skin `" + skin + "` stings are distinct");
        }
        // `gaia` ships five stingers but no preset selects it. That is a property of the pack, not a bug in the
        // table, so it is asserted: if a preset ever gains that skin, this check is what reports it.
        check(!skins.contains("gaia"), "the pack ships `gaia` audio but no preset selects that skin");
        check(!SoundBank.variants("valorant_gaia").isEmpty(), "the unused `gaia` stingers still exist");
        check(SoundBank.variants("valorant_gaia").size() == 5, "and it has all five");
    }

    /** Every preset resolves to a mode the renderer knows, and the census is the reference's. */
    static void modeCoverage() throws IOException {
        Map<String, String> modes = new LinkedHashMap<>();
        try (var stream = Files.list(DIR)) {
            for (Path file : stream.sorted().toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                KillIconPresets.Preset preset = KillIconPresets.parse(id,
                        Files.readString(file, StandardCharsets.UTF_8));
                if (preset != null) modes.put(id, preset.mode());
            }
        }
        check(modes.size() == 37, "37 modes, got " + modes.size());
        // A preset's mode is the full element key (`kill_icon/scrolling`), not a bare name -- that is what the
        // pack's JSON contains, and `Preset.usesScrolling` is the predicate for the scrolling one.
        long scrolling = modes.values().stream().filter(m -> m.equals("kill_icon/scrolling")).count();
        check(scrolling == 6, "6 presets use the scrolling mode, found " + scrolling);
        // The two subtitle-only presets are the ones with no kill_icon mode configured at all.
        long other = modes.size() - scrolling;
        check(other == 31, "the remaining 31 use named modes, found " + other);
        // A mode is never blank -- `KillIconPresets.parse` falls back rather than returning null.
        for (var entry : modes.entrySet()) {
            check(!entry.getValue().isBlank(), "mode is never blank: " + entry.getKey());
        }
        // The modes actually present, so the renderer's coverage can be compared against this list.
        List<String> distinct = modes.values().stream().distinct().sorted().toList();
        check(distinct.size() >= 5, "the pack uses several modes, found " + distinct);
        System.out.println("  modes: " + distinct);
    }

    private static Map<String, String> loadNames() throws IOException {
        Map<String, String> names = new LinkedHashMap<>();
        try (var stream = Files.list(DIR)) {
            for (Path file : stream.sorted().toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                KillIconPresets.Preset preset = KillIconPresets.parse(id,
                        Files.readString(file, StandardCharsets.UTF_8));
                if (preset != null && preset.name() != null) names.put(id, preset.name());
            }
        }
        return names;
    }
}
