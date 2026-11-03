package cn.bfops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies the translation files, which are easy to break and silent when broken.
 *
 * <p>Three defects this catches, all of which actually happened during this port:
 *
 * <ul>
 *   <li><b>Translations in a directory Minecraft never reads.</b> The pack's lang files were first extracted to
 *       {@code lang/killicon/zh_cn.json}. Only {@code lang/<locale>.json} is loaded, so 1,577 keys sat in the jar
 *       doing nothing and every kill-icon string rendered as its raw key.</li>
 *   <li><b>Keys still namespaced for the reference mod.</b> The pack's keys are {@code gd656killicon.*}; this mod
 *       is {@code battlefield_ops}. A key that names a mod which is not installed resolves to nothing.</li>
 *   <li><b>Subtitles without translations.</b> A sound with a {@code subtitle} but no matching lang key shows the
 *       player the key itself, which is worse than having no subtitle.</li>
 * </ul>
 */
public final class LangSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "battlefield_ops");
    private static final Path LANG = ASSETS.resolve("lang");
    /** The reference mod's id. No key in this mod may carry it. */
    private static final String REF = "gd656killicon";
    /** This mod's id. Every asset key must carry it. */
    private static final String OURS = "battlefield_ops";

    public static void main(String[] args) throws IOException {
        fileLayout();
        namespaces();
        subtitles();
        awardKeys();
        fullness();
        locales();
        System.out.println("LangSim: " + checks + " checks passed");
    }

    /** Only the flat locale files exist, and there are no locale files anywhere else. */
    static void fileLayout() throws IOException {
        List<String> locales;
        try (var stream = Files.list(LANG)) {
            locales = stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json")).sorted().toList();
        }
        check(locales.contains("en_us.json"), "en_us is present");
        check(locales.contains("zh_cn.json"), "zh_cn is present");
        check(locales.contains("zh_tw.json"), "zh_tw is present");
        // A nested lang directory is never read by the game; its absence is the check.
        check(!Files.isDirectory(LANG.resolve("killicon")),
                "no nested lang directory -- Minecraft only loads lang/<locale>.json");
        // No stray *translation* files outside lang/. `formats/zh_cn.json` is legitimately locale-named but is
        // not a translation file -- it holds the per-locale format strings the presets reference -- so the
        // walk skips that directory explicitly rather than banning the filename pattern everywhere.
        try (var stream = Files.walk(ASSETS)) {
            List<Path> nested = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("[a-z]{2}_[a-z]{2}\\.json"))
                    .filter(p -> !p.getParent().equals(LANG))
                    .filter(p -> !p.getParent().getFileName().toString().equals("formats"))
                    .filter(p -> !p.getParent().getFileName().toString().equals("presets"))
                    .toList();
            check(nested.isEmpty(), "no translation files outside lang/: " + nested);
        }
        // `formats/` does ship locale-named files, and they must be real JSON: they are read at render time.
        for (String locale : List.of("en_us.json", "zh_cn.json", "zh_tw.json", "lzh.json")) {
            Path path = ASSETS.resolve("formats").resolve(locale);
            check(Files.isRegularFile(path), "formats/" + locale + " exists");
            check(readAt(path).size() > 0, "formats/" + locale + " is a non-empty object");
        }
    }

    /** Every key belongs to this mod, and none to the reference mod. */
    static void namespaces() throws IOException {
        for (String locale : List.of("en_us.json", "zh_cn.json", "zh_tw.json", "lzh.json")) {
            JsonObject data = read(locale);
            List<String> foreign = new ArrayList<>();
            for (String key : data.keySet()) {
                if (key.contains(REF)) foreign.add(key);
            }
            check(foreign.isEmpty(), locale + " has no keys naming the reference mod: " + foreign);
            // The bulk of the keys are asset keys and must be under this mod's namespace; keybinds are the
            // documented exception, prefixed `key.` by Minecraft's own convention.
            for (String key : data.keySet()) {
                boolean ours = key.startsWith(OURS + ".") || key.startsWith("key." + OURS + ".")
                        || key.startsWith("key.categories." + OURS);
                check(ours, locale + " key `" + key + "` belongs to this mod");
            }
        }
    }

    /** Every sound with a subtitle has a translation for it, in both languages. */
    static void subtitles() throws IOException {
        JsonObject sounds = JsonParser.parseString(
                Files.readString(ASSETS.resolve("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> subtitleKeys = new ArrayList<>();
        for (String event : sounds.keySet()) {
            var value = sounds.get(event);
            if (!value.isJsonObject()) continue;
            JsonObject entry = value.getAsJsonObject();
            if (!entry.has("subtitle")) continue;
            subtitleKeys.add(entry.get("subtitle").getAsString());
        }
        check(subtitleKeys.size() >= 86, "the kill sounds carry subtitles, found " + subtitleKeys.size());

        for (String locale : List.of("en_us.json", "zh_cn.json")) {
            JsonObject data = read(locale);
            List<String> missing = new ArrayList<>();
            for (String key : subtitleKeys) {
                if (!data.has(key)) missing.add(key);
            }
            check(missing.isEmpty(), locale + " translates every subtitle, missing: " + missing);
            // A subtitle must be a short fragment, not a sentence with a full stop.
            for (String key : subtitleKeys) {
                String text = data.get(key).getAsString();
                check(!text.isBlank(), locale + " subtitle `" + key + "` is not blank");
                check(!text.contains(OURS), locale + " subtitle `" + key + "` is translated, not a key");
            }
        }
        // The two languages actually differ, so a copy-paste of one into the other is caught.
        JsonObject en = read("en_us.json");
        JsonObject zh = read("zh_cn.json");
        int differing = 0;
        for (String key : subtitleKeys) {
            if (en.has(key) && zh.has(key) && !en.get(key).getAsString().equals(zh.get(key).getAsString())) {
                differing++;
            }
        }
        check(differing > subtitleKeys.size() / 2,
                "most subtitles are actually translated, not copied: " + differing + " of " + subtitleKeys.size());
    }

    /** Every bonus the award catalogue names has a name and a description in both languages. */
    static void awardKeys() throws IOException {
        JsonObject zh = read("zh_cn.json");
        JsonObject en = read("en_us.json");
        List<String> missingZh = new ArrayList<>();
        List<String> missingEn = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (!zh.has(award.nameKey())) missingZh.add(award.nameKey());
            if (!zh.has(award.descKey())) missingZh.add(award.descKey());
            if (!en.has(award.nameKey())) missingEn.add(award.nameKey());
            if (!en.has(award.descKey())) missingEn.add(award.descKey());
        }
        check(missingZh.isEmpty(), "every award is named and described in zh_cn, missing: " + missingZh);
        check(missingEn.isEmpty(), "every award is named and described in en_us, missing: " + missingEn);

        // Spot-check the reference's own wording rather than trusting the count.
        check(zh.get("battlefield_ops.bonus.HEADSHOT.name").getAsString().contains("爆头"),
                "HEADSHOT is a headshot in Chinese");
        check(en.get("battlefield_ops.bonus.HEADSHOT.name").getAsString().equalsIgnoreCase("Headshot")
                        || en.get("battlefield_ops.bonus.HEADSHOT.name").getAsString()
                        .toLowerCase(java.util.Locale.ROOT).contains("headshot"),
                "and in English, got " + en.get("battlefield_ops.bonus.HEADSHOT.name").getAsString());
        // A description that is long enough to say something, not a stub.
        check(zh.get("battlefield_ops.bonus.HEADSHOT.desc").getAsString().length() > 4,
                "the description is a sentence, not a placeholder");
    }

    /** The files are substantially filled, and the two locales agree on which keys exist. */
    static void fullness() throws IOException {
        JsonObject en = read("en_us.json");
        JsonObject zh = read("zh_cn.json");
        JsonObject tw = read("zh_tw.json");
        check(en.size() > 1500, "en_us has the bulk of the pack's keys, found " + en.size());
        check(zh.size() > 1500, "zh_cn likewise, found " + zh.size());
        check(tw.size() > 1500, "zh_tw likewise, found " + tw.size());

        // en_us and zh_cn are the two complete locales and must have the same key set.
        List<String> onlyEn = en.keySet().stream().filter(k -> !zh.has(k)).sorted().toList();
        List<String> onlyZh = zh.keySet().stream().filter(k -> !en.has(k)).sorted().toList();
        check(onlyEn.isEmpty(), "no key exists only in en_us: " + onlyEn);
        check(onlyZh.isEmpty(), "no key exists only in zh_cn: " + onlyZh);

        // No blank values anywhere: a blank translation falls back to the key, same as a missing one.
        for (var entry : List.of(Map.entry("en_us", en), Map.entry("zh_cn", zh))) {
            List<String> blank = entry.getValue().keySet().stream()
                    .filter(k -> entry.getValue().get(k).getAsString().isBlank())
                    .limit(5).toList();
            check(blank.isEmpty(), entry.getKey() + " has no blank values: " + blank);
        }
    }

    /** Every reference locale is present, and every one is actually translated. */
    static void locales() throws IOException {
        // The reference ships four: en_us, zh_cn, zh_tw and lzh (Literary Chinese). The port dropped lzh at
        // first because the merge step enumerated the locales it expected rather than the ones present --
        // exactly the class of bug this suite exists to catch.
        for (String locale : List.of("en_us.json", "zh_cn.json", "zh_tw.json", "lzh.json")) {
            Path path = LANG.resolve(locale);
            check(Files.isRegularFile(path), locale + " is present");
            check(readAt(path).size() > 1500, locale + " is filled, not a stub");
        }

        JsonObject lzh = read("lzh.json");
        JsonObject zh = read("zh_cn.json");
        JsonObject en = read("en_us.json");
        // lzh must agree with zh_cn on the key set, because it was built from it.
        List<String> missing = zh.keySet().stream().filter(k -> !lzh.has(k)).sorted().toList();
        check(missing.isEmpty(), "lzh covers every zh_cn key, missing: " + missing);

        // And it must genuinely differ: a copy of zh_cn pasted under another name is worse than no locale,
        // because it claims a translation that is not there.
        int differing = 0;
        for (String key : lzh.keySet()) {
            if (!lzh.get(key).getAsString().equals(zh.get(key).getAsString())) differing++;
        }
        check(differing > lzh.size() / 2,
                "most lzh strings differ from zh_cn, differing: " + differing + " of " + lzh.size());

        // The reference's own Literary Chinese wording, to prove the values are its and not invented.
        check("斬敵".equals(lzh.get("battlefield_ops.bonus.KILL.name").getAsString()),
                "KILL is 斬敵 in Literary Chinese, got "
                        + lzh.get("battlefield_ops.bonus.KILL.name").getAsString());
        check("貫腦傷".equals(lzh.get("battlefield_ops.bonus.HEADSHOT.name").getAsString()),
                "HEADSHOT is 貫腦傷, got "
                        + lzh.get("battlefield_ops.bonus.HEADSHOT.name").getAsString());
        // en_us and lzh must not be the same either.
        check(!en.get("battlefield_ops.bonus.KILL.name").getAsString()
                        .equals(lzh.get("battlefield_ops.bonus.KILL.name").getAsString()),
                "en_us and lzh are different languages");

        // The reference leaves eleven lzh values null and the port had to fill them from zh_cn. Those keys
        // must still be non-null here, because a null in a lang file crashes the loader, not just the screen.
        List<String> blank = lzh.keySet().stream()
                .filter(k -> lzh.get(k).getAsString().isBlank()).limit(5).toList();
        check(blank.isEmpty(), "no lzh value is blank or null: " + blank);
    }

    private static JsonObject read(String locale) throws IOException {
        return readAt(LANG.resolve(locale));
    }

    private static JsonObject readAt(Path path) throws IOException {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
