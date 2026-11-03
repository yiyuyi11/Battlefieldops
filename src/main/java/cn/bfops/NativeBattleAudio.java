package cn.bfops;
import com.google.gson.JsonObject;
import java.util.*;
import cn.bfops.voice.common.audio.*;
import cn.bfops.voice.client.audio.FrontVoiceClientAudioPlayer;
/** Snapshot-edge callouts. Initial snapshots never announce pre-existing ownership. */
final class NativeBattleAudio {
    record Call(String event,long at){}
    private static final Map<String,Integer> owners=new HashMap<>();
    private static final ArrayDeque<Call> queue=new ArrayDeque<>();
    private static String room="",lastLanguage="";private static boolean ready;private static long nextVoice,lastCapture;
    static void reset(){owners.clear();queue.clear();room="";lastLanguage="";ready=false;nextVoice=lastCapture=0;}
    static void tick(JsonObject state,long now){
        var me=state.has("me")?state.getAsJsonObject("me"):new JsonObject();String current=Engine.str(me,"map","");
        boolean running=state.has("roomRunning")&&state.get("roomRunning").getAsBoolean();
        if(current.isEmpty()||!running){reset();return;}
        if(!room.equals(current)){reset();room=current;}
        var arena=FactionArt.arena();int team=Engine.num(me,"team",-1);
        if(arena==null||team<0||team>1||!state.has("flags"))return;
        var language=VoiceLanguages.resolve(arena,team);String id=language.name();
        if(!lastLanguage.equals(id)){queue.clear();lastLanguage=id;}
        var flags=state.getAsJsonArray("flags");
        for(int i=0;i<flags.size();i++){
            var flag=flags.get(i).getAsJsonObject();String key=i<arena.points.size()?arena.points.get(i).id:Integer.toString(i);
            int owner=Engine.num(flag,"owner",-1);Integer previous=owners.put(key,owner);
            if(!ready||previous==null||previous==owner)continue;
            String cue=owner==team?"TAKEN":previous==team?"LOST":null;
            if(cue==null)continue;
            if(owner==team&&now-lastCapture>=3000){MusicDirector.captured();lastCapture=now;}
            String event=NativeAudioBank.announcer(id,cue);
            if(event!=null&&language!=CampType.NONE&&queue.size()<3)queue.addLast(new Call(event,now));
        }
        ready=true;
        while(!queue.isEmpty()&&now-queue.peekFirst().at()>5000)queue.removeFirst();
        if(!NativeAudioBank.voiceEnabled()){queue.clear();return;}
        if(now>=nextVoice&&!queue.isEmpty()){
            Call call=queue.removeFirst();FrontVoiceClientAudioPlayer.play(FrontVoicePlayRequest.raw(call.event(),FrontVoicePlaybackMode.LOCAL,-1,null,.75f,1,0));nextVoice=now+Math.max(1000,Math.round(NativeAudioBank.duration(call.event())*1000)+150);
        }
    }
}
