package cn.bfops.voice.common.audio;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record FrontVoicePlayRequest(
    @Nullable String soundEventId,
    @Nullable VoiceCue voiceCue,
    @Nullable CampType campType,
    FrontVoicePlaybackMode playbackMode,
    int sourceEntityId,
    Vec3 position,
    float volume,
    float pitch,
    float attenuationDistance
) {
    public boolean hasSourceEntity() {
        return sourceEntityId >= 0;
    }

    public boolean hasPosition() {
        return position != null;
    }

    public boolean isCue() {
        return voiceCue != null;
    }

    // Constructor for raw sound event
    public static FrontVoicePlayRequest raw(String soundEventId, FrontVoicePlaybackMode playbackMode, int sourceEntityId, Vec3 position, float volume, float pitch, float attenuationDistance) {
        return new FrontVoicePlayRequest(soundEventId, null, null, playbackMode, sourceEntityId, position, volume, pitch, attenuationDistance);
    }

    // Constructor for voice cue
    public static FrontVoicePlayRequest cue(VoiceCue voiceCue, CampType campType, FrontVoicePlaybackMode playbackMode, int sourceEntityId, Vec3 position, float volume, float pitch, float attenuationDistance) {
        return new FrontVoicePlayRequest(null, voiceCue, campType, playbackMode, sourceEntityId, position, volume, pitch, attenuationDistance);
    }
}
