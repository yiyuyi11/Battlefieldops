package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * The 49 honour titles, ported from {@code HonorDefinitionsArea}.
 *
 * <p><b>Why this is a separate table from {@link AwardCatalog}.</b> These are two different vocabularies that
 * look alike, and conflating them was a defect during this port:
 *
 * <ul>
 *   <li>{@link AwardCatalog}'s 72 <em>bonuses</em> are scoring events — {@code HEADSHOT}, {@code DAMAGE},
 *       {@code SQUAD_BEACON_DEPLOY}. They answer "how many points, and what text goes in the feed".</li>
 *   <li>These 49 <em>honours</em> are the badge art — {@code headhunter}, {@code roadhog}, {@code pathfinder}.
 *       They answer "which picture, and which title".</li>
 * </ul>
 *
 * <p>They overlap in only one place ({@code avenge}/{@code AVENGE}), which is exactly enough to make a shared
 * lookup look plausible and then fail for 71 of 72 awards.
 *
 * <p><b>What each honour carries.</b> The reference's {@code HonorDefinition} has an id, a condition type, a
 * condition parameter string, a score, and a texture key derived as {@code "honor_" + id}. The score is what a
 * player is awarded; the condition is what the server evaluates to decide whether it fires. The condition
 * vocabulary is large (see {@link Honours}), so this table records the reference's own condition string rather
 * than pretending to evaluate it — the server half of this mod decides what happened, and this is the client's
 * catalogue of what the badges mean.
 *
 * @param id        the reference's id, which is also the badge's filename stem after {@code honor_}
 * @param kind      {@code streak} for a kill-streak honour, {@code conditional} for a situational one
 * @param condition the reference's own condition string, kept verbatim so the two can be compared
 * @param score     the points the honour is worth
 */
record HonourCatalog(String id, Kind kind, String condition, int score) {

    /** How the reference decides whether an honour fires. */
    enum Kind {
        /** {@code KILL_STREAK} — the condition names a streak length after a colon, e.g. {@code headshot:3}. */
        STREAK,
        /** {@code CONDITIONAL} — the condition names a server-side event. */
        CONDITIONAL
    }

    private static final List<HonourCatalog> ALL = build();

    private static List<HonourCatalog> build() {
        List<HonourCatalog> out = new ArrayList<>(49);
        // Verbatim from the reference's HonorDefinitionsArea, in its own registration order.
        out.add(streak("headhunter", "headshot:3", 30));
        out.add(streak("warrior", "moving:3", 50));
        out.add(conditional("avenge", "avenge", 30));
        out.add(conditional("bomb_technician", "rush_bomb_defused", 50));
        out.add(conditional("combat_medic", "round_revive_20", 8));
        out.add(conditional("detonator", "rush_objective_destroyed", 35));
        out.add(conditional("raider", "kill_within_8s_after_combo4", 150));
        out.add(conditional("scout", "spot_20", 30));
        out.add(conditional("arsenal", "arsenal_in_30s_3_items", 5));
        out.add(conditional("artillery", "vehicle_damage_800", 8));
        out.add(conditional("avenger", "leave_it_to_me", 65));
        out.add(conditional("ballistics", "destroy_vehicle_outside", 20));
        out.add(conditional("ace_pilot", "destroy_air_vehicle_in_air", 30));
        out.add(conditional("bail_rocket", "bail_out_destroy_air_return", 1));
        out.add(conditional("breaker", "conquest_stage_breaker", 60));
        out.add(conditional("top", "victim_top_scorer", 30));
        out.add(conditional("medic", "revive_3_consecutive", 60));
        out.add(conditional("mechanic", "repair_vehicle_25", 20));
        out.add(conditional("minelayer", "mine_destroy_vehicle", 8));
        out.add(conditional("fire_support", "fire_suppression_5", 45));
        out.add(conditional("field_medic", "revive_5_in_life", 40));
        out.add(conditional("flight_dispatcher", "victim_riding_air", 25));
        out.add(conditional("pathfinder", "squad_deploy_3", 35));
        out.add(conditional("demolition", "destroy_7_vehicles", 5));
        out.add(conditional("destroyer", "tactical_gadget_3", 25));
        out.add(conditional("executioner", "backstab", 30));
        out.add(conditional("flank", "kill_blinded", 30));
        out.add(conditional("frenzy", "buff_kill", 50));
        out.add(conditional("garrison", "garrison_kill_3", 50));
        out.add(conditional("ground_control", "conquest_stage_ground_control", 25));
        out.add(conditional("guardian", "leave_it_to_me", 50));
        out.add(conditional("gunner", "gunner_kill_5", 35));
        out.add(conditional("quickdraw", "quickdraw", 8));
        out.add(conditional("roadhog", "roadkill", 50));
        out.add(conditional("quartermaster", "supply_revive_25", 35));
        out.add(conditional("support", "support_1000_score", 30));
        out.add(conditional("tank_destroyer", "destroy_tank", 10));
        out.add(conditional("recon", "recon_1000_score", 30));
        out.add(conditional("assault", "assault_1000_score", 30));
        out.add(conditional("engineer", "engineer_1000_score", 30));
        out.add(conditional("sharp_shooter", "long_range_headshot", 30));
        out.add(conditional("long_shot", "long_shot_range", 60));
        out.add(conditional("marksman", "sniper_kill_10_15", 8));
        out.add(conditional("rifleman", "rifle_kill_15_20", 8));
        out.add(conditional("sniper_duel", "sniper_duel", 5));
        out.add(conditional("smoke_screen", "smoke_revive_5", 5));
        out.add(conditional("unstoppable", "mg_kill_5_no_reload", 10));
        out.add(conditional("vanguard", "first_kill_in_team", 3));
        out.add(conditional("warhead", "heavy_headshot", 8));
        return List.copyOf(out);
    }

