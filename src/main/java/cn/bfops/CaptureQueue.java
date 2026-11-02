package cn.bfops;

import java.util.*;

/** The reference HUD queue's 24px spacing, focus sizes and 0.18s layout interpolation. */
final class CaptureQueue {
    record Slot(int point,float x,float y,float radius,float alpha,float textScale){}
    private final Map<Integer,Slot> slots=new HashMap<>();private String room="";private long last;
    List<Slot> update(String key,List<Integer> points,int focus,int width,int available,long now){
        if(!room.equals(key)){reset();room=key;}
        float dt=last==0?0:Math.max(0,Math.min(.25f,(now-last)/1000f));last=now;
        float step=1-(float)Math.exp(-Math.max(dt,.004166667f)/.18f);
        boolean focused=points.contains(focus);int count=points.size()-(focused?1:0),columns=Math.max(1,available/24),index=0;
        int rows=Math.max(1,(count+columns-1)/columns);var out=new ArrayList<Slot>();
        for(int point:points){
            Slot target;
            if(focused&&point==focus)target=new Slot(point,width*.5f,75+(rows-1)*24,11.6f,.95f,.76f);
            else{
                int row=index/columns,col=index%columns,inRow=Math.min(columns,count-row*columns);
                target=new Slot(point,width*.5f-(inRow-1)*12+col*24,37+row*24,5.8f,.9f,.58f);index++;
            }
            Slot old=slots.get(point),next=old==null?target:new Slot(point,mix(old.x,target.x,step),mix(old.y,target.y,step),mix(old.radius,target.radius,step),mix(old.alpha,target.alpha,step),mix(old.textScale,target.textScale,step));
            slots.put(point,next);out.add(next);
        }
        slots.keySet().retainAll(points);return out;
    }
    private static float mix(float a,float b,float step){return a+(b-a)*step;}
    void reset(){slots.clear();room="";last=0;}
}
