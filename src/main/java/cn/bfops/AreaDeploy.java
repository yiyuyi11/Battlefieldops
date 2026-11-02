package cn.bfops;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;

final class AreaDeploy {
    static AreaShape shape(Model.Arena map,String id){
        for(var spawn:map.spawns)if(spawn.id.equals(id))return AreaShape.of(spawn);
        for(var point:map.points)if(Deploy.pointId(point).equals(id))return AreaShape.of(point);
        return null; // Squad, beacon and occupied-seat destinations keep their exact validated positions.
    }
    static boolean footprint(AreaShape area,AABB box){
        if(area==null||!area.active())return true;
        return area.contains(box.minX,box.minZ)&&area.contains(box.minX,box.maxZ)
                &&area.contains(box.maxX,box.minZ)&&area.contains(box.maxX,box.maxZ)
                &&area.contains((box.minX+box.maxX)/2,box.minZ)&&area.contains((box.minX+box.maxX)/2,box.maxZ)
                &&area.contains(box.minX,(box.minZ+box.maxZ)/2)&&area.contains(box.maxX,(box.minZ+box.maxZ)/2);
    }
    static Model.Spawn choose(ServerLevel level,ServerPlayer player,Deploy.Site site,Model.Arena map,int team,
            String mobility,double width,double height,Predicate<Model.Spawn> allowed){
        var area=shape(map,site.id());var exact=site.asSpawn(team);
        if(area==null||!area.active())return exact;
        double[] bounds=area.bounds();int loaded=0;
        for(int attempt=0;attempt<40;attempt++){
            double x=attempt==39?area.x():bounds[0]+level.random.nextDouble()*(bounds[2]-bounds[0]);
            double z=attempt==39?area.z():bounds[1]+level.random.nextDouble()*(bounds[3]-bounds[1]);
            if(!area.contains(x,z))continue;
            var block=BlockPos.containing(x,area.y(),z);
            if(!level.hasChunkAt(block)){
                if(loaded>=1)continue;
                level.getChunkAt(block);loaded++;
            }
            double surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,block.getX(),block.getZ());
            double[] ys=mobility.equals("A")?new double[]{area.y()}
                    :new double[]{area.y(),surface,area.y()+1,area.y()-1,area.y()+2,area.y()-2,area.y()+3,area.y()-3};
            for(double y:ys){
                if(Math.abs(y-area.y())>area.height()/2)continue;
                var box=new AABB(x-width/2,y,z-width/2,x+width/2,y+height,z+width/2);
                if(!footprint(area,box)||!level.noCollision(player,box))continue;
                if(!mobility.equals("A")){
                    if(!level.getFluidState(BlockPos.containing(x,y,z)).isEmpty())continue;
                    if(level.noCollision(player,new AABB(x-.25,y-.12,z-.25,x+.25,y-.01,z+.25)))continue;
                }
                Model.Spawn result=site.asSpawn(team);result.x=x;result.y=y;result.z=z;
                if(allowed.test(result))return result;
            }
        }
        throw new IllegalArgumentException("出生范围内暂时没有安全空间，请选择其他部署点或调整范围与高度");
    }
    private AreaDeploy(){}
}
