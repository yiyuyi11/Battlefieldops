package cn.bfops.voice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.FrontVoicePlayRequest;
import cn.bfops.voice.common.audio.FrontVoicePlaybackMode;
import cn.bfops.voice.common.audio.VoiceCue;

/**
 * Server-to-client playback request: the server already resolved the cue and camp, and this only carries the
 * resolved {@link FrontVoicePlayRequest} so the client can hand it to its audio player unchanged.
 */
public record ClientboundPlayAudioPayload(FrontVoicePlayRequest request) implements CustomPacketPayload {
    public static final Type<ClientboundPlayAudioPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("battlefield_ops", "voice_play"));
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayAudioPayload> CODEC =
            StreamCodec.of(ClientboundPlayAudioPayload::write, ClientboundPlayAudioPayload::read);

    private static void write(FriendlyByteBuf buffer, ClientboundPlayAudioPayload payload) {
        FrontVoicePlayRequest request = payload.request;
        buffer.writeBoolean(request.isCue());
        if (request.isCue()) {
            buffer.writeEnum(request.voiceCue());
            buffer.writeEnum(request.campType());
        } else {
            buffer.writeUtf(request.soundEventId() != null ? request.soundEventId() : "");
        }
        buffer.writeEnum(request.playbackMode());
        buffer.writeInt(request.sourceEntityId());
        buffer.writeBoolean(request.hasPosition());
        if (request.hasPosition()) {
            buffer.writeDouble(request.position().x);
            buffer.writeDouble(request.position().y);
            buffer.writeDouble(request.position().z);
        }
        buffer.writeFloat(request.volume());
        buffer.writeFloat(request.pitch());
        buffer.writeFloat(request.attenuationDistance());
    }

    private static ClientboundPlayAudioPayload read(FriendlyByteBuf buffer) {
        boolean isCue = buffer.readBoolean();
        VoiceCue voiceCue = null;
        CampType campType = null;
        String soundEventId = null;
        if (isCue) {
            voiceCue = buffer.readEnum(VoiceCue.class);
            campType = buffer.readEnum(CampType.class);
        } else {
            soundEventId = buffer.readUtf();
            if (soundEventId.isEmpty()) soundEventId = null;
        }
        FrontVoicePlaybackMode playbackMode = buffer.readEnum(FrontVoicePlaybackMode.class);
        int sourceEntityId = buffer.readInt();
        Vec3 position = null;
        if (buffer.readBoolean()) {
            position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        float volume = buffer.readFloat();
        float pitch = buffer.readFloat();
        float attenuationDistance = buffer.readFloat();
        return new ClientboundPlayAudioPayload(new FrontVoicePlayRequest(
                soundEventId, voiceCue, campType, playbackMode, sourceEntityId, position, volume, pitch, attenuationDistance));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
