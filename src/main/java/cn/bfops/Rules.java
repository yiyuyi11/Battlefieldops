package cn.bfops;

import java.util.*;

/**
 * Deterministic capture / ticket simulation, independent of Minecraft so it can be verified
 * without a running server.
 *
 * <p>Capture uses a 0-100 control bar rather than a boolean flag: every point stores
 * {@code progress}, where 100 is fully held by team 0, 0 fully held by team 1 and 50 neutral.
 * Each step counts both sides' presence and moves the bar by the net ratio
 * {@code (a-b)/(a+b)}. Three attackers versus one defender still advance, just slower; an even
 * fight stalls; a defender cannot freeze a capture by standing in the zone. Ownership is derived
 * from the bar, so crossing the midpoint neutralises a point before it flips.
 *
 * <p>Scoring rules (as confirmed by the operator):
 * <ul>
 *   <li>CONQUEST - both teams own a ticket pool; the side holding fewer points bleeds tickets each
 *       second in proportion to the point deficit, and deaths cost tickets.</li>
 *   <li>BREAKTHROUGH - only the attacker (team 0) owns a pool; every point of the current stage must
 *       be taken to advance, the final stage wins, and the defender wins on timeout.</li>
 *   <li>Kills and captures only award personal score; they never unlock kits.</li>
 *   <li>DEATHMATCH - enemy eliminations increase a separate team score; no flag capture or ticket
 *       drain. First to the target wins; on timeout the higher score wins, or equal scores draw.</li>
 * </ul>
 */
public final class Rules {
    /** Per-point capture state. {@code progress} is the 0-100 control bar. */
    public static final class Flag {
        public int owner = -1;
        public double progress;
        public int driving = -1;
        public boolean contested;
    }

    /** A completed ownership change, reported so the caller can award personal score. */
    public record Capture(int point, int team, boolean neutralised) {}

    public final Model.Arena map;
    public final List<Flag> flags = new ArrayList<>();
    public final int[] tickets;
    /** Team eliminations are separate from personal score and from ticket pools. */
    public final int[] teamScores = {0,0};
    public record Notice(int team, String text) {}
    private final List<Notice> notices = new ArrayList<>();
    private final boolean[][] milestones = new boolean[2][3];
    private int previousLeader = -1;
    public int stage, elapsed = 0, winner = -2;
    public boolean rushArmed;

    public Rules(Model.Arena map) {
        this.map = map;
        this.tickets = new int[]{map.tickets, map.tickets};
        // A firing range is not a match: no capture points, no ticket pools, nothing to win.
        if (map.isTraining()) return;
        for (Model.Point ignored : map.points) {
            Flag flag = new Flag();
            if (isStaged()) {
                // The defender starts holding every stage; the attacker pushes the bar to 100.
                flag.owner = 1;
                flag.progress = 0;
            } else {
                flag.progress = 50;
            }
            flags.add(flag);
        }
    }

    public boolean isBreakthrough(){return map.mode.equals("BREAKTHROUGH");}
    public boolean isStaged() {
        return map.isStaged();
    }

    public boolean active(int index) {
        return !map.isDeathmatch() && (!isStaged() || map.points.get(index).sector == stage);
    }

    public int stageCount() {
        return map.points.stream().mapToInt(p -> p.sector).max().orElse(0) + 1;
    }

    /** Points currently held by each team, used by the HUD and by the conquest bleed. */
    public int[] owned() {
        int[] owned = {0, 0};
        for (Flag flag : flags) if (flag.owner >= 0) owned[flag.owner]++;
        return owned;
    }

