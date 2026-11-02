package cn.bfops.voice.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.server.audio.FrontVoiceAudioEmitter;
import cn.bfops.voice.server.audio.FrontVoiceCueEmitter;
import cn.bfops.voice.server.compat.GdcqCompat;

public final class FrontVoiceApi {
    private FrontVoiceApi() {
    }

    public static boolean playCue(ServerPlayer sourcePlayer, String cueId) {
        if (sourcePlayer == null || cueId == null || cueId.isBlank()) {
            return false;
        }
        VoiceCue cue = VoiceCue.fromString(cueId);
        if (cue == null) {
            return false;
        }
        FrontVoiceCueEmitter.emitCue(sourcePlayer, cue);
        return true;
    }

    public static boolean playConfirmedDeath(ServerPlayer sourcePlayer) {
        if (sourcePlayer == null) {
            return false;
        }
        FrontVoiceCueEmitter.emitCue(sourcePlayer, VoiceCue.FINAL_DEATH);
        return true;
    }

    public static boolean playCueAt(ServerPlayer sourcePlayer, Vec3 position, String cueId) {
        if (sourcePlayer == null || position == null || cueId == null || cueId.isBlank()) {
            return false;
        }
        VoiceCue cue = VoiceCue.fromString(cueId);
        if (cue == null) {
            return false;
        }
        CampType camp = GdcqCompat.resolveCamp(sourcePlayer);
        FrontVoiceAudioEmitter.broadcastCueAtPosition(cue, camp, sourcePlayer, position, 1.0F, 1.0F, 24.0F);
        return true;
    }

    public static boolean playThirdPersonSound(ServerPlayer sourcePlayer, String soundId, float volume, float pitch, double maxDistance) {
        if (sourcePlayer == null || soundId == null || soundId.isBlank()) {
            return false;
        }
        FrontVoiceAudioEmitter.broadcastRawForEntity(soundId, sourcePlayer, volume, pitch, (float) maxDistance);
        return true;
    }
}
