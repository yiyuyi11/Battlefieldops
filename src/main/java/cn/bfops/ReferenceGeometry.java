package cn.bfops;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

/** Static Bedrock geometry baked with GeckoLib's axis, pivot, Z/Y/X and per-face UV conventions. */
final class ReferenceGeometry {
    record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz){}
    record Mesh(List<Vertex> vertices,int cubes){}
    private static final String[] FACES={"west","east","north","south","up","down"};
    private static final int[][] INDICES={{3,2,0,1},{4,5,7,6},{2,4,6,0},{5,3,1,7},{3,5,4,2},{0,6,7,1}};
    private static final float[][] NORMALS={{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,1,0},{0,-1,0}};
    static Mesh bake(JsonObject document){
        JsonObject geo=document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description=geo.getAsJsonObject("description");float tw=description.get("texture_width").getAsFloat(),th=description.get("texture_height").getAsFloat();
        Model.require(tw>0&&th>0&&tw<=4096&&th<=4096,"Invalid prop texture size");
        Map<String,JsonObject> bones=new LinkedHashMap<>();
        for(var e:geo.getAsJsonArray("bones")){var b=e.getAsJsonObject();Model.require(bones.put(b.get("name").getAsString(),b)==null,"Duplicate prop bone");}
        Model.require(bones.size()<=256,"Too many prop bones");
        List<Vertex> out=new ArrayList<>();int cubes=0;
        for(var bone:bones.values()){
            if(!bone.has("cubes")||bool(bone,"neverRender"))continue;
            Matrix4f parent=boneMatrix(bone,bones,new HashSet<>());
            for(var e:bone.getAsJsonArray("cubes")){
                Model.require(++cubes<=4096,"Too many prop cubes");var cube=e.getAsJsonObject();
                float[] origin=vec(cube,"origin"),size=vec(cube,"size");
                for(float n:size)Model.require(n>=0&&n<=4096,"Invalid prop cube size");
                float inflate=(cube.has("inflate")?cube.get("inflate").getAsFloat():bone.has("inflate")?bone.get("inflate").getAsFloat():0)/16;
                float x=-(origin[0]+size[0])/16,y=origin[1]/16,z=origin[2]/16;
                float x0=x-inflate,x1=x+size[0]/16+inflate,y0=y-inflate,y1=y+size[1]/16+inflate,z0=z-inflate,z1=z+size[2]/16+inflate;
                float[][] positions={{x0,y0,z0},{x0,y0,z1},{x0,y1,z0},{x0,y1,z1},{x1,y1,z0},{x1,y1,z1},{x1,y0,z0},{x1,y0,z1}};
                Matrix4f matrix=new Matrix4f(parent).mul(pivotRotation(cube));Matrix3f normalMatrix=matrix.normal(new Matrix3f());
                boolean mirror=bool(cube,"mirror")||bool(bone,"mirror");
                Model.require(cube.has("uv")&&cube.get("uv").isJsonObject(),"Prop requires per-face UVs");var faces=cube.getAsJsonObject("uv");
                for(int f=0;f<6;f++){
                    if(!faces.has(FACES[f]))continue;var face=faces.getAsJsonObject(FACES[f]);
                    float[] uv=pair(face,"uv"),uvSize=pair(face,"uv_size");
                    float u=uv[0]/tw,v=uv[1]/th,u1=(uv[0]+uvSize[0])/tw,v1=(uv[1]+uvSize[1])/th;
                    if(!mirror){float t=u;u=u1;u1=t;}
                    float[][] texture={{u,v},{u1,v},{u1,v1},{u,v1}};
                    int rotation=face.has("uv_rotation")?Math.floorMod(face.get("uv_rotation").getAsInt(),360)/90:0;
                    int effective=f;if(mirror){if(f==0)effective=1;else if(f==1)effective=0;else if(f==4)effective=5;else if(f==5)effective=4;}
                    Vector3f normal=new Vector3f(NORMALS[f]);if(mirror)normal.x=-normal.x;normal.mul(normalMatrix).normalize();
                    for(int i=0;i<4;i++){
                        Vector3f pos=new Vector3f(positions[INDICES[effective][i]]).mulPosition(matrix);float[] tex=texture[(i+rotation)%4];
                        Model.require(Float.isFinite(pos.x)&&Float.isFinite(pos.y)&&Float.isFinite(pos.z)&&Float.isFinite(tex[0])&&Float.isFinite(tex[1]),"Non-finite prop geometry");
                        out.add(new Vertex(pos.x,pos.y,pos.z,tex[0],tex[1],normal.x,normal.y,normal.z));
                    }
                }
            }
        }
        return new Mesh(List.copyOf(out),cubes);
    }
    private static Matrix4f boneMatrix(JsonObject bone,Map<String,JsonObject> bones,Set<String> active){
        String name=bone.get("name").getAsString();Model.require(active.add(name),"Prop bone parent cycle");Matrix4f m=new Matrix4f();
        if(bone.has("parent")){var parent=bones.get(bone.get("parent").getAsString());Model.require(parent!=null,"Missing prop parent");m.set(boneMatrix(parent,bones,active));}
        m.mul(pivotRotation(bone));active.remove(name);return m;
    }
    private static Matrix4f pivotRotation(JsonObject value){
        float[] p=vec(value,"pivot"),r=vec(value,"rotation");float d=(float)(Math.PI/180);
        return new Matrix4f().translate(-p[0]/16,p[1]/16,p[2]/16).rotateZ(r[2]*d).rotateY(-r[1]*d).rotateX(-r[0]*d).translate(p[0]/16,-p[1]/16,-p[2]/16);
    }
    private static float[] vec(JsonObject value,String key){float[] out={0,0,0};if(value.has(key)){var a=value.getAsJsonArray(key);for(int i=0;i<3;i++){out[i]=a.get(i).getAsFloat();Model.require(Float.isFinite(out[i]),"Invalid prop coordinate");}}return out;}
    private static float[] pair(JsonObject value,String key){var a=value.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat()};}
    private static boolean bool(JsonObject v,String key){return v.has(key)&&v.get(key).getAsBoolean();}
}
