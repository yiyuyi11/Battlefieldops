package cn.bfops;
import com.google.gson.*;
import java.nio.file.*;
import cn.bfops.voice.common.audio.*;
public final class UiVoiceRegressionSim {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("UI/voice check "+checks);}
    public static void main(String[] args)throws Exception{
        check(PlayerMarkerSize.self(9)==9&&PlayerMarkerSize.ALLY==10);
        check(PlayerMarkerSize.self(Float.NaN)==9);
        check(PlayerMarkerSize.self(999)==20&&PlayerMarkerSize.self(-99)==4);
        for(float scale:new float[]{.25f,.35f,.5f,1f,1.5f,2f}){
            var pose=new org.joml.Matrix4f().translate(13,21,0).scale(scale);
            int[] b=GuiClip.bounds(pose,446,112,270,274);
            check(b[0]<b[2]&&b[1]<b[3]);
            check(b[0]<=13+446*scale&&b[2]>=13+716*scale);
            check(b[2]<=Math.ceil(13+726*scale));
        }
        var bank=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/battlefield_ops/bf1_audio_bank.json"))).getAsJsonObject();
        var field=NativeAudioBank.class.getDeclaredField("cached");field.setAccessible(true);field.set(null,bank);
        for(String lang:new String[]{"UK","US","DE","FR","IT","RU","TK"}){
            var entries=bank.getAsJsonObject("voices").getAsJsonObject(lang);check(entries.size()==26);
            for(var entry:entries.entrySet()){
                VoiceCue cue=VoiceCue.valueOf(entry.getKey());CampType camp=CampType.valueOf(lang);
                String expected=entry.getValue().getAsString();
                check(CueRouter.resolveFirstPersonLayers(cue,camp).equals(java.util.List.of(expected)));
                check(expected.equals(CueRouter.resolveThirdPerson(cue,camp)));
                check(expected.startsWith("bf1.voice."+lang.toLowerCase()+"."));
            }
            for(VoiceCue unsupported:new VoiceCue[]{VoiceCue.NICE_SHOT,VoiceCue.RPG_ALERT,VoiceCue.LOW_HEALTH_PANIC,VoiceCue.PLACE_C4,VoiceCue.HEAVY_HURT}){
                check(CueRouter.resolveFirstPersonLayers(unsupported,CampType.valueOf(lang)).isEmpty());
                check(CueRouter.resolveThirdPerson(unsupported,CampType.valueOf(lang))==null);
            }
            check(NativeAudioBank.announcer(lang,"TAKEN").startsWith("bf1.announcer."+lang.toLowerCase()+"."));
            check(NativeAudioBank.announcer(lang,"LOST").startsWith("bf1.announcer."+lang.toLowerCase()+"."));
        }
        check(CueRouter.resolveFirstPersonLayers(VoiceCue.THROW_AMMO,CampType.NONE).isEmpty());
        System.out.println("UiVoiceRegressionSim: "+checks+" routing/clip/marker checks passed");
    }
}
