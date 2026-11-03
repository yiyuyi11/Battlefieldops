package cn.bfops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the port to the reference's own list of configurable elements.
 *
 * <p>The reference's {@code assets/gd656killicon/formats/<locale>.json} is the authoritative inventory: it
 * names every element type the config system knows. It holds exactly six keys — one icon element
 * ({@code kill_icon/honor}) and five subtitle elements. Everything else the renderer can draw
 * ({@code kill_icon/scrolling}, {@code valorant}, {@code card}, …) is declared per-preset inside
 * {@code presets/official/*.json}, not in the formats file.
 *
 * <p><b>Why this test exists.</b> During the port the docs claimed "6 subtitle renderers". That number came
 * from counting the six <em>format</em> keys and assuming they were all subtitles; one of them is an icon
 * element. The mistake survived several rounds of review because nothing compared the prose against the
 * file. This test compares the code's view of the subtitle set against the formats file, so the next
 * miscount fails a build instead of reaching a changelog.
 */
public final class FormatManifestSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "battlefield_ops");

    public static void main(String[] args) throws IOException {
        manifestShape();
        elementCoverage();
        subtitleRenderers();
        System.out.println("FormatManifestSim: " + checks + " checks passed");
    }

    /** The port ships the same manifest the reference does, in the same locales. */
    static void manifestShape() throws IOException {
        Path formats = ASSETS.resolve("formats");
        check(Files.isDirectory(formats), "the formats directory exists");
        // The reference ships four locales for formats, including Literary Chinese.
        for (String locale : List.of("en_us.json", "zh_cn.json", "zh_tw.json", "lzh.json")) {
            check(Files.isRegularFile(formats.resolve(locale)), "formats/" + locale + " exists");
        }
        // Every locale declares the same key set, or a player switching language loses elements.
        Set<String> reference = null;
        for (String locale : List.of("en_us.json", "zh_cn.json", "zh_tw.json", "lzh.json")) {
            JsonObject data = read(formats.resolve(locale));
            Set<String> keys = new LinkedHashSet<>(data.keySet());
            check(keys.size() == 6, "formats/" + locale + " declares 6 elements, found " + keys.size());
            if (reference == null) reference = keys;
            else check(keys.equals(reference), "formats/" + locale + " matches en_us's key set, got " + keys);
        }

        // The split is 1 icon + 5 subtitle, and that is the number the docs must quote.
        JsonObject en = read(formats.resolve("en_us.json"));
        List<String> icons = new ArrayList<>();
        List<String> subs = new ArrayList<>();
        for (String key : en.keySet()) {
            if (key.startsWith("kill_icon/")) icons.add(key);
            else if (key.startsWith("subtitle/")) subs.add(key);
            else throw new AssertionError("unknown element kind: " + key);
        }
        check(icons.equals(List.of("kill_icon/honor")),
                "the manifest names one icon element, found " + icons);
        check(subs.size() == 5, "the manifest names five subtitle elements, found " + subs);
        check(subs.containsAll(List.of("subtitle/kill_feed", "subtitle/bonus_list", "subtitle/score",
                        "subtitle/hit_info", "subtitle/combo")),
                "and they are the five the renderer implements, found " + subs);
        check(icons.size() + subs.size() == 6, "1 + 5 = 6");
    }

    /**
     * Every element the presets actually configure is one the manifest knows or one the renderer handles.
     *
     * <p>The manifest is not the whole story: presets configure {@code kill_icon/card} and friends, which the
     * formats file never mentions. So this walks the presets and asserts that each element key is either in
     * the manifest or in the renderer's own dispatch list — nothing is silently ignored.
     */
    static void elementCoverage() throws IOException {
        Path presets = ASSETS.resolve("presets/official");
        check(Files.isDirectory(presets), "the preset directory exists");
        List<Path> files;
        try (var stream = Files.list(presets)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        check(files.size() == 37, "37 presets ship, found " + files.size());

        JsonObject manifest = read(ASSETS.resolve("formats/en_us.json"));
        Set<String> seenIcons = new LinkedHashSet<>();
        Set<String> seenSubs = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (Path file : files) {
            JsonObject preset = read(file);
            for (String key : preset.keySet()) {
                if (key.startsWith("kill_icon/")) {
                    seenIcons.add(key);
                    // The seven renderable icon modes are handled by KillIconHud's dispatch.
                    if (!isKnownIconElement(key) && !manifest.has(key)) unknown.add(key);
                } else if (key.startsWith("subtitle/")) {
                    seenSubs.add(key);
                    if (!isKnownSubtitleElement(key) && !manifest.has(key)) unknown.add(key);
                }
            }
        }
        check(unknown.isEmpty(), "every configured element is renderable, unknown: " + unknown);
        // Seven icon modes, as the docs claim.
        check(seenIcons.size() == 7, "the presets configure seven icon modes, found " + seenIcons);
        // Five subtitle kinds, and each one appears in the manifest.
        check(seenSubs.equals(new LinkedHashSet<>(List.of("subtitle/kill_feed", "subtitle/bonus_list",
                        "subtitle/score", "subtitle/hit_info", "subtitle/combo"))),
                "the presets use exactly the five manifest subtitles, found " + seenSubs);
        for (String key : seenSubs) {
            check(manifest.has(key), key + " is declared in the formats manifest");
        }
    }

    /** The renderer has a branch for every subtitle element the manifest declares, and no others. */
    static void subtitleRenderers() throws IOException {
        // KillIconSubtitles' own list of supported keys, which is what the dispatch switches on.
        List<String> supported = KillIconSubtitles.supported();
        check(supported.size() == 5, "the renderer supports five subtitle kinds, found " + supported);
        JsonObject manifest = read(ASSETS.resolve("formats/en_us.json"));
        List<String> declared = new ArrayList<>();
        for (String key : manifest.keySet()) if (key.startsWith("subtitle/")) declared.add(key);
        check(new LinkedHashSet<>(supported).equals(new LinkedHashSet<>(declared)),
                "the renderer's kinds match the manifest exactly: renderer " + supported
                        + " vs manifest " + declared);

        // The combo streak strings are part of the combo element, not a sixth subtitle kind.
        JsonObject combo = new JsonObject();
        check(AwardCatalog.byId("KILL_COMBO").streakSubtitles().size() == 7,
                "the seven combo streak strings belong to KILL_COMBO");
        check(!supported.contains("subtitle/streak"),
                "there is no separate streak subtitle element -- the streak strings are a field of KILL_COMBO");

        // Each supported key resolves a non-blank label in both complete locales, so the renderer can never
        // draw an untranslated element name. The pack's key shape is
        // `battlefield_ops.element.name.<kind>.<element>`, with the slash turned into a dot.
        JsonObject zh = read(ASSETS.resolve("lang/zh_cn.json"));
        for (String key : supported) {
            String langKey = "battlefield_ops.element.name." + key.replace('/', '.');
            check(zh.has(langKey), "the element label resolves: " + langKey);
            check(!zh.get(langKey).getAsString().isBlank(), "and is not blank: " + langKey);
        }
        // And every icon element has one too -- twelve labels for twelve elements.
        int labels = 0;
        for (String langKey : zh.keySet()) {
            if (langKey.startsWith("battlefield_ops.element.name.")) labels++;
        }
        check(labels == 12, "twelve element labels: 7 icon + 5 subtitle, found " + labels);
    }

    /** The seven icon element keys the HUD dispatches on. */
    static boolean isKnownIconElement(String key) {
        return switch (key) {
            case "kill_icon/scrolling", "kill_icon/card", "kill_icon/card_bar", "kill_icon/combo",
                 "kill_icon/valorant", "kill_icon/honor", "kill_icon/battlefield1" -> true;
            default -> false;
        };
    }

    static boolean isKnownSubtitleElement(String key) {
        return KillIconSubtitles.supported().contains(key);
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
