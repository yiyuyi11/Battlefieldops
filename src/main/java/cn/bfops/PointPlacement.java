package cn.bfops;
final class PointPlacement {
    static void requireFinite(double x,double y,double z){
        Model.require(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z),"地形尚未加载，无法确定高度；请靠近后重试，或手动填写坐标");
    }
    static void move(Model.Point point,double x,double y,double z){requireFinite(x,y,z);point.x=x;point.y=y;point.z=z;}
    static void move(Model.Spawn point,double x,double y,double z){requireFinite(x,y,z);point.x=x;point.y=y;point.z=z;}
}
