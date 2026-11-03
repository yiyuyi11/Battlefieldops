package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks for the marking system: the two kinds, their lifetimes, their visibility and their snapshot
 * shape.
 *
 * <p>What is at stake: a mark is the only way information crosses from one player to another. Get the
 * visibility wrong and a team gets a free radar; get the lifetime wrong and a mark becomes a permanent
 * wall-hack; get the kind wrong and a friendly callout reads as an enemy warning. None of those throw, so they
 * are only findable by reading the rules carefully — which is what this file is for.
 */
public final class MarkSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        kinds();
        lifetime();
        visibility();
        cap();
        mates();
        snapshot();
        System.out.println("MarkSim: " + checks + " checks passed");
    }

    /** The two kinds are distinct and round-trip through the snapshot's string field. */
    private static void kinds() {
        check(Spotting.Kind.ENEMY.id().equals("ENEMY"), "the enemy kind serialises as ENEMY");
        check(Spotting.Kind.MATE.id().equals("MATE"), "the mate kind serialises as MATE");
        check(Spotting.Kind.of("MATE") == Spotting.Kind.MATE, "MATE parses back");
        check(Spotting.Kind.of("ENEMY") == Spotting.Kind.ENEMY, "ENEMY parses back");
        check(Spotting.Kind.of("mate") == Spotting.Kind.MATE, "parsing is case-insensitive");
        // Two failure directions, both benign: an unknown kind is an enemy (the safe reading -- it warns), and
        // a missing kind is an enemy too, so a snapshot from an older server still renders.
        check(Spotting.Kind.of(null) == Spotting.Kind.ENEMY, "a null kind reads as an enemy");
        check(Spotting.Kind.of("") == Spotting.Kind.ENEMY, "a blank kind reads as an enemy");
        check(Spotting.Kind.of("SOMETHING") == Spotting.Kind.ENEMY, "an unknown kind reads as an enemy");
        // The four-argument constructor keeps the old call sites working and defaults to an enemy mark.
        Spotting.Mark legacy = new Spotting.Mark(0, 1, 2, 3);
        check(legacy.kind() == Spotting.Kind.ENEMY, "the 4-arg constructor defaults to an enemy mark");
        check(legacy.name().isEmpty(), "and carries an empty name rather than null");
        Spotting.Mark nullKind = new Spotting.Mark(0, 1, 2, 3, null, null);
        check(nullKind.kind() == Spotting.Kind.ENEMY, "an explicit null kind is normalised to enemy");
        check(nullKind.name().isEmpty(), "an explicit null name is normalised to empty");
    }

    private static void lifetime() {
        long now = 1_000L;
        Spotting.Mark live = new Spotting.Mark(0, 1, 2, now + 4_000, Spotting.Kind.ENEMY, "x");
        check(live.active(now), "a mark with time left is active");
        check(live.active(now + 3_999), "and still active one millisecond before expiry");
        check(!live.active(now + 4_000), "and inactive exactly at expiry");
        check(!live.active(now + 10_000), "and inactive well after");
        check(live.leftMs(now) == 4_000, "leftMs reports the remainder");
        check(live.leftMs(now + 9_000) == 0, "and clamps to zero rather than going negative");
        check(Spotting.MARK_LIFETIME_MS == 5_000L, "the documented fallback lifetime is five seconds");
    }

    private static void visibility() {
        long now = 1_000L;
        Spotting.Mark blue = new Spotting.Mark(0, 0, 0, now + 1_000, Spotting.Kind.ENEMY, "e");
        Spotting.Mark red = new Spotting.Mark(1, 0, 0, now + 1_000, Spotting.Kind.ENEMY, "e");
        Spotting.Mark both = new Spotting.Mark(-1, 0, 0, now + 1_000, Spotting.Kind.ENEMY, "e");
        check(blue.visibleTo(0), "a blue mark is visible to blue");
        check(!blue.visibleTo(1), "and not to red");
        check(red.visibleTo(1), "a red mark is visible to red");
        check(!red.visibleTo(0), "and not to blue");
        // -1 means "both", which is how a spectator-only or a global mark would be expressed.
        check(both.visibleTo(0) && both.visibleTo(1) && both.visibleTo(-1),
                "a teamless mark is visible to everyone");

        List<Spotting.Mark> all = List.of(blue, red, both);
        check(Spotting.visibleTo(all, 0, now).size() == 2, "blue sees its own mark and the teamless one");
        check(Spotting.visibleTo(all, 1, now).size() == 2, "red sees its own mark and the teamless one");
        check(Spotting.visibleTo(all, 0, now + 2_000).isEmpty(), "expired marks are not visible to anyone");
        check(Spotting.visibleTo(null, 0, now).isEmpty(), "a null list yields no marks");
        check(Spotting.visibleTo(List.of(), 0, now).isEmpty(), "an empty list yields no marks");
        // A mark whose kind is MATE is visible by exactly the same rule: the kind changes how it is drawn,
        // not who may see it. Conflating the two would either leak or hide information.
        Spotting.Mark mateMark = new Spotting.Mark(0, 0, 0, now + 1_000, Spotting.Kind.MATE, "ally");
        check(mateMark.visibleTo(0) && !mateMark.visibleTo(1),
                "a teammate mark follows the same team visibility as an enemy one");
    }

    private static void cap() {
        long now = 1_000L;
        List<Spotting.Mark> many = new ArrayList<>();
        for (int i = 0; i < Spotting.MAX_MARKS + 5; i++) {
            many.add(new Spotting.Mark(0, i, i, now + 10_000, Spotting.Kind.ENEMY, "m" + i));
        }
        List<Spotting.Mark> kept = Spotting.prune(many, now);
        check(kept.size() == Spotting.MAX_MARKS, "the cap keeps " + Spotting.MAX_MARKS + " marks");
        // Newest survive: the last one added is the one somebody just called out.
        check(kept.get(kept.size() - 1).x() == many.get(many.size() - 1).x(),
                "the newest mark survives the cap");
        check(kept.get(0).x() == many.get(many.size() - Spotting.MAX_MARKS).x(),
                "and the oldest kept one is exactly MAX_MARKS back");
        check(Spotting.prune(null, now).isEmpty(), "pruning a null list is empty, not a throw");
        check(Spotting.prune(List.of(), now).isEmpty(), "pruning an empty list is empty");
        List<Spotting.Mark> mixed = new ArrayList<>(many.subList(0, 3));
        mixed.add(new Spotting.Mark(0, 0, 0, now - 1, Spotting.Kind.ENEMY, "gone"));
        check(Spotting.prune(mixed, now).size() == 3, "expired entries are dropped from a short list");
    }

    /** A teammate is markable by proximity alone; an enemy needs the cone and line of sight. */
    private static void mates() {
        check(Spotting.MATE_MARK_RANGE == Tactics.MATE_GLOW_RANGE,
                "the mate mark range matches the mate glow range, so nothing is markable that is not visible");
        check(Spotting.markableMate(10, 0, true), "a teammate 10 blocks away is markable");
        check(Spotting.markableMate(0, 0, true), "a teammate on top of you is markable");
        check(!Spotting.markableMate(200, 0, true), "a teammate beyond the range is not");
        check(!Spotting.markableMate(10, 0, false), "an out-of-range flag vetoes it regardless");
        // The boundary: exactly at the range is still inside, one block past is not.
        check(Spotting.markableMate(Spotting.MATE_MARK_RANGE, 0, true),
                "exactly at the range is still markable");
        check(!Spotting.markableMate(Spotting.MATE_MARK_RANGE + 1, 0, true),
                "one block past the range is not");
        // The cone test is unchanged for enemies: it is what stops the enemy case marking something behind you.
        check(Spotting.inCone(0, Math.toRadians(12), 0, 10), "a point straight ahead is in the cone");
        check(!Spotting.inCone(0, Math.toRadians(12), 0, -10), "a point directly behind is not");
        check(!Spotting.inCone(0, Math.toRadians(12), 10, 0), "a point 90 degrees off is not");
        check(Spotting.inCone(0, Math.toRadians(180), 0, -10),
                "a half-angle of 180 degrees accepts everything");
        check(Spotting.inCone(0, Math.toRadians(12), 0, 0),
                "a zero-length offset is accepted rather than dividing by zero");
    }

    /** The snapshot carries both the kind and the name, and the client can read them. */
    private static void snapshot() {
        JsonObject state = new JsonObject();
        JsonArray marks = new JsonArray();
        marks.add(entry("ENEMY", "敌军甲", 10, 64, 20, 3_000));
        marks.add(entry("MATE", "队友乙", -40, 70, -5, 1_200));
        state.add("marks", marks);

        JsonArray read = state.getAsJsonArray("marks");
        check(read.size() == 2, "both marks survive serialisation");
        check(Engine.str(read.get(0).getAsJsonObject(), "kind", "").equals("ENEMY"),
                "the enemy kind is readable from the snapshot");
        check(Engine.str(read.get(1).getAsJsonObject(), "kind", "").equals("MATE"),
                "the mate kind is readable from the snapshot");
        check(Engine.str(read.get(1).getAsJsonObject(), "name", "").equals("队友乙"),
                "the marked player's name is readable");
        check(Spotting.Kind.of(Engine.str(read.get(1).getAsJsonObject(), "kind", ""))
                == Spotting.Kind.MATE, "and parses back to the right kind");
        // A mark from an older server has no kind field: it must read as an enemy, not as a broken mark.
        JsonObject legacy = new JsonObject();
        legacy.addProperty("x", 0);
        legacy.addProperty("z", 0);
        check(Spotting.Kind.of(Engine.str(legacy, "kind", "ENEMY")) == Spotting.Kind.ENEMY,
                "a snapshot without a kind field reads as an enemy mark");
        // The fade driver needs leftMs; its absence must not break the draw.
        JsonObject noFade = new JsonObject();
        noFade.addProperty("x", 0);
        noFade.addProperty("z", 0);
        check(!noFade.has("leftMs"), "a mark without leftMs is representable");
    }

    private static JsonObject entry(String kind, String name, double x, double y, double z, long leftMs) {
        JsonObject one = new JsonObject();
        one.addProperty("kind", kind);
        one.addProperty("name", name);
        one.addProperty("x", x);
        one.addProperty("y", y);
        one.addProperty("z", z);
        one.addProperty("leftMs", leftMs);
        one.addProperty("left", (leftMs + 999) / 1000);
        return one;
    }
}