    private static HonourCatalog streak(String id, String condition, int score) {
        return new HonourCatalog(id, Kind.STREAK, condition, score);
    }

    private static HonourCatalog conditional(String id, String condition, int score) {
        return new HonourCatalog(id, Kind.CONDITIONAL, condition, score);
    }

    /** All 49, in the reference's registration order. */
    static List<HonourCatalog> all() {
        return ALL;
    }

    static int size() {
        return ALL.size();
    }

    static HonourCatalog byId(String id) {
        if (id == null) return null;
        for (HonourCatalog honour : ALL) {
            if (honour.id().equals(id)) return honour;
        }
        return null;
    }

    /** The badge texture file for this honour, matching the pack's own naming. */
    String textureFile() {
        return "honor_" + id + ".png";
    }

    /** The translation key for the honour's name. */
    String nameKey() {
        return "battlefield_ops.honor." + id + ".name";
    }

    /** The translation key for the honour's description. */
    String descKey() {
        return "battlefield_ops.honor." + id + ".desc";
    }

    /**
     * The honours a kill-streak count and kind could earn.
     *
     * <p>Only the two {@code :}-parameterised streaks exist ({@code headshot:3}, {@code moving:3}), so this
     * returns them when the count is reached and the kind matches. The rest are server-side conditionals that
     * this mod's own evidence model decides — see {@link Honours}.
     */
    static List<HonourCatalog> streakHonours(KillType kind, int count) {
        List<HonourCatalog> out = new ArrayList<>(2);
        for (HonourCatalog honour : ALL) {
            if (honour.kind() != Kind.STREAK) continue;
            int colon = honour.condition().indexOf(':');
            if (colon < 0) continue;
            String subject = honour.condition().substring(0, colon);
            int needed;
            try {
                needed = Integer.parseInt(honour.condition().substring(colon + 1));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (count < needed) continue;
            boolean matches = switch (subject) {
                case "headshot" -> kind != null && "HEADSHOT".equals(kind.id());
                // "moving" is a kill made while moving, which the reference infers server-side; this mod's
                // evidence model does not track movement, so a moving streak is not claimable here.
                default -> false;
            };
            if (matches) out.add(honour);
        }
        return out;
    }
}
