package cn.bfops.voice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import cn.bfops.voice.common.audio.VoiceCue;

/**
 * Client-to-server request to emit a quick-message voice cue for the sending player.
 */
public record C2SRequestCuePayload(VoiceCue cue) implements CustomPacketPayload {
    public static final Type<C2SRequestCuePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("battlefield_ops", "voice_request"));
    public static final StreamCodec<FriendlyByteBuf, C2SRequestCuePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeEnum(payload.cue),
                    buffer -> new C2SRequestCuePayload(buffer.readEnum(VoiceCue.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
