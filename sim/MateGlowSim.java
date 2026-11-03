package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Headless checks for the always-on friendly halo and the audio state cycle.
 *
 * <p>Both subjects are small and both fail quietly. A halo that does not appear looks like "the map is quiet
 * today"; an audio cycle that skips a state leaves a player unable to reach silence without editing a file. So
 * what is checked here is the decision logic, which is the whole of either feature that can be wrong without
 * throwing: who counts as a teammate, whether the map rule can switch the halo off, and which state the audio key
 * lands on for every starting point.
 *
 * <p>The drawing itself is checked only for its invariants — the ring geometry — because it needs a
 * {@code GuiGraphics} that a headless run cannot construct.
 */
public final class MateGlowSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        teammateSet();
        ruleHonoured();
        geometry();
        audioCycle();
        System.out.println("MateGlowSim: " + checks + " checks passed");
    }

    /** Exactly the living, non-hostile players of the viewer's own match are teammates. */
    private static void teammateSet() {
        MateGlow.reset();
        check(!MateGlow.isMate("nobody"), "an empty cache knows nobody");

        JsonObject state = new JsonObject();
        state.addProperty("inMatch", true);
        JsonArray mates = new JsonArray();
        mates.add(row("Alice", "uuid-a", false, true));
        mates.add(row("Bob", "uuid-b", false, false));      // dead
        mates.add(row("Cara", "uuid-c", true, true));       // hostile
        state.add("mates", mates);
        MateGlow.remember(state);

        check(MateGlow.isMate("Alice"), "a living teammate is a mate");
        check(MateGlow.isMate("uuid-a"), "and is findable by uuid, in case the name is ambiguous");
        check(!MateGlow.isMate("Bob"), "a dead teammate is not haloed: their body is not a squad marker");
        check(!MateGlow.isMate("Cara"), "a hostile is never haloed, whatever the team table says");
        check(!MateGlow.isMate("uuid-c"), "and not by uuid either");
        check(!MateGlow.isMate(null), "a null name is not a mate");

        // A new snapshot replaces the set rather than accumulating it: a player who changes team, or dies, must
        // lose the halo on the very next tick.
        JsonObject next = new JsonObject();
        next.addProperty("inMatch", true);
        JsonArray nextMates = new JsonArray();
        nextMates.add(row("Cara", "uuid-c", false, true));
        next.add("mates", nextMates);
        MateGlow.remember(next);
        check(MateGlow.isMate("Cara"), "a player who joined your team becomes a mate");
        check(!MateGlow.isMate("Alice"), "and one who left it stops being one");

        // Snapshots without a mates array must empty the set, not keep the previous one.
        MateGlow.remember(new JsonObject());
        check(!MateGlow.isMate("Cara"), "a snapshot with no mates clears the set");

        MateGlow.reset();
        check(!MateGlow.isMate("Alice"), "reset() forgets everyone");
    }

    /** The map rule can switch the halo off, and its absence means "on". */
    private static void ruleHonoured() {
        check(MateGlow.allowed(snapshot(true)) == true, "the halo is on by default");
        check(MateGlow.allowed(snapshot(false)) == false, "the map rule can switch it off");
        // A snapshot from a server that does not send the rule must not silently disable the feature: this is
        // the difference between "off" and "unspecified", and confusing them hides a working feature.
        JsonObject noRule = new JsonObject();
        noRule.addProperty("inMatch", true);
        noRule.add("arena", new JsonObject());
        check(MateGlow.allowed(noRule) == true, "an absent rule means on, not off");
        JsonObject noArena = new JsonObject();
        noArena.addProperty("inMatch", true);
        check(MateGlow.allowed(noArena) == true, "an absent arena means on");
        check(MateGlow.allowed(null) == false, "no snapshot at all means nothing is drawn");
        JsonObject outOfMatch = new JsonObject();
        outOfMatch.addProperty("inMatch", false);
        check(MateGlow.allowed(outOfMatch) == false,
                "the halo is a match feature, so it is off outside one");
        // A non-object rules field is malformed, and the safe reading is the default rather than a crash.
        JsonObject malformed = new JsonObject();
        malformed.addProperty("inMatch", true);
        JsonObject arena = new JsonObject();
        arena.addProperty("rules", 7);
        malformed.add("arena", arena);
        check(MateGlow.allowed(malformed) == true, "a malformed rules field falls back to on");
    }

    /** The ring geometry holds for every radius, including the degenerate ones. */
    private static void geometry() {
        for (int radius = 1; radius <= 20; radius++) {
            int[][] bands = MateGlow.ringBands(radius);
            boolean inside = true, holeFits = true;
            for (int[] band : bands) {
                int dy = band[0], outer = band[1], hole = band[2];
                if (Math.abs(dy) > radius) inside = false;
                // The run must not exceed the circle's own chord, or the halo leaks outside the shape.
                int chord = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
                if (outer > chord) inside = false;
                if (outer < 0 || hole < 0) inside = false;
                // The hole can never be wider than the band it is punched out of.
                if (hole > outer) holeFits = false;
            }
            check(inside, "ring r=" + radius + " stays inside its own radius");
            check(holeFits, "ring r=" + radius + " keeps its hole inside the band");
            check(bands.length == radius * 2 + 1, "ring r=" + radius + " covers every row");
        }
        check(MateGlow.ringBands(0).length >= 1, "a zero radius still produces a drawable row");
        check(MateGlow.ringBands(-5).length >= 1, "a negative radius is clamped, not crashed");
        // The ring has a hole in the middle: the core is drawn separately, so the two must not double-draw.
        int[][] mid = MateGlow.ringBands(9);
        int holeAtCentre = -1;
        for (int[] band : mid) if (band[0] == 0) holeAtCentre = band[2];
        check(holeAtCentre > 0, "the middle row has a hole, leaving room for the core");
        // And the topmost row is solid: the chord there is too short to punch anything out of.
        check(MateGlow.ringBands(9)[0][2] == 0, "the topmost row is solid rather than hollow");
        check(MateGlow.PULSE_MS >= 1_000L, "the pulse is slow, not a flicker");
        int a = MateGlow.pulseAlpha(0), b = MateGlow.pulseAlpha(MateGlow.PULSE_MS / 4);
        check(a >= 0 && a <= 0xFF && b >= 0 && b <= 0xFF, "the pulse alpha stays in byte range");
        check(a != b, "and actually varies over its period");
        check(MateGlow.pulseAlpha(0) == MateGlow.pulseAlpha(MateGlow.PULSE_MS),
                "and returns to where it started after one period");
        // A negative timestamp (a clock that moved) must not produce a negative alpha.
        check(MateGlow.pulseAlpha(-1) >= 0, "a negative time still yields a valid alpha");
    }

    /** The audio key visits every state and always returns to fully on. */
    private static void audioCycle() {
        // cycleAudio writes the player's config, so this drives the pure state machine instead.
        String all = ClientPrefs.AudioState.ALL;
        String sfxOnly = ClientPrefs.AudioState.next(all);
        String silent = ClientPrefs.AudioState.next(sfxOnly);
        check(sfxOnly.equals(ClientPrefs.AudioState.SFX_ONLY),
                "fully on goes to effects only, dropping music and voice");
        check(silent.equals(ClientPrefs.AudioState.SILENT), "effects only goes to silent");
        check(ClientPrefs.AudioState.next(silent).equals(all), "silent goes back to fully on");
        check(ClientPrefs.AudioState.next(all).equals(sfxOnly), "and the cycle repeats");
        // Silence must always be reachable from every state in at most two presses, or a player who edited the
        // file into an unusual combination cannot get quiet quickly. A state that is already silent counts as
        // reachable in zero presses -- requiring a press to *reach* silence from silence would be nonsense.
        for (String state : java.util.List.of(all, sfxOnly, silent, ClientPrefs.AudioState.VOICE_MUSIC)) {
            String one = ClientPrefs.AudioState.next(state);
            String two = ClientPrefs.AudioState.next(one);
            check(state.equals(ClientPrefs.AudioState.SILENT)
                            || one.equals(ClientPrefs.AudioState.SILENT)
                            || two.equals(ClientPrefs.AudioState.SILENT),
                    "silence is reachable from " + state + " within two presses");
        }
        // And no state is a dead end: every state has a successor, so one key always gets you out.
        for (String state : java.util.List.of(all, sfxOnly, silent, ClientPrefs.AudioState.VOICE_MUSIC)) {
            check(ClientPrefs.AudioState.next(state) != null,
                    state + " has a successor, so the key is never a dead end");
        }
        // An unexpected combination must resolve to a defined state rather than getting stuck.
        check(ClientPrefs.AudioState.next("NONSENSE").equals(all),
                "an unknown state returns to fully on, which is the safe recovery");
        check(ClientPrefs.AudioState.next(null).equals(all), "a null state does too");
        // The names are what the action bar shows, so they have to be non-empty for every state.
        for (String state : java.util.List.of(all, sfxOnly, silent, ClientPrefs.AudioState.VOICE_MUSIC)) {
            check(!ClientPrefs.AudioState.describe(state).isBlank(),
                    state + " has something to say on the action bar");
        }
        check(ClientPrefs.AudioState.of(true, true, true).equals(all), "all three switches means all on");
        check(ClientPrefs.AudioState.of(true, false, false).equals(sfxOnly),
                "effects alone is SFX_ONLY");
        check(ClientPrefs.AudioState.of(false, false, false).equals(silent), "none at all is SILENT");
        check(ClientPrefs.AudioState.of(false, true, true).equals(ClientPrefs.AudioState.VOICE_MUSIC),
                "voice and music without effects is its own state");
        // The four named states are exactly the four the classifier can return, and nothing else.
        java.util.Set<String> reachable = new java.util.HashSet<>();
        for (int mask = 0; mask < 8; mask++) {
            reachable.add(ClientPrefs.AudioState.of((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0));
        }
        check(reachable.size() == 4, "eight switch combinations collapse to four named states");
        check(reachable.contains(all) && reachable.contains(sfxOnly) && reachable.contains(silent)
                && reachable.contains(ClientPrefs.AudioState.VOICE_MUSIC),
                "and they are the four the table names");
    }

    // --- helpers -------------------------------------------------------------------------------

    private static JsonObject row(String name, String uuid, boolean hostile, boolean alive) {
        JsonObject one = new JsonObject();
        one.addProperty("name", name);
        one.addProperty("uuid", uuid);
        one.addProperty("hostile", hostile);
        one.addProperty("alive", alive);
        return one;
    }

    private static JsonObject snapshot(boolean glowTeammates) {
        JsonObject state = new JsonObject();
        state.addProperty("inMatch", true);
        JsonObject arena = new JsonObject();
        JsonObject rules = new JsonObject();
        rules.addProperty("glowTeammates", glowTeammates);
        arena.add("rules", rules);
        state.add("arena", arena);
        return state;
    }
}
