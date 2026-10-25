package cn.bfops;
import java.util.*;

/** Full draft snapshots make clear, vertex edits and form changes reversible together. */
final class DraftHistory {
    private final List<String> states=new ArrayList<>();
    private int at=-1;
    void reset(String value){states.clear();states.add(value);at=0;}
    void record(String value){
        if(at>=0&&states.get(at).equals(value))return;
        while(states.size()>at+1)states.removeLast();
        states.add(value);at++;
        if(states.size()>100){states.removeFirst();at--;}
    }
    String undo(){if(at>0)at--;return at<0?null:states.get(at);}
    String redo(){if(at+1<states.size())at++;return at<0?null:states.get(at);}
}
