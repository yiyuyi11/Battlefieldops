package cn.bfops.voice.server.audio;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.FrontVoicePlayRequest;
import cn.bfops.voice.common.audio.FrontVoicePlaybackMode;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.network.ClientboundPlayAudioPayload;
import cn.bfops.voice.network.VoiceNetwork;

public final class FrontVoiceAudioEmitter {
    private FrontVoiceAudioEmitter() {
    }

    public static void playLocal(ServerPlayer listener, String soundEventId, float volume, float pitch) {
        if (!VoiceConfig.isServerAudioEnabled()) return;
        send(listener, FrontVoicePlayRequest.raw(soundEventId, FrontVoicePlaybackMode.LOCAL, -1, null, volume * VoiceConfig.getServerMasterVolume(), pitch, 0.0F));
    }

    public static void playAtPosition(
        ServerPlayer listener,
        String soundEventId,
        Vec3 position,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()) return;
        send(listener, FrontVoicePlayRequest.raw(soundEventId, FrontVoicePlaybackMode.POSITION, -1, position, volume * VoiceConfig.getServerMasterVolume(), pitch, attenuationDistance));
    }

    public static void playForEntity(
        ServerPlayer listener,
        String soundEventId,
        Entity sourceEntity,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()) return;
        send(
            listener,
            FrontVoicePlayRequest.raw(
                soundEventId,
                FrontVoicePlaybackMode.ENTITY,
                sourceEntity.getId(),
                sourceEntity.position(),
                volume * VoiceConfig.getServerMasterVolume(),
                pitch,
                attenuationDistance
            )
        );
    }

    public static void playCueForEntity(
        ServerPlayer listener,
        VoiceCue voiceCue,
        CampType campType,
        Entity sourceEntity,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()) return;
        send(
            listener,
            FrontVoicePlayRequest.cue(
                voiceCue,
                campType,
                FrontVoicePlaybackMode.ENTITY,
                sourceEntity.getId(),
                sourceEntity.position(),
                volume * VoiceConfig.getServerMasterVolume(),
                pitch,
                attenuationDistance
            )
        );
    }

    public static void broadcastCueForEntity(
        VoiceCue voiceCue,
        CampType campType,
        Entity sourceEntity,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()) return;
        FrontVoicePlayRequest request = FrontVoicePlayRequest.cue(
            voiceCue,
            campType,
            FrontVoicePlaybackMode.ENTITY,
            sourceEntity.getId(),
            sourceEntity.position(),
            volume * VoiceConfig.getServerMasterVolume(),
            pitch,
            attenuationDistance
        );
        VoiceNetwork.sendToTrackingAndSelf(sourceEntity, new ClientboundPlayAudioPayload(request));
    }

    public static void broadcastCueAtPosition(
        VoiceCue voiceCue,
        CampType campType,
        ServerPlayer sourcePlayer,
        Vec3 position,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()
            || sourcePlayer == null
            || sourcePlayer.server == null
            || position == null) {
            return;
        }
        float resolvedDistance = attenuationDistance <= 0.0F ? 32.0F : attenuationDistance;
        FrontVoicePlayRequest request = FrontVoicePlayRequest.cue(
            voiceCue,
            campType,
            FrontVoicePlaybackMode.POSITION,
            -1,
            position,
            volume * VoiceConfig.getServerMasterVolume(),
            pitch,
            resolvedDistance
        );
        double maxDistanceSqr = resolvedDistance * resolvedDistance;
        for (ServerPlayer listener : sourcePlayer.serverLevel().players()) {
            if (listener == null || listener.level() != sourcePlayer.level() || !cn.bfops.RoomAudience.member(sourcePlayer) || !cn.bfops.RoomAudience.same(sourcePlayer,listener)) {
                continue;
            }
            if (listener.position().distanceToSqr(position) > maxDistanceSqr) {
                continue;
            }
            send(listener, request);
        }
    }

    public static void broadcastRawForEntity(
        String soundEventId,
        Entity sourceEntity,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (!VoiceConfig.isServerAudioEnabled()
            || sourceEntity == null
            || soundEventId == null
            || soundEventId.isBlank()) {
            return;
        }
        FrontVoicePlayRequest request = FrontVoicePlayRequest.raw(
            soundEventId,
            FrontVoicePlaybackMode.ENTITY,
            sourceEntity.getId(),
            sourceEntity.position(),
            volume * VoiceConfig.getServerMasterVolume(),
            pitch,
            attenuationDistance
        );
        VoiceNetwork.sendToTrackingAndSelf(sourceEntity, new ClientboundPlayAudioPayload(request));
    }

    private static void send(ServerPlayer listener, FrontVoicePlayRequest request) {
        VoiceNetwork.sendToPlayer(listener, new ClientboundPlayAudioPayload(request));
    }
}
