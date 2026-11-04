package cn.bfops;

import java.util.*;

/** World-coordinate footprint shared by editing, capture, deployment and visualisation. */
record AreaShape(double x,double y,double z,double radius,double height,List<Model.Vec> boundary) {
    static AreaShape of(Model.Point p){return new AreaShape(p.x,p.y,p.z,p.radius,p.height,p.boundary);}
    static AreaShape of(Model.Spawn p){return new AreaShape(p.x,p.y,p.z,p.radius,p.height,p.boundary);}
    boolean polygonal(){return boundary!=null&&boundary.size()>=3;}
    boolean active(){return polygonal()||radius>0;}
    static boolean horizontal(double px,double pz,double x,double z,double radius,List<Model.Vec> polygon){
        if(polygon!=null&&polygon.size()>=3)return Model.contains(polygon,px,pz);
        double dx=px-x,dz=pz-z;return dx*dx+dz*dz<=radius*radius+1e-7;
    }
    boolean contains(double px,double pz){return horizontal(px,pz,x,z,radius,boundary);}
    List<Model.Vec> outline(){
        if(polygonal())return boundary;
        if(radius<=0)return List.of();
        var out=new ArrayList<Model.Vec>();
        for(int i=0;i<48;i++){double a=i*Math.PI/24;out.add(new Model.Vec(x+Math.cos(a)*radius,z+Math.sin(a)*radius));}
        return out;
    }
    double[] bounds(){
        if(!polygonal())return new double[]{x-radius,z-radius,x+radius,z+radius};
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(var v:boundary){minX=Math.min(minX,v.x());minZ=Math.min(minZ,v.z());maxX=Math.max(maxX,v.x());maxZ=Math.max(maxZ,v.z());}
        return new double[]{minX,minZ,maxX,maxZ};
    }
    static Model.Vec anchor(List<Model.Vec> polygon,double x,double z){
        if(Model.contains(polygon,x,z))return new Model.Vec(x,z);
        var bounds=new AreaShape(0,0,0,0,0,polygon).bounds();
        double cx=(bounds[0]+bounds[2])/2,cz=(bounds[1]+bounds[3])/2;
        if(Model.contains(polygon,cx,cz))return new Model.Vec(cx,cz);
        for(int i=1;i<=32;i++)for(int j=1;j<=32;j++){
            double px=bounds[0]+(bounds[2]-bounds[0])*i/33,pz=bounds[1]+(bounds[3]-bounds[1])*j/33;
            if(Model.contains(polygon,px,pz))return new Model.Vec(px,pz);
        }
        return polygon.get(0);
    }
    static void validatePolygon(List<Model.Vec> polygon){
        Model.require(polygon!=null&&polygon.size()>=3&&polygon.size()<=64,"点位范围需要 3～64 个顶点");
        for(var vertex:polygon){Model.require(vertex!=null,"点位范围存在空顶点");Model.pos(vertex.x(),0,vertex.z());}
        double twice=0;
        for(int i=0;i<polygon.size();i++){
            var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());
            Model.require(a!=null&&b!=null&&!a.equals(b),"点位范围存在空顶点或相邻重复点");Model.pos(a.x(),0,a.z());
            twice+=a.x()*b.z()-b.x()*a.z();
            for(int j=i+2;j<polygon.size();j++){
                if(i==0&&j==polygon.size()-1)continue;
                Model.require(!Model.intersect(a,b,polygon.get(j),polygon.get((j+1)%polygon.size())),"点位范围不能自交");
            }
        }
        Model.require(Math.abs(twice)>1,"点位范围面积过小");
        var b=new AreaShape(0,0,0,0,0,polygon).bounds();
        Model.require(b[2]-b[0]<=512&&b[3]-b[1]<=512,"单个点位范围长宽最多 512 格");
    }
    void validate(Model.Arena map,String name){
        Model.require(boundary!=null,"点位范围数据为空："+name);
        if(boundary.isEmpty())return;
        validatePolygon(boundary);
        Model.require(contains(x,z),name+" 的中心标记须位于自定义范围内");
        for(int i=0;i<boundary.size();i++){
            var a=boundary.get(i);var b=boundary.get((i+1)%boundary.size());
            Model.require(Model.contains(map.boundary,a.x(),a.z()),name+" 的范围须位于地图内");
            // A segment crossing a concave map edge is invalid even when both endpoints are inside.
            for(int j=0;j<map.boundary.size();j++){
                var c=map.boundary.get(j);var d=map.boundary.get((j+1)%map.boundary.size());
                Model.require(!(Model.cross(a,b,c)*Model.cross(a,b,d)<-1e-8&&Model.cross(c,d,a)*Model.cross(c,d,b)<-1e-8),name+" 的边线穿出了地图边界");
            }
            Model.require(Model.contains(map.boundary,(a.x()+b.x())/2,(a.z()+b.z())/2),name+" 的边线须位于地图内");
        }
    }
    static void translate(List<Model.Vec> polygon,double dx,double dz){
        if(polygon!=null)for(int i=0;i<polygon.size();i++){var v=polygon.get(i);polygon.set(i,new Model.Vec(v.x()+dx,v.z()+dz));}
    }
}
