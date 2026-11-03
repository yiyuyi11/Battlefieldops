package cn.bfops;

import java.util.*;
import java.util.zip.*;
import java.nio.file.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact resource transfer plus CPU geometry/UV/normal/rotation verification. No GPU image assertion. */
public final class ReferenceGeometrySim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static boolean close(float a,float b){return Math.abs(a-b)<.0001;}
    static JsonObject cube(){return JsonParser.parseString("{\"minecraft:geometry\":[{\"description\":{\"texture_width\":16,\"texture_height\":16},\"bones\":[{\"name\":\"root\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[16,16,16],\"uv\":{\"north\":{\"uv\":[0,0],\"uv_size\":[16,16]}}}]}]}]}").getAsJsonObject();}
    static JsonObject bone(JsonObject d){return d.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject();}
    public static void main(String[] args)throws Exception{
        var raw=cube();var mesh=ReferenceGeometry.bake(raw);check(mesh.cubes()==1&&mesh.vertices().size()==4,"one supplied face produces one quad");
        var first=mesh.vertices().getFirst();check(close(first.x(),-1)&&close(first.y(),1)&&close(first.z(),0),"Bedrock origin mirrors X exactly like GeckoLib");
        check(close(first.u(),1)&&close(first.v(),0),"non-mirrored face has reference U reversal");
        check(close(first.nx(),0)&&close(first.ny(),0)&&close(first.nz(),-1),"north normal");
        bone(raw).add("rotation",JsonParser.parseString("[90,0,0]"));first=ReferenceGeometry.bake(raw).vertices().getFirst();check(close(first.y(),0)&&close(first.z(),-1),"bone X rotation uses reference negative sign");
        raw=cube();bone(raw).getAsJsonArray("cubes").get(0).getAsJsonObject().add("rotation",JsonParser.parseString("[0,0,90]"));first=ReferenceGeometry.bake(raw).vertices().getFirst();check(close(first.x(),-1)&&close(first.y(),-1),"cube pivot Z rotation uses reference positive sign");
        raw=cube();bone(raw).addProperty("parent","root");try{ReferenceGeometry.bake(raw);throw new AssertionError("cycle accepted");}catch(IllegalArgumentException expected){checks++;}
        int[] expectedCubes={11,12,18,19,31,32};String[] names={"ammo_bag","medic_bag","ammo_box","med_box","radio_beacon","ground_sensor"};
        try(var shipped=new ZipFile("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar");var reference=new ZipFile("gd656conquest-0.10.6-normal.jar")){
            for(int i=0;i<names.length;i++){
                String name=names[i],geo="geo/"+name+".geo.json",png="textures/item/"+name+".png";
                for(String path:List.of(geo,png)){
                    byte[] a=shipped.getInputStream(shipped.getEntry("assets/battlefield_ops/"+path)).readAllBytes();
                    byte[] b=reference.getInputStream(reference.getEntry("assets/gd656conquest/"+path)).readAllBytes();check(Arrays.equals(a,b),"original bytes retained: "+path);
                }
                try(var input=shipped.getInputStream(shipped.getEntry("assets/battlefield_ops/"+png))){check(ImageIO.read(input)!=null,"valid original texture");}
                JsonObject document;try(var input=shipped.getInputStream(shipped.getEntry("assets/battlefield_ops/"+geo))){document=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                var model=ReferenceGeometry.bake(document);check(model.cubes()==expectedCubes[i],"all original cubes baked: "+name);check(!model.vertices().isEmpty()&&model.vertices().size()%4==0,"quad stream: "+name);
                int faces=0;for(var b:document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))if(b.getAsJsonObject().has("cubes"))for(var c:b.getAsJsonObject().getAsJsonArray("cubes"))faces+=c.getAsJsonObject().getAsJsonObject("uv").size();
                check(model.vertices().size()==faces*4,"no source per-face UV omitted: "+name);
                for(var v:model.vertices()){
                    check(Float.isFinite(v.x())&&Float.isFinite(v.y())&&Float.isFinite(v.z())&&Math.abs(v.x())<16&&Math.abs(v.y())<16&&Math.abs(v.z())<16,"bounded finite model vertex");
                    check(Float.isFinite(v.u())&&Float.isFinite(v.v()),"finite fractional UV");
                    check(Math.abs(v.nx()*v.nx()+v.ny()*v.ny()+v.nz()*v.nz()-1)<.001,"unit transformed normal");
                }
            }
        }
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/Client$Events.class"))).accept(n,0);boolean renderer=false;
        for(var method:n.methods)if(method.name.equals("referenceProps"))for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode m&&m.owner.equals("cn/bfops/ReferenceProps")&&m.name.equals("render"))renderer=true;
        check(renderer,"actual RenderLiving pre-event connected to prop model renderer");
        String engine=Files.readString(Path.of("src/main/java/cn/bfops/Engine.java"));check(!engine.contains("new ItemStack(Items.SEA_LANTERN)"),"beacon creation/restore no longer equips placeholder sea-lantern block");
        System.out.println("ReferenceGeometrySim: "+checks+" geometry, UV, rotation, normals, original-resource and wiring checks passed (no GPU test)");
    }
}
