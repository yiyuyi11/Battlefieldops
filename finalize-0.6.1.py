from pathlib import Path
r=Path('src/main/java/cn/bfops')
(r/'CrawlPose.java').write_text('''package cn.bfops;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** NeoForge adds forced-pose methods at runtime; the offline compile jar is unpatched. */
final class CrawlPose {
    private static final Map<Player, Pose> previous = new WeakHashMap<>();
    private static final Method SET = method("setForcedPose", Pose.class);
    private static final Method GET = method("getForcedPose");
    private static Method method(String name, Class<?>... args) {
        try { return Player.class.getMethod(name, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("NeoForge forced-pose API missing", e); }
    }
    static synchronized void apply(Player player, boolean downed) {
        if (player == null) return;
        try {
            if (downed) {
                if (!previous.containsKey(player)) previous.put(player, (Pose) GET.invoke(player));
                SET.invoke(player, Pose.SWIMMING);
                player.setPose(Pose.SWIMMING);
                player.setSprinting(false);
            } else if (previous.containsKey(player)) {
                Pose old = previous.remove(player);
                SET.invoke(player, new Object[]{old});
                // Vanilla checks headroom and chooses standing/crouching on its next tick.
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot update downed pose", e);
        }
    }
}
''','utf-8')
p=r/'Engine.java';s=p.read_text('utf-8').replace('player.setPose(downed ? net.minecraft.world.entity.Pose.SWIMMING : net.minecraft.world.entity.Pose.STANDING);\n        if (downed) player.setSprinting(false);','CrawlPose.apply(player, downed);')
s=s.replace('// view and their gun, and is limited only by being on the floor and slow. Slowness I is the crawl\n        // itself rather than an extra penalty, and the swimming pose is what visibly puts them on the ground.','// view and their gun; crawling uses an owned speed modifier instead of potion effects.')
s=s.replace('helper.displayClientMessage(Component.literal("§a正在救援 §f" + targetMember.name\n                + "§a，空手对准并按住右键…"), true);','helper.displayClientMessage(Component.literal("§b救援 " + targetMember.name + " "\n                + (int)(100 * Medic.fraction(targetMember.reviveProgress, Medic.reviveTicks(matches.get(targetMember.map).rules.map.reviveSeconds)))\n                + "% · 保持空手并按住右键"), true);')
p.write_text(s,'utf-8')
p=r/'Client.java';s=p.read_text('utf-8');s=s.replace('if (me != null && me.has("downed") && me.get("downed").getAsBoolean()) {\n                    mc.player.setPose(net.minecraft.world.entity.Pose.SWIMMING);\n                    mc.player.setSprinting(false);\n                }','CrawlPose.apply(mc.player, me != null && me.has("downed") && me.get("downed").getAsBoolean());');s=s.replace('public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {','public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {\n            CrawlPose.apply(Minecraft.getInstance().player, false);');p.write_text(s,'utf-8')
p=r/'CombatHud.java';s=p.read_text('utf-8');needle='int outside=Engine.num(me,"outside",0);';s=s.replace(needle,'''if (me.has("downed") && me.get("downed").getAsBoolean()) {
            double fraction = Medic.fraction(Engine.num(state,"reviveProgress",0), Medic.reviveTicks(arena.reviveSeconds));
            if (fraction > 0) {
                int px=width/2-60, py=height/2+4;
                g.fill(px-3,py-3,px+123,py+21,0xe0080d12);
                g.drawCenteredString(mc.font,"正在救援 "+(int)(fraction*100)+"%",width/2,py,0xff65dbe8);
                g.fill(px,py+13,px+(int)(120*fraction),py+17,0xff65dbe8);
            }
        }
        '''+needle);p.write_text(s,'utf-8')
p=r/'Medic.java';s=p.read_text('utf-8').replace('cannot fight','can crawl slowly').replace('holding the revive key','holding empty-hand right-click');p.write_text(s,'utf-8')
p=Path('install-0.6.1.ps1');s=Path('install-0.6.0.ps1').read_text('utf-8').replace('0.6.0','0.6.1');s=s.replace("'battlefield-ops-0.5.9-neoforge-1.21.1.jar'))", "'battlefield-ops-0.5.9-neoforge-1.21.1.jar','battlefield-ops-0.6.0-neoforge-1.21.1.jar','battlefield-ops-0.6.1-neoforge-1.21.1.jar'))");p.write_text(s,'utf-8-sig')
