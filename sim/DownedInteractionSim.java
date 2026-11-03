package cn.bfops;
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
