package cn.bfops;

/** Optional shared physical hub. Unconfigured servers keep the original inventory/position restoration. */
final class LobbySettings {
    boolean enabled;
    String dimension="minecraft:overworld";
    double x,y=64,z;
    float yaw,pitch;
    void validate(){
        Model.require(dimension!=null&&dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"大厅维度无效");
        Model.require(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=29999984&&Math.abs(z)<=29999984&&y>=-2048&&y<=2048,"大厅坐标无效");
        Model.require(Float.isFinite(yaw)&&Float.isFinite(pitch)&&pitch>=-90&&pitch<=90,"大厅朝向无效");
    }
}
