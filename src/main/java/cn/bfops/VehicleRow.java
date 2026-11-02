package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What the vehicle deploy page should say about each authorised vehicle right now.
 *
 * <h2>Why this is separate from the screen</h2>
 *
 * <p>The deploy page has to answer, per vehicle row: how much health does the hull I already have on the
 * field have, and how many of its seats are taken. Both answers need the same three server facts — the
 * snapshot's {@code vehicles} blips, the per-vehicle {@code vehicles} authorisation type id, and the seat
 * capacity the start-up scan recorded — and both are easy to get subtly wrong (matching on the display name
 * instead of the registry id, summing across both teams, counting a destroyed hull).
 *
 * <p>So the arithmetic lives here, without a {@code GuiGraphics} or a {@code Minecraft} in sight, and
 * {@code VehicleRowSim} covers it: healthy, damaged, wrecked, absent, two teams fielding the same type, and a
 * blip with no seat data at all.
 *
 * <h2>What it deliberately does not claim</h2>
 *
 * <p>Per-<em>seat</em> occupation (which specific seat, and whether a station is manned) is not available:
 * the snapshot carries the passenger count, which is what the hull's own {@code getPassengers()} reports, and
 * Superb Warfare does not expose a per-seat map without instantiating the vehicle. `2/4 座` is therefore an
 * occupation count, not a seating chart, and the wording says so rather than implying the latter.
 */
final class VehicleRow {
    /**
     * One row's worth of live state.
     *
     * @param onField   how many hulls of this type this team currently has out
     * @param occupants passengers across those hulls, summed
     * @param seats     static seat capacity of one hull of this type, 0 when unknown
     * @param health    health fraction of the most damaged hull on the field, or -1 when nothing is out
     */
    record Live(int onField, int occupants, int seats, float health) {
        boolean present() {
            return onField > 0;
        }

        /** The fraction the bar should show: a type with no hull out has nothing to draw. */
        float fraction() {
            return health < 0 ? 0f : Math.max(0f, Math.min(1f, health));
        }

        /** Below this a hull is worth calling out for repair; matches the minimap's amber threshold. */
        boolean damaged() {
            return health >= 0 && health < 0.35f;
        }

        /** {@code "2/4 座"}, or {@code "—"} when the capacity could not be read. */
        String seatText() {
            if (seats <= 0) return occupants > 0 ? occupants + " 人" : "—";
            return occupants + "/" + seats + " 座";
        }
    }

    private VehicleRow() { }

    /**
     * Reads one vehicle type's live state out of the snapshot.
     *
     * @param state     the server snapshot, possibly {@code null}
     * @param vehicleId the authorised vehicle id, which is the entity registry id
     * @param team      the viewer's team; hulls belonging to the other side are not this row's business
     * @param fallbackSeats seat capacity from the start-up scan, used when the blip carries none
     */
    static Live live(JsonObject state, String vehicleId, int team, int fallbackSeats) {
        if (state == null || vehicleId == null || vehicleId.isEmpty()
                || !state.has("vehicles") || !state.get("vehicles").isJsonArray()) {
            return new Live(0, 0, Math.max(0, fallbackSeats), -1f);
        }
        JsonArray vehicles = state.getAsJsonArray("vehicles");
        int onField = 0, occupants = 0, seats = 0;
        float worst = -1f;
        for (var element : vehicles) {
            if (!element.isJsonObject()) continue;
            JsonObject blip = element.getAsJsonObject();
            // Match on the registry id, never on the pretty name: two addon hulls can prettify alike, and a
            // row that silently adopts the other type's health is worse than a row with no health at all.
            if (!vehicleId.equals(Engine.str(blip, "type", ""))) continue;
            // Team filter uses the blip's own team field rather than "friendly", because "friendly" is
            // relative to whoever asked and this row is built for the local viewer.
            if (team >= 0 && Engine.num(blip, "team", -1) != team) continue;
            onField++;
            occupants += Engine.num(blip, "occupants", 0);
            if (blip.has("seats")) seats = Math.max(seats, Engine.num(blip, "seats", 0));
            if (blip.has("health")) {
                float value = blip.get("health").getAsFloat();
                // Show the hull in the worst shape: "one of my three tanks is nearly dead" is the fact that
                // prompts a repair call, and an average would hide it behind the two healthy ones.
                if (worst < 0 || value < worst) worst = value;
            }
        }
        if (seats <= 0) seats = Math.max(0, fallbackSeats);
        return new Live(onField, occupants, seats, worst);
    }

    /**
     * The row's second line: class, then whatever live facts exist.
     *
     * <p>Written to stay honest in every combination. A type with nothing on the field says so instead of
     * showing an empty bar, and a hull whose health could not be read shows seats but no percentage rather
     * than a fabricated 100%.
     */
    static String describe(String vehicleClass, Live live, String staticFacts) {
        StringBuilder text = new StringBuilder(vehicleClass);
        if (!live.present()) {
            if (staticFacts != null && !staticFacts.isEmpty()) text.append(" · ").append(staticFacts);
            text.append(" · 本队暂无");
            return text.toString();
        }
        text.append(" · 场上 ").append(live.onField());
        if (live.health() >= 0) text.append(" · 血 ").append(Math.round(live.fraction() * 100)).append("%");
        text.append(" · ").append(live.seatText());
        return text.toString();
    }
}
