package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * The honours a kill can earn, and the arithmetic that decides them.
 *
 * <h2>Where the list comes from</h2>
 *
 * <p>The reference pack's {@code gd656killicon} defines 56 kill honours in its {@code BonusType} enum. They
 * are enumerated here by name as read from that jar, because the set itself is the design: which moments are
 * worth celebrating was decided by playtesting, and a shorter list invented from scratch would be a list of
 * the obvious ones.
 *
 * <p>Not all 56 are awardable by this mod, and pretending otherwise would be worse than shipping fewer.
 * The distinction is <b>observability</b>: a mod that owns its own gun system can see whether a shot was the
 * last round in the magazine, or whether the shooter was blind at the time. This mod sits on top of TaCZ and
 * Superb Warfare, so it sees damage events and geometry — and geometry supports a surprising number:
 * distance, angle, height, speed, what the victim was doing.
 *
 * <p>Every honour below is therefore in one of three states, stated rather than implied:
 *
 * <ul>
 *   <li><b>Awarded</b> — decided in {@link #judge}, testable, and reachable in play.</li>
 *   <li><b>Not possible</b> — requires state this mod cannot observe. Listed in {@link #UNREACHABLE} with the
 *       missing input, so the gap is documented rather than silently absent.</li>
 *   <li><b>Not implemented</b> — observable in principle, but needs bookkeeping not yet written.</li>
 * </ul>
 *
 * <h2>Why every decision is a pure function</h2>
 *
 * <p>{@link #judge} takes a plain {@link Evidence} record and returns a list. No clock, no world, no server.
 * That makes the entire honour system testable in {@code HonourSim}, which matters because the failure mode
 * of this code is not a crash — it is an award that fires for the wrong reason, which nobody notices until a
 * player complains that they got "绝对制空" for shooting a helicopter once.
 */
final class Honours {
    /** Kills closer than this (in blocks) count as knife-range. */
    static final double CLOSE_RANGE = 4.0;
    /** Kills beyond this count as long-range. */
    static final double LONG_RANGE = 60.0;
    /** Height above the victim that counts as "from above". */
    static final double AIRBORNE_HEIGHT = 9.0;
    /** Consecutive kills within the streak window that switch from a named streak to a rampage honour. */
    static final int RAMPAGE_COUNT = 6;
    /** Speed above which a kill counts as happening while moving fast, in blocks per tick. */
    static final double FAST_SPEED = 0.32;

    /**
     * One kill, reduced to what the honours need.
     *
     * <p>Deliberately flat. Passing the live entities would let a decision read state that has already
     * changed by the time it runs, and would make every rule untestable without a running server.
     *
     * @param distance        attacker to victim, in blocks
     * @param drop            attacker's feet Y minus victim's feet Y; positive means shooting downward
     * @param headshot        the killing blow landed on the head
     * @param explosion       the killing blow was explosive
     * @param streak          consecutive kills including this one
     * @param victimWasStreak the victim was on a streak of their own of at least 3
     * @param victimSpotted   this client had the victim spotted before the kill
     * @param attackerSpotted the attacker was spotted by the enemy team at the time
     * @param backstab        the blow came from behind the victim, within its rear arc
     * @param attackerOnFoot  the attacker was not in a vehicle
     * @param victimInVehicle the victim was in a vehicle
     * @param attackerMoving  the attacker's horizontal speed, in blocks per tick
     * @param victimHealthFrac the victim's health before the killing blow, as a fraction of maximum
     * @param attackerHealthFrac the attacker's health when the kill landed
     * @param blind           the attacker could not see (a flash or blindness effect was active)
     */
    record Evidence(double distance, double drop, boolean headshot, boolean explosion,
                    int streak, boolean victimWasStreak, boolean victimSpotted,
                    boolean attackerSpotted, boolean backstab, boolean attackerOnFoot,
                    boolean victimInVehicle, double attackerMoving, double victimHealthFrac,
                    double attackerHealthFrac, boolean blind) { }

    /**
     * The honours this mod cannot award, and the input each one would need.
     *
     * <p>Written out rather than left as an absence, because "why is there no 最后一发" is a question with a
     * real answer: the magazine state belongs to TaCZ, and the damage event does not carry it.
     */
    static final List<String> UNREACHABLE = List.of(
            "last_bullet_kill — needs the magazine state, which lives inside TaCZ",
            "one_bullet_multi_kill — needs shot grouping, which the damage events do not identify",
            "quick_switch — needs weapon-swap timing, not sent by the gun mods",
            "buff_kill / debuff_kill / both — needs potion state, which the addons do not track",
            "potato_aim — needs the shooter's accuracy history",
            "locked_target — needs the missile lock state from the vehicle addon",
            "farewell / silent kills — need the victim's awareness, which is not observable",
            "leave_it_to_me / savior — need ally threat assessment, not modelled",
            "hold_position / charge_assault / fire_suppression — need positional intent, not observable");

    private Honours() { }

    /**
     * Every honour this kill earns.
     *
     * <p>Ordered from the most specific to the least, so that a kill deserving several reads as its most
     * interesting feature first. A headshot from a helicopter at 80 blocks is "绝对制空", not "击杀".
     *
     * @return honour ids, possibly empty; never {@code null}
     */
    static List<String> judge(Evidence e) {
        List<String> out = new ArrayList<>();
        if (e == null) return out;

        // --- shooting skill -------------------------------------------------------------
        if (e.headshot()) out.add("headshot");
        if (e.explosion() && e.victimInVehicle()) out.add("kill_explosion");
        if (e.distance() >= LONG_RANGE) out.add("long_distance");
        if (e.distance() <= CLOSE_RANGE) out.add("close_quarter");
        // From above, and on foot: a helicopter kill is air control, not "justice from above".
        if (e.drop() >= AIRBORNE_HEIGHT && e.attackerOnFoot()) out.add("justice_from_above");
        // Air-to-ground at long range is the reference pack's "absolute air control".
        if (e.drop() >= AIRBORNE_HEIGHT && !e.attackerOnFoot() && e.distance() >= LONG_RANGE) {
            out.add("absolute_air_control");
        }
        if (e.backstab()) out.add("backstab");
        if (e.blind()) out.add("blind_kill");
        if (e.attackerMoving() >= FAST_SPEED) out.add("moving_kill");
        // Killed at death's door: the reference pack's "desperate counterattack".
        if (e.attackerHealthFrac() <= 0.25) out.add("desperate");

        // --- what the victim was doing ---------------------------------------------------
        if (e.victimWasStreak()) out.add("interrupted_streak");
        // The reference pack's "effortless": the victim was already badly hurt, so this was a formality.
        if (e.victimHealthFrac() <= 0.2) out.add("effortless");

        // --- streaks ---------------------------------------------------------------------
        if (e.streak() >= RAMPAGE_COUNT) {
            // Past five, the reference pack stops naming and starts grading: 嗜血 / 无情 / 英勇 / 凶猛 / 残暴.
            // Graded by streak so a long run reads as escalation rather than one label repeated.
            out.add(switch (Math.min(10, e.streak())) {
                case 6 -> "bloodthirsty";
                case 7 -> "merciless";
                case 8 -> "valiant";
                case 9 -> "fierce";
                default -> "savage";
            });
        }

        // --- spotting --------------------------------------------------------------------
        if (e.victimSpotted()) out.add("spotting_kill");
        if (e.attackerSpotted()) out.add("brave_return");

        return out;
    }

    /**
     * Human-readable name for an honour id.
     *
     * <p>Chinese names follow the reference pack's own translations where the mod's lang file has one, so the
     * same honour reads the same way in both packs — a player moving between them should not have to learn a
     * second vocabulary for the same achievement.
     */
    static String name(String id) {
        if (id == null) return "击杀";
        return switch (id) {
            case "headshot" -> "爆头";
            case "kill_explosion" -> "载具摧毁";
            case "long_distance" -> "远距离击杀";
            case "close_quarter" -> "近身击杀";
            case "justice_from_above" -> "天降正义";
            case "absolute_air_control" -> "绝对制空";
            case "backstab" -> "背刺";
            case "blind_kill" -> "盲杀";
            case "moving_kill" -> "运动中击杀";
            case "desperate" -> "绝境反击";
            case "interrupted_streak" -> "终结连杀";
            case "effortless" -> "轻松击杀";
            case "bloodthirsty" -> "嗜血";
            case "merciless" -> "无情";
            case "valiant" -> "英勇";
            case "fierce" -> "凶猛";
            case "savage" -> "残暴";
            case "spotting_kill" -> "标记击杀";
            case "brave_return" -> "英勇回击";
            case "revenge" -> "复仇";
            case "assist" -> "助攻";
            default -> "击杀";
        };
    }

    /**
     * Glyph kind for an honour, for the icons in {@link FeedIcons}.
     *
     * <p>Mapped onto the small set of shapes the feed actually draws, rather than one glyph per honour: at
     * seven pixels, fifty-six distinct shapes would be indistinguishable from one another.
     */
    static String icon(String id) {
        if (id == null) return "kill";
        return switch (id) {
            case "headshot", "long_distance", "blind_kill" -> "headshot";
            case "kill_explosion", "absolute_air_control" -> "explosion";
            case "interrupted_streak", "brave_return", "revenge" -> "revenge";
            case "spotting_kill" -> "mark";
            case "bloodthirsty", "merciless", "valiant", "fierce", "savage" -> "streak";
            case "justice_from_above", "backstab" -> "aerial";
            default -> "kill";
        };
    }

    /** How much score an honour is worth, or 0 when it is purely cosmetic. */
    static int points(String id) {
        if (id == null) return 0;
        return switch (id) {
            case "absolute_air_control" -> 100;
            case "interrupted_streak", "justice_from_above", "blind_kill" -> 50;
            case "backstab", "long_distance", "desperate" -> 30;
            case "headshot" -> 25;
            default -> 0;
        };
    }
}
