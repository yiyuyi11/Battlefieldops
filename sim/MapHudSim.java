package cn.bfops;

import com.google.gson.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** CPU raster and actual renderer wiring checks; does not simulate a GPU/shader pack. */
public final class MapHudSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static ClassNode type(String name)throws Exception{
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+name+".class"))).accept(n,0);return n;
    }
    static boolean calls(String cls,String method,String target,String name)throws Exception{
        for(var m:type(cls).methods)if(m.name.equals(method))for(var i:m.instructions)
            if(i instanceof MethodInsnNode c&&c.owner.equals("cn/bfops/"+target)&&c.name.equals(name))return true;
        return false;
    }
    static int signature(Model.Arena a,JsonObject s)throws Exception{
        var m=MapBake.class.getDeclaredMethod("signatureOf",Model.Arena.class,JsonObject.class,int.class,int.class);m.setAccessible(true);return (int)m.invoke(null,a,s,0,0);
    }
    public static void main(String[] args)throws Exception{
        int previous=-1;
        for(int a=0;a<256;a++){
            int backing=HudLabelStyle.backing(a<<24|0x619fea)>>>24;
            check(backing<=88&&backing<=a&&backing>=previous,"background opacity follows foreground and never becomes opaque");previous=backing;
        }
        check(HudLabelStyle.focusedOpacity(0)==0,"fully faded label stays hidden");
        check(HudLabelStyle.focusedOpacity(.05f)>=.69f,"crosshair-focused objective name remains legible");
        check(HudLabelStyle.focusedOpacity(1)==1,"foreground opacity is bounded");
        Model.Arena arena=new Model.Arena();arena.boundary.addAll(List.of(new Model.Vec(-8,-8),new Model.Vec(8,-8),new Model.Vec(8,8),new Model.Vec(-8,8)));
        arena.points.add(new Model.Point());
        Minimap map=new Minimap(0,0,64,64,0,0,32);
        int[] before=new int[4096],after=new int[4096];
        JsonObject state=JsonParser.parseString("{\"flags\":[{\"owner\":0,\"progress\":20}]}").getAsJsonObject();
        map.paintStatic(Minimap.painter(before,64,64),arena,state,0,false,true);
        int sig=signature(arena,state);
        state.getAsJsonArray("flags").get(0).getAsJsonObject().addProperty("progress",90);
        state.getAsJsonArray("flags").get(0).getAsJsonObject().addProperty("owner",1);
        map.paintStatic(Minimap.painter(after,64,64),arena,state,0,false,true);
        check(Arrays.equals(before,after),"ownership/progress icons never persist in terrain bake");
        check(signature(arena,state)==sig,"capture ticks do not trigger static rebakes");
        check(before[0]==0xb0141820,"reference outside-battle-area mask");
        check(before[32*64+32]==0,"map centre has no obsolete square/bar or opaque panel over terrain");
        arena.boundary.set(0,new Model.Vec(-12,-8));
        check(signature(arena,state)!=sig,"moving vertex without changing count invalidates cache");
        var sector=new Model.Sector();sector.boundary.addAll(arena.boundary);arena.sectors.add(sector);sig=signature(arena,state);
        sector.boundary.set(0,new Model.Vec(-14,-8));
        check(signature(arena,state)!=sig,"moving sector vertices also invalidates cache");
        check(calls("Minimap","draw","MapIcons","contacts"),"minimap uses common contact layer");
        check(calls("MapPicker","players","MapIcons","contacts"),"tactical map uses same contact layer");
        check(calls("MapPicker","draw","MapIcons","capture"),"tactical objectives use native capture adapter");
        check(calls("Minimap","drawPointLabels","MapIcons","capture"),"minimap objectives use same adapter");
        check(calls("MapIcons","nativeMarker","conquest/client/marker/TacticalMarkerRenderer","drawMarker"),"contacts invoke original native renderer");
        int modes=0;
        for(var m:type("conquest/client/render/CaptureDiamondRenderer").methods)
            if(m.name.equals("drawIconLabelInHud")||m.name.equals("drawIconGlyphInHud"))for(var i:m.instructions)
                if(i instanceof FieldInsnNode f&&f.owner.equals("net/minecraft/client/gui/Font$DisplayMode")){
                    modes++;check(f.name.equals("NORMAL"),"HUD glyphs use regular screen font pass, not see-through world pass");
                }
        check(modes>=2,"both HUD glyph and HUD label font paths verified");
        System.out.println("MapHudSim: "+checks+" raster, opacity and renderer wiring checks passed (no GPU test)");
    }
}
