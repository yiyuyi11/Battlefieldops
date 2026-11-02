package cn.bfops.voice.server.audio;

import net.minecraft.server.level.ServerPlayer;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.server.compat.GdcqCompat;

public final class FrontVoiceCueEmitter {
    private FrontVoiceCueEmitter() {
    }

    public static void emitCue(ServerPlayer sourcePlayer, VoiceCue cue) {
        if (sourcePlayer == null || sourcePlayer.server == null) {
            return;
        }
        if(cue==VoiceCue.DOWNED_HELP){if(cn.bfops.BattlefieldOps.engine!=null)cn.bfops.BattlefieldOps.engine.callRescue(sourcePlayer);return;}
        CampType camp = GdcqCompat.resolveCamp(sourcePlayer);
        FrontVoiceAudioEmitter.broadcastCueForEntity(cue, camp, sourcePlayer, 1.0F, 1.0F, 32.0F);
    }
}
