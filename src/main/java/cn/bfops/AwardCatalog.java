package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * The 72 scoring events the 656 kill-icon pack names "bonuses", ported from {@code BonusDefinitionsArea}.
 *
 * <p>The pack's own term is "bonus"; this codebase calls the on-screen version an award. The word "award" is
 * already used for the score-popup entries elsewhere in this mod, and the two are the same thing here, so the
 * port keeps this codebase's word and records the pack's.
 *
 * <p><b>This table is transcribed, not designed.</b> An earlier revision of this file invented its own
 * {@code Merge} values per award and left every {@code scoreCap} at zero. Both were wrong, and the shape of the
 * error is worth recording because it is easy to make again: the reference's {@code BonusDefinition.Builder}
 * defaults {@code mergeBehavior} to {@link Merge#BY_TYPE_EXTRA} and {@code scoreCap} to {@code -1}, and only
 * <em>one</em> of the 72 entries overrides either. Reading the builder showed 71 of 72 share one merge rule,
 * with {@code KILL_COMBO} the only exception — nothing like the per-award variety that inventing values had
 * produced. The values below come from {@code gen-awards.py}, which parses the decompiled source.
 *
 * <p><b>What each field actually does.</b>
 *
 * <ul>
 *   <li>{@code expression} is the reference's default score expression, a configurable string ({@code "5"},
 *       {@code "1.5"}, {@code "1"}). The server overrides it per player via {@code ServerData}; it is not an
 *       arithmetic formula despite the name.</li>
 *   <li>{@code factor} says what the configured multiplier is multiplied by — damage dealt, victim's max health,
 *       distance, combo length, streak length, a count, or an already-computed score. Only 12 of 72 set it.</li>
 *   <li>{@code priorityKill} marks the six events that replace a plain kill line rather than adding to it.</li>
 *   <li>{@code iconKillType} borrows a {@link KillType}'s art; only {@code REVIVE} and
 *       {@code VEHICLE_DESTROY_ASSIST} use it, with MEDIC and VEHICLE_DESTROY_ASSIST art respectively.</li>
 *   <li>{@code statCategory} routes the event into a per-match statistic; only four events do.</li>
 * </ul>
 *
 * <p><b>Why the caps matter.</b> The reference clamps a bonus to its own {@code scoreCap} when one is set
 * positive, then to a global server maximum. Both clamps are kept. Only {@code VALUE_TARGET_DESTROYED} has a cap
 * ({@code 2000}), because it is the one damage-scaled event that can run away within a single match.
 */
final class AwardCatalog {

    /** What a bonus's configured multiplier is multiplied by. Ported verbatim from {@code FactorType}. */
    enum Factor {
        /** The expression is the score; nothing scales it. */
        NONE,
        /** Scaled by the victim's maximum health, so a tank hit is worth more than a chicken. */
        VICTIM_MAX_HEALTH,
        /** Scaled by damage dealt, which is why damage events need a cap. */
        DAMAGE,
        /** Scaled by kill distance. */
        DISTANCE,
        /** Scaled by the current combo length. */
        COMBO,
        /** Scaled by the interrupting streak's length. */
        STREAK,
        /** Scaled by how many things were hit. */
        COUNT,
        /** Scaled by a score the caller already computed. */
        SCORE
    }

    /**
     * How repeated instances of one bonus combine in a single flush.
     *
     * <p>Ported verbatim from {@code MergeBehavior}, which has exactly two members — not the three the earlier
     * revision of this file invented. The engine merges on a key of either {@code "COMBO"} (all combo events
     * collapse together) or {@code type + "|" + extra} (the same event on the same target sums).
     */
    enum Merge {
        /** Group by type and the extra payload, summing scores. The default for 71 of 72. */
        BY_TYPE_EXTRA,
        /** Group every combo event together regardless of victim. Used only by {@code KILL_COMBO}. */
        BY_COMBO
    }

    /** Which per-match statistic a bonus contributes to. Ported verbatim from {@code StatCategory}. */
    enum Stat {
        /** Not counted in any statistic. */
        NONE,
        /** Counted as an assist. */
        ASSIST,
        /** Counted as a revive. */
        REVIVE,
        /** Counted as damage. */
        DAMAGE
    }