    public List<Capture> tick(int[][] presence) {
        if (map.isTraining()) {
            // Only the clock runs; everything else (capture bars, bleed, end conditions) is match logic.
            elapsed++;
            return java.util.List.of();
        }
        List<Capture> captures = new ArrayList<>();
        if (winner != -2) return captures;
        elapsed++;
        if(map.isDeathmatch()){checkEnd();return captures;}
        // One call == one second of match time, so a fully uncontested side walks the whole
        // 100-point bar in exactly captureSeconds seconds and a neutral point flips in half that.
        double delta = 100.0 / Math.max(1, map.captureSeconds);

        for (int i = 0; i < flags.size(); i++) {
            if (!active(i)||map.isRush()) continue;
            Flag flag = flags.get(i);
            int a = presence[i][0], b = presence[i][1];
            flag.contested = a > 0 && b > 0;
            int previous = flag.owner;

            double ratio = (a + b == 0) ? 0 : (double) (a - b) / (a + b);
            flag.driving = a == b ? -1 : (a > b ? 0 : 1);
            flag.progress = clamp(flag.progress + ratio * delta, 0, 100);
            flag.owner = owner(previous, flag.progress);
            if (flag.owner != previous) {
                captures.add(new Capture(i, flag.owner, flag.owner == -1));
            }
        }

        if (isStaged()) {
            boolean cleared = true;
            for (int i = 0; i < flags.size(); i++) {
                if (active(i) && flags.get(i).owner != 0) cleared = false;
            }
            if (cleared) {
                if (stage >= stageCount() - 1) {
                    winner = 0;
                } else {
                    stage++;
                    tickets[0] = Math.min(100000, tickets[0] + map.sectorBonus);
                }
            }
        } else {
            int[] owned = owned();
            if (owned[0] != owned[1]) {
                int losing = owned[0] > owned[1] ? 1 : 0;
                tickets[losing] = Math.max(0, tickets[losing] - map.bleed * Math.abs(owned[0] - owned[1]));
            }
        }

        checkEnd();
        return captures;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Ownership follows the control bar, so crossing the midpoint neutralises a point first. */
    private static int owner(int previous, double progress) {
        return switch (previous) {
            case 0 -> progress > 50 ? 0 : -1;
            case 1 -> progress < 50 ? 1 : -1;
            default -> progress >= 99.999 ? 0 : progress <= 0.001 ? 1 : -1;
        };
    }

    public void death(int team) {
        if (winner != -2) return;
        if (tracksTickets(team)) tickets[team] = Math.max(0, tickets[team] - map.deathCost);
        checkEnd();
    }

    /** Breakthrough only exposes the attacker pool; conquest exposes both. */
    public boolean tracksTickets(int team) {
        return team>=0&&team<2&&!map.isDeathmatch()&&(!isStaged() || team == 0);
    }

    /** Called only from the server's once-per-elimination credit path, never from a client score. */
    public void kill(int killerTeam,int victimTeam) {
        if(!map.isDeathmatch()||winner!=-2||killerTeam<0||killerTeam>1||victimTeam<0||victimTeam>1||killerTeam==victimTeam)return;
        int score=++teamScores[killerTeam],target=Math.max(1,map.targetKills);
        if(score>=target){winner=killerTeam;return;}
        if(!milestones[killerTeam][0]&&(long)score*2>=target){milestones[killerTeam][0]=true;notices.add(new Notice(killerTeam,"累计击杀已过半"));}
        if(!milestones[killerTeam][1]&&(long)score*5>=(long)target*4){milestones[killerTeam][1]=true;notices.add(new Notice(killerTeam,"距离目标还剩 "+(target-score)+" 次击杀"));}
        if(score>teamScores[1-killerTeam]&&previousLeader!=killerTeam){
            if(previousLeader>=0)notices.add(new Notice(killerTeam,"已经反超比分"));
            previousLeader=killerTeam;
        }
        if(!milestones[killerTeam][2]&&(long)(score-teamScores[1-killerTeam])*5>=target){milestones[killerTeam][2]=true;notices.add(new Notice(killerTeam,"保持领先优势"));}
    }

    public List<Notice> drainNotices(){var out=List.copyOf(notices);notices.clear();return out;}

    /** Ticket death is settled before personal credit, so its final kill must still be paid. */
    public boolean acceptsKillCredit(){return !map.isDeathmatch()||winner==-2;}

    private void checkEnd() {
        if (winner != -2) return;
        // A firing range never ends: players stay to test weapons and loadouts.
        if(map.isTraining())return;
        if(map.isDeathmatch()){
            if(elapsed>=map.roundMinutes*60)winner=teamScores[0]==teamScores[1]?-1:teamScores[0]>teamScores[1]?0:1;
            return;
        }
        if(map.isRush()&&(rushArmed||java.util.stream.IntStream.range(0,flags.size()).filter(this::active).allMatch(i->flags.get(i).owner==0)))return; // A live charge gets its final detonation/defuse window.
        if (tickets[0] <= 0) {
            winner = !isStaged() && tickets[1] <= 0 ? -1 : 1;
            return;
        }
        if (!isStaged() && tickets[1] <= 0) {
            winner = 0;
            return;
        }
        if (elapsed >= map.roundMinutes * 60) {
            if (isStaged()) {
                winner = 1;
            } else {
                winner = Integer.compare(tickets[0], tickets[1]) == 0 ? -1 : tickets[0] > tickets[1] ? 0 : 1;
            }
        }
    }

    public String result() {
        String verdict = switch (winner) {
            case -1 -> "平局";
            case 0 -> isStaged() ? "机械轴心 / 进攻方胜利" : "机械轴心胜利";
            case 1 -> isStaged() ? "格雷同盟 / 防守方胜利" : "格雷同盟胜利";
            default -> "进行中";
        };
        return map.isDeathmatch()&&winner!=-2?verdict+" · 团队击杀 "+Factions.name(map,0)+" "+teamScores[0]+" : "+Factions.name(map,1)+" "+teamScores[1]:verdict;
    }
}
