package cn.bfops;

/** The same inclusive cylinder is used by server presence and client capture feedback. */
final class ObjectiveZone {
    static boolean horizontal(Model.Point p,double x,double z){return AreaShape.horizontal(x,z,p.x,p.z,p.radius,p.boundary);}
    static boolean contains(Model.Point p,double x,double y,double z){
        return horizontal(p,x,z)&&Math.abs(y-p.y)<=p.height/2.0+1e-7;
    }
}
