from pathlib import Path
r=Path('src/main/java/cn/bfops')
p=r/'Engine.java';s=p.read_text('utf-8');s=s.replace('public boolean killCredited;', '''public boolean killCredited;
        boolean claimKill() {
            if (killCredited) return false;
            killCredited = true;
            return true;
        }''');s=s.replace('member.killCredited = true;\n        creditKill(victim, attacker);','if (member.claimKill()) creditKill(victim, attacker);');s=s.replace('Member credited = member.killCredited ? null : creditKill(player, killer);','Member credited = member.claimKill() ? creditKill(player, killer) : null;');s=s.replace('if (ticks - otherMember.reviveHeldAt > 6 || !canRescue(other, patient))', 'if (!Medic.continuingRescue(ticks, otherMember.reviveHeldAt, canRescue(other, patient)))');s=s.replace('member.alive = false;\n                    member.readyAt', 'member.alive = false;\n                    member.downed = false;\n                    member.reviveProgress = 0;\n                    member.reviving = null;\n                    clearReviver(player.getUUID());\n                    downPose(player, false);\n                    member.readyAt');p.write_text(s,'utf-8')
p=r/'Medic.java';s=p.read_text('utf-8').replace('final class Medic {','''final class Medic {
    static boolean continuingRescue(long now, long lastHeld, boolean validTarget) {
        return validTarget && now >= lastHeld && now - lastHeld <= 6;
    }
''');p.write_text(s,'utf-8')
Path('sim/DownedInteractionSim.java').write_text('''package cn.bfops;
public final class DownedInteractionSim {
    public static void main(String[] args) {
        var member = new Engine.Member("arena", "victim", 0);
        check(member.claimKill(), "knockdown earns kill");
        check(!member.claimKill(), "bleed-out cannot earn second kill");
        member.killCredited = false; // successful revive or new deployment
        check(member.claimKill(), "next life earns a new kill");
        check(!member.claimKill(), "duplicate death callback is ignored");
        check(!Medic.continuingRescue(0, -100, true), "no initial hold");
        check(Medic.continuingRescue(104, 100, true), "four-tick heartbeat accepted");
        check(!Medic.continuingRescue(107, 100, true), "release times out");
        check(!Medic.continuingRescue(104, 100, false), "switch weapon, look away, wall or range interrupts immediately");
        check(!Medic.continuingRescue(99, 100, true), "future heartbeat rejected");
        int progress = 0;
        for (int t = 0; t < 80; t++) progress = Medic.progress(progress, Medic.continuingRescue(t, t - t % 4, true), 80);
        check(progress == 80, "holding right-click completes configured revive");
        progress = 0;
        for (int t = 0; t < 80; t++) progress = Medic.progress(progress, Medic.continuingRescue(t, 0, true), 80);
        check(progress == 0, "single click cannot finish by standing nearby");
        check(FeedIcons.width("kill") == FeedIcons.width("headshot"), "both skulls have same layout");
        System.out.println("DownedInteractionSim: 12 checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
''','utf-8')
