package cn.bfops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the 49 honour titles against the reference's own table and the pack's badge art.
 *
 * <p>Honours and bonuses are the two vocabularies this port came closest to conflating, so the test states the
 * distinction and then holds both sides to it: 49 honours with badges, 72 bonuses with score caps, and exactly
 * one id in common.
 */
public final class HonourCatalogSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "battlefield_ops");

    public static void main(String[] args) throws IOException {
        tableShape();
        conditions();
        badges();
        translations();
        streakRules();
        separation();
        System.out.println("HonourCatalogSim: " + checks + " checks passed");
    }

    /** 49 entries, unique ids, a condition and a score each. */
    static void tableShape() {
        List<HonourCatalog> all = HonourCatalog.all();
        check(all.size() == 49, "the reference registers 49 honours, found " + all.size());
        check(HonourCatalog.size() == 49, "size() agrees");
        Set<String> ids = new HashSet<>();
        for (HonourCatalog honour : all) {
            check(ids.add(honour.id()), "honour id is unique: " + honour.id());
            check(honour.kind() != null, "honour has a condition kind: " + honour.id());
            check(honour.condition() != null && !honour.condition().isBlank(),
                    "honour has a condition string: " + honour.id());
            check(honour.score() > 0, "honour is worth points: " + honour.id());
            // Ids are lowercase snake_case, because they are also filename stems.
            check(honour.id().matches("[a-z0-9_]+"), "honour id is a safe filename stem: " + honour.id());
        }
        check(ids.size() == 49, "all 49 ids are distinct");

        // The reference's own first and last entries, to catch a transcription slip.
        check(all.get(0).id().equals("headhunter"), "the first honour is headhunter");
        check(all.get(0).condition().equals("headshot:3"), "and it needs three headshots");
        check(all.get(0).score() == 30, "and is worth 30");
        check(all.get(all.size() - 1).id().equals("warhead"), "the last honour is warhead");
        check(all.get(all.size() - 1).condition().equals("heavy_headshot"), "a heavy headshot");

        // Two streak honours and 47 conditionals, per the reference's mix.
        long streaks = all.stream().filter(h -> h.kind() == HonourCatalog.Kind.STREAK).count();
        long conditionals = all.stream().filter(h -> h.kind() == HonourCatalog.Kind.CONDITIONAL).count();
        check(streaks == 2, "two honours are streaks, found " + streaks);
        check(conditionals == 47, "47 are conditional, found " + conditionals);
        check(streaks + conditionals == 49, "the two kinds partition the table");

        // Lookup is exact and safe.
        check(HonourCatalog.byId("headhunter") != null, "a known honour is found");
        check(HonourCatalog.byId("HEADHUNTER") == null, "ids are case-sensitive");
        check(HonourCatalog.byId(null) == null, "a null id finds nothing");
        check(HonourCatalog.byId("nope") == null, "an unknown id finds nothing");

        // Two honours share the `leave_it_to_me` condition, which is the reference's own duplication -- they
        // differ in score, so the pair is not an accident but a design.
        List<String> shared = new ArrayList<>();
        for (HonourCatalog honour : all) {
            if (honour.condition().equals("leave_it_to_me")) shared.add(honour.id());
        }
        check(shared.equals(List.of("avenger", "guardian")),
                "avenger and guardian both mean 'leave it to me', found " + shared);
        check(HonourCatalog.byId("avenger").score() == 65, "avenger is worth more (65)");
        check(HonourCatalog.byId("guardian").score() == 50, "guardian is worth 50");
    }

    /** Condition strings keep the reference's own vocabulary, including the two parameterised ones. */
    static void conditions() {
        // A streak condition is `subject:count`; both must parse.
        for (HonourCatalog honour : HonourCatalog.all()) {
            if (honour.kind() != HonourCatalog.Kind.STREAK) continue;
            int colon = honour.condition().indexOf(':');
            check(colon > 0, honour.id() + " has a colon-separated condition");
            String subject = honour.condition().substring(0, colon);
            check(!subject.isBlank(), honour.id() + " names what is being counted");
            int needed;
            try {
                needed = Integer.parseInt(honour.condition().substring(colon + 1));
            } catch (NumberFormatException ex) {
                throw new AssertionError(honour.id() + " has a numeric count");
            }
            check(needed > 1, honour.id() + " needs more than one: " + needed);
        }
        // A conditional names an event, never a colon pair.
        for (HonourCatalog honour : HonourCatalog.all()) {
            if (honour.kind() != HonourCatalog.Kind.CONDITIONAL) continue;
            check(!honour.condition().contains(":"),
                    honour.id() + " is not parameterised: " + honour.condition());
        }
        // A handful of the reference's distinctive conditions, to catch a wholesale re-typing.
        check(HonourCatalog.byId("roadhog").condition().equals("roadkill"), "roadhog is a roadkill");
        check(HonourCatalog.byId("pathfinder").condition().equals("squad_deploy_3"),
                "pathfinder is three squad deploys");
        check(HonourCatalog.byId("ace_pilot").condition().equals("destroy_air_vehicle_in_air"),
                "ace_pilot destroys an air vehicle in the air");
        check(HonourCatalog.byId("tank_destroyer").condition().equals("destroy_tank"),
                "tank_destroyer destroys a tank");
        check(HonourCatalog.byId("long_shot").score() == 60, "long_shot is worth 60");
        check(HonourCatalog.byId("raider").score() == 150, "raider is the highest at 150");
        check(HonourCatalog.byId("bail_rocket").score() == 1, "and bail_rocket the lowest at 1");
    }

    /** Every honour has badge art, and the art is exactly the 49 files the pack ships. */
    static void badges() throws IOException {
        Path honour = ASSETS.resolve("icons/honor");
        check(Files.isDirectory(honour), "the honour art directory exists");
        List<String> files;
        try (var stream = Files.list(honour)) {
            files = stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".png")).sorted().toList();
        }
        check(files.size() == 49, "the pack ships 49 badges, found " + files.size());

        List<String> missing = new ArrayList<>();
        for (HonourCatalog entry : HonourCatalog.all()) {
            if (!files.contains(entry.textureFile())) missing.add(entry.id());
        }
        check(missing.isEmpty(), "every honour has its badge, missing: " + missing);

        List<String> orphaned = new ArrayList<>();
        for (String file : files) {
            String stem = file.substring(0, file.length() - ".png".length());
            String id = stem.startsWith("honor_") ? stem.substring("honor_".length()) : stem;
            if (HonourCatalog.byId(id) == null) orphaned.add(file);
        }
        check(orphaned.isEmpty(), "no badge lacks an honour entry, orphaned: " + orphaned);

        // The texture key derivation matches the reference exactly.
        check(HonourCatalog.byId("headhunter").textureFile().equals("honor_headhunter.png"),
                "the texture file is `honor_` + id, as the reference builds it");
        // And `KillType.honourFile` agrees with the catalogue-based list.
        check(KillType.honourFile("format_headhunter").equals("honor/honor_headhunter.png"),
                "KillType resolves the same badge");
        check(KillType.honourTitles().size() == 49, "KillType reports 49 titles");
        check(KillType.honourTitles().containsAll(
                        HonourCatalog.all().stream().map(HonourCatalog::id).toList()),
                "KillType's title list comes from the catalogue");
    }

    /** Every honour is named and described in both complete locales. */
    static void translations() throws IOException {
        JsonObject zh = read("zh_cn.json");
        JsonObject en = read("en_us.json");
        List<String> missingZh = new ArrayList<>();
        List<String> missingEn = new ArrayList<>();
        for (HonourCatalog honour : HonourCatalog.all()) {
            if (!zh.has(honour.nameKey())) missingZh.add(honour.nameKey());
            if (!zh.has(honour.descKey())) missingZh.add(honour.descKey());
            if (!en.has(honour.nameKey())) missingEn.add(honour.nameKey());
            if (!en.has(honour.descKey())) missingEn.add(honour.descKey());
        }
        check(missingZh.isEmpty(), "every honour is named and described in zh_cn, missing: " + missingZh);
        check(missingEn.isEmpty(), "every honour is named and described in en_us, missing: " + missingEn);

        // The reference's own wording. Honour names are *titles* ("路霸" for roadhog), not descriptions of the
        // condition -- so checking that the name mentions a vehicle would be asserting the wrong thing about a
        // translation. What matters is that each name is the reference's, non-empty, and not the id echoed back.
        check(zh.get("battlefield_ops.honor.roadhog.name").getAsString().equals("路霸"),
                "roadhog is the reference's 路霸, got "
                        + zh.get("battlefield_ops.honor.roadhog.name").getAsString());
        check(zh.get("battlefield_ops.honor.headhunter.name").getAsString().equals("猎头者"),
                "headhunter is 猎头者");
        check(zh.get("battlefield_ops.honor.ace_pilot.name").getAsString().equals("王牌飞行员"),
                "ace_pilot is 王牌飞行员");
        // A name must be a translation, not the id.
        for (HonourCatalog honour : HonourCatalog.all()) {
            String zhName = zh.get(honour.nameKey()).getAsString();
            check(!zhName.isBlank(), honour.id() + " has a non-blank Chinese name");
            check(!zhName.equals(honour.id()), honour.id() + " is translated, not echoed");
            String desc = zh.get(honour.descKey()).getAsString();
            check(desc.length() > 4, honour.id() + " has a real description, not a stub: " + desc);
        }
        // Names differ between the languages, so a copy-paste is caught.
        int differing = 0;
        for (HonourCatalog honour : HonourCatalog.all()) {
            if (!zh.get(honour.nameKey()).getAsString()
                    .equals(en.get(honour.nameKey()).getAsString())) differing++;
        }
        check(differing == 49, "all 49 names are actually translated, differing: " + differing);
    }

    /** The streak rule: headshots at three earn headhunter, and nothing else does here. */
    static void streakRules() {
        List<HonourCatalog> two = HonourCatalog.streakHonours(KillType.byId("HEADSHOT"), 2);
        check(two.isEmpty(), "two headshots do not earn headhunter");
        List<HonourCatalog> three = HonourCatalog.streakHonours(KillType.byId("HEADSHOT"), 3);
        check(three.size() == 1, "three headshots earn exactly one honour: " + three);
        check(three.get(0).id().equals("headhunter"), "and it is headhunter");
        // More than three still earns it.
        check(!HonourCatalog.streakHonours(KillType.byId("HEADSHOT"), 9).isEmpty(),
                "nine headshots still earn it");
        // A different kind earns nothing: `moving:3` needs a movement signal this mod does not have.
        check(HonourCatalog.streakHonours(KillType.byId("NORMAL"), 9).isEmpty(),
                "a plain kill streak earns no streak honour");
        check(HonourCatalog.streakHonours(null, 9).isEmpty(), "a null kind earns nothing");
        // `warrior` (moving:3) is never claimable from a kind alone, which is the honest limit.
        boolean warriorClaimable = false;
        for (KillType kind : KillType.all()) {
            for (HonourCatalog honour : HonourCatalog.streakHonours(kind, 50)) {
                if (honour.id().equals("warrior")) warriorClaimable = true;
            }
        }
        check(!warriorClaimable, "warrior needs a movement signal, so no kind can earn it alone");
    }

    /** The two vocabularies are separate, and only one id is in both. */
    static void separation() {
        Set<String> honourIds = new HashSet<>();
        for (HonourCatalog honour : HonourCatalog.all()) honourIds.add(honour.id());
        List<String> overlap = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (honourIds.contains(award.id().toLowerCase(java.util.Locale.ROOT))) {
                overlap.add(award.id());
            }
        }
        check(overlap.equals(List.of("AVENGE")),
                "exactly AVENGE is both a bonus and an honour, found " + overlap);
        check(AwardCatalog.size() == 72, "72 bonuses and 49 honours are different-sized tables");
        check(HonourCatalog.size() == 49, "and the honour table is the smaller one");
        // Scores are independent: an honour's score is not the bonus's cap.
        check(HonourCatalog.byId("avenge").score() == 30,
                "avenge the honour is worth 30 points");
        // Their key namespaces differ too, so a lookup cannot silently cross over.
        check(HonourCatalog.byId("avenge").nameKey().startsWith("battlefield_ops.honor."),
                "honour keys live under `honor.`");
        check(AwardCatalog.byId("AVENGE").nameKey().startsWith("battlefield_ops.bonus."),
                "bonus keys live under `bonus.`");
    }

    private static JsonObject read(String locale) throws IOException {
        return JsonParser.parseString(
                Files.readString(ASSETS.resolve("lang").resolve(locale), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