    /**
     * One scoring event.
     *
     * @param id           stable identifier; also the language-key infix and the {@code format_} config key stem
     * @param type         the numeric id the reference used, persisted in saved stats, so never renumber these
     * @param expression   the default score expression, overridden per player on the server
     * @param factor       what the configured multiplier is multiplied by
     * @param scoreCap     the most this event may ever be worth; {@code 0} means uncapped
     * @param merge        how repeated instances in one flush combine
     * @param priorityKill true when this event should replace, rather than accompany, a plain kill line
     * @param iconKillType the {@link KillType} whose art this event borrows, or {@code -1} for none
     * @param killFeedPayload the fixed feed payload this event carries instead of a victim name, or {@code null}
     * @param stat         the per-match statistic this event feeds
     * @param disabledByDefault true when the pack ships this event switched off
     * @param streakSubtitles combo lengths that have their own subtitle string
     */
    record Award(String id, int type, String expression, Factor factor, float scoreCap, Merge merge,
                 boolean priorityKill, int iconKillType, String killFeedPayload, Stat stat,
                 boolean disabledByDefault, List<Integer> streakSubtitles) {

        /** The language key for the label. */
        String nameKey() {
            return "battlefield_ops.bonus." + id + ".name";
        }

        /** The language key for the one-line explanation. */
        String descKey() {
            return "battlefield_ops.bonus." + id + ".desc";
        }

        /** The config key the pack uses for this event's layout. */
        String formatConfigKey() {
            return "format_" + id.toLowerCase(java.util.Locale.ROOT);
        }

        /** The {@link KillType} whose art this event borrows, or {@code null}. */
        KillType iconKind() {
            return iconKillType < 0 ? null : KillType.byType(iconKillType);
        }
    }

    private static final List<Award> ALL = build();

    private AwardCatalog() { }

    static List<Award> all() {
        return ALL;
    }

    static int size() {
        return ALL.size();
    }

    /** The award with this id, or {@code null}. Unlike kill types, an unknown award is not a default one. */
    static Award byId(String id) {
        if (id == null) return null;
        for (Award award : ALL) if (award.id().equals(id)) return award;
        return null;
    }

    /** The award with this numeric type, or {@code null}. */
    static Award byType(int type) {
        for (Award award : ALL) if (award.type() == type) return award;
        return null;
    }

    /**
     * The badge texture for an award id, or {@code null} when the pack ships none.
     *
     * <p>Most of the 72 scoring awards have no badge: the art collection is 49 <em>honour titles</em>
     * ({@code honor_assault}, {@code honor_pathfinder}, …) which are a different vocabulary entirely — see
     * {@link HonourCatalog}. An award's on-screen icon comes from its {@link Award#iconKillType} instead, and only
     * the honour titles use badges. Returning {@code null} rather than guessing a filename keeps that
     * distinction visible at every call site.
     */
    static String badgeFor(String id) {
        return byId(id) == null ? null : KillType.honourFile(id);
    }

    /**
     * A point value clamped to the award's own cap and then to a global maximum.
     *
     * <p>Both clamps are the reference's, in its order. The global maximum is passed in rather than read from
     * config here so this stays a pure function: a server that has not loaded its config yet still scores
     * predictably.
     */
    static float resolveScore(String id, float raw, int globalMax) {
        Award award = byId(id);
        float limited = raw;
        if (award != null && award.scoreCap() > 0f && raw > award.scoreCap()) limited = award.scoreCap();
        if (globalMax > 0 && limited > (float) globalMax) limited = (float) globalMax;
        // Negative scores are legal in this system -- they are penalties -- but NaN is not, and a NaN would
        // propagate into the total and make the whole scoreboard unreadable.
        return Float.isNaN(limited) ? 0f : limited;
    }

    /**
     * The awards that should appear in a kill feed, i.e. those that borrow a kill icon or carry a fixed payload.
     *
     * <p>Most bonuses are score feedback; only the ones with an {@code iconKillType} or a
     * {@code killFeedPayload} produce a feed line. The distinction is the reference's, and it is what stops the
     * feed from being flooded with "damage" entries.
     */
    static List<Award> feedAwards() {
        List<Award> out = new ArrayList<>();
        for (Award award : ALL) {
            if (award.iconKillType() >= 0 || award.killFeedPayload() != null) out.add(award);
        }
        return out;
    }

    /** The six events that replace a plain kill line rather than adding to it. */
    static List<Award> priorityKills() {
        List<Award> out = new ArrayList<>();
        for (Award award : ALL) if (award.priorityKill()) out.add(award);
        return out;
    }

