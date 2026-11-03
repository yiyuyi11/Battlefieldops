package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the award-to-icon mapping and the bitmap fallback.
 *
 * <p>This replaces the test that covered the previous kill-icon layer, whose 8-style catalogue was deleted in
 * favour of the 1.3.3 port. What still exists and still needs checking:
 *
 * <ul>
 *   <li>{@link KillIcons#kindFor} — this mod's award names to the pack's 11 kinds. A wrong mapping draws the
 *       wrong icon, which is a lie about what happened rather than a cosmetic flaw.</li>
 *   <li>{@link KillIcons#bitmapKind} — the small-size fallback. It must always name a glyph {@link FeedIcons}
 *       actually draws, or the fallback is silence.</li>
 *   <li>{@link KillIconFinishes} — the surviving Valorant finish axis.</li>
 * </ul>
 */
public final class KillIconSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
    }

    public static void main(String[] args) {
        awardMapping();
        texturing();
        bitmapFallback();
        finishes();
        System.out.println("KillIconSim: " + checks + " checks passed");
    }

    /** Every award name this mod emits maps to a real pack kind. */
    private static void awardMapping() {
        // The awards the server can actually send. Taken from the `icon` field of the award payloads.
        List<String> emitted = List.of("kill", "headshot", "crit", "assist", "capture", "medic", "heal",
                "rescue", "vehicle", "vehicle_destroy", "vehicle_assist", "spot_assist", "mark", "streak",
                "aerial", "revenge");
        for (String award : emitted) {
            KillType kind = KillIcons.kindFor(award);
            check(kind != null, "award `" + award + "` maps to a kind");
            check(kind.id() != null && !kind.id().isBlank(), "kind for `" + award + "` has an id");
            check(kind.textureKey() != null && !kind.textureKey().isBlank(),
                    "kind for `" + award + "` has a texture key");
        }
        // The informative mappings must be distinct: if a headshot and a plain kill resolved to the same kind,
        // the feed would stop distinguishing the thing players care most about.
        check(!KillIcons.kindFor("headshot").equals(KillIcons.kindFor("kill")),
                "a headshot is not a plain kill");
        check(!KillIcons.kindFor("crit").equals(KillIcons.kindFor("headshot")),
                "a crit is not a headshot");
        check(!KillIcons.kindFor("vehicle").equals(KillIcons.kindFor("kill")),
                "a vehicle kill is not an infantry kill");
        check(!KillIcons.kindFor("assist").equals(KillIcons.kindFor("kill")),
                "an assist is not a kill");
        check(!KillIcons.kindFor("capture").equals(KillIcons.kindFor("kill")),
                "a capture is not a kill");

        // The mappings that are deliberately the same, because the pack has no separate art.
        check(KillIcons.kindFor("vehicle").equals(KillIcons.kindFor("vehicle_destroy")),
                "the two spellings of a vehicle kill agree");
        check(KillIcons.kindFor("spot_assist").equals(KillIcons.kindFor("mark")),
                "a mark and a spot assist are the same event");
        check(KillIcons.kindFor("medic").equals(KillIcons.kindFor("heal")),
                "healing and a medic award share the medic icon");

        // Unknown and absent awards degrade to a plain kill rather than to null: an unrecognised award must
        // still show that something good happened.
        check(KillIcons.kindFor("nonsense").equals(KillIcons.kindFor("kill")), "an unknown award is a kill");
        check(KillIcons.kindFor("").equals(KillIcons.kindFor("kill")), "an empty award is a kill");
        check(KillIcons.kindFor(null).equals(KillIcons.kindFor("kill")), "a null award is a kill");

        // Every mapping names one of the 11 kinds the pack defines.
        List<String> ids = KillType.all().stream().map(KillType::id).toList();
        check(ids.size() == 11, "the pack defines 11 kill types, found " + ids.size());
        for (String award : emitted) {
            check(ids.contains(KillIcons.kindFor(award).id()),
                    "award `" + award + "` maps to one of the 11 kinds");
        }
    }

    /** Each kind's texture chain ends at a file that exists in every family. */
    private static void texturing() {
        // The order matters: the first entry is what a correct pack draws, and the last is the safety net.
        for (KillType kind : KillType.all()) {
            for (String family : List.of("scrolling", "battlefield1", "battlefield6")) {
                List<String> candidates = kind.textureCandidates("00037", family);
                // Not "at least two steps": a kind whose own art *is* the family default (NORMAL everywhere,
                // ASSIST in battlefield1) correctly collapses to one entry after de-duplication. The invariant
                // that matters is a non-empty, repeat-free chain ending at the family's default texture.
                check(!candidates.isEmpty(), kind.id() + "/" + family + " has at least one path");
                check(candidates.stream().distinct().count() == candidates.size(),
                        kind.id() + "/" + family + " has no duplicate lookups: " + candidates);
                String last = candidates.get(candidates.size() - 1);
                check(last.endsWith("_default.png"),
                        kind.id() + "/" + family + " ends at a default texture, ended at " + last);
                // Every entry is a plain png filename, not a path: the caller adds the namespace.
                for (String file : candidates) {
                    check(file.endsWith(".png"), "candidate is a png: " + file);
                    check(!file.contains("/"), "candidate is a bare filename: " + file);
                    check(file.startsWith("killicon_"), "candidate is pack art: " + file);
                }
            }
        }
        // The three kinds with no art in the `scrolling` family must fall back, and the fallback must be the
        // `df` variant rather than the `scrolling` one, because `scrolling` genuinely has no such file.
        for (String missing : List.of("MEDIC", "VEHICLE_DESTROY_ASSIST", "CAPTURE")) {
            List<String> chain = KillType.byId(missing).textureCandidates("00037", "scrolling");
            check(chain.stream().anyMatch(f -> f.startsWith("killicon_df_")),
                    missing + " falls back to the `df` family art: " + chain);
        }
        // A highlighted kind has several families' art and must not fall back at all.
        List<String> headshot = KillType.byId("HEADSHOT").textureCandidates("00037", "battlefield1");
        check(headshot.get(0).contains("battlefield1"),
                "a family that has the art uses it first: " + headshot.get(0));

        // The scrolling family selects art by preset, but only for the presets the pack actually gives their
        // own art to: 00007 draws Battlefield 5 art and 00037 draws Battlefield 6 art. The remainder (including
        // 00001) deliberately fall back to the shared `scrolling` set, because the pack ships no per-preset art
        // for them -- so the interesting assertion is that the *named* presets differ, not that every id does.
        String bf5 = KillType.byId("HEADSHOT").textureCandidates("00007", "scrolling").get(0);
        String bf6 = KillType.byId("HEADSHOT").textureCandidates("00037", "scrolling").get(0);
        String shared = KillType.byId("HEADSHOT").textureCandidates("00001", "scrolling").get(0);
        check(bf5.contains("battlefield5"), "00007 draws Battlefield 5 art: " + bf5);
        check(bf6.contains("battlefield6"), "00037 draws Battlefield 6 art: " + bf6);
        check(shared.contains("scrolling"), "00001 uses the shared scrolling art: " + shared);
        check(!bf5.equals(bf6) && !bf5.equals(shared) && !bf6.equals(shared),
                "the three families genuinely differ: " + bf5 + " / " + bf6 + " / " + shared);
    }

    /** The bitmap fallback always names a glyph that is actually drawn. */
    private static void bitmapFallback() {
        List<String> emitted = List.of("kill", "headshot", "crit", "assist", "capture", "medic", "heal",
                "rescue", "vehicle", "vehicle_destroy", "vehicle_assist", "spot_assist", "mark", "streak",
                "aerial", "revenge");
        for (String award : emitted) {
            String glyph = KillIcons.bitmapKind(award);
            check(glyph != null && !glyph.isBlank(), "award `" + award + "` has a fallback glyph");
            check(FeedIcons.defined(glyph),
                    "the fallback glyph for `" + award + "` (" + glyph + ") is a drawn bitmap");
            check(FeedIcons.width(glyph) > 0, "glyph `" + glyph + "` has a width");
            check(FeedIcons.height(glyph) > 0, "glyph `" + glyph + "` has a height");
        }
        // The fallback is a coarser vocabulary than the texture one: it projects onto the hand-drawn shapes.
        check(KillIcons.bitmapKind("spot_assist").equals("mark"),
                "a spot assist falls back to the mark ring");
        check(KillIcons.bitmapKind("crit").equals("headshot"),
                "a crit falls back to the headshot skull");
        // There is no vehicle bitmap; a burst is the closest 9px read, which is what the previous renderer did.
        check(KillIcons.bitmapKind("vehicle").equals("explosion"),
                "a vehicle kill falls back to the explosion burst");
        check(KillIcons.bitmapKind(null).equals("kill"), "a null award falls back to the kill glyph");
        check(KillIcons.bitmapKind("nonsense").equals("kill"), "an unknown award falls back to the kill glyph");

        // Tints are per-award so the fallback still distinguishes a headshot from a kill at a glance.
        check(KillIcons.tint("headshot") != KillIcons.tint("kill"),
                "the fallback colours a headshot differently from a kill");
        check(KillIcons.tint("vehicle") != KillIcons.tint("kill"),
                "and a vehicle kill differently again");
        check(KillIcons.tint(null) != 0, "a null award still gets a colour");
    }

    /** The survivor of the old layer: the Valorant finish axis. */
    private static void finishes() {
        List<String> finishes = KillIconFinishes.FINISHES;
        check(!finishes.isEmpty(), "the pack ships Valorant finishes");
        // `base` is the unadorned default and must be first, so an unset preference lands on it.
        check(finishes.get(0).equals("base"), "the plain default is first, found " + finishes.get(0));
        check(finishes.size() > 8,
                "every finish the pack ships is selectable, not a hand-kept eight (" + finishes.size() + ")");
        // Cycling visits every finish exactly once and wraps. Starting at `base`, N steps return to `base` and
        // pass through all N entries on the way, so the set of visited names must be the whole list.
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        String current = finishes.get(0);
        for (int i = 0; i < finishes.size(); i++) {
            current = KillIconFinishes.next(current);
            seen.add(current);
        }
        check(seen.size() == finishes.size(),
                "cycling visits every finish, saw " + seen.size() + " of " + finishes.size());
        check(current.equals(finishes.get(0)), "and wraps back to the default");
        // Unknown input lands on the default rather than throwing.
        check(KillIconFinishes.orDefault("nope").equals("base"), "an unknown finish falls back to base");
        check(KillIconFinishes.orDefault(null).equals("base"), "a null finish falls back to base");
        check(!KillIconFinishes.isValid("nope"), "an invented finish is rejected");
        check(KillIconFinishes.isValid("base"), "the default is a real finish");
        // Display names are readable rather than raw stems.
        check(KillIconFinishes.displayName("base").equals("默认"), "the default has a Chinese label");
        check(KillIconFinishes.displayName("gaia_v1").equals("Gaia V1"),
                "a stem renders as words, got " + KillIconFinishes.displayName("gaia_v1"));
        check(!KillIconFinishes.displayName("gaia_v1").contains("_"),
                "a display name has no underscores in it");
        check(!KillIconFinishes.displayName("nope").isBlank(),
                "an unknown finish still renders something");
    }
}
