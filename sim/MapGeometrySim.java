package cn.bfops;
import java.util.Arrays;
public final class MapGeometrySim {
    private static int passed;
    private static void check(boolean value){if(!value)throw new AssertionError("clip "+passed);passed++;}
    public static void main(String[] args){
        check(Arrays.equals(MapGeometry.clip(-100000,50,100000,50,0,0,100,100),new int[]{0,50,100,50}));
        check(Arrays.equals(MapGeometry.clip(50,-100000,50,100000,0,0,100,100),new int[]{50,0,50,100}));
        check(MapGeometry.clip(-10,0,-10,100,0,0,100,100)==null);
        check(Arrays.equals(MapGeometry.clip(10,10,90,90,0,0,100,100),new int[]{10,10,90,90}));
        check(Arrays.equals(MapGeometry.clip(200,200,-100,-100,0,0,100,100),new int[]{100,100,0,0}));
        check(MapGeometry.clip(110,120,200,130,0,0,100,100)==null);
        check(Arrays.equals(MapGeometry.clip(0,0,0,0,0,0,100,100),new int[]{0,0,0,0}));
        check(MapGeometry.clip(-1,-1,-1,-1,0,0,100,100)==null);
        System.out.println("MAP GEOMETRY: "+passed+" assertions passed");
    }
}
