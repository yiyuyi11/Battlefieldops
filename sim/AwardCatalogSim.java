package cn.bfops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the award catalogue against the pack's real badge art and the reference's own bonus list.
 *
 * <p>Two vocabularies live in this area and they are easy to confuse, so the test states the distinction rather
 * than assuming it:
 *
 * <ul>
 *   <li>72 <em>scoring awards</em> ({@code HEADSHOT}, {@code SQUAD_BEACON_DEPLOY}, …) — what happened.</li>
 *   <li>49 <em>honour titles</em> ({@code honor_assault}, {@code honor_pathfinder}, …) — the badge art, keyed by
 *       {@code format_*} entries in the pack's {@code formats/*.json}.</li>
 * </ul>
 *
 * <p>A lookup that mixes the two never resolves, and an unresolved badge draws nothing, which looks exactly
 * like "the feature is off" rather than "the filename is wrong".
 */
public final class AwardCatalogSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws IOException {
        catalogueShape();
        badgeArt();
        scoringRules();
        killTypeProjection();
        declaredFields();
        System.out.println("AwardCatalogSim: " + checks + " checks passed");
    }

    /** The table's own invariants: ids and types unique, contiguous, and ordered. */
    static void catalogueShape() {
        List<AwardCatalog.Award> all = AwardCatalog.all();
        check(all.size() == 72, "the reference defines 72 bonuses, found " + all.size());
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<Integer> types = new java.util.HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            AwardCatalog.Award award = all.get(i);
            check(ids.add(award.id()), "award id is unique: " + award.id());
            check(types.add(award.type()), "award type is unique: " + award.type());
            check(award.type() == i, "awards are in type order at index " + i + ", found " + award.type());
            check(award.nameKey() != null && award.nameKey().contains(award.id()),
                    "the name key names its award: " + award.id());
            check(award.descKey() != null && award.descKey().contains(award.id()),
                    "the description key names its award: " + award.id());
            check(award.merge() != null, "award declares a merge behaviour: " + award.id());
            check(!award.nameKey().isBlank(), "award has a label key");
        }
        check(AwardCatalog.size() == 72, "size() agrees");

        // The reference's own first eleven ids, to catch a transcription slip in the common cases.
        String[] firstEleven = {"DAMAGE", "KILL", "EXPLOSION", "HEADSHOT", "CRIT", "KILL_EXPLOSION",
                "KILL_HEADSHOT", "KILL_CRIT", "KILL_COMBO", "KILL_LONG_DISTANCE", "KILL_INVISIBLE"};
        for (int i = 0; i < firstEleven.length; i++) {
            check(all.get(i).id().equals(firstEleven[i]),
                    "award " + i + " is " + firstEleven[i] + ", found " + all.get(i).id());
        }
        // The last eleven, likewise -- a table this long is easy to truncate.
        String[] lastFour = {"GROUND_SENSOR_SCAN", "RUSH_BOMB_DEFUSED", "RUSH_BOMB_PLANTED",
                "RUSH_OBJECTIVE_DESTROYED"};
        for (int i = 0; i < lastFour.length; i++) {
            check(all.get(all.size() - lastFour.length + i).id().equals(lastFour[i]),
                    "award near the end is " + lastFour[i]);
        }

        check(AwardCatalog.byId("HEADSHOT") != null, "byId finds a known award");
        check(AwardCatalog.byId("headshot") == null, "ids are case-sensitive");
        check(AwardCatalog.byId(null) == null, "a null id finds nothing");
        check(AwardCatalog.byId("NOPE") == null, "an unknown id finds nothing");
        check(AwardCatalog.byType(3).id().equals("HEADSHOT"), "byType 3 is HEADSHOT");
        check(AwardCatalog.byType(999) == null, "an unknown type finds nothing");
    }

    /**
     * The 49 honour badges on disk must be exactly the 49 {@code format_*} entries minus {@code format_best}.
     */
    static void badgeArt() throws IOException {
        Path honor = Path.of("src", "main", "resources", "assets", "battlefield_ops", "icons", "honor");
        check(Files.isDirectory(honor), "the honour art directory exists");
        List<String> files;
        try (var stream = Files.list(honor)) {
            files = stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".png")).sorted().toList();
        }
        check(files.size() == 49, "the pack ships 49 honour badges, found " + files.size());

        // Every title the code lists has a file, and every file the pack ships is listed.
        List<String> titles = KillType.honourTitles();
        check(titles.size() == 49, "the code lists 49 titles, found " + titles.size());
        List<String> missing = new ArrayList<>();
        for (String title : titles) {
            if (!files.contains("honor_" + title + ".png")) missing.add(title);
        }
        check(missing.isEmpty(), "every listed title has art, missing: " + missing);
        List<String> unlisted = new ArrayList<>();
        for (String file : files) {
            String title = file.substring("honor_".length(), file.length() - ".png".length());
            if (!titles.contains(title)) unlisted.add(title);
        }
        check(unlisted.isEmpty(), "every shipped badge is listed, unlisted: " + unlisted);

        // The resolver agrees with the filesystem, and refuses the one entry that is a label, not a badge.
        for (String title : titles) {
            String file = KillType.honourFile("format_" + title);
            check(file != null && file.equals("honor/honor_" + title + ".png"),
                    "honourFile resolves " + title + ", got " + file);
            check(files.contains("honor_" + title + ".png"), "and the file exists");
        }
        check(KillType.honourFile("format_best") == null,
                "format_best is the 'new best' label and has no badge art");
        check(KillType.honourFile("assault") != null, "the format_ prefix is optional");
        check(KillType.honourFile(null) == null, "a null format key resolves to nothing");
        check(KillType.honourFile("  ") == null, "a blank format key resolves to nothing");

        // The two vocabularies are mostly disjoint, which is what makes mixing them fail loudly rather than
        // silently. Exactly one id is spelled the same in both lists -- AVENGE/avenge -- and for that one the
        // badge genuinely is the right art. Asserting the exact count is stronger than asserting "mostly":
        // it fails if a future edit adds an award that collides, which is the case worth knowing about.
        List<String> overlap = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (titles.contains(award.id().toLowerCase())) overlap.add(award.id());
        }
        check(overlap.equals(List.of("AVENGE")),
                "exactly AVENGE is both an award and an honour title, found " + overlap);
        // Which means badgeFor resolves for that one and returns null for everything else.
        int withBadge = 0;
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            String badge = AwardCatalog.badgeFor(award.id());
            if (badge == null) continue;
            withBadge++;
            check(titles.contains(award.id().toLowerCase()),
                    "an award resolves a badge only when its id is also a title: " + award.id());
        }
        check(withBadge == overlap.size(),
                "exactly the overlapping ids resolve a badge, found " + withBadge);
        check(withBadge < AwardCatalog.size(), "most scoring awards have no badge of their own");
        check(AwardCatalog.badgeFor("NOPE") == null, "an unknown award has no badge");
        check(AwardCatalog.badgeFor("AVENGE").equals("honor/honor_avenge.png"),
                "AVENGE is both an award and an honour title, and resolves to its badge");
    }

    /** Scoring: per-award cap, then the global maximum, and never NaN. */
    static void scoringRules() {
        // An uncapped award passes its raw score through.
        check(AwardCatalog.resolveScore("KILL", 25f, 0) == 25f, "an uncapped award keeps its score");
        // The global maximum clamps everything.
        check(AwardCatalog.resolveScore("KILL", 5_000f, 100) == 100f, "the global max clamps");
        check(AwardCatalog.resolveScore("KILL", 50f, 100) == 50f, "below the max is untouched");
        // A zero global max means "no limit" rather than "everything is zero".
        check(AwardCatalog.resolveScore("KILL", 5_000f, 0) == 5_000f, "a zero max means uncapped");
        // Negative scores are legal (penalties) and survive both clamps.
        check(AwardCatalog.resolveScore("KILL", -10f, 100) == -10f, "negative scores pass through");
        check(AwardCatalog.resolveScore("KILL", -10f, 100) < 0f, "and stay negative");
        // NaN must not escape: it would poison a running total.
        check(AwardCatalog.resolveScore("KILL", Float.NaN, 100) == 0f, "NaN becomes zero");
        check(!Float.isNaN(AwardCatalog.resolveScore("KILL", Float.NaN, 0)), "NaN is never returned");
        // An unknown award is still scored sensibly rather than throwing.
        check(AwardCatalog.resolveScore("NOPE", 42f, 100) == 42f, "an unknown award still scores");
        check(AwardCatalog.resolveScore(null, 7f, 100) == 7f, "a null award still scores");
        // Infinity clamps rather than escaping.
        check(AwardCatalog.resolveScore("KILL", Float.POSITIVE_INFINITY, 100) == 100f,
                "positive infinity clamps to the max");
    }

    /** Every award that borrows art must name a kind the icon renderer actually knows. */
    static void killTypeProjection() {
        List<AwardCatalog.Award> feed = AwardCatalog.feedAwards();
        check(!feed.isEmpty(), "some awards appear in the kill feed");

        // The reference gives only two bonuses an iconKillType, and two more a fixed feed payload.
        List<String> iconAwards = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.iconKillType() >= 0) iconAwards.add(award.id());
        }
        check(iconAwards.equals(List.of("REVIVE", "VEHICLE_DESTROY_ASSIST")),
                "only REVIVE and VEHICLE_DESTROY_ASSIST borrow a kill icon, found " + iconAwards);
        check(feed.size() == 4, "four awards produce a feed line, found " + feed.size());

        // The two borrowed kinds are MEDIC (8) and VEHICLE_DESTROY_ASSIST (7), per the reference's constants.
        check(AwardCatalog.byId("REVIVE").iconKind() == KillType.byType(8),
                "REVIVE borrows MEDIC's art");
        check(AwardCatalog.byId("REVIVE").iconKind().id().equals("MEDIC"),
                "and MEDIC is type 8, got " + AwardCatalog.byId("REVIVE").iconKind().id());
        check(AwardCatalog.byId("VEHICLE_DESTROY_ASSIST").iconKind().id().equals("VEHICLE_DESTROY_ASSIST"),
                "VEHICLE_DESTROY_ASSIST borrows its own kind");
        for (AwardCatalog.Award award : feed) {
            if (award.iconKillType() >= 0) {
                check(award.iconKind() != null,
                        award.id() + " resolves a real kill type from " + award.iconKillType());
            }
        }

        // The two fixed payloads are what the rush bomb events render instead of a victim name.
        check("rush_bomb_defused".equals(AwardCatalog.byId("RUSH_BOMB_DEFUSED").killFeedPayload()),
                "RUSH_BOMB_DEFUSED carries its own feed payload");
        check("rush_bomb_planted".equals(AwardCatalog.byId("RUSH_BOMB_PLANTED").killFeedPayload()),
                "RUSH_BOMB_PLANTED carries its own feed payload");

        // Awards without an icon are score feedback only, and must not be in the feed.
        check(AwardCatalog.byId("DAMAGE").iconKind() == null, "damage is score feedback, not a feed icon");
        check(AwardCatalog.byId("DAMAGE").killFeedPayload() == null, "and carries no feed payload");
        check(AwardCatalog.byId("KILL").iconKind() == null,
                "a plain kill has no iconKillType either -- it IS the kill line");

        // Merge behaviour: the reference has two values and only one award deviates.
        int byCombo = 0;
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.merge() == AwardCatalog.Merge.BY_COMBO) byCombo++;
        }
        check(byCombo == 1, "exactly one award merges by combo, found " + byCombo);
        check(AwardCatalog.byId("KILL_COMBO").merge() == AwardCatalog.Merge.BY_COMBO,
                "and it is KILL_COMBO");
        check(AwardCatalog.byId("DAMAGE").merge() == AwardCatalog.Merge.BY_TYPE_EXTRA,
                "everything else groups by type and payload");
        check(AwardCatalog.byId("BLOODTHIRSTY").merge() == AwardCatalog.Merge.BY_TYPE_EXTRA,
                "streak awards use the default merge too -- there is no MAX in the reference");
    }

    /** The score-expression, factor, stat and priority fields, which the earlier revision left blank. */
    static void declaredFields() {
        // Every award names a default expression, and it parses as a number.
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            check(award.expression() != null && !award.expression().isBlank(),
                    award.id() + " has a score expression");
            try {
                float value = Float.parseFloat(award.expression());
                check(value > 0f, award.id() + " expression is positive, got " + value);
            } catch (NumberFormatException ex) {
                throw new AssertionError(award.id() + " expression is numeric: " + award.expression());
            }
            check(award.factor() != null, award.id() + " declares a factor");
            check(award.stat() != null, award.id() + " declares a stat category");
        }

        // The reference's own values for the handful that matter.
        check(AwardCatalog.byId("KILL").expression().equals("5"), "a kill is worth 5");
        check(AwardCatalog.byId("KILL_COMBO").expression().equals("8"), "a combo is worth 8");
        check(AwardCatalog.byId("RUSH_OBJECTIVE_DESTROYED").expression().equals("500"),
                "an objective kill is worth 500, the highest");
        check(AwardCatalog.byId("REVIVE").expression().equals("100"), "a revive is worth 100");
        check(AwardCatalog.byId("HIT_VEHICLE_ARMOR").expression().equals("1.5"),
                "vehicle armour is the one fractional expression");
        check(AwardCatalog.byId("DESTROY_VEHICLE").expression().equals("1.5"),
                "and vehicle destruction too");

        // Factors: 12 of 72 set one, and DAMAGE is the common case.
        int withFactor = 0;
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.factor() != AwardCatalog.Factor.NONE) withFactor++;
        }
        check(withFactor == 12, "twelve awards scale by a factor, found " + withFactor);
        check(AwardCatalog.byId("DAMAGE").factor() == AwardCatalog.Factor.DAMAGE,
                "damage scales by damage");
        check(AwardCatalog.byId("KILL_LONG_DISTANCE").factor() == AwardCatalog.Factor.DISTANCE,
                "long distance scales by range");
        check(AwardCatalog.byId("KILL_COMBO").factor() == AwardCatalog.Factor.COMBO,
                "a combo scales by combo length");
        check(AwardCatalog.byId("ONE_BULLET_MULTI_KILL").factor() == AwardCatalog.Factor.COUNT,
                "a multi-kill scales by count");
        check(AwardCatalog.byId("DESTROY_VEHICLE").factor() == AwardCatalog.Factor.SCORE,
                "vehicle destruction scales by the score it already computed");
        check(AwardCatalog.byId("INTERRUPTED_STREAK").factor() == AwardCatalog.Factor.STREAK,
                "an interrupted streak scales by the streak it broke");

        // Only one award is capped, and only one ships disabled.
        List<String> capped = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.scoreCap() > 0f) capped.add(award.id());
        }
        check(capped.equals(List.of("VALUE_TARGET_DESTROYED")),
                "only VALUE_TARGET_DESTROYED is capped, found " + capped);
        check(AwardCatalog.byId("VALUE_TARGET_DESTROYED").scoreCap() == 2000f, "at 2000");

        List<String> disabled = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.disabledByDefault()) disabled.add(award.id());
        }
        check(disabled.equals(List.of("DESTROY_BLOCK")),
                "only DESTROY_BLOCK ships switched off, found " + disabled);

        // The six priority kills, in the reference's order.
        List<String> priority = new ArrayList<>();
        for (AwardCatalog.Award award : AwardCatalog.priorityKills()) priority.add(award.id());
        check(priority.equals(List.of("KILL", "KILL_EXPLOSION", "KILL_HEADSHOT", "KILL_CRIT",
                        "KILL_COMBO", "KILL_LONG_DISTANCE")),
                "the six priority kills are the base kill and its refinements, found " + priority);

        // Stat categories: four damage, one assist, one revive.
        check(AwardCatalog.byId("DAMAGE").stat() == AwardCatalog.Stat.DAMAGE, "damage counts as damage");
        check(AwardCatalog.byId("ASSIST").stat() == AwardCatalog.Stat.ASSIST, "an assist counts as an assist");
        check(AwardCatalog.byId("REVIVE").stat() == AwardCatalog.Stat.REVIVE, "a revive counts as a revive");
        int statted = 0;
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (award.stat() != AwardCatalog.Stat.NONE) statted++;
        }
        check(statted == 6, "six awards feed a statistic, found " + statted);

        // Only KILL_COMBO declares streak subtitles, and it declares 2..8.
        check(AwardCatalog.byId("KILL_COMBO").streakSubtitles().equals(List.of(2, 3, 4, 5, 6, 7, 8)),
                "a combo has subtitles for 2 through 8");
        for (AwardCatalog.Award award : AwardCatalog.all()) {
            if (!award.id().equals("KILL_COMBO")) {
                check(award.streakSubtitles().isEmpty(),
                        award.id() + " declares no streak subtitles");
            }
        }

        // The config key the pack uses is derived, not stored.
        check(AwardCatalog.byId("KILL_LONG_DISTANCE").formatConfigKey().equals("format_kill_long_distance"),
                "the format config key is lowercased with a prefix");
    }
}
