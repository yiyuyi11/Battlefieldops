package cn.bfops;

import java.util.List;

/**
 * Headless checks for the sound cue table and its coverage of the imported catalogue.
 *
 * <p>The failure mode that matters here is silence. A cue naming an event that does not exist simply never
 * plays, nothing throws, and the only way to notice is to be in a match when a capture point falls and hear
 * nothing. So the central assertions are coverage in both directions: nothing is referenced that does not
 * exist, and nothing exists that nothing references.
 */
public final class SoundBankSim {
    private static int checks;

    /**
     * Sounds played directly by {@code Sounds.get(...)} rather than through the cue table — the bullet-whizz
     * near-miss effects. They are still registered and categorized (SFX), but no {@link SoundBank} cue names
     * them, so the coverage checks must not demand a cue for them.
     */
    private static final List<String> DIRECT_PLAY =
            List.of("whizz.rifle", "whizz.smg", "whizz.sniper", "whizz.rpg");

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        catalogue();
        cueTable();
        coverage();
        categories();
        picking();
        lookup();
        System.out.println("SoundBankSim: " + checks + " checks passed");
    }

    /** The generated catalogue is complete and matches what the mod registers. */
    private static void catalogue() {
        // 120 from the conquest mod (music, loops, voice, sfx) plus 86 kill sounds from the kill-icon mod,
        // plus the 4 bullet-whizz sounds played directly by NearMissEffect rather than through the cue table.
        check(SoundCatalog.ALL.size() == 206 + DIRECT_PLAY.size(),
                "the catalogue holds 206 reference events plus " + DIRECT_PLAY.size() + " bullet-whizz sounds");
        check(SoundCatalog.ALL.stream().filter(n -> n.startsWith("kill.")).count() == 86,
                "86 of them are the kill-icon mod's own sounds");
        check(SoundCatalog.ALL.stream().allMatch(n -> n.startsWith("music.") || n.startsWith("loop.")
                || n.startsWith("voice.") || n.startsWith("sfx.") || n.startsWith("kill.")
                || n.startsWith("whizz.")),
                "every name uses one of the six prefixes");
        check(SoundCatalog.ALL.stream().distinct().count() == SoundCatalog.ALL.size(),
                "no name appears twice");
        check(SoundCatalog.ALL.contains("music.victory"), "music.victory is registered");
        check(SoundCatalog.ALL.contains("sfx.capture_in"), "sfx.capture_in is registered");
        check(SoundCatalog.ALL.contains("voice.capture.objective_taken_a"),
                "the voice callouts are registered, not skipped");
        check(SoundCatalog.ALL.contains("sfx.shelling.incoming_heavyshell_6"),
                "all six heavy-shell variants are registered");
        // The kill-icon sounds keep the pack's own filenames, so they can be traced back to the file.
        check(SoundCatalog.ALL.contains("kill.killsound_bf6"), "the Battlefield 6 kill stinger is registered");
        check(SoundCatalog.ALL.contains("kill.honorkillsound_normal"), "as is the honour stinger");
        check(SoundCatalog.ALL.contains("kill.valorant_primekill_1"),
                "and the Valorant finishes, which is how the finish selector makes a sound");
    }

    /** The cue table itself is well formed. */
    private static void cueTable() {
        check(!SoundBank.cues().isEmpty(), "the cue table is not empty");
        boolean noEmpty = true, noBlanks = true;
        for (String cue : SoundBank.cues()) {
            List<String> variants = SoundBank.variants(cue);
            if (variants.isEmpty()) noEmpty = false;
            for (String name : variants) if (name == null || name.isBlank()) noBlanks = false;
        }
        check(noEmpty, "every cue has at least one event");
        check(noBlanks, "no cue lists a blank event name");
        check(SoundBank.variants("no_such_cue").isEmpty(), "an unknown cue returns an empty list");
        check(SoundBank.variants(null).isEmpty(), "a null cue returns an empty list, not a throw");
        check(SoundBank.pick("no_such_cue", 0) == null, "picking from an unknown cue returns null");
        // The multi-variant cues are the ones the reference deliberately ships several samples for.
        check(SoundBank.variants("shell_incoming_heavy").size() == 6,
                "incoming heavy shells keep all six variants");
        check(SoundBank.variants("voice_objective_taken").size() == 6,
                "the objective-taken callout keeps all six variants");
        check(SoundBank.variants("shell_launch_light").size() == 1,
                "a single-sample cue reports exactly one variant");
    }

    /** Nothing referenced is missing, and nothing imported is unreachable. */
    private static void coverage() {
        List<String> missing = SoundBank.missingFrom(SoundCatalog.ALL);
        check(missing.isEmpty(), "no cue names an event the catalogue lacks" + suffix(missing));
        List<String> unreferenced = new java.util.ArrayList<>(SoundBank.unreferenced(SoundCatalog.ALL));
        unreferenced.removeAll(DIRECT_PLAY);
        check(unreferenced.isEmpty(),
                "every cue-able event is reachable from some cue" + suffix(unreferenced));
        check(SoundBank.referencedEvents() == SoundCatalog.ALL.size() - DIRECT_PLAY.size(),
                "the table references every cue-able event ("
                        + SoundBank.referencedEvents() + " of "
                        + (SoundCatalog.ALL.size() - DIRECT_PLAY.size()) + ")");
    }

    /** The three categories split the catalogue the way the reference's own prefixes do. */
    private static void categories() {
        check(SoundBank.Category.of("music.victory") == SoundBank.Category.MUSIC,
                "music.* is MUSIC");
        check(SoundBank.Category.of("loop.deploy_screen") == SoundBank.Category.MUSIC,
                "loop.* is MUSIC too, so one toggle silences every bed");
        check(SoundBank.Category.of("voice.capture.objective_taken_a") == SoundBank.Category.VOICE,
                "voice.* is VOICE");
        check(SoundBank.Category.of("sfx.marker_spot_fail") == SoundBank.Category.SFX,
                "sfx.* is SFX");
        check(SoundBank.Category.of(null) == SoundBank.Category.SFX,
                "an unknown name defaults to SFX rather than to silence");
        // Every event lands in exactly one bucket, and none falls through. The kill-icon sounds are effects:
        // they are short feedback stingers, so the SFX switch governs them, which is what a player expects
        // when they turn effects down to hear callouts better.
        long music = SoundCatalog.ALL.stream().filter(n -> SoundBank.Category.of(n)
                == SoundBank.Category.MUSIC).count();
        long voice = SoundCatalog.ALL.stream().filter(n -> SoundBank.Category.of(n)
                == SoundBank.Category.VOICE).count();
        long sfx = SoundCatalog.ALL.stream().filter(n -> SoundBank.Category.of(n)
                == SoundBank.Category.SFX).count();
        check(music + voice + sfx == SoundCatalog.ALL.size(),
                "the three categories partition all " + SoundCatalog.ALL.size() + " events");
        check(music == 16, "16 beds (" + music + ")");
        check(voice == 33, "33 callouts (" + voice + ")");
        // 71 conquest effects plus the 86 kill-icon stingers plus the 4 bullet-whizz sounds.
        check(sfx == 157 + DIRECT_PLAY.size(),
                "157 + " + DIRECT_PLAY.size() + " effects: 71 conquest + 86 kill-icon + whizz (" + sfx + ")");
        check(SoundBank.Category.of("kill.killsound_bf6") == SoundBank.Category.SFX,
                "a kill stinger is an effect, governed by the effects switch");
    }

    /** Variant selection walks the list instead of repeating. */
    private static void picking() {
        List<String> heavy = SoundBank.variants("shell_incoming_heavy");
        check(SoundBank.pick("shell_incoming_heavy", 0).equals(heavy.get(0)), "pick(0) is the first");
        check(SoundBank.pick("shell_incoming_heavy", 5).equals(heavy.get(5)), "pick(5) is the sixth");
        check(SoundBank.pick("shell_incoming_heavy", 6).equals(heavy.get(0)),
                "pick wraps at the end of the list");
        check(SoundBank.pick("shell_incoming_heavy", -1).equals(heavy.get(5)),
                "a negative index floors into range instead of throwing");
        check(SoundBank.pick("shell_incoming_heavy", -7).equals(heavy.get(5)),
                "-7 floors to the same slot as -1 with a list of six");
        // The round robin: six consecutive picks must visit six different events.
        SoundCues.reset();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 6; i++) {
            seen.add(SoundBank.pick("shell_incoming_heavy", SoundCues.nextPick("shell_incoming_heavy")));
        }
        check(seen.size() == 6, "six successive picks hear all six variants, none repeated");
        SoundCues.reset();
        check(SoundBank.pick("voice_objective_taken", SoundCues.nextPick("voice_objective_taken"))
                        .equals(SoundBank.variants("voice_objective_taken").get(0)),
                "after a reset the round robin starts from the first variant again");
    }

    /** Sounds resolves names and refuses unknown ones without throwing. */
    private static void lookup() {
        // Registration needs the game's registries to be bootstrapped, which they are not in a headless run,
        // so this deliberately never triggers it: an unknown or unregistered name must return null rather than
        // throwing, because that is what the HUD calls on every hit and every award.
        check(Sounds.get("no.such.event") == null, "an unknown event name resolves to null");
        check(Sounds.get(null) == null, "a null name resolves to null");
        check(Sounds.names().size() == Sounds.size(), "names() and size() agree");
        check(Sounds.size() == SoundCatalog.ALL.size(),
                "one holder per catalogue entry (" + Sounds.size() + ")");
        // The cue table and the registry are generated from the same file, so a name in one is in the other.
        check(Sounds.names().containsAll(SoundCatalog.ALL),
                "every catalogue name is in the registerable set");
        check(Sounds.registered() == 0,
                "nothing is registered before the mod constructor runs (the bootstrapping guard working)");
    }

    private static String suffix(List<String> names) {
        return names.isEmpty() ? "" : " -> " + names;
    }
}
