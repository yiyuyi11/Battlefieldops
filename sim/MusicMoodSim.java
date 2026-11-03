package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Headless checks for the music director's mood selection.
 *
 * <p>The bed is chosen from the match state, and the state is not a single number: whether the player is alive,
 * who is winning, whether the match is over, whether they are standing on a point. The order those are tested
 * in is the whole design — a final segment that is also being lost must play the defeat bed, not the generic
 * attack one — so each rule is checked both alone and in the presence of a more specific one.
 *
 * <p>Playback itself cannot be checked here (it needs a sound engine), so what is checked is exactly the part
 * that decides <em>what</em> would play. The decision is where a bug would be silent: a wrong bed sounds
 * plausible and nobody reports it.
 */
public final class MusicMoodSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        outOfMatch();
        deployAndDeath();
        results();
        finale();
        objective();
        volume();
        dwell();
        System.out.println("MusicMoodSim: " + checks + " checks passed");
    }

    private static void outOfMatch() {
        check(MusicDirector.Mood.bed(null).isEmpty(), "a null snapshot asks for no music");
        check(MusicDirector.Mood.bed(new JsonObject()).isEmpty(),
                "an empty snapshot asks for no music rather than guessing");
        JsonObject plain = new JsonObject();
        plain.addProperty("inMatch", false);
        check(MusicDirector.Mood.bed(plain).isEmpty(),
                "outside a match with no room there is no bed");
        JsonObject lobby = new JsonObject();
        lobby.addProperty("inMatch", false);
        JsonObject lobbyMe = new JsonObject();
        lobbyMe.addProperty("map", "dm");
        lobby.add("me", lobbyMe);
        check(MusicDirector.Mood.bed(lobby).equals("loop_queue"),
                "in a room but not in the match, the queue bed plays");
    }

    private static void deployAndDeath() {
        JsonObject alive = state(0, 500, 500, true);
        check(MusicDirector.Mood.bed(alive).isEmpty(),
                "a normal mid-match player hears no continuous bed");
        JsonObject dead = state(0, 500, 500, false);
        check(MusicDirector.Mood.bed(dead).equals("loop_deploy"),
                "dead or deploying, the deploy bed plays");
        // The deploy bed outranks ticket pressure: a player choosing where to spawn is not "retreating".
        JsonObject deadAndLosing = state(0, 10, 990, false);
        check(MusicDirector.Mood.bed(deadAndLosing).equals("loop_deploy"),
                "the deploy bed outranks the desperate bed");
    }

    private static void results() {
        // The result field names who won, not whether the viewer did.
        JsonObject won = state(0, 500, 0, true);
        won.addProperty("result", "机械轴心胜利");
        check(MusicDirector.Mood.bed(won).equals("music_victory"), "blue winning plays blue's victory music");
        JsonObject lost = state(0, 0, 500, true);
        lost.addProperty("result", "格雷同盟胜利");
        check(MusicDirector.Mood.bed(lost).equals("music_defeat"),
                "and the same result plays red's defeat music for blue");
        // The other side reads the same string the other way round.
        JsonObject redWon = state(1, 500, 0, true);
        redWon.addProperty("result", "格雷同盟胜利");
        check(MusicDirector.Mood.bed(redWon).equals("music_victory"),
                "red winning plays red's victory music");
        JsonObject redLost = state(1, 0, 500, true);
        redLost.addProperty("result", "机械轴心胜利");
        check(MusicDirector.Mood.bed(redLost).equals("music_defeat"),
                "and blue winning plays red's defeat music");
        // A result outranks everything else, including zero tickets.
        check(MusicDirector.Mood.bed(lost).equals("music_defeat"),
                "the result outranks the desperate bed at zero tickets");
        JsonObject drawn = state(0, 0, 0, true);
        drawn.addProperty("result", "平局");
        check(MusicDirector.Mood.bed(drawn).isEmpty(),
                "a draw falls through to silence, not a victory or defeat");
        check(MusicDirector.Mood.resultWon(drawn, drawn.getAsJsonObject("me")) == null,
                "and resultWon reports no outcome for a draw");
        // A spectator is on nobody's side.
        JsonObject spectator = state(-1, 500, 500, true);
        spectator.addProperty("result", "机械轴心胜利");
        check(MusicDirector.Mood.resultWon(spectator, spectator.getAsJsonObject("me")) == null,
                "a spectator gets no outcome either way");
        check(MusicDirector.Mood.resultWon(new JsonObject(), new JsonObject()) == null,
                "an empty result reads as undecided, not as a loss");
        check(MusicDirector.Mood.resultWon(drawn, new JsonObject()) == null,
                "an unreadable result reads as undecided");
    }

    private static void finale() {
        // Near the end the music comes back, event-driven: about-to-lose, about-to-win, or the last stage.
        check(matchBed(0, 150, 850, 1000, "CONQUEST").equals("loop_desperate"),
                "own pool at 15% plays the desperate bed");
        check(matchBed(0, 800, 150, 1000, "CONQUEST").equals("loop_last_objective_victory"),
                "enemy pool at 15% while ahead plays the winning last-objective bed");
        check(matchBed(0, 500, 500, 1000, "CONQUEST").isEmpty(),
                "two healthy pools play no finale");
        check(deathmatchBed(0, 9, 5, 10).equals("loop_last_objective_victory"),
                "near the kill goal while ahead plays the winning finale");
        check(deathmatchBed(0, 5, 9, 10).equals("loop_desperate"),
                "an enemy near the kill goal while behind plays the desperate bed");
        check(breakthroughBed(0, 300, 700, 1, 1).equals("loop_final_defeat"),
                "the last breakthrough stage plays the losing final-segment bed");
        // ticketShare still reports its own cap-relative share.
        JsonObject zero = state(0, 0, 0, true);
        check(MusicDirector.Mood.ticketShare(zero, zero.getAsJsonObject("me")) == -1,
                "a zero total reports an unknown share, not a division by zero");
        JsonObject malformed = state(0, 0, 0, true);
        malformed.add("tickets", new JsonArray());
        check(MusicDirector.Mood.ticketShare(malformed, malformed.getAsJsonObject("me")) == -1,
                "a one-element tickets array reports an unknown share");
    }

    private static String matchBed(int team, int own, int other, int cap, String mode) {
        return MusicDirector.Mood.bed(match(team, own, other, cap, mode));
    }

    private static JsonObject match(int team, int own, int other, int cap, String mode) {
        JsonObject s = state(team, own, other, true);
        JsonObject a = new JsonObject();
        a.addProperty("tickets", cap);
        a.addProperty("mode", mode);
        a.addProperty("targetKills", 100);
        s.add("arena", a);
        return s;
    }

    private static String deathmatchBed(int team, int ownScore, int otherScore, int goal) {
        JsonObject s = state(team, 500, 500, true);
        JsonObject a = new JsonObject();
        a.addProperty("tickets", 1000);
        a.addProperty("mode", "DEATHMATCH");
        a.addProperty("targetKills", goal);
        s.add("arena", a);
        JsonArray scores = new JsonArray();
        if (team == 1) { scores.add(otherScore); scores.add(ownScore); } else { scores.add(ownScore); scores.add(otherScore); }
        s.add("teamScores", scores);
        return MusicDirector.Mood.bed(s);
    }

    private static String breakthroughBed(int team, int own, int other, int stage, int stages) {
        JsonObject s = match(team, own, other, 1000, "BREAKTHROUGH");
        s.addProperty("stage", stage);
        s.addProperty("stages", stages);
        return MusicDirector.Mood.bed(s);
    }

    private static void objective() {
        // inObjective is flag-aware: it reads the objective index AND requires the viewer's team to be
        // actively driving a point it does not already own, so the objective bed only plays during a real
        // capture rather than whenever the player is merely standing in a zone.
        JsonObject onPoint = state(0, 800, 200, true);
        onPoint.addProperty("objective", 1);
        onPoint.add("flags", flags(2, 1, -1, 0));
        check(MusicDirector.Mood.bed(onPoint).equals("loop_in_objective"),
                "standing on a point your team is driving plays the objective bed");
        JsonObject ownedPoint = state(0, 800, 200, true);
        ownedPoint.addProperty("objective", 1);
        ownedPoint.add("flags", flags(2, 1, 0, 0));
        check(MusicDirector.Mood.bed(ownedPoint).isEmpty(),
                "standing on an already-owned point plays no objective bed");
        JsonObject offPoint = state(0, 800, 200, true);
        offPoint.addProperty("objective", -1);
        check(MusicDirector.Mood.bed(offPoint).isEmpty(),
                "standing nowhere in particular plays silence");
        // A captured point plays the objective bed even while losing, because the capture is the event.
        JsonObject losingOnPoint = state(0, 100, 900, true);
        losingOnPoint.addProperty("objective", 0);
        losingOnPoint.add("flags", flags(2, 0, -1, 0));
        check(MusicDirector.Mood.bed(losingOnPoint).equals("loop_in_objective"),
                "a point being driven plays the objective bed");
        check(MusicDirector.Mood.inObjective(onPoint), "inObjective reads a valid driven index");
        check(!MusicDirector.Mood.inObjective(ownedPoint), "but not an already-owned point");
        check(!MusicDirector.Mood.inObjective(offPoint), "and not a negative one");
        check(!MusicDirector.Mood.inObjective(new JsonObject()), "and not an absent one");
    }

    /** A {@code flags} array of {@code count} neutral points, with one entry overridden. */
    private static com.google.gson.JsonArray flags(int count, int index, int owner, int driving) {
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject f = new JsonObject();
            f.addProperty("owner", -1);
            f.addProperty("driving", -1);
            flags.add(f);
        }
        JsonObject target = flags.get(index).getAsJsonObject();
        target.addProperty("owner", owner);
        target.addProperty("driving", driving);
        return flags;
    }

    private static void volume() {
        check(MusicDirector.Mood.volume("") == 0f, "no bed means no volume");
        check(MusicDirector.Mood.volume(null) == 0f, "a null bed means no volume");
        check(MusicDirector.Mood.volume("music_victory") == 1.0f,
                "a win is the loudest thing the mod says");
        check(MusicDirector.Mood.volume("music_defeat") == 1.0f, "so is a loss");
        check(MusicDirector.Mood.volume("loop_attack") < 0.6f,
                "a loop sits well under a stinger so it does not fight gunfire");
        check(MusicDirector.Mood.volume("loop_desperate") > MusicDirector.Mood.volume("loop_attack"),
                "the desperate bed is louder than the ordinary loops");
        check(MusicDirector.Mood.volume("loop_lobby") > 0f, "every bed has an audible volume");
        boolean inRange = true;
        for (String bed : java.util.List.of("music_victory", "music_defeat", "music_first_deploy",
                "music_death", "music_stage_begin", "loop_attack", "loop_retreat", "loop_deploy",
                "loop_lobby", "loop_desperate", "loop_in_objective", "loop_queue",
                "loop_last_objective")) {
            float v = MusicDirector.Mood.volume(bed);
            if (v <= 0f || v > 1.2f) inRange = false;
        }
        check(inRange, "every bed a mood can return has a sane volume");
    }

    private static void dwell() {
        check(MusicDirector.MIN_DWELL_MS >= 1_000L,
                "the dwell window is at least a second, so a point flip cannot thrash the music");
        check(MusicDirector.MIN_DWELL_MS <= 5_000L,
                "and short enough that a real change is audible while it is still true");
        check(MusicDirector.current().isEmpty(), "nothing is playing before the first tick");
        check(MusicDirector.wanted(state(0, 500, 500, true)).isEmpty(),
                "wanted() answers silence for a normal mid-match player");
        check(MusicDirector.current().isEmpty(), "and does not change what is playing");
        // Silence is safe to call when nothing is playing, which is what every logout path does.
        MusicDirector.silence();
        MusicDirector.silence();
        check(MusicDirector.current().isEmpty(), "silence() is idempotent");
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A snapshot in a match, with the given team and ticket counts. */
    private static JsonObject state(int team, int own, int other, boolean alive) {
        JsonObject state = new JsonObject();
        state.addProperty("inMatch", true);
        JsonObject me = new JsonObject();
        me.addProperty("team", team);
        me.addProperty("alive", alive);
        state.add("me", me);
        JsonArray tickets = new JsonArray();
        // Blue is index 0 and red index 1; `own` is the viewer's side.
        if (team == 1) {
            tickets.add(other);
            tickets.add(own);
        } else {
            tickets.add(own);
            tickets.add(other);
        }
        state.add("tickets", tickets);
        return state;
    }

    private static String bed(int team, int own, int other) {
        return MusicDirector.Mood.bed(state(team, own, other, true));
    }
}
