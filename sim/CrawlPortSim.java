package cn.bfops;
import cn.bfops.crawl.CrawlWindow;
import cn.bfops.crawl.ProneGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CrawlPortSim {
    static int checks;
    static void check(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    public static void main(String[] args){
        var guard=new CrawlWindow(false,1000);
        check(!guard.blocked(1000,1000,500),"initial standing player may shoot immediately");
        guard.observe(true,1200);check(guard.blocked(2199,1000,500),"enter protection lasts 1000 ms");
        check(!guard.blocked(2200,1000,500),"enter protection boundary");
        guard.observe(true,2300);check(!guard.blocked(2300,1000,500),"repeated tick does not restart protection");
        guard.observe(false,2400);check(guard.blocked(2899,1000,500),"exit protection lasts 500 ms");
        check(!guard.blocked(2900,1000,500),"exit protection boundary");
        check(!guard.blocked(2400,0,0),"zero settings disable protection");
        var body=new AABB(-.3,64,-.3,.3,64.6,.3);
        for(float yaw:new float[]{0,90,180,270}){
            var boxes=ProneGeometry.boxes(body,yaw,.42,.62);
            double r=Math.toRadians(yaw);var forward=new Vec3(-Math.sin(r),0,Math.cos(r));
            check(Math.abs(boxes.head().getCenter().subtract(body.getCenter()).dot(forward)-.42)<1e-9,"head extends forward");
            check(Math.abs(boxes.feet().getCenter().subtract(body.getCenter()).dot(forward)+.62)<1e-9,"feet extend backward");
            check(boxes.head().maxY==64.6&&boxes.feet().maxY==64.6,"lying parts remain low");
            Vec3 above=boxes.head().getCenter().add(0,2,0),below=above.add(0,-4,0);
            check(boxes.head().clip(above,below).isPresent(),"prone head remains hittable");
            Vec3 along=boxes.feet().getCenter();
            check(boxes.feet().clip(along.add(0,2,0),along.add(0,-2,0)).isPresent(),"extended feet remain hittable");
        }
        check(SquadRules.inCombat(1140,1139),"reference 140 tick combat lock active");
        check(!SquadRules.inCombat(1140,1140),"combat lock expires at boundary");
        System.out.println("CrawlPortSim: "+checks+" checks passed");
    }
}
