package cn.bfops.crawl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class ProneGeometry {
    public record Boxes(AABB body,AABB head,AABB feet){}
    public static Boxes boxes(AABB body,float yaw,double headOffset,double footOffset){
        double radians=Math.toRadians(yaw);Vec3 forward=new Vec3(-Math.sin(radians),0,Math.cos(radians));
        return new Boxes(body,body.move(forward.scale(headOffset)),body.move(forward.scale(-footOffset)));
    }
}
