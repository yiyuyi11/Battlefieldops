package cn.bfops;

/** The same inclusive cylinder is used by server presence and client capture feedback. */
final class ObjectiveZone {
    static boolean contains(Model.Point p,double x,double y,double z){
        double dx=x-p.x,dz=z-p.z;
        return dx*dx+dz*dz<=p.radius*p.radius+1e-7&&Math.abs(y-p.y)<=p.height/2.0+1e-7;
    }
}
