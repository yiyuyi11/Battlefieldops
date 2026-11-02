package cn.bfops;

import java.util.*;

/** Per-room presentation state. New server snapshots do not restart the entry animation. */
final class MarkerAnimation {
    private static final class State {long born,seen;float alpha;State(long now){born=seen=now;}}
    private final Map<String,State> states=new HashMap<>();
    private String room="";private long last,now;private float dt=1/60f;
    void begin(String key,long time){
        if(!room.equals(key)||time<last){reset();room=key;}
        now=time;dt=last==0?1/60f:Math.max(0,Math.min(.25f,(time-last)/1000f));last=time;
        states.values().removeIf(s->time-s.seen>2000);
    }
    float delta(){return dt;}
    float alpha(String key,float target){var state=state(key);state.alpha=MarkerVisualRules.approach(state.alpha,target,dt);return state.alpha;}
    float entry(String key,int remainingTicks){var state=state(key);return MarkerVisualRules.entry((now-state.born)/50f,remainingTicks);}
    private State state(String key){var state=states.computeIfAbsent(key,k->new State(now));state.seen=now;return state;}
    int size(){return states.size();}
    void reset(){states.clear();room="";last=0;}
}
