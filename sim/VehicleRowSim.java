package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Headless checks for the vehicle deploy page's per-row live state.
 *
 * <p>The arithmetic that decides "how much health does my tank have" and "how many of its seats are taken"
 * is easy to get subtly wrong: matching the hull on its display name instead of its registry id, summing the
 * other team's hulls into your own, counting a wreck, or showing a full bar for a vehicle nobody has spawned.
 * Every one of those produces a screen that looks plausible and is wrong, so each gets an assertion.
 */
public final class VehicleRowSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        absent();
        present();
        teamFilter();
        typeMatching();
        damaged();
        seatText();
        describe();
        System.out.println("VehicleRowSim: " + checks + " checks passed");
    }

    private static JsonObject state(JsonObject... blips) {
        JsonObject state = new JsonObject();
        JsonArray array = new JsonArray();
        for (JsonObject blip : blips) array.add(blip);
        state.add("vehicles", array);
        return state;
    }

    private static JsonObject blip(String type, int team, float health, int occupants, int seats) {
        JsonObject blip = new JsonObject();
        blip.addProperty("type", type);
        blip.addProperty("team", team);
        if (health >= 0) blip.addProperty("health", health);
        blip.addProperty("occupants", occupants);
        if (seats > 0) blip.addProperty("seats", seats);
        return blip;
    }

    /** A type with no hull on the field says so rather than showing a bar. */
    private static void absent() {
        VehicleRow.Live live = VehicleRow.live(state(), "minecraft:tank", 0, 4);
        check(!live.present(), "nothing on the field -> not present");
        check(live.health() < 0, "no health to report when nothing is out");
        check(live.seats() == 4, "the start-up scan's seat count is still known");
        check(!live.damaged(), "an absent hull is not 'damaged'");

        VehicleRow.Live nullState = VehicleRow.live(null, "minecraft:tank", 0, 4);
        check(!nullState.present(), "a null snapshot is not a crash");

        VehicleRow.Live noField = VehicleRow.live(new JsonObject(), "minecraft:tank", 0, 0);
        check(!noField.present(), "a snapshot without a vehicles array is not a crash");
        check(noField.seats() == 0, "no scan data and no blip -> capacity unknown, not guessed");
    }

    /** One healthy hull reports its real numbers. */
    private static void present() {
        JsonObject state = state(blip("minecraft:tank", 0, 1.0f, 2, 4));
        VehicleRow.Live live = VehicleRow.live(state, "minecraft:tank", 0, 4);
        check(live.present(), "a matching blip -> present");
        check(live.onField() == 1, "one hull counted");
        check(live.occupants() == 2, "two passengers counted");
        check(Math.abs(live.fraction() - 1.0f) < 1e-6, "full health reads as 1.0");
        check(!live.damaged(), "full health is not damaged");
        check("2/4 座".equals(live.seatText()), "seat text is occupants over capacity");
    }

    /** The other side's hulls are not this row's business. */
    private static void teamFilter() {
        JsonObject state = state(blip("minecraft:tank", 1, 0.2f, 3, 4), blip("minecraft:tank", 0, 0.9f, 1, 4));
        VehicleRow.Live live = VehicleRow.live(state, "minecraft:tank", 0, 4);
        check(live.onField() == 1, "only this team's hull is counted");
        check(Math.abs(live.fraction() - 0.9f) < 1e-6, "the enemy's damaged hull does not drag our bar down");
        check(live.occupants() == 1, "the enemy's passengers are not counted");
        // A spectator (team -1) sees everything: they have no side to filter by.
        VehicleRow.Live spectator = VehicleRow.live(state, "minecraft:tank", -1, 4);
        check(spectator.onField() == 2, "a spectator counts every hull of the type");
    }

    /** Hulls are matched on the registry id, never on a prettified display name. */
    private static void typeMatching() {
        JsonObject state = new JsonObject();
        JsonArray array = new JsonArray();
        JsonObject tank = new JsonObject();
        // Deliberately no "type": an older server only sent "name", which is a display string.
        tank.addProperty("name", "Tank");
        tank.addProperty("team", 0);
        tank.addProperty("health", 0.5f);
        array.add(tank);
        state.add("vehicles", array);
        VehicleRow.Live live = VehicleRow.live(state, "superbwarfare:tank", 0, 4);
        check(!live.present(), "a blip without a registry id is not adopted by name");
        check(live.health() < 0, "and contributes no health");
    }

    /** Several hulls of one type: count them all, report the worst. */
    private static void damaged() {
        JsonObject state = state(
                blip("minecraft:tank", 0, 0.9f, 1, 4),
                blip("minecraft:tank", 0, 0.2f, 2, 4),
                blip("minecraft:tank", 0, 0.7f, 0, 4));
        VehicleRow.Live live = VehicleRow.live(state, "minecraft:tank", 0, 4);
        check(live.onField() == 3, "three hulls counted");
        check(live.occupants() == 3, "occupants summed across hulls");
        check(Math.abs(live.fraction() - 0.2f) < 1e-6,
                "the worst hull drives the bar (a nearly-dead tank must not hide behind healthy ones)");
        check(live.damaged(), "below the 35% threshold counts as damaged");
        VehicleRow.Live healthy = VehicleRow.live(state(blip("minecraft:tank", 0, 0.4f, 0, 4)),
                "minecraft:tank", 0, 4);
        check(!healthy.damaged(), "just above the threshold is not damaged");
        VehicleRow.Live edge = VehicleRow.live(state(blip("minecraft:tank", 0, 0.35f, 0, 4)),
                "minecraft:tank", 0, 4);
        check(!edge.damaged(), "exactly at the threshold is not yet damaged");
    }

    /** Seat text stays honest when the capacity is unknown. */
    private static void seatText() {
        check("—".equals(new VehicleRow.Live(1, 0, 0, 1f).seatText()),
                "no capacity and nobody aboard reads as unknown, not as 0/0");
        check("2 人".equals(new VehicleRow.Live(1, 2, 0, 1f).seatText()),
                "no capacity but passengers aboard reports the count alone");
        check("0/4 座".equals(new VehicleRow.Live(1, 0, 4, 1f).seatText()),
                "an empty hull with a known capacity reads 0/4");
        check("4/4 座".equals(new VehicleRow.Live(1, 4, 4, 1f).seatText()), "a full hull reads 4/4");
        // Occupants can exceed the capacity if the addon seats more than its metadata claims; the text must
        // not silently clamp, because the discrepancy is real information.
        check("5/4 座".equals(new VehicleRow.Live(1, 5, 4, 1f).seatText()),
                "an over-full hull reports what it observes");
    }

    /** The row's second line never claims a number it does not have. */
    private static void describe() {
        String absentText = VehicleRow.describe("地面载具", new VehicleRow.Live(0, 0, 4, -1f), "4 座");
        check(absentText.contains("本队暂无"), "an absent type says so");
        check(!absentText.contains("血 "), "an absent type shows no health percentage");
        check(absentText.contains("4 座"), "but keeps the static facts it does have");

        String liveText = VehicleRow.describe("地面载具", new VehicleRow.Live(1, 2, 4, 0.42f), "4 座");
        check(liveText.contains("场上 1"), "a present type says how many are out");
        check(liveText.contains("血 42%"), "and rounds its health to a percentage");
        check(liveText.contains("2/4 座"), "and shows occupation against capacity");

        String unreadable = VehicleRow.describe("航空器", new VehicleRow.Live(1, 1, 2, -1f), "");
        check(unreadable.contains("场上 1"), "health being unreadable does not hide the hull");
        check(!unreadable.contains("血 "), "and does not fabricate 100%");
        check(unreadable.contains("1/2 座"), "seats still show");

        String noStatics = VehicleRow.describe("航空器", new VehicleRow.Live(0, 0, 0, -1f), "");
        check(noStatics.equals("航空器 · 本队暂无"), "no statics at all still produces a clean line");
    }
}
