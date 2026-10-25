package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a player may appear, and where a vehicle may be launched from.
 *
 * <p>A deploy site is either a spawn point or a capture point the team currently holds. Holding ground
 * is supposed to matter, so an owned flag doubles as a forward launch site: taking it lets a squad
 * bring vehicles in from the front instead of driving from the back of the map.
 *
 * <p>The rule is deliberately asymmetric: <b>capture points launch ground vehicles only</b>. A flag on
 * a hilltop is not a runway, and letting aircraft appear on any owned point would make the air war
 * meaningless. Aircraft therefore require a spawn point explicitly marked as an air point
 * ({@link Model.Spawn#allows} containing {@code 'A'}).
 *
 * <p>Sites carry their {@link Model.Spawn#kind()} so the deploy list can group them: a headquarters, a
 * forward post and an airfield are three different choices to a player, and flattening them into one
 * list of names hides the one thing that matters about an airfield — that it is where the aircraft are.
 *
 * <p>Both sides compute this list from the same function — the server to validate, the client to draw
 * — because a client that guessed a different list would offer choices the server then rejects, and a
 * server that used a different rule would silently relocate players.
 */
final class Deploy {
    /** One deploy option. {@code allows} is the vehicle mask, matching {@link Model.Spawn#allows}. */
    record Site(String id, String label, double x, double y, double z, float yaw,
                String allows, boolean spawn, String kind) {
        boolean allows(char vehicleClass) {
            return allows != null && allows.indexOf(Character.toUpperCase(vehicleClass)) >= 0;
        }

        boolean air() {
            return "AIR".equals(kind) || allows('A');
        }

        /** Synthetic spawn so the existing spawn-choice helpers keep working unchanged. */
        Model.Spawn asSpawn(int team) {
            Model.Spawn spawn = new Model.Spawn();
            spawn.id = id;
            spawn.team = team;
            spawn.x = x;
            spawn.y = y;
            spawn.z = z;
            spawn.yaw = yaw;
            spawn.allows = allows;
            spawn.kind = kind;
            return spawn;
        }
    }

    private Deploy() {}

    /**
     * Every site the team may deploy at right now, in a stable order: spawn points first (so the
     * primary bases stay at the top of the list), then held capture points.
     *
     * <p>Air points sort ahead of other spawns within their group, because a player looking for the
     * airfield is looking for exactly one thing and should not have to read past the headquarters.
     *
     * @param owners per-point owner from the live match, {@code -1} when neutral or not yet known
     */
    static List<Site> sites(Model.Arena map, int team, int stage, boolean breakthrough, int[] owners) {
        List<Site> out = new ArrayList<>();
        if (map == null || team < 0 || team > 1) return out;
        List<Site> spawnSites = new ArrayList<>();
        List<Site> airSites = new ArrayList<>();
        for (Model.Spawn spawn : map.spawns) {
            if (spawn.team != team) continue;
            if (breakthrough && spawn.sector != stage) continue;
            // A spawn outside the team's activity zone would drop the player outside their area, so it
            // is not offered. The same filter exists server-side in Engine.legalSpawns.
            if (!map.inZone(team, spawn.x, spawn.z)) continue;
            Site site = new Site(spawn.id, spawn.id, spawn.x, spawn.y, spawn.z, spawn.yaw,
                    spawn.allows == null || spawn.allows.isEmpty() ? "G" : spawn.allows, true,
                    spawn.kind());
            if (spawn.air()) airSites.add(site); else spawnSites.add(site);
        }
        out.addAll(spawnSites);
        out.addAll(airSites);
        for (int i = 0; i < map.points.size(); i++) {
            Model.Point point = map.points.get(i);
            if (breakthrough && point.sector != stage) continue;
            if (owners == null || i >= owners.length || owners[i] != team) continue;
            String id = pointId(point);
            // Ground only: an owned flag is a staging ground, not an airstrip.
            out.add(new Site(id, id, point.x, point.y, point.z, 0f, "G", false, Model.Spawn.POINT));
        }
        return out;
    }

    /** Id used for a capture point offered as a deploy site. Distinct from any spawn id by the prefix. */
    static String pointId(Model.Point point) {
        return "据点 " + point.id;
    }

    /** Sites that can launch the given vehicle class. */
    static List<Site> sitesFor(List<Site> sites, char vehicleClass) {
        List<Site> out = new ArrayList<>();
        for (Site site : sites) if (site.allows(vehicleClass)) out.add(site);
        return out;
    }

    /**
     * The site to actually use for this vehicle choice, given the player's current pick.
     *
     * <p>This exists because the two rows — "部署点" and the vehicle — are chosen independently, and
     * nothing used to reconcile them. Picking an aircraft while the site still pointed at the ground
     * headquarters made the deploy button go grey with only a one-line caption to explain it, which
     * reads exactly like "deploying vehicles is broken for me". The fix is to keep the pair consistent
     * on the player's behalf: if the current site can launch what they picked, keep it; otherwise move
     * to the first site that can.
     *
     * <p>Returns the current choice unchanged when the vehicle is empty (infantry can use any site)
     * or when no site can launch it — the caller then shows the button as disabled, which is correct,
     * because the map genuinely has no such site.
     */
    static String siteFor(List<Site> sites, String current, String vehicle, char vehicleClass) {
        if (sites.isEmpty()) return "";
        if (vehicle == null || vehicle.isEmpty()) {
            return sites.stream().anyMatch(s -> s.id().equals(current)) ? current : sites.getFirst().id();
        }
        List<Site> capable = sitesFor(sites, vehicleClass);
        if (capable.isEmpty()) return current;
        if (capable.stream().anyMatch(s -> s.id().equals(current))) return current;
        return capable.getFirst().id();
    }

    /** First non-air spawn, used as the fallback when a requested site disappears. */
    static Site first(List<Site> sites) {
        return sites.isEmpty() ? null : sites.getFirst();
    }

    /**
     * Picks the site to use when the requested one is gone or no longer legal.
     *
     * <p>Prefers a site whose vehicle mask matches what the player asked to bring, so a rejected
     * request degrades to "same vehicle from another base" instead of "deployed without it".
     */
    static Site fallback(List<Site> sites, char vehicleClass, boolean needsVehicle) {
        if (sites.isEmpty()) return null;
        if (needsVehicle) {
            List<Site> matching = sitesFor(sites, vehicleClass);
            if (!matching.isEmpty()) return matching.getFirst();
        }
        return sites.getFirst();
    }

    /** Owner array from a live ruleset, for the server side. */
    static int[] owners(Rules rules) {
        int[] owners = new int[rules.flags.size()];
        for (int i = 0; i < owners.length; i++) owners[i] = rules.flags.get(i).owner;
        return owners;
    }

    /**
     * The site list a client should show, from the server's snapshot or, before the first snapshot,
     * computed locally with the same rules.
     *
     * <p>Both screens need this and they must agree: if the deploy screen offered a site the editor
     * did not (or the reverse), the operator would be looking at two different maps for one match.
     * Reading {@code deploySites} when present keeps the authority on the server — the local
     * computation is only a stand-in for the ticks before the snapshot arrives.
     */
    static List<Site> fromState(JsonObject state, Model.Arena arena, int team) {
        if (arena == null || team < 0) return List.of();
        JsonArray array = state != null && state.has("deploySites")
                && state.get("deploySites").isJsonArray() ? state.getAsJsonArray("deploySites") : null;
        if (array != null && !array.isEmpty()) {
            List<Site> out = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject site = element.getAsJsonObject();
                String id = Engine.str(site, "id", "");
                if (id.isEmpty()) continue;
                out.add(new Site(id, id,
                        site.has("x") ? site.get("x").getAsDouble() : 0,
                        site.has("y") ? site.get("y").getAsDouble() : 64,
                        site.has("z") ? site.get("z").getAsDouble() : 0,
                        0f,
                        Engine.str(site, "allows", "G"),
                        site.has("spawn") && site.get("spawn").getAsBoolean(),
                        Engine.str(site, "kind", Model.Spawn.BASE)));
            }
            if (!out.isEmpty()) return out;
        }
        return local(state, arena, team);
    }

    /** Locally computed sites, for the ticks before the first server snapshot arrives. */
    static List<Site> local(JsonObject state, Model.Arena arena, int team) {
        if (arena == null || team < 0) return List.of();
        int stage = Engine.num(state, "stage", 0);
        int[] owners = null;
        if (state != null && state.has("flags") && state.get("flags").isJsonArray()) {
            JsonArray flags = state.getAsJsonArray("flags");
            owners = new int[flags.size()];
            for (int i = 0; i < flags.size(); i++) {
                if (!flags.get(i).isJsonObject()) continue;
                owners[i] = Engine.num(flags.get(i).getAsJsonObject(), "owner", -1);
            }
        }
        return sites(arena, team, stage, "BREAKTHROUGH".equals(arena.mode), owners);
    }
}
