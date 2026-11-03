package cn.bfops;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import cn.bfops.conquest.client.render.CaptureDiamondRenderer;

/** Runs the adapted renderer's pure geometry/colour functions, not a GPU rendering test. */
public final class NativeMarkerSim {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static void near(float a,float b,String reason){check(Math.abs(a-b)<.0001f,reason+": "+a+" vs "+b);}
    static Object call(String name,Class<?>[] types,Object...args)throws Exception{var m=CaptureDiamondRenderer.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
    public static void main(String[] args)throws Exception{
        for(int owner=-1;owner<2;owner++)for(int self=0;self<2;self++)for(int progress=0;progress<=100;progress+=5){
            float converted=MarkerVisualRules.displayProgress(progress,self);String token=MarkerVisualRules.teamToken(owner,self);
            String sector=(String)call("resolveSectorPerspectiveToken",new Class<?>[]{float.class,String.class,String.class},converted,token,"NEUTRAL");
            String expected=owner>=0?(owner==self?"ALLY":"ENEMY"):converted<50?"ALLY":converted>50?"ENEMY":"NEUTRAL";
            check(sector.equals(expected),"progress sector follows the viewer's side");
            near(Math.abs(converted-50),Math.abs(progress-50),"view translation preserves capture amount");
        }
        near(MarkerVisualRules.displayProgress(100,0),0,"blue fully owned is allied");
        near(MarkerVisualRules.displayProgress(0,1),0,"red fully owned is allied");
        near(MarkerVisualRules.displayProgress(Float.NaN,0),50,"invalid progress does not poison geometry");
        float[] ally=(float[])call("resolveBaseColor",new Class<?>[]{float.class,String.class},0f,"ALLY");
        near(ally[0],.2f,"reference allied red component");near(ally[1],.33f,"reference allied green component");near(ally[2],.7f,"reference allied blue component");
        float[] neutral=(float[])call("resolveBaseColor",new Class<?>[]{float.class,String.class},80f,"NEUTRAL");
        for(float channel:neutral)near(channel,.45f,"neutral point fill does not pretend ownership");
        for(boolean clockwise:new boolean[]{false,true})for(int quarter=0;quarter<=4;quarter++){
            @SuppressWarnings("unchecked") var points=(List<float[]>)call("buildSectorPerimeter",new Class<?>[]{float.class,float.class,boolean.class},7.5f,quarter/4f,clockwise);
            near(points.getFirst()[0],0,"sector starts at top x");near(points.getFirst()[1],-7.5f,"sector starts at screen top, not bottom");
            for(var p:points)near(Math.abs(p[0])+Math.abs(p[1]),7.5f,"sector lies on diamond perimeter");
            if(quarter==4){near(points.getLast()[0],points.getFirst()[0],"full sector closes x");near(points.getLast()[1],points.getFirst()[1],"full sector closes y");}
        }
        for(int width:new int[]{320,427,854,1920})for(int height:new int[]{240,480,1080}){
            near(MarkerVisualRules.alpha(width,height,width*.5,height*.5,false),.05f,"crosshair alpha matches reference");
            near(MarkerVisualRules.alpha(width,height,width*.5+300,height*.5,false),.4f,"periphery alpha matches reference");
            near(MarkerVisualRules.alpha(width,height,width*.5,height*.5,true),.4f,"camera override bypasses aiming fade");
            float previous=0;
            for(int d=0;d<=400;d++){float a=MarkerVisualRules.alpha(width,height,width*.5+d,height*.5,false);check(a>=previous-1e-6f&&a>=.05f-1e-6f&&a<=.4f+1e-6f,"alpha varies continuously and remains bounded");previous=a;}
        }
        near(MarkerVisualRules.entry(0,Integer.MAX_VALUE),0,"entry starts hidden");near(MarkerVisualRules.entry(14,Integer.MAX_VALUE),1,"entry settles in 14 ticks");
        near(MarkerVisualRules.entry(100,0),0,"expired marker shrinks out");near(MarkerVisualRules.friendScale(3),1.1f,"near friendly scale");near(MarkerVisualRules.friendScale(30),.72f,"far friendly scale");
        var animation=new MarkerAnimation();animation.begin("room-a:blue",1000);animation.entry("beacon",Integer.MAX_VALUE);
        animation.begin("room-a:blue",1700);near(animation.entry("beacon",Integer.MAX_VALUE),1,"snapshot refresh does not restart entry");
        animation.begin("room-b:blue",1716);check(animation.size()==0,"room change discards old animations");near(animation.entry("beacon",Integer.MAX_VALUE),0,"same ID in another room gets fresh entry");
        animation.begin("room-b:red",1732);check(animation.size()==0,"side change clears presentation state");
        animation.entry("expired",Integer.MAX_VALUE);animation.begin("room-b:red",4000);check(animation.size()==0,"missing marker state expires");
        var queue=new CaptureQueue();var ids=List.of(0,1,2);
        var initial=queue.update("a",ids,-1,854,260,1000);
        near(initial.get(1).x(),427,"queue centres evenly spaced objectives");near(initial.get(1).radius(),5.8f,"queue uses native normal radius");
        var moved=queue.update("a",ids,1,854,260,1016).get(1);
        check(moved.radius()>5.8f&&moved.radius()<11.6f&&moved.y()>37&&moved.y()<75,"focus animates rather than teleporting");
        queue.update("b",ids,1,854,260,1032);var focus=queue.update("b",ids,1,854,260,1048).get(1);
        near(focus.radius(),11.6f,"fresh room focus uses native focus radius");near(focus.alpha(),.95f,"focus uses native alpha");
        for(int w:new int[]{320,427,854}){
            var many=new java.util.ArrayList<Integer>();for(int i=0;i<24;i++)many.add(i);
            queue.reset();var slots=queue.update("many",many,0,w,Math.min(260,w-16),1000);
            for(var slot:slots)check(slot.x()-slot.radius()>0&&slot.x()+slot.radius()<w&&slot.y()>28,"24-objective layout stays inside screen and below ticket bar");
        }
        check(queue.update("dm",List.of(),-1,320,260,2000).isEmpty(),"deathmatch has no stale capture queue");
        Path assets=Path.of("src/main/resources/assets/battlefield_ops/textures/gui");
        try(var reference=new ZipFile("gd656conquest-0.10.6-normal.jar")){
            for(String name:List.of("hud_beacon","hud_ammo_box","hud_medical_box","hud_ammo_bag","hud_medic_bag","marker_med_box")){
                byte[] original=reference.getInputStream(reference.getEntry("assets/gd656conquest/textures/gui/"+name+".png")).readAllBytes();
                check(Arrays.equals(original,Files.readAllBytes(assets.resolve(name+".png"))),"native asset unchanged: "+name);
            }
            for(String role:MateMarker.stems()){
                var mask=ImageIO.read(assets.resolve("class_"+role+".png").toFile());
                var original=ImageIO.read(reference.getInputStream(reference.getEntry("assets/gd656conquest/textures/gui/marker_class_"+role+"_team.png")));
                check(mask.getWidth()==original.getWidth()&&mask.getHeight()==original.getHeight(),"role mask dimensions unchanged");
                for(int y=0;y<mask.getHeight();y++)for(int x=0;x<mask.getWidth();x++)check(mask.getRGB(x,y)>>>24==original.getRGB(x,y)>>>24,"role mask preserves original coverage");
            }
        }
        System.out.println("NativeMarkerSim: "+checks+" reference geometry, perspective, alpha, lifecycle and asset checks passed (no GPU test)");
    }
}