    /**
     * The 72 awards, in the reference's own order and numbering.
     *
     * <p>Generated by {@code gen-awards.py} from the decompiled {@code BonusDefinitionsArea}. Do not hand-edit
     * the field values: re-run the generator so the table and the source cannot drift.
     */
    private static List<Award> build() {
        List<Award> awards = new ArrayList<>(72);
        awards.addAll(List.of(
        new Award("DAMAGE", 0, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.DAMAGE, false, List.of()),
        new Award("KILL", 1, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, true, -1, null, Stat.NONE, false, List.of()),
        new Award("EXPLOSION", 2, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.DAMAGE, false, List.of()),
        new Award("HEADSHOT", 3, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.DAMAGE, false, List.of()),
        new Award("CRIT", 4, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.DAMAGE, false, List.of()),
        new Award("KILL_EXPLOSION", 5, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, true, -1, null, Stat.NONE, false, List.of()),
        new Award("KILL_HEADSHOT", 6, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, true, -1, null, Stat.NONE, false, List.of()),
        new Award("KILL_CRIT", 7, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, true, -1, null, Stat.NONE, false, List.of()),
        new Award("KILL_COMBO", 8, "8", Factor.COMBO, 0f, Merge.BY_COMBO, true, -1, null, Stat.NONE, false, List.of(2, 3, 4, 5, 6, 7, 8)),
        new Award("KILL_LONG_DISTANCE", 9, "1", Factor.DISTANCE, 0f, Merge.BY_TYPE_EXTRA, true, -1, null, Stat.NONE, false, List.of()),
        new Award("KILL_INVISIBLE", 10, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("ASSIST", 11, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.ASSIST, false, List.of()),
        new Award("DESPERATE_COUNTERATTACK", 12, "12", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("AVENGE", 13, "25", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SHOCKWAVE", 14, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BLIND_KILL", 15, "12", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BUFF_KILL", 16, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("DEBUFF_KILL", 17, "7", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BOTH_BUFF_DEBUFF_KILL", 18, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("LAST_BULLET_KILL", 19, "30", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("ONE_BULLET_MULTI_KILL", 20, "8", Factor.COUNT, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("EFFORTLESS_KILL", 21, "8", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BACKSTAB_KILL", 22, "8", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BACKSTAB_MELEE_KILL", 23, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BRAVE_RETURN", 24, "12", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("JUSTICE_FROM_ABOVE", 25, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("ABSOLUTE_AIR_CONTROL", 26, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BERSERKER", 27, "3", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("INTERRUPTED_STREAK", 28, "6", Factor.STREAK, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("LEAVE_IT_TO_ME", 29, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SAVIOR", 30, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SLAY_THE_LEADER", 31, "18", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("PURGE", 32, "30", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("QUICK_SWITCH", 33, "3", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SEIZE_OPPORTUNITY", 34, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("BLOODTHIRSTY", 35, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("MERCILESS", 36, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("VALIANT", 37, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("FIERCE", 38, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SAVAGE", 39, "25", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("POTATO_AIM", 40, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("HIT_VEHICLE_ARMOR", 41, "1.5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("DESTROY_VEHICLE", 42, "1.5", Factor.SCORE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("VEHICLE_REPAIR", 43, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("VALUE_TARGET_DESTROYED", 44, "1", Factor.DAMAGE, 2000f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("LOCKED_TARGET", 45, "12", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("HOLD_POSITION", 46, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("CHARGE_ASSAULT", 47, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("FIRE_SUPPRESSION", 48, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("DESTROY_BLOCK", 49, "1", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, true, List.of()),
        new Award("SPOTTING", 50, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SPOTTING_KILL", 51, "30", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SPOTTING_TEAM_ASSIST", 52, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("CONQUEST_CAPTURE_PROGRESS", 53, "2", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("CONQUEST_CAPTURE_NEUTRALIZE", 54, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("CONQUEST_CAPTURE_CONTROL", 55, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SQUAD_DEPLOY_ON_YOU", 56, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SQUAD_LAST_MEMBER_KILL", 57, "15", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("EMERGENCY_REINFORCEMENT", 58, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SQUAD_WIPE_COMPLETION", 59, "40", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("TACTICAL_GADGET_DESTROYED", 60, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("SQUAD_BEACON_DEPLOY", 61, "20", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("VALUE_OBJECTIVE_SUPPORT_BEACON_DEPLOY", 62, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("HEALING", 63, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("AMMO_SUPPLY", 64, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("REVIVE", 65, "100", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, 8, null, Stat.REVIVE, false, List.of()),
        new Award("VEHICLE_DESTROY_ASSIST", 66, "1", Factor.DAMAGE, 0f, Merge.BY_TYPE_EXTRA, false, 7, null, Stat.NONE, false, List.of()),
        new Award("FRIENDLY_DEPLOY_ON_YOUR_VEHICLE", 67, "10", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("GROUND_SENSOR_SCAN", 68, "5", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of()),
        new Award("RUSH_BOMB_DEFUSED", 69, "200", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, "rush_bomb_defused", Stat.NONE, false, List.of()),
        new Award("RUSH_BOMB_PLANTED", 70, "200", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, "rush_bomb_planted", Stat.NONE, false, List.of()),
        new Award("RUSH_OBJECTIVE_DESTROYED", 71, "500", Factor.NONE, 0f, Merge.BY_TYPE_EXTRA, false, -1, null, Stat.NONE, false, List.of())
        ));
        return List.copyOf(awards);
    }
}
