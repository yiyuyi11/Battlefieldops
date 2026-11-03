package cn.bfops;
public final class Bf1LayoutSim {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int w:new int[]{320,360,426,480,619,620,640,854,960,1280,1920})
        for(int h:new int[]{180,240,270,360,480,720,1080}){
            var layout=new Bf1Layout(w,h);var bounds=new Layout.Rect(0,0,w,h);
            var regions=new Layout.Rect[]{layout.loadout,layout.deploy,layout.squad,layout.options,new Layout.Rect(8,h-30,100,20)};
            for(var r:regions){check(r.w()>0&&r.h()>0,"positive size");check(r.equals(r.clippedTo(bounds)),"outside screen "+w+"x"+h+" "+r);}
            for(int i=0;i<regions.length;i++)for(int j=i+1;j<regions.length;j++)check(!regions[i].overlaps(regions[j]),"control overlap "+w+"x"+h+" "+i+"/"+j);
            for(int i=0;i<8;i++){
                var r=layout.slot(i);check(r.equals(r.clippedTo(layout.loadout)),"slot outside strip");check(r.w()>=35&&r.h()>=22,"slot too small");
                for(int j=i+1;j<8;j++)check(!r.overlaps(layout.slot(j)),"slot overlap");
            }
            var lobby=Layout.bf1Lobby(w,h);check(!lobby.left.overlaps(lobby.right),"lobby columns");
            check(!lobby.title.overlaps(lobby.content),"lobby heading");check(!lobby.content.overlaps(lobby.bar),"lobby footer");
        }
        System.out.println("BF1 responsive layout: "+checks+" checks passed");
    }
}
